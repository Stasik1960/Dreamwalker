package daot.mixin;
import daot.compat.DamageEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LivingEntity.class)
abstract class BackportAfterDamageMixin {
    // Same tail/local contract as Fabric's 1.21 AFTER_DAMAGE event.
    @Inject(method="damage", at=@At("TAIL"), locals=LocalCapture.CAPTURE_FAILHARD)
    private void daot$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
            float baseDamage, boolean blocked) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if (!entity.getWorld().isClient && !entity.isDead()) {
            DamageEvents.AFTER_DAMAGE.invoker().afterDamage(entity, source, baseDamage, amount, blocked);
        }
    }
}
