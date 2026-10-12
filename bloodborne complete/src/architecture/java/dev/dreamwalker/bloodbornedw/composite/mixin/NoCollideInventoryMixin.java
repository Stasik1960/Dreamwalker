package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear before inventory transactions, drops, swaps, slot changes and creative inventory writes. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class NoCollideInventoryMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method={"onClickSlot","onCreativeInventoryAction","onUpdateSelectedSlot","onPlayerAction"},at=@At("HEAD"))
    private void dreamwalker$forgetMovedItem(CallbackInfo ci){NoCollidePlacement.invalidate(player);}
}
