package daot;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class IceburstFurnaceBlock extends BlockWithEntity {
   public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
   public static final BooleanProperty LIT = Properties.LIT;

   public IceburstFurnaceBlock(Settings properties) {
      super(properties);
      this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
   }


   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(FACING, LIT);
   }

   @Nullable
   @Override
   public BlockState getPlacementState(ItemPlacementContext ctx) {
      return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
   }

   @Override
   public BlockRenderType getRenderType(BlockState state) {
      return BlockRenderType.MODEL;
   }

   @Override
   public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
      if (!world.isClient && world.getBlockEntity(pos) instanceof IceburstFurnaceBlockEntity furnace) {
         player.openHandledScreen(furnace);
      }

      return ActionResult.success(world.isClient);
   }

   @Nullable
   @Override
   public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
      return new IceburstFurnaceBlockEntity(pos, state);
   }

   @Nullable
   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
      return world.isClient ? null : checkType(type, DannysAot.ICEBURST_FURNACE_BLOCK_ENTITY, IceburstFurnaceBlockEntity::serverTick);
   }

   @Override
   public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
      if (!state.isOf(newState.getBlock())) {
         if (world.getBlockEntity(pos) instanceof IceburstFurnaceBlockEntity furnace) {
            if (world instanceof ServerWorld) {
               ItemScatterer.spawn(world, pos, furnace);
            }

            world.updateComparators(pos, this);
         }

         super.onStateReplaced(state, world, pos, newState, moved);
      }
   }

   @Override
   public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
      if (state.get(LIT)) {
         double x = pos.getX() + 0.5;
         double y = pos.getY();
         double z = pos.getZ() + 0.5;
         if (random.nextDouble() < 0.1) {
            world.playSound(x, y, z, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
         }

         Direction direction = state.get(FACING);
         Axis axis = direction.getAxis();
         double offset = random.nextDouble() * 0.6 - 0.3;
         double xOff = axis == Axis.X ? direction.getOffsetX() * 0.52 : offset;
         double yOff = random.nextDouble() * 6.0 / 16.0;
         double zOff = axis == Axis.Z ? direction.getOffsetZ() * 0.52 : offset;
         world.addParticle(ParticleTypes.SMOKE, x + xOff, y + yOff, z + zOff, 0.0, 0.0, 0.0);
         world.addParticle(ParticleTypes.SNOWFLAKE, x + xOff, y + yOff, z + zOff, 0.0, 0.0, 0.0);
      }
   }
}
