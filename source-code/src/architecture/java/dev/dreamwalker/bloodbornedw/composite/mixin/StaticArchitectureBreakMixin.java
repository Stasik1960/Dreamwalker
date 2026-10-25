package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=World.class,priority=1200)
public abstract class StaticArchitectureBreakMixin {
    @Inject(method="breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
    private void dw$staticWholeBreak(BlockPos pos,boolean drop,Entity entity,int depth,CallbackInfoReturnable<Boolean> result){
        if(CompositeRuntime.writing()||!((Object)this instanceof ServerWorld world)||!(world.getBlockState(pos).getBlock() instanceof StaticArchitectureBlock))return;
        result.setReturnValue(StaticArchitectureRuntime.breakAt(world,pos,entity instanceof PlayerEntity player?player:null,drop).outcome()==TransactionCore.Outcome.COMMITTED);
    }
}
