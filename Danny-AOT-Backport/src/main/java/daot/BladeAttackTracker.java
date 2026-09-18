package daot;

import daot.network.EffectPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BladeAttackTracker {
   private static final List<BladeAttackTracker.ActiveAttack> activeAttacks = new ArrayList<>();
   private static float pendingNapeChargeFraction = 1.0F;
   private static final float TITAN_BLOODLINE_NAPE_MULT = 3.0F;
   private static final int MIN_CHARGE_TICKS = 12;
   private static final int ACKERMAN_MIN_CHARGE_TICKS = 10;
   private static final double SPEED_TO_CHARGE_TICKS = 8.0;
   private static final int SPINNER_HIT_INTERVAL = 5;
   private static final int SPINNER_MARGIN = 5;

   public static float consumeNapeChargeFraction() {
      float f = pendingNapeChargeFraction;
      pendingNapeChargeFraction = 1.0F;
      return f;
   }

   private static boolean isTitanWeakpoint(Entity entity) {
      String name = entity.getClass().getSimpleName();
      return name.contains("NapeEntity") || name.contains("EyeEntity");
   }

   private static boolean isTrainingDummy(Entity entity) {
      return entity instanceof TitanDummyEntity || entity instanceof TitanDummyNapeEntity || entity instanceof TitanDummyEyeEntity;
   }

   public static void startAttack(ServerPlayerEntity player, int attackType, int chargeTimeTicks, float playerSpeed) {
      if (!DefeatedCarryTracker.isIncapacitatedOrDefeated(player)) {
         boolean ogre = player.getCommandTags().contains("ogre_shifter");
         boolean escapesGrabs = ogre || player.getCommandTags().contains("titan_bloodline");
         Entity vehicle = player.getVehicle();
         if (vehicle instanceof AttackTitanGrabEntity ag) {
            AttackTitanEntity parent = ag.getParentTitan();
            if (parent != null && parent.isGrabbing()) {
               parent.releaseGrabbedEntity();
               if (escapesGrabs) {
                  spawnHandSliceEffects(player);
               }

               return;
            }
         } else if (vehicle instanceof FemaleTitanGrabEntity fg) {
            FemaleTitanEntity parent = fg.getParentTitan();
            if (parent != null && parent.isGrabbing()) {
               parent.releaseGrabbedEntity();
               if (escapesGrabs) {
                  spawnHandSliceEffects(player);
               }

               return;
            }
         } else if (escapesGrabs && vehicle instanceof BeastTitanGrabEntity bg) {
            BeastTitanEntity parent = bg.getParentTitan();
            if (parent != null && parent.getGrabbedEntityId() == player.getId()) {
               parent.releaseGrabbedEntity();
               spawnHandSliceEffects(player);
               return;
            }
         }

         if (escapesGrabs) {
            GrabbingTitan eater = null;
            if (vehicle instanceof GrabbingTitan gt && gt.getEatingTargetId() == player.getId()) {
               eater = gt;
            } else if (player.getWorld() instanceof ServerWorld sl) {
               Iterator var22 = sl.getEntitiesByClass(
                     LivingEntity.class, player.getBoundingBox().expand(24.0), e -> e instanceof GrabbingTitan g && g.getEatingTargetId() == player.getId()
                  )
                  .iterator();
               if (var22.hasNext()) {
                  LivingEntity le = (LivingEntity)var22.next();
                  eater = (GrabbingTitan)le;
               }
            }

            if (eater != null) {
               eater.cancelEating();
               eater.triggerEyeHurt();
               player.stopRiding();
               spawnHandSliceEffects(player);
               return;
            }
         }

         activeAttacks.removeIf(a -> a.playerUUID.equals(player.getUuid()));
         int currentTick = player.getServer().getTicks();
         boolean isAwakenedSpinner = false;
         if (player.getWorld() instanceof ServerWorld slx && PowerAuthority.isAckerman(player.getUuid()) && AwakenedPowerTracker.isActive(player.getUuid())) {
            isAwakenedSpinner = true;
         }

         int duration;
         if (isAwakenedSpinner) {
            duration = 60;
         } else if (attackType == 0) {
            duration = 10;
         } else {
            duration = 7;
         }

         int endTick = currentTick + duration;
         BladeAttackTracker.ActiveAttack attack = new BladeAttackTracker.ActiveAttack(player.getUuid(), attackType, currentTick, endTick);
         attack.isAwakenedSpinner = isAwakenedSpinner;
         attack.chargeTimeTicks = isAwakenedSpinner ? 12 : chargeTimeTicks;
         attack.attackerSpeed = playerSpeed;
         activeAttacks.add(attack);
      }
   }

   private static void spawnHandSliceEffects(ServerPlayerEntity player) {
      if (player.getWorld() instanceof ServerWorld level) {
         double var15 = player.getX();
         double y = player.getY() + player.getHeight() * 0.5;
         double z = player.getZ();
         SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
         SoundEvent slash = slashSounds[player.getRandom().nextInt(3)];
         float pitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
         level.playSound(null, var15, y, z, slash, SoundCategory.PLAYERS, 8.0F, pitch);
         EffectPayload bloodPayload = new EffectPayload("blood", var15, y, z, 1.5F);
         BlockPos pos = BlockPos.ofFloored(var15, y, z);

         for (ServerPlayerEntity p : PlayerLookup.tracking(level, pos)) {
            ServerPlayNetworking.send(p, bloodPayload);
         }
      }
   }

   public static void tick(MinecraftServer server) {
      int currentTick = server.getTicks();
      Iterator<BladeAttackTracker.ActiveAttack> iter = activeAttacks.iterator();

      while (iter.hasNext()) {
         BladeAttackTracker.ActiveAttack attack = iter.next();
         if (currentTick >= attack.endTick) {
            iter.remove();
         } else {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(attack.playerUUID);
            if (player != null && !player.isRemoved()) {
               ItemStack mainHand = player.getMainHandStack();
               ItemStack offHand = player.getOffHandStack();
               boolean mainBlade = mainHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY;
               boolean offBlade = offHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY;
               if (!mainBlade && !offBlade) {
                  iter.remove();
               } else {
                  if (attack.isAwakenedSpinner) {
                     int elapsed = currentTick - attack.startTick;
                     int remaining = attack.endTick - currentTick;
                     if (elapsed < 5 || remaining < 5 || attack.lastMultiHitTick >= 0 && currentTick - attack.lastMultiHitTick < 5) {
                        continue;
                     }

                     attack.hitEntities.clear();
                     attack.bladeDamaged = false;
                     attack.lastMultiHitTick = currentTick;
                     float swingPitch = 0.65F + player.getRandom().nextFloat() * 0.05F;
                     player.getWorld()
                        .playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_SWING, SoundCategory.PLAYERS, 0.4F, swingPitch);
                  }

                  float baseDamage = 9.0F;
                  boolean titanBloodline = player.getCommandTags().contains("titan_bloodline");
                  if (titanBloodline) {
                     baseDamage *= 4.0F;
                  }

                  boolean ogre = player.getCommandTags().contains("ogre_shifter");
                  if (ogre) {
                     baseDamage *= 4.0F;
                  }

                  boolean extendedReach = ogre || titanBloodline;
                  boolean oneShotsPureTitans = ogre || titanBloodline;
                  List<Entity> targets;
                  if (attack.attackType != 0 && !attack.isAwakenedSpinner) {
                     Vec3d lookDir = player.getRotationVector().normalize();
                     Box area = player.getBoundingBox().expand(extendedReach ? 4.0 : 2.0);
                     targets = player.getWorld().getOtherEntities(player, area, e -> {
                        if (e instanceof LivingEntity && e.isAlive() && e != player) {
                           if (attack.hitEntities.contains(e.getId())) {
                              return false;
                           } else {
                              Vec3d toEntity = e.getPos().subtract(player.getPos()).normalize();
                              double dot = lookDir.dotProduct(toEntity);
                              return dot > Math.cos(Math.toRadians(22.5));
                           }
                        } else {
                           return false;
                        }
                     });
                  } else {
                     double aoeRange = ModConfig.get().bladeAirSwingRange;
                     if (player.getWorld() instanceof ServerWorld sl && PowerAuthority.isAckerman(player.getUuid())) {
                        if (AwakenedPowerTracker.isActive(player.getUuid())) {
                           aoeRange = ModConfig.get().ackermanAwakenedBladeAirSwingRange;
                        } else {
                           aoeRange = ModConfig.get().ackermanBladeAirSwingRange;
                        }
                     }

                     if (extendedReach) {
                        aoeRange *= 2.0;
                     }

                     Box area = player.getBoundingBox().expand(aoeRange);
                     targets = player.getWorld()
                        .getOtherEntities(player, area, e -> e instanceof LivingEntity && e.isAlive() && e != player && !attack.hitEntities.contains(e.getId()));
                  }

                  if (!attack.isAwakenedSpinner && player.isOnGround()) {
                     Vec3d lookDir = player.getRotationVector().normalize();
                     Box area = player.getBoundingBox().expand(extendedReach ? 4.0 : 2.0);

                     for (Entity ct : player.getWorld().getOtherEntities(player, area, e -> {
                        if (e instanceof LivingEntity && e.isAlive() && e != player) {
                           if (attack.hitEntities.contains(e.getId())) {
                              return false;
                           } else {
                              Vec3d toEntity = e.getPos().subtract(player.getPos()).normalize();
                              double dot = lookDir.dotProduct(toEntity);
                              return dot > Math.cos(Math.toRadians(22.5));
                           }
                        } else {
                           return false;
                        }
                     })) {
                        if (!targets.contains(ct)) {
                           targets.add(ct);
                        }
                     }
                  }

                  if (!targets.isEmpty()) {
                     boolean chargedMode = DannysAot.isChargedODMAttacksEnabled(player.getWorld());
                     int requiredCharge = 12;
                     if (player.getWorld() instanceof ServerWorld sl2 && PowerAuthority.isAckerman(player.getUuid())) {
                        requiredCharge = 10;
                     }

                     double effectiveCharge = attack.chargeTimeTicks + attack.attackerSpeed * 8.0;
                     boolean isDeepSlice = attack.isAwakenedSpinner || ogre || effectiveCharge >= requiredCharge;
                     boolean hitNonDummy = false;
                     DamageSource source = player.getDamageSources().playerAttack(player);

                     for (Entity target : targets) {
                        if (target instanceof LivingEntity living) {
                           boolean weakpoint = isTitanWeakpoint(target);
                           boolean isNape = weakpoint && target.getClass().getSimpleName().contains("NapeEntity");
                           if (!isTrainingDummy(target)) {
                              hitNonDummy = true;
                           }

                           if (oneShotsPureTitans) {
                              LivingEntity pureTitan = null;
                              if (living instanceof GrabbingTitan) {
                                 pureTitan = living;
                              } else if (target instanceof PureTitanHitbox hitbox) {
                                 pureTitan = hitbox.pureTitanOwner();
                              }

                              if (pureTitan != null && pureTitan.isAlive()) {
                                 pureTitan.damage(source, Float.MAX_VALUE);
                                 pureTitan.timeUntilRegen = 0;
                                 attack.hitEntities.add(target.getId());
                                 continue;
                              }
                           }

                           if (chargedMode && weakpoint && !isNape && !isDeepSlice) {
                              SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
                              SoundEvent slash = slashSounds[player.getRandom().nextInt(3)];
                              float pitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
                              player.getWorld().playSound(null, living.getX(), living.getY(), living.getZ(), slash, SoundCategory.PLAYERS, 8.0F, pitch);
                              attack.hitEntities.add(target.getId());
                              if (!attack.shallowSliceMessageSent) {
                                 attack.shallowSliceMessageSent = true;
                                 boolean isDummy = target instanceof TitanDummyNapeEntity || target instanceof TitanDummyEyeEntity;
                                 if (isDummy) {
                                    player.sendMessage(
                                       Text.literal("Not a deep enough slice! ")
                                          .styled(style -> style.withColor(Formatting.RED))
                                          .append(
                                             Text.literal("(Power: " + (int)Math.floor(effectiveCharge) + "/" + requiredCharge + ")")
                                                .styled(style -> style.withColor(Formatting.GRAY))
                                          ),
                                       true
                                    );
                                 } else {
                                    player.sendMessage(Text.literal("Not a deep enough slice!").styled(style -> style.withColor(Formatting.RED)), true);
                                 }
                              }
                           } else {
                              float napeChargeFraction = 1.0F;
                              if (chargedMode && isNape && !ogre) {
                                 napeChargeFraction = (float)Math.max(0.0, Math.min(1.0, effectiveCharge / requiredCharge));
                                 if (napeChargeFraction < 1.0F && !attack.shallowSliceMessageSent) {
                                    attack.shallowSliceMessageSent = true;
                                    player.sendMessage(
                                       Text.literal("Partial slice — charge more for a full cut! ")
                                          .styled(style -> style.withColor(Formatting.YELLOW))
                                          .append(
                                             Text.literal("(Power: " + (int)Math.floor(effectiveCharge) + "/" + requiredCharge + ")")
                                                .styled(style -> style.withColor(Formatting.GRAY))
                                          ),
                                       true
                                    );
                                 }
                              }

                              if (isNape && titanBloodline) {
                                 napeChargeFraction *= 3.0F;
                              }

                              float dmg = baseDamage;
                              if (living instanceof PlayerEntity) {
                                 dmg = baseDamage * 0.5F;
                              }

                              if (isNape) {
                                 pendingNapeChargeFraction = napeChargeFraction;
                              }

                              living.damage(source, dmg);
                              if (isNape) {
                                 pendingNapeChargeFraction = 1.0F;
                              }

                              living.timeUntilRegen = 0;
                              attack.hitEntities.add(target.getId());
                              if (living instanceof PlayerEntity) {
                                 SoundEvent[] slashSounds = new SoundEvent[]{ModSounds.SLASH1, ModSounds.SLASH2, ModSounds.SLASH3};
                                 SoundEvent slash = slashSounds[player.getRandom().nextInt(3)];
                                 float pitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
                                 player.getWorld().playSound(null, living.getX(), living.getY(), living.getZ(), slash, SoundCategory.PLAYERS, 8.0F, pitch);
                                 if (player.getWorld() instanceof ServerWorld serverLevel) {
                                    BlockStateParticleEffect bloodParticle = new BlockStateParticleEffect(
                                       ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()
                                    );
                                    serverLevel.spawnParticles(
                                       bloodParticle, living.getX(), living.getY() + living.getHeight() * 0.5, living.getZ(), 20, 0.3, 0.4, 0.3, 0.1
                                    );
                                 }
                              }
                           }
                        }
                     }

                     if (!attack.bladeDamaged && !attack.isAwakenedSpinner && hitNonDummy) {
                        attack.bladeDamaged = true;
                        if (mainBlade) {
                           BladeItem.damageBladeByAmount(mainHand, 1, player);
                        }

                        if (offBlade) {
                           BladeItem.damageBladeByAmount(offHand, 1, player);
                        }
                     }
                  }
               }
            } else {
               iter.remove();
            }
         }
      }
   }

   public static void onPlayerDisconnect(UUID uuid) {
      activeAttacks.removeIf(a -> a.playerUUID.equals(uuid));
   }

   private static class ActiveAttack {
      final UUID playerUUID;
      final int attackType;
      final int startTick;
      final int endTick;
      final Set<Integer> hitEntities = new HashSet<>();
      boolean bladeDamaged = false;
      boolean isAwakenedSpinner = false;
      int lastMultiHitTick = -1;
      int chargeTimeTicks = 0;
      double attackerSpeed = 0.0;
      boolean shallowSliceMessageSent = false;

      ActiveAttack(UUID playerUUID, int attackType, int startTick, int endTick) {
         this.playerUUID = playerUUID;
         this.attackType = attackType;
         this.startTick = startTick;
         this.endTick = endTick;
      }
   }
}
