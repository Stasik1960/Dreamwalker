package daot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class TitanSlideHelper {
   private static final double BUFFER_ZONE = 0.6;

   public static void titanPushEntities(Entity titan) {
      if (titan.getWorld().isClient()) {
         Box inflated = titan.getBoundingBox().expand(0.6);

         for (Entity entity : titan.getWorld().getOtherEntities(titan, inflated, e -> e instanceof PlayerEntity && e.isPushable() && !e.noClip)) {
            pushEntityAway(titan, entity);
         }
      }
   }

   private static void pushEntityAway(Entity titan, Entity other) {
      double dx = other.getX() - titan.getX();
      double dz = other.getZ() - titan.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (dist < 0.01) {
         dx = other.getWorld().random.nextDouble() - 0.5;
         dz = other.getWorld().random.nextDouble() - 0.5;
         dist = Math.sqrt(dx * dx + dz * dz);
      }

      double nx = dx / dist;
      double nz = dz / dist;
      Box titanBox = titan.getBoundingBox();
      Box otherBox = other.getBoundingBox();
      double titanHalfW = titanBox.getXLength() / 2.0;
      double otherHalfW = otherBox.getXLength() / 2.0;
      double surfaceDist = dist - titanHalfW - otherHalfW;
      if (!(surfaceDist > 0.6)) {
         double t = 1.0 - Math.max(0.0, surfaceDist) / 0.6;
         double eject = 0.04 + t * 0.25;
         other.addVelocity(nx * eject, 0.0, nz * eject);
      }
   }
}
