package daot;

import com.mojang.serialization.MapCodec;
import daot.world.PortalLocationTracker;
import java.util.Random;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class ParadisPortalBlock extends BlockWithEntity {
   protected static final VoxelShape SHAPE = createCuboidShape(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);
   private static final RegistryKey<World> PARADIS_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));

   public ParadisPortalBlock() {
      super(Settings.create().mapColor(MapColor.BLACK).noCollision().luminance(state -> 15).strength(-1.0F, 3600000.0F).dropsNothing().nonOpaque());
   }


   @Override
   public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
      return new ParadisPortalBlockEntity(pos, state);
   }

   @Override
   public BlockRenderType getRenderType(BlockState state) {
      return BlockRenderType.ENTITYBLOCK_ANIMATED;
   }

   @Override
   public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
      return SHAPE;
   }

   @Override
   public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
      if (!world.isClient() && entity instanceof ServerPlayerEntity player) {
         if (player.hasPortalCooldown()) {
            player.resetPortalCooldown();
            return;
         }

         ServerWorld serverLevel = (ServerWorld)world;
         RegistryKey<World> currentDimension = world.getRegistryKey();
         PortalLocationTracker tracker = PortalLocationTracker.get(serverLevel);
         Random random = new Random();
         BlockPos targetPos = null;
         RegistryKey<World> destinationKey;
         if (currentDimension.equals(PARADIS_DIMENSION)) {
            destinationKey = World.OVERWORLD;
            targetPos = tracker.getRandomOverworldDock(random);
         } else {
            destinationKey = PARADIS_DIMENSION;
            targetPos = tracker.getRandomParadisReturnPillar(random);
         }

         ServerWorld destination = serverLevel.getServer().getWorld(destinationKey);
         if (destination == null) {
            DannysAot.LOGGER.warn("Could not find destination dimension: {}", destinationKey.getValue());
            return;
         }

         world.playSound(null, pos, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.5F, 1.0F);
         double destX;
         double destY;
         double destZ;
         if (targetPos != null) {
            destX = targetPos.getX() + 2;
            destZ = targetPos.getZ() + 2;
            destY = this.findSafeY(destination, destX, destZ);
         } else {
            destX = player.getX();
            destZ = player.getZ();
            destY = this.findSafeY(destination, destX, destZ);
            DannysAot.LOGGER.info("No tracked portal locations found, using same coordinates");
         }

         player.teleport(destination, destX, destY, destZ, player.getYaw(), player.getPitch());
         player.resetPortalCooldown();
         destination.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.5F, 1.0F);
         DannysAot.LOGGER
            .info(
               "Player {} teleported to {} at ({}, {}, {}) via Paradis Portal",
               new Object[]{player.getName().getString(), destinationKey.getValue(), (int)destX, (int)destY, (int)destZ}
            );
      }
   }

   private double findSafeY(ServerWorld level, double x, double z) {
      Mutable pos = new Mutable(x, (double)level.getTopY(), z);

      while (pos.getY() > level.getBottomY()) {
         pos.move(0, -1, 0);
         BlockState state = level.getBlockState(pos);
         if (state.isOpaqueFullCube(level, pos)) {
            return pos.getY() + 1;
         }
      }

      return 64.0;
   }

   @Override
   public ItemStack getPickStack(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
      return ItemStack.EMPTY;
   }
}
