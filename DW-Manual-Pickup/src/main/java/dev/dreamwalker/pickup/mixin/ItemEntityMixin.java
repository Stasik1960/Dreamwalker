package dev.dreamwalker.pickup.mixin;

import dev.dreamwalker.pickup.PickupService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void dwManualPickup$blockAutomaticPickup(PlayerEntity player, CallbackInfo ci) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (!item.getWorld().isClient && !PickupService.isPermitted(item, player)) ci.cancel();
    }
}
