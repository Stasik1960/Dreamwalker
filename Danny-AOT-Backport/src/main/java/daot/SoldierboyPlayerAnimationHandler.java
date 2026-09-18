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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class SoldierboyPlayerAnimationHandler {
   private static final Identifier CONTROLLER_ID = new Identifier("dannys-aot", "soldierboy_chest");
   private static final Identifier ANIM_CHEST_LASER = new Identifier("dannys-aot", "chest_laser");
   private static final Identifier ANIM_NECK_GRAB = new Identifier("dannys-aot", "neck_grab");
   private static final Identifier ANIM_NECK_PUNCH = new Identifier("dannys-aot", "neck_punch");
   private static final int FADE_TICKS = 6;
   private static final int FAST_FADE_TICKS = 2;
   private static final int NECK_PUNCH_DEFAULT_TICKS = 14;
   private static final String[] BONE_NAMES = new String[]{
      "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item", "left_item", "cape", "elytra"
   };
   private static final Map<UUID, SoldierboyPlayerAnimationHandler.AnimState> states = new HashMap<>();
   private static final Set<UUID> NECK_GRAB_ACTIVE = ConcurrentHashMap.newKeySet();

   public static void register() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY
         .registerFactory(CONTROLLER_ID, 104, player -> new PlayerAnimationController(player, SoldierboyPlayerAnimationHandler::predicate));
   }

   public static boolean isInChestAbilityState(UUID uuid) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.player.getUuid().equals(uuid)) {
         return SoldierboyInputHandler.getLocalChargingAbility() >= 0;
      } else {
         return SoldierboyClientHandler.getRemoteCharge(uuid) != null ? true : SoldierboyClientHandler.laserFirers().contains(uuid);
      }
   }

   public static void triggerNeckPunch(AbstractClientPlayerEntity player) {
      SoldierboyPlayerAnimationHandler.AnimState state = states.computeIfAbsent(player.getUuid(), k -> new SoldierboyPlayerAnimationHandler.AnimState());
      long now = player.getWorld().getTime();
      Animation anim = PlayerAnimResources.getAnimation(ANIM_NECK_PUNCH);
      int duration = anim != null ? Math.max(2, (int)Math.ceil(anim.length())) : 14;
      state.oneShot = ANIM_NECK_PUNCH;
      state.oneShotEndTick = now + duration;
   }

   public static void setNeckGrabActive(UUID uuid, boolean active) {
      if (active) {
         NECK_GRAB_ACTIVE.add(uuid);
      } else {
         NECK_GRAB_ACTIVE.remove(uuid);
      }
   }

   public static void clearState(UUID uuid) {
      states.remove(uuid);
      NECK_GRAB_ACTIVE.remove(uuid);
   }

   public static void clearAll() {
      states.clear();
      NECK_GRAB_ACTIVE.clear();
   }

   private static PlayState predicate(AnimationController controller, AnimationData data, AnimationSetter setter) {
      if (!(controller instanceof PlayerAnimationController pac)) {
         return PlayState.STOP;
      } else {
         AbstractClientPlayerEntity player = pac.getPlayer();
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && mc.world != null) {
            boolean isSoldierboy = BloodlineClientData.get(player.getUuid()) == BloodlineType.SOLDIERBOY;
            SoldierboyPlayerAnimationHandler.AnimState state = states.computeIfAbsent(player.getUuid(), k -> new SoldierboyPlayerAnimationHandler.AnimState());
            long now = player.getWorld().getTime();
            Identifier target = null;
            if (isSoldierboy) {
               if (state.oneShot != null && now < state.oneShotEndTick) {
                  target = state.oneShot;
               } else {
                  state.oneShot = null;
                  if (NECK_GRAB_ACTIVE.contains(player.getUuid())) {
                     target = ANIM_NECK_GRAB;
                  } else if (isInChestAbilityState(player.getUuid())) {
                     target = ANIM_CHEST_LASER;
                  }
               }
            }

            if (target == null) {
               if (state.currentAnim != null) {
                  AbstractFadeModifier fadeOut = AbstractFadeModifier.functionalFadeIn(6, (boneName, progress) -> 1.0F - progress);
                  pac.addModifierBefore(fadeOut);
                  state.currentAnim = null;
                  state.fadingOut = true;
                  state.fadeOutEndTick = now + 6L;
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
                  state.currentAnim = target;
                  Map<String, AdvancedBoneSnapshot> snapshots = snapshotBones(pac);
                  Animation anim = PlayerAnimResources.getAnimation(target);
                  if (anim == null) {
                     return PlayState.STOP;
                  }

                  RawAnimation raw = target.equals(ANIM_NECK_PUNCH) ? RawAnimation.begin().thenPlayAndHold(anim) : RawAnimation.begin().thenLoop(anim);
                  pac.forceAnimationReset();
                  setter.setAnimation(raw);
                  applyFadeFromSnapshot(pac, snapshots, fadeTicksFor(target));
               } else if (!pac.isActive()) {
                  Map<String, AdvancedBoneSnapshot> snapshots = snapshotBones(pac);
                  Animation anim = PlayerAnimResources.getAnimation(target);
                  if (anim != null) {
                     RawAnimation raw = target.equals(ANIM_NECK_PUNCH) ? RawAnimation.begin().thenPlayAndHold(anim) : RawAnimation.begin().thenLoop(anim);
                     pac.forceAnimationReset();
                     setter.setAnimation(raw);
                     applyFadeFromSnapshot(pac, snapshots, fadeTicksFor(target));
                  }
               }

               return PlayState.CONTINUE;
            }
         } else {
            return PlayState.STOP;
         }
      }
   }

   private static int fadeTicksFor(Identifier target) {
      return target.equals(ANIM_NECK_PUNCH) ? 2 : 6;
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
      Identifier oneShot = null;
      long oneShotEndTick = 0L;
   }
}
