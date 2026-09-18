package daot;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BambooLeaves;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class IronBambooItem extends Item {
   public IronBambooItem(Settings properties) {
      super(properties);
   }

   @Override
   public ActionResult useOnBlock(ItemUsageContext context) {
      World level = context.getWorld();
      BlockPos clickedPos = context.getBlockPos();
      BlockState clickedState = level.getBlockState(clickedPos);
      Direction face = context.getSide();
      if (clickedState.isOf(DannysAot.IRON_BAMBOO_SAPLING) && face == Direction.UP) {
         if (!level.isClient) {
            level.setBlockState(
               clickedPos,
               DannysAot.IRON_BAMBOO
                  .getDefaultState()
                  .with(IronBambooBlock.LEAVES, BambooLeaves.NONE)
                  .with(IronBambooBlock.STAGE, 0)
                  .with(IronBambooBlock.AGE, 0),
               3
            );
            BlockPos abovePos = clickedPos.up();
            if (level.getBlockState(abovePos).isAir()) {
               level.setBlockState(
                  abovePos,
                  DannysAot.IRON_BAMBOO
                     .getDefaultState()
                     .with(IronBambooBlock.LEAVES, BambooLeaves.SMALL)
                     .with(IronBambooBlock.STAGE, 0)
                     .with(IronBambooBlock.AGE, 0),
                  3
               );
            }

            BlockSoundGroup sound = BlockSoundGroup.BAMBOO;
            level.playSound(null, abovePos, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            if (!context.getPlayer().getAbilities().creativeMode) {
               context.getStack().decrement(1);
            }
         }

         return ActionResult.success(level.isClient);
      } else if (clickedState.isOf(DannysAot.IRON_BAMBOO) && face == Direction.UP) {
         BlockPos abovePosx = clickedPos.up();
         if (level.getBlockState(abovePosx).isAir()) {
            if (!level.isClient) {
               int stage = clickedState.get(IronBambooBlock.STAGE);
               BambooLeaves currentLeaves = clickedState.get(IronBambooBlock.LEAVES);
               if (currentLeaves != BambooLeaves.NONE) {
                  BlockPos belowClicked = clickedPos.down();
                  BlockState belowState = level.getBlockState(belowClicked);
                  if (belowState.isOf(DannysAot.IRON_BAMBOO) && belowState.get(IronBambooBlock.LEAVES) == BambooLeaves.SMALL) {
                     level.setBlockState(belowClicked, belowState.with(IronBambooBlock.LEAVES, BambooLeaves.NONE), 3);
                  }

                  level.setBlockState(clickedPos, clickedState.with(IronBambooBlock.LEAVES, BambooLeaves.SMALL), 3);
               }

               level.setBlockState(
                  abovePosx,
                  DannysAot.IRON_BAMBOO
                     .getDefaultState()
                     .with(IronBambooBlock.LEAVES, BambooLeaves.LARGE)
                     .with(IronBambooBlock.STAGE, stage)
                     .with(IronBambooBlock.AGE, 0),
                  3
               );
               BlockSoundGroup sound = BlockSoundGroup.BAMBOO;
               level.playSound(null, abovePosx, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
               if (!context.getPlayer().getAbilities().creativeMode) {
                  context.getStack().decrement(1);
               }
            }

            return ActionResult.success(level.isClient);
         } else {
            return ActionResult.FAIL;
         }
      } else {
         BlockPos placePos = clickedPos.offset(face);
         BlockState groundState = level.getBlockState(placePos.down());
         if (face == Direction.UP && groundState.isIn(BlockTags.BAMBOO_PLANTABLE_ON) && level.getBlockState(placePos).isAir()) {
            if (!level.isClient) {
               level.setBlockState(placePos, DannysAot.IRON_BAMBOO_SAPLING.getDefaultState(), 3);
               BlockSoundGroup sound = BlockSoundGroup.BAMBOO_SAPLING;
               level.playSound(null, placePos, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
               if (!context.getPlayer().getAbilities().creativeMode) {
                  context.getStack().decrement(1);
               }
            }

            return ActionResult.success(level.isClient);
         } else {
            return ActionResult.PASS;
         }
      }
   }
}
