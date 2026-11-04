package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.item.NrpPickaxe;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
abstract class NrpCombatMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void dw$pickaxeIsNotAWeapon(Entity target,CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!player.getMainHandStack().isOf(NrpPickaxe.ITEM)) return;
        if (!player.getWorld().isClient && player.isCreative() && target instanceof RpObjectEntity) {
            // The native RP handler verifies the ray target and permissions before removing decor.
            target.damage(player.getDamageSources().playerAttack(player),0.0F);
        }
        ci.cancel();
    }
}
