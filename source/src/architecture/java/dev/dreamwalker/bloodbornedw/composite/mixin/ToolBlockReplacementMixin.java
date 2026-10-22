package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.tool.ToolCommands;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Destroying/replacing an ordinary hooked block cannot leave commands on a new block. */
@Mixin(World.class)
public abstract class ToolBlockReplacementMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("RETURN"))
    private void dreamwalker$clearReplacedCommands(BlockPos pos,BlockState next,int flags,int depth,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValue()&&(Object)this instanceof ServerWorld world)ToolCommands.blockChanged(world,pos,next);
    }
}
