package daot;

import daot.network.GasSyncPayload;
import daot.network.KineticOdmImpactPayload;
import daot.network.ODMHookUpdatePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@Environment(EnvType.CLIENT)
public class ODMTickHandler {
   private static final Map<UUID, HookPoint> leftHooks = new HashMap<>();
   private static final Map<UUID, HookPoint> rightHooks = new HashMap<>();
   private static final Map<UUID, Long> lastHookTime = new HashMap<>();
   private static final Map<UUID, Boolean> lastHookSide = new HashMap<>();
   private static final Map<UUID, Double> boostLevels = new HashMap<>();
   private static final Map<UUID, Vec3d> storedMomentum = new HashMap<>();
   private static final Map<UUID, Integer> gasTickCounter = new HashMap<>();
   private static final Map<UUID, Boolean> perchingForGas = new HashMap<>();
   private static final Map<UUID, Boolean> wasDualHooking = new HashMap<>();
   private static final Map<UUID, Long> dualHookStartTime = new HashMap<>();
   private static final Map<UUID, Boolean> leftHookWasLatched = new HashMap<>();
   private static final Map<UUID, Boolean> rightHookWasLatched = new HashMap<>();
   private static final Map<UUID, Boolean> titanWasCrouchingLastTick = new HashMap<>();
   private static final Map<UUID, Set<UUID>> hooksLatchedBeforeCrouch = new HashMap<>();
   private static final Map<UUID, Long> titanWireBreakCooldowns = new HashMap<>();
   private static final long TITAN_WIRE_BREAK_COOLDOWN_MS = 7000L;
   private static final Map<UUID, Long> odmJamEndTime = new HashMap<>();
   private static final long ODM_JAM_DURATION_MS = 1500L;
   private static int weatherJamRollTick = 0;
   private static final int WEATHER_JAM_ROLL_INTERVAL_TICKS = 20;
   private static final double WEATHER_JAM_CHANCE = 0.05;
   private static final long WEATHER_JAM_MIN_MS = 2000L;
   private static final long WEATHER_JAM_MAX_MS = 5000L;
   private static boolean leftJamSoundPlayed = false;
   private static boolean rightJamSoundPlayed = false;
   private static boolean leftDisabledSoundPlayed = false;
   private static boolean rightDisabledSoundPlayed = false;
   private static boolean odmAllowedByGamerule = true;
   private static boolean grassOdmAllowed = true;
   private static boolean weatherAffectsOdm = false;
   private static boolean kineticOdmDamage = false;
   private static double prevTickHorizontalSpeed = 0.0;
   private static final double KINETIC_IMPACT_THRESHOLD = 1.2;
   private static int hookSyncTickCounter = 0;
   private static final int HOOK_SYNC_INTERVAL = 2;
   private static boolean lastSyncLeftActive = false;
   private static boolean lastSyncRightActive = false;
   private static boolean lastSyncBoosting = false;
   private static boolean currentlyBoosting = false;
   private static final Set<Block> IGNORED_BLOCKS = Set.of(
      Blocks.GRASS, Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.FERN, Blocks.DEAD_BUSH, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY
   );

   private static float getODMPitch(PlayerEntity player) {
      return DannysAot.isAPG(player.getEquippedStack(EquipmentSlot.LEGS).getItem()) ? 1.5F : 1.0F;
   }

   static float getRemoteODMPitch(Entity entity) {
      if (entity instanceof PlayerEntity player) {
         return DannysAot.isAPG(player.getEquippedStack(EquipmentSlot.LEGS).getItem()) ? 1.5F : 1.0F;
      } else {
         return 1.0F;
      }
   }

   public static HookPoint getLeftHook() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null ? leftHooks.get(mc.player.getUuid()) : null;
   }

   public static HookPoint getRightHook() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null ? rightHooks.get(mc.player.getUuid()) : null;
   }

   public static void setODMAllowedByGamerule(boolean allowed) {
      odmAllowedByGamerule = allowed;
   }

   public static void setGrassODMAllowed(boolean allowed) {
      grassOdmAllowed = allowed;
   }

   public static void setWeatherAffectsOdm(boolean enabled) {
      weatherAffectsOdm = enabled;
   }

   public static void setKineticOdmDamage(boolean enabled) {
      kineticOdmDamage = enabled;
   }

   private static long getDualHookEaseTime() {
      return ODMConfig.get().dualHookEaseTime;
   }

   private static int getGasTickInterval() {
      return ODMConfig.get().gasTickInterval;
   }

   private static int getGasConsumptionNormal() {
      return ODMConfig.get().gasConsumptionNormal;
   }

   private static int getGasConsumptionBoost() {
      return ODMConfig.get().gasConsumptionBoost;
   }

   private static double getBasePullSpeed() {
      return ODMConfig.get().basePullSpeed;
   }

   private static double getDualHookPullMultiplier() {
      return ODMConfig.get().dualHookPullMultiplier;
   }

   private static double getDualHookBoostPullMultiplier() {
      return ODMConfig.get().dualHookBoostPullMultiplier;
   }

   private static double getOrbitPullMultiplier() {
      return ODMConfig.get().orbitPullMultiplier;
   }

   private static double getBaseOrbitSpeed() {
      return ODMConfig.get().baseOrbitSpeed;
   }

   private static double getDualHookOrbitMultiplier() {
      return ODMConfig.get().dualHookOrbitMultiplier;
   }

   private static double getUpwardLift() {
      return ODMConfig.get().upwardLift;
   }

   private static double getBoostPullMultiplier() {
      return ODMConfig.get().boostPullMultiplier;
   }

   private static double getBoostOrbitMultiplier() {
      return ODMConfig.get().boostOrbitMultiplier;
   }

   private static double getBoostRampRate() {
      return ODMConfig.get().boostRampRate;
   }

   private static double getBoostDecayRate() {
      return ODMConfig.get().boostDecayRate;
   }

   private static double getMaxHookDistance() {
      return ODMConfig.get().maxHookDistance;
   }

   private static long getMomentumPreserveTime() {
      return ODMConfig.get().momentumPreserveTime;
   }

   private static double getFlightSoundVelocityThreshold() {
      return ODMConfig.get().flightSoundVelocityThreshold;
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (client.player != null) {
                  ClientPlayerEntity player = client.player;
                  if (isWearingODMGear(player) && !player.isSpectator()) {
                     HookPoint leftHook = leftHooks.computeIfAbsent(player.getUuid(), k -> new HookPoint());
                     HookPoint rightHook = rightHooks.computeIfAbsent(player.getUuid(), k -> new HookPoint());
                     if (player.hasVehicle()) {
                        clearHooks(player);
                        syncHooksToServer(player, leftHook, rightHook);
                     } else {
                        if (player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                           StatusEffectInstance weaknessEffect = player.getStatusEffect(StatusEffects.WEAKNESS);
                           if (weaknessEffect != null && weaknessEffect.getAmplifier() >= 4) {
                              clearHooks(player);
                              syncHooksToServer(player, leftHook, rightHook);
                              return;
                           }
                        }

                        updateHookEntityPosition(leftHook);
                        updateHookEntityPosition(rightHook);
                        boolean wireBroke = checkAttackTitanCrouchAction(leftHook, rightHook, player);
                        wireBroke = wireBroke || checkArmoredTitanCrouchAction(leftHook, rightHook, player);
                        wireBroke = wireBroke || checkFemaleTitanCrouchAction(leftHook, rightHook, player);
                        if (wireBroke) {
                           player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                           player.sendMessage(Text.literal("Wire broke!").styled(style -> style.withColor(Formatting.RED).withBold(true)), true);
                        }

                        boolean wasHooked = leftHook.active && !leftHook.isExtending && !leftHook.isRetracting
                           || rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
                        Vec3d velocity = player.getVelocity();
                        leftHook.updateVelocity(velocity);
                        rightHook.updateVelocity(velocity);
                        float yaw = (float)Math.toRadians(player.getYaw());
                        double legOffset = 0.3;
                        Vec3d playerPos = player.getPos();
                        Vec3d leftLegPos = playerPos.add(Math.cos(yaw) * legOffset, 0.1, Math.sin(yaw) * legOffset);
                        Vec3d rightLegPos = playerPos.add(-Math.cos(yaw) * legOffset, 0.1, -Math.sin(yaw) * legOffset);
                        boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
                        Hand visualLeftHand = mainIsRight ? Hand.OFF_HAND : Hand.MAIN_HAND;
                        Hand visualRightHand = mainIsRight ? Hand.MAIN_HAND : Hand.OFF_HAND;
                        ItemStack odmGear = player.getEquippedStack(EquipmentSlot.LEGS);
                        boolean hasLeftBlade = DannysAot.isValidGripForLeggings(odmGear, player.getStackInHand(visualLeftHand).getItem());
                        boolean hasRightBlade = DannysAot.isValidGripForLeggings(odmGear, player.getStackInHand(visualRightHand).getItem());
                        boolean hasGas = DannysAot.gearHasGas(odmGear, player);
                        tryWeatherJam(player, leftHook, rightHook);
                        tryKineticImpact(player, leftHook, rightHook);
                        boolean isOdmJammed = isOdmJammed(player.getUuid());
                        boolean isOdmDisabled = !odmAllowedByGamerule && !player.isCreative() && !player.hasPermissionLevel(2);
                        if (!HandcuffsTracker.isClientCuffed(player.getUuid()) && !player.getCommandTags().contains("handcuffed")) {
                           if (CombatModeState.isLeftHookDown()) {
                              if (!hasLeftBlade) {
                                 if (!leftHook.active) {
                                    if (mainIsRight) {
                                       ODMGasHUD.showNoBladeOffhand();
                                    } else {
                                       ODMGasHUD.showNoBladeMainhand();
                                    }
                                 }
                              } else if (!hasGas) {
                                 if (!leftHook.active) {
                                    ODMGasHUD.showOutOfGas();
                                 }
                              } else if (isOdmDisabled && !leftHook.active) {
                                 if (!leftDisabledSoundPlayed) {
                                    ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                    leftDisabledSoundPlayed = true;
                                 }

                                 player.sendMessage(Text.literal("ODM is Disabled").styled(style -> style.withColor(Formatting.RED)), true);
                              } else if (isOdmJammed && !leftHook.active) {
                                 if (!leftJamSoundPlayed) {
                                    ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                    leftJamSoundPlayed = true;
                                 }

                                 player.sendMessage(Text.literal("ODM jammed!").styled(style -> style.withColor(Formatting.RED)), true);
                              } else if (!leftHook.active) {
                                 HitResult hit = getPlayerLookTarget(player);
                                 if (hit != null && hit.getType() != Type.MISS) {
                                    if (hit.getType() == Type.ENTITY) {
                                       Entity entity = ((EntityHitResult)hit).getEntity();
                                       if (isKennyHatProtected(entity)) {
                                          if (!leftJamSoundPlayed) {
                                             ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                             leftJamSoundPlayed = true;
                                          }

                                          jamOdmForDuration(player.getUuid(), 5000L);
                                          player.sendMessage(Text.literal("ODM jammed!").styled(style -> style.withColor(Formatting.RED)), true);
                                       } else {
                                          Vec3d hookPos = hit.getPos();
                                          ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                          leftHook.setHookOnEntity(hookPos, leftLegPos, entity);
                                          spawnBloodParticles(player, hookPos);
                                          leftHook.maxRopeLength = leftLegPos.distanceTo(hookPos);
                                          BladeAnimationHandler.triggerFire(visualLeftHand);
                                       }
                                    } else {
                                       Vec3d hookPos = hit.getPos();
                                       ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                       leftHook.setHook(hookPos, leftLegPos);
                                       spawnHookImpactParticles(player, hookPos, hit);
                                       leftHook.maxRopeLength = leftLegPos.distanceTo(hookPos);
                                       BladeAnimationHandler.triggerFire(visualLeftHand);
                                    }
                                 }
                              }
                           } else {
                              BladeAnimationHandler.release(visualLeftHand);
                              leftJamSoundPlayed = false;
                              leftDisabledSoundPlayed = false;
                              if (leftHook.active && !leftHook.isRetracting) {
                                 ODMSoundManager.playLocalOneShot(ModSounds.HOOK_RETRACT, 0.7F, getODMPitch(player));
                                 leftHook.startRetract(leftLegPos);
                              }
                           }

                           if (CombatModeState.isRightHookDown()) {
                              if (!hasRightBlade) {
                                 if (!rightHook.active) {
                                    if (mainIsRight) {
                                       ODMGasHUD.showNoBladeMainhand();
                                    } else {
                                       ODMGasHUD.showNoBladeOffhand();
                                    }
                                 }
                              } else if (!hasGas) {
                                 if (!rightHook.active) {
                                    ODMGasHUD.showOutOfGas();
                                 }
                              } else if (isOdmDisabled && !rightHook.active) {
                                 if (!rightDisabledSoundPlayed) {
                                    ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                    rightDisabledSoundPlayed = true;
                                 }

                                 player.sendMessage(Text.literal("ODM is Disabled").styled(style -> style.withColor(Formatting.RED)), true);
                              } else if (isOdmJammed && !rightHook.active) {
                                 if (!rightJamSoundPlayed) {
                                    ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                    rightJamSoundPlayed = true;
                                 }

                                 player.sendMessage(Text.literal("ODM jammed!").styled(style -> style.withColor(Formatting.RED)), true);
                              } else if (!rightHook.active) {
                                 HitResult hit = getPlayerLookTarget(player);
                                 if (hit != null && hit.getType() != Type.MISS) {
                                    if (hit.getType() == Type.ENTITY) {
                                       Entity entity = ((EntityHitResult)hit).getEntity();
                                       if (isKennyHatProtected(entity)) {
                                          if (!rightJamSoundPlayed) {
                                             ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                             rightJamSoundPlayed = true;
                                          }

                                          jamOdmForDuration(player.getUuid(), 5000L);
                                          player.sendMessage(Text.literal("ODM jammed!").styled(style -> style.withColor(Formatting.RED)), true);
                                       } else {
                                          Vec3d hookPos = hit.getPos();
                                          ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                          rightHook.setHookOnEntity(hookPos, rightLegPos, entity);
                                          spawnBloodParticles(player, hookPos);
                                          rightHook.maxRopeLength = rightLegPos.distanceTo(hookPos);
                                          BladeAnimationHandler.triggerFire(visualRightHand);
                                       }
                                    } else {
                                       Vec3d hookPos = hit.getPos();
                                       ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.1F, getODMPitch(player));
                                       rightHook.setHook(hookPos, rightLegPos);
                                       spawnHookImpactParticles(player, hookPos, hit);
                                       rightHook.maxRopeLength = rightLegPos.distanceTo(hookPos);
                                       BladeAnimationHandler.triggerFire(visualRightHand);
                                    }
                                 }
                              }
                           } else {
                              BladeAnimationHandler.release(visualRightHand);
                              rightJamSoundPlayed = false;
                              rightDisabledSoundPlayed = false;
                              if (rightHook.active && !rightHook.isRetracting) {
                                 ODMSoundManager.playLocalOneShot(ModSounds.HOOK_RETRACT, 0.7F, getODMPitch(player));
                                 rightHook.startRetract(rightLegPos);
                              }
                           }

                           boolean leftLatched = leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
                           boolean rightLatched = rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
                           boolean leftWasLatched = leftHookWasLatched.getOrDefault(player.getUuid(), false);
                           boolean rightWasLatched = rightHookWasLatched.getOrDefault(player.getUuid(), false);
                           boolean wearingApg = DannysAot.isAPG(player.getEquippedStack(EquipmentSlot.LEGS).getItem());
                           if (leftLatched) {
                              BladeAnimationHandler.triggerLatch(visualLeftHand);
                              if (!leftWasLatched) {
                                 SoundEvent hookSound;
                                 float hookPitch;
                                 if (wearingApg) {
                                    hookSound = ModSounds.HOOK_SHOOT_APG;
                                    hookPitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
                                 } else {
                                    boolean useFirstSound = lastHookSide.getOrDefault(player.getUuid(), true);
                                    hookSound = useFirstSound ? ModSounds.HOOK_SHOOT_1 : ModSounds.HOOK_SHOOT_2;
                                    hookPitch = getODMPitch(player);
                                    lastHookSide.put(player.getUuid(), !useFirstSound);
                                 }

                                 ODMSoundManager.playLocalOneShot(hookSound, wearingApg ? 0.15F : 0.3F, hookPitch);
                                 ODMSoundManager.playLocalOneShot(ModSounds.HOOK_IMPACT, 0.4F, getODMPitch(player));
                              }
                           }

                           if (rightLatched) {
                              BladeAnimationHandler.triggerLatch(visualRightHand);
                              if (!rightWasLatched) {
                                 SoundEvent hookSound;
                                 float hookPitch;
                                 if (wearingApg) {
                                    hookSound = ModSounds.HOOK_SHOOT_APG;
                                    hookPitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
                                 } else {
                                    boolean useFirstSound = lastHookSide.getOrDefault(player.getUuid(), true);
                                    hookSound = useFirstSound ? ModSounds.HOOK_SHOOT_1 : ModSounds.HOOK_SHOOT_2;
                                    hookPitch = getODMPitch(player);
                                    lastHookSide.put(player.getUuid(), !useFirstSound);
                                 }

                                 ODMSoundManager.playLocalOneShot(hookSound, wearingApg ? 0.15F : 0.3F, hookPitch);
                                 ODMSoundManager.playLocalOneShot(ModSounds.HOOK_IMPACT, 0.4F, getODMPitch(player));
                              }
                           }

                           leftHookWasLatched.put(player.getUuid(), leftLatched);
                           rightHookWasLatched.put(player.getUuid(), rightLatched);
                           boolean isHooked = leftLatched || rightLatched;
                           if (isHooked) {
                              applyHookMovement(player, leftHook, rightHook);
                              lastHookTime.put(player.getUuid(), System.currentTimeMillis());
                           } else {
                              if (wasHooked && !isHooked) {
                                 lastHookTime.put(player.getUuid(), System.currentTimeMillis());
                              }

                              BladeAnimationHandler.setBoosting(false);
                              ODMSoundManager.stopFlightSound(player);
                              ODMSoundManager.stopGasBoostSound(player);
                              currentlyBoosting = false;
                           }

                           if (player.isOnGround()) {
                              boostLevels.put(player.getUuid(), 0.0);
                              storedMomentum.remove(player.getUuid());
                              lastHookTime.remove(player.getUuid());
                           }

                           if (!isHooked && !player.isOnGround()) {
                              Long lastHook = lastHookTime.get(player.getUuid());
                              if (lastHook != null) {
                                 long timeSinceHook = System.currentTimeMillis() - lastHook;
                                 if (timeSinceHook < getMomentumPreserveTime()) {
                                    Vec3d momentum = storedMomentum.get(player.getUuid());
                                    if (momentum != null) {
                                       Vec3d currentVel = player.getVelocity();
                                       double blend = 1.0 - (double)timeSinceHook / getMomentumPreserveTime();
                                       blend = Math.max(0.0, Math.min(1.0, blend));
                                       Vec3d blendedVel = new Vec3d(
                                          currentVel.x + momentum.x * blend * 0.02, currentVel.y, currentVel.z + momentum.z * blend * 0.02
                                       );
                                       player.setVelocity(blendedVel);
                                    }
                                 }
                              }
                           }

                           syncHooksToServer(player, leftHook, rightHook);
                        }
                     }
                  } else {
                     clearHooks(player);
                     lastHookTime.remove(player.getUuid());
                     ODMSoundManager.stopAllSounds(player);
                     syncHooksToServer(player, null, null);
                  }
               }
            }
         );
   }

   private static void syncHooksToServer(ClientPlayerEntity player, HookPoint leftHook, HookPoint rightHook) {
      if (MinecraftClient.getInstance().getNetworkHandler() != null) {
         boolean leftActive = leftHook != null && leftHook.active;
         boolean rightActive = rightHook != null && rightHook.active;
         boolean boostChanged = currentlyBoosting != lastSyncBoosting;
         boolean stateChanged = leftActive != lastSyncLeftActive || rightActive != lastSyncRightActive || boostChanged;
         hookSyncTickCounter++;
         boolean periodicSync = (leftActive || rightActive || currentlyBoosting) && hookSyncTickCounter >= 2;
         if (stateChanged || periodicSync) {
            hookSyncTickCounter = 0;
            lastSyncLeftActive = leftActive;
            lastSyncRightActive = rightActive;
            lastSyncBoosting = currentlyBoosting;
            Vec3d leftPos = leftActive && leftHook.position != null ? leftHook.position : Vec3d.ZERO;
            Vec3d leftStart = leftActive && leftHook.startPosition != null ? leftHook.startPosition : Vec3d.ZERO;
            Vec3d rightPos = rightActive && rightHook.position != null ? rightHook.position : Vec3d.ZERO;
            Vec3d rightStart = rightActive && rightHook.startPosition != null ? rightHook.startPosition : Vec3d.ZERO;
            int leftEntityId = leftActive && leftHook.hookedEntity != null ? leftHook.hookedEntity.getId() : -1;
            int rightEntityId = rightActive && rightHook.hookedEntity != null ? rightHook.hookedEntity.getId() : -1;
            ODMHookUpdatePayload payload = new ODMHookUpdatePayload(
               leftActive,
               leftActive && leftHook.isExtending,
               leftActive && leftHook.isRetracting,
               leftPos.x,
               leftPos.y,
               leftPos.z,
               leftStart.x,
               leftStart.y,
               leftStart.z,
               rightActive,
               rightActive && rightHook.isExtending,
               rightActive && rightHook.isRetracting,
               rightPos.x,
               rightPos.y,
               rightPos.z,
               rightStart.x,
               rightStart.y,
               rightStart.z,
               currentlyBoosting,
               leftEntityId,
               rightEntityId
            );
            ClientPlayNetworking.send(payload);
         }
      }
   }

   private static boolean isWearingODMGear(ClientPlayerEntity player) {
      return DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem());
   }

   private static HitResult getPlayerLookTarget(ClientPlayerEntity player) {
      double reachDistance = getMaxHookDistance();
      Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
      Vec3d eyePosition = camera.isThirdPerson() ? camera.getPos() : player.getEyePos();
      Vec3d lookVector = player.getRotationVector();
      Vec3d cursorRay = CombatModeState.getCursorWorldRay();
      if (cursorRay != null) {
         lookVector = cursorRay;
         eyePosition = camera.getPos();
      } else {
         float cameraTilt = CameraTiltHandler.getCurrentTilt();
         float cameraPitch = CameraTiltHandler.getCurrentPitch();
         if (Math.abs(cameraPitch) > 0.01F) {
            float playerPitch = player.getPitch();
            float playerYaw = player.getYaw();
            float adjustedPitch = playerPitch - cameraPitch;
            float pitchRad = (float)Math.toRadians(adjustedPitch);
            float yawRad = (float)Math.toRadians(-playerYaw);
            float cosPitch = (float)Math.cos(pitchRad);
            lookVector = new Vec3d(Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
         }
      }

      Vec3d reachVector = eyePosition.add(lookVector.multiply(reachDistance));
      EntityHitResult entityHit = projectileUtil_getEntityHitResult(
         player.getWorld(),
         player,
         eyePosition,
         reachVector,
         player.getBoundingBox().stretch(lookVector.multiply(reachDistance)).expand(1.0),
         entity -> !entity.isSpectator()
            && (
               entity.canHit()
                  || entity instanceof SmallTitanEntity
                  || entity instanceof SmallTitan2Entity
                  || entity instanceof TitanEntity
                  || entity instanceof TitanDummyEntity
            )
            && entity != player
      );
      if (entityHit != null) {
         return entityHit;
      } else {
         BlockHitResult blockHit = raycastIgnoringBlocks(player, eyePosition, reachVector);
         return blockHit != null && blockHit.getType() != Type.MISS ? blockHit : null;
      }
   }

   private static BlockHitResult raycastIgnoringBlocks(ClientPlayerEntity player, Vec3d start, Vec3d end) {
      Vec3d currentStart = start;
      int maxIterations = 50;

      for (int i = 0; i < maxIterations; i++) {
         BlockHitResult hit = player.getWorld().raycast(new RaycastContext(currentStart, end, ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (hit.getType() == Type.MISS) {
            return hit;
         }

         Block hitBlock = player.getWorld().getBlockState(hit.getBlockPos()).getBlock();
         if (!IGNORED_BLOCKS.contains(hitBlock)) {
            if (grassOdmAllowed
               || hitBlock != Blocks.GRASS_BLOCK
                  && hitBlock != Blocks.DIRT
                  && hitBlock != Blocks.COARSE_DIRT
                  && hitBlock != Blocks.ROOTED_DIRT
                  && hitBlock != Blocks.DIRT_PATH
                  && hitBlock != Blocks.PODZOL
                  && hitBlock != Blocks.MYCELIUM
                  && hitBlock != Blocks.MUD
                  && hitBlock != Blocks.MUDDY_MANGROVE_ROOTS
                  && hitBlock != Blocks.SAND
                  && hitBlock != Blocks.RED_SAND) {
               return hit;
            }

            return BlockHitResult.createMissed(end, hit.getSide(), hit.getBlockPos());
         }

         Vec3d hitLocation = hit.getPos();
         Vec3d direction = end.subtract(start).normalize();
         currentStart = hitLocation.add(direction.multiply(0.1));
         if (currentStart.squaredDistanceTo(start) > end.squaredDistanceTo(start)) {
            return BlockHitResult.createMissed(end, hit.getSide(), hit.getBlockPos());
         }
      }

      return BlockHitResult.createMissed(end, Direction.UP, BlockPos.ofFloored(end));
   }

   private static EntityHitResult projectileUtil_getEntityHitResult(
      World level, Entity shooter, Vec3d startVec, Vec3d endVec, Box boundingBox, Predicate<Entity> filter
   ) {
      double closestDistance = Double.MAX_VALUE;
      Entity closestEntity = null;
      Vec3d closestHitPos = null;

      for (Entity entity : level.getOtherEntities(shooter, boundingBox, filter)) {
         Box entityBox = entity.getBoundingBox().expand(0.3);
         Optional<Vec3d> optional = entityBox.raycast(startVec, endVec);
         if (optional.isPresent()) {
            Vec3d hitPos = optional.get();
            double distance = startVec.squaredDistanceTo(hitPos);
            if (distance < closestDistance) {
               closestDistance = distance;
               closestEntity = entity;
               closestHitPos = hitPos;
            }
         }
      }

      return closestEntity != null ? new EntityHitResult(closestEntity, closestHitPos) : null;
   }

   private static void applyHookMovement(ClientPlayerEntity player, HookPoint leftHook, HookPoint rightHook) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Vec3d targetPos = null;
      boolean bothHooksActive = false;
      if (leftHook.active && !leftHook.isExtending && !leftHook.isRetracting && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting) {
         targetPos = leftHook.position.add(rightHook.position).multiply(0.5);
         bothHooksActive = true;
      } else if (leftHook.active && !leftHook.isExtending && !leftHook.isRetracting) {
         targetPos = leftHook.position;
      } else if (rightHook.active && !rightHook.isExtending && !rightHook.isRetracting) {
         targetPos = rightHook.position;
      }

      if (targetPos == null) {
         wasDualHooking.put(player.getUuid(), false);
         dualHookStartTime.remove(player.getUuid());
      } else {
         boolean wasDoublehooking = wasDualHooking.getOrDefault(player.getUuid(), false);
         boolean justReleasedOneHook = wasDoublehooking && !bothHooksActive;
         boolean justStartedDualHook = !wasDoublehooking && bothHooksActive;
         if (justStartedDualHook) {
            dualHookStartTime.put(player.getUuid(), System.currentTimeMillis());
         } else if (!bothHooksActive) {
            dualHookStartTime.remove(player.getUuid());
         }

         double dualHookDirectness = 0.0;
         if (bothHooksActive) {
            Long startTime = dualHookStartTime.get(player.getUuid());
            if (startTime != null) {
               long elapsed = System.currentTimeMillis() - startTime;
               dualHookDirectness = Math.min(1.0, (double)elapsed / getDualHookEaseTime());
            }
         }

         wasDualHooking.put(player.getUuid(), bothHooksActive);
         Vec3d playerPos = player.getPos();
         Vec3d toHook = targetPos.subtract(playerPos);
         Vec3d direction = toHook.normalize();
         boolean isBoosting = mc.options.jumpKey.isPressed();
         BladeAnimationHandler.setBoosting(isBoosting);
         currentlyBoosting = isBoosting && boostLevels.getOrDefault(player.getUuid(), 0.0) > 0.1;
         boolean pressingA = mc.options.leftKey.isPressed();
         boolean pressingD = mc.options.rightKey.isPressed();
         boolean isOrbiting = pressingA || pressingD;
         double currentBoostLevel = boostLevels.getOrDefault(player.getUuid(), 0.0);
         if (isBoosting) {
            currentBoostLevel = Math.min(1.0, currentBoostLevel + getBoostRampRate());
         } else {
            currentBoostLevel = Math.max(0.0, currentBoostLevel - getBoostDecayRate());
         }

         boostLevels.put(player.getUuid(), currentBoostLevel);
         boolean isAckerman = BloodlineClientData.isAckerman();
         boolean ogreShifter = player.getCommandTags().contains("ogre_shifter");
         boolean titanBloodline = TitanBloodlineClientData.isActive();
         ItemStack odmGear = player.getEquippedStack(EquipmentSlot.LEGS);
         int tickCount = gasTickCounter.getOrDefault(player.getUuid(), 0) + 1;
         int gasInterval = ogreShifter ? getGasTickInterval() * 6 : (titanBloodline ? getGasTickInterval() * 5 : getGasTickInterval());
         boolean wasPerching = perchingForGas.getOrDefault(player.getUuid(), false);
         double perchExitSpeed = wasPerching ? 0.3 : 0.08;
         boolean perching = !isBoosting && !isOrbiting && !player.isOnGround() && player.getVelocity().length() < perchExitSpeed;
         perchingForGas.put(player.getUuid(), perching);
         if (perching) {
            tickCount = 0;
         } else if (tickCount >= gasInterval) {
            tickCount = 0;
            int baseGas = isBoosting ? getGasConsumptionBoost() : getGasConsumptionNormal();
            int gasToConsume = isAckerman ? (int)Math.ceil(baseGas * 1.15) : baseGas;
            DannysAot.consumeGasFromGear(odmGear, gasToConsume, player);
            ClientPlayNetworking.send(new GasSyncPayload(DannysAot.getGasFromGear(odmGear)));
            odmGear.damage(1, player, p -> p.sendEquipmentBreakStatus(EquipmentSlot.LEGS));
            if (!DannysAot.gearHasGas(odmGear, player)) {
               ODMGasHUD.showOutOfGas();
               forceRetractHooks(player, leftHook, rightHook);
               boostLevels.put(player.getUuid(), 0.0);
               return;
            }
         }

         gasTickCounter.put(player.getUuid(), tickCount);
         double orbitSpeed = getBaseOrbitSpeed();
         if (bothHooksActive && isOrbiting) {
            orbitSpeed *= 1.5;
         } else if (bothHooksActive) {
            orbitSpeed *= getDualHookOrbitMultiplier();
         }

         if (currentBoostLevel > 0.01) {
            orbitSpeed *= 1.0 + (getBoostOrbitMultiplier() - 1.0) * currentBoostLevel;
         }

         double pullSpeed;
         if (isOrbiting && !bothHooksActive) {
            pullSpeed = orbitSpeed * getOrbitPullMultiplier();
         } else {
            pullSpeed = getBasePullSpeed();
            if (bothHooksActive) {
               pullSpeed *= getDualHookPullMultiplier();
               if (currentBoostLevel > 0.01) {
                  pullSpeed *= 1.0 + (getDualHookBoostPullMultiplier() - 1.0) * currentBoostLevel;
               }
            }

            if (!bothHooksActive && currentBoostLevel > 0.01) {
               pullSpeed *= 1.0 + (getBoostPullMultiplier() - 1.0) * currentBoostLevel;
            }
         }

         if (isAckerman && currentBoostLevel > 0.01) {
            double ackermanBonus = (AwakenedPowerClientData.isActive() ? 0.35 : 0.25) * currentBoostLevel;
            pullSpeed *= 1.0 + ackermanBonus;
            orbitSpeed *= 1.0 + ackermanBonus;
         }

         if (DannysAot.isAPG(odmGear.getItem()) && currentBoostLevel > 0.01) {
            pullSpeed *= 1.2;
            orbitSpeed *= 1.2;
         }

         if (titanBloodline) {
            pullSpeed *= 2.0;
            orbitSpeed *= 2.0;
         }

         double lift = getUpwardLift();
         if (currentBoostLevel > 0.01) {
            lift *= 1.0 + currentBoostLevel;
         }

         Vec3d currentVel = player.getVelocity();
         Vec3d pullForce = direction.multiply(pullSpeed);
         if (playerPos.y < targetPos.y) {
            pullForce = pullForce.add(0.0, lift, 0.0);
         }

         Vec3d orbitForce = Vec3d.ZERO;
         if (isOrbiting) {
            Vec3d right = direction.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
            if (right.lengthSquared() < 0.01) {
               right = direction.crossProduct(new Vec3d(1.0, 0.0, 0.0)).normalize();
            }

            if (pressingD) {
               orbitForce = right.multiply(orbitSpeed);
            }

            if (pressingA) {
               orbitForce = right.multiply(-orbitSpeed);
            }
         }

         double drag = 0.98;
         Vec3d newVelocity;
         if (justReleasedOneHook) {
            Vec3d preserved = storedMomentum.getOrDefault(player.getUuid(), currentVel);
            newVelocity = preserved.multiply(drag).add(pullForce.multiply(0.3)).add(orbitForce);
         } else if (justStartedDualHook) {
            newVelocity = currentVel.multiply(drag).add(orbitForce);
         } else if (bothHooksActive && isOrbiting) {
            newVelocity = currentVel.multiply(drag).add(pullForce).add(orbitForce);
         } else if (bothHooksActive) {
            Vec3d normalVelocity = currentVel.multiply(drag).add(pullForce);
            double towardsSpeed = currentVel.dotProduct(direction);
            Vec3d towardsVelocity = direction.multiply(Math.max(0.0, towardsSpeed));
            Vec3d directVelocity = towardsVelocity.multiply(drag).add(pullForce);
            newVelocity = normalVelocity.lerp(directVelocity, dualHookDirectness);
         } else {
            newVelocity = currentVel.multiply(drag).add(pullForce).add(orbitForce);
         }

         double speed = newVelocity.length();
         if (!player.isOnGround() && !isBoosting && !isOrbiting && speed < 0.3) {
            double t = speed / 0.3;
            double yDamp = 0.3 + 0.7 * t;
            newVelocity = new Vec3d(newVelocity.x, newVelocity.y * yDamp, newVelocity.z);
         }

         storedMomentum.put(player.getUuid(), newVelocity);
         player.setVelocity(newVelocity);
         float odmPitch = getODMPitch(player);
         if (!player.isOnGround()) {
            ODMSoundManager.playFlightSound(player, odmPitch);
         } else {
            ODMSoundManager.stopFlightSound(player);
         }

         if (isBoosting && currentBoostLevel > 0.1) {
            ODMSoundManager.playGasBoostSound(player, odmPitch);
            spawnBoostParticles(player);
         } else {
            ODMSoundManager.stopGasBoostSound(player);
         }
      }
   }

   private static void spawnBoostParticles(ClientPlayerEntity player) {
      Vec3d velocity = player.getVelocity();
      Vec3d playerPos = player.getPos();
      double baseY = DannysAot.isAPG(player.getEquippedStack(EquipmentSlot.LEGS).getItem()) ? 1.0 : 0.5;
      Vec3d particleDirection = velocity.normalize().multiply(-1.0);

      for (int i = 0; i < 2; i++) {
         double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.3;
         double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.3;
         double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.3;
         double particleX = playerPos.x + particleDirection.x * 0.5 + offsetX;
         double particleY = playerPos.y + baseY + offsetY;
         double particleZ = playerPos.z + particleDirection.z * 0.5 + offsetZ;
         double velX = (player.getRandom().nextDouble() - 0.5) * 0.05;
         double velY = (player.getRandom().nextDouble() - 0.5) * 0.05;
         double velZ = (player.getRandom().nextDouble() - 0.5) * 0.05;
         player.getWorld().addParticle(daot.compat.BackportEffects.WHITE_SMOKE, particleX, particleY, particleZ, velX, velY, velZ);
      }

      for (int i = 0; i < 2; i++) {
         double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.4;
         double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.4;
         double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.4;
         double particleX = playerPos.x + particleDirection.x * 0.5 + offsetX;
         double particleY = playerPos.y + baseY + offsetY;
         double particleZ = playerPos.z + particleDirection.z * 0.5 + offsetZ;
         double velX = (player.getRandom().nextDouble() - 0.5) * 0.03;
         double velY = (player.getRandom().nextDouble() - 0.5) * 0.03;
         double velZ = (player.getRandom().nextDouble() - 0.5) * 0.03;
         player.getWorld().addParticle(ParticleTypes.CLOUD, particleX, particleY, particleZ, velX, velY, velZ);
      }

      for (int i = 0; i < 3; i++) {
         double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.5;
         double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.5;
         double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.5;
         double particleX = playerPos.x + particleDirection.x * 0.5 + offsetX;
         double particleY = playerPos.y + baseY + offsetY;
         double particleZ = playerPos.z + particleDirection.z * 0.5 + offsetZ;
         double velX = (player.getRandom().nextDouble() - 0.5) * 0.02;
         double velY = -0.01;
         double velZ = (player.getRandom().nextDouble() - 0.5) * 0.02;
         player.getWorld().addParticle(ParticleTypes.WHITE_ASH, particleX, particleY, particleZ, velX, velY, velZ);
      }
   }

   private static void spawnHookImpactParticles(ClientPlayerEntity player, Vec3d hookPos, HitResult hit) {
      BlockPos blockPos;
      if (hit instanceof BlockHitResult blockHit) {
         blockPos = blockHit.getBlockPos();
      } else {
         blockPos = BlockPos.ofFloored(hookPos);
      }

      BlockState blockState = player.getWorld().getBlockState(blockPos);
      if (!blockState.isAir()) {
         for (int i = 0; i < 10; i++) {
            double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.4;
            double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.4;
            double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.4;
            double velX = (player.getRandom().nextDouble() - 0.5) * 0.3;
            double velY = player.getRandom().nextDouble() * 0.3;
            double velZ = (player.getRandom().nextDouble() - 0.5) * 0.3;
            player.getWorld()
               .addParticle(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                  hookPos.x + offsetX,
                  hookPos.y + offsetY,
                  hookPos.z + offsetZ,
                  velX,
                  velY,
                  velZ
               );
         }
      }
   }

   private static void spawnBloodParticles(ClientPlayerEntity player, Vec3d hookPos) {
      for (int i = 0; i < 10; i++) {
         double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.4;
         double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.4;
         double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.4;
         double velX = (player.getRandom().nextDouble() - 0.5) * 0.3;
         double velY = player.getRandom().nextDouble() * 0.3;
         double velZ = (player.getRandom().nextDouble() - 0.5) * 0.3;
         player.getWorld()
            .addParticle(
               new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
               hookPos.x + offsetX,
               hookPos.y + offsetY,
               hookPos.z + offsetZ,
               velX,
               velY,
               velZ
            );
      }
   }

   private static void clearHooks(ClientPlayerEntity player) {
      leftHooks.remove(player.getUuid());
      rightHooks.remove(player.getUuid());
      boostLevels.remove(player.getUuid());
      storedMomentum.remove(player.getUuid());
      gasTickCounter.remove(player.getUuid());
      perchingForGas.remove(player.getUuid());
      wasDualHooking.remove(player.getUuid());
      dualHookStartTime.remove(player.getUuid());
   }

   private static void forceRetractHooks(ClientPlayerEntity player, HookPoint leftHook, HookPoint rightHook) {
      float yaw = (float)Math.toRadians(player.getYaw());
      double legOffset = 0.3;
      Vec3d playerPos = player.getPos();
      Vec3d leftLegPos = playerPos.add(Math.cos(yaw) * legOffset, 0.1, Math.sin(yaw) * legOffset);
      Vec3d rightLegPos = playerPos.add(-Math.cos(yaw) * legOffset, 0.1, -Math.sin(yaw) * legOffset);
      if (leftHook.active && !leftHook.isRetracting) {
         ODMSoundManager.playLocalOneShot(ModSounds.HOOK_RETRACT, 0.7F, getODMPitch(player));
         leftHook.startRetract(leftLegPos);
      }

      if (rightHook.active && !rightHook.isRetracting) {
         ODMSoundManager.playLocalOneShot(ModSounds.HOOK_RETRACT, 0.7F, getODMPitch(player));
         rightHook.startRetract(rightLegPos);
      }

      ODMSoundManager.stopFlightSound(player);
      ODMSoundManager.stopGasBoostSound(player);
   }

   public static HookPoint getLeftHook(UUID playerId) {
      return leftHooks.get(playerId);
   }

   public static HookPoint getRightHook(UUID playerId) {
      return rightHooks.get(playerId);
   }

   public static boolean isLeftHookActive(UUID playerId) {
      HookPoint hook = leftHooks.get(playerId);
      return hook != null && hook.active;
   }

   public static boolean isRightHookActive(UUID playerId) {
      HookPoint hook = rightHooks.get(playerId);
      return hook != null && hook.active;
   }

   private static boolean checkAttackTitanCrouchAction(HookPoint leftHook, HookPoint rightHook, ClientPlayerEntity player) {
      if (AwakenedPowerClientData.isActive()) {
         return false;
      } else {
         Set<AttackTitanEntity> hookedTitans = new HashSet<>();
         AttackTitanEntity leftTitan = getAttackTitanFromHook(leftHook);
         if (leftTitan != null) {
            hookedTitans.add(leftTitan);
         }

         AttackTitanEntity rightTitan = getAttackTitanFromHook(rightHook);
         if (rightTitan != null) {
            hookedTitans.add(rightTitan);
         }

         boolean anyWireBroke = false;
         UUID playerUUID = player.getUuid();

         for (AttackTitanEntity titan : hookedTitans) {
            UUID titanUUID = titan.getUuid();
            boolean wasCrouching = titanWasCrouchingLastTick.getOrDefault(titanUUID, false);
            boolean isCrouching = titan.shouldJamHookedOdm();
            if (!isCrouching) {
               hooksLatchedBeforeCrouch.computeIfAbsent(titanUUID, k -> new HashSet<>()).add(playerUUID);
            }

            if (!wasCrouching && isCrouching && canTitanBreakWire(titanUUID)) {
               Set<UUID> playersLatchedBefore = hooksLatchedBeforeCrouch.get(titanUUID);
               if (playersLatchedBefore != null && playersLatchedBefore.contains(playerUUID)) {
                  long now = System.currentTimeMillis();
                  titanWireBreakCooldowns.put(titanUUID, now + 7000L);
                  if (leftTitan != null && leftTitan.getUuid().equals(titanUUID) && leftHook.active) {
                     leftHook.release();
                     BladeAnimationHandler.release(Hand.OFF_HAND);
                  }

                  if (rightTitan != null && rightTitan.getUuid().equals(titanUUID) && rightHook.active) {
                     rightHook.release();
                     BladeAnimationHandler.release(Hand.MAIN_HAND);
                  }

                  jamOdm(playerUUID);
                  anyWireBroke = true;
                  playersLatchedBefore.remove(playerUUID);
               }
            }

            titanWasCrouchingLastTick.put(titanUUID, isCrouching);
            if (wasCrouching && !isCrouching) {
               hooksLatchedBeforeCrouch.remove(titanUUID);
            }
         }

         titanWasCrouchingLastTick.keySet().removeIf(uuid -> {
            for (AttackTitanEntity titanx : hookedTitans) {
               if (titanx.getUuid().equals(uuid)) {
                  return false;
               }
            }

            return true;
         });
         return anyWireBroke;
      }
   }

   private static AttackTitanEntity getAttackTitanFromHook(HookPoint hook) {
      if (hook != null && hook.active && hook.hookedEntity != null) {
         if (hook.hookedEntity instanceof AttackTitanEntity titan) {
            return titan;
         } else if (hook.hookedEntity instanceof AttackTitanEyeEntity eye) {
            return eye.getParentTitan();
         } else {
            return hook.hookedEntity instanceof AttackTitanNapeEntity nape ? nape.getParentTitan() : null;
         }
      } else {
         return null;
      }
   }

   private static ArmoredTitanEntity getArmoredTitanFromHook(HookPoint hook) {
      if (hook != null && hook.active && hook.hookedEntity != null) {
         if (hook.hookedEntity instanceof ArmoredTitanEntity titan) {
            return titan;
         } else if (hook.hookedEntity instanceof ArmoredTitanEyeEntity eye) {
            return eye.getParentTitan();
         } else {
            return hook.hookedEntity instanceof ArmoredTitanNapeEntity nape ? nape.getParentTitan() : null;
         }
      } else {
         return null;
      }
   }

   private static boolean checkArmoredTitanCrouchAction(HookPoint leftHook, HookPoint rightHook, ClientPlayerEntity player) {
      if (AwakenedPowerClientData.isActive()) {
         return false;
      } else {
         Set<ArmoredTitanEntity> hookedTitans = new HashSet<>();
         ArmoredTitanEntity leftTitan = getArmoredTitanFromHook(leftHook);
         if (leftTitan != null) {
            hookedTitans.add(leftTitan);
         }

         ArmoredTitanEntity rightTitan = getArmoredTitanFromHook(rightHook);
         if (rightTitan != null) {
            hookedTitans.add(rightTitan);
         }

         boolean anyWireBroke = false;
         UUID playerUUID = player.getUuid();

         for (ArmoredTitanEntity titan : hookedTitans) {
            UUID titanUUID = titan.getUuid();
            boolean wasCrouching = titanWasCrouchingLastTick.getOrDefault(titanUUID, false);
            boolean isCrouching = titan.isInSneakingPose();
            if (!isCrouching) {
               hooksLatchedBeforeCrouch.computeIfAbsent(titanUUID, k -> new HashSet<>()).add(playerUUID);
            }

            if (!wasCrouching && isCrouching && canTitanBreakWire(titanUUID)) {
               Set<UUID> playersLatchedBefore = hooksLatchedBeforeCrouch.get(titanUUID);
               if (playersLatchedBefore != null && playersLatchedBefore.contains(playerUUID)) {
                  long now = System.currentTimeMillis();
                  titanWireBreakCooldowns.put(titanUUID, now + 7000L);
                  if (leftTitan != null && leftTitan.getUuid().equals(titanUUID) && leftHook.active) {
                     leftHook.release();
                     BladeAnimationHandler.release(Hand.OFF_HAND);
                  }

                  if (rightTitan != null && rightTitan.getUuid().equals(titanUUID) && rightHook.active) {
                     rightHook.release();
                     BladeAnimationHandler.release(Hand.MAIN_HAND);
                  }

                  jamOdm(playerUUID);
                  anyWireBroke = true;
                  playersLatchedBefore.remove(playerUUID);
               }
            }

            titanWasCrouchingLastTick.put(titanUUID, isCrouching);
            if (wasCrouching && !isCrouching) {
               hooksLatchedBeforeCrouch.remove(titanUUID);
            }
         }

         titanWasCrouchingLastTick.keySet().removeIf(uuid -> {
            for (ArmoredTitanEntity titanx : hookedTitans) {
               if (titanx.getUuid().equals(uuid)) {
                  return false;
               }
            }

            return true;
         });
         return anyWireBroke;
      }
   }

   private static FemaleTitanEntity getFemaleTitanFromHook(HookPoint hook) {
      if (hook != null && hook.active && hook.hookedEntity != null) {
         if (hook.hookedEntity instanceof FemaleTitanEntity titan) {
            return titan;
         } else if (hook.hookedEntity instanceof FemaleTitanEyeEntity eye) {
            return eye.getParentTitan();
         } else {
            return hook.hookedEntity instanceof FemaleTitanNapeEntity nape ? nape.getParentTitan() : null;
         }
      } else {
         return null;
      }
   }

   private static boolean checkFemaleTitanCrouchAction(HookPoint leftHook, HookPoint rightHook, ClientPlayerEntity player) {
      if (AwakenedPowerClientData.isActive()) {
         return false;
      } else {
         Set<FemaleTitanEntity> hookedTitans = new HashSet<>();
         FemaleTitanEntity leftTitan = getFemaleTitanFromHook(leftHook);
         if (leftTitan != null) {
            hookedTitans.add(leftTitan);
         }

         FemaleTitanEntity rightTitan = getFemaleTitanFromHook(rightHook);
         if (rightTitan != null) {
            hookedTitans.add(rightTitan);
         }

         boolean anyWireBroke = false;
         UUID playerUUID = player.getUuid();

         for (FemaleTitanEntity titan : hookedTitans) {
            UUID titanUUID = titan.getUuid();
            boolean wasCrouching = titanWasCrouchingLastTick.getOrDefault(titanUUID, false);
            boolean isCrouching = titan.isInSneakingPose();
            if (!isCrouching) {
               hooksLatchedBeforeCrouch.computeIfAbsent(titanUUID, k -> new HashSet<>()).add(playerUUID);
            }

            if (!wasCrouching && isCrouching && canTitanBreakWire(titanUUID)) {
               Set<UUID> playersLatchedBefore = hooksLatchedBeforeCrouch.get(titanUUID);
               if (playersLatchedBefore != null && playersLatchedBefore.contains(playerUUID)) {
                  long now = System.currentTimeMillis();
                  titanWireBreakCooldowns.put(titanUUID, now + 7000L);
                  if (leftTitan != null && leftTitan.getUuid().equals(titanUUID) && leftHook.active) {
                     leftHook.release();
                     BladeAnimationHandler.release(Hand.OFF_HAND);
                  }

                  if (rightTitan != null && rightTitan.getUuid().equals(titanUUID) && rightHook.active) {
                     rightHook.release();
                     BladeAnimationHandler.release(Hand.MAIN_HAND);
                  }

                  jamOdm(playerUUID);
                  anyWireBroke = true;
                  playersLatchedBefore.remove(playerUUID);
               }
            }

            titanWasCrouchingLastTick.put(titanUUID, isCrouching);
            if (wasCrouching && !isCrouching) {
               hooksLatchedBeforeCrouch.remove(titanUUID);
            }
         }

         titanWasCrouchingLastTick.keySet().removeIf(uuid -> {
            for (FemaleTitanEntity titanx : hookedTitans) {
               if (titanx.getUuid().equals(uuid)) {
                  return false;
               }
            }

            return true;
         });
         return anyWireBroke;
      }
   }

   private static boolean canTitanBreakWire(UUID titanUUID) {
      Long cooldownEnd = titanWireBreakCooldowns.get(titanUUID);
      if (cooldownEnd == null) {
         return true;
      } else if (System.currentTimeMillis() >= cooldownEnd) {
         titanWireBreakCooldowns.remove(titanUUID);
         return true;
      } else {
         return false;
      }
   }

   private static void tryWeatherJam(PlayerEntity player, HookPoint leftHook, HookPoint rightHook) {
      if (weatherAffectsOdm) {
         if (leftHook.active || rightHook.active) {
            if (player.getWorld().hasRain(player.getBlockPos())) {
               if (!isOdmJammed(player.getUuid())) {
                  weatherJamRollTick++;
                  if (weatherJamRollTick >= 20) {
                     weatherJamRollTick = 0;
                     Random rng = player.getWorld().random;
                     if (!(rng.nextDouble() >= 0.05)) {
                        long duration = 2000L + (long)(rng.nextDouble() * 3000.0);
                        jamOdmForDuration(player.getUuid(), duration);
                        if (leftHook.active) {
                           leftHook.release();
                           BladeAnimationHandler.release(Hand.OFF_HAND);
                        }

                        if (rightHook.active) {
                           rightHook.release();
                           BladeAnimationHandler.release(Hand.MAIN_HAND);
                        }

                        player.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
                        ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.6F, getODMPitch(player));
                        player.sendMessage(Text.literal("ODM jammed by weather!").styled(style -> style.withColor(Formatting.RED)), true);
                     }
                  }
               }
            }
         }
      }
   }

   private static void tryKineticImpact(ClientPlayerEntity player, HookPoint leftHook, HookPoint rightHook) {
      double currentSpeed = player.getVelocity().horizontalLength();

      try {
         if (!kineticOdmDamage) {
            return;
         }

         if (leftHook.active || rightHook.active) {
            if (player.isCreative() || player.isSpectator()) {
               return;
            }

            if (!player.horizontalCollision) {
               return;
            }

            if (prevTickHorizontalSpeed <= 1.2) {
               return;
            }

            float reported = (float)Math.min(prevTickHorizontalSpeed, 5.0);
            ClientPlayNetworking.send(new KineticOdmImpactPayload(reported));
            return;
         }
      } finally {
         prevTickHorizontalSpeed = currentSpeed;
      }
   }

   public static void jamOdm(UUID playerUUID) {
      odmJamEndTime.put(playerUUID, System.currentTimeMillis() + 1500L);
   }

   public static void jamOdmForDuration(UUID playerUUID, long durationMs) {
      if (!isJamImmune(playerUUID)) {
         odmJamEndTime.put(playerUUID, System.currentTimeMillis() + durationMs);
      }
   }

   public static boolean isJamImmune(UUID playerUUID) {
      return isLocalPlayer(playerUUID) && TitanBloodlineClientData.isActive() ? true : isOgreShifterLocal(playerUUID);
   }

   private static boolean isLocalPlayer(UUID playerUUID) {
      ClientPlayerEntity local = MinecraftClient.getInstance().player;
      return local != null && local.getUuid().equals(playerUUID);
   }

   private static boolean isOgreShifterLocal(UUID playerUUID) {
      ClientPlayerEntity local = MinecraftClient.getInstance().player;
      return local != null && local.getUuid().equals(playerUUID) && local.getCommandTags().contains("ogre_shifter");
   }

   private static boolean isOdmJammed(UUID playerUUID) {
      Long jamEnd = odmJamEndTime.get(playerUUID);
      if (jamEnd == null) {
         return false;
      } else if (System.currentTimeMillis() >= jamEnd) {
         odmJamEndTime.remove(playerUUID);
         return false;
      } else {
         return true;
      }
   }

   private static boolean isKennyHatProtected(Entity entity) {
      BeastTitanEntity beast = resolveBeastTitan(entity);
      return beast != null && beast.isHookProtected();
   }

   private static BeastTitanEntity resolveBeastTitan(Entity entity) {
      if (entity instanceof BeastTitanEntity bt) {
         return bt;
      } else if (entity instanceof BeastTitanNapeEntity nape) {
         return nape.getParentTitan();
      } else if (entity instanceof BeastTitanEyeEntity eye) {
         return eye.getParentTitan();
      } else {
         return entity instanceof BeastTitanGrabEntity grab ? grab.getParentTitan() : null;
      }
   }

   private static void updateHookEntityPosition(HookPoint hook) {
      if (hook.hookedEntity != null && !hook.hookedEntity.isRemoved()) {
         if (hook.hookedBoneName != null && hook.hookedEntity instanceof TitanEntity) {
            Vec3d bonePos = TitanBoneCache.getBonePosition(hook.hookedEntity.getId(), hook.hookedBoneName);
            if (bonePos != null) {
               hook.position = bonePos;
            } else {
               hook.position = hook.hookedEntity.getPos().add(0.0, hook.hookedEntity.getHeight() * 0.5, 0.0);
            }
         } else {
            hook.updateEntityPosition();
         }
      } else {
         if (hook.hookedEntity != null) {
            hook.release();
         }
      }
   }
}
