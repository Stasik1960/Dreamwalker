package daot;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class ButcherTentacleRenderer {
   private static final int SEGMENTS_PER_TENTACLE = 10;
   private static final double TENTACLE_LENGTH = 11.0;
   private static final double WAVE_AMPLITUDE = 0.45;
   private static final double WAVE_FREQUENCY = 3.0;
   private static final double WAVE_TIME_SPEED = 0.4;
   private static final double BASE_WIDTH = 0.32;
   private static final float TENTACLE_R = 0.45F;
   private static final float TENTACLE_G = 0.38F;
   private static final float TENTACLE_B = 0.3F;
   private static final float OUTLINE_R = 0.08F;
   private static final float OUTLINE_G = 0.06F;
   private static final float OUTLINE_B = 0.04F;
   private static final float OUTLINE_WIDTH_SCALE = 1.25F;
   private static final int TENTACLE_ALPHA = 255;
   private static final double[][] ANGLE_OFFSETS = new double[][]{{-9.0, -6.0}, {9.0, -6.0}, {-9.0, 6.0}, {9.0, 6.0}};
   private static final double[] PHASE_OFFSETS = new double[]{0.0, Math.PI / 2, Math.PI, Math.PI * 3.0 / 2.0};

   private ButcherTentacleRenderer() {
   }

   public static void register() {
      WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ButcherTentacleRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      if (!ButcherTentacleClientHandler.activeFirers().isEmpty() || !ButcherGrabClientState.activeFirers().isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            MatrixStack poseStack = context.matrixStack();
            Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();
            Vec3d cameraPos = context.camera().getPos();
            float partialTick = context.tickDelta();
            double now = (float)mc.world.getTime() + partialTick;
            RenderSystem.enableDepthTest();
            VertexConsumer buffer = buffers.getBuffer(HomelanderRenderTypes.TENTACLE);
            Matrix4f matrix = poseStack.peek().getPositionMatrix();

            for (UUID firerId : ButcherTentacleClientHandler.activeFirers()) {
               if (mc.world.getPlayerByUuid(firerId) instanceof AbstractClientPlayerEntity player && ButcherGrabClientState.get(firerId) == null) {
                  Vec3d baseDir = ButcherTentacleClientHandler.directionFor(firerId);
                  if (baseDir != null) {
                     float progress = ButcherTentacleClientHandler.extensionProgress(firerId, partialTick);
                     if (!(progress <= 0.0F)) {
                        long startTick = ButcherTentacleClientHandler.startTickFor(firerId);
                        double age = now - startTick;
                        Vec3d chest = chestPos(player, partialTick);

                        for (int t = 0; t < 4; t++) {
                           renderTentacle(matrix, buffer, chest, baseDir, t, progress, age, cameraPos, 11.0);
                        }
                     }
                  }
               }
            }

            for (UUID firerIdx : ButcherGrabClientState.activeFirers()) {
               if (mc.world.getPlayerByUuid(firerIdx) instanceof AbstractClientPlayerEntity playerx) {
                  ButcherGrabClientState.State state = ButcherGrabClientState.get(firerIdx);
                  if (state != null && state.active()) {
                     Vec3d chest = chestPos(playerx, partialTick);

                     for (int t = 0; t < 4; t++) {
                        renderTentacle(matrix, buffer, chest, state.dir(), t, 1.0F, now, cameraPos, state.distance());
                     }
                  }
               }
            }

            buffers.draw(HomelanderRenderTypes.TENTACLE);
         }
      }
   }

   private static Vec3d chestPos(AbstractClientPlayerEntity player, float partialTick) {
      double px = MathHelper.lerp((double)partialTick, player.prevX, player.getX());
      double py = MathHelper.lerp((double)partialTick, player.prevY, player.getY());
      double pz = MathHelper.lerp((double)partialTick, player.prevZ, player.getZ());
      double eyeY = py + player.getStandingEyeHeight();
      return new Vec3d(px, eyeY - player.getStandingEyeHeight() * 0.3, pz);
   }

   private static void renderTentacle(
      Matrix4f matrix, VertexConsumer buffer, Vec3d chest, Vec3d baseDir, int tentacleIdx, float progress, double age, Vec3d cameraPos, double maxLength
   ) {
      double baseYaw = Math.atan2(-baseDir.x, baseDir.z);
      double basePitch = -Math.asin(MathHelper.clamp(baseDir.y, -1.0, 1.0));
      double yawRad = baseYaw + Math.toRadians(ANGLE_OFFSETS[tentacleIdx][0]);
      double pitchRad = basePitch + Math.toRadians(ANGLE_OFFSETS[tentacleIdx][1]);
      double cosPitch = Math.cos(pitchRad);
      Vec3d tentacleDir = new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
      Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
      Vec3d perpA = tentacleDir.crossProduct(worldUp);
      if (perpA.lengthSquared() < 1.0E-4) {
         perpA = tentacleDir.crossProduct(new Vec3d(1.0, 0.0, 0.0));
      }

      perpA = perpA.normalize();
      Vec3d perpB = tentacleDir.crossProduct(perpA).normalize();
      double phase = PHASE_OFFSETS[tentacleIdx];
      double currentLength = maxLength * progress;
      double wavePhase = phase + age * 0.4;
      Vec3d[] points = new Vec3d[10];

      for (int i = 0; i < 10; i++) {
         double tFrac = i / 9.0;
         double along = tFrac * currentLength;
         double envelope = Math.sin(tFrac * Math.PI);
         double waveA = Math.sin(tFrac * 3.0 + wavePhase) * 0.45 * envelope;
         double waveB = Math.cos(tFrac * 3.0 + wavePhase * 0.7) * 0.45 * envelope;
         points[i] = chest.add(tentacleDir.multiply(along)).add(perpA.multiply(waveA)).add(perpB.multiply(waveB));
      }

      for (int i = 0; i < 9; i++) {
         double tFrac = i / 9.0;
         float radius = (float)(0.16 * (1.0 - 0.5 * tFrac));
         renderTubeSegment(matrix, buffer, points[i], points[i + 1], cameraPos, radius, perpA, perpB);
      }
   }

   private static void renderTubeSegment(
      Matrix4f matrix, VertexConsumer buffer, Vec3d start, Vec3d end, Vec3d cameraPos, float radius, Vec3d perp1, Vec3d perp2
   ) {
      Vec3d axis = end.subtract(start);
      if (!(axis.lengthSquared() < 1.0E-6)) {
         Vec3d[] corner = new Vec3d[]{perp1.multiply(radius), perp2.multiply(radius), perp1.multiply(-radius), perp2.multiply(-radius)};
         Vec3d[] sCorners = new Vec3d[4];
         Vec3d[] eCorners = new Vec3d[4];

         for (int c = 0; c < 4; c++) {
            sCorners[c] = start.add(corner[c]).subtract(cameraPos);
            eCorners[c] = end.add(corner[c]).subtract(cameraPos);
         }

         for (int c = 0; c < 4; c++) {
            int next = (c + 1) % 4;
            Vec3d outward = corner[c].add(corner[next]);
            if (!(outward.lengthSquared() < 1.0E-6)) {
               outward = outward.normalize();
               float light = (float)(0.675 + 0.325 * outward.y);
               int ir = Math.max(0, Math.min(255, Math.round(0.45F * light * 255.0F)));
               int ig = Math.max(0, Math.min(255, Math.round(0.38F * light * 255.0F)));
               int ib = Math.max(0, Math.min(255, Math.round(0.3F * light * 255.0F)));
               buffer.vertex(matrix, (float)sCorners[c].x, (float)sCorners[c].y, (float)sCorners[c].z).color(ir, ig, ib, 255).next();
               buffer.vertex(matrix, (float)sCorners[next].x, (float)sCorners[next].y, (float)sCorners[next].z).color(ir, ig, ib, 255).next();
               buffer.vertex(matrix, (float)eCorners[next].x, (float)eCorners[next].y, (float)eCorners[next].z).color(ir, ig, ib, 255).next();
               buffer.vertex(matrix, (float)eCorners[c].x, (float)eCorners[c].y, (float)eCorners[c].z).color(ir, ig, ib, 255).next();
            }
         }
      }
   }

   private static void renderRibbonSegment(
      Matrix4f matrix, VertexConsumer buffer, Vec3d start, Vec3d end, Vec3d cameraPos, float width, float r, float g, float b, int alpha
   ) {
      Vec3d p1 = start.subtract(cameraPos);
      Vec3d p2 = end.subtract(cameraPos);
      Vec3d lineDir = p2.subtract(p1);
      if (!(lineDir.lengthSquared() < 1.0E-6)) {
         lineDir = lineDir.normalize();
         Vec3d midpoint = p1.add(p2).multiply(0.5);
         Vec3d toCamera = midpoint.multiply(-1.0);
         Vec3d perpendicular = lineDir.crossProduct(toCamera);
         if (perpendicular.lengthSquared() < 1.0E-4) {
            perpendicular = lineDir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
         }

         if (!(perpendicular.lengthSquared() < 1.0E-4)) {
            perpendicular = perpendicular.normalize().multiply(width * 0.5);
            Vec3d v1 = p1.add(perpendicular);
            Vec3d v2 = p1.subtract(perpendicular);
            Vec3d v3 = p2.subtract(perpendicular);
            Vec3d v4 = p2.add(perpendicular);
            int red = Math.round(r * 255.0F);
            int green = Math.round(g * 255.0F);
            int blue = Math.round(b * 255.0F);
            buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).color(red, green, blue, alpha).next();
            buffer.vertex(matrix, (float)v4.x, (float)v4.y, (float)v4.z).color(red, green, blue, alpha).next();
         }
      }
   }
}
