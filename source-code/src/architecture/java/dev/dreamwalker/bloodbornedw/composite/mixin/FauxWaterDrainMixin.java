package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Waterloggable.class)
public interface FauxWaterDrainMixin {
    @Inject(method="tryDrainFluid",at=@At("HEAD"),cancellable=true)
    private void dw$noRealBucket(WorldAccess world,BlockPos pos,BlockState state,CallbackInfoReturnable<ItemStack> ci){if(FauxWaterlogging.logged(world,pos))ci.setReturnValue(ItemStack.EMPTY);}
}
