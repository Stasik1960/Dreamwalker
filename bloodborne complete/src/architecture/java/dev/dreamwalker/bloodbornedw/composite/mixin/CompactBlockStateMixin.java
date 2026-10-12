package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Do not repeatedly ask millions of palette cubes to create a deliberately absent BE. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class CompactBlockStateMixin {
    @Inject(method="hasBlockEntity",at=@At("HEAD"),cancellable=true)
    private void dreamwalker$compactPaletteOnly(CallbackInfoReturnable<Boolean> result){
        BlockState state=(BlockState)(Object)this;
        if(state.getBlock() instanceof ImportedCompactBlock block&&block.spec!=null&&ImportedCompactBlock.compact(state))result.setReturnValue(false);
    }
}
