package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ThunderSpearAttachmentRenderer {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/thunder_spear.png");
   public static float OFFSET_X = 0.0F;
   public static float OFFSET_Y = 0.65F;
   public static float OFFSET_Z = 0.42F;
   public static float ROTATION_X = 0.0F;
   public static float ROTATION_Y = 0.0F;
   public static float ROTATION_Z = 0.0F;
   public static float RIGHT_OFFSET_X = 0.6F;
   public static float RIGHT_OFFSET_Y = 0.65F;
   public static float RIGHT_OFFSET_Z = 0.42F;
   public static float RIGHT_ROTATION_X = 180.0F;
   public static float RIGHT_ROTATION_Y = 0.0F;
   public static float RIGHT_ROTATION_Z = 0.0F;
   public static float SCALE = 0.8F;
   private static final float G2_PX = -0.19454F;
   private static final float G2_PY = -0.33495F;
   private static final float G2_PZ = -0.11517F;
   private static final float TS_PX = -1.64889F;
   private static final float TS_PY = -0.31591F;
   private static final float TS_PZ = -0.0718F;
   public static Vec3d mainHandBoneRestWorldPos = null;
   public static Vec3d offHandBoneRestWorldPos = null;
   private static ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel model;

   private static ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel getModel() {
      if (model == null) {
         model = new ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel();
      }

      return model;
   }

   private static Vec3d computeBoneWorldPos(MatrixStack poseStack, float ox, float oy, float oz, float rx, float ry, float rz, boolean isRightHand) {
      Matrix4f M = new Matrix4f(poseStack.peek().getPositionMatrix());
      M.translate(ox, oy, oz);
      if (rx != 0.0F) {
         M.rotateX((float)Math.toRadians(rx));
      }

      if (ry != 0.0F) {
         M.rotateY((float)Math.toRadians(ry));
      }

      if (rz != 0.0F) {
         M.rotateZ((float)Math.toRadians(rz));
      }

      float zs = isRightHand ? -SCALE : SCALE;
      M.scale(SCALE, SCALE, zs);
      float g2x = -0.01215875F;
      float g2y = -0.020934375F;
      float g2z = -0.007198125F;
      M.translate(g2x, g2y, g2z);
      M.rotateZ((float)Math.toRadians(90.0));
      M.rotateY((float)Math.toRadians(-90.0));
      M.translate(-g2x, -g2y, -g2z);
      Vector4f boneLocal = new Vector4f(-0.103055626F, -0.019744376F, -0.0044875F, 1.0F);
      M.transform(boneLocal);
      Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
      return new Vec3d(boneLocal.x() + camPos.getX(), boneLocal.y() + camPos.getY(), boneLocal.z() + camPos.getZ());
   }

   public static void render(MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, Hand hand, boolean isRightHand, float partialTick) {
      if (ThunderSpearAttachmentTracker.shouldRender(hand)) {
         ThunderSpearAttachmentTracker.AnimState state = ThunderSpearAttachmentTracker.getState(hand);
         float progress = ThunderSpearAttachmentTracker.getProgress(hand, partialTick);
         ThunderSpearAttachmentRenderer.ThunderSpearAttachmentModel geoModel = getModel();
         BakedGeoModel bakedModel = geoModel.getBakedModel(geoModel.getModelResource(null));
         if (bakedModel != null) {
            GeoBone thunderSpearBone = (GeoBone)bakedModel.getBone("thunder_spear").orElse(null);
            GeoBone boneBone = (GeoBone)bakedModel.getBone("bone").orElse(null);
            float ox;
            float oy;
            float oz;
            float rx;
            float ry;
            float rz;
            if (isRightHand) {
               ox = RIGHT_OFFSET_X;
               oy = RIGHT_OFFSET_Y;
               oz = RIGHT_OFFSET_Z;
               rx = RIGHT_ROTATION_X;
               ry = RIGHT_ROTATION_Y;
               rz = RIGHT_ROTATION_Z;
            } else {
               ox = OFFSET_X;
               oy = OFFSET_Y;
               oz = OFFSET_Z;
               rx = ROTATION_X;
               ry = ROTATION_Y;
               rz = ROTATION_Z;
            }

            if (state != ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY) {
               Vec3d restWorld = computeBoneWorldPos(poseStack, ox, oy, oz, rx, ry, rz, isRightHand);
               if (hand == Hand.MAIN_HAND) {
                  mainHandBoneRestWorldPos = restWorld;
               } else {
                  offHandBoneRestWorldPos = restWorld;
               }
            }

            if (state == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY) {
               if (thunderSpearBone != null) {
                  thunderSpearBone.setHidden(true);
               }
            } else {
               if (thunderSpearBone != null) {
                  thunderSpearBone.setHidden(false);
               }

               if (thunderSpearBone != null) {
                  applyThunderSpearAnimation(thunderSpearBone, state, progress);
               }

               if (boneBone != null) {
                  applyBoneAnimation(boneBone, state, progress);
               }
            }

            poseStack.push();
            poseStack.translate(ox, oy, oz);
            if (rx != 0.0F) {
               poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
            }

            if (ry != 0.0F) {
               poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
            }

            if (rz != 0.0F) {
               poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
            }

            if (isRightHand) {
               poseStack.scale(SCALE, SCALE, -SCALE);
            } else {
               poseStack.scale(SCALE, SCALE, SCALE);
            }

            RenderLayer renderType = RenderLayer.getEntityCutoutNoCull(TEXTURE);
            VertexConsumer buffer = bufferSource.getBuffer(renderType);
            GeoModelHelper.renderModel(poseStack, bakedModel, buffer, packedLight, OverlayTexture.DEFAULT_UV, -1);
            poseStack.pop();
            if (thunderSpearBone != null) {
               thunderSpearBone.setHidden(false);
               thunderSpearBone.updatePosition(0.0F, 0.0F, 0.0F);
               thunderSpearBone.updateRotation(0.0F, 0.0F, 0.0F);
            }

            if (boneBone != null) {
               boneBone.updateRotation(0.0F, 0.0F, 0.0F);
            }
         }
      }
   }

   private static void applyThunderSpearAnimation(GeoBone bone, ThunderSpearAttachmentTracker.AnimState state, float progress) {
      float posX = 0.0F;
      float rotXDeg = 0.0F;
      switch (state) {
         case LOADING:
            posX = interpolate3(progress, 0.0F, 33.0F, 0.667F, -1.0F, 1.0F, 0.0F);
            rotXDeg = interpolate3(progress, 0.0F, 0.0F, 0.667F, 30.0F, 1.0F, 0.0F);
            break;
         case LOADED:
            posX = 0.0F;
            rotXDeg = 0.0F;
            break;
         case UNLOADING:
            posX = interpolate3(progress, 0.0F, 0.0F, 0.667F, 1.0F, 1.0F, 33.0F);
            rotXDeg = interpolate3(progress, 0.0F, 0.0F, 0.667F, 30.0F, 1.0F, 0.0F);
            break;
         case FIRING:
            posX = interpolate3(progress, 0.0F, 0.0F, 0.667F, -1.0F, 1.0F, -36.0F);
            rotXDeg = interpolate3(progress, 0.0F, 0.0F, 0.667F, 30.0F, 1.0F, 0.0F);
      }

      bone.setPosX(posX);
      bone.setRotX((float)Math.toRadians(rotXDeg));
   }

   private static void applyBoneAnimation(GeoBone bone, ThunderSpearAttachmentTracker.AnimState state, float progress) {
      float rotYDeg = 0.0F;
      switch (state) {
         case LOADING:
         case FIRING:
            if (progress <= 0.5F) {
               rotYDeg = interpolate3(progress, 0.0F, 15.0F, 0.333F, -2.5F, 0.5F, 0.0F);
            }
         case LOADED:
         default:
            break;
         case UNLOADING:
            if (progress <= 0.5F) {
               rotYDeg = interpolate3(progress, 0.0F, -15.0F, 0.333F, 2.5F, 0.5F, 0.0F);
            }
      }

      bone.setRotY((float)Math.toRadians(rotYDeg));
   }

   private static float interpolate3(float t, float t0, float v0, float t1, float v1, float t2, float v2) {
      if (t <= t0) {
         return v0;
      } else if (t <= t1) {
         float local = (t - t0) / (t1 - t0);
         local = local * local * (3.0F - 2.0F * local);
         return v0 + (v1 - v0) * local;
      } else if (t <= t2) {
         float local = (t - t1) / (t2 - t1);
         local = local * local * (3.0F - 2.0F * local);
         return v1 + (v2 - v1) * local;
      } else {
         return v2;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class ThunderSpearAttachmentModel extends GeoModel<ThunderSpearItem> {
      private static final Identifier MODEL = new Identifier("dannys-aot", "geo/thunder_spear.geo.json");

      public Identifier getModelResource(ThunderSpearItem animatable) {
         return MODEL;
      }

      public Identifier getTextureResource(ThunderSpearItem animatable) {
         return ThunderSpearAttachmentRenderer.TEXTURE;
      }

      public Identifier getAnimationResource(ThunderSpearItem animatable) {
         return null;
      }
   }
}
