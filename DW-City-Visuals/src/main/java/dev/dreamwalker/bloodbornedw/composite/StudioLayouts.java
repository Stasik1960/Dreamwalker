package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Exact, shared selectors from the reviewed Collision Studio export. */
public final class StudioLayouts {
    public record Seat(Vec3d contact, Vec3d forward) {}
    public record Pose(Vec3d offset, List<Box> players, List<Box> blocks, List<Box> outline, List<Seat> seats) {}
    public record Model(Vec3d anchor, Map<Integer, Pose> poses, Map<Boolean, Pose> dogVisibility) {
        public Pose pose(int variant, boolean open) {
            Pose exact = poses.get(variant * 2 + (open ? 1 : 0));
            return exact != null ? exact : poses.get(-1);
        }

        public Pose pose(RpObjectEntity object) {
            if (object.supportsDogVisibility() && !dogVisibility.isEmpty()) {
                Pose exact = dogVisibility.get(object.dogsVisible());
                if (exact != null) return exact;
            }
            return pose(0, object.isOpen());
        }
    }

    private static final Map<String, Model> MODELS = load();
    private static final Map<CompositeSpec, Model> COMPOSITES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<List<CompositeSpec.Part>, Map<Vec3d, List<CompositeSpec.Part>>> ART = new IdentityHashMap<>();
    private static final Model UNEDITED = new Model(Vec3d.ZERO, Map.of(), Map.of());

    private StudioLayouts() {}

    public static Model model(String id) { return MODELS.get(id); }

    public static Model model(CompositeSpec spec) {
        Model model = COMPOSITES.computeIfAbsent(spec, key -> MODELS.getOrDefault(key.id.toString(), UNEDITED));
        return model == UNEDITED ? null : model;
    }

    public static Pose pose(Model model, BlockState state) {
        return model.pose(CompositeSpec.poseVariant(state), state.get(CompositeRootBlock.OPEN));
    }

    public static Model model(RpObjectEntity object) {
        return MODELS.get("bloodborne_rp:" + object.assetId());
    }

    public static Pose pose(RpObjectEntity object) {
        Model model = model(object);
        return model == null ? null : model.pose(object);
    }

    public static List<CompositeSpec.Part> art(List<CompositeSpec.Part> original, Model model, Pose pose, boolean placedStatic) {
        Vec3d offset = pose == null ? Vec3d.ZERO : pose.offset;
        if (!placedStatic) offset = offset.subtract(model.anchor);
        if (offset.equals(Vec3d.ZERO)) return original;
        synchronized (ART) {
            return ART.computeIfAbsent(original, key -> new HashMap<>())
                    .computeIfAbsent(offset, delta -> translate(original, delta));
        }
    }

    private static List<CompositeSpec.Part> translate(List<CompositeSpec.Part> original, Vec3d delta) {
        List<CompositeSpec.Part> parts = new ArrayList<>(original.size());
        for (var part : original) {
            var offset = part.offset();
            parts.add(new CompositeSpec.Part(part.model(), part.altModel(),
                    new CompositeSpec.Vec(offset.x() + 16 * delta.x, offset.y() + 16 * delta.y, offset.z() + 16 * delta.z),
                    part.yaw(), part.pitch(), part.pivot(), part.extraYaw(), part.extraPivot()));
        }
        return List.copyOf(parts);
    }

    private static Map<String, Model> load() {
        try (var input = StudioLayouts.class.getResourceAsStream("/bloodborne_dw/studio-layouts-v22.json")) {
            if (input == null) throw new IOException("Missing packaged Collision Studio project");
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (data.get("schemaVersion").getAsInt() != 4 || data.get("unitsPerBlock").getAsInt() != 16)
                throw new IOException("Unsupported Collision Studio project");
            Map<String, Model> models = new HashMap<>();
            for (var item : data.getAsJsonArray("models")) {
                var model = item.getAsJsonObject();
                String registry = model.get("registryId").getAsString();
                var anchor = model.has("anchorCell") ? vector(model.getAsJsonArray("anchorCell"), 1) : Vec3d.ZERO;
                Map<Integer, Pose> poses = new HashMap<>();
                Map<Boolean, Pose> dogVisibility = new HashMap<>();
                for (var element : model.getAsJsonArray("states")) {
                    var state = element.getAsJsonObject();
                    var selector = state.getAsJsonObject("selector");
                    if (selector.has("DogsVisible")) {
                        if (!registry.startsWith("bloodborne_rp:") || selector.size() != 1
                                || dogVisibility.putIfAbsent(selector.get("DogsVisible").getAsBoolean(), readPose(state)) != null)
                            throw new IOException("Invalid Studio dog visibility pose for " + registry + " " + selector);
                        continue;
                    }
                    int key = selector.has("variant") ? 2 * selector.get("variant").getAsInt() : 0;
                    var open = selector.has("open") ? selector.get("open") : selector.get("Open");
                    key = open == null ? -1 : key + (open.getAsBoolean() ? 1 : 0);
                    if (poses.putIfAbsent(key, readPose(state)) != null)
                        throw new IOException("Repeated Studio pose for " + model.get("registryId") + " " + selector);
                }
                if (models.putIfAbsent(registry, new Model(anchor, Map.copyOf(poses), Map.copyOf(dogVisibility))) != null)
                    throw new IOException("Repeated Studio model");
            }
            return Map.copyOf(models);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load reviewed Collision Studio layouts", failure);
        }
    }

    private static Pose readPose(JsonObject state) {
        List<Box> players = new ArrayList<>(), blocks = new ArrayList<>(), outline = new ArrayList<>();
        for (var item : state.getAsJsonArray("colliders")) {
            var box = item.getAsJsonObject();
            if (!vector(box.getAsJsonArray("rotation"), 1).equals(Vec3d.ZERO))
                throw new IllegalArgumentException("This packaged layout requires axis-aligned authoring volumes");
            Vec3d center = vector(box.getAsJsonArray("center"), 16);
            Vec3d half = vector(box.getAsJsonArray("size"), 32);
            Box volume = new Box(center.subtract(half), center.add(half));
            switch (box.get("mode").getAsString()) {
                case "both" -> { players.add(volume); blocks.add(volume); }
                case "players" -> players.add(volume);
                case "blocks" -> blocks.add(volume);
                case "outline" -> outline.add(volume);
                default -> throw new IllegalArgumentException("Unknown Studio collider mode");
            }
        }
        List<Seat> seats = new ArrayList<>();
        for (var item : state.getAsJsonArray("seats")) {
            var seat = item.getAsJsonObject();
            Vec3d rotation = vector(seat.getAsJsonArray("rotation"), 1);
            Vec3d forward = vector(seat.getAsJsonArray("front"), 1);
            var direction = new org.joml.Matrix3d().rotateZYX(Math.toRadians(rotation.z), Math.toRadians(rotation.y),
                    Math.toRadians(rotation.x)).transform(new org.joml.Vector3d(forward.x, forward.y, forward.z));
            seats.add(new Seat(vector(seat.getAsJsonArray("surfaceCenter"), 16), new Vec3d(direction.x, direction.y, direction.z)));
        }
        return new Pose(vector(state.getAsJsonArray("modelOffset"), 16), List.copyOf(players), List.copyOf(blocks),
                List.copyOf(outline), List.copyOf(seats));
    }

    private static Vec3d vector(JsonArray values, double units) {
        if (values.size() != 3) throw new IllegalArgumentException("Expected a Studio vector");
        double x = values.get(0).getAsDouble() / units, y = values.get(1).getAsDouble() / units, z = values.get(2).getAsDouble() / units;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) > 256)
            throw new IllegalArgumentException("Studio vector outside runtime range");
        return new Vec3d(x == 0 ? 0 : x, y == 0 ? 0 : y, z == 0 ? 0 : z);
    }
}
