package daot.mixin.client;

import daot.GeassClientState;
import daot.network.MindControlSprintPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public class GeassInputLockMixin {
   @Inject(method = "tick", at = @At("TAIL"))
   private void applyGeassInputs(boolean isSneaking, float sneakSpeedModifier, CallbackInfo ci) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         KeyboardInput self = (KeyboardInput)(Object)this;
         if (GeassClientState.isMindControlController) {
            MinecraftClient mcClient = MinecraftClient.getInstance();
            boolean wantsSprint = mcClient.options.sprintKey.isPressed() && self.movementForward > 0.0F;
            boolean wantsShift = mcClient.options.sneakKey.isPressed();
            boolean invOpen = mcClient.currentScreen instanceof InventoryScreen || mcClient.currentScreen instanceof CreativeInventoryScreen;
            GeassClientState.controllerInventoryOpen = invOpen;
            ClientPlayNetworking.send(new MindControlSprintPayload(self.movementForward, self.movementSideways, self.jumping, wantsSprint, wantsShift, invOpen));
         }

         if (GeassClientState.isMindControlTarget) {
            MinecraftClient mcClient = MinecraftClient.getInstance();
            if (GeassClientState.mcInputInventoryOpen) {
               if (!(mcClient.currentScreen instanceof InventoryScreen)) {
                  mcClient.setScreen(new InventoryScreen(mcClient.player));
               }
            } else if (mcClient.currentScreen instanceof InventoryScreen) {
               mcClient.setScreen(null);
            }
         }

         if (GeassClientState.isMindControlTarget) {
            self.pressingForward = GeassClientState.mcInputForward > 0.0F;
            self.pressingBack = GeassClientState.mcInputForward < 0.0F;
            self.pressingLeft = GeassClientState.mcInputStrafe > 0.0F;
            self.pressingRight = GeassClientState.mcInputStrafe < 0.0F;
            self.movementForward = GeassClientState.mcInputForward;
            self.movementSideways = GeassClientState.mcInputStrafe;
            self.jumping = GeassClientState.mcInputJumping;
            self.sneaking = GeassClientState.mcInputShifting;
            if (GeassClientState.mcInputShifting) {
               self.movementForward *= sneakSpeedModifier;
               self.movementSideways *= sneakSpeedModifier;
            }

            player.setYaw(GeassClientState.mcInputYaw);
            player.setPitch(GeassClientState.mcInputPitch);
            player.headYaw = GeassClientState.mcInputYaw;
            if (GeassClientState.mcInputHasPos) {
               double dx = GeassClientState.mcInputPosX - player.getX();
               double dy = GeassClientState.mcInputPosY - player.getY();
               double dz = GeassClientState.mcInputPosZ - player.getZ();
               double distSq = dx * dx + dy * dy + dz * dz;
               if (distSq > 4.0) {
                  player.setPosition(GeassClientState.mcInputPosX, GeassClientState.mcInputPosY, GeassClientState.mcInputPosZ);
               } else if (distSq > 0.01) {
                  player.setPosition(player.getX() + dx * 0.5, player.getY() + dy * 0.5, player.getZ() + dz * 0.5);
               }
            }

            if (GeassClientState.mcInputSwing) {
               player.swingHand(Hand.MAIN_HAND);
               GeassClientState.mcInputSwing = false;
            }

            player.setSprinting(GeassClientState.mcInputSprinting);
            player.getInventory().selectedSlot = GeassClientState.mcInputSelectedSlot;
         } else if (GeassClientState.isControlled()) {
            self.pressingForward = false;
            self.pressingBack = false;
            self.pressingLeft = false;
            self.pressingRight = false;
            self.movementForward = 0.0F;
            self.movementSideways = 0.0F;
            self.jumping = false;
            self.sneaking = false;
            if (GeassClientState.isMovementLocked() && GeassClientState.activeCommand != 4) {
               GeassClientState.applyMovementInputs(self);
            }
         }
      }
   }
}

