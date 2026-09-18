package daot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class RegimentBannerBlock extends Block implements BlockEntityProvider {
   public static final IntProperty ROTATION = Properties.ROTATION;
   private static final VoxelShape SHAPE = Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);

   public RegimentBannerBlock() {
      super(Settings.create().strength(1.0F).nonOpaque().noCollision());
      this.setDefaultState(this.stateManager.getDefaultState().with(ROTATION, 0));
   }

   @Override
   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      return SHAPE;
   }

   @Override
   public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
      return world.getBlockState(pos.down()).isSolid();
   }

   @Override
   public BlockState getStateForNeighborUpdate(
      BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
   ) {
      return direction == Direction.DOWN && !this.canPlaceAt(state, world, pos)
         ? Blocks.AIR.getDefaultState()
         : super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
   }

   @Override
   public BlockState getPlacementState(ItemPlacementContext ctx) {
      return this.getDefaultState().with(ROTATION, MathHelper.floor(ctx.getPlayerYaw() * 16.0F / 360.0F + 0.5) & 15);
   }

   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(ROTATION);
   }

   @Override
   public BlockState rotate(BlockState state, BlockRotation rotation) {
      return state.with(ROTATION, rotation.rotate(state.get(ROTATION), 16));
   }

   @Override
   public BlockState mirror(BlockState state, BlockMirror mirror) {
      return state.with(ROTATION, mirror.mirror(state.get(ROTATION), 16));
   }

   @Override
   public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
      return new RegimentBannerBlockEntity(pos, state);
   }

   @Override
   public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
      if (!world.isClient && !player.isCreative() && world.getBlockEntity(pos) instanceof RegimentBannerBlockEntity banner) {
         ItemStack drop = getBannerItemFor(banner.getRegimentType());
         Block.dropStack(world, pos, drop);
      }

      super.onBreak(world, pos, state, player);
   }

   @Override
   public ItemStack getPickStack(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
      return world.getBlockEntity(pos) instanceof RegimentBannerBlockEntity banner
         ? getBannerItemFor(banner.getRegimentType())
         : new ItemStack(DannysAot.GARRISON_BANNER);
   }

   static ItemStack getBannerItemFor(RegimentType type) {
      return switch (type) {
         case GARRISON -> new ItemStack(DannysAot.GARRISON_BANNER);
         case MILITARY_POLICE -> new ItemStack(DannysAot.MILITARY_POLICE_BANNER);
         case SCOUT -> new ItemStack(DannysAot.SCOUT_BANNER);
         case TRAINING -> new ItemStack(DannysAot.TRAINING_CORPS_BANNER);
         case GOLD_CLOAK -> new ItemStack(DannysAot.GOLD_CLOAK_BANNER);
      };
   }
}
