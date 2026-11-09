package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.studiomixin.StudioRasterInvoker;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** One bounded, shared result for each exact art state and saved source transform. */
public final class StudioFootprints {
    private static final double EPS = 1e-8;
    private static final int CACHE_LIMIT = 2048;
    private static final Map<BlockData, Geometry> DYNAMIC = new ConcurrentHashMap<>();
    private static final Map<StaticArchitectureCatalog.Pose, Geometry> STATIC = new ConcurrentHashMap<>();

    public record Geometry(Map<Cell, Footprint> storage, Map<Cell, List<Box>> players,
            Map<Cell, List<Box>> blocks, Map<Cell, List<Box>> outline, Map<Cell, Footprint> stock,
            List<BenchSeating.Point> seats) {}
    public record PhysicsContext(BlockView view, boolean player) {}

    private StudioFootprints() {}

    public static boolean edited(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock block)) return false;
        var model = StudioLayouts.model(block.spec);
        return model != null && (!model.anchor().equals(Vec3d.ZERO) || StudioLayouts.pose(model, state) != null);
    }

    public static Geometry dynamic(BlockData data) {
        if (StudioLayouts.model(data.blockId()) == null) return null;
        var cached = DYNAMIC.get(data);
        if (cached != null) return cached;
        BlockState state = CompositeData.state(data);
        if (!edited(state)) return null;
        Geometry geometry = compute((CompositeRootBlock) state.getBlock(), state,
                CompositeData.nbt(data.blockEntityNbt()), false, null);
        if (DYNAMIC.size() >= CACHE_LIMIT) DYNAMIC.clear();
        Geometry existing = DYNAMIC.putIfAbsent(data, geometry);
        return existing == null ? geometry : existing;
    }

    public static Geometry staticPose(StaticArchitectureCatalog.Pose pose) {
        var model = StudioLayouts.model(pose.block().spec);
        if (model == null || StudioLayouts.pose(model, pose.state()) == null) return null;
        return STATIC.computeIfAbsent(pose, key -> compute(key.block(), key.state(), key.payload(), true, key.footprint()));
    }

    private static Geometry compute(CompositeRootBlock block, BlockState state, NbtCompound payload,
            boolean placedStatic, Map<Cell, Footprint> knownStock) {
        var model = StudioLayouts.model(block.spec);
        var pose = StudioLayouts.pose(model, state);
        Vec3d anchor = placedStatic ? Vec3d.ZERO : model.anchor();
        NbtCompound stockPayload = payload;
        if (!anchor.equals(Vec3d.ZERO)) {
            stockPayload = payload.copy();
            var shift = CompositeSourceShift.local(payload);
            NbtList position = new NbtList();
            position.add(NbtDouble.of(shift.x() - anchor.x));
            position.add(NbtDouble.of(shift.y() - anchor.y));
            position.add(NbtDouble.of(shift.z() - anchor.z));
            stockPayload.put("SourceShift", position);
        }
        var stock = knownStock == null ? CompositeSourceShift.footprint(block.spec, state, stockPayload) : knownStock;
        if (pose == null) {
            return new Geometry(stock, null, null, null, stock, List.of());
        }
        var shift = CompositeSourceShift.world(state, payload);
        var translation = new Vec3d(shift.x(), shift.y() + payload.getDouble("MountY") + payload.getDouble(VerticalMount.KEY), shift.z());
        int yaw = state.get(CompositeRootBlock.ROTATION);
        Matrix4d mount = new Matrix4d();
        var plane = ThinWindowRootBlock.mount(state);
        if (plane != ThinWindowRootBlock.Mount.VERTICAL) {
            mount.translate(.5, .5, .5).rotateX(plane == ThinWindowRootBlock.Mount.FLOOR ? Math.PI / 2 : -Math.PI / 2)
                    .rotateY(Math.toRadians(block.spec.variant(state).mountAlignmentYaw())).translate(-.5, -.5, -.5);
        }
        var players = clipped(pose.players(), anchor, mount, yaw, translation);
        var blocks = clipped(pose.blocks(), anchor, mount, yaw, translation);
        var outline = clipped(pose.outline(), anchor, mount, yaw, translation);
        var rotation = new Matrix4d().translate(translation.x + .5, translation.y + .5, translation.z + .5)
                .rotateY(Math.toRadians(-yaw * 45)).translate(-.5, -.5, -.5).mul(mount);
        List<BenchSeating.Point> seats = new ArrayList<>();
        TreeSet<Cell> cells = new TreeSet<>(stock.keySet());
        cells.addAll(players.keySet());
        cells.addAll(blocks.keySet());
        cells.addAll(outline.keySet());
        for (var seat : pose.seats()) {
            var point = seat.contact().subtract(anchor);
            var center = rotation.transformPosition(new Vector3d(point.x, point.y, point.z));
            var forward = seat.forward();
            var direction = rotation.transformDirection(new Vector3d(forward.x, forward.y, forward.z));
            seats.add(new BenchSeating.Point(new Vec3d(center.x, center.y, center.z),
                    (float) Math.toDegrees(Math.atan2(-direction.x, direction.z))));
            cells.add(new Cell((int) Math.floor(center.x), (int) Math.floor(center.y), (int) Math.floor(center.z)));
        }
        cells.add(Cell.ORIGIN);
        Map<Cell, Footprint> storage = new TreeMap<>();
        for (Cell cell : cells) storage.put(cell, new Footprint(blocks.getOrDefault(cell, List.of()), outline.getOrDefault(cell, List.of())));
        return new Geometry(Map.copyOf(storage), players, blocks, outline, stock, List.copyOf(seats));
    }

    private static Map<Cell, List<Box>> clipped(List<net.minecraft.util.math.Box> volumes, Vec3d anchor,
            Matrix4d mount, int yaw, Vec3d translation) {
        Map<Cell, List<Box>> result = new HashMap<>();
        for (var source : volumes) {
            var box = source.offset(-anchor.x, -anchor.y, -anchor.z);
            var transformed = transformBounds(box, mount);
            var bounds = new CompositeSpec.Bounds(
                    new CompositeSpec.Vec(transformed.minX * 16, transformed.minY * 16, transformed.minZ * 16),
                    new CompositeSpec.Vec(transformed.maxX * 16, transformed.maxY * 16, transformed.maxZ * 16));
            Map<Cell, List<Box>> raster = new HashMap<>();
            StudioRasterInvoker.dw$raster(bounds, yaw, 0, raster, 16);
            for (var entry : raster.entrySet()) {
                Cell cell = entry.getKey();
                for (Box part : entry.getValue()) clip(result,
                        part.minX() + cell.x() + translation.x, part.minY() + cell.y() + translation.y, part.minZ() + cell.z() + translation.z,
                        part.maxX() + cell.x() + translation.x, part.maxY() + cell.y() + translation.y, part.maxZ() + cell.z() + translation.z);
            }
        }
        result.replaceAll((cell, boxes) -> List.copyOf(boxes));
        return Map.copyOf(result);
    }

    private static void clip(Map<Cell, List<Box>> result, double x0, double y0, double z0, double x1, double y1, double z1) {
        for (int x = (int) Math.floor(x0 + EPS); x < Math.ceil(x1 - EPS); x++)
            for (int y = (int) Math.floor(y0 + EPS); y < Math.ceil(y1 - EPS); y++)
                for (int z = (int) Math.floor(z0 + EPS); z < Math.ceil(z1 - EPS); z++) {
                    double a = Math.max(0, x0 - x), b = Math.max(0, y0 - y), c = Math.max(0, z0 - z);
                    double d = Math.min(1, x1 - x), e = Math.min(1, y1 - y), f = Math.min(1, z1 - z);
                    if (d - a > EPS && e - b > EPS && f - c > EPS)
                        result.computeIfAbsent(new Cell(x, y, z), key -> new ArrayList<>()).add(new Box(a, b, c, d, e, f));
                }
    }

    public static net.minecraft.util.math.Box transformBounds(net.minecraft.util.math.Box box, Matrix4d matrix) {
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0;
        double x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        for (int i = 0; i < 8; i++) {
            var point = matrix.transformPosition(new Vector3d((i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
            x0 = Math.min(x0, point.x); y0 = Math.min(y0, point.y); z0 = Math.min(z0, point.z);
            x1 = Math.max(x1, point.x); y1 = Math.max(y1, point.y); z1 = Math.max(z1, point.z);
        }
        return new net.minecraft.util.math.Box(x0, y0, z0, x1, y1, z1);
    }
}
