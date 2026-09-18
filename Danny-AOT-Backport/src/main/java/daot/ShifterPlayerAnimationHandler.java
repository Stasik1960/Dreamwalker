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
import com.zigythebird.playeranimcore.bones.AdvancedBoneSnapshot;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ShifterPlayerAnimationHandler {
   private static final Identifier CONTROLLER_ID = new Identifier("dannys-aot", "shifter");
   private static final Identifier ANIM_EXIT_IDLE = new Identifier("dannys-aot", "exit_idle");
   private static final Identifier ANIM_ENTER_NAPE = new Identifier("dannys-aot", "enter_nape");
   private static final Identifier ANIM_EXIT_NAPE = new Identifier("dannys-aot", "exit_nape");
   private static final long TRANSITION_TICKS = 10L;
   private static final int FADE_TICKS = 4;
   private static final String[] BONE_NAMES = new String[]{
      "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item", "cape", "elytra"
   };
   private static final Map<UUID, ShifterPlayerAnimationHandler.ShifterAnimState> states = new HashMap<>();

   public static void register() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY
         .registerFactory(CONTROLLER_ID, 110, player -> new PlayerAnimationController(player, ShifterPlayerAnimationHandler::predicate));
   }

   public static void clearState(UUID uuid) {
      states.remove(uuid);
   }

   public static void reset() {
      states.clear();
   }

   private static PlayState predicate(AnimationController controller, AnimationData data, AnimationSetter setter) {
      if (!(controller instanceof PlayerAnimationController pac)) {
         return PlayState.STOP;
      } else {
         AbstractClientPlayerEntity player = pac.getPlayer();
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && mc.world != null) {
            ShifterPlayerAnimationHandler.ShifterAnimState state = states.computeIfAbsent(
               player.getUuid(), k -> new ShifterPlayerAnimationHandler.ShifterAnimState()
            );
            long now = player.getWorld().getTime();
            Entity vehicle = player.getVehicle();
            boolean isShifterRider = vehicle instanceof ShifterTitan;
            boolean inDismountMode = isShifterRider && ((ShifterTitan)vehicle).isDismounting();
            if (state.wasShifterRider && isShifterRider && state.lastDismounting != null && state.lastDismounting != inDismountMode) {
               if (inDismountMode) {
                  state.oneshot = ANIM_EXIT_NAPE;
                  state.oneshotEndTick = now + 10L;
               } else {
                  state.oneshot = ANIM_ENTER_NAPE;
                  state.oneshotEndTick = now + 10L;
               }
            }

            state.lastDismounting = inDismountMode;
            state.wasShifterRider = isShifterRider;
            Identifier target = null;
            if (state.oneshot != null) {
               if (now < state.oneshotEndTick) {
                  target = state.oneshot;
               } else {
                  state.oneshot = null;
               }
            }

            if (target == null && isShifterRider && inDismountMode) {
               target = ANIM_EXIT_IDLE;
            }

            if (target == null) {
               if (state.currentAnim != null) {
                  AbstractFadeModifier fadeOut = AbstractFadeModifier.functionalFadeIn(4, (boneName, progress) -> 1.0F - progress);
                  pac.addModifierBefore(fadeOut);
                  state.currentAnim = null;
                  state.fadingOut = true;
                  state.fadeOutEndTick = now + 4L;
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
               if (!target.equals(state.currentAnim)) {
                  Identifier prev = state.currentAnim;
                  state.currentAnim = target;
                  Animation anim = PlayerAnimResources.getAnimation(target);
                  if (anim == null) {
                     return PlayState.STOP;
                  }

                  RawAnimation raw;
                  if (target.equals(ANIM_EXIT_IDLE)) {
                     raw = RawAnimation.begin().thenLoop(anim);
                  } else {
                     raw = RawAnimation.begin().thenPlayAndHold(anim);
                  }

                  setter.setAnimation(raw);
                  applyFade(pac, 4);
                  if (prev == null) {
                  }
               }

               return PlayState.CONTINUE;
            }
         } else {
            return PlayState.STOP;
         }
      }
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

   @Environment(EnvType.CLIENT)
   private static class ShifterAnimState {
      Boolean lastDismounting = null;
      boolean wasShifterRider = false;
      Identifier oneshot = null;
      long oneshotEndTick = 0L;
      Identifier currentAnim = null;
      boolean fadingOut = false;
      long fadeOutEndTick = 0L;
   }
}
