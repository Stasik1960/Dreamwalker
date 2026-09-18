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
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class BeastTitanRenderer extends GeoEntityRenderer<BeastTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();
   private static final String[] ROCK_BONES = new String[]{
      "rock", "rocklower", "rocktop", "rock1", "rock2", "rock3", "rock4", "rock5", "rock6", "rock7", "rock8"
   };

   public BeastTitanRenderer(Context context) {
      super(context, new BeastTitanModel());
      this.shadowRadius = 2.0F;
   }

   public void render(BeastTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      boolean hideRocks = entity.getGrabbedEntityId() != -1
         && (entity.getRockThrowPhase() == 1 || entity.getRockThrowPhase() == 2 || entity.getRockThrowPhase() == 3);
      if (hideRocks) {
         for (String name : ROCK_BONES) {
            GeoBone bone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone(name));
            if (bone != null) {
               bone.setHidden(true);
            }
         }
      }

      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      if (hideRocks) {
         for (String namex : ROCK_BONES) {
            GeoBone bone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone(namex));
            if (bone != null) {
               bone.setHidden(false);
            }
         }
      }

      this.syncHitboxBones(entity, partialTick);
      this.snapGrabbedToBone(entity, partialTick);
      ShifterHeadCameraAnchor.capture(((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("head")), entity, partialTick);
   }

   public boolean shouldRender(BeastTitanEntity entity, Frustum frustum, double camX, double camY, double camZ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && mc.player.getVehicle() == entity ? true : super.shouldRender(entity, frustum, camX, camY, camZ);
   }

   private void snapGrabbedToBone(BeastTitanEntity entity, float partialTick) {
      if (entity.getGrabbedEntityId() != -1) {
         BeastTitanGrabEntity grab = BeastTitanGrabEntity.getClientInstance(entity.getId());
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

   private void syncHitboxBones(BeastTitanEntity entity, float partialTick) {
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

                  BeastTitanNapeEntity napeEntity = BeastTitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null && napeBone != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  BeastTitanEyeEntity eyeEntity = BeastTitanEyeEntity.getClientInstance(entity.getId());
                  if (eyeEntity != null && eyeBone != null) {
                     eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
                  if (grabBone != null) {
                     Vec3d grabPos = TitanBoneCache.getAnimatedBoneWorldPos(grabBone, entityPos, yBodyRot);
                     ClientPlayNetworking.send(new GrabBoneSyncPayload(entity.getId(), grabPos.x, grabPos.y, grabPos.z));
                     BeastTitanGrabEntity grabEntity = BeastTitanGrabEntity.getClientInstance(entity.getId());
                     if (grabEntity != null) {
                        grabEntity.setRendererPosition(grabPos.x, grabPos.y, grabPos.z);
                     }
                  }
               }
            }
         }
      }
   }

   protected float getDeathMaxRotation(BeastTitanEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(BeastTitanEntity animatable, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }
}
