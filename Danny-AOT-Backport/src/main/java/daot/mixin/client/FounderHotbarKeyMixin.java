package daot.mixin.client;

import daot.FounderAbilityBarState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public class FounderHotbarKeyMixin {
   @Inject(method = "handleInputEvents", at = @At("HEAD"))
   private void dannysaot$suppressHotbarSlotsForFounderBar(CallbackInfo ci) {
      if (FounderAbilityBarState.isActive()) {
         MinecraftClient mc = (MinecraftClient)(Object)this;

         for (KeyBinding key : mc.options.hotbarKeys) {
            while (key.wasPressed()) {
            }
         }
      }
   }
}

