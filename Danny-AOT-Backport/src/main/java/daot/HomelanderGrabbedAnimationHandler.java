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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class HomelanderGrabbedAnimationHandler {
   private static final Identifier CONTROLLER_ID = new Identifier("dannys-aot", "homelander_grabbed");
   private static final Identifier ANIM_GRABBED = new Identifier("dannys-aot", "grabbed");
   private static final int FADE_TICKS = 6;
   private static final String[] BONE_NAMES = new String[]{
      "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item", "cape", "elytra"
   };
   private static final Map<UUID, HomelanderGrabbedAnimationHandler.AnimState> states = new HashMap<>();
   private static final FirstPersonConfiguration FP_BOTH_ARMS = new FirstPersonConfiguration(true, true, true, true);

   private HomelanderGrabbedAnimationHandler() {
   }

   public static void register() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CONTROLLER_ID, 106, player -> {
         PlayerAnimationController controller = new PlayerAnimationController(player, HomelanderGrabbedAnimationHandler::predicate);
         controller.setFirstPersonModeHandler(HomelanderGrabbedAnimationHandler::firstPersonModeFor);
         controller.setFirstPersonConfigurationHandler(c -> FP_BOTH_ARMS);
         return controller;
      });
   }

   private static FirstPersonMode firstPersonModeFor(AnimationController controller) {
      if (controller instanceof PlayerAnimationController pac) {
         HomelanderGrabbedAnimationHandler.AnimState state = states.get(pac.getPlayer().getUuid());
         return state != null && state.playing ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
      } else {
         return FirstPersonMode.NONE;
      }
   }

   public static void clearState(UUID uuid) {
      states.remove(uuid);
   }

   private static PlayState predicate(AnimationController controller, AnimationData data, AnimationSetter setter) {
      if (controller instanceof PlayerAnimationController pac) {
         AbstractClientPlayerEntity player = pac.getPlayer();
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return PlayState.STOP;
         } else {
            HomelanderGrabbedAnimationHandler.AnimState state = states.computeIfAbsent(player.getUuid(), k -> new HomelanderGrabbedAnimationHandler.AnimState());
            long now = player.getWorld().getTime();
            boolean grabbed = HomelanderGrabClientHandler.findGrabberOf(player.getId()) != null;
            if (!grabbed) {
               if (state.playing) {
                  AbstractFadeModifier fadeOut = AbstractFadeModifier.functionalFadeIn(6, (boneName, progress) -> 1.0F - progress);
                  pac.addModifierBefore(fadeOut);
                  state.playing = false;
                  state.fadingOut = true;
                  state.fadeOutEndTick = now + 6L;
               }

               if (state.fadingOut) {
                  if (now >= state.fadeOutEndTick) {
                     state.fadingOut = false;
                     return PlayState.STOP;
                  } else {
                     return PlayState.CONTINUE;
                  }
               } else {
                  return PlayState.STOP;
               }
            } else {
               state.fadingOut = false;
               if (!state.playing) {
                  Map<String, AdvancedBoneSnapshot> snapshots = snapshotBones(pac);
                  Animation anim = PlayerAnimResources.getAnimation(ANIM_GRABBED);
                  if (anim == null) {
                     return PlayState.STOP;
                  }

                  RawAnimation raw = RawAnimation.begin().thenLoop(anim);
                  pac.forceAnimationReset();
                  setter.setAnimation(raw);
                  applyFadeFromSnapshot(pac, snapshots, 6);
                  state.playing = true;
               } else if (!pac.isActive()) {
                  Map<String, AdvancedBoneSnapshot> snapshots = snapshotBones(pac);
                  Animation anim = PlayerAnimResources.getAnimation(ANIM_GRABBED);
                  if (anim == null) {
                     return PlayState.STOP;
                  }

                  RawAnimation raw = RawAnimation.begin().thenLoop(anim);
                  pac.forceAnimationReset();
                  setter.setAnimation(raw);
                  applyFadeFromSnapshot(pac, snapshots, 6);
               }

               return PlayState.CONTINUE;
            }
         }
      } else {
         return PlayState.STOP;
      }
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
      boolean playing = false;
      boolean fadingOut = false;
      long fadeOutEndTick = 0L;
   }
}
