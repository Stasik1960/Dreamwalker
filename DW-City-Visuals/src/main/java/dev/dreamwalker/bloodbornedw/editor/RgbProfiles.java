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
import java.util.EnumMap;
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
    private record UvZone(float minU, float minV, float maxU, float maxV) {
        private static final float EPSILON = 0.0002f;
        boolean covers(float u0, float v0, float u1, float v1) {
            return u0 >= minU - EPSILON && v0 >= minV - EPSILON
                    && u1 <= maxU + EPSILON && v1 <= maxV + EPSILON;
        }
    }
    public static final class Paint {
        private final Identifier neutral;
        private final int[] manual, regional;
        private final UvZone[] zones;

        private Paint(Identifier neutral, int[] manual, int[] regional, UvZone[] zones) {
            this.neutral = neutral;
            this.manual = manual;
            this.regional = regional;
            this.zones = zones;
        }

        public Identifier neutral() { return neutral; }
        public boolean whole() { return zones.length == 0; }
        public boolean covers(float u0, float v0, float u1, float v1) {
            if (whole()) return true;
            for (UvZone zone : zones) if (zone.covers(u0, v0, u1, v1)) return true;
            return false;
        }
        int sample(int role, boolean stored, boolean horizontal) {
            return stored ? manual[role] : regional[role == 4 && horizontal ? 0 : role];
        }
    }
    public static final class Profile {
        private final Set<Identifier> models;
        private final Set<Identifier> assets;
        private final Map<Identifier,Paint> paints;
        private final Paint unambiguous;
        private final Map<Direction,Paint> sampleFaces;

        private Profile(Set<Identifier> models, Set<Identifier> assets, Map<Identifier,Paint> paints,
                        boolean sampleUnambiguous, Map<Direction,Paint> sampleFaces) {
            this.models = Set.copyOf(models);
            this.assets = Set.copyOf(assets);
            this.paints = Map.copyOf(paints);
            this.unambiguous = sampleUnambiguous && paints.size() == 1 ? paints.values().iterator().next() : null;
            this.sampleFaces = Map.copyOf(sampleFaces);
        }

        public Set<Identifier> assets() { return assets; }
        public boolean hasModel(Identifier model) { return models.contains(model); }
        public Paint paint(Identifier source) { return paints.get(source); }
    }
    private record NativeBinding(Profile profile, int mesh) {}
    private record Policy(Map<Identifier,Map<Integer,Profile>> sources, Set<Profile> profiles,
                          Map<Integer,Profile> nativeMeshes, Map<Integer,NativeBinding> nativeCells,
                          Map<Identifier,String> assets) {}
    private static final Policy POLICY = load();

    private RgbProfiles() {}
    public static void initialize() {}
    public static Map<Identifier,String> assets() { return POLICY.assets; }
    public static Set<Profile> profiles() { return POLICY.profiles; }

    public static Profile source(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock)) return null;
        Map<Integer,Profile> variants = POLICY.sources.get(Registries.BLOCK.getId(state.getBlock()));
        return variants == null ? null : variants.get(CompositeSpec.poseVariant(state));
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
            var fragment = fragments.get(0);
            if (fragment.origin().x() != 0 || fragment.origin().y() != 0 || fragment.origin().z() != 0) return null;
            var pose = fragment.pose();
            Profile profile = source(pose.state());
            if (profile == null || pose.parts().isEmpty()) return null;
            for (var part : pose.parts()) if (!profile.hasModel(part.model())) return null;
            return profile;
        }
        return source(state);
    }

    private static Profile nativeCell(NativeGridCatalog.Cell cell) {
        String function = cell.localFunction();
        if (function != null && !function.isEmpty() || cell.fluid() != null && !cell.fluid().isEmpty()
                || NativeGridCatalog.membership(cell.index()) != null) return null;
        Profile profile = nativeGeometry(cell.index(), cell.mesh());
        if (profile == null) return null;
        int alternate = cell.alt();
        if (alternate >= 0 && alternate != cell.index()) {
            if (alternate >= NativeGridCatalog.cells().size()) return null;
            var alternateCell = NativeGridCatalog.cell(alternate);
            if (nativeGeometry(alternate, alternateCell.mesh()) != profile) return null;
        }
        return profile;
    }

    private static Profile nativeGeometry(int cell, int mesh) {
        NativeBinding binding = POLICY.nativeCells.get(cell);
        int effectiveMesh = NativeVisualRepairs.mesh(cell);
        if (binding != null) return effectiveMesh == binding.mesh ? binding.profile : null;
        Profile profile = POLICY.nativeMeshes.get(mesh);
        return profile != null && POLICY.nativeMeshes.get(effectiveMesh) == profile ? profile : null;
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
        } else if (chunk.getBlockState(pos).getBlock() instanceof StaticArchitectureBlock) {
            var fragments = StaticArchitectureCatalog.recipe(chunk.getBlockState(pos)).fragments();
            if (fragments.size() != 1) return null;
            var fragment = fragments.get(0);
            var origin = fragment.origin();
            pos = pos.add(origin.x(), origin.y(), origin.z());
            chunk = loaded(world,pos);
            if (chunk == null) return null;
            BlockState rootState = chunk.getBlockState(pos);
            if (!(rootState.getBlock() instanceof StaticArchitectureBlock)
                    || !StaticArchitectureCatalog.matchesRoot(rootState, fragment.pose())) return null;
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
        if (profile == null) return -1;
        boolean horizontal = hit.getSide().getAxis() == Direction.Axis.Y;
        Paint paint = profile.unambiguous;
        if (paint == null && state.getBlock() instanceof CompositeRootBlock && root.equals(hit.getBlockPos())
                && (horizontal || state.contains(CompositeRootBlock.ROTATION) && state.get(CompositeRootBlock.ROTATION) == 0)) {
            paint = profile.sampleFaces.get(hit.getSide());
        }
        if (paint == null) return -1;
        if (!stored && ObsidianBandAlt.matches(state)) return ObsidianBandAlt.REFERENCE_RGB;
        return paint.sample(role,stored,horizontal);
    }

    private static WorldChunk loaded(ServerWorld world, BlockPos pos) {
        var chunk = world.getChunkManager().getChunk(pos.getX() >> 4,pos.getZ() >> 4,ChunkStatus.FULL,false);
        return chunk instanceof WorldChunk ready ? ready : null;
    }

    private static Policy load() {
        try (var stream = RgbProfiles.class.getResourceAsStream("/bloodborne_dw/rgb-phase1.json")) {
            if (stream == null) throw new IllegalStateException("Missing RGB material table");
            var root = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 2) throw new IllegalStateException("Unknown RGB material table");
            Map<Identifier,Map<Integer,Profile>> sources = new HashMap<>();
            Set<Profile> profiles = new HashSet<>();
            Map<Integer,Profile> meshes = new HashMap<>();
            Map<Integer,NativeBinding> cells = new HashMap<>();
            Map<Identifier,String> allAssets = new HashMap<>();
            Map<String,Identifier> ids = new HashMap<>();
            root.getAsJsonObject("assets").entrySet().forEach(asset ->
                    allAssets.put(ids.computeIfAbsent(asset.getKey(),Identifier::new),asset.getValue().getAsString()));
            for (var row : root.getAsJsonObject("profiles").entrySet()) {
                var definition = row.getValue().getAsJsonObject();
                Set<Integer> variants = new HashSet<>();
                for (var value : definition.getAsJsonArray("variants")) variants.add(value.getAsInt());
                Set<Identifier> models = new HashSet<>();
                for (String key : definition.getAsJsonObject("models").keySet())
                    models.add(ids.computeIfAbsent(key,Identifier::new));
                Set<Identifier> assets = new HashSet<>();
                for (var asset : definition.getAsJsonArray("assets")) {
                    Identifier id = ids.get(asset.getAsString());
                    if (id == null || !allAssets.containsKey(id))
                        throw new IllegalStateException("Unbound RGB asset " + asset);
                    assets.add(id);
                }
                Map<Identifier,Paint> paints = new HashMap<>();
                for (var rowPaint : definition.getAsJsonObject("paints").entrySet()) {
                    var paint = rowPaint.getValue().getAsJsonObject();
                    paints.put(new Identifier(rowPaint.getKey()),new Paint(new Identifier(paint.get("neutral").getAsString()),
                            tones(paint.getAsJsonArray("manual")),tones(paint.getAsJsonArray("regional")),
                            zones(paint.getAsJsonArray("zones"))));
                }
                Map<Direction,Paint> sampleFaces = new EnumMap<>(Direction.class);
                if (definition.has("sampleFaces")) {
                    for (var face : definition.getAsJsonObject("sampleFaces").entrySet()) {
                        Direction direction = Direction.byName(face.getKey());
                        Paint paint = paints.get(new Identifier(face.getValue().getAsString()));
                        if (direction == null || paint == null)
                            throw new IllegalStateException("Unbound RGB sample face " + face);
                        sampleFaces.put(direction,paint);
                    }
                }
                var profile = new Profile(models,assets,paints,
                        !definition.has("sampleUnambiguous") || definition.get("sampleUnambiguous").getAsBoolean(),sampleFaces);
                profiles.add(profile);
                var byVariant = sources.computeIfAbsent(new Identifier(definition.get("block").getAsString()),
                        unused -> new HashMap<>());
                for (int variant : variants) if (byVariant.putIfAbsent(variant, profile) != null)
                    throw new IllegalStateException("Duplicate RGB block variant " + row.getKey());
                for (var mesh : definition.getAsJsonArray("nativeMeshes"))
                    if (meshes.putIfAbsent(mesh.getAsInt(),profile) != null)
                        throw new IllegalStateException("Duplicate RGB native mesh " + mesh);
                if (definition.has("nativeCells")) {
                    for (var value : definition.getAsJsonArray("nativeCells")) {
                        var pair = value.getAsJsonArray();
                        if (pair.size() != 2 || pair.get(0).getAsInt() < 0 || pair.get(1).getAsInt() < 0
                                || cells.putIfAbsent(pair.get(0).getAsInt(),new NativeBinding(profile,pair.get(1).getAsInt())) != null)
                            throw new IllegalStateException("Invalid or duplicate RGB native cell " + value);
                    }
                }
            }
            sources.replaceAll((block, variants) -> Map.copyOf(variants));
            return new Policy(Map.copyOf(sources),Set.copyOf(profiles),Map.copyOf(meshes),Map.copyOf(cells),Map.copyOf(allAssets));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load RGB material table",ex);
        }
    }

    private static UvZone[] zones(JsonArray values) {
        if (values == null) return new UvZone[0];
        if (values.isEmpty()) throw new IllegalStateException("An RGB zone list must not be empty");
        UvZone[] result = new UvZone[values.size()];
        for (int i = 0; i < result.length; i++) {
            var zone = values.get(i).getAsJsonArray();
            if (zone.size() != 4) throw new IllegalStateException("Invalid RGB face zone");
            float u0 = zone.get(0).getAsFloat(), v0 = zone.get(1).getAsFloat();
            float u1 = zone.get(2).getAsFloat(), v1 = zone.get(3).getAsFloat();
            if (!(u0 >= 0 && v0 >= 0 && u1 <= 16 && v1 <= 16 && u0 <= u1 && v0 <= v1))
                throw new IllegalStateException("Invalid RGB face bounds");
            result[i] = new UvZone(u0, v0, u1, v1);
        }
        return result;
    }

    private static int[] tones(JsonArray values) {
        if (values.size() != 5) throw new IllegalStateException("Invalid RGB reference tones");
        int[] result = new int[5];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsInt();
        return result;
    }
}
