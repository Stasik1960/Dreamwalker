package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.util.math.Box;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Verifies each authored entity collider read from the shipped Studio project. */
public final class DiagonalRpCollisionCheck {
    private static final double EPSILON = 1e-7;
    private static final double[] SAMPLES = {.001, .1, .25, .5, .75, .9, .999};

    public static void main(String[] args) throws Exception {
        try (var jar = new ZipFile(Path.of(args[0]).toFile());
                var reader = new InputStreamReader(
                        jar.getInputStream(jar.getEntry("bloodborne_dw/studio-layouts-v22.json")), StandardCharsets.UTF_8)) {
            var project = JsonParser.parseReader(reader).getAsJsonObject();
            int models = 0, poses = 0, boxes = 0;
            for (var item : project.getAsJsonArray("models")) {
                var model = item.getAsJsonObject();
                if (!model.get("registryId").getAsString().startsWith("bloodborne_rp:")) continue;
                models++;
                var anchor = model.has("anchorCell") ? vector(model.getAsJsonArray("anchorCell"), 1) : new Vector3d();
                for (var state : model.getAsJsonArray("states")) {
                    poses++;
                    for (var element : state.getAsJsonObject().getAsJsonArray("colliders")) {
                        var collider = element.getAsJsonObject();
                        if (collider.get("mode").getAsString().equals("outline")) continue;
                        var center = vector(collider.getAsJsonArray("center"), 16);
                        var half = vector(collider.getAsJsonArray("size"), 32);
                        var box = new Box(center.x - half.x, center.y - half.y, center.z - half.z,
                                center.x + half.x, center.y + half.y, center.z + half.z);
                        for (int yaw : new int[]{45, 135, 225, 315}) {
                            for (double scale : new double[]{.25, 1, 8}) {
                                var matrix = new Matrix4d().translate(-143.5, 64, 780.25)
                                        .rotateY(Math.toRadians(yaw - 90)).scale(scale)
                                        .translate(-anchor.x, -anchor.y, -anchor.z);
                                checkBox(box, matrix);
                            }
                        }
                        boxes++;
                    }
                }
            }
            check(models > 0 && poses >= models && boxes > 0, "No authored RP colliders tested");
            for (int yaw : new int[]{0, 90, 180, 270}) {
                check(!DiagonalRpCollision.supports(new Matrix4d().rotateY(Math.toRadians(yaw))),
                        "Cardinal yaw must retain the original code path");
            }
            var diagonal = new Matrix4d().rotateY(Math.PI / 4);
            check(!DiagonalRpCollision.supports(new Matrix4d(diagonal).rotateZ(Math.PI / 4)),
                    "Pitched poses must retain the original code path");
            check(DiagonalRpCollision.boxes(List.of(), diagonal).isEmpty(), "Empty collider mode must stay empty");
            var wall = new Box(-2, 0, -.03125, 2, 2, .03125);
            var wallParts = DiagonalRpCollision.boxes(List.of(wall), diagonal);
            check(!contains(wallParts, 1, 1, 1), "Empty corner of diagonal wall must remain passable");
            check(contains(wallParts, 1, 1, -1), "Diagonal wall centerline must remain solid");
            System.out.printf("PASS %d RP models, %d poses, %d authored colliders at 4 diagonal yaws and 3 scales%n",
                    models, poses, boxes);
        }
    }

    private static void checkBox(Box box, Matrix4d transform) {
        check(DiagonalRpCollision.supports(transform), "Supported diagonal not recognized");
        List<Box> parts = DiagonalRpCollision.boxes(List.of(box), transform);
        check(parts.size() > 1 && parts.size() <= 128, "Expected a bounded set of strips");
        var bounds = transformedBounds(box, transform);
        for (Box part : parts) {
            check(part.minX >= bounds.minX - EPSILON && part.maxX <= bounds.maxX + EPSILON
                    && part.minY >= bounds.minY - EPSILON && part.maxY <= bounds.maxY + EPSILON
                    && part.minZ >= bounds.minZ - EPSILON && part.maxZ <= bounds.maxZ + EPSILON,
                    "A strip exceeded the bounds of the source box");
        }
        for (double x : SAMPLES) for (double z : SAMPLES) {
            var point = transform.transformPosition(new Vector3d(box.minX + x * (box.maxX - box.minX),
                    (box.minY + box.maxY) / 2, box.minZ + z * (box.maxZ - box.minZ)));
            check(contains(parts, point.x, point.y, point.z), "A point in the authored box lost collision");
        }
        double volume = parts.stream().mapToDouble(DiagonalRpCollisionCheck::volume).sum();
        check(volume < volume(bounds), "The old corner-filling box was not tightened");
    }

    private static Box transformedBounds(Box box, Matrix4d transform) {
        Box bounds = null;
        for (int i = 0; i < 8; i++) {
            var point = transform.transformPosition(new Vector3d((i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
            Box corner = new Box(point.x, point.y, point.z, point.x, point.y, point.z);
            bounds = bounds == null ? corner : bounds.union(corner);
        }
        return bounds;
    }

    private static double volume(Box box) {
        return (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
    }

    private static boolean contains(List<Box> boxes, double x, double y, double z) {
        return boxes.stream().anyMatch(box -> x >= box.minX - EPSILON && x <= box.maxX + EPSILON
                && y >= box.minY - EPSILON && y <= box.maxY + EPSILON
                && z >= box.minZ - EPSILON && z <= box.maxZ + EPSILON);
    }

    private static Vector3d vector(JsonArray values, double units) {
        return new Vector3d(values.get(0).getAsDouble() / units, values.get(1).getAsDouble() / units,
                values.get(2).getAsDouble() / units);
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
