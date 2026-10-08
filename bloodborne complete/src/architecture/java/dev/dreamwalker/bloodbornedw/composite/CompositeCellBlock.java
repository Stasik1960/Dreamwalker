package dev.dreamwalker.bloodbornedw.composite;

import java.util.List;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class CompositeCellBlock extends BlockWithEntity {
    public CompositeCellBlock(){super(Settings.create().strength(.4F).nonOpaque().dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CompositeBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return CompositeRuntime.cellShape(world,pos,true,state,context);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return CompositeRuntime.cellShape(world,pos,false,state);}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return 0;}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return true;}
    @Override public List<ItemStack> getDroppedStacks(BlockState state,LootContextParameterSet.Builder context){return List.of();}
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){return CompositeRuntime.defaultPick(world,pos,state);}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){return CompositeRuntime.use(world,pos,player);}
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){if(!state.isOf(next.getBlock())&&!CompositeRuntime.writing()&&world instanceof ServerWorld server)CompositeRuntime.scheduleCleanup(server,pos);super.onStateReplaced(state,world,pos,next,moved);}
}
