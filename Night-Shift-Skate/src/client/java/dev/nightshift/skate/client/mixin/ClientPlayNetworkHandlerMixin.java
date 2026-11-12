package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.SkateboardEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {
    @Shadow @Final private MinecraftClient client;

    @ModifyArgs(method = "onEntityPassengersSet", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/text/MutableText;"))
    private void nightShift$showSkateControls(Args args) {
        if (client.player != null && client.player.getVehicle() instanceof SkateboardEntity
                && "mount.onboard".equals(args.get(0))) {
            GameOptions keys = client.options;
            args.set(0, "night_shift_skate.riding");
            args.set(1, new Object[]{keys.sneakKey.getBoundKeyLocalizedText(), keys.useKey.getBoundKeyLocalizedText(),
                    keys.jumpKey.getBoundKeyLocalizedText(), Text.keybind("key.night_shift_skate.trick")});
        }
    }
}
