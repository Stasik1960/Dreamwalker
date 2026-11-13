package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.compat.worldedit.SelectionAxe;
import dev.dreamwalker.bloodbornedw.composite.ForcedBlockRemoval;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** WorldEdit has already handled the attack callback before tryBreakBlock. */
@Mixin(value=ServerPlayerInteractionManager.class,priority=2500)
public abstract class CreativeRemovalMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Shadow protected ServerWorld world;
    @Shadow public abstract GameMode getGameMode();

    @WrapMethod(method={"tryBreakBlock","method_14266"},remap=false)
    private boolean dreamwalker$finishCreativeRemoval(BlockPos pos,Operation<Boolean> original) {
        if(SelectionAxe.heldBy(player))return false;
        if(!player.isCreative()||!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)
                ||!world.getWorldBorder().contains(pos)||!world.canPlayerModifyAt(player,pos)
                ||!player.getAbilities().allowModifyWorld||BbToolService.holding(player)
                ||player.isBlockBreakingRestricted(world,pos,getGameMode()))return original.call(pos);
        var before=world.getBlockState(pos);
        if((!ForcedBlockRemoval.isModBlock(before)&&!before.isAir())||!ForcedBlockRemoval.hasCompositeLayers(world,pos)
                ||!player.getMainHandStack().getItem().canMine(before,world,pos,player))
            return original.call(pos);
        boolean result=false;
        try{result=ForcedBlockRemoval.runCreativeBreak(world,pos,player,()->original.call(pos));}
        catch(RuntimeException failure){
            DreamwalkerBb.LOG.debug("Creative removal failed at {}",pos,failure);
        }
        if(!result&&world.getBlockState(pos).equals(before))return false;
        if(!ForcedBlockRemoval.hasDreamWalkerData(world,pos))return true;
        return (ForcedBlockRemoval.isOrphanHelper(world,pos)
                ||world.getBlockState(pos).isAir()&&ForcedBlockRemoval.hasNoLiveOwners(world,pos))
                &&ForcedBlockRemoval.clearAllAt(world,pos,player);
    }
}
