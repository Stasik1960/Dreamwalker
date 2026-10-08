package dev.dreamwalker.bloodbornedw.architecture.ladder_source;

import java.util.List;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.*;

/** Explicit legacy physics role: original beehive's measured full cube, at its fixed cell. */
public final class SourceBackingBlock extends BlockWithEntity {
    public SourceBackingBlock(){super(Settings.create().strength(.4F).nonOpaque().dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new SourceLadderBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    private static boolean shifted(BlockView world,BlockPos pos){if(dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots.worker(world))return dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots.shiftedSourceBacking(world,pos);return dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.loadedEntity(world,pos) instanceof SourceLadderBlockEntity source&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.offset(world,source.root())!=0;}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return shifted(world,pos)?VoxelShapes.empty():VoxelShapes.fullCube();}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return shifted(world,pos)?VoxelShapes.empty():VoxelShapes.fullCube();}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return 0;}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return true;}
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){BlockPos root=SourceLadderRuntime.resolveRoot(world,pos);return root==null?ItemStack.EMPTY:world.getBlockState(root).getBlock().getPickStack(world,root,world.getBlockState(root));}
    @Override public List<ItemStack> getDroppedStacks(BlockState state,LootContextParameterSet.Builder context){return List.of();}
    @Override public void onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player){if(world instanceof ServerWorld server)SourceLadderRuntime.remove(server,pos,player,!player.isCreative());super.onBreak(world,pos,state,player);}
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){if(!next.isOf(this)&&world instanceof ServerWorld server&&!SourceLadderRuntime.writing()&&!dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.writing())SourceLadderRuntime.partReplaced(server,pos,state);super.onStateReplaced(state,world,pos,next,moved);}
}
