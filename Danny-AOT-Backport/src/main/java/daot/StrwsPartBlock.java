package daot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class StrwsPartBlock extends Block {
   public static final IntProperty LAT_OFF = IntProperty.of("lat_off", 0, 2);
   public static final IntProperty Y_OFF = IntProperty.of("y_off", 0, 2);

   public StrwsPartBlock() {
      super(Settings.create().strength(1.0F).nonOpaque().pistonBehavior(PistonBehavior.BLOCK));
      this.setDefaultState(this.stateManager.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH).with(LAT_OFF, 0).with(Y_OFF, 0));
   }

   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(Properties.HORIZONTAL_FACING, LAT_OFF, Y_OFF);
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
   public BlockRenderType getRenderType(BlockState state) {
      return BlockRenderType.INVISIBLE;
   }

   @Override
   public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
      return player.isSneaking() ? StrwsMultiblock.openMenu(world, pos, state, player) : StrwsMultiblock.interact(world, pos, state, player);
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
