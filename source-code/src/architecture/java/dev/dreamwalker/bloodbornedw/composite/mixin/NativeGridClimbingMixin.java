package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.NativeGridRuntime;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class NativeGridClimbingMixin {
    @Shadow private Optional<BlockPos> climbingPos;
    @Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
    private void dw$nativeGridClimbing(CallbackInfoReturnable<Boolean> result) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (result.getReturnValue() || entity.isSpectator()) return;
        var pos = NativeGridRuntime.climbing(entity.getWorld(), entity.getBoundingBox());
        if (pos.isPresent()) { climbingPos = pos; result.setReturnValue(true); }
    }
}
