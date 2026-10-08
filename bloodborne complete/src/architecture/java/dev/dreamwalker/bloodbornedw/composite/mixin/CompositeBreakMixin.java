package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class CompositeBreakMixin {
    @Inject(method="breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
    private void bloodborne$wholeBreak(BlockPos pos,boolean drop,Entity entity,int depth,CallbackInfoReturnable<Boolean> result){if(CompositeRuntime.writing()||!((Object)this instanceof ServerWorld world))return;PlayerEntity player=entity instanceof PlayerEntity actor?actor:null;var owner=player==null?null:CompositeRuntime.target(world,pos,player);if(owner==null)owner=CompositeRuntime.soleNativeOwner(world,pos);if(owner!=null)result.setReturnValue(CompositeRuntime.remove(world,owner,player,drop&&(player==null||!player.getAbilities().creativeMode)).outcome()==TransactionCore.Outcome.COMMITTED);}
}
