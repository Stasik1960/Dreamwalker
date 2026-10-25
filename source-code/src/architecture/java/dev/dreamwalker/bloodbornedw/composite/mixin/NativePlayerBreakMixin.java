package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.dreamwalker.bloodbornedw.composite.NativePlayerBreak;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class NativePlayerBreakMixin {
    @Shadow protected ServerWorld world;
    @Shadow protected ServerPlayerEntity player;
    @WrapMethod(method="tryBreakBlock")
    private boolean dw$breakScope(BlockPos pos,Operation<Boolean> original){
        var scope=NativePlayerBreak.begin(world,player,pos);boolean success=false;
        try{success=original.call(pos);return success;}catch(NativePlayerBreak.Refused refusal){NativePlayerBreak.canceled(scope);return false;}finally{NativePlayerBreak.end(scope,success);}
    }
    @WrapOperation(method="tryBreakBlock",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerWorld;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean dw$atomicRemoval(ServerWorld target,BlockPos pos,boolean move,Operation<Boolean> original){Boolean result=NativePlayerBreak.remove(target,pos);if(result==null)return original.call(target,pos,move);if(!result)throw new NativePlayerBreak.Refused();return true;}
    @WrapOperation(method="tryBreakBlock",at=@At(value="INVOKE",target="Lnet/minecraft/block/Block;afterBreak(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/item/ItemStack;)V"))
    private void dw$singleDrop(Block block,net.minecraft.world.World target,PlayerEntity player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool,Operation<Void> original){
        if(NativePlayerBreak.handled()){player.incrementStat(Stats.MINED.getOrCreateStat(block));player.addExhaustion(.005F);}
        else original.call(block,target,player,pos,state,entity,tool);
    }
}
