package daot;

import daot.network.BladeAOEPayload;
import daot.network.BladeBlockPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;

@Environment(EnvType.CLIENT)
public class BladeAnimationHandler {
   private static BladeAnimationHandler.AnimState leftState = BladeAnimationHandler.AnimState.IDLE;
   private static BladeAnimationHandler.AnimState rightState = BladeAnimationHandler.AnimState.IDLE;
   private static float leftHandAnimation = 0.0F;
   private static float rightHandAnimation = 0.0F;
   private static float previousLeftHandAnimation = 0.0F;
   private static float previousRightHandAnimation = 0.0F;
   private static final float FIRE_RISE_SPEED = 0.25F;
   private static final float FIRE_DECAY_SPEED = 0.15F;
   private static final float LATCH_RISE_SPEED = 0.18F;
   private static final float RELEASE_DECAY_SPEED = 0.25F;
   public static final float FIRE_PITCH_TILT = 12.0F;
   public static final float LATCH_PITCH_TILT = 22.5F;
   public static final float LATCH_ROLL_TILT = 15.0F;
   public static final float LATCH_YAW_TILT = 15.0F;
   public static final float BOOST_EXTRA_ROLL = 15.0F;
   private static final float BOOST_TRANSITION_SPEED = 0.15F;
   private static boolean isBoosting = false;
   private static float boostAnimation = 0.0F;
   private static float previousBoostAnimation = 0.0F;
   private static float leftThunderAnim = 0.0F;
   private static float rightThunderAnim = 0.0F;
   private static float prevLeftThunderAnim = 0.0F;
   private static float prevRightThunderAnim = 0.0F;
   private static float leftThunderTarget = 0.0F;
   private static float rightThunderTarget = 0.0F;
   private static final float THUNDER_RISE_SPEED = 0.35F;
   private static final float THUNDER_FALL_SPEED = 0.15F;
   public static final float THUNDER_FIRE_PITCH = 20.0F;
   public static final float THUNDER_FIRE_ROLL = 8.0F;
   private static BladeAnimationHandler.SwingPhase leftSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
   private static BladeAnimationHandler.SwingPhase rightSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
   private static float leftSwingAnim = 0.0F;
   private static float rightSwingAnim = 0.0F;
   private static float prevLeftSwingAnim = 0.0F;
   private static float prevRightSwingAnim = 0.0F;
   private static final float SLASH_SPEED = 0.14F;
   public static final float SWING_WINDUP_PITCH = -22.0F;
   public static final float SWING_WINDUP_ROLL = 0.0F;
   public static final float SWING_WINDUP_YAW = 0.0F;
   public static final float SWING_END_PITCH = 28.0F;
   public static final float SWING_END_ROLL = 25.0F;
   public static final float SWING_END_YAW = -8.0F;
   private static boolean isBlockingState = false;
   private static long blockCooldownEnd = 0L;
   public static final float BLOCK_ROLL_TILT = 60.0F;
   public static final float BLOCK_PITCH_TILT = -10.0F;
   public static final float BLOCK_YAW_TILT = 15.0F;
   private static final float BLOCK_TRANSITION_SPEED = 0.18F;
   private static float blockAnimation = 0.0F;
   private static float previousBlockAnimation = 0.0F;
   private static boolean isAimingMain = false;
   private static boolean isAimingOff = false;
   private static float aimAnimationMain = 0.0F;
   private static float previousAimAnimationMain = 0.0F;
   private static float aimAnimationOff = 0.0F;
   private static float previousAimAnimationOff = 0.0F;
   private static final float AIM_TRANSITION_SPEED = 0.2F;
   public static final float AIM_PITCH_TILT = 14.0F;
   public static final float AIM_YAW_TILT = 12.0F;
   public static final float AIM_ROLL_TILT = -4.0F;
   private static float apgRecoilMain = 0.0F;
   private static float apgRecoilOff = 0.0F;
   private static float prevApgRecoilMain = 0.0F;
   private static float prevApgRecoilOff = 0.0F;
   public static final float APG_RECOIL_KICK = 55.0F;
   private static final float APG_RECOIL_DECAY = 0.18F;
   private static boolean chargedODMAttacksEnabled = false;
   private static boolean isCharging = false;
   private static long chargeStartTick = 0L;
   private static int chargeAnimAction = 0;
   private static int chargeAttackType = 0;
   private static final int CHARGE_PAUSE_DELAY_TICKS = 3;
   private static boolean wasAttackKeyDown = false;

   public static void setChargedODMAttacksEnabled(boolean enabled) {
      chargedODMAttacksEnabled = enabled;
      if (!enabled && isCharging) {
         isCharging = false;
         ODMAnimationHandler.resumeFromCharge();
         leftSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
         rightSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
      }
   }

   public static void register() {
      ClientTickEvents.START_CLIENT_TICK.register(BladeAnimationHandler::tickAttack);
      ClientTickEvents.END_CLIENT_TICK.register(BladeAnimationHandler::tick);
   }

   public static void triggerFire(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightState = BladeAnimationHandler.AnimState.FIRING;
         rightHandAnimation = 0.0F;
      } else {
         leftState = BladeAnimationHandler.AnimState.FIRING;
         leftHandAnimation = 0.0F;
      }
   }

   public static void triggerLatch(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         if (rightState != BladeAnimationHandler.AnimState.LATCHED) {
            rightState = BladeAnimationHandler.AnimState.LATCHED;
            rightHandAnimation = 0.0F;
         }
      } else if (leftState != BladeAnimationHandler.AnimState.LATCHED) {
         leftState = BladeAnimationHandler.AnimState.LATCHED;
         leftHandAnimation = 0.0F;
      }
   }

   public static void release(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightState = BladeAnimationHandler.AnimState.IDLE;
      } else {
         leftState = BladeAnimationHandler.AnimState.IDLE;
      }
   }

   public static void setBoosting(boolean boosting) {
      isBoosting = boosting;
   }

   public static void triggerThunderSpearFire(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightThunderTarget = 1.0F;
         rightThunderAnim = 0.0F;
         prevRightThunderAnim = 0.0F;
      } else {
         leftThunderTarget = 1.0F;
         leftThunderAnim = 0.0F;
         prevLeftThunderAnim = 0.0F;
      }
   }

   public static void triggerThunderSpearSnap(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightThunderTarget = -1.0F;
         rightThunderAnim = 0.0F;
         prevRightThunderAnim = 0.0F;
      } else {
         leftThunderTarget = -1.0F;
         leftThunderAnim = 0.0F;
         prevLeftThunderAnim = 0.0F;
      }
   }

   public static float getThunderAnimation(Hand hand, float partialTick) {
      return hand == Hand.MAIN_HAND
         ? prevRightThunderAnim + (rightThunderAnim - prevRightThunderAnim) * partialTick
         : prevLeftThunderAnim + (leftThunderAnim - prevLeftThunderAnim) * partialTick;
   }

   public static void triggerSwing(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightSwingPhase = BladeAnimationHandler.SwingPhase.SLASHING;
         rightSwingAnim = 0.0F;
         prevRightSwingAnim = 0.0F;
      } else {
         leftSwingPhase = BladeAnimationHandler.SwingPhase.SLASHING;
         leftSwingAnim = 0.0F;
         prevLeftSwingAnim = 0.0F;
      }
   }

   public static void triggerCharge(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightSwingPhase = BladeAnimationHandler.SwingPhase.CHARGING;
         rightSwingAnim = 0.0F;
         prevRightSwingAnim = 0.0F;
      } else {
         leftSwingPhase = BladeAnimationHandler.SwingPhase.CHARGING;
         leftSwingAnim = 0.0F;
         prevLeftSwingAnim = 0.0F;
      }
   }

   public static void releaseCharge(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         rightSwingPhase = BladeAnimationHandler.SwingPhase.SLASHING;
      } else {
         leftSwingPhase = BladeAnimationHandler.SwingPhase.SLASHING;
      }
   }

   public static void startAiming(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         isAimingMain = true;
      } else {
         isAimingOff = true;
      }
   }

   public static void stopAiming(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         isAimingMain = false;
      } else {
         isAimingOff = false;
      }
   }

   public static void stopAllAiming() {
      isAimingMain = false;
      isAimingOff = false;
   }

   public static boolean isAiming(Hand hand) {
      return hand == Hand.MAIN_HAND ? isAimingMain : isAimingOff;
   }

   public static float getAimAnimation(Hand hand, float partialTick) {
      return hand == Hand.MAIN_HAND
         ? previousAimAnimationMain + (aimAnimationMain - previousAimAnimationMain) * partialTick
         : previousAimAnimationOff + (aimAnimationOff - previousAimAnimationOff) * partialTick;
   }

   public static void triggerApgFireRecoil(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         apgRecoilMain = 1.0F;
      } else {
         apgRecoilOff = 1.0F;
      }
   }

   public static float getApgFireRecoil(Hand hand, float partialTick) {
      return hand == Hand.MAIN_HAND
         ? prevApgRecoilMain + (apgRecoilMain - prevApgRecoilMain) * partialTick
         : prevApgRecoilOff + (apgRecoilOff - prevApgRecoilOff) * partialTick;
   }

   public static boolean isBlocking() {
      return isBlockingState;
   }

   private static void startBlocking(MinecraftClient mc, ClientPlayerEntity player) {
      isBlockingState = true;
      ClientPlayNetworking.send(new BladeBlockPayload(true));
      ODMAnimationHandler.triggerBlock();
      player.getWorld()
         .playSound(
            player,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.ITEM_ARMOR_EQUIP_IRON,
            SoundCategory.PLAYERS,
            1.0F,
            0.8F + player.getRandom().nextFloat() * 0.1F
         );
   }

   private static void stopBlocking(MinecraftClient mc, ClientPlayerEntity player) {
      if (isBlockingState) {
         isBlockingState = false;
         ClientPlayNetworking.send(new BladeBlockPayload(false));
         ODMAnimationHandler.stopBlock();
      }
   }

   public static void onBlockBroken() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         isBlockingState = false;
         ODMAnimationHandler.stopBlock();
         blockCooldownEnd = mc.player.getWorld().getTime() + 60L;
         ClientPlayerEntity player = mc.player;

         for (int i = 0; i < 12; i++) {
            double ox = (player.getRandom().nextDouble() - 0.5) * 0.8;
            double oy = player.getRandom().nextDouble() * 1.2 + 0.2;
            double oz = (player.getRandom().nextDouble() - 0.5) * 0.8;
            mc.world.addParticle(ParticleTypes.CRIT, player.getX() + ox, player.getY() + oy, player.getZ() + oz, ox * 0.5, 0.1, oz * 0.5);
         }
      }
   }

   private static void tickAttack(MinecraftClient mc) {
      if (mc.player != null && mc.currentScreen == null) {
         ClientPlayerEntity player = mc.player;
         ItemStack mainHand = player.getMainHandStack();
         ItemStack offHand = player.getOffHandStack();
         boolean mainBlade = mainHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY;
         boolean offBlade = offHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY;
         if (!mainBlade && !offBlade) {
            if (isBlockingState) {
               stopBlocking(mc, player);
            }
         } else if (player.isSneaking()) {
            if (isBlockingState) {
               stopBlocking(mc, player);
            }
         } else {
            boolean useKeyDown = CombatModeState.isEnabled() ? CombatModeState.isBlockKeyDown() : mc.options.useKey.isPressed();
            boolean blockOnCooldown = mc.player.getWorld().getTime() < blockCooldownEnd;
            boolean attackAnimPlaying = ODMAnimationHandler.isLocalPlayerAttacking();
            boolean lookingAtInteractable = false;
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == Type.BLOCK) {
               BlockHitResult blockHit = (BlockHitResult)mc.crosshairTarget;
               BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
               lookingAtInteractable = state.hasBlockEntity()
                  || state.getBlock() instanceof DoorBlock
                  || state.getBlock() instanceof TrapdoorBlock
                  || state.getBlock() instanceof FenceGateBlock
                  || state.getBlock() instanceof ButtonBlock
                  || state.getBlock() instanceof LeverBlock
                  || state.getBlock() instanceof BedBlock;
            }

            if (useKeyDown) {
               if (isBoosting) {
                  if (isBlockingState) {
                     stopBlocking(mc, player);
                  }
               } else if (lookingAtInteractable) {
                  if (isBlockingState) {
                     stopBlocking(mc, player);
                  }
               } else if (blockOnCooldown) {
                  if (isBlockingState) {
                     stopBlocking(mc, player);
                  }

                  while (mc.options.useKey.wasPressed()) {
                  }
               } else if (attackAnimPlaying) {
                  if (isBlockingState) {
                     stopBlocking(mc, player);
                  }

                  while (mc.options.useKey.wasPressed()) {
                  }
               } else {
                  if (!isBlockingState && mainBlade) {
                     startBlocking(mc, player);
                  }

                  if (isBlockingState) {
                     while (mc.options.useKey.wasPressed()) {
                     }
                  }
               }
            } else if (isBlockingState) {
               stopBlocking(mc, player);
            }

            if (isBlockingState) {
               while (mc.options.attackKey.wasPressed()) {
               }

               if (isCharging) {
                  isCharging = false;
                  ODMAnimationHandler.resumeFromCharge();
                  leftSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
                  rightSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
               }
            } else if (!isCharging && ODMAnimationHandler.isLocalPlayerAttacking()) {
               while (mc.options.attackKey.wasPressed()) {
               }

               wasAttackKeyDown = false;
            } else {
               boolean chargedMode = chargedODMAttacksEnabled;
               if (chargedMode) {
                  boolean attackDown = mc.options.attackKey.isPressed();

                  while (mc.options.attackKey.wasPressed()) {
                  }

                  if (attackDown && !isCharging && !ODMAnimationHandler.isLocalPlayerAttacking()) {
                     isCharging = true;
                     chargeStartTick = mc.world.getTime();
                     if (mainBlade) {
                        triggerCharge(Hand.MAIN_HAND);
                     }

                     if (offBlade) {
                        triggerCharge(Hand.OFF_HAND);
                     }

                     chargeAnimAction = ODMAnimationHandler.triggerAttack();
                     chargeAttackType = ODMAnimationHandler.determineAttackType(player);
                  } else if (isCharging) {
                     long elapsed = mc.world.getTime() - chargeStartTick;
                     if (elapsed >= 3L && !ODMAnimationHandler.isChargeHoldPaused()) {
                        ODMAnimationHandler.pauseForCharge();
                     }

                     if (!attackDown) {
                        isCharging = false;
                        int chargeTicks = (int)(mc.world.getTime() - chargeStartTick);
                        ODMAnimationHandler.resumeFromCharge();
                        if (mainBlade) {
                           releaseCharge(Hand.MAIN_HAND);
                        }

                        if (offBlade) {
                           releaseCharge(Hand.OFF_HAND);
                        }

                        float speed = (float)player.getVelocity().length();
                        ClientPlayNetworking.send(new BladeAOEPayload(chargeAttackType, chargeAnimAction, chargeTicks, speed));
                        float pitch = 0.65F + player.getRandom().nextFloat() * 0.05F;
                        mc.getSoundManager()
                           .play(new EntityTrackingSoundInstance(ModSounds.BLADE_SWING, SoundCategory.PLAYERS, 0.1F, pitch, player, mc.world.random.nextLong()));
                        CameraShakeHandler.triggerSwingDip();
                     }
                  }
               } else {
                  boolean attackDown = mc.options.attackKey.isPressed();
                  boolean clicked = attackDown && !wasAttackKeyDown;
                  wasAttackKeyDown = attackDown;

                  while (mc.options.attackKey.wasPressed()) {
                     clicked = true;
                  }

                  if (clicked) {
                     if (mainBlade) {
                        triggerSwing(Hand.MAIN_HAND);
                     }

                     if (offBlade) {
                        triggerSwing(Hand.OFF_HAND);
                     }

                     int animAction = ODMAnimationHandler.triggerAttack();
                     int attackType = ODMAnimationHandler.determineAttackType(player);
                     ClientPlayNetworking.send(new BladeAOEPayload(attackType, animAction));
                     float pitch = 0.65F + player.getRandom().nextFloat() * 0.05F;
                     mc.getSoundManager()
                        .play(new EntityTrackingSoundInstance(ModSounds.BLADE_SWING, SoundCategory.PLAYERS, 0.1F, pitch, player, mc.world.random.nextLong()));
                     CameraShakeHandler.triggerSwingDip();
                  }
               }
            }
         }
      } else {
         if (isBlockingState) {
            stopBlocking(mc, mc.player);
         }
      }
   }

   private static void tick(MinecraftClient client) {
      previousLeftHandAnimation = leftHandAnimation;
      previousRightHandAnimation = rightHandAnimation;
      previousBoostAnimation = boostAnimation;
      previousBlockAnimation = blockAnimation;
      prevLeftThunderAnim = leftThunderAnim;
      prevRightThunderAnim = rightThunderAnim;
      prevLeftSwingAnim = leftSwingAnim;
      prevRightSwingAnim = rightSwingAnim;
      float freezeScale = 1.0F;
      float newLeftHand = updateAnimation(leftHandAnimation, leftState);
      float newRightHand = updateAnimation(rightHandAnimation, rightState);
      leftHandAnimation = previousLeftHandAnimation + (newLeftHand - previousLeftHandAnimation) * freezeScale;
      rightHandAnimation = previousRightHandAnimation + (newRightHand - previousRightHandAnimation) * freezeScale;
      if (isBoosting) {
         float newBoost = boostAnimation + (1.0F - boostAnimation) * 0.15F;
         boostAnimation = previousBoostAnimation + (newBoost - previousBoostAnimation) * freezeScale;
      } else {
         float newBoost = boostAnimation * 0.85F;
         boostAnimation = previousBoostAnimation + (newBoost - previousBoostAnimation) * freezeScale;
      }

      float newLeftThunder = updateThunder(leftThunderAnim, leftThunderTarget, true);
      float newRightThunder = updateThunder(rightThunderAnim, rightThunderTarget, false);
      leftThunderAnim = prevLeftThunderAnim + (newLeftThunder - prevLeftThunderAnim) * freezeScale;
      rightThunderAnim = prevRightThunderAnim + (newRightThunder - prevRightThunderAnim) * freezeScale;
      float newLeftSwing = updateSwing(leftSwingAnim, leftSwingPhase, true);
      float newRightSwing = updateSwing(rightSwingAnim, rightSwingPhase, false);
      leftSwingAnim = prevLeftSwingAnim + (newLeftSwing - prevLeftSwingAnim) * freezeScale;
      rightSwingAnim = prevRightSwingAnim + (newRightSwing - prevRightSwingAnim) * freezeScale;
      if (isBlockingState) {
         float newBlock = blockAnimation + (1.0F - blockAnimation) * 0.18F;
         blockAnimation = previousBlockAnimation + (newBlock - previousBlockAnimation) * freezeScale;
      } else {
         float newBlock = blockAnimation * 0.82F;
         blockAnimation = previousBlockAnimation + (newBlock - previousBlockAnimation) * freezeScale;
      }

      previousAimAnimationMain = aimAnimationMain;
      previousAimAnimationOff = aimAnimationOff;
      if (isAimingMain) {
         aimAnimationMain = aimAnimationMain + (1.0F - aimAnimationMain) * 0.2F;
      } else {
         aimAnimationMain *= 0.8F;
      }

      if (isAimingOff) {
         aimAnimationOff = aimAnimationOff + (1.0F - aimAnimationOff) * 0.2F;
      } else {
         aimAnimationOff *= 0.8F;
      }

      prevApgRecoilMain = apgRecoilMain;
      prevApgRecoilOff = apgRecoilOff;
      apgRecoilMain *= 0.82F;
      apgRecoilOff *= 0.82F;
      if (leftHandAnimation < 0.001F) {
         leftHandAnimation = 0.0F;
      }

      if (rightHandAnimation < 0.001F) {
         rightHandAnimation = 0.0F;
      }

      if (boostAnimation < 0.001F) {
         boostAnimation = 0.0F;
      }

      if (blockAnimation < 0.001F) {
         blockAnimation = 0.0F;
      }

      if (aimAnimationMain < 0.001F) {
         aimAnimationMain = 0.0F;
      }

      if (aimAnimationOff < 0.001F) {
         aimAnimationOff = 0.0F;
      }

      if (apgRecoilMain < 0.001F) {
         apgRecoilMain = 0.0F;
      }

      if (apgRecoilOff < 0.001F) {
         apgRecoilOff = 0.0F;
      }
   }

   private static float updateSwing(float anim, BladeAnimationHandler.SwingPhase phase, boolean isLeft) {
      switch (phase) {
         case IDLE:
         default:
            if (anim > 0.001F) {
               anim *= 0.7F;
               if (anim < 0.001F) {
                  anim = 0.0F;
               }
            }
            break;
         case CHARGING:
            if (anim < 0.3F) {
               anim += 0.06F;
               if (anim > 0.3F) {
                  anim = 0.3F;
               }
            }
            break;
         case SLASHING:
            anim += 0.14F;
            if (anim >= 1.0F) {
               anim = 0.0F;
               if (isLeft) {
                  leftSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
               } else {
                  rightSwingPhase = BladeAnimationHandler.SwingPhase.IDLE;
               }
            }
      }

      return anim;
   }

   private static float updateThunder(float anim, float target, boolean isLeft) {
      if (target != 0.0F) {
         float diff = target - anim;
         anim += diff * 0.35F;
         if (Math.abs(target - anim) < 0.05F) {
            anim = target;
            if (isLeft) {
               leftThunderTarget = 0.0F;
            } else {
               rightThunderTarget = 0.0F;
            }
         }
      } else {
         anim *= 0.85F;
         if (Math.abs(anim) < 0.01F) {
            anim = 0.0F;
         }
      }

      return anim;
   }

   private static float updateAnimation(float anim, BladeAnimationHandler.AnimState state) {
      return switch (state) {
         default -> anim * 0.75F;
         case FIRING -> {
            if (anim < 1.0F) {
               yield anim + (1.0F - anim) * 0.25F;
            } else {
               yield anim * 0.85F;
            }
         }
         case LATCHED -> {
            if (anim < 1.0F) {
               yield anim + (1.0F - anim) * 0.18F;
            }
            yield anim;
         }
      };
   }

   public static float getAnimation(Hand hand, float partialTick) {
      return hand == Hand.MAIN_HAND
         ? previousRightHandAnimation + (rightHandAnimation - previousRightHandAnimation) * partialTick
         : previousLeftHandAnimation + (leftHandAnimation - previousLeftHandAnimation) * partialTick;
   }

   public static boolean isFiring(Hand hand) {
      return hand == Hand.MAIN_HAND ? rightState == BladeAnimationHandler.AnimState.FIRING : leftState == BladeAnimationHandler.AnimState.FIRING;
   }

   public static boolean isLatched(Hand hand) {
      return hand == Hand.MAIN_HAND ? rightState == BladeAnimationHandler.AnimState.LATCHED : leftState == BladeAnimationHandler.AnimState.LATCHED;
   }

   public static float getBoostAnimation(float partialTick) {
      return previousBoostAnimation + (boostAnimation - previousBoostAnimation) * partialTick;
   }

   public static BladeAnimationHandler.SwingPhase getSwingPhase(Hand hand) {
      return hand == Hand.MAIN_HAND ? rightSwingPhase : leftSwingPhase;
   }

   public static boolean isSwingActive(Hand hand) {
      return getSwingPhase(hand) != BladeAnimationHandler.SwingPhase.IDLE;
   }

   public static boolean isChargingAttack() {
      return isCharging;
   }

   public static float getBlockAnimation(float partialTick) {
      return previousBlockAnimation + (blockAnimation - previousBlockAnimation) * partialTick;
   }

   public static float getSwingAnimation(Hand hand, float partialTick) {
      if (hand == Hand.MAIN_HAND) {
         return rightSwingPhase == BladeAnimationHandler.SwingPhase.IDLE && rightSwingAnim == 0.0F
            ? 0.0F
            : prevRightSwingAnim + (rightSwingAnim - prevRightSwingAnim) * partialTick;
      } else {
         return leftSwingPhase == BladeAnimationHandler.SwingPhase.IDLE && leftSwingAnim == 0.0F
            ? 0.0F
            : prevLeftSwingAnim + (leftSwingAnim - prevLeftSwingAnim) * partialTick;
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum AnimState {
      IDLE,
      FIRING,
      LATCHED;
   }

   @Environment(EnvType.CLIENT)
   public static enum SwingPhase {
      IDLE,
      CHARGING,
      SLASHING;
   }
}
