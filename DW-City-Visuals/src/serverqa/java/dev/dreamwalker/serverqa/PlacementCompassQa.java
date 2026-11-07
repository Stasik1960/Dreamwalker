package dev.dreamwalker.serverqa;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** An opt-in test of placement and the real WorldEdit compass dispatch on a disposable flat server. */
public final class PlacementCompassQa implements ModInitializer {
    private boolean pending;

    @Override public void onInitialize() {
        if (System.getProperty("dw.placementCompassQa") == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> pending = true);
        ServerTickEvents.END_SERVER_TICK.register(this::verify);
    }

    private void verify(MinecraftServer server) {
        if (!pending) return;
        pending = false;
        var result = new ArrayList<String>();
        try {
            var world = server.getOverworld();
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) world.getChunk(x, z);
            var player = FakePlayer.get(world, new GameProfile(UUID.randomUUID(), "DwCompassQa"));
            player.changeGameMode(GameMode.CREATIVE);
            server.getPlayerManager().addToOperators(player.getGameProfile());
            player.refreshPositionAndAngles(11.5, 105, 3.5, 0, 0);
            check(result, "X- and Z-authored models face four cardinal player directions", () -> checkRotations(world, player));
            check(result, "full movement height, vanilla block and a mod fence above, owned contribution remains",
                    () -> checkFences(world, player));
            check(result, "real Fabric WorldEdit right click crossed a DreamWalker block and landed in air",
                    () -> checkCompass(server, world, player));
            check(result, "shutter and plain door IDs, two-cell door placement, operation without side frame",
                    () -> checkNewBlocks(world, player));
        } catch (Throwable failure) {
            result.add("FAIL: " + failure);
            failure.printStackTrace();
        }
        try {
            Files.write(Path.of(System.getProperty("dw.placementCompassQa")), result);
        } catch (Exception failure) {
            failure.printStackTrace();
        } finally {
            server.stop(false);
        }
    }

    @FunctionalInterface
    private interface Check { void run() throws Exception; }

    private static void check(ArrayList<String> result, String label, Check check) {
        try {
            check.run();
            result.add("PASS: " + label);
        } catch (Throwable failure) {
            result.add("FAIL: " + label + " - " + failure);
            failure.printStackTrace();
        }
    }

    private static void checkRotations(ServerWorld world, ServerPlayerEntity player) {
        checkRotationFamily(world, player, "source_addon_floor_panels_10", 4);
        checkRotationFamily(world, player, "source_addon_stripped_oka_log_0", 22);
    }

    private static void checkRotationFamily(ServerWorld world, ServerPlayerEntity player, String id, int z) {
        Block block = block(id);
        for (int turn = 0; turn < 4; turn++) {
            BlockPos floor = new BlockPos(4 + turn * 6, 100, z);
            world.setBlockState(floor, Blocks.STONE.getDefaultState());
            player.setYaw(turn * 90);
            require(place(player, new ItemStack(block), floor), "Item placement failed at yaw " + player.getYaw()
                    + " (" + CompositeRuntime.lastResult() + ")");
            BlockPos root = floor.up();
            var placed = world.getBlockState(root);
            require(world.getBlockEntity(root) instanceof CompositeBlockEntity, "Missing placed owner for " + id);
            var entity = (CompositeBlockEntity)world.getBlockEntity(root);
            var model = CompositeRuntime.instance(world, root, placed, entity.resident().instanceId(), entity.payload());
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (var cell : model.cells().entrySet()) for (var part : cell.getValue().selection()) {
                minX = Math.min(minX, cell.getKey().x() + part.minX());
                maxX = Math.max(maxX, cell.getKey().x() + part.maxX());
                minZ = Math.min(minZ, cell.getKey().z() + part.minZ());
                maxZ = Math.max(maxZ, cell.getKey().z() + part.maxZ());
            }
            require((turn & 1) == 0 ? maxX - minX > maxZ - minZ : maxZ - minZ > maxX - minX,
                    "Wrong visual axis at yaw " + player.getYaw() + " for " + id + " (" + placed + ")");
        }
    }

    private static void checkFences(ServerWorld world, ServerPlayerEntity player) {
        Block fence = block("source_assembly_nether_brick_fence");
        BlockPos floor = new BlockPos(4, 100, 9);
        world.setBlockState(floor, Blocks.STONE.getDefaultState());
        require(place(player, compact(fence), floor), "First compact fence rejected");
        BlockPos lower = floor.up();
        var state = world.getBlockState(lower);
        var shape = state.getCollisionShape(world, lower, ShapeContext.of(player));
        require(state.isOf(fence) && ImportedCompactBlock.compact(state) && !shape.isEmpty()
                && shape.getMax(Direction.Axis.Y) > 1.49, "Fence lost its 1.5-block movement collider");
        require(place(player, compact(fence), lower), "Could not put a mod fence on a mod fence");
        require(world.getBlockState(lower.up()).isOf(fence), "Stacked fence did not occupy the cell immediately above");

        BlockPos owned = new BlockPos(11, 101, 9);
        world.setBlockState(owned.down(), Blocks.STONE.getDefaultState());
        var ownedFence = fence.getDefaultState().with(ImportedCompactBlock.COMPACT, false);
        var id = UUID.randomUUID();
        var placement = CompositeRuntime.place(world, owned, ownedFence, id, player);
        require(placement.outcome() == TransactionCore.Outcome.COMMITTED, "Owned fence setup " + placement);
        require(CompositeRuntime.contributions(world, owned.up()).stream().anyMatch(entry ->
                id.equals(entry.owner().instanceId()) && !entry.shape().collision().isEmpty()),
                "Owned fence fixture lacks its elevated movement contribution");
        require(place(player, new ItemStack(Items.STONE), owned), "Vanilla block rejected above owned fence");
        require(world.getBlockState(owned.up()).isOf(Blocks.STONE), "Ordinary block occupies the wrong cell");
        require(CompositeRuntime.contributions(world, owned.up()).stream().anyMatch(entry ->
                id.equals(entry.owner().instanceId())), "Ordinary placement lost the surviving owner contribution");
    }

    private static void checkCompass(MinecraftServer server, ServerWorld world, ServerPlayerEntity player) throws Exception {
        BlockPos obstacle = new BlockPos(11, 102, 16);
        for (int z = 12; z <= 20; z++) world.setBlockState(new BlockPos(11, 100, z), Blocks.STONE.getDefaultState());
        Block target = block("dw_whiteness");
        world.setBlockState(obstacle, target.getDefaultState());
        world.setBlockState(obstacle.down(), target.getDefaultState());
        player.refreshPositionAndAngles(11.5, 101, 13.5, 0, 0);
        player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.COMPASS));
        Map<UUID, ServerPlayerEntity> players = playerLookup(server.getPlayerManager());
        players.put(player.getUuid(), player);
        try {
            var hit = new BlockHitResult(new Vec3d(11.5, 102.6, obstacle.getZ()), Direction.NORTH, obstacle, false);
            var action = UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, hit);
            require(action.isAccepted(), "WorldEdit did not consume the right click: " + action);
            require(player.getZ() > obstacle.getZ() + 1 && player.getZ() < 20,
                    "WorldEdit did not land behind the mod block: " + player.getPos());
            require(world.isSpaceEmpty(player, player.getBoundingBox()), "Compass landed inside a collider");
        } finally {
            players.remove(player.getUuid());
        }
    }

    private static void checkNewBlocks(ServerWorld world, ServerPlayerEntity player) {
        Block shutter = block("source_shutter_window");
        Block door = block("door_90960_plain");
        require(door instanceof DoorBlock, "Plain variant must use vanilla door behavior");
        var shutterEntry = DebugCatalogue.entry(Registries.BLOCK.getId(shutter));
        var doorEntry = DebugCatalogue.entry(Registries.BLOCK.getId(door));
        require(shutterEntry != null && "93241".equals(shutterEntry.temporaryId()), "Shutter ID 93241 missing");
        require(doorEntry != null && "93242".equals(doorEntry.temporaryId()), "Door ID 93242 missing");
        BlockPos floor = new BlockPos(19, 100, 8);
        world.setBlockState(floor, Blocks.STONE.getDefaultState());
        player.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.refreshPositionAndAngles(19.5, 101, 5.5, 0, 0);
        require(place(player, new ItemStack(door), floor), "Plain door rejected");
        BlockPos lower = floor.up();
        var placed = world.getBlockState(lower);
        require(placed.isOf(door) && placed.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && world.getBlockState(lower.up()).get(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "Door halves unavailable");
        require(world.getBlockState(lower.east()).isAir() && world.getBlockState(lower.west()).isAir(), "Door placed an unwanted frame");
        var hit = new BlockHitResult(Vec3d.ofCenter(lower), Direction.NORTH, lower, false);
        require(placed.onUse(world, player, Hand.MAIN_HAND, hit).isAccepted(), "Door refused right-click");
        require(world.getBlockState(lower).get(DoorBlock.OPEN) && world.getBlockState(lower.up()).get(DoorBlock.OPEN),
                "Door halves did not open together");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayerEntity> playerLookup(PlayerManager manager) throws IllegalAccessException {
        for (var field : PlayerManager.class.getDeclaredFields()) {
            if (field.getGenericType() instanceof ParameterizedType type && type.getRawType() == Map.class
                    && type.getActualTypeArguments()[0] == UUID.class && type.getActualTypeArguments()[1] == ServerPlayerEntity.class) {
                field.setAccessible(true);
                return (Map<UUID, ServerPlayerEntity>)field.get(manager);
            }
        }
        throw new AssertionError("Player manager UUID lookup unavailable");
    }

    private static boolean place(ServerPlayerEntity player, ItemStack stack, BlockPos support) {
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false);
        return ((BlockItem)stack.getItem()).place(new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit)).isAccepted();
    }

    private static ItemStack compact(Block block) {
        ItemStack stack = new ItemStack(block);
        stack.getOrCreateSubNbt("BlockStateTag").putString("compact", "true");
        return stack;
    }

    private static Block block(String name) {
        Identifier id = new Identifier("bloodborne_dw", name);
        require(Registries.BLOCK.containsId(id), "Missing QA fixture " + id);
        return Registries.BLOCK.get(id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
