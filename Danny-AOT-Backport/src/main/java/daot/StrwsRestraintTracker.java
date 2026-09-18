package daot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class StrwsRestraintTracker {
   public static final int NORMAL_WIRES = 6;
   public static final int OGRE_WIRES = 12;
   public static final int ARMORED_WIRES = 41;
   public static final int ATTACK_WIRES = 24;
   public static final int BEAST_WIRES = 36;
   public static final int FEMALE_WIRES = 24;
   public static final int JAW_WIRES = 18;
   public static final int WARHAMMER_WIRES = 30;
   private static final Identifier RESTRAINT_ID = new Identifier("dannys-aot", "strws_restraint");
   public static final Predicate<Entity> IS_RESTRAINABLE = e -> (TitanPowerHelper.isPureTitan(e) || e instanceof ShifterTitan)
      && !(e instanceof ColossalTitanEntity);
   private static final Map<RegistryKey<World>, Map<Long, UUID[]>> BY_LEVEL = new HashMap<>();
   private static final Set<UUID> RESTRAINED = new HashSet<>();
   private static final Set<UUID> FULLY_RESTRAINED = new HashSet<>();
   private static final Map<UUID, Integer> WIRE_COUNTS = new HashMap<>();

   private StrwsRestraintTracker() {
   }

   public static boolean isFullyRestrained(UUID titan) {
      return FULLY_RESTRAINED.contains(titan);
   }

   public static int getWireCount(UUID titan) {
      return WIRE_COUNTS.getOrDefault(titan, 0);
   }

   public static boolean isFullyRestrained(Entity shifter) {
      return shifter instanceof WireRestrainable wr ? wr.getWireRestraintCount() >= threshold(shifter) : isFullyRestrained(shifter.getUuid());
   }

   public static double movementFactor(Entity shifter, int wireCount) {
      return wireCount <= 0 ? 1.0 : Math.max(0.0, 1.0 - Math.min(1.0, (double)wireCount / threshold(shifter)));
   }

   public static void attach(ServerWorld level, BlockPos controllerPos, int bone, UUID titan) {
      BY_LEVEL.computeIfAbsent(level.getRegistryKey(), k -> new HashMap<>()).computeIfAbsent(controllerPos.asLong(), k -> new UUID[6])[bone] = titan;
   }

   public static void detach(ServerWorld level, BlockPos controllerPos, int bone) {
      Map<Long, UUID[]> byCtrl = BY_LEVEL.get(level.getRegistryKey());
      if (byCtrl != null) {
         UUID[] wires = byCtrl.get(controllerPos.asLong());
         if (wires != null) {
            wires[bone] = null;
         }
      }
   }

   public static void clearController(ServerWorld level, BlockPos controllerPos) {
      Map<Long, UUID[]> byCtrl = BY_LEVEL.get(level.getRegistryKey());
      if (byCtrl != null) {
         byCtrl.remove(controllerPos.asLong());
      }
   }

   public static void clearAll() {
      BY_LEVEL.clear();
      RESTRAINED.clear();
      FULLY_RESTRAINED.clear();
   }

   public static void tick(MinecraftServer server) {
      if (!BY_LEVEL.isEmpty()) {
         Map<UUID, Integer> counts = new HashMap<>();
         Map<UUID, ServerWorld> levelOf = new HashMap<>();

         for (Entry<RegistryKey<World>, Map<Long, UUID[]>> dim : BY_LEVEL.entrySet()) {
            ServerWorld level = server.getWorld(dim.getKey());
            if (level != null) {
               Map<Long, UUID[]> byCtrl = dim.getValue();
               byCtrl.values().removeIf(wiresx -> allNull(wiresx));

               for (Entry<Long, UUID[]> ctrl : byCtrl.entrySet()) {
                  BlockPos cpos = BlockPos.fromLong(ctrl.getKey());
                  UUID[] wires = ctrl.getValue();
                  Vec3d cCenter = new Vec3d(cpos.getX() + 0.5, cpos.getY() + 0.5, cpos.getZ() + 0.5);
                  StrwsBlockEntity be = level.getBlockEntity(cpos) instanceof StrwsBlockEntity s ? s : null;

                  for (int bone = 0; bone < wires.length; bone++) {
                     UUID u = wires[bone];
                     if (u != null) {
                        Entity te = level.getEntity(u);
                        if (te != null) {
                           if (!te.isAlive()) {
                              wires[bone] = null;
                              if (be != null) {
                                 be.serverDetachWire(bone);
                              }
                           } else {
                              if (be != null) {
                                 Vec3d end = te.getPos().add(be.getWireOffset(bone));
                                 if (cCenter.distanceTo(end) > 75.0) {
                                    wires[bone] = null;
                                    be.serverDetachWire(bone);
                                    continue;
                                 }
                              }

                              counts.merge(u, 1, Integer::sum);
                              levelOf.put(u, level);
                           }
                        }
                     }
                  }
               }
            }
         }

         FULLY_RESTRAINED.clear();
         WIRE_COUNTS.clear();
         WIRE_COUNTS.putAll(counts);

         for (Entry<UUID, Integer> e : counts.entrySet()) {
            ServerWorld level = levelOf.get(e.getKey());
            if (level != null && level.getEntity(e.getKey()) instanceof LivingEntity le) {
               applyRestraint(le, e.getValue());
            }
         }

         for (UUID u : new ArrayList<>(RESTRAINED)) {
            if (!counts.containsKey(u)) {
               if (findEntity(server, u) instanceof LivingEntity le) {
                  clearRestraint(le);
               }

               RESTRAINED.remove(u);
            }
         }

         RESTRAINED.addAll(counts.keySet());
      }
   }

   private static void applyRestraint(LivingEntity titan, int wireCount) {
      int threshold = threshold(titan);
      double fraction = Math.min(1.0, (double)wireCount / threshold);
      EntityAttributeInstance speed = titan.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (speed != null) {
         daot.compat.AttributeModifiers.removeModifier(speed, RESTRAINT_ID);
         if (fraction > 0.0) {
            speed.addTemporaryModifier(daot.compat.AttributeModifiers.create(RESTRAINT_ID, -fraction, Operation.MULTIPLY_TOTAL));
         }
      }

      if (titan instanceof WireRestrainable wr) {
         wr.setWireRestraintCount(wireCount);
      }

      if (fraction >= 1.0) {
         FULLY_RESTRAINED.add(titan.getUuid());
         Vec3d v = titan.getVelocity();
         titan.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
      }
   }

   private static void clearRestraint(LivingEntity titan) {
      EntityAttributeInstance speed = titan.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (speed != null) {
         daot.compat.AttributeModifiers.removeModifier(speed, RESTRAINT_ID);
      }

      if (titan instanceof WireRestrainable wr) {
         wr.setWireRestraintCount(0);
      }
   }

   public static int threshold(Entity titan) {
      if (titan instanceof OgreTitanEntity) {
         return 12;
      } else if (titan instanceof TestShifterTitanEntity) {
         return 18;
      } else if (titan instanceof AttackTitanEntity) {
         return 24;
      } else if (titan instanceof ArmoredTitanEntity) {
         return 41;
      } else if (titan instanceof BeastTitanEntity) {
         return 36;
      } else if (titan instanceof FemaleTitanEntity) {
         return 24;
      } else {
         return titan instanceof WarhammerTitanEntity ? 30 : 6;
      }
   }

   private static Entity findEntity(MinecraftServer server, UUID uuid) {
      for (ServerWorld level : server.getWorlds()) {
         Entity e = level.getEntity(uuid);
         if (e != null) {
            return e;
         }
      }

      return null;
   }

   private static boolean allNull(UUID[] wires) {
      for (UUID u : wires) {
         if (u != null) {
            return false;
         }
      }

      return true;
   }
}
