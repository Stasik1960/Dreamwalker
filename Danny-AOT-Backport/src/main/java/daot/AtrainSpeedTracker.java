package daot;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class AtrainSpeedTracker {
   private static final double SPEED_BONUS = 17.0;
   private static final double STEP_HEIGHT_BONUS = 1.5;
   private static final double GRAVITY_BONUS = 2.0;
   private static final double JUMP_STRENGTH_BONUS = 2.0;
   private static final Identifier SPEED_MOD_ID = new Identifier("dannys-aot", "atrain_speed");
   private static final Identifier STEP_MOD_ID = new Identifier("dannys-aot", "atrain_step_height");
   private static final Identifier GRAVITY_MOD_ID = new Identifier("dannys-aot", "atrain_gravity");
   private static final Identifier JUMP_MOD_ID = new Identifier("dannys-aot", "atrain_jump");
   private static final Set<UUID> toggledOn = ConcurrentHashMap.newKeySet();
   private static final Set<UUID> stepActive = ConcurrentHashMap.newKeySet();
   private static final Set<UUID> boosted = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Vec3d> lastPos = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> lastGibSpawnTick = new ConcurrentHashMap<>();
   private static final long GIB_COOLDOWN_TICKS = 20L;
   private static final double TELEPORT_THRESHOLD = 12.0;
   private static final double DAMAGE_THRESHOLD = 0.4;

   private AtrainSpeedTracker() {
   }

   public static boolean isToggledOn(UUID uuid) {
      return toggledOn.contains(uuid);
   }

   public static void setToggled(UUID uuid, boolean active) {
      if (active) {
         toggledOn.add(uuid);
      } else {
         toggledOn.remove(uuid);
      }
   }

   public static void clear(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      toggledOn.remove(id);
      boosted.remove(id);
      stepActive.remove(id);
      lastPos.remove(id);
      removeSpeedAndGravity(player);
      removeStepHeight(player);
   }

   public static void tick(MinecraftServer server) {
      if (!toggledOn.isEmpty() || !boosted.isEmpty() || !stepActive.isEmpty()) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            ServerWorld level = player.getServerWorld();
            boolean isAtrain = BloodlineData.get(level).getBloodline(id) == BloodlineType.ATRAIN;
            boolean powerDisabled = ModEffects.isPowerDisabled(player);
            boolean wantsStep = isAtrain && !powerDisabled && toggledOn.contains(id);
            boolean wantsBoost = wantsStep && player.isSprinting();
            boolean hasStep = stepActive.contains(id);
            boolean hasBoost = boosted.contains(id);
            if (wantsStep && !hasStep) {
               applyStepHeight(player);
               stepActive.add(id);
            } else if (!wantsStep && hasStep) {
               removeStepHeight(player);
               stepActive.remove(id);
            }

            if (wantsBoost && !hasBoost) {
               applySpeedAndGravity(player);
               boosted.add(id);
               lastPos.put(id, player.getPos());
            } else if (!wantsBoost && hasBoost) {
               removeSpeedAndGravity(player);
               boosted.remove(id);
               lastPos.remove(id);
            }

            if (hasBoost || wantsBoost) {
               Vec3d prev = lastPos.put(id, player.getPos());
               if (prev != null) {
                  Vec3d dpt = player.getPos().subtract(prev);
                  double speed = dpt.length();
                  if (speed >= 0.4 && speed <= 12.0) {
                     damageEntitiesInPath(level, player, dpt, speed);
                  }
               }

               if (player.getVelocity().horizontalLengthSquared() > 0.0025) {
                  player.addExhaustion(0.2F);
               }
            }
         }

         boosted.removeIf(idx -> server.getPlayerManager().getPlayer(idx) == null);
         stepActive.removeIf(idx -> server.getPlayerManager().getPlayer(idx) == null);
         toggledOn.removeIf(idx -> server.getPlayerManager().getPlayer(idx) == null);
         lastPos.keySet().removeIf(idx -> server.getPlayerManager().getPlayer(idx) == null);
      }
   }

   public static void playSpeedSound(ServerPlayerEntity player) {
      player.getServerWorld().playSoundFromEntity(null, player, ModSounds.SPEED, SoundCategory.PLAYERS, 1.0F, 1.0F);
   }

   private static void damageEntitiesInPath(ServerWorld level, ServerPlayerEntity player, Vec3d dpt, double speed) {
      Box hitBox = player.getBoundingBox().expand(0.3);
      Box swept = hitBox.stretch(dpt.x, dpt.y, dpt.z);
      float damage = (float)Math.min(60.0, speed * 30.0);
      Vec3d knock = dpt.normalize().multiply(speed * 0.7);
      Vec3d flightDir = dpt.normalize();
      boolean wasAbilityFlag = PowerDamageMarker.isApplyingAbility();
      if (!wasAbilityFlag) {
         PowerDamageMarker.APPLYING_ABILITY.set(Boolean.TRUE);
      }

      boolean hitAny = false;

      try {
         for (Entity e : level.getOtherEntities(player, swept)) {
            if (e instanceof LivingEntity living && !living.isTeammate(player)) {
               if (living instanceof PlayerEntity targetPlayer && targetPlayer.isBlocking()) {
                  targetPlayer.disableShield(false);
               }

               boolean wasAlive = living.isAlive();
               living.damage(level.getDamageSources().playerAttack(player), damage);
               living.setVelocity(living.getVelocity().add(knock));
               living.velocityModified = true;
               HomelanderFlightServerHandler.playFlyHitFx(level, living);
               hitAny = true;
               boolean killed = wasAlive && living.isDead();
               if (killed) {
                  GibEntity.spawnGibs(level, living, flightDir, 1.3F);
                  lastGibSpawnTick.remove(living.getUuid());
               } else if (damage >= 10.0F) {
                  long nowTick = level.getTime();
                  Long last = lastGibSpawnTick.get(living.getUuid());
                  if (last == null || nowTick - last >= 20L) {
                     GibEntity.spawnGibs(level, living, flightDir, 1.3F);
                     lastGibSpawnTick.put(living.getUuid(), nowTick);
                  }
               }
            }
         }
      } finally {
         if (!wasAbilityFlag) {
            PowerDamageMarker.APPLYING_ABILITY.set(Boolean.FALSE);
         }
      }

      if (hitAny) {
         HomelanderBloodTracker.onAtrainHit(player);
      }
   }

   private static void applyStepHeight(ServerPlayerEntity player) {
      EntityAttributeInstance step = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT);
      if (step != null && !daot.compat.AttributeModifiers.hasModifier(step, STEP_MOD_ID)) {
         step.addTemporaryModifier(daot.compat.AttributeModifiers.create(STEP_MOD_ID, 1.5, Operation.ADDITION));
      }
   }

   private static void removeStepHeight(ServerPlayerEntity player) {
      if (player != null) {
         EntityAttributeInstance step = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT);
         if (step != null) {
            daot.compat.AttributeModifiers.removeModifier(step, STEP_MOD_ID);
         }
      }
   }

   private static void applySpeedAndGravity(ServerPlayerEntity player) {
      EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
      if (speed != null && !daot.compat.AttributeModifiers.hasModifier(speed, SPEED_MOD_ID)) {
         speed.addTemporaryModifier(daot.compat.AttributeModifiers.create(SPEED_MOD_ID, 17.0, Operation.MULTIPLY_BASE));
      }

      EntityAttributeInstance gravity = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.GRAVITY);
      if (gravity != null && !daot.compat.AttributeModifiers.hasModifier(gravity, GRAVITY_MOD_ID)) {
         gravity.addTemporaryModifier(daot.compat.AttributeModifiers.create(GRAVITY_MOD_ID, 2.0, Operation.MULTIPLY_BASE));
      }

      EntityAttributeInstance jump = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.JUMP_STRENGTH);
      if (jump != null && !daot.compat.AttributeModifiers.hasModifier(jump, JUMP_MOD_ID)) {
         jump.addTemporaryModifier(daot.compat.AttributeModifiers.create(JUMP_MOD_ID, 2.0, Operation.MULTIPLY_BASE));
      }
   }

   private static void removeSpeedAndGravity(ServerPlayerEntity player) {
      if (player != null) {
         EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
         if (speed != null) {
            daot.compat.AttributeModifiers.removeModifier(speed, SPEED_MOD_ID);
         }

         EntityAttributeInstance gravity = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.GRAVITY);
         if (gravity != null) {
            daot.compat.AttributeModifiers.removeModifier(gravity, GRAVITY_MOD_ID);
         }

         EntityAttributeInstance jump = player.getAttributeInstance(daot.compat.attributes.DaotEntityAttributes.JUMP_STRENGTH);
         if (jump != null) {
            daot.compat.AttributeModifiers.removeModifier(jump, JUMP_MOD_ID);
         }
      }
   }
}
