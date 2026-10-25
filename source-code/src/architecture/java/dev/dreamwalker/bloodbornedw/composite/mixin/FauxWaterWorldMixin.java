package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging;
import net.minecraft.world.World;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(World.class)
public abstract class FauxWaterWorldMixin {
    @Inject(method="getFluidState",at=@At("RETURN"),cancellable=true)
    private void dw$fauxPhysics(BlockPos pos,CallbackInfoReturnable<FluidState> ci){ci.setReturnValue(FauxWaterlogging.physics((World)(Object)this,pos,ci.getReturnValue()));}
}
