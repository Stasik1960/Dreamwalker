package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntity.class)
public abstract class BuilderAttackGuardMixin {
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void dwBuilderDamage(Entity entity,CallbackInfo ci){if(BuildingTool.isHeld((PlayerEntity)(Object)this))ci.cancel();}
}
