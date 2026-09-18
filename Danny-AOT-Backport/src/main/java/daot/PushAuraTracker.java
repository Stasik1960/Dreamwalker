package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.FreezeVignetteSyncPayload;
import daot.network.PushAuraStatePayload;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PushAuraTracker {
   public static final String FROZEN_TAG = "dannys-aot:push_frozen";
   public static final String WAS_CRIT_ARROW_TAG = "dannys-aot:push_was_crit_arrow";
   private static final double RADIUS = 7.5;
   private static final double CORE_RADIUS = 1.5;
   private static final double FULL_FREEZE_RADIUS = 1.25;
   private static final double FIRE_RADIUS = 3.75;
   private static final double PUSH_SPEED_CAP = 1.8;
   private static final double ATTRACT_SPEED_CAP = 1.5;
   private static final Identifier FREEZE_DAMAGE_ID = new Identifier("dannys-aot", "push_aura_freeze_damage");
   private static final Identifier FREEZE_ATTACK_SPEED_ID = new Identifier("dannys-aot", "push_aura_freeze_attack_speed");
   private static final Identifier FREEZE_MOVE_SPEED_ID = new Identifier("dannys-aot", "push_aura_freeze_move_speed");
   private static final Identifier KNOCKBACK_RESIST_ID = new Identifier("dannys-aot", "push_aura_kb_resist");
   public static final Set<UUID> APPLYING_REDUCED_DAMAGE = new HashSet<>();
   private static final Set<UUID> ACTIVE = new HashSet<>();
   private static final Set<UUID> ATTRACT = new HashSet<>();
   private static final Set<UUID> WAS_CROUCHING = new HashSet<>();
   private static final Map<UUID, Set<Integer>> FROZEN_BY_HOLDER = new HashMap<>();
   public static volatile boolean clientLocalFrozenVignette = false;
   private static final Map<UUID, Set<UUID>> VIGNETTE_TARGETS = new HashMap<>();
   private static final Map<Integer, Vec3d> FROZEN_ARROW_DIRS = new HashMap<>();
   private static final Random RNG = new Random();

   public static boolean isActive(UUID uuid) {
      return ACTIVE.contains(uuid);
   }

   public static boolean isAttract(UUID uuid) {
      return ATTRACT.contains(uuid);
   }

   public static void toggleActive(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (ACTIVE.contains(uuid)) {
         disable(player);
      } else {
         ACTIVE.add(uuid);
         applyAuraKnockbackResist(player);
         broadcastState(player, true, ATTRACT.contains(uuid));
         player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.5F);
      }
   }

   public static void applyAuraKnockbackResist(LivingEntity le) {
      EntityAttributeInstance kb = le.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
      if (kb != null) {
         daot.compat.AttributeModifiers.removeModifier(kb, KNOCKBACK_RESIST_ID);
         kb.addTemporaryModifier(daot.compat.AttributeModifiers.create(KNOCKBACK_RESIST_ID, 1.0, Operation.ADDITION));
      }

      EntityAttributeInstance ekb = le.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE);
      if (ekb != null) {
         daot.compat.AttributeModifiers.removeModifier(ekb, KNOCKBACK_RESIST_ID);
         ekb.addTemporaryModifier(daot.compat.AttributeModifiers.create(KNOCKBACK_RESIST_ID, 1.0, Operation.ADDITION));
      }
   }

   public static void removeAuraKnockbackResist(LivingEntity le) {
      EntityAttributeInstance kb = le.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
      if (kb != null) {
         daot.compat.AttributeModifiers.removeModifier(kb, KNOCKBACK_RESIST_ID);
      }

      EntityAttributeInstance ekb = le.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE);
      if (ekb != null) {
         daot.compat.AttributeModifiers.removeModifier(ekb, KNOCKBACK_RESIST_ID);
      }
   }

   public static void unfreezeAllNearby(ServerPlayerEntity player) {
      if (player.getWorld() instanceof ServerWorld level) {
         for (Entity entity : level.getOtherEntities(player, player.getBoundingBox().expand(35.0))) {
            unfreezeEntity(entity);
         }
      }

      removeAuraKnockbackResist(player);
      WAS_CROUCHING.remove(player.getUuid());
      ATTRACT.remove(player.getUuid());
      FROZEN_BY_HOLDER.remove(player.getUuid());
      Set<UUID> targets = VIGNETTE_TARGETS.remove(player.getUuid());
      if (targets != null && player.getServer() != null) {
         for (UUID tid : targets) {
            ServerPlayerEntity tp = player.getServer().getPlayerManager().getPlayer(tid);
            if (tp != null) {
               ServerPlayNetworking.send(tp, new FreezeVignetteSyncPayload(false));
            }
         }
      }
   }

   public static void toggleAttract(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (ACTIVE.contains(uuid)) {
         boolean nowAttract = !ATTRACT.contains(uuid);
         if (nowAttract) {
            ATTRACT.add(uuid);
            player.getWorld()
               .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 1.5F, 0.8F);
         } else {
            ATTRACT.remove(uuid);
            Set<UUID> targets = VIGNETTE_TARGETS.remove(uuid);
            if (targets != null) {
               for (UUID tid : targets) {
                  ServerPlayerEntity tp = player.server.getPlayerManager().getPlayer(tid);
                  if (tp != null) {
                     ServerPlayNetworking.send(tp, new FreezeVignetteSyncPayload(false));
                  }
               }
            }

            player.getWorld()
               .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.8F);
         }

         broadcastState(player, true, nowAttract);
      }
   }

   public static void disable(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (ACTIVE.remove(uuid)) {
         unfreezeAllNearby(player);
         broadcastState(player, false, false);
         player.getWorld()
            .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.5F);
      }
   }

   public static void onPlayerLogout(ServerPlayerEntity player) {
      if (ACTIVE.contains(player.getUuid())) {
         disable(player);
      }
   }

   public static void onPlayerLogin(ServerPlayerEntity player) {
      for (UUID uuid : ACTIVE) {
         ServerPlayerEntity holder = player.getServer().getPlayerManager().getPlayer(uuid);
         if (holder != null) {
            ServerPlayNetworking.send(player, new PushAuraStatePayload(holder.getId(), true, ATTRACT.contains(uuid)));
         }
      }
   }

   private static void broadcastState(ServerPlayerEntity player, boolean active, boolean attract) {
      if (player.getWorld() instanceof ServerWorld level) {
         PushAuraStatePayload var7 = new PushAuraStatePayload(player.getId(), active, attract);

         for (ServerPlayerEntity p : PlayerLookup.world(level)) {
            ServerPlayNetworking.send(p, var7);
         }
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (!ACTIVE.isEmpty()) {
         for (UUID uuid : new HashSet<>(ACTIVE)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
               ACTIVE.remove(uuid);
               ATTRACT.remove(uuid);
               WAS_CROUCHING.remove(uuid);
               VIGNETTE_TARGETS.remove(uuid);
               FROZEN_BY_HOLDER.remove(uuid);
            } else {
               tickPlayer(player);
            }
         }
      }
   }

   private static void tickPlayer(ServerPlayerEntity player) {
      if (player.getWorld() instanceof ServerWorld level) {
         UUID var21 = player.getUuid();
         boolean crouching = player.isSneaking();
         boolean attract = ATTRACT.contains(var21);
         boolean wasCrouching = WAS_CROUCHING.contains(var21);
         if (crouching && !wasCrouching) {
            WAS_CROUCHING.add(var21);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 1.5F, 0.3F);
            Set<UUID> targets = new HashSet<>();

            for (ServerPlayerEntity p : level.getPlayers()) {
               if (p != player && p.distanceTo(player) <= 7.5) {
                  ServerPlayNetworking.send(p, new FreezeVignetteSyncPayload(true));
                  targets.add(p.getUuid());
               }
            }

            VIGNETTE_TARGETS.put(var21, targets);
         } else if (!crouching && wasCrouching) {
            WAS_CROUCHING.remove(var21);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.5F);
            Set<UUID> old = VIGNETTE_TARGETS.remove(var21);
            if (old != null) {
               for (UUID tid : old) {
                  ServerPlayerEntity tp = player.server.getPlayerManager().getPlayer(tid);
                  if (tp != null) {
                     ServerPlayNetworking.send(tp, new FreezeVignetteSyncPayload(false));
                  }
               }
            }
         } else if (crouching) {
            Set<UUID> cur = VIGNETTE_TARGETS.computeIfAbsent(var21, k -> new HashSet<>());
            Set<UUID> inRange = new HashSet<>();

            for (ServerPlayerEntity px : level.getPlayers()) {
               if (px != player && px.distanceTo(player) <= 7.5) {
                  inRange.add(px.getUuid());
                  if (cur.add(px.getUuid())) {
                     ServerPlayNetworking.send(px, new FreezeVignetteSyncPayload(true));
                  }
               }
            }

            cur.removeIf(tidx -> {
               if (inRange.contains(tidx)) {
                  return false;
               } else {
                  ServerPlayerEntity tp = player.server.getPlayerManager().getPlayer(tidx);
                  if (tp != null) {
                     ServerPlayNetworking.send(tp, new FreezeVignetteSyncPayload(false));
                  }

                  return true;
               }
            });
         }

         Set<Integer> previouslyFrozen = FROZEN_BY_HOLDER.getOrDefault(var21, Collections.emptySet());
         Set<Integer> currentlyFrozen = new HashSet<>();

         for (Entity entity : level.getOtherEntities(player, player.getBoundingBox().expand(7.5))) {
            if (entity != player) {
               double dx = entity.getX() - player.getX();
               double dy = entity.getY() - player.getY();
               double dz = entity.getZ() - player.getZ();
               double rawDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
               double distance = Math.max(rawDist, 0.5);
               if (!(distance > 7.5)) {
                  if (crouching) {
                     handleFreeze(player, entity, dx, dy, dz, distance);
                     if (entity.getCommandTags().contains("dannys-aot:push_frozen")) {
                        currentlyFrozen.add(entity.getId());
                     }
                  } else if (attract) {
                     handleAttract(player, entity, dx, dy, dz, distance);
                     if (entity.getCommandTags().contains("dannys-aot:push_frozen")) {
                        currentlyFrozen.add(entity.getId());
                     }
                  } else {
                     if (entity instanceof LivingEntity le) {
                        if (distance <= 1.5) {
                           applyFreezeModifiers(le, Math.max(0.0, distance / 1.5));
                        } else {
                           removeFreezeModifiers(le);
                        }
                     }

                     unfreezeEntity(entity);
                     handlePush(entity, dx, dy, dz, distance);
                  }
               }
            }
         }

         if (!previouslyFrozen.isEmpty()) {
            for (Integer id : previouslyFrozen) {
               if (!currentlyFrozen.contains(id)) {
                  Entity stale = level.getEntityById(id);
                  if (stale != null) {
                     unfreezeEntity(stale);
                  }
               }
            }
         }

         if (currentlyFrozen.isEmpty()) {
            FROZEN_BY_HOLDER.remove(var21);
         } else {
            FROZEN_BY_HOLDER.put(var21, currentlyFrozen);
         }

         if (!crouching) {
            pushFireBlocksInPushZone(player, level);
         }
      }
   }

   private static void handlePush(Entity entity, double dx, double dy, double dz, double distance) {
      if (!(entity instanceof PlayerEntity)) {
         double dirX = dx / distance;
         double dirY = Math.max(0.02, dy / distance);
         double dirZ = dz / distance;
         double t = 1.0 - distance / 7.5;
         if (entity instanceof ThunderSpearEntity ts && !ts.isLodged()) {
            if (ts.isFreezeLodged()) {
               ts.unfreezeLodge();
            }

            ts.inPushZone = true;
            double pushAccel = 2.0;
            double proximityScale = Math.max(0.3, 1.0 - distance / 7.5);
            Vec3d awayDir = new Vec3d(dirX, 0.1, dirZ).normalize();
            Vec3d cur = entity.getVelocity();
            double projSpd = cur.dotProduct(awayDir);
            Vec3d perp = cur.subtract(awayDir.multiply(projSpd)).multiply(0.5);
            double newProjSpd = projSpd + pushAccel * proximityScale;
            Vec3d newVel = awayDir.multiply(newProjSpd).add(perp);
            if (newVel.length() > 1.8) {
               newVel = newVel.normalize().multiply(1.8);
            }

            entity.setVelocity(newVel);
            entity.velocityModified = true;
            if (newProjSpd > 0.0 && !ts.isRepelled()) {
               ts.setRepelled(true);
            }
         } else {
            boolean kbImmune = entity instanceof LivingEntity le
               && le.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) != null
               && le.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) >= 1.0;
            if (kbImmune) {
               entity.setPosition(entity.getX() + dirX * t, entity.getY() + dirY * t * 0.3, entity.getZ() + dirZ * t);
            } else {
               Vec3d kb = new Vec3d(dirX * t, dirY * t * 0.3 + 0.02 * t, dirZ * t);
               entity.setVelocity(entity.getVelocity().add(kb));
            }

            entity.velocityModified = true;
         }
      }
   }

   private static void handleAttract(ServerPlayerEntity player, Entity entity, double dx, double dy, double dz, double distance) {
      boolean isPlayer = entity instanceof PlayerEntity;
      if (entity instanceof ThunderSpearEntity tsx && tsx.isFreezeLodged()) {
         tsx.unfreezeLodge();
      }

      if (!isPlayer && !entity.getCommandTags().contains("dannys-aot:push_frozen")) {
         entity.addCommandTag("dannys-aot:push_frozen");
         entity.setNoGravity(true);
      }

      FROZEN_ARROW_DIRS.remove(entity.getId());
      double hDist = Math.sqrt(dx * dx + dz * dz);
      double hDirX = hDist > 0.1 ? dx / hDist : 0.0;
      double hDirZ = hDist > 0.1 ? dz / hDist : 0.0;
      double targetX = player.getX() + hDirX * 1.5;
      double targetY = player.getY() + 1.0;
      double targetZ = player.getZ() + hDirZ * 1.5;
      double toTX = targetX - entity.getX();
      double toTY = targetY - entity.getY();
      double toTZ = targetZ - entity.getZ();
      double distToT = Math.sqrt(toTX * toTX + toTY * toTY + toTZ * toTZ);
      boolean kbImmune = entity instanceof LivingEntity le
         && le.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) != null
         && le.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) >= 1.0;
      if (distToT < 0.5) {
         entity.setPosition(targetX, targetY, targetZ);
         entity.setVelocity(Vec3d.ZERO);
         if (entity instanceof LivingEntity lex) {
            applyFreezeModifiers(lex, 0.01);
         }
      } else {
         if (entity instanceof LivingEntity lex) {
            applyFreezeModifiers(lex, Math.min(0.3, distToT / 10.0));
         }

         double speed = Math.min(distToT * 0.3, 1.5);
         double ntx = toTX / distToT;
         double nty = toTY / distToT;
         double ntz = toTZ / distToT;
         if (kbImmune) {
            double moveScale = Math.min(speed, distToT);
            entity.setPosition(entity.getX() + ntx * moveScale, entity.getY() + nty * moveScale, entity.getZ() + ntz * moveScale);
         } else {
            entity.setVelocity(ntx * speed, nty * speed, ntz * speed);
         }
      }

      if (isPlayer) {
         ServerPlayNetworking.send((ServerPlayerEntity)entity, new FreezeVignetteSyncPayload(true));
         VIGNETTE_TARGETS.computeIfAbsent(player.getUuid(), k -> new HashSet<>()).add(entity.getUuid());
      }

      entity.velocityModified = true;
   }

   private static void handleFreeze(ServerPlayerEntity player, Entity entity, double dx, double dy, double dz, double distance) {
      boolean isPlayer = entity instanceof PlayerEntity;
      boolean isArrow = entity instanceof PersistentProjectileEntity;
      double freezeFactor = distance <= 1.25 ? 0.0 : (distance - 1.25) / 6.25;
      if (entity instanceof ThunderSpearEntity ts && distance <= 1.25) {
         if (!ts.isLodged()) {
            ts.freezeLodge();
         }
      } else {
         if (isArrow) {
            saveArrowDirection(entity);
            if (entity instanceof PersistentProjectileEntity arrow && arrow.isCritical()) {
               arrow.setCritical(false);
               entity.addCommandTag("dannys-aot:push_was_crit_arrow");
            }
         }

         boolean justFrozen = false;
         if (!isPlayer && !entity.getCommandTags().contains("dannys-aot:push_frozen")) {
            entity.addCommandTag("dannys-aot:push_frozen");
            entity.setNoGravity(true);
            justFrozen = true;
         }

         if (entity instanceof LivingEntity le) {
            if (isPlayer && distance < 0.75) {
               double movReduction = -(1.0 - freezeFactor) * 0.9;
               EntityAttributeInstance dmg = le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
               if (dmg != null) {
                  daot.compat.AttributeModifiers.removeModifier(dmg, FREEZE_DAMAGE_ID);
               }

               EntityAttributeInstance atk = le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
               if (atk != null) {
                  daot.compat.AttributeModifiers.removeModifier(atk, FREEZE_ATTACK_SPEED_ID);
               }

               applyModifier(le, EntityAttributes.GENERIC_MOVEMENT_SPEED, FREEZE_MOVE_SPEED_ID, movReduction);
            } else {
               applyFreezeModifiers(le, freezeFactor);
            }
         }

         if (!isPlayer) {
            if (isArrow) {
               Vec3d vel = entity.getVelocity();
               Vec3d arrowDir = FROZEN_ARROW_DIRS.get(entity.getId());
               if (arrowDir != null) {
                  if (freezeFactor <= 0.001) {
                     entity.setVelocity(Vec3d.ZERO);
                  } else {
                     double speed = vel.length() * freezeFactor;
                     entity.setVelocity(arrowDir.multiply(speed));
                  }

                  double hSpd = Math.sqrt(arrowDir.x * arrowDir.x + arrowDir.z * arrowDir.z);
                  float yaw = (float)Math.toDegrees(Math.atan2(arrowDir.x, arrowDir.z));
                  float pitch = (float)Math.toDegrees(Math.atan2(-arrowDir.y, hSpd));
                  entity.setYaw(yaw);
                  entity.prevYaw = yaw;
                  entity.setPitch(pitch);
                  entity.prevPitch = pitch;
               } else {
                  entity.setVelocity(vel.x * freezeFactor, vel.y * freezeFactor, vel.z * freezeFactor);
               }
            } else {
               double newX = entity.prevX + (entity.getX() - entity.prevX) * freezeFactor;
               double newY;
               if (entity.isOnGround()) {
                  newY = entity.getY();
               } else if (freezeFactor < 0.1) {
                  newY = entity.getY();
               } else {
                  newY = entity.prevY + (entity.getY() - entity.prevY) * freezeFactor;
               }

               double newZ = entity.prevZ + (entity.getZ() - entity.prevZ) * freezeFactor;
               entity.setPosition(newX, newY, newZ);
               double frozenY = freezeFactor >= 0.1 && !entity.isOnGround() ? entity.getVelocity().y * freezeFactor : 0.0;
               Vec3d velx = entity.getVelocity();
               entity.setVelocity(velx.x * freezeFactor, frozenY, velx.z * freezeFactor);
            }

            if (!isArrow || justFrozen) {
               entity.velocityModified = true;
            }
         }
      }
   }

   private static void saveArrowDirection(Entity arrow) {
      if (!FROZEN_ARROW_DIRS.containsKey(arrow.getId())) {
         Vec3d vel = arrow.getVelocity();
         if (vel.lengthSquared() > 1.0E-4) {
            FROZEN_ARROW_DIRS.put(arrow.getId(), vel.normalize());
         } else {
            double yaw = Math.toRadians(arrow.getYaw());
            double pitch = Math.toRadians(arrow.getPitch());
            double cos = Math.cos(pitch);
            FROZEN_ARROW_DIRS.put(arrow.getId(), new Vec3d(-Math.sin(yaw) * cos, -Math.sin(pitch), Math.cos(yaw) * cos));
         }
      }
   }

   public static void unfreezeEntity(Entity entity) {
      if (entity.getCommandTags().contains("dannys-aot:push_frozen")) {
         entity.removeScoreboardTag("dannys-aot:push_frozen");
         entity.setNoGravity(false);
         FROZEN_ARROW_DIRS.remove(entity.getId());
         if (entity.getCommandTags().contains("dannys-aot:push_was_crit_arrow")) {
            if (entity instanceof PersistentProjectileEntity arrow) {
               arrow.setCritical(true);
            }

            entity.removeScoreboardTag("dannys-aot:push_was_crit_arrow");
         }
      }

      if (entity instanceof LivingEntity le) {
         removeFreezeModifiers(le);
      }
   }

   private static void applyFreezeModifiers(LivingEntity le, double factor) {
      applyModifier(le, EntityAttributes.GENERIC_ATTACK_DAMAGE, FREEZE_DAMAGE_ID, -(1.0 - factor));
      applyModifier(le, EntityAttributes.GENERIC_ATTACK_SPEED, FREEZE_ATTACK_SPEED_ID, -(1.0 - factor));
      applyModifier(le, EntityAttributes.GENERIC_MOVEMENT_SPEED, FREEZE_MOVE_SPEED_ID, -(1.0 - factor));
   }

   private static void removeFreezeModifiers(LivingEntity le) {
      EntityAttributeInstance a = le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
      if (a != null) {
         daot.compat.AttributeModifiers.removeModifier(a, FREEZE_DAMAGE_ID);
      }

      a = le.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
      if (a != null) {
         daot.compat.AttributeModifiers.removeModifier(a, FREEZE_ATTACK_SPEED_ID);
      }

      a = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (a != null) {
         daot.compat.AttributeModifiers.removeModifier(a, FREEZE_MOVE_SPEED_ID);
      }
   }

   private static void applyModifier(LivingEntity le, EntityAttribute attr, Identifier id, double amount) {
      EntityAttributeInstance inst = le.getAttributeInstance(attr);
      if (inst != null) {
         daot.compat.AttributeModifiers.removeModifier(inst, id);
         inst.addTemporaryModifier(daot.compat.AttributeModifiers.create(id, amount, Operation.MULTIPLY_TOTAL));
      }
   }

   private static void pushFireBlocksInPushZone(ServerPlayerEntity player, ServerWorld level) {
      int ir = (int)Math.ceil(3.75);
      int px = (int)Math.floor(player.getX());
      int py = (int)Math.floor(player.getY());
      int pz = (int)Math.floor(player.getZ());

      for (int dx = -ir; dx <= ir; dx++) {
         for (int dy = -ir; dy <= ir; dy++) {
            for (int dz = -ir; dz <= ir; dz++) {
               double distSq = dx * dx + dy * dy + dz * dz;
               if (!(distSq > 14.0625)) {
                  BlockPos pos = new BlockPos(px + dx, py + dy, pz + dz);
                  BlockState state = level.getBlockState(pos);
                  Block block = state.getBlock();
                  if (block instanceof AbstractFireBlock) {
                     level.removeBlock(pos, false);
                  } else if (block instanceof FireBlock || block instanceof CampfireBlock) {
                     level.removeBlock(pos, false);
                     double dist = Math.sqrt(distSq);
                     double dirX = dx;
                     double dirY = dy;
                     double dirZ = dz;
                     if (dist > 0.5) {
                        dirX /= dist;
                        dirY /= dist;
                        dirZ /= dist;
                     }

                     double velX = dirX * 0.6 + (RNG.nextDouble() - 0.5) * 0.2;
                     double velY = dirY * 0.6 + 0.3 + RNG.nextDouble() * 0.2;
                     double velZ = dirZ * 0.6 + (RNG.nextDouble() - 0.5) * 0.2;
                     FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
                     ((FallingBlockEntityAccessor)fb).setBlockState(state);
                     fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                     fb.setFallingBlockPos(pos);
                     fb.setVelocity(velX, velY, velZ);
                     fb.timeFalling = 1;
                     fb.dropItem = false;
                     level.spawnEntity(fb);
                  }
               }
            }
         }
      }
   }

   public static void registerEvents() {
      ServerLivingEntityEvents.ALLOW_DAMAGE.register(PushAuraTracker::onAllowDamage);
      ServerLivingEntityEvents.AFTER_DEATH.register(PushAuraTracker::onAfterDeath);
   }

   private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
      if (!(entity instanceof PlayerEntity player)) {
         return true;
      } else {
         UUID uuid = player.getUuid();
         if (!ACTIVE.contains(uuid)) {
            return true;
         } else if (APPLYING_REDUCED_DAMAGE.contains(uuid)) {
            return true;
         } else if (source.isOf(DamageTypes.GENERIC_KILL)) {
            if (player instanceof ServerPlayerEntity sp) {
               Text msg = Text.literal("You cannot kill a god.").formatted(Formatting.DARK_RED, Formatting.ITALIC);

               for (ServerPlayerEntity p : sp.server.getPlayerManager().getPlayerList()) {
                  p.sendMessage(msg);
               }
            }

            return false;
         } else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            Entity explosionSource = source.getAttacker();
            if (explosionSource == null) {
               explosionSource = source.getSource();
            }

            if (explosionSource != null) {
               if (!player.getBoundingBox().intersects(explosionSource.getBoundingBox())) {
                  return false;
               }
            } else {
               Vec3d pos = source.getPosition();
               if (pos == null || !player.getBoundingBox().expand(0.5).contains(pos)) {
                  return false;
               }
            }

            APPLYING_REDUCED_DAMAGE.add(uuid);

            try {
               player.damage(source, amount * 0.5F);
            } finally {
               APPLYING_REDUCED_DAMAGE.remove(uuid);
            }

            return false;
         } else if (!source.isOf(DamageTypes.INDIRECT_MAGIC) && !source.isOf(DamageTypes.MAGIC)) {
            Entity directSource = source.getSource();
            boolean sourceInHitbox;
            if (directSource != null && directSource.getWidth() > 3.0) {
               sourceInHitbox = player.distanceTo(directSource) < 3.0;
            } else {
               sourceInHitbox = directSource != null && player.getBoundingBox().intersects(directSource.getBoundingBox());
            }

            if (!sourceInHitbox) {
               return false;
            } else if (!(directSource instanceof PlayerEntity)) {
               APPLYING_REDUCED_DAMAGE.add(uuid);

               try {
                  player.damage(source, amount * 0.5F);
               } finally {
                  APPLYING_REDUCED_DAMAGE.remove(uuid);
               }

               return false;
            } else {
               return true;
            }
         } else {
            APPLYING_REDUCED_DAMAGE.add(uuid);

            try {
               player.damage(source, amount * 0.05F);
            } finally {
               APPLYING_REDUCED_DAMAGE.remove(uuid);
            }

            return false;
         }
      }
   }

   private static void onAfterDeath(LivingEntity entity, DamageSource source) {
      if (entity instanceof ServerPlayerEntity sp) {
         if (ACTIVE.contains(sp.getUuid())) {
            disable(sp);
         }
      }
   }
}
