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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class SoldierboyLaserRenderer {
   private static final double BEAM_RANGE = 80.0;
   private static final double CHEST_BELOW_EYE = 0.55;
   private static final float[][] BEAM_LAYERS = new float[][]{
      {0.9F, 1.0F, 0.45F, 0.05F, 70.0F}, {0.42F, 1.0F, 0.75F, 0.05F, 130.0F}, {0.14F, 1.0F, 0.95F, 0.55F, 230.0F}
   };

   private SoldierboyLaserRenderer() {
   }

   public static void register() {
      WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SoldierboyLaserRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      if (!SoldierboyClientHandler.laserFirers().isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            MatrixStack poseStack = context.matrixStack();
            Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
            Vec3d cameraPos = context.camera().getPos();
            float partialTick = context.tickDelta();
            RenderSystem.enableDepthTest();
            VertexConsumer buffer = bufferSource.getBuffer(HomelanderRenderTypes.BEAM);
            Matrix4f matrix = poseStack.peek().getPositionMatrix();

            for (UUID firerId : SoldierboyClientHandler.laserFirers()) {
               if (mc.world.getPlayerByUuid(firerId) instanceof AbstractClientPlayerEntity player) {
                  renderBeam(player, matrix, buffer, cameraPos, partialTick);
               }
            }

            bufferSource.draw(HomelanderRenderTypes.BEAM);
         }
      }
   }

   private static void renderBeam(AbstractClientPlayerEntity player, Matrix4f matrix, VertexConsumer buffer, Vec3d cameraPos, float partialTick) {
      float headYaw = MathHelper.lerpAngleDegrees(partialTick, player.prevHeadYaw, player.headYaw);
      float yawRad = (float)Math.toRadians(headYaw);
      Vec3d dir = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      Vec3d chest = player.getCameraPosVec(partialTick).subtract(0.0, 0.55, 0.0).add(dir.multiply(0.2));
      double hit = raycastBeamLength(player, chest, dir);
      Vec3d end = chest.add(dir.multiply(hit));

      for (float[] layer : BEAM_LAYERS) {
         renderBeamLayer(matrix, buffer, chest, end, cameraPos, layer[0], layer[1], layer[2], layer[3], (int)layer[4]);
      }
   }

   private static double raycastBeamLength(AbstractClientPlayerEntity player, Vec3d origin, Vec3d dir) {
      Vec3d endpoint = origin.add(dir.multiply(80.0));
      RaycastContext ctx = new RaycastContext(origin, endpoint, ShapeType.OUTLINE, FluidHandling.NONE, player);
      BlockHitResult result = player.getWorld().raycast(ctx);
      return result.getType() == Type.MISS ? 80.0 : result.getPos().distanceTo(origin);
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
