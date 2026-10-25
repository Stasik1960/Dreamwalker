package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/** Items for explicitly retained old native whole poses only. Shared catalog
 * metadata, never coordinates, placed identities or per-instance geometry. */
public final class NativeGridRetainedWholeItems {
    public static final String RESOURCE = "/assets/bloodborne_dw/native_grid/retained_whole_items.json";
    private NativeGridRetainedWholeItems() {}

    private static final class Whitelist {
        private static String resourceSha256;
        private static final Map<String, StaticArchitectureCatalog.Pose> POSES = load();
        private static Map<String, StaticArchitectureCatalog.Pose> load() {
            try (InputStream input = NativeGridRetainedWholeItems.class.getResourceAsStream(RESOURCE)) {
                if (input == null) return Map.of();
                byte[] bytes = input.readAllBytes();
                resourceSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                JsonObject data = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                check(data.get("schemaVersion").getAsInt() == 1, "Unknown retained whole-item schema");
                Map<String, StaticArchitectureCatalog.Pose> known = new HashMap<>();
                for (var pose : StaticArchitectureCatalog.poses()) known.put(pose.key(), pose);
                Map<String, StaticArchitectureCatalog.Pose> allowed = new LinkedHashMap<>();
                for (JsonElement element : data.getAsJsonArray("poses")) {
                    JsonObject row = element.getAsJsonObject();
                    check(row.keySet().equals(Set.of("legacyPose", "registryId", "properties", "shift", "mountY")), "Unexpected retained pose fields");
                    String key = row.get("legacyPose").getAsString();
                    var pose = known.get(key);
                    check(pose != null && Registries.BLOCK.getId(pose.state().getBlock()).toString().equals(row.get("registryId").getAsString()), "Unknown retained native pose/registry");
                    Map<String, String> properties = new TreeMap<>();
                    row.getAsJsonObject("properties").entrySet().forEach(entry -> properties.put(entry.getKey(), entry.getValue().getAsString()));
                    check(CompositeData.data(pose.state(), new NbtCompound()).properties().equals(properties), "Retained native pose properties differ");
                    JsonArray shift = row.getAsJsonArray("shift");
                    check(shift.size() == 3 && same(shift.get(0).getAsDouble(), pose.shift().x()) && same(shift.get(1).getAsDouble(), pose.shift().y()) && same(shift.get(2).getAsDouble(), pose.shift().z()) && same(row.get("mountY").getAsDouble(), pose.mountY()), "Retained native pose shift/mount differs");
                    check(pose.mountY() == 0, "Existing source item cannot encode an installation MountY");
                    check(allowed.putIfAbsent(key, pose) == null, "Repeated retained native whole pose");
                    ItemStack stack = encode(pose);
                    check(!stack.isEmpty() && stack.getCount() == 1 && stack.getItem() instanceof BlockItem, "Retained pose has no usable canonical source item");
                }
                return Collections.unmodifiableMap(allowed);
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot read exact retained native whole-item whitelist", failure);
            }
        }
    }

    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalArgumentException(reason); }
    private static boolean same(double actual, double expected) { return Double.isFinite(actual) && actual == expected; }
    public static boolean allowed(StaticArchitectureCatalog.Pose pose) { return pose != null && Whitelist.POSES.get(pose.key()) == pose; }
    public static int count() { return Whitelist.POSES.size(); }
    public static String resourceSha256() { count(); return Whitelist.resourceSha256; }
    private static ItemStack encode(StaticArchitectureCatalog.Pose pose) { return pose.block().art(pose.state(), pose.payload()); }
    public static ItemStack item(StaticArchitectureCatalog.Pose pose) { return allowed(pose) ? encode(pose) : ItemStack.EMPTY; }

    /** Same nearest-fragment selection as the old native server mechanism.
     * Unlisted nearer artwork keeps its previous pick behavior. */
    public static ItemStack pick(World world, BlockPos pos, PlayerEntity player) {
        if (player == null || !world.isChunkLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof StaticArchitectureBlock)) return ItemStack.EMPTY;
        var component = world instanceof ServerWorld server ? StaticArchitectureRuntime.target(server, pos, player) : clientTarget(world, pos, player);
        if (component == null || !allowed(component.pose()) || !world.isChunkLoaded(component.root()) || !StaticArchitectureCatalog.matchesRoot(world.getBlockState(component.root()), component.pose())) return ItemStack.EMPTY;
        return item(component.pose());
    }
    private static StaticArchitectureRuntime.Component clientTarget(World world, BlockPos pos, PlayerEntity player) {
        BlockState state = world.getBlockState(pos);
        var recipe = StaticArchitectureCatalog.recipe(state);
        Vec3d start = player.getEyePos(), end = start.add(player.getRotationVec(1).multiply(8));
        StaticArchitectureRuntime.Component best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var fragment : recipe.fragments()) {
            var origin = fragment.origin();
            var boxes = fragment.pose().outline().getOrDefault(new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell(-origin.x(), -origin.y(), -origin.z()), List.of());
            var hit = ImportedPhysicalShapes.exact(boxes).raycast(start, end, pos);
            double next = hit == null ? Double.POSITIVE_INFINITY : hit.getPos().squaredDistanceTo(start);
            if (next < distance) { distance = next; best = new StaticArchitectureRuntime.Component(pos.add(origin.x(), origin.y(), origin.z()), fragment.pose()); }
        }
        return best;
    }

    /** All resource validation and canonical encoding must precede deletion. */
    static ItemStack prepareDrop(ServerWorld world, StaticArchitectureRuntime.Component component, PlayerEntity player, boolean requested) {
        if (!requested || player != null && player.getAbilities().creativeMode || !world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)) return ItemStack.EMPTY;
        return item(component.pose());
    }
    static void dropCommitted(ServerWorld world, StaticArchitectureRuntime.Component component, ItemStack prepared,
                              dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Result result) {
        if (result.outcome() == dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED && result.changedCells() > 0 && !prepared.isEmpty())
            Block.dropStack(world, component.root(), prepared);
    }
}
