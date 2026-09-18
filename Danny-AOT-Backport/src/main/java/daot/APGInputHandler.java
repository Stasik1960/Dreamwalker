package daot;

import daot.network.APGAimUpdatePayload;
import daot.network.APGFirePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class APGInputHandler {
   private static boolean wasAttackDown = false;
   private static long lastFireTick = -1000L;
   private static final int FIRE_COOLDOWN_TICKS = 8;
   private static boolean lastSentMainAim = false;
   private static boolean lastSentOffAim = false;
   public static final int FIRE_MASK_MAIN = 1;
   public static final int FIRE_MASK_OFF = 2;
   private static final double DUAL_FIRE_PUSH = 1.1;

   public static void register() {
      ClientTickEvents.START_CLIENT_TICK.register(APGInputHandler::tick);
   }

   private static void tick(MinecraftClient mc) {
      if (mc.player != null && mc.currentScreen == null) {
         ClientPlayerEntity player = mc.player;
         ItemStack main = player.getMainHandStack();
         ItemStack off = player.getOffHandStack();
         boolean holdingApg = main.getItem() == DannysAot.APG_GUN || off.getItem() == DannysAot.APG_GUN;
         boolean attackRising = false;
         if (holdingApg) {
            while (mc.options.attackKey.wasPressed()) {
               attackRising = true;
            }
         }

         boolean useDown = mc.options.useKey.isPressed();
         boolean wantAim = useDown && !player.isSneaking();
         boolean mainEligible = isHandAimEligible(player, Hand.MAIN_HAND);
         boolean offEligible = isHandAimEligible(player, Hand.OFF_HAND);
         applyAimState(Hand.MAIN_HAND, wantAim && mainEligible);
         applyAimState(Hand.OFF_HAND, wantAim && offEligible);
         syncAimStateToServer();
         if (BladeAnimationHandler.isAiming(Hand.MAIN_HAND) || BladeAnimationHandler.isAiming(Hand.OFF_HAND)) {
            while (mc.options.useKey.wasPressed()) {
            }
         }

         boolean attackDown = mc.options.attackKey.isPressed();
         boolean rising = attackDown && !wasAttackDown;
         wasAttackDown = attackDown;
         int fireMask = 0;
         if (BladeAnimationHandler.isAiming(Hand.MAIN_HAND) && APGGunItem.isLoaded(player.getMainHandStack())) {
            fireMask |= 1;
         }

         if (BladeAnimationHandler.isAiming(Hand.OFF_HAND) && APGGunItem.isLoaded(player.getOffHandStack())) {
            fireMask |= 2;
         }

         if (fireMask != 0) {
            if (rising || attackRising) {
               long now = player.getWorld().getTime();
               if (now - lastFireTick >= 8L) {
                  lastFireTick = now;
                  fire(mc, player, fireMask);
               }
            }
         }
      } else {
         BladeAnimationHandler.stopAllAiming();
         wasAttackDown = false;
         syncAimStateToServer();
      }
   }

   private static boolean isHandAimEligible(ClientPlayerEntity player, Hand hand) {
      ItemStack stack = player.getStackInHand(hand);
      if (stack.getItem() != DannysAot.APG_GUN) {
         return false;
      } else {
         HookPoint left = ODMTickHandler.getLeftHook();
         HookPoint right = ODMTickHandler.getRightHook();
         boolean leftActive = left != null && left.active;
         boolean rightActive = right != null && right.active;
         if (leftActive && rightActive) {
            return true;
         } else {
            HookPoint mine = hookForHand(player, hand);
            return mine == null ? true : !mine.active;
         }
      }
   }

   private static HookPoint hookForHand(ClientPlayerEntity player, Hand hand) {
      boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
      boolean isRightSide = hand == Hand.MAIN_HAND ? mainIsRight : !mainIsRight;
      return isRightSide ? ODMTickHandler.getRightHook() : ODMTickHandler.getLeftHook();
   }

   private static void applyAimState(Hand hand, boolean shouldAim) {
      if (shouldAim) {
         if (!BladeAnimationHandler.isAiming(hand)) {
            BladeAnimationHandler.startAiming(hand);
         }
      } else if (BladeAnimationHandler.isAiming(hand)) {
         BladeAnimationHandler.stopAiming(hand);
      }
   }

   private static void syncAimStateToServer() {
      boolean main = BladeAnimationHandler.isAiming(Hand.MAIN_HAND);
      boolean off = BladeAnimationHandler.isAiming(Hand.OFF_HAND);
      if (main != lastSentMainAim || off != lastSentOffAim) {
         lastSentMainAim = main;
         lastSentOffAim = off;
         if (ClientPlayNetworking.canSend(APGAimUpdatePayload.TYPE)) {
            ClientPlayNetworking.send(new APGAimUpdatePayload(main, off));
         }
      }
   }

   private static void fire(MinecraftClient mc, ClientPlayerEntity player, int fireMask) {
      Vec3d eye = player.getCameraPosVec(1.0F);
      HookPoint left = ODMTickHandler.getLeftHook();
      HookPoint right = ODMTickHandler.getRightHook();
      boolean doubleHooked = left != null && left.active && left.position != null && right != null && right.active && right.position != null;
      Vec3d fireDir;
      if (doubleHooked) {
         Vec3d toLeft = left.position.subtract(eye).normalize();
         Vec3d toRight = right.position.subtract(eye).normalize();
         Vec3d sum = toLeft.add(toRight);
         fireDir = sum.lengthSquared() < 1.0E-6 ? player.getRotationVec(1.0F) : sum.normalize();
      } else {
         fireDir = player.getRotationVec(1.0F);
      }

      float fireYaw = (float)Math.toDegrees(Math.atan2(-fireDir.x, fireDir.z));
      float firePitch = (float)Math.toDegrees(-Math.asin(fireDir.y));
      ClientPlayNetworking.send(new APGFirePayload(firePitch, fireYaw, fireMask));
      float shotPitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
      ODMSoundManager.playLocalOneShot(ModSounds.GUN_SHOOT_APG, 1.0F, shotPitch);
      float shot2Pitch = 1.1F + player.getRandom().nextFloat() * 0.1F;
      ODMSoundManager.playLocalOneShot(ModSounds.GUN_SHOOT_APG2, 0.3F, shot2Pitch);
      ODMSoundManager.playLocalOneShot(ModSounds.TRIGGER, 0.6F, 1.0F);
      int handsFiring = Integer.bitCount(fireMask);
      CameraShakeHandler.triggerFastShake(1.1F + 0.4F * (handsFiring - 1));
      CameraShakeHandler.triggerGunRecoilDip(handsFiring == 2 ? 26.0F : 14.0F);
      if ((fireMask & 1) != 0) {
         BladeAnimationHandler.triggerApgFireRecoil(Hand.MAIN_HAND);
      }

      if ((fireMask & 2) != 0) {
         BladeAnimationHandler.triggerApgFireRecoil(Hand.OFF_HAND);
      }

      if (handsFiring == 2) {
         double upBoost = 0.25;
         Vec3d push = fireDir.multiply(-1.1).add(0.0, upBoost, 0.0);
         Vec3d current = player.getVelocity();
         player.setVelocity(current.x + push.x, current.y + push.y, current.z + push.z);
         player.velocityDirty = true;
      }

      Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
      Vec3d rightVec = fireDir.crossProduct(worldUp);
      if (rightVec.lengthSquared() < 1.0E-6) {
         rightVec = new Vec3d(1.0, 0.0, 0.0);
      } else {
         rightVec = rightVec.normalize();
      }

      boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
      if ((fireMask & 1) != 0) {
         spawnMuzzle(mc, player, fireDir, rightVec, mainIsRight ? 1 : -1);
      }

      if ((fireMask & 2) != 0) {
         spawnMuzzle(mc, player, fireDir, rightVec, mainIsRight ? -1 : 1);
      }
   }

   private static void spawnMuzzle(MinecraftClient mc, ClientPlayerEntity player, Vec3d look, Vec3d right, int sideSign) {
      double offX = right.x * 0.35 * sideSign + look.x * 0.5;
      double offY = right.y * 0.35 * sideSign + look.y * 0.5 - 0.15;
      double offZ = right.z * 0.35 * sideSign + look.z * 0.5;
      double px = player.getX() + offX;
      double py = player.getEyeY() + offY;
      double pz = player.getZ() + offZ;

      for (int i = 0; i < 5; i++) {
         mc.world
            .addParticle(
               ParticleTypes.SMOKE,
               px,
               py,
               pz,
               look.x * 0.05 + (player.getRandom().nextDouble() - 0.5) * 0.02,
               look.y * 0.05 + (player.getRandom().nextDouble() - 0.5) * 0.02,
               look.z * 0.05 + (player.getRandom().nextDouble() - 0.5) * 0.02
            );
      }

      mc.world.addParticle(ParticleTypes.FLAME, px, py, pz, look.x * 0.1, look.y * 0.1, look.z * 0.1);
   }
}
