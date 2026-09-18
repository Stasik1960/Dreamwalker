package daot.mixin.client;

import daot.ShifterTitan;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public class ShifterRideInputLockMixin {
   @Unique
   private static boolean daot$pilotingShifter() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player == null ? false : mc.player.getVehicle() instanceof ShifterTitan shifter && !shifter.isDismounting();
   }

   @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
   private void daot$blockAttack(CallbackInfoReturnable<Boolean> cir) {
      if (daot$pilotingShifter()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
   private void daot$blockContinueAttack(boolean holding, CallbackInfo ci) {
      if (daot$pilotingShifter()) {
         ci.cancel();
      }
   }

   @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
   private void daot$blockUseItem(CallbackInfo ci) {
      if (daot$pilotingShifter()) {
         ci.cancel();
      }
   }

   @Inject(method = "handleInputEvents", at = @At("HEAD"))
   private void daot$blockInventoryKeys(CallbackInfo ci) {
      if (daot$pilotingShifter()) {
         MinecraftClient mc = (MinecraftClient)(Object)this;

         while (mc.options.dropKey.wasPressed()) {
         }

         while (mc.options.swapHandsKey.wasPressed()) {
         }

         while (mc.options.inventoryKey.wasPressed()) {
         }
      }
   }
}

