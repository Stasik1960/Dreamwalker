package daot;

import daot.network.GrabBoneSyncPayload;
import daot.network.HitboxBoneSyncPayload;
import daot.network.LegBoneSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

@Environment(EnvType.CLIENT)
public class ArmoredTitanRenderer extends GeoEntityRenderer<ArmoredTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();

   public ArmoredTitanRenderer(Context renderManager) {
      super(renderManager, new ArmoredTitanModel());
      this.shadowRadius = 2.0F;
      this.addRenderLayer(new AutoGlowingGeoLayer<ArmoredTitanEntity>(this) {
         protected Identifier getTextureResource(ArmoredTitanEntity animatable) {
            return ArmoredTitanModel.baseTexture();
         }
      });
      this.addRenderLayer(
         new GeoRenderLayer<ArmoredTitanEntity>(this) {
            private static final Identifier HARDENING_OVERLAY = new Identifier("dannys-aot", "textures/entity/armoredtitan_hardening_overlay.png");

            public void render(
               MatrixStack poseStack,
               ArmoredTitanEntity animatable,
               BakedGeoModel bakedModel,
               RenderLayer renderType,
               VertexConsumerProvider bufferSource,
               VertexConsumer buffer,
               float partialTick,
               int packedLight,
               int packedOverlay
            ) {
               if (animatable.isClimbEnabled()) {
                  RenderLayer overlayType = RenderLayer.getEntityCutoutNoCull(HARDENING_OVERLAY);
                  this.getRenderer()
                     .reRender(
                        bakedModel,
                        poseStack,
                        bufferSource,
                        animatable,
                        overlayType,
                        bufferSource.getBuffer(overlayType),
                        partialTick,
                        packedLight,
                        packedOverlay,
                        1F, 1F, 1F, 1F
                     );
               }
            }
         }
      );
   }

   public void render(
      ArmoredTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.syncHitboxBones(entity, partialTick);
      this.snapGrabbedToBone(entity, partialTick);
      ShifterHeadCameraAnchor.capture(((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("head")), entity, partialTick);
   }

   private void snapGrabbedToBone(ArmoredTitanEntity entity, float partialTick) {
      if (entity.getGrabPhase() != 0 || entity.getTossPhase() != 0) {
         ArmoredTitanGrabEntity grab = ArmoredTitanGrabEntity.getClientInstance(entity.getId());
         if (grab != null) {
            if (!grab.getPassengerList().isEmpty()) {
               GeoBone grabBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("grab_hitbox"));
               if (grabBone != null) {
                  Vec3d entityPos = entity.getLerpedPos(partialTick);
                  float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
                  Vec3d bonePos = TitanBoneCache.getAnimatedBoneWorldPos(grabBone, entityPos, yBodyRot);
                  grab.setRendererPosition(bonePos.x, bonePos.y, bonePos.z);
                  Entity passenger = grab.getPassengerList().get(0);
                  double cy = bonePos.y - passenger.getHeight() / 2.0;
                  passenger.setPosition(bonePos.x, cy, bonePos.z);
                  passenger.prevX = bonePos.x;
                  passenger.prevY = cy;
                  passenger.prevZ = bonePos.z;
                  passenger.lastRenderX = bonePos.x;
                  passenger.lastRenderY = cy;
                  passenger.lastRenderZ = bonePos.z;
               }
            }
         }
      }
   }

   public boolean shouldRender(ArmoredTitanEntity entity, Frustum frustum, double camX, double camY, double camZ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && mc.player.getVehicle() == entity ? true : super.shouldRender(entity, frustum, camX, camY, camZ);
   }

   private void syncHitboxBones(ArmoredTitanEntity entity, float partialTick) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getNetworkHandler() != null) {
         long currentTick = entity.getWorld().getTime();
         Long lastTick = lastSendTick.get(entity.getId());
         if (lastTick == null || lastTick != currentTick) {
            lastSendTick.put(entity.getId(), currentTick);
            double distSq = mc.player.squaredDistanceTo(entity);
            if (!(distSq > 4096.0)) {
               GeoBone napeBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("nape_hitbox"));
               GeoBone eyeBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("eye_hitbox"));
               GeoBone grabBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("grab_hitbox"));
               if (napeBone != null || eyeBone != null) {
                  Vec3d entityPos = entity.getLerpedPos(partialTick);
                  float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
                  double napeX = 0.0;
                  double napeY = 0.0;
                  double napeZ = 0.0;
                  double eyeX = 0.0;
                  double eyeY = 0.0;
                  double eyeZ = 0.0;
                  if (napeBone != null) {
                     Vec3d napePos = TitanBoneCache.getAnimatedBoneWorldPos(napeBone, entityPos, yBodyRot);
                     napeX = napePos.x;
                     napeY = napePos.y;
                     napeZ = napePos.z;
                  }

                  if (eyeBone != null) {
                     Vec3d eyePos = TitanBoneCache.getAnimatedBoneWorldPos(eyeBone, entityPos, yBodyRot);
                     eyeX = eyePos.x;
                     eyeY = eyePos.y;
                     eyeZ = eyePos.z;
                  }

                  ArmoredTitanNapeEntity napeEntity = ArmoredTitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null && napeBone != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  ArmoredTitanEyeEntity eyeEntity = ArmoredTitanEyeEntity.getClientInstance(entity.getId());
                  if (eyeEntity != null && eyeBone != null) {
                     eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
                  if (grabBone != null) {
                     Vec3d grabPos = TitanBoneCache.getAnimatedBoneWorldPos(grabBone, entityPos, yBodyRot);
                     ClientPlayNetworking.send(new GrabBoneSyncPayload(entity.getId(), grabPos.x, grabPos.y, grabPos.z));
                     ArmoredTitanGrabEntity grabEntity = ArmoredTitanGrabEntity.getClientInstance(entity.getId());
                     if (grabEntity != null) {
                        grabEntity.setRendererPosition(grabPos.x, grabPos.y, grabPos.z);
                     }
                  }

                  GeoBone leftLegBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("left_leg_hitbox"));
                  GeoBone rightLegBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("right_leg_hitbox"));
                  if (leftLegBone != null && rightLegBone != null) {
                     Vec3d leftPos = TitanBoneCache.getAnimatedBoneWorldPos(leftLegBone, entityPos, yBodyRot);
                     Vec3d rightPos = TitanBoneCache.getAnimatedBoneWorldPos(rightLegBone, entityPos, yBodyRot);
                     ClientPlayNetworking.send(new LegBoneSyncPayload(entity.getId(), leftPos.x, leftPos.y, leftPos.z, rightPos.x, rightPos.y, rightPos.z));
                     ArmoredTitanLegEntity leftLeg = ArmoredTitanLegEntity.getClientInstance(entity.getId(), true);
                     if (leftLeg != null) {
                        leftLeg.setRendererPosition(leftPos.x, leftPos.y, leftPos.z);
                     }

                     ArmoredTitanLegEntity rightLeg = ArmoredTitanLegEntity.getClientInstance(entity.getId(), false);
                     if (rightLeg != null) {
                        rightLeg.setRendererPosition(rightPos.x, rightPos.y, rightPos.z);
                     }
                  }
               }
            }
         }
      }
   }
}
