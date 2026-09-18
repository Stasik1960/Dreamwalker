package daot;

import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class StrwsAim {
   public static final int WIRE_COUNT = 6;
   public static final double MAX_WIRE = 75.0;
   private static final double[][] SHOOT_PIVOTS = new double[][]{
      {8.0, 43.9569, -10.03448},
      {-8.0, 43.9569, -10.03448},
      {-8.0, 27.9569, -10.03448},
      {8.0, 27.9569, -10.03448},
      {8.0, 11.9569, -10.03448},
      {-8.0, 11.9569, -10.03448}
   };
   private static final double[] AIMABLE_PIVOT = new double[]{0.0, 35.9569, -0.03448};
   private static final double[] MAIN_PIVOT = new double[]{0.0, 29.18389, 2.89847};
   public static final float YAW_SIGN = 1.0F;
   public static final float PITCH_SIGN = 1.0F;

   private StrwsAim() {
   }

   public static Vec3d boneWorldPos(BlockPos controller, Direction facing, float yawDeg, float pitchDeg, int index) {
      double[] p = SHOOT_PIVOTS[index];
      double x = p[0];
      double y = p[1];
      double z = p[2];
      double pr = Math.toRadians(1.0F * pitchDeg);
      double cy = Math.cos(pr);
      double sy = Math.sin(pr);
      double dy = y - AIMABLE_PIVOT[1];
      double dz = z - AIMABLE_PIVOT[2];
      y = AIMABLE_PIVOT[1] + dy * cy - dz * sy;
      z = AIMABLE_PIVOT[2] + dy * sy + dz * cy;
      double yr = Math.toRadians(1.0F * yawDeg);
      double cyaw = Math.cos(yr);
      double syaw = Math.sin(yr);
      double dx = x - MAIN_PIVOT[0];
      double dz2 = z - MAIN_PIVOT[2];
      x = MAIN_PIVOT[0] + dx * cyaw + dz2 * syaw;
      z = MAIN_PIVOT[2] - dx * syaw + dz2 * cyaw;
      Vec3d local = rotateYaw(new Vec3d(x / 16.0, y / 16.0, z / 16.0), facingRadians(facing));
      return new Vec3d(controller.getX() + 0.5 + local.x, controller.getY() + 0.01 + local.y, controller.getZ() + 0.5 + local.z);
   }

   public static Vec3d aimDirection(Direction facing, float yawDeg, float pitchDeg) {
      Vec3d dir = new Vec3d(0.0, 0.0, -1.0);
      double pr = Math.toRadians(1.0F * pitchDeg);
      double cy = Math.cos(pr);
      double sy = Math.sin(pr);
      dir = new Vec3d(dir.x, dir.y * cy - dir.z * sy, dir.y * sy + dir.z * cy);
      dir = rotateYaw(dir, Math.toRadians(1.0F * yawDeg) + facingRadians(facing));
      return dir.normalize();
   }

   public static Vec3d latch(World level, Vec3d origin, Vec3d dir) {
      Vec3d end = origin.add(dir.multiply(75.0));
      BlockHitResult hit = level.raycast(new RaycastContext(origin, end, ShapeType.COLLIDER, FluidHandling.NONE, (net.minecraft.entity.Entity)null));
      return hit.getType() == Type.MISS ? end : hit.getPos();
   }

   private static Vec3d rotateYaw(Vec3d v, double rad) {
      double c = Math.cos(rad);
      double s = Math.sin(rad);
      return new Vec3d(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
   }

   private static double facingRadians(Direction facing) {
      return switch (facing) {
         case SOUTH -> Math.PI;
         case WEST -> Math.PI / 2;
         case EAST -> Math.PI * 3.0 / 2.0;
         default -> 0.0;
      };
   }
}
