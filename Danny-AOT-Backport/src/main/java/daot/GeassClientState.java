package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class GeassClientState {
   public static int activeCommand = 0;
   public static int targetEntityId = -1;
   private static int killPhase = 0;
   private static int cooldownTicks = 0;
   private static int jumpTicks = 0;
   public static boolean isMindControlController = false;
   public static int mindControlTargetEntityId = -1;
   public static float controllerYaw = 0.0F;
   public static float controllerPitch = 0.0F;
   public static boolean isMindControlTarget = false;
   public static float mcInputYaw;
   public static float mcInputPitch;
   public static float prevMcInputYaw;
   public static float prevMcInputPitch;
   public static long mcInputPayloadTimeNano = 0L;
   public static boolean mcInputSwing = false;
   public static float mcInputForward;
   public static float mcInputStrafe;
   public static boolean mcInputJumping;
   public static boolean mcInputSprinting;
   public static boolean mcInputShifting;
   public static int mcInputSelectedSlot = 0;
   public static double mcInputPosX;
   public static double mcInputPosY;
   public static double mcInputPosZ;
   public static boolean mcInputHasPos = false;
   public static boolean mcInputInventoryOpen = false;
   public static boolean controllerInventoryOpen = false;

   public static boolean isControlled() {
      return activeCommand != 0;
   }

   public static boolean isMovementLocked() {
      return activeCommand == 1 || activeCommand == 2 || activeCommand == 4;
   }

   public static boolean isCameraLocked() {
      return activeCommand == 1 || activeCommand == 2 || activeCommand == 4 || activeCommand == 5;
   }

   public static void update(int command, int entityId) {
      if (command == 0) {
         clear();
      } else if (command == 7) {
         isMindControlController = true;
         mindControlTargetEntityId = entityId;
      } else {
         if (command == 2 && activeCommand != 2) {
            killPhase = 0;
            cooldownTicks = 0;
            jumpTicks = 0;
            selectBestWeapon();
         }

         activeCommand = command;
         targetEntityId = entityId;
      }
   }

   public static void clear() {
      activeCommand = 0;
      targetEntityId = -1;
      killPhase = 0;
      isMindControlController = false;
      mindControlTargetEntityId = -1;
      isMindControlTarget = false;
      mcInputSwing = false;
      mcInputPayloadTimeNano = 0L;
      mcInputForward = 0.0F;
      mcInputStrafe = 0.0F;
      mcInputJumping = false;
      mcInputSprinting = false;
      mcInputShifting = false;
      mcInputSelectedSlot = 0;
      mcInputHasPos = false;
   }

   public static void applyMindControlInput(
      float yaw,
      float pitch,
      float forward,
      float strafe,
      boolean jumping,
      boolean sprinting,
      boolean shifting,
      int selectedSlot,
      double posX,
      double posY,
      double posZ
   ) {
      isMindControlTarget = true;
      if (mcInputPayloadTimeNano == 0L) {
         prevMcInputYaw = mcInputYaw;
         prevMcInputPitch = mcInputPitch;
         mcInputYaw = yaw;
         mcInputPitch = pitch;
         mcInputPayloadTimeNano = System.nanoTime();
      }

      mcInputForward = forward;
      mcInputStrafe = strafe;
      mcInputJumping = jumping;
      mcInputSprinting = sprinting;
      mcInputShifting = shifting;
      mcInputSelectedSlot = selectedSlot;
      mcInputPosX = posX;
      mcInputPosY = posY;
      mcInputPosZ = posZ;
      mcInputHasPos = true;
   }

   public static float[] getDesiredRotation() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && targetEntityId != -1) {
         Entity target = player.getWorld().getEntityById(targetEntityId);
         if (target == null) {
            return null;
         } else {
            float partialTick = mc.getTickDelta();
            double tx = MathHelper.lerp((double)partialTick, target.lastRenderX, target.getX());
            double tz = MathHelper.lerp((double)partialTick, target.lastRenderZ, target.getZ());
            double ty = MathHelper.lerp((double)partialTick, target.lastRenderY, target.getY()) + target.getStandingEyeHeight();
            double dx = tx - player.getX();
            double dz = tz - player.getZ();
            double dy = ty - player.getEyeY();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float desiredYaw = (float)(Math.atan2(-dx, dz) * (180.0 / Math.PI));
            float desiredPitch = (float)(-Math.atan2(dy, Math.max(dist, 0.1)) * (180.0 / Math.PI));
            float currentYaw = player.getYaw();
            float currentPitch = player.getPitch();
            float yawDiff = MathHelper.wrapDegrees(desiredYaw - currentYaw);
            float pitchDiff = desiredPitch - currentPitch;
            float smoothFactor = 0.4F;
            float newYaw = currentYaw + yawDiff * smoothFactor;
            float newPitch = currentPitch + pitchDiff * smoothFactor;
            return new float[]{newYaw, newPitch};
         }
      } else {
         return null;
      }
   }

   public static void applyMovementInputs(KeyboardInput input) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && targetEntityId != -1) {
         Entity target = player.getWorld().getEntityById(targetEntityId);
         if (target != null) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (activeCommand == 1) {
               if (dist > 2.0) {
                  input.pressingForward = true;
                  input.movementForward = 1.0F;
                  if (dist > 4.0 && player.isOnGround()) {
                     player.setSprinting(true);
                     input.jumping = true;
                  }
               }
            } else if (activeCommand == 2) {
               tickKill(input, player, target, dist);
            }
         }
      }
   }

   private static void tickKill(KeyboardInput input, ClientPlayerEntity player, Entity victim, double dist) {
      MinecraftClient mc = MinecraftClient.getInstance();
      switch (killPhase) {
         case 0:
            if (dist > 3.0) {
               input.pressingForward = true;
               input.movementForward = 1.0F;
               player.setSprinting(true);
               if (player.isOnGround()) {
                  input.jumping = true;
               }
            } else {
               player.setSprinting(false);
               killPhase = 1;
               jumpTicks = 0;
            }
            break;
         case 1:
            player.setSprinting(false);
            if (player.isOnGround()) {
               input.jumping = true;
               input.pressingForward = true;
               input.movementForward = 0.3F;
               jumpTicks = 0;
               killPhase = 2;
            }
            break;
         case 2:
            jumpTicks++;
            if (dist > 1.0) {
               input.pressingForward = true;
               input.movementForward = 0.2F;
            }

            if (jumpTicks >= 4 && !player.isOnGround() && player.fallDistance > 0.0F && player.getVelocity().y < 0.0 && dist < 4.5) {
               if (mc.interactionManager != null && victim instanceof LivingEntity) {
                  player.setSprinting(false);
                  mc.interactionManager.attackEntity(player, victim);
                  player.swingHand(Hand.MAIN_HAND);
               }

               killPhase = 3;
               cooldownTicks = 20;
            }

            if (jumpTicks > 3 && player.isOnGround()) {
               killPhase = 0;
            }
            break;
         case 3:
            cooldownTicks--;
            if (dist > 3.0) {
               input.pressingForward = true;
               input.movementForward = 0.5F;
            }

            if (cooldownTicks <= 0) {
               killPhase = 0;
            }
      }
   }

   private static void selectBestWeapon() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof SwordItem) {
               mc.player.getInventory().selectedSlot = i;
               return;
            }
         }
      }
   }
}
