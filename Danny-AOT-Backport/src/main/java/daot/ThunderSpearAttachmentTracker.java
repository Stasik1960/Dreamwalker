package daot;

import daot.network.ThunderSpearFirePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class ThunderSpearAttachmentTracker {
   private static final int ANIMATION_TICKS = 10;
   private static final int FIRING_ANIMATION_TICKS = 13;
   public static final float PROJECTILE_SPEED = 1.8F;
   public static final int FIRING_HOLDOVER_TICKS = 5;
   private static final int MOUNT_ONLY_TICKS = 60;
   private static ThunderSpearAttachmentTracker.AnimState mainHandState = ThunderSpearAttachmentTracker.AnimState.NONE;
   private static ThunderSpearAttachmentTracker.AnimState offHandState = ThunderSpearAttachmentTracker.AnimState.NONE;
   private static int mainHandTick = 0;
   private static int offHandTick = 0;
   private static int mainHandPrevTick = 0;
   private static int offHandPrevTick = 0;
   private static boolean mainHandHadSpear = false;
   private static boolean offHandHadSpear = false;
   private static boolean mainHandPendingFire = false;
   private static boolean offHandPendingFire = false;

   public static void startLoad(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         mainHandState = ThunderSpearAttachmentTracker.AnimState.LOADING;
         mainHandTick = 0;
         mainHandPrevTick = 0;
      } else {
         offHandState = ThunderSpearAttachmentTracker.AnimState.LOADING;
         offHandTick = 0;
         offHandPrevTick = 0;
      }
   }

   public static void startUnload(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         mainHandState = ThunderSpearAttachmentTracker.AnimState.UNLOADING;
         mainHandTick = 0;
         mainHandPrevTick = 0;
      } else {
         offHandState = ThunderSpearAttachmentTracker.AnimState.UNLOADING;
         offHandTick = 0;
         offHandPrevTick = 0;
      }
   }

   public static void startFire(Hand hand) {
      if (hand == Hand.MAIN_HAND) {
         mainHandState = ThunderSpearAttachmentTracker.AnimState.FIRING;
         mainHandTick = 0;
         mainHandPrevTick = 0;
         mainHandPendingFire = true;
      } else {
         offHandState = ThunderSpearAttachmentTracker.AnimState.FIRING;
         offHandTick = 0;
         offHandPrevTick = 0;
         offHandPendingFire = true;
      }
   }

   public static void instantFire(Hand hand) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         int handId = hand == Hand.MAIN_HAND ? 0 : 1;
         ClientPlayNetworking.send(new ThunderSpearFirePayload(handId, mc.player.getPitch(), mc.player.getYaw()));
         BladeAnimationHandler.triggerThunderSpearFire(hand);
         spawnFlyingSpear(hand);
         if (hand == Hand.MAIN_HAND) {
            mainHandState = ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY;
            mainHandTick = 0;
            mainHandPrevTick = 0;
            mainHandPendingFire = false;
         } else {
            offHandState = ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY;
            offHandTick = 0;
            offHandPrevTick = 0;
            offHandPendingFire = false;
         }
      }
   }

   public static void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         ItemStack mainHand = mc.player.getMainHandStack();
         ItemStack offHand = mc.player.getOffHandStack();
         boolean mainHasSpear = mainHand.getItem() instanceof BladeItem && BladeItem.hasThunderSpear(mainHand);
         boolean offHasSpear = offHand.getItem() instanceof BladeItem && BladeItem.hasThunderSpear(offHand);
         if (mainHasSpear
            && !mainHandHadSpear
            && (mainHandState == ThunderSpearAttachmentTracker.AnimState.NONE || mainHandState == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY)) {
            startLoad(Hand.MAIN_HAND);
         }

         if (offHasSpear
            && !offHandHadSpear
            && (offHandState == ThunderSpearAttachmentTracker.AnimState.NONE || offHandState == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY)) {
            startLoad(Hand.OFF_HAND);
         }

         if (!mainHasSpear
            && mainHandHadSpear
            && mainHandState != ThunderSpearAttachmentTracker.AnimState.FIRING
            && mainHandState != ThunderSpearAttachmentTracker.AnimState.LAUNCHING
            && mainHandState != ThunderSpearAttachmentTracker.AnimState.UNLOADING
            && mainHandState != ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY) {
            startUnload(Hand.MAIN_HAND);
         }

         if (!offHasSpear
            && offHandHadSpear
            && offHandState != ThunderSpearAttachmentTracker.AnimState.FIRING
            && offHandState != ThunderSpearAttachmentTracker.AnimState.LAUNCHING
            && offHandState != ThunderSpearAttachmentTracker.AnimState.UNLOADING
            && offHandState != ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY) {
            startUnload(Hand.OFF_HAND);
         }

         mainHandHadSpear = mainHasSpear;
         offHandHadSpear = offHasSpear;
      }

      mainHandPrevTick = mainHandTick;
      tickHand(true);
      offHandPrevTick = offHandTick;
      tickHand(false);
   }

   private static void tickHand(boolean isMainHand) {
      ThunderSpearAttachmentTracker.AnimState state = isMainHand ? mainHandState : offHandState;
      int tick = isMainHand ? mainHandTick : offHandTick;
      if (state == ThunderSpearAttachmentTracker.AnimState.LOADING
         || state == ThunderSpearAttachmentTracker.AnimState.UNLOADING
         || state == ThunderSpearAttachmentTracker.AnimState.FIRING) {
         tick++;
         int duration = state == ThunderSpearAttachmentTracker.AnimState.FIRING ? 13 : 10;
         if (tick >= duration) {
            if (state == ThunderSpearAttachmentTracker.AnimState.LOADING) {
               state = ThunderSpearAttachmentTracker.AnimState.LOADED;
            } else if (state == ThunderSpearAttachmentTracker.AnimState.FIRING) {
               boolean pending = isMainHand ? mainHandPendingFire : offHandPendingFire;
               if (pending) {
                  MinecraftClient mc = MinecraftClient.getInstance();
                  Hand hand = isMainHand ? Hand.MAIN_HAND : Hand.OFF_HAND;
                  int handId = isMainHand ? 0 : 1;
                  float xRot = mc.player != null ? mc.player.getPitch() : 0.0F;
                  float yRot = mc.player != null ? mc.player.getYaw() : 0.0F;
                  ClientPlayNetworking.send(new ThunderSpearFirePayload(handId, xRot, yRot));
                  spawnFlyingSpear(hand);
                  if (isMainHand) {
                     mainHandPendingFire = false;
                  } else {
                     offHandPendingFire = false;
                  }
               }

               state = ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY;
            } else {
               state = ThunderSpearAttachmentTracker.AnimState.NONE;
            }

            tick = 0;
            if (isMainHand) {
               mainHandPrevTick = 0;
            } else {
               offHandPrevTick = 0;
            }
         }
      } else if (state == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY) {
         if (++tick >= 60) {
            state = ThunderSpearAttachmentTracker.AnimState.NONE;
            tick = 0;
            if (isMainHand) {
               mainHandPrevTick = 0;
            } else {
               offHandPrevTick = 0;
            }
         }
      }

      if (isMainHand) {
         mainHandState = state;
         mainHandTick = tick;
      } else {
         offHandState = state;
         offHandTick = tick;
      }
   }

   private static int getHandId(Hand hand) {
      return hand == Hand.MAIN_HAND ? 0 : 1;
   }

   private static void spawnFlyingSpear(Hand hand) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         boolean mainIsRight = mc.player.getMainArm() == Arm.RIGHT;
         boolean isLeftSide = hand == Hand.MAIN_HAND ? !mainIsRight : mainIsRight;
         Vec3d lookDir = mc.player.getRotationVector();
         Vec3d right = lookDir.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
         Vec3d up = right.crossProduct(lookDir).normalize();
         float sideOffset = isLeftSide ? -0.35F : 0.35F;
         Vec3d startPos = mc.player.getEyePos().add(right.multiply(sideOffset)).add(up.multiply(-0.25)).add(lookDir.multiply(0.6));
         FlyingThunderSpearTracker.spawn(startPos, lookDir, 1.8F, isLeftSide, null);
      }
   }

   public static ThunderSpearAttachmentTracker.AnimState getState(Hand hand) {
      return hand == Hand.MAIN_HAND ? mainHandState : offHandState;
   }

   public static float getProgress(Hand hand, float partialTick) {
      int tick = hand == Hand.MAIN_HAND ? mainHandTick : offHandTick;
      int prevTick = hand == Hand.MAIN_HAND ? mainHandPrevTick : offHandPrevTick;
      ThunderSpearAttachmentTracker.AnimState state = getState(hand);
      if (state != ThunderSpearAttachmentTracker.AnimState.NONE
         && state != ThunderSpearAttachmentTracker.AnimState.LOADED
         && state != ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY) {
         int duration;
         if (state == ThunderSpearAttachmentTracker.AnimState.FIRING) {
            duration = 13;
         } else {
            duration = 10;
         }

         float interpolated = prevTick + (tick - prevTick) * partialTick;
         return Math.min(interpolated / duration, 1.0F);
      } else {
         return state == ThunderSpearAttachmentTracker.AnimState.LOADED ? 1.0F : 0.0F;
      }
   }

   public static boolean shouldRender(Hand hand) {
      ThunderSpearAttachmentTracker.AnimState state = getState(hand);
      return state != ThunderSpearAttachmentTracker.AnimState.NONE;
   }

   public static boolean isInFiringHoldover(Hand hand) {
      ThunderSpearAttachmentTracker.AnimState state = getState(hand);
      int tick = hand == Hand.MAIN_HAND ? mainHandTick : offHandTick;
      return state == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY && tick < 5;
   }

   public static float getHoldoverTime(Hand hand, float partialTick) {
      int tick = hand == Hand.MAIN_HAND ? mainHandTick : offHandTick;
      int prevTick = hand == Hand.MAIN_HAND ? mainHandPrevTick : offHandPrevTick;
      return prevTick + (tick - prevTick) * partialTick;
   }

   public static void endLaunching() {
   }

   public static void endOldestHoldover() {
      boolean mainIsMount = mainHandState == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY;
      boolean offIsMount = offHandState == ThunderSpearAttachmentTracker.AnimState.MOUNT_ONLY;
      if (mainIsMount && offIsMount) {
         if (mainHandTick >= offHandTick) {
            mainHandState = ThunderSpearAttachmentTracker.AnimState.NONE;
            mainHandTick = 0;
            mainHandPrevTick = 0;
         } else {
            offHandState = ThunderSpearAttachmentTracker.AnimState.NONE;
            offHandTick = 0;
            offHandPrevTick = 0;
         }
      } else if (mainIsMount) {
         mainHandState = ThunderSpearAttachmentTracker.AnimState.NONE;
         mainHandTick = 0;
         mainHandPrevTick = 0;
      } else if (offIsMount) {
         offHandState = ThunderSpearAttachmentTracker.AnimState.NONE;
         offHandTick = 0;
         offHandPrevTick = 0;
      }
   }

   @Environment(EnvType.CLIENT)
   public static enum AnimState {
      NONE,
      LOADING,
      LOADED,
      UNLOADING,
      FIRING,
      LAUNCHING,
      MOUNT_ONLY;
   }
}
