package daot;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.AnimationController.AnimationSetter;
import com.zigythebird.playeranimcore.animation.layered.AnimationSnapshot;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.AdvancedBoneSnapshot;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class HomelanderPlayerAnimationHandler {
   private static final Identifier CONTROLLER_ID = new Identifier("dannys-aot", "homelander_flight");
   private static final Identifier ANIM_FLY_IDLE = new Identifier("dannys-aot", "fly_idle");
   private static final Identifier ANIM_FLY_FORWARD = new Identifier("dannys-aot", "fly_forward");
   private static final Identifier ANIM_FLY_BACKWARD = new Identifier("dannys-aot", "fly_backward");
   private static final Identifier ANIM_FLY_LEFT = new Identifier("dannys-aot", "fly_left");
   private static final Identifier ANIM_FLY_RIGHT = new Identifier("dannys-aot", "fly_right");
   private static final Identifier ANIM_SUPER_FLY_START = new Identifier("dannys-aot", "super_fly_start");
   private static final Identifier ANIM_SUPER_FLY = new Identifier("dannys-aot", "super_fly");
   private static final Identifier ANIM_FLY_GRAB = new Identifier("dannys-aot", "fly_grab");
   private static final Identifier ANIM_FLY_ATTACK = new Identifier("dannys-aot", "fly_attack");
   private static final Identifier ANIM_ATTACK_1 = new Identifier("dannys-aot", "attack1");
   private static final Identifier ANIM_ATTACK_2 = new Identifier("dannys-aot", "attack2");
   private static final Identifier ANIM_FLY_START = new Identifier("dannys-aot", "fly_start");
   public static final int FLY_START_DURATION_TICKS = 10;
   private static final int FADE_TICKS = 8;
   private static final int FAST_FADE_TICKS = 2;
   private static final double MOTION_THRESHOLD = 0.05;
   private static final String[] BONE_NAMES = new String[]{
      "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item", "cape", "elytra"
   };
   private static final Map<UUID, HomelanderPlayerAnimationHandler.AnimState> states = new HashMap<>();
   private static final Map<UUID, Byte> remoteInputBits = new ConcurrentHashMap<>();
   private static final int FLY_START_EFFECT_TICK_OFFSET = 8;
   private static final FirstPersonConfiguration FP_BOTH_ARMS = new FirstPersonConfiguration(true, true, true, true);
   private static final FirstPersonConfiguration FP_RIGHT_ARM = new FirstPersonConfiguration(true, false, true, true);
   private static final FirstPersonConfiguration FP_LEFT_ARM = new FirstPersonConfiguration(false, true, true, true);

   public static void register() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CONTROLLER_ID, 105, player -> {
         PlayerAnimationController controller = new PlayerAnimationController(player, HomelanderPlayerAnimationHandler::predicate);
         controller.setFirstPersonModeHandler(HomelanderPlayerAnimationHandler::firstPersonModeFor);
         controller.setFirstPersonConfigurationHandler(HomelanderPlayerAnimationHandler::firstPersonConfigFor);
         return controller;
      });
   }

   private static FirstPersonMode firstPersonModeFor(AnimationController controller) {
      if (controller instanceof PlayerAnimationController pac) {
         HomelanderPlayerAnimationHandler.AnimState state = states.get(pac.getPlayer().getUuid());
         if (state == null) {
            return FirstPersonMode.NONE;
         } else {
            Identifier active = activeAnim(state);
            return isFirstPersonAnim(active) ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
         }
      } else {
         return FirstPersonMode.NONE;
      }
   }

   private static FirstPersonConfiguration firstPersonConfigFor(AnimationController controller) {
      if (controller instanceof PlayerAnimationController pac) {
         HomelanderPlayerAnimationHandler.AnimState state = states.get(pac.getPlayer().getUuid());
         if (state == null) {
            return FP_BOTH_ARMS;
         } else {
            Identifier active = activeAnim(state);
            if (active == null) {
               return FP_BOTH_ARMS;
            } else if (active.equals(ANIM_ATTACK_1)) {
               return FP_RIGHT_ARM;
            } else {
               return active.equals(ANIM_ATTACK_2) ? FP_LEFT_ARM : FP_BOTH_ARMS;
            }
         }
      } else {
         return FP_BOTH_ARMS;
      }
   }

   private static Identifier activeAnim(HomelanderPlayerAnimationHandler.AnimState state) {
      return state.currentAttack != null ? state.currentAttack : state.currentAnim;
   }

   private static boolean isFirstPersonAnim(Identifier anim) {
      return anim == null
         ? false
         : anim.equals(ANIM_FLY_GRAB)
            || anim.equals(ANIM_SUPER_FLY_START)
            || anim.equals(ANIM_FLY_ATTACK)
            || anim.equals(ANIM_ATTACK_1)
            || anim.equals(ANIM_ATTACK_2);
   }

   public static void clearState(UUID uuid) {
      states.remove(uuid);
      remoteInputBits.remove(uuid);
   }

   public static void onRemoteInputBits(UUID uuid, byte bits) {
      if (bits == 0) {
         remoteInputBits.remove(uuid);
      } else {
         remoteInputBits.put(uuid, bits);
      }
   }

   public static void clearRemoteInputBits(UUID uuid) {
      remoteInputBits.remove(uuid);
   }

   public static boolean isInSuperFly(UUID uuid) {
      HomelanderPlayerAnimationHandler.AnimState state = states.get(uuid);
      if (state == null) {
         return false;
      } else {
         Identifier anim = state.currentAnim;
         return anim != null && (anim.equals(ANIM_SUPER_FLY) || anim.equals(ANIM_SUPER_FLY_START));
      }
   }

   public static boolean isInFlyStart(UUID uuid) {
      HomelanderPlayerAnimationHandler.AnimState state = states.get(uuid);
      if (state != null && state.currentAttack == ANIM_FLY_START) {
         MinecraftClient mc = MinecraftClient.getInstance();
         return mc.world == null ? true : mc.world.getTime() < state.attackEndTick;
      } else {
         return false;
      }
   }

   public static void setLocalGrabIntent(boolean wantsGrab) {
      AbstractClientPlayerEntity self = MinecraftClient.getInstance().player;
      if (self != null) {
         HomelanderPlayerAnimationHandler.AnimState state = states.computeIfAbsent(self.getUuid(), k -> new HomelanderPlayerAnimationHandler.AnimState());
         state.grabIntent = wantsGrab;
      }
   }

   public static void setRemoteGrabIntent(UUID uuid, boolean wantsGrab) {
      HomelanderPlayerAnimationHandler.AnimState state = states.computeIfAbsent(uuid, k -> new HomelanderPlayerAnimationHandler.AnimState());
      state.grabIntent = wantsGrab;
   }

   public static void triggerFlyStart(AbstractClientPlayerEntity player) {
      HomelanderPlayerAnimationHandler.AnimState state = states.computeIfAbsent(player.getUuid(), k -> new HomelanderPlayerAnimationHandler.AnimState());
      long now = player.getWorld().getTime();
      state.currentAttack = ANIM_FLY_START;
      state.attackStartTick = now;
      state.attackEndTick = now + 10L;
      state.flyStartEffectFired = false;
   }

   private static void playTakeoffEffect(AbstractClientPlayerEntity player) {
      World level = player.getWorld();
      if (level != null) {
         double cx = player.getX();
         double cy = player.getY();
         double cz = player.getZ();
         level.playSound(cx, cy, cz, ModSounds.BOOM, SoundCategory.PLAYERS, 1.0F, 1.0F, false);
         if (player == MinecraftClient.getInstance().player) {
            CameraShakeHandler.triggerEarthquakeShake(6, 1.0F);
         }

         Random rng = level.getRandom();
         int ring = 40;

         for (int i = 0; i < ring; i++) {
            double angle = (Math.PI * 2) * i / ring;
            double cs = Math.cos(angle);
            double sn = Math.sin(angle);
            double r = 0.5 + rng.nextDouble() * 1.0;
            double px = cx + cs * r;
            double pz = cz + sn * r;
            double vx = cs * (0.2 + rng.nextDouble() * 0.3);
            double vz = sn * (0.2 + rng.nextDouble() * 0.3);
            double vy = 0.5 + rng.nextDouble() * 0.5;
            level.addParticle(ParticleTypes.POOF, px, cy + 0.1, pz, vx, vy, vz);
         }

         for (int i = 0; i < 20; i++) {
            double ox = (rng.nextDouble() - 0.5) * 0.6;
            double oz = (rng.nextDouble() - 0.5) * 0.6;
            double vx = (rng.nextDouble() - 0.5) * 0.3;
            double vz = (rng.nextDouble() - 0.5) * 0.3;
            double vy = 0.4 + rng.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.POOF, cx + ox, cy + 0.1, cz + oz, vx, vy, vz);
         }
      }
   }

   public static void triggerAttack(AbstractClientPlayerEntity player, byte attackType, int cooldownTicks) {
      HomelanderPlayerAnimationHandler.AnimState state = states.computeIfAbsent(player.getUuid(), k -> new HomelanderPlayerAnimationHandler.AnimState());
      Identifier anim;
      switch (attackType) {
         case 0:
            anim = ANIM_FLY_ATTACK;
            break;
         case 1:
            anim = ANIM_ATTACK_1;
            break;
         case 2:
            anim = ANIM_ATTACK_2;
            break;
         default:
            return;
      }

      long now = player.getWorld().getTime();
      state.currentAttack = anim;
      state.attackStartTick = now;
      state.attackEndTick = now + cooldownTicks;
   }

   private static PlayState predicate(AnimationController controller, AnimationData data, AnimationSetter setter) {
      if (controller instanceof PlayerAnimationController pac) {
         AbstractClientPlayerEntity player = pac.getPlayer();
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && mc.world != null) {
            HomelanderPlayerAnimationHandler.AnimState state = states.computeIfAbsent(player.getUuid(), k -> new HomelanderPlayerAnimationHandler.AnimState());
            long now = player.getWorld().getTime();
            boolean flying = HomelanderFlightHandler.isFlying(player.getUuid());
            if (!flying) {
               if (state.currentAnim != null) {
                  AbstractFadeModifier fadeOut = AbstractFadeModifier.functionalFadeIn(8, (boneName, progress) -> 1.0F - progress);
                  pac.addModifierBefore(fadeOut);
                  state.currentAnim = null;
                  state.fadingOut = true;
                  state.fadeOutEndTick = now + 8L;
               }

               if (!state.fadingOut) {
                  return PlayState.STOP;
               } else if (now >= state.fadeOutEndTick) {
                  state.fadingOut = false;
                  return PlayState.STOP;
               } else {
                  return PlayState.CONTINUE;
               }
            } else {
               state.fadingOut = false;
               if (state.currentAttack == ANIM_FLY_START && !state.flyStartEffectFired && now >= state.attackStartTick + 8L) {
                  state.flyStartEffectFired = true;
                  playTakeoffEffect(player);
               }

               Identifier target = pickAnimation(player, mc, state, now);
               if (!target.equals(state.currentAnim)) {
                  state.currentAnim = target;
                  Map<String, AdvancedBoneSnapshot> snapshots = snapshotBones(pac);
                  RawAnimation raw = buildRawAnimation(target);
                  if (raw == null) {
                     return PlayState.STOP;
                  }

                  pac.forceAnimationReset();
                  setter.setAnimation(raw);
                  int fadeTicks = fadeTicksFor(target);
                  if (fadeTicks > 0) {
                     applyFadeFromSnapshot(pac, snapshots, fadeTicks);
                  }
               } else if (!pac.isActive()) {
                  Map<String, AdvancedBoneSnapshot> snapshotsx = snapshotBones(pac);
                  RawAnimation rawx = buildRawAnimation(target);
                  if (rawx != null) {
                     pac.forceAnimationReset();
                     setter.setAnimation(rawx);
                     int fadeTicks = fadeTicksFor(target);
                     if (fadeTicks > 0) {
                        applyFadeFromSnapshot(pac, snapshotsx, fadeTicks);
                     }
                  }
               }

               return PlayState.CONTINUE;
            }
         } else {
            return PlayState.STOP;
         }
      } else {
         return PlayState.STOP;
      }
   }

   private static RawAnimation buildRawAnimation(Identifier target) {
      Animation anim = PlayerAnimResources.getAnimation(target);
      if (anim == null) {
         return null;
      } else {
         return !target.equals(ANIM_SUPER_FLY_START)
               && !target.equals(ANIM_FLY_ATTACK)
               && !target.equals(ANIM_ATTACK_1)
               && !target.equals(ANIM_ATTACK_2)
               && !target.equals(ANIM_FLY_START)
            ? RawAnimation.begin().thenLoop(anim)
            : RawAnimation.begin().thenPlayAndHold(anim);
      }
   }

   private static Identifier pickAnimation(AbstractClientPlayerEntity player, MinecraftClient mc, HomelanderPlayerAnimationHandler.AnimState state, long now) {
      if (state.currentAttack != null) {
         if (now < state.attackEndTick) {
            return state.currentAttack;
         }

         state.currentAttack = null;
      }

      if (state.grabIntent) {
         return ANIM_FLY_GRAB;
      } else {
         boolean isLocal = player == mc.player;
         double fwd;
         double strafe;
         boolean jumping;
         boolean shifting;
         boolean superFlying;
         if (isLocal && mc.player != null) {
            ClientPlayerEntity lp = mc.player;
            fwd = lp.input.movementForward;
            strafe = lp.input.movementSideways;
            jumping = lp.input.jumping;
            shifting = lp.input.sneaking;
            superFlying = mc.options.sprintKey.isPressed();
         } else {
            byte bits = remoteInputBits.getOrDefault(player.getUuid(), (byte)0);
            boolean fwdBit = (bits & 1) != 0;
            boolean backBit = (bits & 2) != 0;
            boolean leftBit = (bits & 4) != 0;
            boolean rightBit = (bits & 8) != 0;
            fwd = fwdBit ? 1.0 : (backBit ? -1 : 0);
            strafe = leftBit ? 1.0 : (rightBit ? -1 : 0);
            jumping = (bits & 16) != 0;
            shifting = (bits & 32) != 0;
            superFlying = (bits & 64) != 0;
         }

         if (superFlying) {
            boolean alreadyInSuperFly = state.currentAnim == ANIM_SUPER_FLY_START || state.currentAnim == ANIM_SUPER_FLY;
            if (!alreadyInSuperFly) {
               Animation startAnim = PlayerAnimResources.getAnimation(ANIM_SUPER_FLY_START);
               if (startAnim != null) {
                  state.superFlyStartEndTick = now + Math.max(8L, (long)Math.ceil(startAnim.length()));
                  return ANIM_SUPER_FLY_START;
               } else {
                  return ANIM_SUPER_FLY;
               }
            } else {
               return state.currentAnim == ANIM_SUPER_FLY_START && now < state.superFlyStartEndTick ? ANIM_SUPER_FLY_START : ANIM_SUPER_FLY;
            }
         } else if (shifting) {
            return ANIM_FLY_BACKWARD;
         } else if (jumping) {
            return ANIM_FLY_FORWARD;
         } else {
            boolean fwdActive = Math.abs(fwd) > 0.05;
            boolean strafeActive = Math.abs(strafe) > 0.05;
            if (!fwdActive || strafeActive && !(Math.abs(fwd) >= Math.abs(strafe))) {
               if (strafeActive) {
                  return strafe > 0.0 ? ANIM_FLY_LEFT : ANIM_FLY_RIGHT;
               } else {
                  return ANIM_FLY_IDLE;
               }
            } else {
               return fwd > 0.0 ? ANIM_FLY_FORWARD : ANIM_FLY_BACKWARD;
            }
         }
      }
   }

   private static int fadeTicksFor(Identifier target) {
      return !target.equals(ANIM_FLY_ATTACK)
            && !target.equals(ANIM_ATTACK_1)
            && !target.equals(ANIM_ATTACK_2)
            && !target.equals(ANIM_FLY_GRAB)
            && !target.equals(ANIM_FLY_START)
         ? 8
         : 2;
   }

   private static Map<String, AdvancedBoneSnapshot> snapshotBones(AnimationController controller) {
      Map<String, AdvancedBoneSnapshot> snapshots = new HashMap<>();

      for (String name : BONE_NAMES) {
         PlayerAnimBone bone = controller.getBone(name);
         if (bone != null) {
            snapshots.put(name, new AdvancedBoneSnapshot(bone));
         }
      }

      return snapshots;
   }

   private static void applyFadeFromSnapshot(AnimationController controller, Map<String, AdvancedBoneSnapshot> snapshots, int ticks) {
      if (snapshots != null && !snapshots.isEmpty()) {
         AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE);
         fade.setTransitionAnimation(new AnimationSnapshot(snapshots));
         controller.addModifierLast(fade);
      }
   }

   @Environment(EnvType.CLIENT)
   private static class AnimState {
      Identifier currentAnim = null;
      boolean fadingOut = false;
      long fadeOutEndTick = 0L;
      long superFlyStartEndTick = 0L;
      Identifier currentAttack = null;
      long attackEndTick = 0L;
      long attackStartTick = 0L;
      boolean grabIntent = false;
      boolean flyStartEffectFired = false;
   }
}
