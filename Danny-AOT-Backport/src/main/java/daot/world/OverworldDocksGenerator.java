package daot.world;

import daot.DannysAot;
import daot.mixin.StructureTemplateAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.StructureTemplate.PalettedBlockInfoList;
import net.minecraft.structure.StructureTemplate.StructureBlockInfo;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.chunk.WorldChunk;

public class OverworldDocksGenerator {
   private static final Set<Long> processedChunks = new HashSet<>();
   private static final Queue<OverworldDocksGenerator.PendingDock> pendingDocks = new ConcurrentLinkedQueue<>();
   private static StructureTemplate docksTemplate = null;
   private static boolean templateLoadAttempted = false;
   private static ServerWorld cachedOverworld = null;
   private static final int RARITY = 100;
   private static final int MIN_DISTANCE = 500;
   private static final int MAX_PROCESS_PER_TICK = 1;

   public static void register() {
      ServerChunkEvents.CHUNK_LOAD.register((Load)(world, chunk) -> {
         if (world.getRegistryKey() == World.OVERWORLD) {
            queueChunkForProcessing(world, chunk);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> processQueuedDocks());
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearProcessedChunks());
      DannysAot.LOGGER.info("Registered Overworld Docks Generator");
   }

   private static void queueChunkForProcessing(ServerWorld world, WorldChunk chunk) {
      ChunkPos chunkPos = chunk.getPos();
      long chunkKey = chunkPos.toLong();
      if (!processedChunks.contains(chunkKey)) {
         processedChunks.add(chunkKey);
         if (processedChunks.size() > 10000) {
            processedChunks.clear();
         }

         if (cachedOverworld == null) {
            cachedOverworld = world;
         }

         long seed = world.getSeed() ^ chunkPos.x * 341873128712L + chunkPos.z * 132897987541L;
         if (Math.abs(seed % 100L) == 0L) {
            pendingDocks.offer(new OverworldDocksGenerator.PendingDock(chunkPos.getCenterX(), chunkPos.getCenterZ(), seed));
         }
      }
   }

   private static void processQueuedDocks() {
      if (cachedOverworld != null && !pendingDocks.isEmpty()) {
         for (int i = 0; i < 1; i++) {
            OverworldDocksGenerator.PendingDock pending = pendingDocks.poll();
            if (pending == null) {
               break;
            }

            try {
               tryPlaceDocksDeferred(cachedOverworld, pending.centerX(), pending.centerZ(), pending.seed());
            } catch (Exception var3) {
               DannysAot.LOGGER.error("Failed to place docks structure: {}", var3.getMessage());
            }
         }
      }
   }

   private static void tryPlaceDocksDeferred(ServerWorld world, int centerX, int centerZ, long seed) {
      int centerY = world.getTopY(Type.WORLD_SURFACE, centerX, centerZ);
      BlockPos centerPos = new BlockPos(centerX, centerY, centerZ);
      if (world.getBiome(centerPos).isIn(BiomeTags.IS_BEACH)) {
         PortalLocationTracker tracker = PortalLocationTracker.get(world);
         if (!tracker.isNearExistingDock(centerPos, 500)) {
            Random random = Random.create(seed);
            Direction waterDir = findWaterDirectionFast(world, centerPos);
            if (waterDir != null) {
               BlockPos edgePos = findWaterEdgeFast(world, centerPos, waterDir);
               if (edgePos != null) {
                  placeDocksStructure(world, edgePos, waterDir, random);
               }
            }
         }
      }
   }

   private static Direction findWaterDirectionFast(ServerWorld world, BlockPos pos) {
      for (Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
         int waterCount = 0;

         for (int dist = 4; dist <= 12; dist += 4) {
            BlockPos checkPos = pos.offset(dir, dist);
            int y = world.getTopY(Type.WORLD_SURFACE, checkPos.getX(), checkPos.getZ());
            if (world.getBlockState(new BlockPos(checkPos.getX(), y - 1, checkPos.getZ())).isOf(Blocks.WATER)) {
               waterCount++;
            }
         }

         if (waterCount >= 2) {
            return dir;
         }
      }

      return null;
   }

   private static BlockPos findWaterEdgeFast(ServerWorld world, BlockPos startPos, Direction waterDir) {
      BlockPos current = startPos;

      for (int i = 0; i < 12; i++) {
         BlockPos next = current.offset(waterDir);
         int y = world.getTopY(Type.WORLD_SURFACE, next.getX(), next.getZ());
         if (world.getBlockState(new BlockPos(next.getX(), y - 1, next.getZ())).isOf(Blocks.WATER)) {
            return new BlockPos(current.getX(), world.getTopY(Type.WORLD_SURFACE, current.getX(), current.getZ()), current.getZ());
         }

         current = next;
      }

      return null;
   }

   private static void placeDocksStructure(ServerWorld world, BlockPos pos, Direction facingWater, Random random) {
      if (!templateLoadAttempted) {
         loadTemplate(world);
      }

      if (docksTemplate != null) {
         BlockRotation rotation = getRotationForDirection(facingWater);
         BlockPos placePos = pos.down(3);
         placeStructurePreservingTerrain(world, placePos, rotation, random);
         PortalLocationTracker tracker = PortalLocationTracker.get(world);
         tracker.addOverworldDock(placePos);
         DannysAot.LOGGER.info("Placed docks1 structure at {} facing {} in overworld", placePos, facingWater);
      }
   }

   private static BlockRotation getRotationForDirection(Direction dir) {
      return switch (dir) {
         case SOUTH -> BlockRotation.NONE;
         case WEST -> BlockRotation.CLOCKWISE_90;
         case NORTH -> BlockRotation.CLOCKWISE_180;
         case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
         default -> BlockRotation.NONE;
      };
   }

   private static void placeStructurePreservingTerrain(ServerWorld world, BlockPos origin, BlockRotation rotation, Random random) {
      StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE).setIgnoreEntities(false);
      List<PalettedBlockInfoList> palettes = ((StructureTemplateAccessor)docksTemplate).getPalettes();
      if (!palettes.isEmpty()) {
         for (StructureBlockInfo blockInfo : palettes.get(0).getAll()) {
            if (!blockInfo.state().isAir()) {
               BlockPos transformedPos = StructureTemplate.transform(settings, blockInfo.pos()).add(origin);
               BlockState transformedState = blockInfo.state().rotate(rotation);
               if (transformedState.isOf(Blocks.GOLD_BLOCK)) {
                  world.setBlockState(transformedPos, DannysAot.PARADIS_PORTAL.getDefaultState(), 3);
               } else {
                  world.setBlockState(transformedPos, transformedState, 3);
               }
            }
         }
      }
   }

   private static void loadTemplate(ServerWorld world) {
      templateLoadAttempted = true;

      try {
         Identifier structureId = new Identifier("dannys-aot", "docks1");
         StructureTemplateManager templateManager = world.getStructureTemplateManager();
         Optional<StructureTemplate> optional = templateManager.getTemplate(structureId);
         if (optional.isPresent()) {
            docksTemplate = optional.get();
            DannysAot.LOGGER.info("Loaded docks1 structure template");
         } else {
            DannysAot.LOGGER.warn("Could not find docks1 structure template - make sure docks1.nbt exists in data/dannys-aot/structure/");
         }
      } catch (Exception var4) {
         DannysAot.LOGGER.error("Failed to load docks1 structure: {}", var4.getMessage());
      }
   }

   public static void clearProcessedChunks() {
      processedChunks.clear();
      pendingDocks.clear();
      templateLoadAttempted = false;
      docksTemplate = null;
      cachedOverworld = null;
   }

   private record PendingDock(int centerX, int centerZ, long seed) {
   }
}
