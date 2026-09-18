package daot;

import daot.network.SoldierboyChargePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class SoldierboyInputHandler {
   public static final int BLAST_CHARGE_DURATION_TICKS = 50;
   public static final int LASER_CHARGE_DURATION_TICKS = 40;
   public static final float MIN_FIRE_THRESHOLD = 0.2F;
   private static int activeAbility = -1;
   private static SoldierboyInputHandler.Mode mode = SoldierboyInputHandler.Mode.IDLE;
   private static long chargeStartTick = 0L;
   private static boolean rHeldLast = false;
   private static boolean gHeldLast = false;

   private SoldierboyInputHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(SoldierboyInputHandler::tick);
   }

   public static float getLocalChargeProgress() {
      if (mode == SoldierboyInputHandler.Mode.IDLE) {
         return 0.0F;
      } else if (mode == SoldierboyInputHandler.Mode.LASER_FIRING) {
         return 1.0F;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player == null) {
            return 0.0F;
         } else {
            long now = mc.player.getWorld().getTime();
            int duration = activeAbility == 1 ? 40 : 50;
            float raw = (float)(now - chargeStartTick) / duration;
            return Math.max(0.0F, Math.min(1.0F, raw));
         }
      }
   }

   public static int getLocalChargingAbility() {
      return activeAbility;
   }

   public static boolean isLocalLaserFiring() {
      return mode == SoldierboyInputHandler.Mode.LASER_FIRING;
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         resetState();
      } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.SOLDIERBOY) {
         resetState();
      } else {
         boolean inGui = mc.currentScreen != null || mc.getWindow() == null;
         boolean rHeld = !inGui && InputUtil.isKeyPressed(mc.getWindow().getHandle(), 82);
         boolean gHeld = !inGui && InputUtil.isKeyPressed(mc.getWindow().getHandle(), 71);
         if (rHeld && !rHeldLast) {
            cancelActive(player);
            startCharge(player, (byte)0);
         }

         if (gHeld && !gHeldLast) {
            cancelActive(player);
            startCharge(player, (byte)1);
         }

         if (!rHeld && rHeldLast && activeAbility == 0) {
            release(player);
         }

         if (!gHeld && gHeldLast && activeAbility == 1) {
            release(player);
         }

         if (mode == SoldierboyInputHandler.Mode.CHARGING || mode == SoldierboyInputHandler.Mode.LASER_FIRING) {
            player.input.movementForward = 0.0F;
            player.input.movementSideways = 0.0F;
            player.input.jumping = false;
            player.input.sneaking = false;
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0.0F;
         }

         if (activeAbility == 1) {
            if (mode == SoldierboyInputHandler.Mode.CHARGING && getLocalChargeProgress() >= 1.0F) {
               mode = SoldierboyInputHandler.Mode.LASER_FIRING;
            }

            if (mode == SoldierboyInputHandler.Mode.LASER_FIRING) {
               Vec3d look = player.getRotationVec(1.0F);
               ClientPlayNetworking.send(new SoldierboyChargePayload((byte)activeAbility, (byte)2, 1.0F, look.x, look.y, look.z));
            }
         }

         rHeldLast = rHeld;
         gHeldLast = gHeld;
      }
   }

   private static void startCharge(ClientPlayerEntity player, byte ability) {
      activeAbility = ability;
      mode = SoldierboyInputHandler.Mode.CHARGING;
      chargeStartTick = player.getWorld().getTime();
      ClientPlayNetworking.send(new SoldierboyChargePayload(ability, (byte)0, 0.0F, 0.0, 0.0, 0.0));
   }

   private static void cancelActive(ClientPlayerEntity player) {
      if (activeAbility >= 0) {
         release(player);
      }
   }

   private static void release(ClientPlayerEntity player) {
      if (activeAbility >= 0) {
         float progress = getLocalChargeProgress();
         Vec3d look = activeAbility == 1 ? player.getRotationVec(1.0F) : Vec3d.ZERO;
         if (activeAbility == 1 && mode == SoldierboyInputHandler.Mode.LASER_FIRING) {
            SoldierboyLaserSoundManager.forceStopLocal(player.getUuid());
         }

         ClientPlayNetworking.send(new SoldierboyChargePayload((byte)activeAbility, (byte)1, progress, look.x, look.y, look.z));
         resetState();
      }
   }

   private static void resetState() {
      activeAbility = -1;
      mode = SoldierboyInputHandler.Mode.IDLE;
      rHeldLast = false;
      gHeldLast = false;
   }

   @Environment(EnvType.CLIENT)
   private static enum Mode {
      IDLE,
      CHARGING,
      LASER_FIRING;
   }
}
