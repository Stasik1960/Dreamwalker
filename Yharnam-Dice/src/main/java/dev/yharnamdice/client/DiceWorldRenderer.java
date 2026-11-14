
package dev.yharnamdice.client;

import dev.yharnamdice.Protocol;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiceWorldRenderer {
    private static final long EXPIRE_MS = 1800;
    private static final Map<BlockPos, Frame> TABLES = new HashMap<>();

    private record Frame(int rotation, long timestamp, long interval,
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
        for (Protocol.Visual die : dice) poses.put(die.seat()*20+die.index(),die);
        TABLES.put(table.toImmutable(), new Frame(rotation,time,
                before == null ? 50 : Math.max(35L,Math.min(100L,time-before.timestamp)),
                before == null ? poses : before.current,poses));
    }

    public static void clear() { TABLES.clear(); }

    public static void render(WorldRenderContext context) {
        long now = System.nanoTime() / 1_000_000;
        TABLES.values().removeIf(table -> now-table.timestamp>EXPIRE_MS);
        if (TABLES.isEmpty() || context.matrixStack()==null || context.consumers()==null) return;
        MatrixStack matrices = context.matrixStack();
        VertexConsumer out = context.consumers().getBuffer(DiceMesh.LAYER);
        Vec3d camera = context.camera().getPos();
        for (var entry : TABLES.entrySet()) {
            BlockPos pos=entry.getKey();
            Frame table=entry.getValue();
            if (pos.getSquaredDistance(camera.x,camera.y,camera.z)>44*44) continue;
            int rotation=table.rotation;
            double x=pos.getX()+.5-camera.x;
            double y=pos.getY()+14.13/16-camera.y;
            double z=pos.getZ()+.5-camera.z;
            if (rotation==0) x+=.5;
            if (rotation==1) z+=.5;
            if (rotation==2) x-=.5;
            if (rotation==3) z-=.5;
            int worldLight = WorldRenderer.getLightmapCoordinates(context.world(),pos.up());
            int light = LightmapTextureManager.pack(Math.max(3,LightmapTextureManager.getBlockLightCoordinates(worldLight)),
                    LightmapTextureManager.getSkyLightCoordinates(worldLight));
            matrices.push();
            matrices.translate(x,y,z);
            matrices.multiply(new Quaternionf().rotationY(-MathHelper.HALF_PI*rotation));
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
        matrices.translate(MathHelper.lerp(alpha,from.x(),to.x()),
                MathHelper.lerp(alpha,from.y(),to.y()),MathHelper.lerp(alpha,from.z(),to.z()));
        Quaternionf orientation = new Quaternionf(from.qx(),from.qy(),from.qz(),from.qw())
                .slerp(new Quaternionf(to.qx(),to.qy(),to.qz(),to.qw()),alpha).normalize();
        matrices.multiply(orientation);
        float size=MathHelper.lerp(alpha,from.scale(),to.scale());
        matrices.scale(size,size,size);
        DiceMesh.draw(out,matrices.peek(),to.skin(),light,OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
