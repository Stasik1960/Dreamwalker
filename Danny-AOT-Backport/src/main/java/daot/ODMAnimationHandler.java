package daot;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.Animation.LoopType;
import com.zigythebird.playeranimcore.animation.AnimationController.AnimationSetter;
import com.zigythebird.playeranimcore.animation.layered.AnimationSnapshot;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.bones.AdvancedBoneSnapshot;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class ODMAnimationHandler {
   private static final Identifier CONTROLLER_ID = new Identifier("dannys-aot", "odm");
   private static final Identifier ANIM_DOUBLE_HOOK = new Identifier("dannys-aot", "double_hook");
   private static final Identifier ANIM_DOUBLE_HOOK_FLYING = new Identifier("dannys-aot", "double_hook_flying");
   private static final Identifier ANIM_DOUBLE_HOOK_FLYING_BOOST = new Identifier("dannys-aot", "double_hook_flying_boost");
   private static final Identifier ANIM_RIGHT_HOOK = new Identifier("dannys-aot", "right_hook");
   private static final Identifier ANIM_LEFT_HOOK = new Identifier("dannys-aot", "left_hook");
   private static final Identifier ANIM_RIGHT_ORBIT = new Identifier("dannys-aot", "right_orbit");
   private static final Identifier ANIM_LEFT_ORBIT = new Identifier("dannys-aot", "left_orbit");
   private static final Identifier ANIM_RIGHT_ORBIT_BOOST = new Identifier("dannys-aot", "right_orbit_boost");
   private static final Identifier ANIM_LEFT_ORBIT_BOOST = new Identifier("dannys-aot", "left_orbit_boost");
   private static final Identifier ANIM_PERCH = new Identifier("dannys-aot", "perch");
   private static final Identifier ANIM_FALLING = new Identifier("dannys-aot", "falling");
   private static final Identifier ANIM_FALLING_UP = new Identifier("dannys-aot", "falling_up");
   private static final Identifier ANIM_LAND = new Identifier("dannys-aot", "land");
   private static final Identifier ANIM_RELOAD = new Identifier("dannys-aot", "reload");
   private static final Identifier ANIM_RIGHT_THUNDER_SPEAR = new Identifier("dannys-aot", "right_thunder_spear");
   private static final Identifier ANIM_LEFT_THUNDER_SPEAR = new Identifier("dannys-aot", "left_thunder_spear");
   private static final Identifier ANIM_DUAL_THUNDER_SPEAR = new Identifier("dannys-aot", "dual_thunder_spear");
   private static final Identifier ANIM_SPIN_SLASH_RIGHT = new Identifier("dannys-aot", "spin_slash_right");
   private static final Identifier ANIM_SPIN_SLASH_LEFT = new Identifier("dannys-aot", "spin_slash_left");
   private static final Identifier ANIM_DOWN_SLASH = new Identifier("dannys-aot", "down_slash");
   private static final Identifier ANIM_UP_SLASH = new Identifier("dannys-aot", "up_slash");
   private static final Identifier ANIM_BLOCK = new Identifier("dannys-aot", "block");
   private static final Identifier ANIM_BITE = new Identifier("dannys-aot", "bite");
   private static final long BITE_DURATION_TICKS = 21L;
   private static final Identifier ANIM_ACK_DOUBLE_HOOK = new Identifier("dannys-aot", "ackerman_double_hook");
   private static final Identifier ANIM_ACK_DOUBLE_HOOK_FLYING = new Identifier("dannys-aot", "ackerman_double_hook_flying");
   private static final Identifier ANIM_ACK_DOUBLE_HOOK_FLYING_BOOST = new Identifier("dannys-aot", "ackerman_double_hook_flying_boost");
   private static final Identifier ANIM_ACK_RIGHT_HOOK = new Identifier("dannys-aot", "ackerman_right_hook");
   private static final Identifier ANIM_ACK_LEFT_HOOK = new Identifier("dannys-aot", "ackerman_left_hook");
   private static final Identifier ANIM_ACK_RIGHT_ORBIT = new Identifier("dannys-aot", "ackerman_right_orbit");
   private static final Identifier ANIM_ACK_LEFT_ORBIT = new Identifier("dannys-aot", "ackerman_left_orbit");
   private static final Identifier ANIM_ACK_RIGHT_ORBIT_BOOST = new Identifier("dannys-aot", "ackerman_right_orbit_boost");
   private static final Identifier ANIM_ACK_LEFT_ORBIT_BOOST = new Identifier("dannys-aot", "ackerman_left_orbit_boost");
   private static final Identifier ANIM_ACK_FALLING = new Identifier("dannys-aot", "ackerman_falling");
   private static final Identifier ANIM_ACK_FALLING_UP = new Identifier("dannys-aot", "ackerman_falling_up");
   private static final Identifier ANIM_ACK_LAND = new Identifier("dannys-aot", "ackerman_land");
   private static final Identifier ANIM_ACK_RELOAD = new Identifier("dannys-aot", "ackerman_reload");
   private static final Identifier ANIM_ACK_RIGHT_THUNDER_SPEAR = new Identifier("dannys-aot", "ackerman_right_thunder_spear");
   private static final Identifier ANIM_ACK_LEFT_THUNDER_SPEAR = new Identifier("dannys-aot", "ackerman_left_thunder_spear");
   private static final Identifier ANIM_ACK_DUAL_THUNDER_SPEAR = new Identifier("dannys-aot", "ackerman_dual_thunder_spear");
   private static final Identifier ANIM_ACK_SPIN_SLASH_RIGHT = new Identifier("dannys-aot", "ackerman_spin_slash_right");
   private static final Identifier ANIM_ACK_SPIN_SLASH_LEFT = new Identifier("dannys-aot", "ackerman_spin_slash_left");
   private static final Identifier ANIM_ACK_DOWN_SLASH = new Identifier("dannys-aot", "ackerman_down_slash");
   private static final Identifier ANIM_ACK_UP_SLASH = new Identifier("dannys-aot", "ackerman_up_slash");
   private static final Identifier ANIM_ACK_BLOCK = new Identifier("dannys-aot", "ackerman_block");
   private static final Identifier ANIM_ACK_SWIRL_LEFT = new Identifier("dannys-aot", "ackerman_swirl_left");
   private static final Identifier ANIM_ACK_SWIRL_RIGHT = new Identifier("dannys-aot", "ackerman_swirl_right");
   private static final Identifier ANIM_ACK_SPINNER = new Identifier("dannys-aot", "ackerman_spinner");
   private static final Map<Identifier, Identifier> ACKERMAN_MAP = Map.ofEntries(
      Map.entry(ANIM_DOUBLE_HOOK, ANIM_ACK_DOUBLE_HOOK),
      Map.entry(ANIM_DOUBLE_HOOK_FLYING, ANIM_ACK_DOUBLE_HOOK_FLYING),
      Map.entry(ANIM_DOUBLE_HOOK_FLYING_BOOST, ANIM_ACK_DOUBLE_HOOK_FLYING_BOOST),
      Map.entry(ANIM_RIGHT_HOOK, ANIM_ACK_RIGHT_HOOK),
      Map.entry(ANIM_LEFT_HOOK, ANIM_ACK_LEFT_HOOK),
      Map.entry(ANIM_RIGHT_ORBIT, ANIM_ACK_RIGHT_ORBIT),
      Map.entry(ANIM_LEFT_ORBIT, ANIM_ACK_LEFT_ORBIT),
      Map.entry(ANIM_RIGHT_ORBIT_BOOST, ANIM_ACK_RIGHT_ORBIT_BOOST),
      Map.entry(ANIM_LEFT_ORBIT_BOOST, ANIM_ACK_LEFT_ORBIT_BOOST),
      Map.entry(ANIM_FALLING, ANIM_ACK_FALLING),
      Map.entry(ANIM_FALLING_UP, ANIM_ACK_FALLING_UP),
      Map.entry(ANIM_LAND, ANIM_ACK_LAND),
      Map.entry(ANIM_RELOAD, ANIM_ACK_RELOAD),
      Map.entry(ANIM_RIGHT_THUNDER_SPEAR, ANIM_ACK_RIGHT_THUNDER_SPEAR),
      Map.entry(ANIM_LEFT_THUNDER_SPEAR, ANIM_ACK_LEFT_THUNDER_SPEAR),
      Map.entry(ANIM_DUAL_THUNDER_SPEAR, ANIM_ACK_DUAL_THUNDER_SPEAR),
      Map.entry(ANIM_DOWN_SLASH, ANIM_ACK_DOWN_SLASH),
      Map.entry(ANIM_UP_SLASH, ANIM_ACK_UP_SLASH),
      Map.entry(ANIM_BLOCK, ANIM_ACK_BLOCK)
   );
   private static final Set<Identifier> ONESHOT_ANIMS = Set.of(
      ANIM_RELOAD,
      ANIM_ACK_RELOAD,
      ANIM_RIGHT_THUNDER_SPEAR,
      ANIM_ACK_RIGHT_THUNDER_SPEAR,
      ANIM_LEFT_THUNDER_SPEAR,
      ANIM_ACK_LEFT_THUNDER_SPEAR,
      ANIM_DUAL_THUNDER_SPEAR,
      ANIM_ACK_DUAL_THUNDER_SPEAR,
      ANIM_SPIN_SLASH_RIGHT,
      ANIM_SPIN_SLASH_LEFT,
      ANIM_ACK_SWIRL_LEFT,
      ANIM_ACK_SWIRL_RIGHT,
      ANIM_ACK_SPINNER,
      ANIM_DOWN_SLASH,
      ANIM_ACK_DOWN_SLASH,
      ANIM_UP_SLASH,
      ANIM_ACK_UP_SLASH,
      ANIM_BITE
   );
   private static final Set<Identifier> NO_FADE_FROM_ANIMS = Set.of(
      ANIM_SPIN_SLASH_RIGHT,
      ANIM_SPIN_SLASH_LEFT,
      ANIM_ACK_SWIRL_LEFT,
      ANIM_ACK_SWIRL_RIGHT,
      ANIM_ACK_SPINNER,
      ANIM_DOWN_SLASH,
      ANIM_ACK_DOWN_SLASH,
      ANIM_UP_SLASH,
      ANIM_ACK_UP_SLASH
   );
   private static final Set<Identifier> LAND_ANIMS = Set.of(ANIM_LAND, ANIM_ACK_LAND);
   private static final Set<Identifier> FALLING_ANIMS = Set.of(ANIM_FALLING, ANIM_ACK_FALLING);
   private static final Set<Identifier> FALLING_UP_ANIMS = Set.of(ANIM_FALLING_UP, ANIM_ACK_FALLING_UP);
   private static final Set<Identifier> INTRO_ANIMS = Set.of(
      ANIM_DOUBLE_HOOK, ANIM_RIGHT_HOOK, ANIM_LEFT_HOOK, ANIM_LAND, ANIM_ACK_DOUBLE_HOOK, ANIM_ACK_RIGHT_HOOK, ANIM_ACK_LEFT_HOOK, ANIM_ACK_LAND
   );
   private static final int RELOAD_DURATION_TICKS = 15;
   private static final int THUNDER_SPEAR_DURATION_TICKS = 9;
   private static final int DUAL_THUNDER_SPEAR_DURATION_TICKS = 17;
   private static final int SPIN_SLASH_DURATION_TICKS = 10;
   private static final int DOWN_SLASH_DURATION_TICKS = 7;
   private static final int UP_SLASH_DURATION_TICKS = 7;
   private static final int ACK_RELOAD_DURATION_TICKS = 15;
   private static final int ACK_THUNDER_SPEAR_DURATION_TICKS = 9;
   private static final int ACK_DUAL_THUNDER_SPEAR_DURATION_TICKS = 17;
   private static final int ACK_SWIRL_DURATION_TICKS = 20;
   private static final int ACK_SPINNER_DURATION_TICKS = 60;
   private static final int ACK_DOWN_SLASH_DURATION_TICKS = 15;
   private static final int ACK_UP_SLASH_DURATION_TICKS = 15;
   private static final int HOOK_INTRO_TICKS = 10;
   private static final int ACK_HOOK_INTRO_TICKS = 20;
   private static final int LAND_INTRO_TICKS = 5;
   private static final int ACK_LAND_INTRO_TICKS = 10;
   private static final double VERTICAL_DOMINANT_RATIO = 0.6;
   private static final int FADE_TICKS = 5;
   private static final int FADE_TICKS_FALL = 5;
   private static final int FADE_TICKS_ATTACK = 8;
   private static final int FADE_TICKS_INTO_ATTACK = 2;
   private static final int FADE_TICKS_INTRO_OUT = 15;
   private static final String[] BONE_NAMES = new String[]{
      "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item", "cape", "elytra"
   };
   private static final Map<UUID, ODMAnimationHandler.PlayerAnimState> playerStates = new HashMap<>();
   private static boolean ackermanAnimsVerified = false;
   private static boolean chargeHoldPaused = false;
   private static int chargeRemainingTicks = 0;
   private static boolean loggedResolveResult = false;

   public static void pauseForCharge() {
      chargeHoldPaused = true;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ODMAnimationHandler.PlayerAnimState state = playerStates.get(mc.player.getUuid());
         if (state != null && state.oneshotAnim != null) {
            chargeRemainingTicks = Math.max(1, (int)(state.oneshotEndTick - mc.player.getWorld().getTime()));
         }
      }
   }

   public static void resumeFromCharge() {
      chargeHoldPaused = false;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && chargeRemainingTicks > 0) {
         ODMAnimationHandler.PlayerAnimState state = playerStates.get(mc.player.getUuid());
         if (state != null && state.oneshotAnim != null) {
            state.oneshotEndTick = mc.player.getWorld().getTime() + chargeRemainingTicks;
         }
      }

      chargeRemainingTicks = 0;
   }

   public static boolean isChargeHoldPaused() {
      return chargeHoldPaused;
   }

   private static ODMAnimationHandler.PlayerAnimState getState(UUID uuid) {
      return playerStates.computeIfAbsent(uuid, k -> new ODMAnimationHandler.PlayerAnimState());
   }

   private static boolean shouldUseAckerman(UUID playerUuid) {
      if (BloodlineClientData.isAckerman(playerUuid)) {
         return true;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return false;
         } else {
            PlayerEntity p = mc.world.getPlayerByUuid(playerUuid);
            return p == null ? false : p.getEquippedStack(EquipmentSlot.LEGS).getItem() == DannysAot.ODM_APG;
         }
      }
   }

   private static Identifier resolve(Identifier anim, UUID playerUuid) {
      if (anim == null) {
         return null;
      } else if (!shouldUseAckerman(playerUuid)) {
         if (!loggedResolveResult) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && playerUuid.equals(mc.player.getUuid())) {
               loggedResolveResult = true;
               DannysAot.LOGGER.info("[ODMAnimationHandler] Ackerman resolve: isAckerman={}, bloodline={}", false, BloodlineClientData.get(playerUuid));
            }
         }

         return anim;
      } else {
         if (AwakenedPowerClientData.isActiveFor(playerUuid) && isAttackAnimation(anim)) {
            Identifier spinner = ANIM_ACK_SPINNER;
            if (PlayerAnimResources.getAnimation(spinner) != null) {
               return spinner;
            }
         }

         if (!ANIM_SPIN_SLASH_RIGHT.equals(anim) && !ANIM_SPIN_SLASH_LEFT.equals(anim)) {
            Identifier ackAnim = ACKERMAN_MAP.get(anim);
            if (ackAnim != null) {
               boolean loaded = PlayerAnimResources.getAnimation(ackAnim) != null;
               if (!loggedResolveResult) {
                  loggedResolveResult = true;
                  DannysAot.LOGGER.info("[ODMAnimationHandler] Ackerman resolve: isAckerman=true, anim={}, loaded={}", ackAnim, loaded);
               }

               if (loaded) {
                  return ackAnim;
               }
            }

            return anim;
         } else {
            Identifier swirl = resolveSwirl(playerUuid);
            return PlayerAnimResources.getAnimation(swirl) != null ? swirl : anim;
         }
      }
   }

   private static boolean isAttackAnimation(Identifier anim) {
      return ANIM_SPIN_SLASH_RIGHT.equals(anim) || ANIM_SPIN_SLASH_LEFT.equals(anim) || ANIM_DOWN_SLASH.equals(anim) || ANIM_UP_SLASH.equals(anim);
   }

   private static Identifier resolveSwirl(UUID playerUuid) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && playerUuid.equals(mc.player.getUuid())) {
         HookPoint leftHook = ODMTickHandler.getLeftHook();
         HookPoint rightHook = ODMTickHandler.getRightHook();
         boolean leftLatched = leftHook != null && leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
         boolean rightLatched = rightHook != null && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
         if (leftLatched && !rightLatched) {
            return ANIM_ACK_SWIRL_RIGHT;
         } else if (rightLatched && !leftLatched) {
            return ANIM_ACK_SWIRL_LEFT;
         } else {
            return mc.player.getRandom().nextBoolean() ? ANIM_ACK_SWIRL_LEFT : ANIM_ACK_SWIRL_RIGHT;
         }
      } else {
         return ANIM_ACK_SWIRL_LEFT;
      }
   }

   public static void clearState(UUID uuid) {
      playerStates.remove(uuid);
      loggedResolveResult = false;
   }

   public static boolean isLocalPlayerAttacking() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         ODMAnimationHandler.PlayerAnimState state = playerStates.get(mc.player.getUuid());
         if (state != null && state.oneshotAnim != null) {
            if (mc.world.getTime() >= state.oneshotEndTick) {
               return false;
            } else {
               Identifier anim = state.oneshotAnim;
               return isAttackAnimation(anim) || isAckermanAttackAnimation(anim);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isAckermanAttackAnimation(Identifier anim) {
      return ANIM_ACK_SPINNER.equals(anim)
         || ANIM_ACK_SWIRL_LEFT.equals(anim)
         || ANIM_ACK_SWIRL_RIGHT.equals(anim)
         || ANIM_ACK_DOWN_SLASH.equals(anim)
         || ANIM_ACK_UP_SLASH.equals(anim);
   }

   public static void register() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY
         .registerFactory(CONTROLLER_ID, 100, player -> new PlayerAnimationController(player, ODMAnimationHandler::animationPredicate));
   }

   public static void verifyAckermanAnimations() {
      int found = 0;
      int missing = 0;

      for (Identifier ackAnim : ACKERMAN_MAP.values()) {
         if (PlayerAnimResources.getAnimation(ackAnim) != null) {
            found++;
         } else {
            DannysAot.LOGGER.warn("[ODMAnimationHandler] Ackerman animation NOT found: {}", ackAnim);
            missing++;
         }
      }

      DannysAot.LOGGER.info("[ODMAnimationHandler] Ackerman animations: {} loaded, {} missing", found, missing);
   }

   private static PlayState animationPredicate(AnimationController controller, AnimationData data, AnimationSetter setter) {
      if (controller instanceof PlayerAnimationController pac) {
         AbstractClientPlayerEntity player = pac.getPlayer();
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player == null) {
            return PlayState.STOP;
         } else {
            if (!ackermanAnimsVerified) {
               verifyAckermanAnimations();
               ackermanAnimsVerified = true;
            }

            boolean isLocal = player.getUuid().equals(mc.player.getUuid());
            ODMAnimationHandler.PlayerAnimState state = getState(player.getUuid());
            Identifier target;
            if (isLocal) {
               target = determineAnimationLocal(mc);
            } else {
               target = determineAnimationRemote(player);
            }

            if (state.introAnim != null) {
               if (player.getWorld().getTime() < state.introEndTick) {
                  if (target != null && isIntroAnimation(target) && !target.equals(state.introAnim)) {
                     state.introAnim = target;
                     state.introEndTick = player.getWorld().getTime() + getIntroDuration(target, player.getUuid());
                  } else {
                     target = state.introAnim;
                  }
               } else {
                  state.introAnim = null;
               }
            } else if (target != null && isIntroAnimation(target)) {
               state.introAnim = target;
               state.introEndTick = player.getWorld().getTime() + getIntroDuration(target, player.getUuid());
            }

            if (state.blocking) {
               target = ANIM_BLOCK;
            }

            if (state.oneshotAnim != null) {
               if (player.getWorld().getTime() < state.oneshotEndTick) {
                  target = state.oneshotAnim;
               } else {
                  state.oneshotAnim = null;
               }
            }

            target = resolve(target, player.getUuid());
            if (isLocal && ANIM_ACK_SPINNER.equals(target) && state.oneshotAnim != null) {
               long remaining = state.oneshotEndTick - player.getWorld().getTime();
               long elapsed = 60L - remaining;
               if (elapsed >= 5L && remaining >= 5L) {
                  CameraShakeHandler.triggerFastShake(0.35F);
               }
            }

            double freezeFactor = 1.0;
            double fadeScale = 1.0;
            if (target == null) {
               if (state.currentAnim != null) {
                  int outFade = (int)(5.0 * fadeScale);
                  if (shouldSkipFadeFrom(state.currentAnim)) {
                     pac.addModifierLast(new ODMAnimationHandler.RotNormModifier(outFade));
                  }

                  AbstractFadeModifier fadeOut = AbstractFadeModifier.functionalFadeIn(
                     outFade, (boneName, progress) -> 1.0F - (float)(0.5 * (1.0 - Math.cos(progress * Math.PI)))
                  );
                  pac.addModifierBefore(fadeOut);
                  state.currentAnim = null;
                  state.fadingOut = true;
                  state.fadeOutEndTick = player.getWorld().getTime() + outFade;
               }

               if (state.fadingOut) {
                  if (player.getWorld().getTime() >= state.fadeOutEndTick) {
                     state.fadingOut = false;
                     pac.removeModifierIf(m -> m instanceof ODMAnimationHandler.RotNormModifier);
                     return PlayState.STOP;
                  } else {
                     return PlayState.CONTINUE;
                  }
               } else {
                  return PlayState.STOP;
               }
            } else {
               state.fadingOut = false;
               if (!target.equals(state.currentAnim) || state.oneshotForceRestart) {
                  boolean forceRestart = state.oneshotForceRestart;
                  state.oneshotForceRestart = false;
                  Identifier prev = state.currentAnim;
                  state.currentAnim = target;
                  Animation anim = PlayerAnimResources.getAnimation(target);
                  if (anim == null) {
                     return PlayState.STOP;
                  }

                  RawAnimation raw;
                  if (ANIM_ACK_SPINNER.equals(target)) {
                     raw = RawAnimation.begin().thenLoop(anim);
                  } else if (!isOneshotAnimation(target) && !isLandAnimation(target) && anim.loopType() != LoopType.HOLD_ON_LAST_FRAME) {
                     raw = RawAnimation.begin().thenLoop(anim);
                  } else {
                     raw = RawAnimation.begin().thenPlayAndHold(anim);
                  }

                  if (forceRestart) {
                     pac.forceAnimationReset();
                  }

                  setter.setAnimation(raw);
                  pac.removeModifierIf(m -> m instanceof ODMAnimationHandler.RotNormModifier);
                  if (prev != null) {
                     if (shouldSkipFadeFrom(prev)) {
                        int normFade = (int)((isOneshotAnimation(prev) ? 8 : 5) * fadeScale);
                        applyFadeNormalized(pac, Math.max(1, normFade));
                     } else {
                        int fadeTicks;
                        if (isOneshotAnimation(target)) {
                           fadeTicks = 2;
                        } else if (isFallTransition(prev, target)) {
                           fadeTicks = 5;
                        } else if (isOneshotAnimation(prev)) {
                           fadeTicks = 8;
                        } else {
                           fadeTicks = 5;
                        }

                        applyFade(pac, Math.max(1, (int)(fadeTicks * fadeScale)));
                     }
                  } else {
                     applyFadeNormalized(pac, Math.max(1, (int)(5.0 * fadeScale)));
                  }
               }

               if (chargeHoldPaused && isLocal && state.oneshotAnim != null) {
                  state.oneshotEndTick = player.getWorld().getTime() + 200L;
                  boolean hasChargeMod = false;

                  for (AbstractModifier mod : pac.getModifiers()) {
                     if (mod instanceof ODMAnimationHandler.ChargeHoldSpeedModifier) {
                        hasChargeMod = true;
                        break;
                     }
                  }

                  if (!hasChargeMod) {
                     pac.addModifierLast(new ODMAnimationHandler.ChargeHoldSpeedModifier());
                  }
               } else {
                  pac.removeModifierIf(m -> m instanceof ODMAnimationHandler.ChargeHoldSpeedModifier);
               }

               ODMAnimationHandler.DannyFreezeSpeedModifier existing = null;

               for (AbstractModifier modx : pac.getModifiers()) {
                  if (modx instanceof ODMAnimationHandler.DannyFreezeSpeedModifier dfsm) {
                     existing = dfsm;
                     break;
                  }
               }

               if (freezeFactor < 0.999) {
                  if (existing != null) {
                     existing.speed = Math.max(0.01F, (float)freezeFactor);
                  } else {
                     pac.addModifierLast(new ODMAnimationHandler.DannyFreezeSpeedModifier(Math.max(0.01F, (float)freezeFactor)));
                  }
               } else if (existing != null) {
                  pac.removeModifierIf(m -> m instanceof ODMAnimationHandler.DannyFreezeSpeedModifier);
               }

               return PlayState.CONTINUE;
            }
         }
      } else {
         return PlayState.STOP;
      }
   }

   private static boolean isOneshotAnimation(Identifier anim) {
      return anim != null && ONESHOT_ANIMS.contains(anim);
   }

   private static boolean isLandAnimation(Identifier anim) {
      return anim != null && LAND_ANIMS.contains(anim);
   }

   private static boolean shouldSkipFadeFrom(Identifier anim) {
      return anim != null && NO_FADE_FROM_ANIMS.contains(anim);
   }

   private static boolean isFallTransition(Identifier from, Identifier to) {
      return from != null && to != null
         ? FALLING_ANIMS.contains(from) && FALLING_UP_ANIMS.contains(to) || FALLING_UP_ANIMS.contains(from) && FALLING_ANIMS.contains(to)
         : false;
   }

   private static boolean isIntroAnimation(Identifier anim) {
      return anim != null && INTRO_ANIMS.contains(anim);
   }

   private static int getIntroDuration(Identifier anim, UUID playerUuid) {
      boolean ack = shouldUseAckerman(playerUuid);
      if (ANIM_LAND.equals(anim)) {
         return ack ? 10 : 5;
      } else {
         return ack ? 20 : 10;
      }
   }

   private static void applyFade(AnimationController controller) {
      applyFade(controller, 5);
   }

   private static void applyFade(AnimationController controller, int ticks) {
      if (controller.isActive()) {
         AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE);
         Map<String, AdvancedBoneSnapshot> snapshots = new HashMap<>();

         for (String name : BONE_NAMES) {
            PlayerAnimBone bone = controller.getBone(name);
            if (bone != null) {
               snapshots.put(name, new AdvancedBoneSnapshot(bone));
            }
         }

         fade.setTransitionAnimation(new AnimationSnapshot(snapshots));
         controller.addModifierLast(fade);
      }
   }

   private static void applyFadeNormalized(AnimationController controller, int ticks) {
      if (controller.isActive()) {
         AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE);
         Map<String, AdvancedBoneSnapshot> snapshots = new HashMap<>();

         for (String name : BONE_NAMES) {
            PlayerAnimBone bone = controller.getBone(name);
            if (bone != null) {
               bone.setRotX(normalizeAngle(bone.getRotX()));
               bone.setRotY(normalizeAngle(bone.getRotY()));
               bone.setRotZ(normalizeAngle(bone.getRotZ()));
               snapshots.put(name, new AdvancedBoneSnapshot(bone));
            }
         }

         fade.setTransitionAnimation(new AnimationSnapshot(snapshots));
         controller.addModifierLast(fade);
      }
   }

   private static float normalizeAngle(float angle) {
      angle %= (float) (Math.PI * 2);
      if (angle > (float) Math.PI) {
         angle -= (float) (Math.PI * 2);
      }

      if (angle < (float) -Math.PI) {
         angle += (float) (Math.PI * 2);
      }

      return angle;
   }

   public static int determineAttackType(AbstractClientPlayerEntity player) {
      Vec3d vel = player.getVelocity();
      double totalSpeed = vel.length();
      if (totalSpeed < 0.01) {
         return 0;
      } else {
         double verticalRatio = Math.abs(vel.y) / totalSpeed;
         if (verticalRatio > 0.6) {
            return vel.y > 0.0 ? 1 : 2;
         } else {
            return 0;
         }
      }
   }

   public static int determineAnimAction(AbstractClientPlayerEntity player) {
      Vec3d vel = player.getVelocity();
      double totalSpeed = vel.length();
      if (totalSpeed > 0.01) {
         double verticalRatio = Math.abs(vel.y) / totalSpeed;
         if (verticalRatio > 0.6) {
            if (vel.y > 0.0) {
               return 6;
            }

            return 5;
         }
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      boolean leftSide;
      if (mc.player != null && player.getUuid().equals(mc.player.getUuid())) {
         HookPoint leftHook = ODMTickHandler.getLeftHook();
         HookPoint rightHook = ODMTickHandler.getRightHook();
         boolean leftLatched = leftHook != null && leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
         boolean rightLatched = rightHook != null && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
         if (leftLatched && !rightLatched) {
            leftSide = false;
         } else if (rightLatched && !leftLatched) {
            leftSide = true;
         } else {
            boolean pressingA = mc.options.leftKey.isPressed();
            boolean pressingD = mc.options.rightKey.isPressed();
            if (pressingA && !pressingD) {
               leftSide = true;
            } else if (pressingD && !pressingA) {
               leftSide = false;
            } else {
               leftSide = player.getRandom().nextBoolean();
            }
         }
      } else {
         leftSide = player.getRandom().nextBoolean();
      }

      return leftSide ? 4 : 3;
   }

   private static Identifier animActionToResource(int animAction) {
      return switch (animAction) {
         case 3 -> ANIM_SPIN_SLASH_RIGHT;
         case 4 -> ANIM_SPIN_SLASH_LEFT;
         case 5 -> ANIM_DOWN_SLASH;
         case 6 -> ANIM_UP_SLASH;
         default -> ANIM_SPIN_SLASH_RIGHT;
      };
   }

   private static int getDurationForAttackAnim(Identifier anim) {
      if (ANIM_DOWN_SLASH.equals(anim) || ANIM_ACK_DOWN_SLASH.equals(anim)) {
         return ANIM_ACK_DOWN_SLASH.equals(anim) ? 15 : 7;
      } else if (ANIM_UP_SLASH.equals(anim) || ANIM_ACK_UP_SLASH.equals(anim)) {
         return ANIM_ACK_UP_SLASH.equals(anim) ? 15 : 7;
      } else if (ANIM_ACK_SWIRL_LEFT.equals(anim) || ANIM_ACK_SWIRL_RIGHT.equals(anim)) {
         return 20;
      } else {
         return ANIM_ACK_SPINNER.equals(anim) ? 60 : 10;
      }
   }

   private static Identifier determineAnimationLocal(MinecraftClient mc) {
      if (mc.player == null) {
         return null;
      } else if (!DannysAot.isODMGear(mc.player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
         return null;
      } else if (mc.player.hasVehicle()) {
         return null;
      } else {
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         HookPoint leftHook = ODMTickHandler.getLeftHook();
         HookPoint rightHook = ODMTickHandler.getRightHook();
         boolean leftLatched = leftHook != null && leftHook.active && !leftHook.isExtending && !leftHook.isRetracting;
         boolean rightLatched = rightHook != null && rightHook.active && !rightHook.isExtending && !rightHook.isRetracting;
         boolean bothLatched = leftLatched && rightLatched;
         boolean onGround = mc.player.isOnGround();
         boolean isBoosting = mc.options.jumpKey.isPressed();
         boolean pressingA = mc.options.leftKey.isPressed();
         boolean pressingD = mc.options.rightKey.isPressed();
         boolean freshRight = rightLatched && !state.prevRightLatched;
         boolean freshLeft = leftLatched && !state.prevLeftLatched;
         state.prevLeftLatched = leftLatched;
         state.prevRightLatched = rightLatched;
         if (leftLatched || rightLatched) {
            state.wasHooked = true;
         }

         if (!freshRight && !freshLeft) {
            double speed = mc.player.getVelocity().length();
            boolean wantsPerch;
            if (state.perching) {
               wantsPerch = speed < 0.3 && !isBoosting && !pressingA && !pressingD && !onGround;
            } else {
               wantsPerch = speed < 0.08 && !isBoosting && !pressingA && !pressingD && !onGround;
            }

            state.perching = wantsPerch && (leftLatched || rightLatched);
            if (state.perching) {
               Vec3d hookPos = null;
               if (leftLatched && leftHook.position != null) {
                  hookPos = leftHook.position;
               }

               if (rightLatched && rightHook.position != null) {
                  hookPos = rightHook.position;
               }

               if (hookPos != null) {
                  double dx = mc.player.getX() - hookPos.x;
                  double dz = mc.player.getZ() - hookPos.z;
                  if (dx * dx + dz * dz > 0.01) {
                     float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                     yaw = Math.round(yaw / 90.0F) * 90.0F;
                     mc.player.bodyYaw = yaw;
                     mc.player.headYaw = yaw;
                     mc.player.prevBodyYaw = yaw;
                     mc.player.prevHeadYaw = yaw;
                  }
               }
            }

            if (bothLatched) {
               state.wasAirborne = !onGround;
               if (onGround) {
                  return ANIM_DOUBLE_HOOK;
               } else if (state.perching) {
                  return ANIM_PERCH;
               } else {
                  return isBoosting ? ANIM_DOUBLE_HOOK_FLYING_BOOST : ANIM_DOUBLE_HOOK_FLYING;
               }
            } else if (rightLatched) {
               state.wasAirborne = !onGround;
               if (pressingD) {
                  return isBoosting ? ANIM_RIGHT_ORBIT_BOOST : ANIM_RIGHT_ORBIT;
               } else if (pressingA) {
                  return isBoosting ? ANIM_LEFT_ORBIT_BOOST : ANIM_LEFT_ORBIT;
               } else if (onGround) {
                  return ANIM_RIGHT_HOOK;
               } else if (state.perching) {
                  return ANIM_PERCH;
               } else {
                  return isBoosting ? ANIM_RIGHT_ORBIT_BOOST : ANIM_RIGHT_ORBIT;
               }
            } else if (leftLatched) {
               state.wasAirborne = !onGround;
               if (pressingA) {
                  return isBoosting ? ANIM_LEFT_ORBIT_BOOST : ANIM_LEFT_ORBIT;
               } else if (pressingD) {
                  return isBoosting ? ANIM_RIGHT_ORBIT_BOOST : ANIM_RIGHT_ORBIT;
               } else if (onGround) {
                  return ANIM_LEFT_HOOK;
               } else if (state.perching) {
                  return ANIM_PERCH;
               } else {
                  return isBoosting ? ANIM_LEFT_ORBIT_BOOST : ANIM_LEFT_ORBIT;
               }
            } else if (!onGround && state.wasHooked) {
               state.wasAirborne = true;
               double vy = mc.player.getVelocity().y;
               if (state.currentAnim != null && FALLING_UP_ANIMS.contains(state.currentAnim)) {
                  return vy >= -0.1 ? ANIM_FALLING_UP : ANIM_FALLING;
               } else {
                  return vy >= 0.0 ? ANIM_FALLING_UP : ANIM_FALLING;
               }
            } else if (state.wasAirborne && state.wasHooked) {
               state.wasAirborne = false;
               state.wasHooked = false;
               return ANIM_LAND;
            } else {
               if (onGround) {
                  state.wasAirborne = false;
                  state.wasHooked = false;
               }

               return null;
            }
         } else if (freshRight && freshLeft) {
            return ANIM_DOUBLE_HOOK;
         } else {
            return freshRight ? ANIM_RIGHT_HOOK : ANIM_LEFT_HOOK;
         }
      }
   }

   private static Identifier determineAnimationRemote(AbstractClientPlayerEntity player) {
      if (!DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
         return null;
      } else if (player.hasVehicle()) {
         return null;
      } else {
         ODMAnimationHandler.PlayerAnimState state = getState(player.getUuid());
         RemoteHookTracker.RemoteHookData hookData = RemoteHookTracker.getHookData(player.getId());
         boolean leftLatched = false;
         boolean rightLatched = false;
         boolean isBoosting = false;
         if (hookData != null) {
            leftLatched = hookData.leftActive && !hookData.leftExtending && !hookData.leftRetracting;
            rightLatched = hookData.rightActive && !hookData.rightExtending && !hookData.rightRetracting;
            isBoosting = hookData.isBoosting;
         }

         boolean freshRight = rightLatched && !state.prevRightLatched;
         boolean freshLeft = leftLatched && !state.prevLeftLatched;
         state.prevLeftLatched = leftLatched;
         state.prevRightLatched = rightLatched;
         if (leftLatched || rightLatched) {
            state.wasHooked = true;
         }

         boolean bothLatched = leftLatched && rightLatched;
         boolean onGround = player.isOnGround();
         if (!freshRight && !freshLeft) {
            double remoteSpeed = player.getVelocity().length();
            boolean remoteWantsPerch;
            if (state.perching) {
               remoteWantsPerch = remoteSpeed < 0.3 && !isBoosting && !onGround;
            } else {
               remoteWantsPerch = remoteSpeed < 0.08 && !isBoosting && !onGround;
            }

            state.perching = remoteWantsPerch && (leftLatched || rightLatched);
            if (state.perching && hookData != null) {
               Vec3d hookPos = null;
               if (leftLatched && hookData.leftPosition != null) {
                  hookPos = hookData.leftPosition;
               }

               if (rightLatched && hookData.rightPosition != null) {
                  hookPos = hookData.rightPosition;
               }

               if (hookPos != null) {
                  double dx = player.getX() - hookPos.x;
                  double dz = player.getZ() - hookPos.z;
                  if (dx * dx + dz * dz > 0.01) {
                     float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                     yaw = Math.round(yaw / 90.0F) * 90.0F;
                     player.bodyYaw = yaw;
                     player.headYaw = yaw;
                     player.prevBodyYaw = yaw;
                     player.prevHeadYaw = yaw;
                  }
               }
            }

            if (bothLatched) {
               state.wasAirborne = !onGround;
               if (onGround) {
                  return ANIM_DOUBLE_HOOK;
               } else if (state.perching) {
                  return ANIM_PERCH;
               } else {
                  return isBoosting ? ANIM_DOUBLE_HOOK_FLYING_BOOST : ANIM_DOUBLE_HOOK_FLYING;
               }
            } else if (rightLatched || leftLatched) {
               state.wasAirborne = !onGround;
               if (onGround) {
                  return rightLatched ? ANIM_RIGHT_HOOK : ANIM_LEFT_HOOK;
               } else if (state.perching) {
                  return ANIM_PERCH;
               } else if (isBoosting) {
                  return rightLatched ? ANIM_RIGHT_ORBIT_BOOST : ANIM_LEFT_ORBIT_BOOST;
               } else {
                  return rightLatched ? ANIM_RIGHT_ORBIT : ANIM_LEFT_ORBIT;
               }
            } else if (!onGround && state.wasHooked) {
               state.wasAirborne = true;
               double vy = player.getVelocity().y;
               if (state.currentAnim != null && FALLING_UP_ANIMS.contains(state.currentAnim)) {
                  return vy >= -0.1 ? ANIM_FALLING_UP : ANIM_FALLING;
               } else {
                  return vy >= 0.0 ? ANIM_FALLING_UP : ANIM_FALLING;
               }
            } else if (state.wasAirborne && state.wasHooked) {
               state.wasAirborne = false;
               state.wasHooked = false;
               return ANIM_LAND;
            } else {
               if (onGround) {
                  state.wasAirborne = false;
                  state.wasHooked = false;
               }

               return null;
            }
         } else if (freshRight && freshLeft) {
            return ANIM_DOUBLE_HOOK;
         } else {
            return freshRight ? ANIM_RIGHT_HOOK : ANIM_LEFT_HOOK;
         }
      }
   }

   public static void triggerReload() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         Identifier resolved = resolve(ANIM_RELOAD, mc.player.getUuid());
         state.currentAnim = null;
         state.fadingOut = false;
         state.fadeOutEndTick = 0L;
         state.oneshotAnim = resolved;
         state.oneshotForceRestart = true;
         boolean ack = ANIM_ACK_RELOAD.equals(resolved);
         state.oneshotEndTick = mc.player.getWorld().getTime() + (ack ? 15 : 15);
      }
   }

   public static void triggerThunderSpear(boolean leftSide) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         Identifier base = leftSide ? ANIM_LEFT_THUNDER_SPEAR : ANIM_RIGHT_THUNDER_SPEAR;
         Identifier resolved = resolve(base, mc.player.getUuid());
         state.oneshotAnim = resolved;
         state.oneshotForceRestart = true;
         boolean ack = ANIM_ACK_LEFT_THUNDER_SPEAR.equals(resolved) || ANIM_ACK_RIGHT_THUNDER_SPEAR.equals(resolved);
         state.oneshotEndTick = mc.player.getWorld().getTime() + (ack ? 9 : 9);
      }
   }

   public static void triggerDualThunderSpear() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         Identifier resolved = resolve(ANIM_DUAL_THUNDER_SPEAR, mc.player.getUuid());
         state.oneshotAnim = resolved;
         state.oneshotForceRestart = true;
         boolean ack = ANIM_ACK_DUAL_THUNDER_SPEAR.equals(resolved);
         state.oneshotEndTick = mc.player.getWorld().getTime() + (ack ? 17 : 17);
      }
   }

   public static int triggerAttack() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return 3;
      } else {
         int animAction = determineAnimAction(mc.player);
         Identifier anim = animActionToResource(animAction);
         Identifier resolved = resolve(anim, mc.player.getUuid());
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         state.oneshotAnim = resolved;
         state.oneshotForceRestart = true;
         state.oneshotEndTick = mc.player.getWorld().getTime() + getDurationForAttackAnim(resolved);
         return animAction;
      }
   }

   public static void triggerBlock() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         state.blocking = true;
      }
   }

   public static void triggerBiteForPlayer(int entityId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         if (mc.world.getEntityById(entityId) instanceof AbstractClientPlayerEntity player) {
            ODMAnimationHandler.PlayerAnimState state = getState(player.getUuid());
            state.currentAnim = null;
            state.fadingOut = false;
            state.fadeOutEndTick = 0L;
            state.oneshotAnim = ANIM_BITE;
            state.oneshotForceRestart = true;
            state.oneshotEndTick = player.getWorld().getTime() + 21L;
         }
      }
   }

   public static boolean isBiting(UUID playerUuid, long gameTime) {
      ODMAnimationHandler.PlayerAnimState state = playerStates.get(playerUuid);
      return state == null ? false : ANIM_BITE.equals(state.oneshotAnim) && gameTime < state.oneshotEndTick;
   }

   public static void stopBlock() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ODMAnimationHandler.PlayerAnimState state = getState(mc.player.getUuid());
         state.blocking = false;
      }
   }

   public static void applyRemoteAction(int entityId, int action) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         if (mc.world.getEntityById(entityId) instanceof AbstractClientPlayerEntity player) {
            if (mc.player == null || !player.getUuid().equals(mc.player.getUuid())) {
               ODMAnimationHandler.PlayerAnimState state = getState(player.getUuid());
               switch (action) {
                  case 0:
                     state.blocking = true;
                     break;
                  case 1:
                     state.blocking = false;
                     break;
                  case 2:
                     state.blocking = false;
                     break;
                  case 3:
                  case 4:
                  case 5:
                  case 6:
                     Identifier anim = animActionToResource(action);
                     Identifier resolved = resolve(anim, player.getUuid());
                     state.oneshotAnim = resolved;
                     state.oneshotForceRestart = true;
                     state.oneshotEndTick = player.getWorld().getTime() + getDurationForAttackAnim(resolved);
                     float pitch = 0.65F + player.getRandom().nextFloat() * 0.05F;
                     mc.getSoundManager()
                        .play(new EntityTrackingSoundInstance(ModSounds.BLADE_SWING, SoundCategory.PLAYERS, 0.1F, pitch, player, mc.world.random.nextLong()));
                     break;
                  case 7:
                     state.oneshotAnim = ANIM_RIGHT_THUNDER_SPEAR;
                     state.oneshotForceRestart = true;
                     state.oneshotEndTick = player.getWorld().getTime() + 9L;
                     break;
                  case 8:
                     state.oneshotAnim = ANIM_LEFT_THUNDER_SPEAR;
                     state.oneshotForceRestart = true;
                     state.oneshotEndTick = player.getWorld().getTime() + 9L;
                     break;
                  case 9:
                     state.oneshotAnim = ANIM_DUAL_THUNDER_SPEAR;
                     state.oneshotForceRestart = true;
                     state.oneshotEndTick = player.getWorld().getTime() + 17L;
                     break;
                  case 10:
                     state.oneshotAnim = ANIM_RELOAD;
                     state.oneshotForceRestart = true;
                     state.oneshotEndTick = player.getWorld().getTime() + 15L;
               }
            }
         }
      }
   }

   public static void reset() {
      playerStates.clear();
   }

   @Environment(EnvType.CLIENT)
   private static class ChargeHoldSpeedModifier extends SpeedModifier {
      public ChargeHoldSpeedModifier() {
         super(0.001F);
      }
   }

   @Environment(EnvType.CLIENT)
   private static class DannyFreezeSpeedModifier extends SpeedModifier {
      public DannyFreezeSpeedModifier(float speed) {
         super(speed);
      }
   }

   @Environment(EnvType.CLIENT)
   private static class PlayerAnimState {
      Identifier currentAnim = null;
      boolean wasAirborne = false;
      boolean fadingOut = false;
      long fadeOutEndTick = 0L;
      boolean wasHooked = false;
      Identifier oneshotAnim = null;
      long oneshotEndTick = 0L;
      boolean oneshotForceRestart = false;
      boolean blocking = false;
      Identifier introAnim = null;
      long introEndTick = 0L;
      boolean prevLeftLatched = false;
      boolean prevRightLatched = false;
      boolean perching = false;
   }

   @Environment(EnvType.CLIENT)
   private static class RotNormModifier extends AbstractModifier {
      private int ticksLeft;

      RotNormModifier(int fadeDuration) {
         this.ticksLeft = fadeDuration + 3;
      }

      public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
         bone = super.get3DTransform(bone);
         bone.setRotX(ODMAnimationHandler.normalizeAngle(bone.getRotX()));
         bone.setRotY(ODMAnimationHandler.normalizeAngle(bone.getRotY()));
         bone.setRotZ(ODMAnimationHandler.normalizeAngle(bone.getRotZ()));
         return bone;
      }

      public void tick(AnimationData state) {
         super.tick(state);
         this.ticksLeft--;
      }

      public boolean canRemove() {
         return this.ticksLeft <= 0;
      }
   }
}
