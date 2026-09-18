package daot.mixin;

import daot.HomelanderFlightServerHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public class HomelanderLaserFireSuppressMixin {
   @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
   private void daot$suppressLaserFireTick(BlockState state, ServerWorld level, BlockPos pos, Random random, CallbackInfo ci) {
      if (HomelanderFlightServerHandler.isLaserFire(level, pos)) {
         ci.cancel();
      }
   }
}
