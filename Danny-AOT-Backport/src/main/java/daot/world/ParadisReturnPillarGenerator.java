package daot.world;

import daot.DannysAot;
import daot.ModConfig;
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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;

public class ParadisReturnPillarGenerator {
   private static final Set<Long> processedChunks = new HashSet<>();
   private static final Queue<ParadisReturnPillarGenerator.PendingPillar> pendingPillars = new ConcurrentLinkedQueue<>();
   private static StructureTemplate pillarTemplate = null;
   private static boolean templateLoadAttempted = false;
   private static ServerWorld cachedParadis = null;
   private static final RegistryKey<World> PARADIS_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static final RegistryKey<Biome> TITAN_COUNTRY_BIOME = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "titan_country"));
   private static final int RARITY = 5000;
   private static final int MIN_DISTANCE = 1000;
   private static final int MAX_PROCESS_PER_TICK = 1;

   public static void register() {
      ServerChunkEvents.CHUNK_LOAD.register((Load)(world, chunk) -> {
         if (ModConfig.get().enableReturnPillarGeneration) {
            if (world.getRegistryKey().equals(PARADIS_DIMENSION)) {
               queueChunkForProcessing(world, chunk);
            }
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> processQueuedPillars());
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearProcessedChunks());
      DannysAot.LOGGER.info("Registered Paradis Return Pillar Generator");
   }

   private static void queueChunkForProcessing(ServerWorld world, WorldChunk chunk) {
      ChunkPos chunkPos = chunk.getPos();
      long chunkKey = chunkPos.toLong();
      if (!processedChunks.contains(chunkKey)) {
         processedChunks.add(chunkKey);
         if (processedChunks.size() > 10000) {
            processedChunks.clear();
         }

         if (cachedParadis == null) {
            cachedParadis = world;
         }

         long seed = world.getSeed() ^ chunkPos.x * 341873128712L + chunkPos.z * 132897987541L + 12345L;
         if (Math.abs(seed % 5000L) == 0L) {
            pendingPillars.offer(new ParadisReturnPillarGenerator.PendingPillar(chunkPos.getCenterX(), chunkPos.getCenterZ(), seed));
         }
      }
   }

   private static void processQueuedPillars() {
      if (cachedParadis != null && !pendingPillars.isEmpty()) {
         for (int i = 0; i < 1; i++) {
            ParadisReturnPillarGenerator.PendingPillar pending = pendingPillars.poll();
            if (pending == null) {
               break;
            }

            try {
               tryPlacePillarDeferred(cachedParadis, pending.centerX(), pending.centerZ(), pending.seed());
            } catch (Exception var3) {
               DannysAot.LOGGER.error("Failed to place return pillar structure: {}", var3.getMessage());
            }
         }
      }
   }

   private static void tryPlacePillarDeferred(ServerWorld world, int centerX, int centerZ, long seed) {
      int centerY = world.getTopY(Type.WORLD_SURFACE, centerX, centerZ);
      BlockPos centerPos = new BlockPos(centerX, centerY, centerZ);
      RegistryEntry<Biome> biomeHolder = world.getBiome(centerPos);
      if (biomeHolder.matchesKey(TITAN_COUNTRY_BIOME)) {
         PortalLocationTracker tracker = PortalLocationTracker.get(world);
         if (!tracker.isNearExistingReturnPillar(centerPos, 1000)) {
            Random random = Random.create(seed);
            placePillarStructure(world, centerPos, random);
         }
      }
   }

   private static void placePillarStructure(ServerWorld world, BlockPos pos, Random random) {
      if (!templateLoadAttempted) {
         loadTemplate(world);
      }

      if (pillarTemplate != null) {
         BlockRotation rotation = BlockRotation.values()[random.nextInt(4)];
         placeStructurePreservingTerrain(world, pos, rotation, random);
         PortalLocationTracker tracker = PortalLocationTracker.get(world);
         tracker.addParadisReturnPillar(pos);
         DannysAot.LOGGER.info("Placed returnpillar structure at {} in Paradis", pos);
      }
   }

   private static void placeStructurePreservingTerrain(ServerWorld world, BlockPos origin, BlockRotation rotation, Random random) {
      StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE).setIgnoreEntities(false);
      List<PalettedBlockInfoList> palettes = ((StructureTemplateAccessor)pillarTemplate).getPalettes();
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
         Identifier structureId = new Identifier("dannys-aot", "returnpillar");
         StructureTemplateManager templateManager = world.getStructureTemplateManager();
         Optional<StructureTemplate> optional = templateManager.getTemplate(structureId);
         if (optional.isPresent()) {
            pillarTemplate = optional.get();
            DannysAot.LOGGER.info("Loaded returnpillar structure template");
         } else {
            DannysAot.LOGGER.warn("Could not find returnpillar structure template - make sure returnpillar.nbt exists in data/dannys-aot/structure/");
         }
      } catch (Exception var4) {
         DannysAot.LOGGER.error("Failed to load returnpillar structure: {}", var4.getMessage());
      }
   }

   public static void clearProcessedChunks() {
      processedChunks.clear();
      pendingPillars.clear();
      templateLoadAttempted = false;
      pillarTemplate = null;
      cachedParadis = null;
   }

   private record PendingPillar(int centerX, int centerZ, long seed) {
   }
}
