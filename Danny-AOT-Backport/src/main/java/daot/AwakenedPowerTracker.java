package daot;

import daot.network.AwakenedPowerSyncPayload;
import daot.network.EffectPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AwakenedPowerTracker {
   public static final float MAX_CHARGE = 300.0F;
   public static final float DRAIN_PER_TICK = 0.6666667F;
   public static final float REFILL_PER_TICK = 0.25F;
   public static final String AWAKENED_POWER_TAG = "dannys-aot:awakened_power";
   private static final int HEARTBEAT_INTERVAL_TICKS = 10;
   private static final Map<UUID, Long> lastContactSwingTick = new HashMap<>();
   private static final int CONTACT_SWING_COOLDOWN_TICKS = 8;
   private static final float SHIFTER_NAPE_DAMAGE_NORMAL = 26.0F;
   private static final float SHIFTER_NAPE_DAMAGE_COLOSSAL = 50.0F;
   private static final int SHIFTER_HIT_COOLDOWN_TICKS = 10;
   private static final Map<UUID, Float> charges = new HashMap<>();
   private static final Map<UUID, Boolean> activeStates = new HashMap<>();
   private static final Map<Integer, Long> shifterHitCooldowns = new HashMap<>();
   private static volatile UUID clientActivePlayerUUID = null;
   private static boolean initialized = false;

   public static void setClientActivePlayer(UUID uuid) {
      clientActivePlayerUUID = uuid;
   }

   public static boolean isClientPlayerActive(UUID uuid) {
      return uuid != null && uuid.equals(clientActivePlayerUUID);
   }

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            for (Entry<UUID, Float> entry : charges.entrySet()) {
               UUID playerUUID = entry.getKey();
               float charge = entry.getValue();
               boolean active = activeStates.getOrDefault(playerUUID, false);
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
               if (player == null) {
                  if (active) {
                     activeStates.put(playerUUID, false);
                  }
               } else if (active && player.isDead()) {
                  activeStates.put(playerUUID, false);
                  player.removeScoreboardTag("dannys-aot:awakened_power");
                  removeHungerEffect(player);
                  entry.setValue(charge);
                  syncToClient(player, charge, false);
               } else if (active) {
                  charge -= 0.6666667F;
                  if (charge <= 0.0F) {
                     charge = 0.0F;
                     activeStates.put(playerUUID, false);
                     player.removeScoreboardTag("dannys-aot:awakened_power");
                     removeHungerEffect(player);
                     entry.setValue(charge);
                     syncToClient(player, charge, false);
                  } else {
                     if (server.getTicks() % 40 == 0) {
                        applyHungerEffect(player);
                     }

                     if (server.getTicks() % 10 == 0 && player.getWorld() instanceof ServerWorld sl) {
                        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0F, 0.7F);
                     }

                     entry.setValue(charge);
                     if (player.getWorld() instanceof ServerWorld serverLevel) {
                        spawnOutwardParticles(serverLevel, player);
                        checkContactDamage(serverLevel, player);
                     }

                     if (server.getTicks() % 5 == 0) {
                        syncToClient(player, charge, true);
                     }
                  }
               } else {
                  if (player.getCommandTags().contains("dannys-aot:awakened_power")) {
                     player.removeScoreboardTag("dannys-aot:awakened_power");
                  }

                  if (charge < 300.0F) {
                     charge = Math.min(300.0F, charge + 0.25F);
                     entry.setValue(charge);
                     if (server.getTicks() % 20 == 0) {
                        syncToClient(player, charge, false);
                     }
                  }
               }
            }
         });
         DannysAot.LOGGER.info("AwakenedPowerTracker initialized");
      }
   }

   private static void syncToClient(ServerPlayerEntity player, float charge, boolean active) {
      ServerPlayNetworking.send(player, new AwakenedPowerSyncPayload(charge, 300.0F, active));
   }

   private static void spawnOutwardParticles(ServerWorld level, ServerPlayerEntity player) {
      double cx = player.getX();
      double cy = player.getY() + player.getHeight() * 0.5;
      double cz = player.getZ();
      float halfWidth = player.getWidth() * 0.5F + 0.2F;
      float halfHeight = player.getHeight() * 0.5F;

      for (int i = 0; i < 2; i++) {
         double theta = player.getRandom().nextDouble() * Math.PI * 2.0;
         double phi = player.getRandom().nextDouble() * Math.PI - (Math.PI / 2);
         double dx = Math.cos(phi) * Math.cos(theta);
         double dy = Math.sin(phi);
         double dz = Math.cos(phi) * Math.sin(theta);
         double startX = cx + dx * halfWidth;
         double startY = cy + dy * halfHeight;
         double startZ = cz + dz * halfWidth;
         double speed = 0.15 + player.getRandom().nextDouble() * 0.1;
         level.spawnParticles(ParticleTypes.LARGE_SMOKE, startX, startY, startZ, 0, dx * speed, dy * speed, dz * speed, 1.0);
      }
   }

   private static void checkContactDamage(ServerWorld level, ServerPlayerEntity player) {
      ItemStack mainHand = player.getMainHandStack();
      ItemStack offHand = player.getOffHandStack();
      boolean mainBlade = mainHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY;
      boolean offBlade = offHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY;
      if (mainBlade || offBlade) {
         Box playerBox = player.getBoundingBox();
         List<Entity> nearby = level.getOtherEntities(player, playerBox.expand(1.0));
         long currentTick = level.getServer().getTicks();
         boolean hitOccurred = false;

         for (Entity entity : nearby) {
            if (playerBox.intersects(entity.getBoundingBox())) {
               if (entity instanceof SmallTitanEyeEntity eye) {
                  SmallTitanEntity parent = eye.getParentTitan();
                  if (parent != null && !parent.isDead() && !parent.isEyeHurt()) {
                     spawnContactBloodEffects(level, eye);
                     parent.triggerEyeHurt();
                     hitOccurred = true;
                  }
               } else if (entity instanceof TitanEyeEntity eyex) {
                  TitanEntity parent = eyex.getParentTitan();
                  if (parent != null && !parent.isDead() && !parent.isEyeHurt()) {
                     spawnContactBloodEffects(level, eyex);
                     parent.triggerEyeHurt();
                     hitOccurred = true;
                  }
               } else if (entity instanceof SmallTitan2EyeEntity eyexx) {
                  SmallTitan2Entity parent = eyexx.getParentTitan();
                  if (parent != null && !parent.isDead() && !parent.isEyeHurt()) {
                     spawnContactBloodEffects(level, eyexx);
                     parent.triggerEyeHurt();
                     hitOccurred = true;
                  }
               } else if (entity instanceof FritzTitanEyeEntity eyexxx) {
                  FritzTitanEntity parent = eyexxx.getParentTitan();
                  if (parent != null && !parent.isDead() && !parent.isEyeHurt()) {
                     spawnContactBloodEffects(level, eyexxx);
                     parent.triggerEyeHurt();
                     hitOccurred = true;
                  }
               } else if (entity instanceof SmallTitanNapeEntity nape) {
                  SmallTitanEntity parent = nape.getParentTitan();
                  if (parent != null && !parent.isDead()) {
                     spawnContactBloodEffects(level, nape);
                     parent.damage(player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
                     hitOccurred = true;
                  }
               } else if (entity instanceof TitanNapeEntity napex) {
                  TitanEntity parent = napex.getParentTitan();
                  if (parent != null && !parent.isDead()) {
                     spawnContactBloodEffects(level, napex);
                     parent.damage(player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
                     hitOccurred = true;
                  }
               } else if (entity instanceof SmallTitan2NapeEntity napexx) {
                  SmallTitan2Entity parent = napexx.getParentTitan();
                  if (parent != null && !parent.isDead()) {
                     spawnContactBloodEffects(level, napexx);
                     parent.damage(player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
                     hitOccurred = true;
                  }
               } else if (entity instanceof FritzTitanNapeEntity napexxx) {
                  FritzTitanEntity parent = napexxx.getParentTitan();
                  if (parent != null && !parent.isDead()) {
                     spawnContactBloodEffects(level, napexxx);
                     parent.damage(player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
                     hitOccurred = true;
                  }
               } else if (entity instanceof AttackTitanEyeEntity eyexxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     AttackTitanEntity parent = eyexxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, eyexxxx);
                        parent.triggerBlindness();
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof ArmoredTitanEyeEntity eyexxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     ArmoredTitanEntity parent = eyexxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, eyexxxxx);
                        parent.triggerBlindness();
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof ColossalTitanEyeEntity eyexxxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     ColossalTitanEntity parent = eyexxxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, eyexxxxxx);
                        parent.triggerBlindness();
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof FemaleTitanEyeEntity eyexxxxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     FemaleTitanEntity parent = eyexxxxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, eyexxxxxxx);
                        parent.triggerBlindness();
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof WarhammerTitanEyeEntity eyexxxxxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     WarhammerTitanEntity parent = eyexxxxxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, eyexxxxxxxx);
                        parent.triggerBlindness();
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof AttackTitanNapeEntity napexxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     AttackTitanEntity parent = napexxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, napexxxx);
                        parent.hurtFromNape(player.getDamageSources().playerAttack(player), 26.0F);
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof ArmoredTitanNapeEntity napexxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     ArmoredTitanEntity parent = napexxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, napexxxxx);
                        parent.hurtFromNape(player.getDamageSources().playerAttack(player), 26.0F);
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof ColossalTitanNapeEntity napexxxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     ColossalTitanEntity parent = napexxxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, napexxxxxx);
                        parent.hurtFromNape(player.getDamageSources().playerAttack(player), 50.0F);
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof FemaleTitanNapeEntity napexxxxxxx) {
                  if (isShifterHitReady(entity.getId(), currentTick)) {
                     FemaleTitanEntity parent = napexxxxxxx.getParentTitan();
                     if (parent != null && !parent.isDead()) {
                        spawnContactBloodEffects(level, napexxxxxxx);
                        parent.hurtFromNape(player.getDamageSources().playerAttack(player), 26.0F);
                        shifterHitCooldowns.put(entity.getId(), currentTick);
                        hitOccurred = true;
                     }
                  }
               } else if (entity instanceof WarhammerTitanNapeEntity napexxxxxxxx && isShifterHitReady(entity.getId(), currentTick)) {
                  WarhammerTitanEntity parent = napexxxxxxxx.getParentTitan();
                  if (parent != null && !parent.isDead()) {
                     spawnContactBloodEffects(level, napexxxxxxxx);
                     parent.hurtFromNape(player.getDamageSources().playerAttack(player), 26.0F);
                     shifterHitCooldowns.put(entity.getId(), currentTick);
                     hitOccurred = true;
                  }
               }
            }
         }

         if (hitOccurred) {
            UUID playerUUID = player.getUuid();
            Long lastSwing = lastContactSwingTick.get(playerUUID);
            if (lastSwing == null || currentTick - lastSwing >= 8L) {
               ServerPlayNetworking.send(player, new EffectPayload("blade_swing", 0.0, 0.0, 0.0, 0.0F));
               lastContactSwingTick.put(playerUUID, currentTick);
            }
         }
      }
   }

   private static boolean isShifterHitReady(int entityId, long currentTick) {
      Long lastHit = shifterHitCooldowns.get(entityId);
      return lastHit == null || currentTick - lastHit >= 10L;
   }

   private static void applyHungerEffect(ServerPlayerEntity player) {
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 60, 19, false, false, false));
   }

   private static void removeHungerEffect(ServerPlayerEntity player) {
      player.removeStatusEffect(StatusEffects.HUNGER);
   }

   private static void spawnContactBloodEffects(ServerWorld level, Entity hitbox) {
      BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
      level.spawnParticles(bloodParticle, hitbox.getX(), hitbox.getY(), hitbox.getZ(), 25, 0.3, 0.3, 0.3, 0.1);
      EffectPayload bloodPayload = new EffectPayload("blood", hitbox.getX(), hitbox.getY(), hitbox.getZ(), 1.0F);

      for (ServerPlayerEntity p : PlayerLookup.tracking(level, new BlockPos((int)hitbox.getX(), (int)hitbox.getY(), (int)hitbox.getZ()))) {
         ServerPlayNetworking.send(p, bloodPayload);
      }

      SoundEvent[] impactSounds = new SoundEvent[]{
         ModSounds.FLESH_IMPACT_1,
         ModSounds.FLESH_IMPACT_2,
         ModSounds.FLESH_IMPACT_3,
         ModSounds.FLESH_IMPACT_4,
         ModSounds.FLESH_IMPACT_5,
         ModSounds.FLESH_IMPACT_6,
         ModSounds.FLESH_IMPACT_7
      };
      SoundEvent sound = impactSounds[hitbox.getWorld().random.nextInt(impactSounds.length)];
      float pitch = 0.9F + hitbox.getWorld().random.nextFloat() * 0.2F;
      level.playSound(null, hitbox.getX(), hitbox.getY(), hitbox.getZ(), sound, SoundCategory.PLAYERS, 6.0F, pitch);
      SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
      SoundEvent slashSound = slashSounds[hitbox.getWorld().random.nextInt(slashSounds.length)];
      float slashPitch = 0.9F + hitbox.getWorld().random.nextFloat() * 0.2F;
      level.playSound(null, hitbox.getX(), hitbox.getY(), hitbox.getZ(), slashSound, SoundCategory.PLAYERS, 4.0F, slashPitch);
   }

   public static boolean activate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      float charge = charges.getOrDefault(uuid, 300.0F);
      if (charge < 300.0F) {
         return false;
      } else {
         player.addCommandTag("dannys-aot:awakened_power");
         activeStates.put(uuid, true);
         charges.put(uuid, 300.0F);
         syncToClient(player, 300.0F, true);
         if (player.getWorld() instanceof ServerWorld serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.0F, 0.5F);
         }

         applyHungerEffect(player);
         if (player.getCommandTags().contains("dannys-aot:being_grabbed")) {
            stunGrabbingTitan(player);
         }

         DannysAot.LOGGER.info("Awakened Power activated for {}", player.getName().getString());
         return true;
      }
   }

   public static void deactivate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      float charge = charges.getOrDefault(uuid, 0.0F);
      activeStates.put(uuid, false);
      player.removeScoreboardTag("dannys-aot:awakened_power");
      removeHungerEffect(player);
      syncToClient(player, charge, false);
      DannysAot.LOGGER.info("Awakened Power deactivated early for {}", player.getName().getString());
   }

   private static void stunGrabbingTitan(ServerPlayerEntity player) {
      Entity vehicle = player.getVehicle();
      if (vehicle == null || !tryStunTitan(vehicle)) {
         if (player.getWorld() instanceof ServerWorld serverLevel) {
            Box searchBox = player.getBoundingBox().expand(30.0);

            for (Entity entity : serverLevel.getOtherEntities(player, searchBox)) {
               if (entity instanceof TitanEntity titan && titan.getEatingTarget() == player) {
                  titan.triggerEyeHurt();
                  return;
               }

               if (entity instanceof SmallTitanEntity titan && titan.getEatingTarget() == player) {
                  titan.triggerEyeHurt();
                  return;
               }

               if (entity instanceof SmallTitan2Entity titan && titan.getEatingTarget() == player) {
                  titan.triggerEyeHurt();
                  return;
               }

               if (entity instanceof FritzTitanEntity titan && titan.getEatingTarget() == player) {
                  titan.triggerEyeHurt();
                  return;
               }
            }
         }
      }
   }

   private static boolean tryStunTitan(Entity entity) {
      if (entity instanceof TitanEntity titan) {
         titan.triggerEyeHurt();
         return true;
      } else if (entity instanceof SmallTitanEntity titan) {
         titan.triggerEyeHurt();
         return true;
      } else if (entity instanceof SmallTitan2Entity titan) {
         titan.triggerEyeHurt();
         return true;
      } else if (entity instanceof FritzTitanEntity titan) {
         titan.triggerEyeHurt();
         return true;
      } else {
         return false;
      }
   }

   public static void ensureTracked(UUID playerUUID) {
      charges.putIfAbsent(playerUUID, 300.0F);
      activeStates.putIfAbsent(playerUUID, false);
   }

   public static boolean isActive(UUID playerUUID) {
      return activeStates.getOrDefault(playerUUID, false);
   }

   public static boolean isFullyCharged(UUID playerUUID) {
      return charges.getOrDefault(playerUUID, 300.0F) >= 300.0F;
   }

   public static float getCharge(UUID playerUUID) {
      return charges.getOrDefault(playerUUID, 300.0F);
   }

   public static void fillCharge(ServerPlayerEntity player) {
      charges.put(player.getUuid(), 300.0F);
      activeStates.put(player.getUuid(), false);
      player.removeScoreboardTag("dannys-aot:awakened_power");
      float ratio = 1.0F;
      boolean active = false;
      ServerPlayNetworking.send(player, new AwakenedPowerSyncPayload(300.0F, 300.0F, active));
   }

   public static void removePlayer(UUID playerUUID) {
      charges.remove(playerUUID);
      activeStates.remove(playerUUID);
   }
}
