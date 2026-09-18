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
public class CrawlingAbnormalTitanRenderer extends GeoEntityRenderer<CrawlingAbnormalTitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();
   private static final Map<Integer, Boolean> loggedMissing = new HashMap<>();

   public CrawlingAbnormalTitanRenderer(Context context) {
      super(context, new CrawlingAbnormalTitanModel());
   }

   public void render(
      CrawlingAbnormalTitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
   ) {
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.syncHitboxBones(entity, partialTick);
   }

   private void syncHitboxBones(CrawlingAbnormalTitanEntity entity, float partialTick) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getNetworkHandler() != null) {
         long currentTick = entity.getWorld().getTime();
         Long lastTick = lastSendTick.get(entity.getId());
         if (lastTick == null || lastTick != currentTick) {
            lastSendTick.put(entity.getId(), currentTick);
            double distSq = mc.player.squaredDistanceTo(entity);
            if (!(distSq > 4096.0)) {
               GeoBone napeBone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone("nape_hitbox"));
               if (napeBone == null) {
                  if (!loggedMissing.containsKey(entity.getId())) {
                     loggedMissing.put(entity.getId(), true);
                     System.out.println("[CrawlingAbnormalTitanHitboxSync] WARNING: nape_hitbox bone not found in model!");
                  }
               } else {
                  Vec3d entityPos = entity.getLerpedPos(partialTick);
                  float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
                  Vec3d napePos = TitanBoneCache.getAnimatedBoneWorldPos(napeBone, entityPos, yBodyRot);
                  double napeX = napePos.x;
                  double napeY = napePos.y;
                  double napeZ = napePos.z;
                  FritzTitanNapeEntity napeEntity = FritzTitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, 0.0, 0.0, 0.0));
               }
            }
         }
      }
   }

   protected float getDeathMaxRotation(CrawlingAbnormalTitanEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(CrawlingAbnormalTitanEntity animatable, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }
}
