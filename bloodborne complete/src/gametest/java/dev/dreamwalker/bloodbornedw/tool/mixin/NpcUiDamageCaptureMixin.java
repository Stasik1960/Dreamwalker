package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.gametest.NpcBossBarV127GameTests;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only observation. Never cancels damage or changes health; absent from production JAR. */
@Mixin(LivingEntity.class)
public abstract class NpcUiDamageCaptureMixin {
    @Inject(method="damage",at=@At("HEAD"))
    private void bloodborne_dw$npcUiDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){
        if((Object)this instanceof RpMobEntity mob)NpcBossBarV127GameTests.captureDamage(mob,source,amount);
    }
}
