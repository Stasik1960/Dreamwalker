package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ThunderSpearEntityRenderer extends EntityRenderer<ThunderSpearEntity> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/thunder_spear.png");
   private static final float WIRE_THICKNESS = 0.0045F;
   private static final int WIRE_R = 65;
   private static final int WIRE_G = 65;
   private static final int WIRE_B = 65;
   private static final int WIRE_A = 255;
   private static ThunderSpearEntityRenderer.ThunderSpearEntityModel geoModel;

   public ThunderSpearEntityRenderer(Context context) {
      super(context);
   }

   private static ThunderSpearEntityRenderer.ThunderSpearEntityModel getModel() {
      if (geoModel == null) {
         geoModel = new ThunderSpearEntityRenderer.ThunderSpearEntityModel();
      }

      return geoModel;
   }

   public void render(
      ThunderSpearEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Entity owner = entity.getOwner();
      if (owner == null || owner != mc.player) {
         if (mc.world != null) {
            Vec3d vel = entity.getVelocity();
            Vec3d pos = entity.getLerpedPos(partialTick);
            double lx = pos.x;
            double ly = pos.y;
            double lz = pos.z;
            if (vel.lengthSquared() > 1.0E-4) {
               Vec3d vn = vel.normalize();
               lx -= vn.x * 0.6;
               ly -= vn.y * 0.6;
               lz -= vn.z * 0.6;
            }

            BlockPos lp = BlockPos.ofFloored(lx, ly, lz);
            int fixedLight = WorldRenderer.getLightmapCoordinates(mc.world, lp);
            int lightAbove = WorldRenderer.getLightmapCoordinates(mc.world, lp.up());
            if (lightAbove > fixedLight) {
               fixedLight = lightAbove;
            }

            if (fixedLight > packedLight) {
               packedLight = fixedLight;
            }
         }

         ThunderSpearEntityRenderer.ThunderSpearEntityModel model = getModel();
         BakedGeoModel bakedModel = model.getBakedModel(model.getModelResource(null));
         if (bakedModel != null) {
            GeoBone groupBone = (GeoBone)bakedModel.getBone("group").orElse(null);
            GeoBone boneBone = (GeoBone)bakedModel.getBone("bone").orElse(null);
            if (groupBone != null) {
               groupBone.setHidden(true);
            }

            if (boneBone != null) {
               boneBone.setHidden(true);
            }

            poseStack.push();
            Vec3d velx = entity.getVelocity();
            float yaw;
            float pitch;
            if (velx.lengthSquared() > 0.001) {
               double hSpd = Math.sqrt(velx.x * velx.x + velx.z * velx.z);
               yaw = (float)Math.toDegrees(Math.atan2(velx.x, velx.z));
               pitch = (float)Math.toDegrees(Math.atan2(-velx.y, hSpd));
            } else {
               yaw = MathHelper.lerp(partialTick, entity.prevYaw, entity.getYaw());
               pitch = MathHelper.lerp(partialTick, entity.prevPitch, entity.getPitch());
            }

            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw + 180.0F));
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
            RenderLayer renderType = RenderLayer.getEntityCutoutNoCull(TEXTURE);
            VertexConsumer buffer = bufferSource.getBuffer(renderType);
            GeoModelHelper.renderModel(poseStack, bakedModel, buffer, packedLight, OverlayTexture.DEFAULT_UV, -1);
            poseStack.pop();
            if (groupBone != null) {
               groupBone.setHidden(false);
            }

            if (boneBone != null) {
               boneBone.setHidden(false);
            }

            if (owner != null) {
               Vec3d entityPos = entity.getLerpedPos(partialTick);
               Vec3d ownerPos = owner.getLerpedPos(partialTick).add(0.0, 1.1, 0.0);
               Vec3d relStart = ownerPos.subtract(entityPos);
               Vec3d relEnd = Vec3d.ZERO;
               VertexConsumer wireBuf = bufferSource.getBuffer(RenderLayer.getDebugQuads());
               Matrix4f mat = poseStack.peek().getPositionMatrix();
               renderWireSegment(wireBuf, mat, relStart, relEnd);
            }
         }
      }
   }

   private static void renderWireSegment(VertexConsumer buf, Matrix4f mat, Vec3d start, Vec3d end) {
      float half = 0.00225F;
      Vec3d ld = end.subtract(start);
      if (!(ld.lengthSquared() < 1.0E-5)) {
         ld = ld.normalize();
         Vec3d perp = ld.crossProduct(new Vec3d(0.0, 1.0, 0.0));
         if (perp.lengthSquared() < 1.0E-5) {
            perp = ld.crossProduct(new Vec3d(1.0, 0.0, 0.0));
         }

         perp = perp.normalize().multiply(half);
         Vec3d v1 = start.add(perp);
         Vec3d v2 = start.subtract(perp);
         Vec3d v3 = end.subtract(perp);
         Vec3d v4 = end.add(perp);
         buf.vertex(mat, (float)v1.x, (float)v1.y, (float)v1.z).color(65, 65, 65, 255).next();
         buf.vertex(mat, (float)v2.x, (float)v2.y, (float)v2.z).color(65, 65, 65, 255).next();
         buf.vertex(mat, (float)v3.x, (float)v3.y, (float)v3.z).color(65, 65, 65, 255).next();
         buf.vertex(mat, (float)v4.x, (float)v4.y, (float)v4.z).color(65, 65, 65, 255).next();
      }
   }

   public Identifier getTexture(ThunderSpearEntity entity) {
      return TEXTURE;
   }

   @Environment(EnvType.CLIENT)
   static class ThunderSpearEntityModel extends GeoModel<ThunderSpearItem> {
      private static final Identifier MODEL = new Identifier("dannys-aot", "geo/thunder_spear.geo.json");

      public Identifier getModelResource(ThunderSpearItem a) {
         return MODEL;
      }

      public Identifier getTextureResource(ThunderSpearItem a) {
         return ThunderSpearEntityRenderer.TEXTURE;
      }

      public Identifier getAnimationResource(ThunderSpearItem a) {
         return null;
      }
   }
}
