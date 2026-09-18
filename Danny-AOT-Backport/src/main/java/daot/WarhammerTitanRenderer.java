package daot;

import daot.network.HammerBoneSyncPayload;
import daot.network.HitboxBoneSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class WarhammerTitanRenderer extends GeoEntityRenderer<WarhammerTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();
   private static final String[] HAMMER_BONES = new String[]{"hammer", "group", "hammer_hitbox"};

   public WarhammerTitanRenderer(Context renderManager) {
      super(renderManager, new WarhammerTitanModel());
      this.shadowRadius = 2.0F;
   }

   public void render(
      WarhammerTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      boolean hideHammer = !entity.hasHammer() && (!entity.isTitanAttacking() || entity.getAttackNumber() != 11);
      if (hideHammer) {
         for (String name : HAMMER_BONES) {
            GeoBone bone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone(name));
            if (bone != null) {
               bone.setHidden(true);
            }
         }
      }

      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      if (hideHammer) {
         for (String namex : HAMMER_BONES) {
            GeoBone bone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone(namex));
            if (bone != null) {
               bone.setHidden(false);
            }
         }
      }

      this.cacheRightfeetBone(entity, partialTick);
      this.syncHitboxBones(entity, partialTick);
      ShifterHeadCameraAnchor.capture(((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("head")), entity, partialTick);
   }

   public boolean shouldRender(WarhammerTitanEntity entity, Frustum frustum, double camX, double camY, double camZ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && mc.player.getVehicle() == entity ? true : super.shouldRender(entity, frustum, camX, camY, camZ);
   }

   private void cacheRightfeetBone(WarhammerTitanEntity entity, float partialTick) {
      GeoBone rightfeetBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("rightfeet"));
      if (rightfeetBone != null) {
         Vec3d entityPos = entity.getLerpedPos(partialTick);
         float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
         Vec3d footPos = TitanBoneCache.getAnimatedBoneWorldPos(rightfeetBone, entityPos, yBodyRot);
         TitanBoneCache.updateBonePosition(entity.getId(), "rightfeet", footPos);
      }
   }

   private void syncHitboxBones(WarhammerTitanEntity entity, float partialTick) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getNetworkHandler() != null) {
         long currentTick = entity.getWorld().getTime();
         Long lastTick = lastSendTick.get(entity.getId());
         if (lastTick == null || lastTick != currentTick) {
            lastSendTick.put(entity.getId(), currentTick);
            double distSq = mc.player.squaredDistanceTo(entity);
            if (!(distSq > 4096.0)) {
               GeoBone eyeBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("eye_hitbox"));
               GeoBone hammerBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("hammer_hitbox"));
               Vec3d entityPos = entity.getLerpedPos(partialTick);
               float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
               double napeX = 0.0;
               double napeY = 0.0;
               double napeZ = 0.0;
               boolean hasWirePos = false;
               int crystalId = entity.getCrystalShellId();
               if (crystalId != -1 && mc.world != null && mc.world.getEntityById(crystalId) instanceof CrystalShellWarhammerEntity crystal) {
                  Vec3d crystalPos = crystal.getLerpedPos(partialTick);
                  Vec3d footWorldPos = TitanBoneCache.getBonePosition(entity.getId(), "rightfeet");
                  if (footWorldPos == null) {
                     footWorldPos = entityPos;
                  }

                  napeX = (crystalPos.x + footWorldPos.x) / 2.0;
                  napeY = (crystalPos.y + footWorldPos.y) / 2.0;
                  napeZ = (crystalPos.z + footWorldPos.z) / 2.0;
                  hasWirePos = true;
               }

               double eyeX = 0.0;
               double eyeY = 0.0;
               double eyeZ = 0.0;
               if (eyeBone != null) {
                  Vec3d eyePos = TitanBoneCache.getAnimatedBoneWorldPos(eyeBone, entityPos, yBodyRot);
                  eyeX = eyePos.x;
                  eyeY = eyePos.y;
                  eyeZ = eyePos.z;
               }

               WarhammerTitanNapeEntity napeEntity = WarhammerTitanNapeEntity.getClientInstance(entity.getId());
               if (napeEntity != null && hasWirePos) {
                  napeEntity.setRendererPosition(napeX, napeY, napeZ);
               }

               WarhammerTitanEyeEntity eyeEntity = WarhammerTitanEyeEntity.getClientInstance(entity.getId());
               if (eyeEntity != null && eyeBone != null) {
                  eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
               }

               ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
               if (hammerBone != null) {
                  Vec3d hammerPos = TitanBoneCache.getAnimatedBoneWorldPos(hammerBone, entityPos, yBodyRot);
                  ClientPlayNetworking.send(new HammerBoneSyncPayload(entity.getId(), hammerPos.x, hammerPos.y, hammerPos.z));
               }
            }
         }
      }
   }
}
