package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class BladeArmRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("dannys-aot");
   private static int logCounter = 0;
   private static final float ARM_WIDTH = 0.16F;
   private static final float ARM_LENGTH = 0.85F;
   private static final float SHOULDER_X_OFFSET = 0.4F;
   private static final float SHOULDER_Y = -0.7F;
   private static final float SHOULDER_Z = 0.2F;
   public static volatile Vec3d apgFirstPersonHandOffsetMain = null;
   public static volatile Vec3d apgFirstPersonHandOffsetOff = null;

   public static void renderArm(
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      AbstractClientPlayerEntity player,
      Hand hand,
      int combinedLight,
      float animation,
      float partialTick,
      float pitchRotation,
      float rollRotation,
      float yawRotation
   ) {
      boolean isMainHand = hand == Hand.MAIN_HAND;
      boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
      boolean isRightHand = isMainHand ? mainIsRight : !mainIsRight;
      float side = isRightHand ? 1.0F : -1.0F;
      Identifier skinTexture = player.getSkinTexture();
      VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getEntityCutoutNoCull(skinTexture));
      poseStack.push();
      poseStack.translate(0.2F * side, -0.3F, 0.25F);
      poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(80.0F));
      poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(25.0F * side));
      poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30.0F * side));
      if (logCounter < 5) {
         LOGGER.info("Arm render: hand={}, pitch={}, roll={}, yaw={}", new Object[]{hand, pitchRotation, rollRotation, yawRotation});
         logCounter++;
      }

      renderArmBox(buffer, poseStack.peek().getPositionMatrix(), combinedLight, 0.16F, 0.85F, isRightHand);
      poseStack.pop();
   }

   public static void renderArm(
      MatrixStack poseStack,
      VertexConsumerProvider bufferSource,
      AbstractClientPlayerEntity player,
      Hand hand,
      int combinedLight,
      float animation,
      float partialTick
   ) {
      renderArm(poseStack, bufferSource, player, hand, combinedLight, animation, partialTick, 0.0F, 0.0F, 0.0F);
   }

   private static void renderArmBox(VertexConsumer buffer, Matrix4f matrix, int light, float size, float length, boolean isRightArm) {
      float half = size / 2.0F;
      float u1 = isRightArm ? 0.6875F : 0.5625F;
      float v1 = isRightArm ? 0.3125F : 0.8125F;
      float u2 = u1 + 0.0625F;
      float v2 = v1 + 0.1875F;
      quad(buffer, matrix, light, -half, 0.0F, -half, half, 0.0F, -half, half, -length, -half, -half, -length, -half, u1, v1, u2, v2);
      quad(buffer, matrix, light, half, 0.0F, half, -half, 0.0F, half, -half, -length, half, half, -length, half, u1, v1, u2, v2);
      quad(buffer, matrix, light, half, 0.0F, -half, half, 0.0F, half, half, -length, half, half, -length, -half, u1, v1, u2, v2);
      quad(buffer, matrix, light, -half, 0.0F, half, -half, 0.0F, -half, -half, -length, -half, -half, -length, half, u1, v1, u2, v2);
      quad(buffer, matrix, light, -half, 0.0F, -half, half, 0.0F, -half, half, 0.0F, half, -half, 0.0F, half, u1, v1, u2, v1 + 0.0625F);
      quad(buffer, matrix, light, -half, -length, half, half, -length, half, half, -length, -half, -half, -length, -half, u1, v1, u2, v1 + 0.0625F);
   }

   private static void quad(
      VertexConsumer buffer,
      Matrix4f matrix,
      int light,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float x4,
      float y4,
      float z4,
      float u1,
      float v1,
      float u2,
      float v2
   ) {
      vertex(buffer, matrix, x1, y1, z1, u1, v1, light);
      vertex(buffer, matrix, x2, y2, z2, u2, v1, light);
      vertex(buffer, matrix, x3, y3, z3, u2, v2, light);
      vertex(buffer, matrix, x4, y4, z4, u1, v2, light);
   }

   private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int light) {
      buffer.vertex(matrix, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0F, 1.0F, 0.0F).next();
   }
}
