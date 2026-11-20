package com.yharnam.casino;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;

public final class GachaponBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    public GachaponBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.SOUTH).with(HALF,DoubleBlockHalf.LOWER));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b) { b.add(FACING,HALF); }

    @Override public @Nullable BlockState getPlacementState(ItemPlacementContext c) {
        BlockPos upper=c.getBlockPos().up();
        var w=c.getWorld();
        if (!w.isInBuildLimit(upper) || !w.getWorldBorder().contains(upper) || !w.getBlockState(upper).canReplace(c)
            || c.getPlayer()!=null && !w.canPlayerModifyAt(c.getPlayer(),upper)) return null;
        return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());
    }
    @Override public void onPlaced(World w,BlockPos p,BlockState s,@Nullable LivingEntity placer,ItemStack stack) {
        if (w.isClient) return;
        w.setBlockState(p.up(),s.with(HALF,DoubleBlockHalf.UPPER),Block.NOTIFY_ALL);
        if (placer instanceof PlayerEntity player && w.getBlockEntity(p) instanceof GachaponEntity game) game.setFounder(player.getUuid());
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos np) {
        boolean lower=s.get(HALF)==DoubleBlockHalf.LOWER;
        if (d==(lower?Direction.UP:Direction.DOWN) && (!n.isOf(this) || n.get(HALF)==s.get(HALF) || n.get(FACING)!=s.get(FACING)))
            return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(s,d,n,w,p,np);
    }
    public BlockPos base(BlockPos p,BlockState s) { return s.get(HALF)==DoubleBlockHalf.LOWER?p:p.down(); }
    @Override public void onBreak(World w,BlockPos p,BlockState s,PlayerEntity player) {
        if (!w.isClient && s.get(HALF)==DoubleBlockHalf.UPPER && w.getBlockState(p.down()).isOf(this))
            w.breakBlock(p.down(),!player.isCreative(),player);
        super.onBreak(w,p,s,player);
    }
    @Override public void onStateReplaced(BlockState s,World w,BlockPos p,BlockState next,boolean moved) {
        if (w instanceof ServerWorld server && s.get(HALF)==DoubleBlockHalf.LOWER &&
                (!next.isOf(this) || next.get(HALF)!=DoubleBlockHalf.LOWER) && w.getBlockEntity(p) instanceof GachaponEntity game)
            game.onRemoved(server);
        super.onStateReplaced(s,w,p,next,moved);
    }
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s) {
        return s.get(HALF)==DoubleBlockHalf.LOWER ? new GachaponEntity(p,s) : null;
    }
    @Override public void scheduledTick(BlockState state,ServerWorld w,BlockPos p,net.minecraft.util.math.random.Random random) {
        if (w.getBlockEntity(p) instanceof GachaponEntity game) game.advanceClock(w);
    }
    @Override public BlockRenderType getRenderType(BlockState s) { return BlockRenderType.MODEL; }
    @Override public ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit) {
        if (hand!=Hand.MAIN_HAND) return ActionResult.PASS;
        BlockPos base=base(p,s);
        if (player instanceof ServerPlayerEntity server && w.getBlockEntity(base) instanceof GachaponEntity game) {
            var d=hit.getPos().subtract(Vec3d.ofCenter(base));
            var f=s.get(FACING);
            var right=f.rotateYCounterclockwise();
            game.interact(server,new Vec3d(d.x*right.getOffsetX()+d.z*right.getOffsetZ(),d.y+.5,d.x*f.getOffsetX()+d.z*f.getOffsetZ()));
        }
        return ActionResult.success(w.isClient);
    }
    @Override public BlockState rotate(BlockState s,BlockRotation rotation) { return s.with(FACING,rotation.rotate(s.get(FACING))); }
    @Override public BlockState mirror(BlockState s,BlockMirror mirror) { return rotate(s,mirror.getRotation(s.get(FACING))); }
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c) {
        return Block.createCuboidShape(1,0,1,15,s.get(HALF)==DoubleBlockHalf.LOWER?16:15.2,15);
    }
}
