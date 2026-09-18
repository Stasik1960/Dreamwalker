package daot.mixin.client;

import daot.HandcuffsTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public class HandcuffedMovementMixin {
   @Inject(method = "tick", at = @At("TAIL"))
   private void blockInputWhenCuffed(boolean isSneaking, float sneakSpeedModifier, CallbackInfo ci) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         boolean cuffed = HandcuffsTracker.isClientCuffed(player.getUuid()) || player.getCommandTags().contains("handcuffed");
         if (cuffed) {
            KeyboardInput self = (KeyboardInput)(Object)this;
            self.pressingForward = false;
            self.pressingBack = false;
            self.pressingLeft = false;
            self.pressingRight = false;
            self.movementForward = 0.0F;
            self.movementSideways = 0.0F;
            self.jumping = false;
         }
      }
   }
}

