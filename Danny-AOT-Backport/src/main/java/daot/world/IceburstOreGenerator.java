package daot.world;

import daot.DannysAot;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class IceburstOreGenerator {
   private static final Set<Long> processedChunks = new HashSet<>();
   private static final Queue<IceburstOreGenerator.PendingOre> pendingOres = new ConcurrentLinkedQueue<>();
   private static ServerWorld cachedParadis = null;
   private static final RegistryKey<World> PARADIS_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static final int RARITY = 2;
   private static final int MIN_Y = -64;
   private static final int MAX_Y = 0;
   private static final int VEIN_SIZE = 26;
   private static final float BURSTING_CHANCE = 0.2F;
   private static final int MAX_PROCESS_PER_TICK = 2;

   public static void register() {
      ServerChunkEvents.CHUNK_LOAD.register((Load)(world, chunk) -> {
         if (world.getRegistryKey().equals(PARADIS_DIMENSION)) {
            queueChunkForProcessing(world, chunk);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> processQueuedOres());
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearProcessedChunks());
      DannysAot.LOGGER.info("Registered Iceburst Ore Generator for Paradis");
   }

   private static void queueChunkForProcessing(ServerWorld world, WorldChunk chunk) {
      ChunkPos chunkPos = chunk.getPos();
      long chunkKey = chunkPos.toLong();
      if (!processedChunks.contains(chunkKey)) {
         processedChunks.add(chunkKey);
         if (processedChunks.size() > 5000) {
            processedChunks.clear();
         }

         if (cachedParadis == null) {
            cachedParadis = world;
         }

         long seed = world.getSeed() ^ chunkPos.x * 517293817L + chunkPos.z * 839201747L;
         if (Math.abs(seed % 2L) == 0L) {
            pendingOres.offer(new IceburstOreGenerator.PendingOre(chunkPos.x, chunkPos.z, seed));
         }
      }
   }

   private static void processQueuedOres() {
      if (cachedParadis != null && !pendingOres.isEmpty()) {
         for (int i = 0; i < 2; i++) {
            IceburstOreGenerator.PendingOre pending = pendingOres.poll();
            if (pending == null) {
               break;
            }

            BlockPos chunkCenter = new BlockPos(pending.chunkX() * 16 + 8, 0, pending.chunkZ() * 16 + 8);
            if (!cachedParadis.canSetBlock(chunkCenter)) {
               pendingOres.offer(pending);
            } else {
               try {
                  generateOreVein(cachedParadis, pending.chunkX(), pending.chunkZ(), pending.seed());
               } catch (Exception var4) {
                  DannysAot.LOGGER.error("Failed to generate iceburst ore: {}", var4.getMessage());
               }
            }
         }
      }
   }

   private static void generateOreVein(ServerWorld world, int chunkX, int chunkZ, long seed) {
      Random random = Random.create(seed);
      int x = chunkX * 16 + random.nextInt(16);
      int z = chunkZ * 16 + random.nextInt(16);
      int y = -64 + random.nextInt(64);
      BlockPos center = new BlockPos(x, y, z);
      BlockPos surfacePos = findCaveSurface(world, center, random);
      if (surfacePos != null) {
         int placed = 0;

         for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
               for (int dz = -3; dz <= 3; dz++) {
                  if (dx * dx + dy * dy + dz * dz <= 10 && !(random.nextFloat() > 0.5F)) {
                     BlockPos pos = surfacePos.add(dx, dy, dz);
                     if (world.canSetBlock(pos)) {
                        BlockState state = world.getBlockState(pos);
                        if (isReplaceableStone(state) && isAdjacentToAir(world, pos)) {
                           BlockState oreState;
                           if (random.nextFloat() < 0.2F) {
                              oreState = DannysAot.BURSTING_ICE_BURST_STONE.getDefaultState();
                           } else {
                              oreState = DannysAot.ICE_BURST_STONE.getDefaultState();
                           }

                           world.setBlockState(pos, oreState, 2);
                           if (++placed >= 26) {
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static BlockPos findCaveSurface(ServerWorld world, BlockPos start, Random random) {
      for (int attempt = 0; attempt < 12; attempt++) {
         int dx = random.nextInt(9) - 4;
         int dy = random.nextInt(9) - 4;
         int dz = random.nextInt(9) - 4;
         BlockPos checkPos = start.add(dx, dy, dz);
         if (world.canSetBlock(checkPos)) {
            BlockState state = world.getBlockState(checkPos);
            if (isReplaceableStone(state) && isAdjacentToAir(world, checkPos)) {
               return checkPos;
            }
         }
      }

      return null;
   }

   private static boolean isAdjacentToAir(ServerWorld world, BlockPos pos) {
      return isAirIfLoaded(world, pos.up())
         || isAirIfLoaded(world, pos.down())
         || isAirIfLoaded(world, pos.north())
         || isAirIfLoaded(world, pos.south())
         || isAirIfLoaded(world, pos.east())
         || isAirIfLoaded(world, pos.west());
   }

   private static boolean isAirIfLoaded(ServerWorld world, BlockPos pos) {
      return world.canSetBlock(pos) && world.getBlockState(pos).isAir();
   }

   private static boolean isReplaceableStone(BlockState state) {
      return state.isIn(BlockTags.STONE_ORE_REPLACEABLES)
         || state.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
         || state.isOf(Blocks.STONE)
         || state.isOf(Blocks.DEEPSLATE)
         || state.isOf(Blocks.TUFF)
         || state.isOf(Blocks.GRANITE)
         || state.isOf(Blocks.DIORITE)
         || state.isOf(Blocks.ANDESITE);
   }

   public static void clearProcessedChunks() {
      processedChunks.clear();
      pendingOres.clear();
      cachedParadis = null;
   }

   private record PendingOre(int chunkX, int chunkZ, long seed) {
   }
}
