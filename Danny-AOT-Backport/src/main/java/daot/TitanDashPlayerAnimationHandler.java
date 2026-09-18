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
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanDashPlayerAnimationHandler {
   private static final Identifier CONTROLLER_ID = new Identifier("dannys-aot", "titan_dash");
   private static final Identifier ANIM_DASH = new Identifier("dannys-aot", "dash");
   private static final long DASH_ANIM_TICKS = 50L;
   private static final int FADE_TICKS = 3;
   private static final String[] BONE_NAMES = new String[]{
      "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item", "cape", "elytra"
   };
   private static final Map<UUID, TitanDashPlayerAnimationHandler.AnimState> states = new ConcurrentHashMap<>();

   public static void register() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY
         .registerFactory(CONTROLLER_ID, 116, player -> new PlayerAnimationController(player, TitanDashPlayerAnimationHandler::predicate));
   }

   public static void triggerDash() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         play(mc.player.getUuid(), mc.player.getWorld().getTime());
      }
   }

   public static void playRemote(int entityId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         if (mc.world.getEntityById(entityId) instanceof AbstractClientPlayerEntity p) {
            play(p.getUuid(), p.getWorld().getTime());
         }
      }
   }

   private static void play(UUID uuid, long now) {
      TitanDashPlayerAnimationHandler.AnimState state = states.computeIfAbsent(uuid, k -> new TitanDashPlayerAnimationHandler.AnimState());
      state.oneshot = ANIM_DASH;
      state.oneshotEndTick = now + 50L;
      state.forceRestart = true;
      state.fadingOut = false;
   }

   public static void clearState(UUID uuid) {
      states.remove(uuid);
   }

   public static void reset() {
      states.clear();
   }

   private static PlayState predicate(AnimationController controller, AnimationData data, AnimationSetter setter) {
      if (controller instanceof PlayerAnimationController pac) {
         AbstractClientPlayerEntity player = pac.getPlayer();
         TitanDashPlayerAnimationHandler.AnimState state = states.get(player.getUuid());
         if (state == null) {
            return PlayState.STOP;
         } else {
            long now = player.getWorld().getTime();
            Identifier target = null;
            if (state.oneshot != null) {
               if (now < state.oneshotEndTick) {
                  target = state.oneshot;
               } else {
                  state.oneshot = null;
               }
            }

            if (target == null) {
               if (state.currentAnim != null) {
                  AbstractFadeModifier fadeOut = AbstractFadeModifier.functionalFadeIn(3, (boneName, progress) -> 1.0F - progress);
                  pac.addModifierBefore(fadeOut);
                  state.currentAnim = null;
                  state.fadingOut = true;
                  state.fadeOutEndTick = now + 3L;
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
               if (state.forceRestart || !target.equals(state.currentAnim)) {
                  boolean forceRestart = state.forceRestart;
                  state.forceRestart = false;
                  state.currentAnim = target;
                  Animation anim = PlayerAnimResources.getAnimation(target);
                  if (anim == null) {
                     return PlayState.STOP;
                  }

                  if (forceRestart) {
                     pac.forceAnimationReset();
                  }

                  setter.setAnimation(RawAnimation.begin().thenPlayAndHold(anim));
                  applyFade(pac, 3);
               }

               return PlayState.CONTINUE;
            }
         }
      } else {
         return PlayState.STOP;
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
   private static class AnimState {
      Identifier oneshot = null;
      long oneshotEndTick = 0L;
      boolean forceRestart = false;
      Identifier currentAnim = null;
      boolean fadingOut = false;
      long fadeOutEndTick = 0L;
   }
}
