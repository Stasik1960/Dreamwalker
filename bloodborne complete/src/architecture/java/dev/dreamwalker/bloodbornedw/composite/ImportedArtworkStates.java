package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One NEW placing item exposes authored artistic/mount states without duplicate Creative entries. */
public final class ImportedArtworkStates {
    private ImportedArtworkStates(){}
    public static ActionResult use(CompositeRootBlock block,BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand){
        if(!block.spec.simplePhysicalCollision||block instanceof ImportedStairBlock||block.spec.variants.size()<2||!player.isSneaking()||!player.getStackInHand(hand).isEmpty())return ActionResult.PASS;
        if(world.isClient)return ActionResult.SUCCESS;
        var owner=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(world,pos);if(owner==null)return ActionResult.FAIL;
        int next=(CompositeSpec.poseVariant(state)+1)%block.spec.variants.size();
        BlockState changed=state.with(CompositeRootBlock.VARIANT,next%16);
        if(block instanceof ImportedArtworkBlock)changed=changed.with(ImportedArtworkBlock.ART_BANK,next/16);
        if(block instanceof ImportedLadderBlock){var property=changed.getBlock().getStateManager().getProperty("height");if(property!=null)changed=apply(changed,property,next==0?"one":"two");}
        var result=CompositeRuntime.transition((ServerWorld)world,owner,changed,player);
        return result.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED?ActionResult.CONSUME:ActionResult.FAIL;
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,net.minecraft.state.property.Property<T> property,String value){return property.parse(value).map(v->state.with(property,v)).orElse(state);}
}
