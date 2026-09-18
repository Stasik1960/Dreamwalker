package daot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;

@Environment(EnvType.CLIENT)
public class TitanBoneCache {
   private static final Map<Integer, Map<String, Vec3d>> bonePositions = new ConcurrentHashMap<>();
   private static final Map<Integer, Map<String, List<Box>>> boneCubeAABBs = new ConcurrentHashMap<>();
   public static final List<String> HOOKABLE_BONES = List.of(
      "UpperTorso",
      "LowerTorso",
      "Belly",
      "LeftShoulder",
      "RightShoulder",
      "LeftUpperArm",
      "RightUpperArm",
      "LeftHand",
      "RightHand",
      "UpperHead",
      "LowerHead",
      "LeftThigh",
      "RightThigh",
      "LeftUpperLeg",
      "RightUpperLeg",
      "LeftLowerLeg",
      "RightLowerLeg"
   );

   public static void updateBonePosition(int entityId, String boneName, Vec3d worldPos) {
      bonePositions.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>()).put(boneName, worldPos);
   }

   public static void updateBoneCubes(int entityId, String boneName, List<Box> cubes) {
      boneCubeAABBs.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>()).put(boneName, new ArrayList<>(cubes));
   }

   public static Vec3d getBonePosition(int entityId, String boneName) {
      Map<String, Vec3d> bones = bonePositions.get(entityId);
      return bones != null ? bones.get(boneName) : null;
   }

   public static Map<String, Vec3d> getAllBonePositions(int entityId) {
      return bonePositions.getOrDefault(entityId, new HashMap<>());
   }

   public static String findClosestBone(int entityId, Vec3d targetPoint) {
      Map<String, Vec3d> bones = bonePositions.get(entityId);
      if (bones != null && !bones.isEmpty()) {
         String closestBone = null;
         double closestDistance = Double.MAX_VALUE;

         for (String boneName : HOOKABLE_BONES) {
            Vec3d bonePos = bones.get(boneName);
            if (bonePos != null) {
               double distance = bonePos.squaredDistanceTo(targetPoint);
               if (distance < closestDistance) {
                  closestDistance = distance;
                  closestBone = boneName;
               }
            }
         }

         return closestBone;
      } else {
         return null;
      }
   }

   public static String findClosestBoneToRay(int entityId, Vec3d rayOrigin, Vec3d rayDirection, double maxDistance) {
      Map<String, Vec3d> bones = bonePositions.get(entityId);
      if (bones != null && !bones.isEmpty()) {
         String closestBone = null;
         double closestDistance = Double.MAX_VALUE;
         double boneTargetRadius = 1.5;

         for (String boneName : HOOKABLE_BONES) {
            Vec3d bonePos = bones.get(boneName);
            if (bonePos != null) {
               Vec3d toBone = bonePos.subtract(rayOrigin);
               double projectionLength = toBone.dotProduct(rayDirection);
               if (!(projectionLength < 0.0) && !(projectionLength > maxDistance)) {
                  Vec3d closestPointOnRay = rayOrigin.add(rayDirection.multiply(projectionLength));
                  double perpendicularDistance = bonePos.distanceTo(closestPointOnRay);
                  if (!(perpendicularDistance > boneTargetRadius) && perpendicularDistance < closestDistance) {
                     closestDistance = perpendicularDistance;
                     closestBone = boneName;
                  }
               }
            }
         }

         return closestBone;
      } else {
         return null;
      }
   }

   public static TitanBoneCache.CubeHitResult raycastCubes(int entityId, Vec3d rayOrigin, Vec3d rayDirection, double maxDistance) {
      Map<String, List<Box>> cubeMap = boneCubeAABBs.get(entityId);
      if (cubeMap != null && !cubeMap.isEmpty()) {
         TitanBoneCache.CubeHitResult closestHit = null;
         double closestDistance = maxDistance;
         Vec3d rayEnd = rayOrigin.add(rayDirection.multiply(maxDistance));

         for (String boneName : HOOKABLE_BONES) {
            List<Box> cubes = cubeMap.get(boneName);
            if (cubes != null) {
               for (Box cube : cubes) {
                  Optional<Vec3d> hitOpt = cube.raycast(rayOrigin, rayEnd);
                  if (hitOpt.isPresent()) {
                     Vec3d hitPoint = hitOpt.get();
                     double distance = rayOrigin.distanceTo(hitPoint);
                     if (distance < closestDistance) {
                        closestDistance = distance;
                        closestHit = new TitanBoneCache.CubeHitResult(boneName, hitPoint, distance);
                     }
                  }
               }
            }
         }

         return closestHit;
      } else {
         return null;
      }
   }

   public static Vec3d getAnimatedBoneWorldPos(GeoBone targetBone, Vec3d entityPos, float yBodyRot) {
      List<GeoBone> chain = new ArrayList<>();

      for (GeoBone current = targetBone; current != null; current = current.getParent()) {
         chain.add(0, current);
      }

      Matrix4f matrix = new Matrix4f().identity();

      for (int i = 0; i < chain.size(); i++) {
         GeoBone bone = chain.get(i);
         boolean isTarget = i == chain.size() - 1;
         matrix.translate(-bone.getPosX() / 16.0F, bone.getPosY() / 16.0F, bone.getPosZ() / 16.0F);
         matrix.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
         if (!isTarget) {
            if (bone.getRotZ() != 0.0F) {
               matrix.rotateZ(bone.getRotZ());
            }

            if (bone.getRotY() != 0.0F) {
               matrix.rotateY(bone.getRotY());
            }

            if (bone.getRotX() != 0.0F) {
               matrix.rotateX(bone.getRotX());
            }

            matrix.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
            matrix.translate(-bone.getPivotX() / 16.0F, -bone.getPivotY() / 16.0F, -bone.getPivotZ() / 16.0F);
         }
      }

      Vector4f localPos = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
      matrix.transform(localPos);
      double yawRad = Math.toRadians(180.0 - yBodyRot);
      double cos = Math.cos(yawRad);
      double sin = Math.sin(yawRad);
      double rotatedX = localPos.x() * cos + localPos.z() * sin;
      double rotatedZ = -localPos.x() * sin + localPos.z() * cos;
      return new Vec3d(entityPos.x + rotatedX, entityPos.y + localPos.y(), entityPos.z + rotatedZ);
   }

   public static void removeTitan(int entityId) {
      bonePositions.remove(entityId);
      boneCubeAABBs.remove(entityId);
   }

   public static void clear() {
      bonePositions.clear();
      boneCubeAABBs.clear();
   }

   @Environment(EnvType.CLIENT)
   public static class CubeHitResult {
      public final String boneName;
      public final Vec3d hitPoint;
      public final double distance;

      public CubeHitResult(String boneName, Vec3d hitPoint, double distance) {
         this.boneName = boneName;
         this.hitPoint = hitPoint;
         this.distance = distance;
      }
   }
}
