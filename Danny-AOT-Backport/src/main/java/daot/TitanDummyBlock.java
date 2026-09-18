package daot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager.Builder;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TitanDummyBlock extends Block {
   public static final BooleanProperty CENTER = BooleanProperty.of("center");
   private static boolean isRemoving = false;

   public TitanDummyBlock() {
      super(Settings.create().mapColor(MapColor.OAK_TAN).strength(2.0F).sounds(BlockSoundGroup.WOOD).nonOpaque());
      this.setDefaultState(this.stateManager.getDefaultState().with(CENTER, false));
   }

   @Override
   protected void appendProperties(Builder<Block, BlockState> builder) {
      builder.add(CENTER);
   }

   @Override
   public BlockRenderType getRenderType(BlockState state) {
      return BlockRenderType.INVISIBLE;
   }

   public static boolean tryPlace(World level, BlockPos centerPos) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = centerPos.add(dx, 0, dz);
            if (!level.getBlockState(pos).isReplaceable()) {
               return false;
            }
         }
      }

      BlockState centerState = DannysAot.TITAN_DUMMY_BLOCK.getDefaultState().with(CENTER, true);
      BlockState partState = DannysAot.TITAN_DUMMY_BLOCK.getDefaultState().with(CENTER, false);
      level.setBlockState(centerPos, centerState, 3);

      for (int dx = -1; dx <= 1; dx++) {
         for (int dzx = -1; dzx <= 1; dzx++) {
            if (dx != 0 || dzx != 0) {
               level.setBlockState(centerPos.add(dx, 0, dzx), partState, 3);
            }
         }
      }

      if (!level.isClient()) {
         TitanDummyEntity entity = new TitanDummyEntity(DannysAot.TITAN_DUMMY, level);
         entity.setCenterBlockPos(centerPos);
         entity.setPosition(centerPos.getX() + 0.5, centerPos.getY(), centerPos.getZ() + 0.5);
         level.spawnEntity(entity);
         entity.spawnHitboxes();
      }

      return true;
   }

   @Override
   public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
      if (!isRemoving && !world.isClient()) {
         isRemoving = true;

         try {
            for (int dx = -2; dx <= 2; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  if (dx != 0 || dz != 0) {
                     BlockPos neighbor = pos.add(dx, 0, dz);
                     if (world.getBlockState(neighbor).getBlock() instanceof TitanDummyBlock) {
                        world.breakBlock(neighbor, false);
                     }
                  }
               }
            }
         } finally {
            isRemoving = false;
         }

         super.onStateReplaced(state, world, pos, newState, moved);
      } else {
         super.onStateReplaced(state, world, pos, newState, moved);
      }
   }
}
