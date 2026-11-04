package dev.dreamwalker.bloodbornedw.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBlock;
import dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualRepairs;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.visual.ObsidianBandAlt;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Finite profile table compiled against the pinned source and native geometry. */
public final class RgbProfiles {
    private record Paint(Identifier neutral, int[] manual, int[] regional) {
        int sample(int role, boolean stored, boolean horizontal) {
            return stored ? manual[role] : regional[role == 4 && horizontal ? 0 : role];
        }
    }
    public static final class Profile {
        private final Set<Integer> variants;
        private final Set<Identifier> models;
        private final Map<Identifier,String> assets;
        private final Map<Identifier,Paint> paints;
        private final Paint unambiguous;

        private Profile(Set<Integer> variants, Set<Identifier> models, Map<Identifier,String> assets, Map<Identifier,Paint> paints) {
            this.variants = Set.copyOf(variants);
            this.models = Set.copyOf(models);
            this.assets = Map.copyOf(assets);
            this.paints = Map.copyOf(paints);
            this.unambiguous = paints.size() == 1 ? paints.values().iterator().next() : null;
        }

        public Map<Identifier,String> assets() { return assets; }
        public boolean hasModel(Identifier model) { return models.contains(model); }
        public Identifier neutral(Identifier source) {
            Paint paint = paints.get(source);
            return paint == null ? null : paint.neutral;
        }
    }
    private record Policy(Map<Identifier,Profile> sources, Map<Integer,Profile> nativeMeshes, Map<Identifier,String> assets) {}
    private static final Policy POLICY = load();

    private RgbProfiles() {}
    public static void initialize() {}
    public static Map<Identifier,String> assets() { return POLICY.assets; }
    public static java.util.Collection<Profile> profiles() { return POLICY.sources.values(); }

    public static Profile source(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock)) return null;
        Profile profile = POLICY.sources.get(Registries.BLOCK.getId(state.getBlock()));
        return profile != null && profile.variants.contains(CompositeSpec.poseVariant(state)) ? profile : null;
    }

    public static Profile at(BlockRenderView world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof NativeGridBlock) return nativeCell(NativeGridCatalog.cell(state));
        if (state.getBlock() instanceof NativePhysicsBlock) {
            int index = NativeVisuals.index(world,pos);
            if (index < 0 || index >= NativeGridCatalog.cells().size()) return null;
            return nativeCell(NativeGridCatalog.cell(index));
        }
        if (state.getBlock() instanceof StaticArchitectureBlock) {
            var fragments = StaticArchitectureCatalog.recipe(state).fragments();
            if (fragments.size() != 1) return null;
            var pose = fragments.get(0).pose();
            Profile profile = source(pose.state());
            if (profile == null || pose.parts().isEmpty()) return null;
            for (var part : pose.parts()) if (!profile.hasModel(part.model())) return null;
            return profile;
        }
        return source(state);
    }

    private static Profile nativeCell(NativeGridCatalog.Cell cell) {
        Profile profile = POLICY.nativeMeshes.get(cell.mesh());
        if (profile == null || cell.localFunction() != null || NativeGridCatalog.membership(cell.index()) != null
                || POLICY.nativeMeshes.get(NativeVisualRepairs.mesh(cell.index())) != profile) return null;
        int alternate = cell.alt();
        if (alternate >= 0 && alternate != cell.index()) {
            if (alternate >= NativeGridCatalog.cells().size()
                    || POLICY.nativeMeshes.get(NativeVisualRepairs.mesh(alternate)) != profile) return null;
        }
        return profile;
    }

    /** Resolve an unambiguous logical owner without loading an absent chunk. */
    public static BlockPos paintTarget(ServerWorld world, BlockPos pos) {
        WorldChunk chunk = loaded(world,pos);
        if (chunk == null) return null;
        if (chunk.getBlockState(pos).getBlock() instanceof CompositeCellBlock) {
            ObjectInstance.Owner owner = null;
            for (var contribution : CompositeRuntime.contributions(world,pos)) {
                if (owner != null && !owner.equals(contribution.owner())) return null;
                owner = contribution.owner();
            }
            if (owner == null) return null;
            pos = CompositeData.pos(owner.root());
            chunk = loaded(world,pos);
            if (chunk == null || !(chunk.getBlockEntity(pos) instanceof CompositeBlockEntity entity)
                    || !owner.equals(entity.resident())) return null;
        }
        return at(world,pos,chunk.getBlockState(pos)) == null ? null : pos.toImmutable();
    }

    /** The collision outline cannot distinguish the stone and glazing of a mixed window. */
    public static int sample(ServerWorld world, BlockHitResult hit, BlockPos root, int role, boolean stored) {
        if (role < 0 || role > 4) return -1;
        WorldChunk chunk = loaded(world,root);
        if (chunk == null) return -1;
        BlockState state = chunk.getBlockState(root);
        Profile profile = at(world,root,state);
        if (profile == null || profile.unambiguous == null) return -1;
        if (!stored && ObsidianBandAlt.matches(state)) return ObsidianBandAlt.REFERENCE_RGB;
        return profile.unambiguous.sample(role,stored,hit.getSide().getAxis() == Direction.Axis.Y);
    }

    private static WorldChunk loaded(ServerWorld world, BlockPos pos) {
        var chunk = world.getChunkManager().getChunk(pos.getX() >> 4,pos.getZ() >> 4,ChunkStatus.FULL,false);
        return chunk instanceof WorldChunk ready ? ready : null;
    }

    private static Policy load() {
        try (var stream = RgbProfiles.class.getResourceAsStream("/bloodborne_dw/rgb-phase1.json")) {
            if (stream == null) throw new IllegalStateException("Missing RGB material table");
            var root = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalStateException("Unknown RGB material table");
            Map<Identifier,Profile> sources = new HashMap<>();
            Map<Integer,Profile> meshes = new HashMap<>();
            Map<Identifier,String> allAssets = new HashMap<>();
            for (var row : root.getAsJsonObject("profiles").entrySet()) {
                var definition = row.getValue().getAsJsonObject();
                Set<Integer> variants = new HashSet<>();
                for (var value : definition.getAsJsonArray("variants")) variants.add(value.getAsInt());
                Set<Identifier> models = new HashSet<>();
                for (String key : definition.getAsJsonObject("models").keySet()) models.add(new Identifier(key));
                Map<Identifier,String> assets = new HashMap<>();
                definition.getAsJsonObject("assets").entrySet().forEach(asset ->
                        assets.put(new Identifier(asset.getKey()),asset.getValue().getAsString()));
                allAssets.putAll(assets);
                Map<Identifier,Paint> paints = new HashMap<>();
                for (var rowPaint : definition.getAsJsonObject("paints").entrySet()) {
                    var paint = rowPaint.getValue().getAsJsonObject();
                    paints.put(new Identifier(rowPaint.getKey()),new Paint(new Identifier(paint.get("neutral").getAsString()),
                            tones(paint.getAsJsonArray("manual")),tones(paint.getAsJsonArray("regional"))));
                }
                var profile = new Profile(variants,models,assets,paints);
                sources.put(new Identifier(definition.get("block").getAsString()),profile);
                for (var mesh : definition.getAsJsonArray("nativeMeshes")) meshes.put(mesh.getAsInt(),profile);
            }
            return new Policy(Map.copyOf(sources),Map.copyOf(meshes),Map.copyOf(allAssets));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load RGB material table",ex);
        }
    }

    private static int[] tones(JsonArray values) {
        if (values.size() != 5) throw new IllegalStateException("Invalid RGB reference tones");
        int[] result = new int[5];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsInt();
        return result;
    }
}
