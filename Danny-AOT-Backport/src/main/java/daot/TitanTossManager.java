package daot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

public final class TitanTossManager {
   private static final float THROW_SPEED = 4.7F;
   private static final int DOWNED_TICKS = 60;
   private static final int DROPPED_DOWNED_TICKS = 30;
   private static final int FLIGHT_TIMEOUT_TICKS = 120;
   private static final Map<Integer, TitanTossManager.TossState> states = new HashMap<>();
   private static boolean initialized = false;

   private TitanTossManager() {
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            BeastTitanEntity.tickThrownEntities(server);
            serverTick();
         });
         DannysAot.LOGGER.info("TitanTossManager initialized");
      }
   }

   public static boolean isTossablePureTitan(Entity e) {
      return e instanceof TitanEntity
         || e instanceof SmallTitanEntity
         || e instanceof SmallTitan2Entity
         || e instanceof FritzTitanEntity
         || e instanceof ConnieFatherEntity
         || e instanceof YellowTitanEntity
         || e instanceof SadTitanEntity;
   }

   public static boolean isRagdolled(int entityId) {
      return states.containsKey(entityId);
   }

   public static void markHeld(MobEntity titan) {
      TitanTossManager.TossState st = states.computeIfAbsent(titan.getId(), id -> new TitanTossManager.TossState(titan));
      st.flying = false;
      st.downedTicks = -1;
      titan.setAiDisabled(true);
      if (titan instanceof GrabbingTitan gt && gt.isEating()) {
         gt.cancelEating();
      }
   }

   public static void release(MobEntity titan) {
      TitanTossManager.TossState st = states.get(titan.getId());
      if (st != null) {
         st.flying = false;
         st.downedTicks = 30;
      }
   }

   public static void throwTitan(MobEntity titan, Vec3d dir, LivingEntity thrower) {
      TitanTossManager.TossState st = states.computeIfAbsent(titan.getId(), id -> new TitanTossManager.TossState(titan));
      st.flying = true;
      st.flightTicks = 0;
      st.downedTicks = -1;
      titan.setAiDisabled(true);
      Vec3d d = dir.lengthSquared() < 1.0E-4 ? new Vec3d(0.0, 0.3, 1.0) : dir.normalize();
      Vec3d launchVel = d.multiply(4.7F).add(0.0, 1.0, 0.0);
      double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
      double launchX = horiz > 0.001 ? thrower.getX() + d.x / horiz * 8.0 : thrower.getX();
      double launchZ = horiz > 0.001 ? thrower.getZ() + d.z / horiz * 8.0 : thrower.getZ();
      double launchY = thrower.getY() + 12.0;
      titan.fallDistance = 0.0F;
      titan.setPosition(launchX, launchY, launchZ);
      BeastTitanEntity.registerThrownEntity(titan, thrower.getId(), launchVel);
   }

   private static void serverTick() {
      if (!states.isEmpty()) {
         Iterator<Entry<Integer, TitanTossManager.TossState>> it = states.entrySet().iterator();

         while (it.hasNext()) {
            TitanTossManager.TossState st = it.next().getValue();
            MobEntity t = st.titan;
            if (!t.isRemoved() && t.isAlive() && t.getWorld() instanceof ServerWorld level) {
               t.setAiDisabled(true);
               if (st.flying) {
                  st.flightTicks++;
                  boolean flightOver = st.flightTicks > 2 && !BeastTitanEntity.isEntityThrown(t.getId());
                  if (flightOver || st.flightTicks > 120) {
                     st.flying = false;
                     st.downedTicks = 60;
                     level.playSound(null, t.getX(), t.getY(), t.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 6.0F, 0.3F);
                  }
               } else if (st.downedTicks > 0) {
                  st.downedTicks--;
                  if (st.downedTicks <= 0) {
                     t.setAiDisabled(false);
                     it.remove();
                  }
               } else if (st.downedTicks < 0 && t.getVehicle() == null) {
                  st.downedTicks = 30;
               }
            } else {
               if (!t.isRemoved()) {
                  t.setAiDisabled(false);
               }

               it.remove();
            }
         }
      }
   }

   private static final class TossState {
      final MobEntity titan;
      boolean flying = false;
      int flightTicks = 0;
      int downedTicks = -1;

      TossState(MobEntity titan) {
         this.titan = titan;
      }
   }
}
