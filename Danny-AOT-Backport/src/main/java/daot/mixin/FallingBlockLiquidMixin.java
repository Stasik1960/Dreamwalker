package daot.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockLiquidMixin {
   @Shadow
   private BlockState block;
   @Shadow
   public int timeFalling;

   @Inject(method = "tick", at = @At("HEAD"))
   private void removeLiquidInLiquid(CallbackInfo ci) {
      if (this.timeFalling > 20) {
         FallingBlockEntity self = (FallingBlockEntity)(Object)this;
         if (this.block != null && this.block.getBlock() instanceof FluidBlock) {
            BlockPos pos = self.getBlockPos();
            if (self.getWorld() != null && !self.getWorld().isClient()) {
               FluidState fluid = self.getWorld().getFluidState(pos);
               if (!fluid.isEmpty()) {
                  self.discard();
               }
            }
         }
      }
   }
}

