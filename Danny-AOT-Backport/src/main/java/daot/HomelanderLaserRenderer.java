package daot;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class HomelanderLaserRenderer {
   private static final double BEAM_RANGE = 80.0;
   private static final double EYE_OFFSET = 0.15;
   private static final double NECK_BELOW_EYE = 0.22;
   private static final double EMIT_ABOVE_NECK_FP = 0.05;
   private static final double EMIT_ABOVE_NECK_TP = 0.18;
   private static final double SUPER_FLY_BODY_PITCH_DEG = -70.28;
   private static final double EMIT_FORWARD = 0.15;
   private static final double EMIT_FORWARD_FP = -0.5;
   private static final double FP_CAMERA_DROP = 0.17;
   private static final double BODY_TILT_PIVOT_Y = 0.75;
   private static final float[][] BEAM_LAYERS = new float[][]{
      {0.4F, 1.0F, 0.1F, 0.05F, 60.0F}, {0.18F, 1.0F, 0.05F, 0.05F, 120.0F}, {0.06F, 1.0F, 0.85F, 0.85F, 220.0F}
   };

   private HomelanderLaserRenderer() {
   }

   public static void register() {
      WorldRenderEvents.BEFORE_DEBUG_RENDER.register(HomelanderLaserRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      if (!HomelanderLaserClientHandler.activeFirers().isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            MatrixStack poseStack = context.matrixStack();
            Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
            Vec3d cameraPos = context.camera().getPos();
            float partialTick = context.tickDelta();
            RenderSystem.enableDepthTest();
            VertexConsumer buffer = bufferSource.getBuffer(HomelanderRenderTypes.BEAM);
            Matrix4f matrix = poseStack.peek().getPositionMatrix();

            for (UUID firerId : HomelanderLaserClientHandler.activeFirers()) {
               if (mc.world.getPlayerByUuid(firerId) instanceof AbstractClientPlayerEntity player) {
                  float extend = HomelanderLaserClientHandler.getExtendProgress(firerId, partialTick);
                  renderBeamsFor(player, matrix, buffer, cameraPos, partialTick, 80.0 * extend, 1.0F);
               }
            }

            bufferSource.draw(HomelanderRenderTypes.BEAM);
         }
      }
   }

   private static void renderBeamsFor(
      AbstractClientPlayerEntity player, Matrix4f matrix, VertexConsumer buffer, Vec3d cameraPos, float partialTick, double maxRange, float alphaScale
   ) {
      MinecraftClient self = MinecraftClient.getInstance();
      boolean isLocal = player == self.player;
      float headYaw;
      float headPitch;
      Vec3d look;
      if (!isLocal) {
         Vec3d broadcast = HomelanderLaserClientHandler.getDirection(player.getUuid());
         if (broadcast != null && broadcast.lengthSquared() > 1.0E-6) {
            look = broadcast.normalize();
            headYaw = (float)Math.toDegrees(Math.atan2(-look.x, look.z));
            headPitch = (float)Math.toDegrees(-Math.asin(MathHelper.clamp(look.y, -1.0, 1.0)));
         } else {
            headYaw = MathHelper.lerpAngleDegrees(partialTick, player.prevHeadYaw, player.headYaw);
            headPitch = MathHelper.lerp(partialTick, player.prevPitch, player.getPitch());
            float yawRadF = (float)Math.toRadians(headYaw);
            float pitchRadF = (float)Math.toRadians(headPitch);
            double cosPitchF = Math.cos(pitchRadF);
            look = new Vec3d(-Math.sin(yawRadF) * cosPitchF, -Math.sin(pitchRadF), Math.cos(yawRadF) * cosPitchF);
         }
      } else {
         if (self.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            look = player.getRotationVec(partialTick);
         } else {
            Quaternionf camRot = new Quaternionf(self.gameRenderer.getCamera().getRotation());
            Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
            camRot.transform(forward);
            look = new Vec3d(forward.x, forward.y, forward.z);
         }

         headYaw = (float)Math.toDegrees(Math.atan2(-look.x, look.z));
         headPitch = (float)Math.toDegrees(-Math.asin(MathHelper.clamp(look.y, -1.0, 1.0)));
      }

      float yawRad = (float)Math.toRadians(headYaw);
      Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
      Vec3d headUp = right.crossProduct(look).normalize();
      boolean isLocalFirstPerson = isLocal && self.options.getPerspective() == Perspective.FIRST_PERSON;
      Vec3d emitCenter;
      if (isLocalFirstPerson) {
         Vec3d eye = player.getCameraPosVec(partialTick);
         emitCenter = eye.add(headUp.multiply(-0.17)).add(look.multiply(-0.5));
      } else {
         Vec3d neckPivot = player.getCameraPosVec(partialTick).subtract(0.0, 0.22, 0.0);
         emitCenter = neckPivot.add(headUp.multiply(0.18)).add(look.multiply(0.15));
      }

      if (!isLocalFirstPerson && HomelanderFlightHandler.isFlying(player.getUuid()) && !HomelanderPlayerAnimationHandler.isInFlyStart(player.getUuid())) {
         double tiltDegrees = -headPitch;
         if (HomelanderPlayerAnimationHandler.isInSuperFly(player.getUuid())) {
            tiltDegrees += -70.28;
         }

         Vec3d tiltPivot = player.getLerpedPos(partialTick).add(0.0, 0.75, 0.0);
         emitCenter = rotateAroundAxis(emitCenter, tiltPivot, right, Math.toRadians(tiltDegrees));
      }

      Vec3d leftEye = emitCenter.add(right.multiply(-0.15));
      Vec3d rightEye = emitCenter.add(right.multiply(0.15));
      double leftHit = raycastBeamLength(player, leftEye, look, maxRange);
      double rightHit = raycastBeamLength(player, rightEye, look, maxRange);
      Vec3d leftEnd = leftEye.add(look.multiply(leftHit));
      Vec3d rightEnd = rightEye.add(look.multiply(rightHit));

      for (float[] layer : BEAM_LAYERS) {
         int scaledAlpha = Math.max(0, Math.min(255, Math.round(layer[4] * alphaScale)));
         if (scaledAlpha > 0) {
            renderBeamLayer(matrix, buffer, leftEye, leftEnd, cameraPos, layer[0], layer[1], layer[2], layer[3], scaledAlpha);
            renderBeamLayer(matrix, buffer, rightEye, rightEnd, cameraPos, layer[0], layer[1], layer[2], layer[3], scaledAlpha);
         }
      }
   }

   private static Vec3d rotateAroundAxis(Vec3d point, Vec3d pivot, Vec3d axis, double angleRad) {
      Vec3d v = point.subtract(pivot);
      double cos = Math.cos(angleRad);
      double sin = Math.sin(angleRad);
      Vec3d rotated = v.multiply(cos).add(axis.crossProduct(v).multiply(sin)).add(axis.multiply(axis.dotProduct(v) * (1.0 - cos)));
      return pivot.add(rotated);
   }

   private static double raycastBeamLength(AbstractClientPlayerEntity player, Vec3d origin, Vec3d look, double maxRange) {
      if (maxRange <= 0.0) {
         return 0.0;
      } else {
         Vec3d endpoint = origin.add(look.multiply(maxRange));
         RaycastContext ctx = new RaycastContext(origin, endpoint, ShapeType.OUTLINE, FluidHandling.NONE, player);
         BlockHitResult hit = player.getWorld().raycast(ctx);
         return hit.getType() == Type.MISS ? maxRange : hit.getPos().distanceTo(origin);
      }
   }

   private static void renderBeamLayer(
      Matrix4f matrix, VertexConsumer buffer, Vec3d start, Vec3d end, Vec3d cameraPos, float width, float r, float g, float b, int alpha
   ) {
      Vec3d p1 = start.subtract(cameraPos);
      Vec3d p2 = end.subtract(cameraPos);
      Vec3d lineDir = p2.subtract(p1);
      if (!(lineDir.lengthSquared() < 1.0E-4)) {
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
