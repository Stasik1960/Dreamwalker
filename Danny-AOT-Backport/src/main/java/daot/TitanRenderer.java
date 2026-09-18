package daot;

import daot.network.HitboxBoneSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public class TitanRenderer extends GeoEntityRenderer<TitanEntity> {
   private static final Map<Integer, Long> lastSendTick = new HashMap<>();
   private static final Map<String, double[][]> BONE_CUBES = new HashMap<>();

   public TitanRenderer(Context context) {
      super(context, new TitanModel());
   }

   public void render(TitanEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      this.cacheBonePositions(entity, partialTick);
      this.syncHitboxBones(entity, partialTick);
   }

   private void cacheBonePositions(TitanEntity entity, float partialTick) {
      Vec3d entityPos = entity.getLerpedPos(partialTick);
      double scale = daot.compat.attributes.LivingEntityAttributeCompat.getScale(entity);
      double yawRad = Math.toRadians(entity.bodyYaw);
      double cos = Math.cos(yawRad);
      double sin = Math.sin(yawRad);

      for (String boneName : TitanBoneCache.HOOKABLE_BONES) {
         GeoBone bone = ((software.bernie.geckolib.cache.object.GeoBone) this.getGeoModel().getAnimationProcessor().getBone(boneName));
         if (bone != null) {
            Vec3d boneWorldPos = this.getBoneWorldPosition(bone, entity, entityPos, scale);
            TitanBoneCache.updateBonePosition(entity.getId(), boneName, boneWorldPos);
            double[][] cubes = BONE_CUBES.get(boneName);
            if (cubes != null) {
               List<Box> aabbs = new ArrayList<>();

               for (double[] cube : cubes) {
                  Box aabb = this.transformCubeToWorld(cube, entityPos, scale, cos, sin);
                  aabbs.add(aabb);
               }

               TitanBoneCache.updateBoneCubes(entity.getId(), boneName, aabbs);
            }
         }
      }
   }

   private Box transformCubeToWorld(double[] cube, Vec3d entityPos, double scale, double cos, double sin) {
      double minX = cube[0] / 16.0 * scale;
      double minY = cube[1] / 16.0 * scale;
      double minZ = cube[2] / 16.0 * scale;
      double maxX = cube[3] / 16.0 * scale;
      double maxY = cube[4] / 16.0 * scale;
      double maxZ = cube[5] / 16.0 * scale;
      double[][] corners = new double[][]{
         {minX, minY, minZ},
         {maxX, minY, minZ},
         {minX, maxY, minZ},
         {maxX, maxY, minZ},
         {minX, minY, maxZ},
         {maxX, minY, maxZ},
         {minX, maxY, maxZ},
         {maxX, maxY, maxZ}
      };
      double worldMinX = Double.MAX_VALUE;
      double worldMinY = Double.MAX_VALUE;
      double worldMinZ = Double.MAX_VALUE;
      double worldMaxX = -Double.MAX_VALUE;
      double worldMaxY = -Double.MAX_VALUE;
      double worldMaxZ = -Double.MAX_VALUE;

      for (double[] corner : corners) {
         double rotX = corner[0] * cos - corner[2] * sin;
         double rotZ = corner[0] * sin + corner[2] * cos;
         double wx = entityPos.x + rotX;
         double wy = entityPos.y + corner[1];
         double wz = entityPos.z + rotZ;
         worldMinX = Math.min(worldMinX, wx);
         worldMinY = Math.min(worldMinY, wy);
         worldMinZ = Math.min(worldMinZ, wz);
         worldMaxX = Math.max(worldMaxX, wx);
         worldMaxY = Math.max(worldMaxY, wy);
         worldMaxZ = Math.max(worldMaxZ, wz);
      }

      return new Box(worldMinX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ);
   }

   private Vec3d getBoneWorldPosition(GeoBone bone, TitanEntity entity, Vec3d entityPos, double scale) {
      float pivotX = bone.getPivotX();
      float pivotY = bone.getPivotY();
      float pivotZ = bone.getPivotZ();
      double localX = pivotX / 16.0 * scale;
      double localY = pivotY / 16.0 * scale;
      double localZ = pivotZ / 16.0 * scale;
      double yawRad = Math.toRadians(entity.bodyYaw);
      double cos = Math.cos(yawRad);
      double sin = Math.sin(yawRad);
      double rotatedX = localX * cos - localZ * sin;
      double rotatedZ = localX * sin + localZ * cos;
      return new Vec3d(entityPos.x + rotatedX, entityPos.y + localY, entityPos.z + rotatedZ);
   }

   private void syncHitboxBones(TitanEntity entity, float partialTick) {
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

                  TitanNapeEntity napeEntity = TitanNapeEntity.getClientInstance(entity.getId());
                  if (napeEntity != null && napeBone != null) {
                     napeEntity.setRendererPosition(napeX, napeY, napeZ);
                  }

                  TitanEyeEntity eyeEntity = TitanEyeEntity.getClientInstance(entity.getId());
                  if (eyeEntity != null && eyeBone != null) {
                     eyeEntity.setRendererPosition(eyeX, eyeY, eyeZ);
                  }

                  ClientPlayNetworking.send(new HitboxBoneSyncPayload(entity.getId(), napeX, napeY, napeZ, eyeX, eyeY, eyeZ));
               }
            }
         }
      }
   }

   protected float getDeathMaxRotation(TitanEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(TitanEntity animatable, float u, float partialTick) {
      return OverlayTexture.DEFAULT_UV;
   }

   static {
      BONE_CUBES.put("Belly", new double[][]{{-14.17, 46.89, -10.66, 14.17, 70.87, 11.14}});
      BONE_CUBES.put("UpperTorso", new double[][]{{-13.08, 70.87, -6.3, 13.08, 86.13, 11.14}, {-12.36, 85.5, -3.45, 12.36, 87.98, 8.9}});
      BONE_CUBES.put("LowerTorso", new double[][]{{-12.0, 44.0, -8.0, 12.0, 58.0, 8.0}});
      BONE_CUBES.put("LeftShoulder", new double[][]{{13.08, 70.87, -2.03, 20.98, 86.13, 6.87}});
      BONE_CUBES.put("RightShoulder", new double[][]{{-20.98, 70.87, -2.03, -13.08, 86.13, 6.87}});
      BONE_CUBES.put("LeftUpperArm", new double[][]{{13.08, 55.61, -1.94, 21.8, 70.87, 6.78}});
      BONE_CUBES.put("RightUpperArm", new double[][]{{-21.8, 55.61, -1.94, -13.08, 70.87, 6.78}});
      BONE_CUBES.put("LeftHand", new double[][]{{13.08, 36.0, -2.4, 22.4, 55.6, 7.3}});
      BONE_CUBES.put("RightHand", new double[][]{{-22.4, 36.0, -2.4, -13.08, 55.6, 7.3}});
      BONE_CUBES.put("UpperHead", new double[][]{{-7.5, 93.0, -5.0, 7.5, 108.0, 8.0}});
      BONE_CUBES.put("LowerHead", new double[][]{{-6.0, 87.0, -3.0, 6.0, 95.0, 6.0}});
      BONE_CUBES.put("LeftThigh", new double[][]{{3.0, 24.0, -4.5, 13.0, 47.0, 6.5}});
      BONE_CUBES.put("RightThigh", new double[][]{{-13.0, 24.0, -4.5, -3.0, 47.0, 6.5}});
      BONE_CUBES.put("LeftUpperLeg", new double[][]{{3.5, 12.0, -4.0, 12.5, 26.0, 6.0}});
      BONE_CUBES.put("RightUpperLeg", new double[][]{{-12.5, 12.0, -4.0, -3.5, 26.0, 6.0}});
      BONE_CUBES.put("LeftLowerLeg", new double[][]{{3.5, 0.0, -4.0, 12.5, 14.0, 6.0}});
      BONE_CUBES.put("RightLowerLeg", new double[][]{{-12.5, 0.0, -4.0, -3.5, 14.0, 6.0}});
   }
}
