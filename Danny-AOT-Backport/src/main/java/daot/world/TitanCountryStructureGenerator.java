package daot.world;

import daot.DannysAot;
import daot.mixin.StructureTemplateAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
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
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;

public class TitanCountryStructureGenerator {
   private static final Set<Long> processedChunks = new HashSet<>();
   private static final Queue<TitanCountryStructureGenerator.PendingStructure> pendingStructures = new ConcurrentLinkedQueue<>();
   private static StructureTemplate chapelTemplate = null;
   private static StructureTemplate utgardTemplate = null;
   private static boolean templatesLoadAttempted = false;
   private static ServerWorld cachedParadis = null;
   private static final RegistryKey<World> PARADIS_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static final RegistryKey<Biome> TITAN_HIGHLANDS_BIOME = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "titan_highlands"));
   private static final int CHAPEL_RARITY = 10000;
   private static final int UTGARD_RARITY = 10000;
   private static final int MIN_DISTANCE_BETWEEN = 500;
   private static final int MIN_DISTANCE_FROM_VILLAGE = 250;
   private static final int MAX_PROCESS_PER_TICK = 1;
   private static final int BLEND_RADIUS = 5;
   private static final int TREE_CLEAR_RADIUS = 5;

   public static void register() {
      ServerChunkEvents.CHUNK_LOAD.register((Load)(world, chunk) -> {
         if (world.getRegistryKey().equals(PARADIS_DIMENSION)) {
            queueChunkForProcessing(world, chunk);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> processQueuedStructures());
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearState());
      DannysAot.LOGGER.info("Registered Titan Highlands Structure Generator (chapel, utgard)");
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

         if (!ParadisChunkGenerator.DMNK) {
            long baseSeed = world.getSeed() ^ chunkPos.x * 341873128712L + chunkPos.z * 132897987541L;
            long chapelSeed = baseSeed + 77777L;
            if (Math.abs(chapelSeed % 10000L) == 0L) {
               pendingStructures.offer(new TitanCountryStructureGenerator.PendingStructure(chunkPos.getCenterX(), chunkPos.getCenterZ(), chapelSeed, "chapel"));
            }

            long utgardSeed = baseSeed + 99999L;
            if (Math.abs(utgardSeed % 10000L) == 0L) {
               pendingStructures.offer(new TitanCountryStructureGenerator.PendingStructure(chunkPos.getCenterX(), chunkPos.getCenterZ(), utgardSeed, "utgard"));
            }
         }
      }
   }

   private static void processQueuedStructures() {
      if (cachedParadis != null && !pendingStructures.isEmpty()) {
         for (int i = 0; i < 1; i++) {
            TitanCountryStructureGenerator.PendingStructure pending = pendingStructures.poll();
            if (pending == null) {
               break;
            }

            try {
               tryPlaceStructure(cachedParadis, pending.centerX(), pending.centerZ(), pending.seed(), pending.type());
            } catch (Exception var3) {
               DannysAot.LOGGER.error("Failed to place {} structure: {}", pending.type(), var3.getMessage());
            }
         }
      }
   }

   private static int findGroundY(ServerWorld world, int x, int z) {
      int rawY = world.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, x, z);
      Mutable probe = new Mutable(x, rawY - 1, z);

      while (probe.getY() > world.getBottomY()) {
         BlockState state = world.getBlockState(probe);
         if (!state.isIn(BlockTags.LOGS)
            && !state.isAir()
            && !state.isOf(Blocks.GRASS)
            && !state.isOf(Blocks.TALL_GRASS)
            && !state.isOf(Blocks.FERN)
            && !state.isOf(Blocks.LARGE_FERN)
            && !state.isIn(BlockTags.FLOWERS)
            && !state.isOf(Blocks.MOSS_CARPET)) {
            return probe.getY() + 1;
         }

         probe.move(0, -1, 0);
      }

      return rawY;
   }

   private static void tryPlaceStructure(ServerWorld world, int centerX, int centerZ, long seed, String type) {
      int centerY = findGroundY(world, centerX, centerZ);
      BlockPos centerPos = new BlockPos(centerX, centerY, centerZ);
      RegistryEntry<Biome> biomeHolder = world.getBiome(centerPos);
      if (biomeHolder.matchesKey(TITAN_HIGHLANDS_BIOME)) {
         int[] nearestVillage = ParadisChunkGenerator.getNearestVillageCenterStatic(centerX, centerZ);
         if (nearestVillage != null && nearestVillage.length == 2) {
            double distToVillage = Math.sqrt(Math.pow(centerX - nearestVillage[0], 2.0) + Math.pow(centerZ - nearestVillage[1], 2.0));
            if (distToVillage < 250.0) {
               return;
            }
         }

         if (!ParadisChunkGenerator.isNearPathStatic(centerX, centerZ)) {
            PortalLocationTracker tracker = PortalLocationTracker.get(world);
            if (!tracker.isNearExistingScatteredStructure(centerPos, 500)) {
               if ("utgard".equals(type)) {
                  centerPos = centerPos.down(3);
               }

               Random random = Random.create(seed);
               placeStructure(world, centerPos, random, type, tracker);
            }
         }
      }
   }

   private static void placeStructure(ServerWorld world, BlockPos pos, Random random, String type, PortalLocationTracker tracker) {
      if (!templatesLoadAttempted) {
         loadTemplates(world);
      }

      StructureTemplate template = "chapel".equals(type) ? chapelTemplate : utgardTemplate;
      if (template != null) {
         BlockRotation rotation = BlockRotation.values()[random.nextInt(4)];
         placeStructureWithTerrainBlend(world, pos, rotation, template, type);
         tracker.addScatteredStructure(pos);
         DannysAot.LOGGER.info("Placed {} structure at {} in Paradis titan_highlands", type, pos);
      }
   }

   private static void placeStructureWithTerrainBlend(ServerWorld world, BlockPos origin, BlockRotation rotation, StructureTemplate template, String type) {
      StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE).setIgnoreEntities(false);
      List<PalettedBlockInfoList> palettes = ((StructureTemplateAccessor)template).getPalettes();
      if (!palettes.isEmpty()) {
         List<StructureBlockInfo> blockInfos = palettes.get(0).getAll();
         int minRelY = Integer.MAX_VALUE;

         for (StructureBlockInfo blockInfo : blockInfos) {
            if (!blockInfo.state().isAir()) {
               int relY = blockInfo.pos().getY();
               if (relY < minRelY) {
                  minRelY = relY;
               }
            }
         }

         if (minRelY != Integer.MAX_VALUE) {
            Set<Long> footprintWorldXZ = new HashSet<>();
            Map<Long, BlockState> bottomBlocksWorld = new HashMap<>();
            int footMinX = Integer.MAX_VALUE;
            int footMaxX = Integer.MIN_VALUE;
            int footMinZ = Integer.MAX_VALUE;
            int footMaxZ = Integer.MIN_VALUE;

            for (StructureBlockInfo blockInfox : blockInfos) {
               if (!blockInfox.state().isAir()) {
                  BlockPos transformed = StructureTemplate.transform(settings, blockInfox.pos());
                  int wx = origin.getX() + transformed.getX();
                  int wz = origin.getZ() + transformed.getZ();
                  long xzKey = (long)wx << 32 | wz & 4294967295L;
                  footprintWorldXZ.add(xzKey);
                  footMinX = Math.min(footMinX, wx);
                  footMaxX = Math.max(footMaxX, wx);
                  footMinZ = Math.min(footMinZ, wz);
                  footMaxZ = Math.max(footMaxZ, wz);
                  if (blockInfox.pos().getY() == minRelY) {
                     BlockState transformedState = blockInfox.state().rotate(rotation);
                     bottomBlocksWorld.put(xzKey, transformedState);
                  }
               }
            }

            int structureBottomY = origin.getY() + minRelY;
            int clearMinX = footMinX - 5;
            int clearMaxX = footMaxX + 5;
            int clearMinZ = footMinZ - 5;
            int clearMaxZ = footMaxZ + 5;
            int clearMinY = structureBottomY - 2;
            int clearMaxY = structureBottomY + 50;
            List<BlockPos> trunkBases = new ArrayList<>();

            for (int cx = clearMinX; cx <= clearMaxX; cx++) {
               for (int cz = clearMinZ; cz <= clearMaxZ; cz++) {
                  for (int cy = clearMinY; cy <= clearMaxY; cy++) {
                     BlockPos pos2 = new BlockPos(cx, cy, cz);
                     BlockState state = world.getBlockState(pos2);
                     if (state.isIn(BlockTags.LOGS)) {
                        BlockState below = world.getBlockState(pos2.down());
                        if (!below.isIn(BlockTags.LOGS)) {
                           trunkBases.add(pos2.toImmutable());
                        }
                        break;
                     }
                  }
               }
            }

            Set<BlockPos> removedPositions = new HashSet<>();

            for (BlockPos trunkBase : trunkBases) {
               List<BlockPos> logPositions = new ArrayList<>();
               Mutable scan = trunkBase.mutableCopy();

               while (scan.getY() <= clearMaxY && world.getBlockState(scan).isIn(BlockTags.LOGS)) {
                  logPositions.add(scan.toImmutable());
                  scan.move(0, 1, 0);
               }

               for (BlockPos logPos : logPositions) {
                  if (removedPositions.add(logPos)) {
                     world.setBlockState(logPos, Blocks.AIR.getDefaultState(), 3);
                  }
               }

               if (!logPositions.isEmpty()) {
                  BlockPos topLog = logPositions.get(logPositions.size() - 1);
                  int leafSearchRadius = 7;

                  for (int lx = -leafSearchRadius; lx <= leafSearchRadius; lx++) {
                     for (int ly = -2; ly <= leafSearchRadius + 2; ly++) {
                        for (int lz = -leafSearchRadius; lz <= leafSearchRadius; lz++) {
                           BlockPos leafPos = topLog.add(lx, ly, lz);
                           if (!removedPositions.contains(leafPos)) {
                              BlockState leafState = world.getBlockState(leafPos);
                              if (leafState.isIn(BlockTags.LEAVES) || leafState.isIn(BlockTags.SAPLINGS)) {
                                 removedPositions.add(leafPos);
                                 world.setBlockState(leafPos, Blocks.AIR.getDefaultState(), 3);
                              }
                           }
                        }
                     }
                  }
               }
            }

            for (int cx = footMinX; cx <= footMaxX; cx++) {
               for (int cz = footMinZ; cz <= footMaxZ; cz++) {
                  for (int cyx = clearMinY; cyx <= clearMaxY; cyx++) {
                     BlockPos clearPos = new BlockPos(cx, cyx, cz);
                     if (!removedPositions.contains(clearPos)) {
                        BlockState existing = world.getBlockState(clearPos);
                        if (existing.isIn(BlockTags.SAPLINGS) || existing.isIn(BlockTags.LEAVES) || existing.isIn(BlockTags.LOGS)) {
                           world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), 3);
                        }
                     }
                  }
               }
            }

            List<BlockPos> chestPositions = new ArrayList<>();

            for (StructureBlockInfo blockInfoxx : blockInfos) {
               if (!blockInfoxx.state().isAir()) {
                  BlockPos transformedPos = StructureTemplate.transform(settings, blockInfoxx.pos()).add(origin);
                  BlockState transformedState = blockInfoxx.state().rotate(rotation);
                  world.setBlockState(transformedPos, transformedState, 3);
                  Block block = transformedState.getBlock();
                  if (block == Blocks.CHEST || block == Blocks.BARREL) {
                     chestPositions.add(transformedPos.toImmutable());
                  }
               }
            }

            for (BlockPos chestPos : chestPositions) {
               assignStructureLoot(world, chestPos, type);
            }

            for (Entry<Long, BlockState> entry : bottomBlocksWorld.entrySet()) {
               long xzKey = entry.getKey();
               BlockState fillBlock = entry.getValue();
               int wx = (int)(xzKey >> 32);
               int wz = (int)(xzKey & 4294967295L);

               for (int y = structureBottomY - 1; y >= world.getBottomY(); y--) {
                  BlockPos fillPos = new BlockPos(wx, y, wz);
                  BlockState existing = world.getBlockState(fillPos);
                  if (!existing.isAir()
                     && existing.getFluidState().isEmpty()
                     && !existing.isOf(Blocks.GRASS)
                     && !existing.isOf(Blocks.TALL_GRASS)
                     && !existing.isOf(Blocks.FERN)
                     && !existing.isOf(Blocks.LARGE_FERN)
                     && !existing.isIn(BlockTags.LOGS)
                     && !existing.isIn(BlockTags.LEAVES)) {
                     break;
                  }

                  world.setBlockState(fillPos, fillBlock, 3);
               }
            }

            int blendMin = 8;

            for (int wx = footMinX - blendMin; wx <= footMaxX + blendMin; wx++) {
               for (int wz = footMinZ - blendMin; wz <= footMaxZ + blendMin; wz++) {
                  long xzKey = (long)wx << 32 | wz & 4294967295L;
                  if (!footprintWorldXZ.contains(xzKey)) {
                     double minDist = Double.MAX_VALUE;

                     for (int fx = Math.max(footMinX, wx - blendMin); fx <= Math.min(footMaxX, wx + blendMin); fx++) {
                        for (int fz = Math.max(footMinZ, wz - blendMin); fz <= Math.min(footMaxZ, wz + blendMin); fz++) {
                           long fKey = (long)fx << 32 | fz & 4294967295L;
                           if (bottomBlocksWorld.containsKey(fKey)) {
                              double d = Math.sqrt((wx - fx) * (wx - fx) + (wz - fz) * (wz - fz));
                              if (d < minDist) {
                                 minDist = d;
                              }
                           }
                        }
                     }

                     if (!(minDist > blendMin)) {
                        int terrainY = findGroundY(world, wx, wz);
                        int heightDiff = structureBottomY - terrainY;
                        if (heightDiff > 0) {
                           double t = Math.max(0.0, 1.0 - minDist / blendMin);
                           t = t * t * (3.0 - 2.0 * t);
                           int targetY = terrainY + (int)Math.round(heightDiff * t);
                           if (targetY > terrainY) {
                              BlockPos surfaceBelow = new BlockPos(wx, terrainY - 1, wz);
                              BlockState surfaceBlock = world.getBlockState(surfaceBelow);
                              boolean isGrassy = surfaceBlock.isOf(Blocks.GRASS_BLOCK) || surfaceBlock.isOf(Blocks.PODZOL);
                              BlockState fillBlock = isGrassy ? Blocks.DIRT.getDefaultState() : surfaceBlock;
                              BlockState topBlock = isGrassy ? Blocks.GRASS_BLOCK.getDefaultState() : surfaceBlock;

                              for (int y = terrainY; y < targetY; y++) {
                                 BlockPos bp = new BlockPos(wx, y, wz);
                                 if (y == targetY - 1) {
                                    world.setBlockState(bp, topBlock, 3);
                                 } else {
                                    world.setBlockState(bp, fillBlock, 3);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void assignStructureLoot(ServerWorld world, BlockPos pos, String type) {
      if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
         long seed = pos.getX() * 374761393L + pos.getZ() * 668265263L ^ pos.getY() * 987654321L;
         java.util.Random lootRand = new java.util.Random(seed);
         Identifier[] structureTables = new Identifier[]{
            LootTables.VILLAGE_WEAPONSMITH_CHEST,
            LootTables.VILLAGE_WEAPONSMITH_CHEST,
            LootTables.VILLAGE_TOOLSMITH_CHEST,
            LootTables.VILLAGE_TOOLSMITH_CHEST,
            LootTables.VILLAGE_ARMORER_CHEST,
            LootTables.STRONGHOLD_CORRIDOR_CHEST
         };
         container.setLootTable(structureTables[lootRand.nextInt(structureTables.length)], lootRand.nextLong());
         if (lootRand.nextFloat() < 0.4F) {
            int slot = lootRand.nextInt(27);
            float roll = lootRand.nextFloat();
            if (roll < 0.25F) {
               container.setStack(slot, new ItemStack(DannysAot.BLADE_COMPONENT, 2 + lootRand.nextInt(3)));
            } else if (roll < 0.45F) {
               container.setStack(slot, new ItemStack(DannysAot.ICE_BURST_CLUSTER, 2 + lootRand.nextInt(4)));
            } else if (roll < 0.65F) {
               container.setStack(slot, new ItemStack(DannysAot.EMPTY_SYRINGE));
            } else {
               container.setStack(slot, new ItemStack(DannysAot.SYRINGE));
            }
         }

         if (lootRand.nextFloat() < 0.2F) {
            int slot2 = lootRand.nextInt(27);
            float roll2 = lootRand.nextFloat();
            if (roll2 < 0.4F) {
               container.setStack(slot2, new ItemStack(DannysAot.BLADE_COMPONENT, 1 + lootRand.nextInt(2)));
            } else if (roll2 < 0.7F) {
               container.setStack(slot2, new ItemStack(DannysAot.ICE_BURST_CLUSTER, 1 + lootRand.nextInt(3)));
            } else {
               container.setStack(slot2, new ItemStack(DannysAot.EMPTY_SYRINGE));
            }
         }

         if ("utgard".equals(type) && lootRand.nextFloat() < 0.6F) {
            int herringSlot = lootRand.nextInt(27);
            int count = 1 + lootRand.nextInt(4);
            container.setStack(herringSlot, new ItemStack(DannysAot.CANNED_HERRING, count));
         }
      }
   }

   private static void loadTemplates(ServerWorld world) {
      templatesLoadAttempted = true;

      try {
         StructureTemplateManager templateManager = world.getStructureTemplateManager();
         Identifier chapelId = new Identifier("dannys-aot", "chapel");
         templateManager.getTemplate(chapelId).ifPresent(t -> {
            chapelTemplate = t;
            DannysAot.LOGGER.info("Loaded chapel structure template");
         });
         Identifier utgardId = new Identifier("dannys-aot", "utgard");
         templateManager.getTemplate(utgardId).ifPresent(t -> {
            utgardTemplate = t;
            DannysAot.LOGGER.info("Loaded utgard structure template");
         });
         if (chapelTemplate == null) {
            DannysAot.LOGGER.warn("Could not find chapel structure template");
         }

         if (utgardTemplate == null) {
            DannysAot.LOGGER.warn("Could not find utgard structure template");
         }
      } catch (Exception var4) {
         DannysAot.LOGGER.error("Failed to load structure templates: {}", var4.getMessage());
      }
   }

   public static void clearState() {
      processedChunks.clear();
      pendingStructures.clear();
      templatesLoadAttempted = false;
      chapelTemplate = null;
      utgardTemplate = null;
      cachedParadis = null;
   }

   private record PendingStructure(int centerX, int centerZ, long seed, String type) {
   }
}
