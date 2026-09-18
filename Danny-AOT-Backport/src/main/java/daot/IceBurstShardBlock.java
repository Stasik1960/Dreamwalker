package daot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.loot.context.LootContextParameterSet.Builder;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class IceBurstShardBlock extends AmethystClusterBlock {
   private final int height;
   private final int aabbOffset;

   public IceBurstShardBlock(int height, int aabbOffset, Settings properties) {
      super(height, aabbOffset, properties);
      this.height = height;
      this.aabbOffset = aabbOffset;
   }


   @Override
   public List<ItemStack> getDroppedStacks(BlockState state, Builder builder) {
      if (this.height == 7) {
         int count = 1 + builder.getWorld().random.nextInt(2);
         List<ItemStack> drops = new ArrayList<>();
         drops.add(new ItemStack(DannysAot.ICE_BURST_CLUSTER, count));
         return drops;
      } else {
         return super.getDroppedStacks(state, builder);
      }
   }

   @Override
   public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
      float hardness = state.getHardness(world, pos);
      if (hardness == -1.0F) {
         return 0.0F;
      } else {
         float speed = 1.0F;
         if (player.getMainHandStack().getItem() instanceof PickaxeItem pickaxe) {
            speed = pickaxe.getMaterial().getMiningSpeedMultiplier();
         }

         return speed / hardness / 30.0F;
      }
   }

   @Override
   public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
      if (random.nextInt(8) == 0) {
         Direction facing = state.get(FACING);
         double tipOffset = this.height / 16.0;
         double x = pos.getX() + 0.5 + facing.getOffsetX() * tipOffset;
         double y = pos.getY() + 0.5 + facing.getOffsetY() * tipOffset;
         double z = pos.getZ() + 0.5 + facing.getOffsetZ() * tipOffset;
         x += (random.nextDouble() - 0.5) * 0.3;
         y += (random.nextDouble() - 0.5) * 0.3;
         z += (random.nextDouble() - 0.5) * 0.3;
         double vx = facing.getOffsetX() * 0.02;
         double vy = facing.getOffsetY() * 0.02 + 0.01;
         double vz = facing.getOffsetZ() * 0.02;
         world.addParticle(daot.compat.BackportEffects.SMALL_GUST, x, y, z, vx, vy, vz);
      }
   }

   public static IceBurstShardBlock createSmall() {
      return new IceBurstShardBlock(
         3,
         4,
         Settings.create()
            .strength(1.5F)
            .sounds(BlockSoundGroup.AMETHYST_CLUSTER)
            .mapColor(MapColor.PALE_PURPLE)
            .nonOpaque()
            .luminance(state -> 15)
            .pistonBehavior(PistonBehavior.DESTROY)
      );
   }

   public static IceBurstShardBlock createMedium() {
      return new IceBurstShardBlock(
         4,
         3,
         Settings.create()
            .strength(1.5F)
            .sounds(BlockSoundGroup.AMETHYST_CLUSTER)
            .mapColor(MapColor.PALE_PURPLE)
            .nonOpaque()
            .luminance(state -> 15)
            .pistonBehavior(PistonBehavior.DESTROY)
      );
   }

   public static IceBurstShardBlock createLarge() {
      return new IceBurstShardBlock(
         5,
         3,
         Settings.create()
            .strength(1.5F)
            .sounds(BlockSoundGroup.AMETHYST_CLUSTER)
            .mapColor(MapColor.PALE_PURPLE)
            .nonOpaque()
            .luminance(state -> 15)
            .pistonBehavior(PistonBehavior.DESTROY)
      );
   }

   public static IceBurstShardBlock createCluster() {
      return new IceBurstShardBlock(
         7,
         3,
         Settings.create()
            .strength(1.5F)
            .sounds(BlockSoundGroup.AMETHYST_CLUSTER)
            .mapColor(MapColor.PALE_PURPLE)
            .nonOpaque()
            .luminance(state -> 15)
            .pistonBehavior(PistonBehavior.DESTROY)
      );
   }
}
