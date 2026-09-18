package daot;

import daot.network.HomelanderFlightInputPayload;
import daot.network.HomelanderFlyTogglePayload;
import daot.network.HomelanderNoclipPayload;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class HomelanderFlightHandler {
   private static final double MAX_SPEED = 0.85;
   private static final double SUPER_FLY_MULTIPLIER = 2.0;
   public static final double NOCLIP_THRESHOLD = 2.4225;
   private static final double ACCEL_LERP = 0.18;
   private static final double DECEL_LERP = 0.09;
   private static final Set<UUID> flyingPlayers = ConcurrentHashMap.newKeySet();
   private static boolean localFlightWanted = false;
   private static boolean wasRKeyDown = false;
   private static boolean wasFlightWantedLastTick = false;
   private static Vec3d currentVelocity = Vec3d.ZERO;
   private static boolean lastNoclipIntentSent = false;
   private static byte lastInputBitsSent = 0;
   private static boolean inputBitsEverSent = false;
   private static int superFlySustainedTicks = 0;
   private static final int SUPER_FLY_RAMP_TICKS = 100;
   private static final int FLY_START_BOOST_DELAY_TICKS = 8;
   private static final double FLY_START_BOOST_VELOCITY = 5.4;
   private static long flyStartBoostTick = 0L;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderFlightHandler::tick);
   }

   public static boolean isFlying(UUID uuid) {
      return flyingPlayers.contains(uuid);
   }

   public static void onRemoteFlyState(UUID uuid, boolean flying, boolean initialTakeoff) {
      if (flying) {
         flyingPlayers.add(uuid);
         if (initialTakeoff) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || uuid.equals(mc.player.getUuid())) {
               return;
            }

            if (mc.world == null) {
               return;
            }

            if (mc.world.getPlayerByUuid(uuid) instanceof AbstractClientPlayerEntity acp) {
               HomelanderPlayerAnimationHandler.triggerFlyStart(acp);
            }
         }
      } else {
         flyingPlayers.remove(uuid);
         HomelanderPlayerAnimationHandler.clearRemoteInputBits(uuid);
      }
   }

   private static void tick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         wasRKeyDown = false;
         wasFlightWantedLastTick = false;
         currentVelocity = Vec3d.ZERO;
         flyStartBoostTick = 0L;
         lastNoclipIntentSent = false;
         lastInputBitsSent = 0;
         inputBitsEverSent = false;
      } else {
         boolean isHomelander = BloodlineClientData.get(player.getUuid()) == BloodlineType.HOMELANDER;
         if (ModEffects.isPowerDisabled(player)) {
            isHomelander = false;
         }

         if (!isHomelander) {
            if (localFlightWanted) {
               localFlightWanted = false;
               flyingPlayers.remove(player.getUuid());
               ClientPlayNetworking.send(new HomelanderFlyTogglePayload(false));
            }

            clearNoclipIntent();
            clearInputBitsIntent();
            wasRKeyDown = false;
            wasFlightWantedLastTick = false;
            currentVelocity = Vec3d.ZERO;
            superFlySustainedTicks = 0;
            flyStartBoostTick = 0L;
            SonicBoomHandler.reset();
         } else {
            boolean rDown = client.currentScreen == null && client.getWindow() != null && InputUtil.isKeyPressed(client.getWindow().getHandle(), 82);
            if (rDown && !wasRKeyDown) {
               localFlightWanted = !localFlightWanted;
               if (localFlightWanted) {
                  flyingPlayers.add(player.getUuid());
                  HomelanderPlayerAnimationHandler.triggerFlyStart(player);
                  flyStartBoostTick = player.getWorld().getTime() + 8L;
               } else {
                  flyingPlayers.remove(player.getUuid());
                  flyStartBoostTick = 0L;
               }

               ClientPlayNetworking.send(new HomelanderFlyTogglePayload(localFlightWanted));
            }

            wasRKeyDown = rDown;
            if (!localFlightWanted) {
               if (wasFlightWantedLastTick) {
                  if (!player.isCreative() && !player.isSpectator()) {
                     player.getAbilities().flying = false;
                     player.sendAbilitiesUpdate();
                  }

                  currentVelocity = Vec3d.ZERO;
                  superFlySustainedTicks = 0;
                  flyStartBoostTick = 0L;
                  SonicBoomHandler.reset();
                  if (player.noClip) {
                     player.noClip = false;
                  }

                  clearNoclipIntent();
                  clearInputBitsIntent();
               }

               wasFlightWantedLastTick = false;
            } else {
               if (!player.getAbilities().flying) {
                  player.getAbilities().flying = true;
                  player.sendAbilitiesUpdate();
               }

               if (flyStartBoostTick != 0L) {
                  long now = player.getWorld().getTime();
                  if (now < flyStartBoostTick) {
                     currentVelocity = Vec3d.ZERO;
                     player.setVelocity(Vec3d.ZERO);
                     player.fallDistance = 0.0F;
                     wasFlightWantedLastTick = true;
                     return;
                  }

                  currentVelocity = new Vec3d(0.0, 5.4, 0.0);
                  flyStartBoostTick = 0L;
               }

               float fwd = player.input.movementForward;
               float strafe = player.input.movementSideways;
               boolean jumping = player.input.jumping;
               boolean shifting = player.input.sneaking;
               Vec3d look = player.getRotationVec(1.0F);
               double yawRad = Math.toRadians(player.getYaw());
               Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
               Vec3d bodyUp = right.crossProduct(look).normalize();
               double vertical = (jumping ? 1 : 0) - 0;
               Vec3d target = look.multiply(fwd).add(right.multiply(-strafe)).add(bodyUp.multiply(vertical));
               if (target.lengthSquared() > 1.0) {
                  target = target.normalize();
               }

               boolean sprinting = client.options.sprintKey.isPressed();
               double topSpeed;
               if (sprinting) {
                  superFlySustainedTicks = Math.min(superFlySustainedTicks + 1, 100);
                  double rampMul = 1.0 + 0.5 * (superFlySustainedTicks / 100.0);
                  topSpeed = 1.7 * rampMul * SonicBoomHandler.getMultiplier();
               } else {
                  superFlySustainedTicks = 0;
                  SonicBoomHandler.reset();
                  topSpeed = 0.85;
               }

               target = target.multiply(topSpeed);
               if (shifting) {
                  target = target.add(0.0, -topSpeed, 0.0);
               }

               double lerp = target.lengthSquared() >= currentVelocity.lengthSquared() ? 0.18 : 0.09;
               currentVelocity = currentVelocity.add(target.subtract(currentVelocity).multiply(lerp));
               if (currentVelocity.lengthSquared() < 1.0E-6) {
                  currentVelocity = Vec3d.ZERO;
               }

               boolean shouldNoclip = shouldNoclipNow(sprinting);
               if (player.noClip != shouldNoclip) {
                  player.noClip = shouldNoclip;
               }

               if (shouldNoclip != lastNoclipIntentSent) {
                  ClientPlayNetworking.send(new HomelanderNoclipPayload(shouldNoclip));
                  lastNoclipIntentSent = shouldNoclip;
               }

               byte inputBits = packInputBits(fwd, strafe, jumping, shifting, sprinting);
               if (!inputBitsEverSent || inputBits != lastInputBitsSent) {
                  ClientPlayNetworking.send(new HomelanderFlightInputPayload(inputBits));
                  lastInputBitsSent = inputBits;
                  inputBitsEverSent = true;
               }

               player.setVelocity(currentVelocity);
               player.fallDistance = 0.0F;
               wasFlightWantedLastTick = true;
            }
         }
      }
   }

   private static void clearNoclipIntent() {
      if (lastNoclipIntentSent) {
         ClientPlayNetworking.send(new HomelanderNoclipPayload(false));
         lastNoclipIntentSent = false;
      }
   }

   private static void clearInputBitsIntent() {
      if (inputBitsEverSent && lastInputBitsSent != 0) {
         ClientPlayNetworking.send(new HomelanderFlightInputPayload((byte)0));
      }

      lastInputBitsSent = 0;
      inputBitsEverSent = false;
   }

   private static byte packInputBits(double fwd, double strafe, boolean jumping, boolean shifting, boolean sprinting) {
      int bits = 0;
      if (fwd > 0.05) {
         bits |= 1;
      } else if (fwd < -0.05) {
         bits |= 2;
      }

      if (strafe > 0.05) {
         bits |= 4;
      } else if (strafe < -0.05) {
         bits |= 8;
      }

      if (jumping) {
         bits |= 16;
      }

      if (shifting) {
         bits |= 32;
      }

      if (sprinting) {
         bits |= 64;
      }

      return (byte)bits;
   }

   public static boolean shouldNoclipNow(boolean sprinting) {
      if (!sprinting) {
         return false;
      } else {
         boolean atPeakRamp = superFlySustainedTicks >= 100;
         boolean boomed = SonicBoomHandler.getMultiplier() > 1.0001;
         return atPeakRamp || boomed;
      }
   }
}
