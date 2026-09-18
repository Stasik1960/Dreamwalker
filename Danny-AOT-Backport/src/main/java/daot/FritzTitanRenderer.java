package daot;

import daot.network.HitboxBoneSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class FritzTitanRenderer extends GeoEntityRenderer<FritzTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();
   private static final Map<Integer, Boolean> loggedMissing = new HashMap<>();

   public FritzTitanRenderer(Context context) {
      super(context, new FritzTitanModel());
   }

   public void render(FritzTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.syncHitboxBones(entity, partialTick);
   }

   private void syncHitboxBones(FritzTitanEntity entity, float partialTick) {
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
               if (napeBone == null && eyeBone == null) {
                  if (!loggedMissing.containsKey(entity.getId())) {
                     loggedMissing.put(entity.getId(), true);
                     System.out.println("[FritzHitboxSync] WARNING: nape_hitbox and eye_hitbox bones not found in model!");
                  }
               } else {
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

                  FritzTitanNapeEntity napeEntity = FritzTitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null && napeBone != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  FritzTitanEyeEntity eyeEntity = FritzTitanEyeEntity.getClientInstance(entity.getId());
                  if (eyeEntity != null && eyeBone != null) {
                     eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
               }
            }
         }
      }
   }

   protected float getDeathMaxRotation(FritzTitanEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(FritzTitanEntity animatable, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }
}
