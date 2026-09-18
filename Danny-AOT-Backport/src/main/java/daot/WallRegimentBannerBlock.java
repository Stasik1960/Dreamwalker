package daot;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class WallRegimentBannerBlock extends Block implements BlockEntityProvider {
   public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
   private static final Map<Direction, VoxelShape> SHAPES = Maps.newEnumMap(
      ImmutableMap.of(
         Direction.NORTH,
         Block.createCuboidShape(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
         Direction.SOUTH,
         Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
         Direction.WEST,
         Block.createCuboidShape(14.0, 0.0, 0.0, 16.0, 16.0, 16.0),
         Direction.EAST,
         Block.createCuboidShape(0.0, 0.0, 0.0, 2.0, 16.0, 16.0)
      )
   );

   public WallRegimentBannerBlock() {
      super(Settings.create().strength(1.0F).nonOpaque().noCollision());
      this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
   }

   @Override
   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      return SHAPES.get(state.get(FACING));
   }

   @Override
   public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
      Direction facing = state.get(FACING);
      return world.getBlockState(pos.offset(facing.getOpposite())).isSolid();
   }

   @Override
   public BlockState getStateForNeighborUpdate(
      BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos
   ) {
      return direction == state.get(FACING).getOpposite() && !this.canPlaceAt(state, world, pos)
         ? Blocks.AIR.getDefaultState()
         : super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
   }

   @Override
   public BlockState getPlacementState(ItemPlacementContext ctx) {
      Direction clickedFace = ctx.getSide();
      if (clickedFace.getAxis().isHorizontal()) {
         BlockState state = this.getDefaultState().with(FACING, clickedFace);
         if (state.canPlaceAt(ctx.getWorld(), ctx.getBlockPos())) {
            return state;
         }
      }

      return null;
   }

   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(FACING);
   }

   @Override
   public BlockState rotate(BlockState state, BlockRotation rotation) {
      return state.with(FACING, rotation.rotate(state.get(FACING)));
   }

   @Override
   public BlockState mirror(BlockState state, BlockMirror mirror) {
      return state.rotate(mirror.getRotation(state.get(FACING)));
   }

   @Override
   public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
      return new RegimentBannerBlockEntity(pos, state);
   }

   @Override
   public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
      if (!world.isClient && !player.isCreative() && world.getBlockEntity(pos) instanceof RegimentBannerBlockEntity banner) {
         ItemStack drop = RegimentBannerBlock.getBannerItemFor(banner.getRegimentType());
         Block.dropStack(world, pos, drop);
      }

      super.onBreak(world, pos, state, player);
   }

   @Override
   public ItemStack getPickStack(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
      return world.getBlockEntity(pos) instanceof RegimentBannerBlockEntity banner
         ? RegimentBannerBlock.getBannerItemFor(banner.getRegimentType())
         : new ItemStack(DannysAot.GARRISON_BANNER);
   }
}
