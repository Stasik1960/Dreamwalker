package daot;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class TitanGrabUtil {
   private TitanGrabUtil() {
   }

   public static boolean canReachTarget(MobEntity titan, LivingEntity target) {
      double grabY = Math.min(titan.getY() + titan.getHeight() * 0.4, target.getEyeY());
      Vec3d grabOrigin = new Vec3d(titan.getX(), grabY, titan.getZ());
      Vec3d targetCenter = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
      RaycastContext ctx = new RaycastContext(grabOrigin, targetCenter, ShapeType.COLLIDER, FluidHandling.NONE, titan);
      return titan.getWorld().raycast(ctx).getType() == Type.MISS;
   }
}
