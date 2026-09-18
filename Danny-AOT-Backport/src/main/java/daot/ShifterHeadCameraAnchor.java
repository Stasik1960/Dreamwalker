package daot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.cache.object.GeoBone;

@Environment(EnvType.CLIENT)
public final class ShifterHeadCameraAnchor {
   private static final Map<Integer, Vec3d> byEntityId = new ConcurrentHashMap<>();

   private ShifterHeadCameraAnchor() {
   }

   public static void set(int entityId, Vec3d worldPos) {
      byEntityId.put(entityId, worldPos);
   }

   public static void clear(int entityId) {
      byEntityId.remove(entityId);
   }

   public static Vec3d get(int entityId) {
      return byEntityId.get(entityId);
   }

   public static void capture(GeoBone headBone, LivingEntity entity, float partialTick) {
      if (headBone == null) {
         clear(entity.getId());
      } else {
         Vec3d entityPos = entity.getLerpedPos(partialTick);
         float yBodyRot = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
         Vec3d worldPos = TitanBoneCache.getAnimatedBoneWorldPos(headBone, entityPos, yBodyRot);
         set(entity.getId(), worldPos.subtract(entityPos));
      }
   }
}
