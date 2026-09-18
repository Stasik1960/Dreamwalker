package daot;

import daot.network.ModNetworking;
import daot.network.ShifterDodgeEffectPayload;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ShifterDodgeManager {
   public static final int DODGE_DURATION_TICKS = 5;
   public static final int DODGE_COOLDOWN_TICKS = 20;
   public static final float DODGE_STAMINA_COST = 6.0F;
   public static final double DODGE_HORIZONTAL_VELOCITY = 2.6;
   public static final int DIR_BACK = 0;
   public static final int DIR_FORWARD = 1;
   public static final int DIR_LEFT = 2;
   public static final int DIR_RIGHT = 3;
   private static final Map<UUID, Long> dodgeEndTick = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> nextAllowedTick = new ConcurrentHashMap<>();
   private static final Map<UUID, Vec3d> activeDodgeVelocity = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> activeTitanId = new ConcurrentHashMap<>();

   private ShifterDodgeManager() {
   }

   public static void tryStart(ServerPlayerEntity player, int direction) {
      if (player != null && !player.getWorld().isClient()) {
         if (player.getVehicle() instanceof LivingEntity titan) {
            if (isDodgeEligibleTitan(titan)) {
               UUID playerUUID = player.getUuid();
               long now = player.getWorld().getTime();
               Long next = nextAllowedTick.get(playerUUID);
               if (next == null || now >= next) {
                  if (!player.isSneaking()) {
                     if (!isTitanDismounting(titan)) {
                        if (!isTitanMovementLocked(titan)) {
                           float stamina = ModNetworking.getStamina(playerUUID);
                           if (!(stamina < 6.0F)) {
                              ModNetworking.drainStamina(playerUUID, 6.0F);
                              float yawDeg = player.getYaw();
                              float yawRad = (float)Math.toRadians(yawDeg);
                              double forwardX = -Math.sin(yawRad);
                              double forwardZ = Math.cos(yawRad);
                              double rightX = -forwardZ;
                              double dx;
                              double dz;
                              switch (direction) {
                                 case 0:
                                    dx = -forwardX;
                                    dz = -forwardZ;
                                    break;
                                 case 1:
                                    dx = forwardX;
                                    dz = forwardZ;
                                    break;
                                 case 2:
                                    dx = -rightX;
                                    dz = -forwardX;
                                    break;
                                 case 3:
                                    dx = rightX;
                                    dz = forwardX;
                                    break;
                                 default:
                                    dx = -forwardX;
                                    dz = -forwardZ;
                              }

                              Vec3d vel = new Vec3d(dx * 2.6, 0.0, dz * 2.6);
                              dodgeEndTick.put(playerUUID, now + 5L);
                              nextAllowedTick.put(playerUUID, now + 20L);
                              activeDodgeVelocity.put(playerUUID, vel);
                              activeTitanId.put(playerUUID, titan.getId());
                              applyDodgeVelocity(titan, vel);
                              if (player.getWorld() instanceof ServerWorld serverLevel) {
                                 ShifterDodgeEffectPayload effect = new ShifterDodgeEffectPayload(titan.getId(), (float)dx, (float)dz);
                                 BlockPos pos = titan.getBlockPos();

                                 for (ServerPlayerEntity nearby : PlayerLookup.tracking(serverLevel, pos)) {
                                    ServerPlayNetworking.send(nearby, effect);
                                 }

                                 ServerPlayNetworking.send(player, effect);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (!dodgeEndTick.isEmpty()) {
         long now = server.getOverworld().getTime();
         Iterator<Entry<UUID, Long>> it = dodgeEndTick.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Long> entry = it.next();
            UUID uuid = entry.getKey();
            if (now >= entry.getValue()) {
               it.remove();
               activeDodgeVelocity.remove(uuid);
               activeTitanId.remove(uuid);
            } else {
               Integer titanId = activeTitanId.get(uuid);
               Vec3d vel = activeDodgeVelocity.get(uuid);
               if (titanId != null && vel != null) {
                  for (ServerWorld level : server.getWorlds()) {
                     if (level.getEntityById(titanId) instanceof LivingEntity titan) {
                        applyDodgeVelocity(titan, vel);
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private static void applyDodgeVelocity(LivingEntity titan, Vec3d vel) {
      titan.setVelocity(vel.x, 0.0, vel.z);
      titan.fallDistance = 0.0F;
      titan.velocityDirty = true;
      titan.velocityModified = true;
   }

   public static boolean isDodgeEligibleTitan(LivingEntity titan) {
      return titan instanceof AttackTitanEntity
         || titan instanceof ArmoredTitanEntity
         || titan instanceof FemaleTitanEntity
         || titan instanceof BeastTitanEntity
         || titan instanceof WarhammerTitanEntity;
   }

   private static boolean isTitanDismounting(LivingEntity titan) {
      if (titan instanceof AttackTitanEntity t) {
         return t.isDismounting();
      } else if (titan instanceof ArmoredTitanEntity t) {
         return t.isDismounting();
      } else if (titan instanceof FemaleTitanEntity t) {
         return t.isDismounting();
      } else if (titan instanceof BeastTitanEntity t) {
         return t.isDismounting();
      } else {
         return titan instanceof WarhammerTitanEntity t ? t.isDismounting() : false;
      }
   }

   private static boolean isTitanMovementLocked(LivingEntity titan) {
      if (titan instanceof AttackTitanEntity t) {
         if (t.isTransforming()) {
            return true;
         } else {
            int n = t.getAttackNumber();
            return t.isTitanAttacking() && (n == 3 || n == 4 || n == 5);
         }
      } else if (titan instanceof ArmoredTitanEntity tx) {
         if (tx.isTransforming()) {
            return true;
         } else {
            return tx.isLowHealth() ? true : tx.isTitanAttacking() && tx.getAttackNumber() == 3;
         }
      } else if (titan instanceof FemaleTitanEntity txx) {
         if (txx.isTransforming()) {
            return true;
         } else if (!txx.isThrowing() && !txx.isEating()) {
            int n = txx.getAttackNumber();
            return txx.isTitanAttacking() && (n == 3 || n == 4);
         } else {
            return true;
         }
      } else if (titan instanceof BeastTitanEntity txxx) {
         return txxx.isTransforming() ? true : txxx.isTitanAttacking();
      } else if (titan instanceof WarhammerTitanEntity txxx) {
         return txxx.isTransforming() ? true : txxx.isTitanAttacking();
      } else {
         return false;
      }
   }

   public static boolean isTitanInDodgeIFrames(LivingEntity titan) {
      if (titan != null && !titan.getWorld().isClient()) {
         if (titan.getControllingPassenger() instanceof ServerPlayerEntity rider) {
            Long end = dodgeEndTick.get(rider.getUuid());
            if (end == null) {
               return false;
            } else {
               long now = titan.getWorld().getTime();
               if (now >= end) {
                  dodgeEndTick.remove(rider.getUuid());
                  activeDodgeVelocity.remove(rider.getUuid());
                  activeTitanId.remove(rider.getUuid());
                  return false;
               } else {
                  return true;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void clear(UUID playerUUID) {
      dodgeEndTick.remove(playerUUID);
      nextAllowedTick.remove(playerUUID);
      activeDodgeVelocity.remove(playerUUID);
      activeTitanId.remove(playerUUID);
   }
}
