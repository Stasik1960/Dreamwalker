package daot;

import daot.network.GrabBoneSyncPayload;
import daot.network.HitboxBoneSyncPayload;
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

@Environment(EnvType.CLIENT)
public class AttackTitanRenderer extends GeoEntityRenderer<AttackTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();

   public AttackTitanRenderer(Context renderManager) {
      super(renderManager, new AttackTitanModel());
      this.shadowRadius = 2.0F;
      this.addRenderLayer(new AutoGlowingGeoLayer<AttackTitanEntity>(this) {
         protected Identifier getTextureResource(AttackTitanEntity animatable) {
            return AttackTitanModel.textureFor(0);
         }
      });
   }

   public void render(AttackTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.syncHitboxBones(entity, partialTick);
      this.snapGrabbedToBone(entity, partialTick);
      ShifterHeadCameraAnchor.capture(((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("head")), entity, partialTick);
   }

   public boolean shouldRender(AttackTitanEntity entity, Frustum frustum, double camX, double camY, double camZ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && mc.player.getVehicle() == entity ? true : super.shouldRender(entity, frustum, camX, camY, camZ);
   }

   private static float hardeningProgress(AttackTitanEntity entity, float partialTick) {
      long start = entity.getHardeningTransitionStartTick();
      if (start < 0L) {
         return 1.0F;
      } else {
         float elapsed = (float)(entity.getWorld().getTime() - start) + partialTick;
         if (elapsed <= 0.0F) {
            return 0.0F;
         } else {
            return elapsed >= 20.0F ? 1.0F : elapsed / 20.0F;
         }
      }
   }

   private static boolean isTransitioning(AttackTitanEntity entity, float partialTick) {
      if (entity.getDisplayedHardeningMode() == entity.getPreviousHardeningMode()) {
         return false;
      } else {
         float p = hardeningProgress(entity, partialTick);
         return p > 0.0F && p < 1.0F;
      }
   }

   public void actuallyRender(
      MatrixStack poseStack,
      AttackTitanEntity animatable,
      BakedGeoModel model,
      RenderLayer renderType,
      VertexConsumerProvider bufferSource,
      VertexConsumer buffer,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      float bpRed, float bpGreen, float bpBlue, float bpAlpha
   ) {
      int colour = daot.compat.RenderColors.pack(bpRed,bpGreen,bpBlue,bpAlpha);
      if (!isReRender && isTransitioning(animatable, partialTick)) {
         float progress = hardeningProgress(animatable, partialTick);
         int rgb = colour & 16777215;
         int newAlpha = Math.max(0, Math.min(255, (int)(progress * 255.0F)));
         Identifier oldTex = AttackTitanModel.textureFor(animatable.getPreviousHardeningMode());
         RenderLayer oldRt = RenderLayer.getEntityCutoutNoCull(oldTex);
         VertexConsumer oldBuffer = bufferSource.getBuffer(oldRt);
         super.actuallyRender(
            poseStack, animatable, model, oldRt, bufferSource, oldBuffer, isReRender, partialTick, packedLight, packedOverlay,daot.compat.RenderColors.red(0xFF000000 | rgb), daot.compat.RenderColors.green(0xFF000000 | rgb), daot.compat.RenderColors.blue(0xFF000000 | rgb), daot.compat.RenderColors.alpha(0xFF000000 | rgb));
         Identifier newTex = AttackTitanModel.textureFor(animatable.getDisplayedHardeningMode());
         RenderLayer newRt = RenderLayer.getEntityTranslucent(newTex);
         VertexConsumer newBuffer = bufferSource.getBuffer(newRt);
         this.reRender(model, poseStack, bufferSource, animatable, newRt, newBuffer, partialTick, packedLight, packedOverlay,daot.compat.RenderColors.red(newAlpha << 24 | rgb), daot.compat.RenderColors.green(newAlpha << 24 | rgb), daot.compat.RenderColors.blue(newAlpha << 24 | rgb), daot.compat.RenderColors.alpha(newAlpha << 24 | rgb));
      } else {
         super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,daot.compat.RenderColors.red(colour), daot.compat.RenderColors.green(colour), daot.compat.RenderColors.blue(colour), daot.compat.RenderColors.alpha(colour));
      }
   }

   private void snapGrabbedToBone(AttackTitanEntity entity, float partialTick) {
      if (entity.getGrabPhase() != 0) {
         AttackTitanGrabEntity grab = AttackTitanGrabEntity.getClientInstance(entity.getId());
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

   private void syncHitboxBones(AttackTitanEntity entity, float partialTick) {
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

                  AttackTitanNapeEntity napeEntity = AttackTitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null && napeBone != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  AttackTitanEyeEntity eyeEntity = AttackTitanEyeEntity.getClientInstance(entity.getId());
                  if (eyeEntity != null && eyeBone != null) {
                     eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
                  if (grabBone != null) {
                     Vec3d grabPos = TitanBoneCache.getAnimatedBoneWorldPos(grabBone, entityPos, yBodyRot);
                     ClientPlayNetworking.send(new GrabBoneSyncPayload(entity.getId(), grabPos.x, grabPos.y, grabPos.z));
                     AttackTitanGrabEntity grabEntity = AttackTitanGrabEntity.getClientInstance(entity.getId());
                     if (grabEntity != null) {
                        grabEntity.setRendererPosition(grabPos.x, grabPos.y, grabPos.z);
                     }
                  }
               }
            }
         }
      }
   }
}
