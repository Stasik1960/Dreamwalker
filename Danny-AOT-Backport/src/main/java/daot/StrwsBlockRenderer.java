package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@Environment(EnvType.CLIENT)
public class StrwsBlockRenderer implements net.minecraft.client.render.block.entity.BlockEntityRenderer<StrwsBlockEntity> {
   private static final int WIRE_SEGMENTS = 24;
   private static final float WIRE_HALF_THICKNESS = 0.015F;

   private final GeoBlockRenderer<StrwsBlockEntity> delegate = new GeoBlockRenderer<>(new StrwsBlockModel());
   public StrwsBlockRenderer(Context context) {}

   public boolean shouldRenderOffScreen(StrwsBlockEntity blockEntity) {
      return true;
   }

   public void render(StrwsBlockEntity be, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
      delegate.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
      this.renderWires(be, partialTick, poseStack, bufferSource);
   }

   private static Vec3d wireEnd(StrwsBlockEntity be, int i, float partialTick) {
      if (be.isWireOnEntity(i)) {
         Entity te = MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getEntityById(be.getWireEntityId(i)) : null;
         if (te != null) {
            double ex = MathHelper.lerp((double)partialTick, te.lastRenderX, te.getX());
            double ey = MathHelper.lerp((double)partialTick, te.lastRenderY, te.getY());
            double ez = MathHelper.lerp((double)partialTick, te.lastRenderZ, te.getZ());
            return new Vec3d(ex, ey, ez).add(be.getWireOffset(i));
         }
      }

      return be.getWireLatch(i);
   }

   private void renderWires(StrwsBlockEntity be, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource) {
      boolean any = false;

      for (int i = 0; i < 6; i++) {
         if (be.isWireActive(i)) {
            any = true;
            break;
         }
      }

      if (any) {
         BlockPos pos = be.getPos();
         Direction facing = be.getCachedState().get(Properties.HORIZONTAL_FACING);
         Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
         Vec3d blockOrigin = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
         Matrix4f matrix = poseStack.peek().getPositionMatrix();
         VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getDebugQuads());

         for (int ix = 0; ix < 6; ix++) {
            if (be.isWireActive(ix)) {
               Vec3d start = StrwsAim.boneWorldPos(pos, facing, be.clientRenderYaw, be.clientRenderPitch, ix);
               Vec3d end = wireEnd(be, ix, partialTick);
               this.drawRibbon(buffer, matrix, start, end, blockOrigin, cameraPos);
            }
         }
      }
   }

   private void drawRibbon(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, Vec3d blockOrigin, Vec3d cameraPos) {
      for (int s = 0; s < 24; s++) {
         double t1 = s / 24.0;
         double t2 = (s + 1) / 24.0;
         Vec3d w1 = start.lerp(end, t1);
         Vec3d w2 = start.lerp(end, t2);
         Vec3d lineDir = w2.subtract(w1);
         if (!(lineDir.lengthSquared() < 1.0E-6)) {
            lineDir = lineDir.normalize();
            Vec3d mid = w1.add(w2).multiply(0.5);
            Vec3d toCamera = cameraPos.subtract(mid);
            Vec3d perp = lineDir.crossProduct(toCamera);
            if (perp.lengthSquared() < 1.0E-6) {
               perp = lineDir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
            }

            perp = perp.normalize().multiply(0.015F);
            Vec3d p1 = w1.subtract(blockOrigin);
            Vec3d p2 = w2.subtract(blockOrigin);
            Vec3d v1 = p1.add(perp);
            Vec3d v2 = p1.subtract(perp);
            Vec3d v3 = p2.subtract(perp);
            Vec3d v4 = p2.add(perp);
            buffer.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(0, 0, 0, 255).next();
            buffer.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(0, 0, 0, 255).next();
            buffer.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).color(0, 0, 0, 255).next();
            buffer.vertex(matrix, (float)v4.x, (float)v4.y, (float)v4.z).color(0, 0, 0, 255).next();
         }
      }
   }
}
