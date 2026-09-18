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
public class SadTitanRenderer extends GeoEntityRenderer<SadTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();

   public SadTitanRenderer(Context context) {
      super(context, new SadTitanModel());
   }

   public void render(SadTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.syncHitboxBones(entity, partialTick);
   }

   private void syncHitboxBones(SadTitanEntity entity, float partialTick) {
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

                  SadTitanNapeEntity napeEntity = SadTitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null && napeBone != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  SadTitanEyeEntity eyeEntity = SadTitanEyeEntity.getClientInstance(entity.getId());
                  if (eyeEntity != null && eyeBone != null) {
                     eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
               }
            }
         }
      }
   }

   protected float getDeathMaxRotation(SadTitanEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(SadTitanEntity animatable, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }
}
