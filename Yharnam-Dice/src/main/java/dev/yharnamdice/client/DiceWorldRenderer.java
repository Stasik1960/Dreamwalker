
package dev.yharnamdice.client;

import dev.yharnamdice.Protocol;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiceWorldRenderer {
    private static final long EXPIRE_MS = 1800;
    private static final Map<BlockPos, Frame> TABLES = new HashMap<>();
    private static final Quaternionf ORIENTATION = new Quaternionf();
    private static final Quaternionf TARGET = new Quaternionf();
    private static final Quaternionf[] TABLE_ROTATIONS = {
            new Quaternionf(), RotationAxis.NEGATIVE_Y.rotationDegrees(90),
            RotationAxis.NEGATIVE_Y.rotationDegrees(180), RotationAxis.NEGATIVE_Y.rotationDegrees(270)
    };

    private record Frame(int rotation, double x, double z, Box bounds, long timestamp, long interval,
                         Map<Integer, Protocol.Visual> previous, Map<Integer, Protocol.Visual> current) {}

    private DiceWorldRenderer() {}

    public static void update(BlockPos table, int rotation, List<Protocol.Visual> dice) {
        if (dice.isEmpty()) {
            TABLES.remove(table);
            return;
        }
        long time = System.nanoTime() / 1_000_000;
        Frame before = TABLES.get(table);
        Map<Integer, Protocol.Visual> poses = new HashMap<>();
        for (Protocol.Visual die : dice) {
            int index = die.seat()*20+die.index();
            Protocol.Visual previous = before == null ? null : before.current.get(index);
            poses.put(index, die.equals(previous) ? previous : die);
        }
        double x = table.getX() + .5 + (rotation == 0 ? .5 : rotation == 2 ? -.5 : 0);
        double z = table.getZ() + .5 + (rotation == 1 ? .5 : rotation == 3 ? -.5 : 0);
        Box bounds = new Box(x - 1, table.getY() + .8, z - 1, x + 1, table.getY() + 1.4, z + 1);
        TABLES.put(table.toImmutable(), new Frame(rotation,x,z,bounds,time,
                before == null ? 50 : Math.max(35L,Math.min(100L,time-before.timestamp)),
                before == null ? poses : before.current,poses));
    }

    public static void clear() { TABLES.clear(); }

    public static void render(WorldRenderContext context) {
        if (TABLES.isEmpty() || context.matrixStack()==null || context.consumers()==null) return;
        long now = System.nanoTime() / 1_000_000;
        TABLES.values().removeIf(table -> now-table.timestamp>EXPIRE_MS);
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        VertexConsumer out = null;
        for (var entry : TABLES.entrySet()) {
            BlockPos pos=entry.getKey();
            Frame table=entry.getValue();
            if (pos.getSquaredDistance(camera.x,camera.y,camera.z)>44*44) continue;
            if (context.frustum() != null && !context.frustum().isVisible(table.bounds)) continue;
            if (out == null) out = context.consumers().getBuffer(DiceMesh.LAYER);
            double x=table.x-camera.x;
            double y=pos.getY()+14.13/16-camera.y;
            double z=table.z-camera.z;
            int worldLight = WorldRenderer.getLightmapCoordinates(context.world(),pos.up());
            int light = LightmapTextureManager.pack(Math.max(3,LightmapTextureManager.getBlockLightCoordinates(worldLight)),
                    LightmapTextureManager.getSkyLightCoordinates(worldLight));
            matrices.push();
            matrices.translate(x,y,z);
            matrices.multiply(TABLE_ROTATIONS[table.rotation]);
            float alpha=MathHelper.clamp((now-table.timestamp)/(float)table.interval,0,1);
            for (var pair : table.current.entrySet()) {
                Protocol.Visual die=pair.getValue();
                Protocol.Visual prev=table.previous.getOrDefault(pair.getKey(),die);
                draw(out,matrices,prev,die,alpha,light);
            }
            matrices.pop();
        }
    }

    private static void draw(VertexConsumer out, MatrixStack matrices, Protocol.Visual from,
                             Protocol.Visual to, float alpha, int light) {
        matrices.push();
        float size = to.scale();
        if (from == to || alpha >= 1) {
            matrices.translate(to.x(), to.y(), to.z());
            ORIENTATION.set(to.qx(), to.qy(), to.qz(), to.qw());
        } else {
            matrices.translate(MathHelper.lerp(alpha,from.x(),to.x()),
                    MathHelper.lerp(alpha,from.y(),to.y()),MathHelper.lerp(alpha,from.z(),to.z()));
            ORIENTATION.set(from.qx(),from.qy(),from.qz(),from.qw())
                    .slerp(TARGET.set(to.qx(),to.qy(),to.qz(),to.qw()),alpha).normalize();
            size = MathHelper.lerp(alpha,from.scale(),to.scale());
        }
        matrices.multiply(ORIENTATION);
        matrices.scale(size,size,size);
        DiceMesh.draw(out,matrices.peek(),to.skin(),light,OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
