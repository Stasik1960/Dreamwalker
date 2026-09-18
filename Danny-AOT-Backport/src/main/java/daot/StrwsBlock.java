package daot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class StrwsBlock extends Block implements BlockEntityProvider {
   public StrwsBlock() {
      super(Settings.create().strength(1.0F).nonOpaque().pistonBehavior(PistonBehavior.BLOCK));
      this.setDefaultState(this.stateManager.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
   }

   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(Properties.HORIZONTAL_FACING);
   }

   @Override
   public BlockState getPlacementState(ItemPlacementContext ctx) {
      return this.getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
   }

   @Override
   public BlockState rotate(BlockState state, BlockRotation rotation) {
      return state.with(Properties.HORIZONTAL_FACING, rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));
   }

   @Override
   public BlockState mirror(BlockState state, BlockMirror mirror) {
      return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING)));
   }

   @Override
   public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
      return player.isSneaking() ? StrwsMultiblock.openMenu(world, pos, state, player) : StrwsMultiblock.interact(world, pos, state, player);
   }

   @Override
   public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
      return new StrwsBlockEntity(pos, state);
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
      return !world.isClient && type == DannysAot.STRWS_BLOCK_ENTITY ? (w, pos, blockState, be) -> StrwsBlockEntity.serverTick(w, pos, blockState, (StrwsBlockEntity)be) : null;
   }

   @Override
   public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
      StrwsMultiblock.destroy(world, pos, state, player);
      super.onBreak(world, pos, state, player);
   }

   @Override
   public ItemStack getPickStack(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
      return new ItemStack(DannysAot.STRWS_ITEM);
   }
}
