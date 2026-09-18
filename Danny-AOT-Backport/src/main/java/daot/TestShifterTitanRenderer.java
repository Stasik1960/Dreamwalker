package daot;

import daot.network.GrabBoneSyncPayload;
import daot.network.HitboxBoneSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

@Environment(EnvType.CLIENT)
public class TestShifterTitanRenderer extends GeoEntityRenderer<TestShifterTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();
   private static final String[] HEAD_BONES_TO_HIDE = new String[]{"head", "head2"};

   public TestShifterTitanRenderer(Context context) {
      super(context, new TestShifterTitanModel());
      this.shadowRadius = 1.5F;
      this.addRenderLayer(new AutoGlowingGeoLayer<TestShifterTitanEntity>(this) {
         protected Identifier getTextureResource(TestShifterTitanEntity animatable) {
            return TestShifterTitanModel.baseTexture();
         }
      });
   }

   public void render(
      TestShifterTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      boolean hideHead = isLocalRiderFirstPerson(entity);
      GeoBone[] hiddenBones = null;
      if (hideHead) {
         hiddenBones = new GeoBone[HEAD_BONES_TO_HIDE.length];

         for (int i = 0; i < HEAD_BONES_TO_HIDE.length; i++) {
            GeoBone bone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone(HEAD_BONES_TO_HIDE[i]));
            if (bone != null) {
               bone.setHidden(true);
            }

            hiddenBones[i] = bone;
         }
      }

      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      if (hiddenBones != null) {
         for (GeoBone bone : hiddenBones) {
            if (bone != null) {
               bone.setHidden(false);
            }
         }
      }

      this.captureHeadBoneWorldPos(entity, partialTick);
      this.syncHitboxBones(entity, partialTick);
      this.snapGrabbedToBone(entity, partialTick);
   }

   private void snapGrabbedToBone(TestShifterTitanEntity entity, float partialTick) {
      if (entity.isGrabbing()) {
         AttackTitanGrabEntity grab = AttackTitanGrabEntity.getClientInstance(entity.getId());
         if (grab != null && !grab.getPassengerList().isEmpty()) {
            GeoBone mouthBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("mouth_hitbox"));
            if (mouthBone != null) {
               Vec3d entityPos = entity.getLerpedPos(partialTick);
               float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
               Vec3d bonePos = TitanBoneCache.getAnimatedBoneWorldPos(mouthBone, entityPos, yBodyRot);
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

   private static boolean isLocalRiderFirstPerson(TestShifterTitanEntity entity) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return false;
      } else {
         return mc.player.getVehicle() != entity ? false : mc.options.getPerspective() == Perspective.FIRST_PERSON;
      }
   }

   private void captureHeadBoneWorldPos(TestShifterTitanEntity entity, float partialTick) {
      GeoBone headBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("head"));
      if (headBone == null) {
         TestShifterCameraAnchor.clear(entity.getId());
      } else {
         Vec3d entityPos = entity.getLerpedPos(partialTick);
         float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
         Vec3d worldPos = TitanBoneCache.getAnimatedBoneWorldPos(headBone, entityPos, yBodyRot);
         TestShifterCameraAnchor.set(entity.getId(), worldPos);
      }
   }

   private void syncHitboxBones(TestShifterTitanEntity entity, float partialTick) {
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
               GeoBone mouthBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("mouth_hitbox"));
               if (napeBone != null || eyeBone != null || mouthBone != null) {
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
                  if (mouthBone != null) {
                     Vec3d mouthPos = TitanBoneCache.getAnimatedBoneWorldPos(mouthBone, entityPos, yBodyRot);
                     ClientPlayNetworking.send(new GrabBoneSyncPayload(entity.getId(), mouthPos.x, mouthPos.y, mouthPos.z));
                     AttackTitanGrabEntity grabEntity = AttackTitanGrabEntity.getClientInstance(entity.getId());
                     if (grabEntity != null) {
                        grabEntity.setRendererPosition(mouthPos.x, mouthPos.y, mouthPos.z);
                     }
                  }
               }
            }
         }
      }
   }

   protected float getDeathMaxRotation(TestShifterTitanEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(TestShifterTitanEntity animatable, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }
}
