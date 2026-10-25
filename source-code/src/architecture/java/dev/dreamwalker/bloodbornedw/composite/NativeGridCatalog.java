package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;

/** Immutable, coordinate-free cell data shared by every occurrence of a state. */
public final class NativeGridCatalog {
    public static final int BANK_SIZE = 128;
    public static final String DIRECTORY = "/assets/bloodborne_dw/native_grid/";
    public record Material(String sprite, String layer, String tintSource, String tintMode,
                           Map<String, String> tintProperties, BlockPos tintOffset) {}
    public record Optics(int opacity, int luminance, float ambientOcclusion,
                         boolean transparent, boolean sidedTransparency, boolean opaque,
                         boolean solidBlock, boolean suffocates, boolean blockVision,
                         boolean opaqueFullCube) {}
    public record Cell(int index, String key, String displayName, int mesh,
                       VoxelShape collision, VoxelShape outline, VoxelShape culling,
                       VoxelShape climbing, Optics optics, FluidState fluid,
                       String localFunction, int alt) {}
    public record Member(BlockPos offset, int cell) {}
    public record WholeObject(String key, String displayName, int baseCell, List<Member> cells) {}
    public record Membership(WholeObject object, BlockPos offset) {}
    private static List<Material> materials = List.of();
    private static List<Cell> cells = List.of();
    private static List<WholeObject> wholeObjects = List.of();
    private static List<Integer> quadCounts = List.of();
    private static Membership[] memberships = new Membership[0];
    private static boolean initialized;
    private NativeGridCatalog() {}

    public static synchronized void initialize() {
        if (initialized) return;
        try (InputStream stream = NativeGridCatalog.class.getResourceAsStream(DIRECTORY + "catalog.json")) {
            if (stream == null) { initialized = true; return; } // Source export starts with the unmodified baseline assets.
            JsonObject document;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                document = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (document.get("schemaVersion").getAsInt() != 1
                    || integer(document, "bankSize", BANK_SIZE) != BANK_SIZE)
                throw new IllegalArgumentException("Unsupported native grid schema/bank size");
            List<Material> loadedMaterials = new ArrayList<>();
            for (JsonElement raw : document.getAsJsonArray("materials")) {
                JsonObject row = raw.getAsJsonObject();
                String layer = row.get("layer").getAsString();
                if (!Set.of("solid", "cutout", "cutout_mipped", "translucent").contains(layer))
                    throw new IllegalArgumentException("Native grid material layer: " + layer);
                Map<String, String> tintProperties = new TreeMap<>();
                if (row.has("tintProperties")) row.getAsJsonObject("tintProperties").entrySet().forEach(entry -> tintProperties.put(entry.getKey(), entry.getValue().getAsString()));
                BlockPos tintOffset = BlockPos.ORIGIN;
                if (row.has("tintOffset")) {
                    JsonArray xyz = row.getAsJsonArray("tintOffset");
                    tintOffset = new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt());
                }
                String mode = string(row, "tintMode", "native");
                if (!mode.equals("native") && !mode.equals("remaining_grass")) throw new IllegalArgumentException("Unknown native grid tint mode");
                loadedMaterials.add(new Material(row.get("sprite").getAsString(), layer,
                        string(row, "tintSource", ""), mode, Map.copyOf(tintProperties), tintOffset));
            }
            List<VoxelShape> shapes = new ArrayList<>();
            for (JsonElement raw : document.getAsJsonArray("shapes")) shapes.add(shape(raw.getAsJsonArray()));
            List<Optics> loadedOptics = new ArrayList<>();
            for (JsonElement raw : document.getAsJsonArray("optics")) {
                JsonObject row = raw.getAsJsonObject();
                int opacity = row.get("opacity").getAsInt(), luminance = row.get("luminance").getAsInt();
                float ao = row.get("ambientOcclusion").getAsFloat();
                if (opacity < 0 || opacity > 15 || luminance < 0 || luminance > 15 || !Float.isFinite(ao) || ao < 0 || ao > 1)
                    throw new IllegalArgumentException("Invalid native grid optics");
                loadedOptics.add(new Optics(opacity, luminance, ao, bool(row, "transparent"),
                        bool(row, "sidedTransparency"), row.has("opaque") ? bool(row, "opaque") : bool(row, "opaqueFullCube"),
                        bool(row, "solidBlock"), bool(row, "suffocates"), bool(row, "blockVision"), bool(row, "opaqueFullCube")));
            }
            List<Integer> loadedCounts = new ArrayList<>();
            for (JsonElement raw : document.getAsJsonArray("meshes")) {
                int count = raw.getAsJsonObject().get("quadCount").getAsInt();
                if (count < 0) throw new IllegalArgumentException("Negative native grid quad count");
                loadedCounts.add(count);
            }
            List<Cell> loadedCells = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (JsonElement raw : document.getAsJsonArray("cells")) {
                JsonObject row = raw.getAsJsonObject();
                String key = row.get("key").getAsString();
                if (!keys.add(key)) throw new IllegalArgumentException("Duplicate native grid cell key");
                int mesh = row.get("mesh").getAsInt();
                if (mesh < 0 || mesh >= loadedCounts.size()) throw new IllegalArgumentException("Native grid mesh reference");
                String function = "";
                if (row.has("localFunction") && !row.get("localFunction").isJsonNull()) {
                    function = row.get("localFunction").isJsonPrimitive() ? row.get("localFunction").getAsString()
                            : row.getAsJsonObject("localFunction").get("kind").getAsString();
                    if (!function.equals("climb")) throw new IllegalArgumentException("Unknown native grid local function: " + function);
                }
                loadedCells.add(new Cell(loadedCells.size(), key, string(row, "displayName", "Городской фрагмент"), mesh,
                        shapes.get(row.get("collision").getAsInt()), shapes.get(row.get("outline").getAsInt()),
                        shapes.get(row.get("culling").getAsInt()), shapes.get(row.get("climbing").getAsInt()),
                        loadedOptics.get(row.get("optics").getAsInt()), fluid(row.get("fluid")), function, integer(row, "alt", -1)));
            }
            if (loadedCells.isEmpty()) throw new IllegalArgumentException("Native grid catalog has no cells");
            for (Cell cell : loadedCells) if (cell.alt() < -1 || cell.alt() >= loadedCells.size())
                throw new IllegalArgumentException("Native grid alt reference");
            Membership[] loadedMemberships = new Membership[loadedCells.size()];
            List<WholeObject> loadedObjects = new ArrayList<>();
            if (document.has("wholeObjects")) for (JsonElement raw : document.getAsJsonArray("wholeObjects")) {
                JsonObject row = raw.getAsJsonObject();
                int base = row.get("baseCell").getAsInt();
                List<Member> members = new ArrayList<>();
                Set<BlockPos> offsets = new HashSet<>();
                boolean hasBase = false;
                for (JsonElement entry : row.getAsJsonArray("cells")) {
                    JsonObject member = entry.getAsJsonObject();
                    JsonArray xyz = member.getAsJsonArray("offset");
                    BlockPos offset = new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt());
                    int type = member.get("cell").getAsInt();
                    if (type < 0 || type >= loadedCells.size() || !offsets.add(offset))
                        throw new IllegalArgumentException("Invalid whole-object member");
                    hasBase |= type == base && offset.equals(BlockPos.ORIGIN);
                    members.add(new Member(offset, type));
                }
                if (!hasBase || members.isEmpty()) throw new IllegalArgumentException("Whole object lacks its zero-offset base");
                WholeObject object = new WholeObject(row.get("key").getAsString(),
                        string(row, "displayName", "Статуя"), base, List.copyOf(members));
                for (Member member : members) {
                    if (loadedMemberships[member.cell()] != null)
                        throw new IllegalArgumentException("Whole-object cell must have a unique finite role");
                    loadedMemberships[member.cell()] = new Membership(object, member.offset());
                }
                loadedObjects.add(object);
            }
            materials = List.copyOf(loadedMaterials);
            quadCounts = List.copyOf(loadedCounts);
            cells = List.copyOf(loadedCells);
            memberships = loadedMemberships;
            wholeObjects = List.copyOf(loadedObjects);
            // Data is published before Block's StateManager asks for optical properties.
            NativeVisualLayout.initialize();
            NativeGridBlock.initialize((cells.size() + BANK_SIZE - 1) / BANK_SIZE);
            NativePhysicsBlock.initialize();
            NativeGridRuntime.initialize();
            initialized = true;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load compiled native city cells", failure);
        }
    }

    private static VoxelShape shape(JsonArray boxes) {
        VoxelShape result = VoxelShapes.empty();
        for (JsonElement raw : boxes) {
            JsonArray box = raw.getAsJsonArray();
            if (box.size() != 6) throw new IllegalArgumentException("Native shape box needs six coordinates");
            double[] v = new double[6];
            for (int i = 0; i < v.length; i++) {
                v[i] = box.get(i).getAsDouble();
                if (!Double.isFinite(v[i]) || v[i] < -1e-7 || v[i] > 1.0000001)
                    throw new IllegalArgumentException("Native shape is outside its cell");
            }
            if (v[0] >= v[3] || v[1] >= v[4] || v[2] >= v[5])
                throw new IllegalArgumentException("Native shape has empty/inverted box");
            result = VoxelShapes.union(result, VoxelShapes.cuboid(v[0], v[1], v[2], v[3], v[4], v[5]));
        }
        return result;
    }
    private static FluidState fluid(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) return Fluids.EMPTY.getDefaultState();
        JsonObject row = raw.getAsJsonObject();
        String id = row.get("id").getAsString();
        FlowableFluid fluid = switch (id) {
            case "minecraft:water", "minecraft:flowing_water" -> Fluids.WATER;
            case "minecraft:lava", "minecraft:flowing_lava" -> Fluids.LAVA;
            default -> throw new IllegalArgumentException("Unsupported compiled fluid: " + id);
        };
        int level = integer(row, "level", 8);
        if (level < 0 || level > 8) throw new IllegalArgumentException("Invalid compiled fluid level");
        return level == 0 || level == 8 ? fluid.getStill(bool(row, "falling")) : fluid.getFlowing(level, bool(row, "falling"));
    }
    private static boolean bool(JsonObject row, String key) { return row.has(key) && !row.get(key).isJsonNull() && row.get(key).getAsBoolean(); }
    private static int integer(JsonObject row, String key, int fallback) { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsInt() : fallback; }
    private static String string(JsonObject row, String key, String fallback) { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : fallback; }
    public static List<Cell> cells() { return cells; }
    public static List<Material> materials() { return materials; }
    public static List<Integer> quadCounts() { return quadCounts; }
    public static List<WholeObject> wholeObjects() { return wholeObjects; }
    public static Cell cell(int index) { return cells.get(index); }
    public static Membership membership(int index) { return memberships[index]; }
    public static Cell cell(BlockState state) { return ((NativeCellBlock) state.getBlock()).cell(state); }
    public static BlockState state(int index) {
        if (index < 0 || index >= cells.size()) throw new IllegalArgumentException("Native grid state index");
        int physics=NativeVisualLayout.physics(index);
        return physics>=0?NativePhysicsBlock.state(physics):NativeGridBlock.state(index);
    }
}
