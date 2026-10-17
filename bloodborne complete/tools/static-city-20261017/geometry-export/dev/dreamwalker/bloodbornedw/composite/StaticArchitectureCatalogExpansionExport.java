package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

/**
 * One-shot, world-free geometry exporter for catalog expansion.
 *
 * <p>This entrypoint must be listed after {@code DreamwalkerBb} in the same
 * {@code main} entrypoint array. DreamwalkerBb registers CompositeArchitecture;
 * this initializer then reads only a finite request, writes its report, and
 * exits before client or dedicated-server world construction.</p>
 */
public final class StaticArchitectureCatalogExpansionExport implements ModInitializer {
    private static final String ENABLE = "dw.staticGeometry.export";
    private static final String INPUT = "dw.staticGeometry.input";
    private static final String OUTPUT = "dw.staticGeometry.output";

    private static JsonArray vec(double... values) {
        JsonArray result = new JsonArray();
        for (double value : values) result.add(value);
        return result;
    }

    private static JsonArray boxes(List<Box> values) {
        JsonArray result = new JsonArray();
        for (Box value : values) {
            result.add(vec(value.minX(), value.minY(), value.minZ(),
                    value.maxX(), value.maxY(), value.maxZ()));
        }
        return result;
    }

    private static JsonArray point(CompositeSpec.Vec value) {
        return vec(value.x(), value.y(), value.z());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static JsonObject issue(JsonObject source, String disposition, String reason, Exception failure) {
        JsonObject result = new JsonObject();
        if (source.has("key")) result.add("key", source.get("key"));
        if (source.has("registryId")) result.add("registryId", source.get("registryId"));
        result.addProperty("disposition", disposition);
        result.addProperty("reason", reason);
        if (failure != null) {
            result.addProperty("exception", failure.getClass().getName());
            result.addProperty("message", String.valueOf(failure.getMessage()));
        }
        return result;
    }

    private static List<String> unsupported(CompositeRootBlock block, BlockState state) {
        List<String> reasons = new ArrayList<>();
        if (block instanceof ImportedStairBlock) reasons.add("IMPORTED_STAIR");
        if (block instanceof ImportedLadderBlock) reasons.add("IMPORTED_LADDER");
        if (block instanceof ThinWindowRootBlock) reasons.add("THIN_WINDOW");
        if (block.spec.openable) reasons.add("OPENABLE_SPEC");
        if (block.spec.functionalDoorPhysics) reasons.add("FUNCTIONAL_DOOR_PHYSICS");
        if (block.spec.importedClimbingBounds != null) reasons.add("IMPORTED_CLIMBING_BOUNDS");
        if (block.spec.requiredSupport) reasons.add("REQUIRED_SUPPORT");
        if (state.get(CompositeRootBlock.OPEN)) reasons.add("OPEN_STATE");
        if (state.get(CompositeRootBlock.PROFILE) != CompositeRootBlock.Profile.BASE) reasons.add("NON_BASE_PROFILE");
        return reasons;
    }

    private static JsonObject exportPose(JsonObject source, CompositeRootBlock block,
                                         BlockState state, NbtCompound payload) {
        JsonObject row = source.deepCopy();
        Map<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell,
                dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint> footprint =
                CompositeSourceShift.footprint(block.spec, state, payload);

        JsonArray cells = new JsonArray();
        for (var item : footprint.entrySet()) {
            JsonObject part = new JsonObject();
            var cell = item.getKey();
            part.add("cell", vec(cell.x(), cell.y(), cell.z()));
            part.add("collision", boxes(item.getValue().collision()));
            part.add("selection", boxes(item.getValue().selection()));
            cells.add(part);
        }
        row.add("footprint", cells);

        JsonArray outline = new JsonArray();
        if (block.spec.simplePhysicalCollision) {
            for (var item : ImportedSelectionShapes.footprint(block, state, payload).entrySet()) {
                JsonObject part = new JsonObject();
                var cell = item.getKey();
                part.add("cell", vec(cell.x(), cell.y(), cell.z()));
                part.add("selection", boxes(item.getValue()));
                outline.add(part);
            }
        } else {
            for (var item : footprint.entrySet()) {
                JsonObject part = new JsonObject();
                var cell = item.getKey();
                part.add("cell", vec(cell.x(), cell.y(), cell.z()));
                part.add("selection", boxes(item.getValue().selection()));
                outline.add(part);
            }
        }
        row.add("outline", outline);

        JsonArray parts = new JsonArray();
        for (var selected : ImportedStairLegacyArt.selectedParts(block, state, payload)) {
            JsonObject part = new JsonObject();
            part.addProperty("model", selected.model().toString());
            part.add("offset", point(selected.offset()));
            part.addProperty("yaw", selected.yaw());
            part.addProperty("pitch", selected.pitch());
            part.add("pivot", point(selected.pivot()));
            part.addProperty("extraYaw", selected.extraYaw());
            part.add("extraPivot", point(selected.extraPivot()));
            parts.add(part);
        }
        row.add("parts", parts);

        var shift = CompositeSourceShift.world(state, payload);
        row.add("worldShift", vec(shift.x(), shift.y(), shift.z()));
        row.addProperty("mountY", payload.getDouble("MountY")
                + payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY));
        row.addProperty("opacity", state.getOpacity(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
        row.addProperty("luminance", state.getLuminance());
        row.addProperty("renderCarrier", ImportedSourceLightingV127.renderCarrier(block.spec, state).toString());
        row.addProperty("simplePhysicalCollision", block.spec.simplePhysicalCollision);
        row.addProperty("openable", block.spec.openable);
        row.addProperty("requiredSupport", block.spec.requiredSupport);
        row.addProperty("functionalDoorPhysics", block.spec.functionalDoorPhysics);
        row.addProperty("fluidEmpty", state.getFluidState().isEmpty());
        return row;
    }

    @Override
    public void onInitialize() {
        if (!Boolean.parseBoolean(System.getProperty(ENABLE, "false"))) return;

        Path output = null;
        int exitCode = 2;
        try {
            Path input = Path.of(System.getProperty(INPUT)).toAbsolutePath().normalize();
            output = Path.of(System.getProperty(OUTPUT)).toAbsolutePath().normalize();
            if (!Files.isRegularFile(input)) throw new IllegalArgumentException("Geometry request is not a regular file: " + input);
            if (Files.exists(output)) throw new IllegalArgumentException("Geometry output already exists: " + output);
            if (output.getParent() == null || !Files.isDirectory(output.getParent()))
                throw new IllegalArgumentException("Geometry output parent is not a directory: " + output);

            byte[] inputBytes = Files.readAllBytes(input);
            JsonObject request = JsonParser.parseString(new String(inputBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray requested = request.getAsJsonArray("poses");
            if (requested == null) throw new IllegalArgumentException("Geometry request has no poses array");

            JsonArray poses = new JsonArray();
            JsonArray excluded = new JsonArray();
            int unsupportedCount = 0;
            int failureCount = 0;
            for (var raw : requested) {
                JsonObject source = raw.getAsJsonObject();
                try {
                    Map<String, String> properties = new TreeMap<>();
                    source.getAsJsonObject("properties").entrySet()
                            .forEach(value -> properties.put(value.getKey(), value.getValue().getAsString()));
                    BlockState state = CompositeData.state(new CellSnapshot.BlockData(
                            source.get("registryId").getAsString(), properties, new byte[0]));
                    if (!(state.getBlock() instanceof CompositeRootBlock block)) {
                        excluded.add(issue(source, "EXCLUDED_UNSUPPORTED", "NON_COMPOSITE_ROOT", null));
                        unsupportedCount++;
                        continue;
                    }

                    List<String> unsupported = unsupported(block, state);
                    if (!unsupported.isEmpty()) {
                        excluded.add(issue(source, "EXCLUDED_UNSUPPORTED", String.join("+", unsupported), null));
                        unsupportedCount++;
                        continue;
                    }

                    NbtCompound payload = StringNbtReader.parse(source.get("payload").toString());
                    poses.add(exportPose(source, block, state, payload));
                } catch (Exception failure) {
                    excluded.add(issue(source, "FAILED_POSE", "POSE_EXPORT_EXCEPTION", failure));
                    failureCount++;
                }
            }

            JsonObject report = new JsonObject();
            report.addProperty("status", failureCount == 0
                    ? "PASS_NATIVE_PREPARED_FINITE_GEOMETRY_NOT_WORLD_ANALYSIS"
                    : "FAIL_SOME_REQUESTED_POSES");
            report.addProperty("inputSha256", sha256(inputBytes));
            report.addProperty("requestedPoseCount", requested.size());
            report.add("poses", poses);
            report.addProperty("poseCount", poses.size());
            report.add("excluded", excluded);
            report.addProperty("excludedCount", excluded.size());
            report.addProperty("unsupportedCount", unsupportedCount);
            report.addProperty("failureCount", failureCount);
            report.addProperty("heavySourceWorldLoaded", false);
            report.addProperty("worldCreatedOrOpened", false);
            report.addProperty("entrypointCompletedBeforeWorldBootstrap", true);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            exitCode = failureCount == 0 ? 0 : 2;
        } catch (Exception fatal) {
            if (output != null && !Files.exists(output) && output.getParent() != null && Files.isDirectory(output.getParent())) {
                try {
                    JsonObject report = new JsonObject();
                    report.addProperty("status", "FAIL_EXPORTER_FATAL");
                    report.addProperty("exception", fatal.getClass().getName());
                    report.addProperty("message", String.valueOf(fatal.getMessage()));
                    report.addProperty("heavySourceWorldLoaded", false);
                    report.addProperty("worldCreatedOrOpened", false);
                    Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (Exception ignored) {
                    // The original fatal error remains the process result.
                }
            }
        }
        System.exit(exitCode);
    }
}
