package daot.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import daot.DannysAot;
import daot.IronBambooBlock;
import daot.ModConfig;
import daot.mixin.StructureTemplateAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.BambooLeaves;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTables;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplate.PalettedBlockInfoList;
import net.minecraft.structure.StructureTemplate.StructureBlockInfo;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.GenerationStep.Carver;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

public class ParadisChunkGenerator extends ChunkGenerator {
   public static final Codec<ParadisChunkGenerator> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Biome.REGISTRY_CODEC.fieldOf("titan_country").forGetter(gen -> gen.titanCountry),
            Biome.REGISTRY_CODEC.fieldOf("giant_forest").forGetter(gen -> gen.giantForest),
            Biome.REGISTRY_CODEC.fieldOf("titan_highlands").forGetter(gen -> gen.titanHighlands),
            Biome.REGISTRY_CODEC.optionalFieldOf("titan_hills").forGetter(gen -> gen.titanHillsOpt),
            Biome.REGISTRY_CODEC.optionalFieldOf("titan_mountains").forGetter(gen -> gen.titanMountainsOpt)
         )
         .apply(instance, ParadisChunkGenerator::new)
   );
   private final RegistryEntry<Biome> titanCountry;
   private final RegistryEntry<Biome> giantForest;
   private final RegistryEntry<Biome> titanHighlands;
   private final Optional<RegistryEntry<Biome>> titanHillsOpt;
   private final Optional<RegistryEntry<Biome>> titanMountainsOpt;
   private static long worldSeed = 0L;
   private static Block cachedZincOre = null;
   private static Block cachedDeepslateZincOre = null;
   private static final boolean ENABLE_VILLAGES = true;
   private static final boolean ENABLE_VILLAGE_WALLS = true;
   private static final boolean ENABLE_VILLAGE_STRUCTURES = true;
   private static final boolean ENABLE_VILLAGE_GROUND = true;
   private static final boolean ENABLE_PATHS = false;
   private static final boolean ENABLE_GIANT_TREES = true;
   private static final boolean ENABLE_PINE_TREES = true;
   private static final boolean ENABLE_VEGETATION = true;
   private static final int BASE_HEIGHT = 64;
   private static final int MAX_TERRAIN_VARIATION = 10;
   private static final int GIANT_FOREST_EXTRA_DEPTH = 15;
   private static final int CITY_CENTER_X = 0;
   private static final int CITY_CENTER_Z = 0;
   static final boolean DMNK = detectDmnk();
   private static final int[] RING_RADII = DMNK ? new int[]{10000, 17000, 35000} : new int[]{1000, 7000, 13000};
   private static final int RING_THICKNESS_BASE = DMNK ? 15 : 9;
   private static final int RING_THICKNESS_UPPER = DMNK ? 15 : 7;
   private static final int RING_HEIGHT = DMNK ? 50 : Math.max(10, ModConfig.get().paradisWallHeight);
   private static final int WALL_BURY_DEPTH = DMNK ? 10 : 15;
   private static final int RING_BASE_HEIGHT = Math.max(2, Math.round(RING_HEIGHT * 26.0F / 50.0F));
   private static final int DISTRICT_WIDTH_AT_BASE = 600;
   private static final int DISTRICT_PROTRUSION = 600;
   private static final int DISTRICT_WIDTH = 800;
   private static final int DISTRICT_WALL_THICKNESS = DMNK ? 15 : 9;
   private static final int DISTRICTS_PER_RING = 4;
   private static final int TOTAL_DISTRICTS = 12;
   private static final int[][] CARDINAL_DIRS = new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
   private static final double[] CARDINAL_ANGLES = new double[]{-Math.PI / 2, 0.0, Math.PI / 2, Math.PI};
   private static final int[] CARDINAL_GATE_ROTATION = new int[]{0, 1, 2, 3};
   private static final double[] DISTRICT_HALF_ARC = new double[RING_RADII.length];
   private static final int[] DISTRICT_INNER_EXTENSION;
   private static final double[] RING_GATE_HALF_ARC;
   private static final int RIDGE_ARC_SPACING;
   private static final int[] RING_RIDGE_COUNT;
   private static final double[] RING_RIDGE_ANGLE_STEP;
   private static final int WALL_INNER_RADIUS_BASE = 290;
   private static final int WALL_INNER_RADIUS_UPPER = 291;
   private static final int WALL_OUTER_RADIUS;
   private static final int WALL_OUTER_RADIUS_BASE;
   private static final int WALL_HEIGHT;
   private static final int WALL_BASE_HEIGHT;
   private static final int GATE_WIDTH = 7;
   private static final int GATE_HEIGHT = 15;
   private static final int WALL_BASE_Y = 59;
   private static final int VILLAGE_GRID_SPACING = 6000;
   private static final double VILLAGE_SPAWN_CHANCE = 0.0;
   private static final int MAX_PATH_DISTANCE = 3500;
   private static final int MAX_PATH_DISTANCE_THROUGH_FOREST = 6000;
   private static final int PATH_WIDTH = 10;
   private static final int INNER_PATH_LENGTH = 15;
   private static final String[] PINE_TREE_VARIANTS;
   private static final String[][] DMNK_TREE_TIERS;
   private static final Set<String> DMNK_TREE_SET;
   private static final int GIANT_TREE_MIN_RADIUS = 3;
   private static final int GIANT_TREE_MAX_RADIUS = 5;
   private static final int GIANT_TREE_SHORT_HEIGHT = 120;
   private static final int GIANT_TREE_TALL_HEIGHT = 220;
   private static final int GIANT_TREE_GRID_SPACING = 38;
   private static final int IRON_BAMBOO_GRID_SPACING = 200;
   private static final double IRON_BAMBOO_SPAWN_CHANCE = 0.2;
   private static final int IRON_BAMBOO_PATCH_MIN_RADIUS = 8;
   private static final int IRON_BAMBOO_PATCH_MAX_RADIUS = 16;
   private static final int IRON_BAMBOO_MIN_HEIGHT = 3;
   private static final int IRON_BAMBOO_MAX_HEIGHT = 8;
   private static final int HOUSE_LENGTH = 22;
   private static final int HOUSE_WIDTH = 10;
   private static final int HOUSE4_LENGTH = 22;
   private static final int HOUSE4_WIDTH = 19;
   private static final int TOWER_SIZE = 7;
   private static final int CASTLE_HALF_SIZE = 24;
   private static final int GATE_STRUCTURE_LENGTH = 19;
   private static final int GATE_STRUCTURE_WIDTH = 15;
   private static final int STRUCTURE_GAP = 5;
   private static final int[] RANDOM_HOUSE_TYPES;
   private static final int SHIG_HOUSE_LENGTH = 12;
   private static final int SHIG_HOUSE_WIDTH = 22;
   private static final int[] RANDOM_SHIG_HOUSE_TYPES;
   private static final int[] RANDOM_SHIG_LARGE_TYPES;
   private static final int VILLAGE_TYPE_INDUSTRIAL = 0;
   private static final int VILLAGE_TYPE_SHIGANSHINA = 1;
   private static final Map<String, List<int[]>> villageHousePositions;
   private static final Map<String, int[]> caveEntrancePositions;
   private static final Map<String, List<int[]>> villageLayoutCache;
   private static final Map<String, int[]> villageCenterCache;
   private static final int[] NO_VILLAGE;
   private static final Map<String, Map<Long, int[]>> villageChunkIndex;
   private static final Map<String, int[][]> villagePlacedRectsCache;
   private static final int[][] DISTRICTS;
   private static final long[] RING_OUTER_SQ;
   private static final long[] RING_INNER_BASE_SQ;
   private static final long[] RING_INNER_UPPER_SQ;
   private static final long WALL_OUTER_RADIUS_SQ;
   private static final long WALL_OUTER_RADIUS_BASE_SQ;
   private static final long WALL_INNER_RADIUS_UPPER_SQ = 84681L;
   private static final long WALL_INNER_RADIUS_BASE_SQ = 84100L;
   private static final double[] RIDGE_ANGLES;
   private static final double[] RIDGE_COS;
   private static final double[] RIDGE_SIN;
   private static final double RIDGE_SIN_SQ_TOLERANCE;
   private static final long RIDGE_INNER_SQ = 82369L;
   private static final long RIDGE_OUTER_SQ;
   private static final ThreadLocal<ParadisChunkGenerator.ChunkCacheState> CHUNK_CACHE;
   private double cachedMoundCenterHeight = Double.NaN;
   private int cachedMoundCenterX = Integer.MIN_VALUE;
   private int cachedMoundCenterZ = Integer.MIN_VALUE;
   private static final double MOUND_RADIUS = 278.0;
   private static final double MOUND_RADIUS_SQ = 77284.0;
   private static final Map<String, StructureTemplate> templateCache;
   private static final int CITY_GIANT_FOREST_BUFFER = 500;
   private static final long CITY_FOREST_EXCLUSION_RADIUS_SQ;
   private static final int ELEVATED_WALL_BUFFER = 2000;
   private static final int ELEVATED_BUFFER_RAMP = 400;
   private static final double HILLS_THRESHOLD = 0.7;
   private static final double HILLS_VALLEY_OFFSET = 25.0;
   private static final double HILLS_PEAK_OFFSET = 186.0;
   private static final double MOUNTAINS_THRESHOLD = 0.72;
   private static final double MOUNTAINS_VALLEY_OFFSET = 40.0;
   private static final double MOUNTAINS_PEAK_OFFSET = 311.0;
   private final ConcurrentHashMap<Long, int[]> districtSegmentTops = new ConcurrentHashMap<>();
   private static final int MAX_SEGMENT_STEP = 1;
   private static final int STEP_BRIDGE_OFFSET = 2;
   private final ConcurrentHashMap<Integer, int[]> ringSegmentTops = new ConcurrentHashMap<>();
   private static final Map<Long, List<int[]>> pathSegmentCache;
   private static final int BRANCH_DISTANCE_MIN = 30;
   private static final int BRANCH_DISTANCE_MAX = 50;
   private static final double MIN_BRANCH_ANGLE = Math.PI / 7;
   private static final int[] CARDINAL_TO_GATE_INDEX;

   private static boolean detectDmnk() {
      try {
         if (FabricLoader.getInstance().isModLoaded("dmnk")) {
            return true;
         }
      } catch (Throwable var4) {
      }

      try {
         Class<?> modList = Class.forName("net.neoforged.fml.ModList");
         Object inst = modList.getMethod("get").invoke(null);
         if (inst != null && Boolean.TRUE.equals(modList.getMethod("isLoaded", String.class).invoke(inst, "dmnk"))) {
            return true;
         }
      } catch (Throwable var3) {
      }

      try {
         if (ParadisChunkGenerator.class.getResource("/dmnk-trees/massive_tree_1.nbt") != null) {
            return true;
         }
      } catch (Throwable var2) {
      }

      return false;
   }

   private static int[] getShigLargeFootprint(int type) {
      switch (type) {
         case 12:
            return new int[]{30, 30};
         case 13:
            return new int[]{31, 25};
         case 14:
            return new int[]{29, 23};
         case 15:
            return new int[]{23, 23};
         default:
            return new int[]{22, 19};
      }
   }

   public static int[][] getAllDistrictCenters() {
      int[][] centers = new int[12][2];

      for (int i = 0; i < 12; i++) {
         centers[i][0] = DISTRICTS[i][2];
         centers[i][1] = DISTRICTS[i][3];
      }

      return centers;
   }

   private double getCachedHeight(int worldX, int worldZ, int chunkX, int chunkZ) {
      ParadisChunkGenerator.ChunkCacheState cache = CHUNK_CACHE.get();
      if (chunkX != cache.heightChunkX || chunkZ != cache.heightChunkZ) {
         if (cache.heightCache == null) {
            cache.heightCache = new double[256];
         }

         cache.heightChunkX = chunkX;
         cache.heightChunkZ = chunkZ;
         int baseX = chunkX << 4;
         int baseZ = chunkZ << 4;

         for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
               cache.heightCache[lx * 16 + lz] = this.getHeightAt(baseX + lx, baseZ + lz);
            }
         }
      }

      int lx = worldX - (chunkX << 4);
      int lz = worldZ - (chunkZ << 4);
      return lx >= 0 && lx < 16 && lz >= 0 && lz < 16 ? cache.heightCache[lx * 16 + lz] : this.getHeightAt(worldX, worldZ);
   }

   private boolean isOnPathFast(int worldX, int worldZ) {
      if (DMNK) {
         return false;
      } else {
         int centerGridX = worldX / 6000;
         int centerGridZ = worldZ / 6000;
         int halfWidth = 5;
         double maxDistSq = (double)(halfWidth + 2) * (halfWidth + 2);

         for (int gridX = centerGridX - 1; gridX <= centerGridX + 1; gridX++) {
            for (int gridZ = centerGridZ - 1; gridZ <= centerGridZ + 1; gridZ++) {
               for (int[] seg : this.getPathSegments(gridX, gridZ)) {
                  int startX = seg[0];
                  int startZ = seg[1];
                  int endX = seg[2];
                  int endZ = seg[3];
                  int bMinX = Math.min(startX, endX) - 10 - 20;
                  int bMaxX = Math.max(startX, endX) + 10 + 20;
                  int bMinZ = Math.min(startZ, endZ) - 10 - 20;
                  int bMaxZ = Math.max(startZ, endZ) + 10 + 20;
                  if (worldX >= bMinX && worldX <= bMaxX && worldZ >= bMinZ && worldZ <= bMaxZ) {
                     double pathDx = endX - startX;
                     double pathDz = endZ - startZ;
                     double pathLenSq = pathDx * pathDx + pathDz * pathDz;
                     if (pathLenSq != 0.0) {
                        double pathLen = Math.sqrt(pathLenSq);
                        double ndx = pathDx / pathLen;
                        double ndz = pathDz / pathLen;
                        double px = worldX - startX;
                        double pz = worldZ - startZ;
                        double t = Math.max(0.0, Math.min(1.0, (px * ndx + pz * ndz) / pathLen));
                        double pathPointX = startX + t * pathLen * ndx;
                        double pathPointZ = startZ + t * pathLen * ndz;
                        int seed = startX * 31 + startZ * 17 + endX * 13 + endZ * 7;
                        double curveOffset = this.getPathCurveOffset(pathPointX, pathPointZ, seed, t);
                        double curvedX = pathPointX + -ndz * curveOffset;
                        double curvedZ = pathPointZ + ndx * curveOffset;
                        double ddx = worldX - curvedX;
                        double ddz = worldZ - curvedZ;
                        if (ddx * ddx + ddz * ddz <= maxDistSq) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   private boolean isOnPathCached(int worldX, int worldZ, int chunkX, int chunkZ) {
      ParadisChunkGenerator.ChunkCacheState cache = CHUNK_CACHE.get();
      if (chunkX != cache.pathMaskChunkX || chunkZ != cache.pathMaskChunkZ) {
         if (cache.pathMask == null) {
            cache.pathMask = new boolean[256];
         }

         cache.pathMaskChunkX = chunkX;
         cache.pathMaskChunkZ = chunkZ;
         int baseX = chunkX << 4;
         int baseZ = chunkZ << 4;

         for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
               cache.pathMask[lx * 16 + lz] = this.isOnPathFast(baseX + lx, baseZ + lz);
            }
         }
      }

      int lx = worldX - (chunkX << 4);
      int lz = worldZ - (chunkZ << 4);
      return lx >= 0 && lx < 16 && lz >= 0 && lz < 16 ? cache.pathMask[lx * 16 + lz] : this.isOnPathFast(worldX, worldZ);
   }

   private boolean isNearCaveEntranceCached(int worldX, int worldZ, int chunkX, int chunkZ) {
      ParadisChunkGenerator.ChunkCacheState cache = CHUNK_CACHE.get();
      if (chunkX != cache.caveMaskChunkX || chunkZ != cache.caveMaskChunkZ) {
         if (cache.caveMask == null) {
            cache.caveMask = new boolean[256];
         }

         cache.caveMaskChunkX = chunkX;
         cache.caveMaskChunkZ = chunkZ;
         int baseX = chunkX << 4;
         int baseZ = chunkZ << 4;
         this.computeCaveMaskForChunk(baseX, baseZ);
      }

      int lx = worldX - (chunkX << 4);
      int lz = worldZ - (chunkZ << 4);
      return lx >= 0 && lx < 16 && lz >= 0 && lz < 16 ? cache.caveMask[lx * 16 + lz] : this.isNearCaveEntrance(worldX, worldZ);
   }

   private void computeCaveMaskForChunk(int baseX, int baseZ) {
      boolean[] caveMask = CHUNK_CACHE.get().caveMask;
      Arrays.fill(caveMask, false);
      int centerChunkX = baseX + 8 >> 4;
      int centerChunkZ = baseZ + 8 >> 4;
      double[][] nearbyEntrances = new double[10][5];
      int entranceCount = 0;

      for (int cx = centerChunkX - 3; cx <= centerChunkX + 3; cx++) {
         for (int cz = centerChunkZ - 3; cz <= centerChunkZ + 3; cz++) {
            long entranceSeed = worldSeed ^ cx * 987654321L ^ cz * 123456789L;
            Random entranceRandom = Random.create(entranceSeed);
            if (entranceRandom.nextInt(2000) < 1) {
               int entranceX = cx * 16 + entranceRandom.nextInt(16);
               int entranceZ = cz * 16 + entranceRandom.nextInt(16);
               if (!this.isInsideVillageWalls(entranceX, entranceZ) && !this.isNearVillage(entranceX, entranceZ, WALL_OUTER_RADIUS + 100)) {
                  double eDirection = entranceRandom.nextDouble() * Math.PI * 2.0;
                  entranceRandom.nextDouble();
                  entranceRandom.nextDouble();
                  int entranceLength = 30 + entranceRandom.nextInt(40);
                  double pathLength = Math.min(entranceLength, 25);
                  if (entranceCount >= nearbyEntrances.length) {
                     nearbyEntrances = Arrays.copyOf(nearbyEntrances, nearbyEntrances.length * 2);

                     for (int i = entranceCount; i < nearbyEntrances.length; i++) {
                        nearbyEntrances[i] = new double[5];
                     }
                  }

                  nearbyEntrances[entranceCount][0] = entranceX;
                  nearbyEntrances[entranceCount][1] = entranceZ;
                  nearbyEntrances[entranceCount][2] = Math.cos(eDirection);
                  nearbyEntrances[entranceCount][3] = Math.sin(eDirection);
                  nearbyEntrances[entranceCount][4] = pathLength;
                  entranceCount++;
               }
            }
         }
      }

      if (entranceCount != 0) {
         for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
               int worldX = baseX + lx;
               int worldZ = baseZ + lz;

               for (int e = 0; e < entranceCount; e++) {
                  double ex = nearbyEntrances[e][0];
                  double ez = nearbyEntrances[e][1];
                  double dxe = worldX - ex;
                  double dze = worldZ - ez;
                  double distSq = dxe * dxe + dze * dze;
                  if (!(distSq > 961.0)) {
                     if (distSq < 64.0) {
                        caveMask[lx * 16 + lz] = true;
                        break;
                     }

                     double dirCos = nearbyEntrances[e][2];
                     double dirSin = nearbyEntrances[e][3];
                     double pathLen = nearbyEntrances[e][4];
                     boolean nearPath = false;

                     for (double d = 0.0; d < pathLen; d += 2.0) {
                        double pathX = ex + dirCos * d;
                        double pathZ = ez + dirSin * d;
                        double pdx = worldX - pathX;
                        double pdz = worldZ - pathZ;
                        if (pdx * pdx + pdz * pdz < 36.0) {
                           nearPath = true;
                           break;
                        }
                     }

                     if (nearPath) {
                        caveMask[lx * 16 + lz] = true;
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private static long packChunkPos(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | chunkZ & 4294967295L;
   }

   private static Map<Long, int[]> buildChunkStructureIndex(List<int[]> layout) {
      Map<Long, List<Integer>> tempIndex = new HashMap<>();

      for (int i = 0; i < layout.size(); i++) {
         int[] data = layout.get(i);
         int structType = data[0];
         int minX = data[5];
         int minZ = data[6];
         int maxX = data[7];
         int maxZ = data[8];
         if (structType != 1 && minX != -1) {
            minX -= 2;
            minZ -= 2;
            maxX += 2;
            maxZ += 2;
         } else {
            int placeX = data[1];
            int placeZ = data[3];
            int gateHalf = 26;
            minX = placeX - gateHalf;
            minZ = placeZ - gateHalf;
            maxX = placeX + gateHalf;
            maxZ = placeZ + gateHalf;
         }

         int minChunkX = minX >> 4;
         int maxChunkX = maxX >> 4;
         int minChunkZ = minZ >> 4;
         int maxChunkZ = maxZ >> 4;

         for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
               long key = packChunkPos(cx, cz);
               tempIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
         }
      }

      Map<Long, int[]> index = new HashMap<>(tempIndex.size());

      for (Entry<Long, List<Integer>> entry : tempIndex.entrySet()) {
         List<Integer> list = entry.getValue();
         int[] arr = new int[list.size()];

         for (int j = 0; j < list.size(); j++) {
            arr[j] = list.get(j);
         }

         index.put(entry.getKey(), arr);
      }

      return index;
   }

   private static int[][] buildPlacedRects(List<int[]> layout) {
      int[][] rects = new int[layout.size()][];

      for (int i = 0; i < layout.size(); i++) {
         int[] data = layout.get(i);
         if (data[0] == 1) {
            rects[i] = new int[]{-1, -1, -1, -1};
         } else {
            rects[i] = new int[]{data[5], data[6], data[7], data[8]};
         }
      }

      return rects;
   }

   private List<int[]> getVillageLayout(int centerX, int centerZ) {
      String key = centerX + "," + centerZ;
      return villageLayoutCache.computeIfAbsent(key, k -> this.calculateVillageLayout(centerX, centerZ));
   }

   private List<int[]> calculateVillageLayout(int centerX, int centerZ) {
      List<int[]> layout = new ArrayList<>();
      java.util.Random rand = new java.util.Random(positionSeed(centerX * 7919, centerZ * 7927));
      int pathHalf = 5;
      int gap = 5;
      int margin = 6;
      int halfWidthBase = 300;
      int halfDepthBase = 300;
      int sideWallLength = 300;
      int[] district = findDistrictByCenter(centerX, centerZ);
      int dirX = district != null ? district[4] : 0;
      int dirZ = district != null ? district[5] : -1;
      int tangX = -dirZ;
      int halfXBound;
      int halfZBound;
      if (dirX != 0) {
         halfXBound = 294;
         halfZBound = 294;
      } else {
         halfXBound = 294;
         halfZBound = 294;
      }

      int wallLimit = Math.min(halfXBound, halfZBound);
      int fHalfWidth = 294;
      long fHalfWidthSq = 86436L;
      int fSideWallLength = 300;
      int fHalfDepth = 300;
      BiFunction<Integer, Integer, Boolean> insideU = (wx, wz) -> {
         int ddx = wx - centerX;
         int ddz = wz - centerZ;
         int rLocal = ddx * dirX + ddz * dirZ + 300;
         int tLocal = ddx * tangX + ddz * dirX;
         if (rLocal < 6) {
            return false;
         } else if (rLocal > 594) {
            return false;
         } else if (rLocal <= 300) {
            return Math.abs(tLocal) <= 294;
         } else {
            int capDx = rLocal - 300;
            return (long)capDx * capDx + (long)tLocal * tLocal <= 86436L;
         }
      };
      int villageType = getVillageType(centerX, centerZ);
      int smallHouseLength;
      int smallHouseWidth;
      int largeHouseLength;
      int largeHouseWidth;
      int largeHouseType;
      int[] houseTypesPool;
      if (villageType == 1) {
         smallHouseLength = 12;
         smallHouseWidth = 22;
         largeHouseLength = 22;
         largeHouseWidth = 19;
         largeHouseType = -2;
         houseTypesPool = RANDOM_SHIG_HOUSE_TYPES;
      } else {
         smallHouseLength = 22;
         smallHouseWidth = 10;
         largeHouseLength = 22;
         largeHouseWidth = 19;
         largeHouseType = 5;
         houseTypesPool = RANDOM_HOUSE_TYPES;
      }

      int rowStep = Math.min(smallHouseLength, smallHouseWidth) + gap;
      List<int[]> placedRects = new ArrayList<>();
      int castleX = centerX - 24;
      int castleZ = centerZ - 24;
      int castleY = 64
         + (int)Math.round(this.getHeightAt(centerX, centerZ))
         + 1
         + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(centerX, centerZ, centerX, centerZ)) : 0);
      int castleMinX = centerX - 24;
      int castleMinZ = centerZ - 24;
      int castleMaxX = centerX + 24;
      int castleMaxZ = centerZ + 24;
      layout.add(new int[]{0, castleX, castleY, castleZ, 0, castleMinX, castleMinZ, castleMaxX, castleMaxZ});
      placedRects.add(new int[]{castleMinX, castleMinZ, castleMaxX, castleMaxZ});
      int[][] gates = this.getVillageGates(centerX, centerZ);
      int[] gateDistrict = findDistrictByCenter(centerX, centerZ);
      int gateRingIdx = gateDistrict != null ? gateDistrict[0] : 0;

      for (int[] gate : gates) {
         int gateX = gate[0];
         int gateZ = gate[1];
         int gateIndex = gate[4];

         int zDir = switch (gateIndex) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 2;
            default -> 0;
         };
         int wallCenterOffset = (WALL_OUTER_RADIUS - 291) / 2 + 1;
         int gateDirX = gate[2];
         int gateDirZ = gate[3];
         int wallCenterX = gateX - gateDirX * wallCenterOffset;
         int wallCenterZ = gateZ - gateDirZ * wallCenterOffset;
         int gateY = 64
            + (int)Math.round(this.getHeightAt(wallCenterX, wallCenterZ))
            + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(wallCenterX, wallCenterZ, centerX, centerZ)) : 0);
         layout.add(new int[]{1, wallCenterX, gateY, wallCenterZ, zDir, gateRingIdx, -1, -1, -1});
      }

      int fHalfX = halfXBound;
      int fHalfZ = halfZBound;
      Function<int[], Boolean> canPlace = rect -> {
         int minXx = rect[0];
         int minZx = rect[1];
         int maxXx = rect[2];
         int maxZx = rect[3];
         if (!insideU.apply(minXx, minZx)) {
            return false;
         } else if (!insideU.apply(maxXx, minZx)) {
            return false;
         } else if (!insideU.apply(minXx, maxZx)) {
            return false;
         } else if (!insideU.apply(maxXx, maxZx)) {
            return false;
         } else if (maxXx + gap > centerX - pathHalf && minXx - gap < centerX + pathHalf) {
            return false;
         } else if (maxZx + gap > centerZ - pathHalf && minZx - gap < centerZ + pathHalf) {
            return false;
         } else {
            for (int[] placed : placedRects) {
               if (maxXx + gap > placed[0] && minXx - gap < placed[2] && maxZx + gap > placed[1] && minZx - gap < placed[3]) {
                  return false;
               }
            }

            return true;
         }
      };

      for (int quadrant = 0; quadrant < 4; quadrant++) {
         int xDir = quadrant != 0 && quadrant != 2 ? -1 : 1;
         int zDir = quadrant != 0 && quadrant != 1 ? -1 : 1;
         int startX = centerX + xDir * (24 + gap + 1);
         int startZ = centerZ + zDir * (pathHalf + gap + 1);
         int currentX = startX;

         for (int rowCount = 0; rowCount < 20; rowCount++) {
            int currentZ = startZ;

            for (int colCount = 0; colCount < 20 && Math.abs(currentX - centerX) <= fHalfX - 6 && Math.abs(currentZ - centerZ) <= fHalfZ - 6; colCount++) {
               boolean tryLargeFirst = (rowCount + colCount) % 3 == 0;
               boolean useRotation = (rowCount + colCount) % 2 == 1;
               boolean placed = false;
               int lgL = largeHouseLength;
               int lgW = largeHouseWidth;
               int lgT = largeHouseType;
               if (villageType == 1) {
                  lgT = RANDOM_SHIG_LARGE_TYPES[rand.nextInt(RANDOM_SHIG_LARGE_TYPES.length)];
                  int[] fp = getShigLargeFootprint(lgT);
                  lgL = fp[0];
                  lgW = fp[1];
               }

               int[][][] structureOptions = tryLargeFirst
                  ? new int[][][]{{{lgL, lgW, lgT}}, {{smallHouseLength, smallHouseWidth, -1}}, {{7, 7, 6}}}
                  : new int[][][]{{{smallHouseLength, smallHouseWidth, -1}}, {{lgL, lgW, lgT}}, {{7, 7, 6}}};

               for (int[][] structOption : structureOptions) {
                  if (placed) {
                     break;
                  }

                  int baseLength = structOption[0][0];
                  int baseWidth = structOption[0][1];
                  int structType = structOption[0][2];

                  for (int rotIdx = 0; rotIdx < 2; rotIdx++) {
                     boolean rotate = rotIdx == 1 ^ useRotation;
                     int length = rotate ? baseWidth : baseLength;
                     int width = rotate ? baseLength : baseWidth;
                     int minX;
                     int maxX;
                     if (xDir > 0) {
                        minX = currentX;
                        maxX = currentX + length - 1;
                     } else {
                        maxX = currentX;
                        minX = currentX - length + 1;
                     }

                     int minZ;
                     int maxZ;
                     if (zDir > 0) {
                        minZ = currentZ;
                        maxZ = currentZ + width - 1;
                     } else {
                        maxZ = currentZ;
                        minZ = currentZ - width + 1;
                     }

                     int[] bounds = new int[]{minX, minZ, maxX, maxZ};
                     if (canPlace.apply(bounds)) {
                        int structCenterX = (minX + maxX) / 2;
                        int structCenterZ = (minZ + maxZ) / 2;
                        int structY = 64
                           + (int)Math.round(this.getHeightAt(structCenterX, structCenterZ))
                           + 1
                           + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(structCenterX, structCenterZ, centerX, centerZ)) : 0);
                        int rotationId = rotate ? 1 : 0;
                        int placeX = rotate ? maxX : minX;
                        int finalType = structType == -1
                           ? houseTypesPool[rand.nextInt(houseTypesPool.length)]
                           : (structType == -2 ? RANDOM_SHIG_LARGE_TYPES[rand.nextInt(RANDOM_SHIG_LARGE_TYPES.length)] : structType);
                        layout.add(new int[]{finalType, placeX, structY, minZ, rotationId, minX, minZ, maxX, maxZ});
                        placedRects.add(new int[]{minX, minZ, maxX, maxZ});
                        placed = true;
                        if (zDir > 0) {
                           currentZ = maxZ + gap + 1;
                        } else {
                           currentZ = minZ - gap - 1;
                        }
                        break;
                     }
                  }
               }

               if (!placed) {
                  currentZ += zDir * 8;
               }
            }

            currentX += xDir * rowStep;
         }
      }

      for (int quadrant = 0; quadrant < 4; quadrant++) {
         int xDir = quadrant != 0 && quadrant != 2 ? -1 : 1;
         int zDir = quadrant != 0 && quadrant != 1 ? -1 : 1;
         int pathStartX = centerX + xDir * (pathHalf + gap + 1);
         int pastCastleZ = centerZ + zDir * (24 + gap + 1);
         int stripEndX = centerX + xDir * (24 + gap);
         int currentX = pathStartX;

         for (int stripRowCount = 0; stripRowCount < 5 && (xDir <= 0 || currentX < stripEndX) && (xDir >= 0 || currentX > stripEndX); stripRowCount++) {
            int currentZ = pastCastleZ;

            for (int colCount = 0; colCount < 15 && Math.abs(currentX - centerX) <= fHalfX - 6 && Math.abs(currentZ - centerZ) <= fHalfZ - 6; colCount++) {
               boolean tryLargeFirstx = (stripRowCount + colCount) % 3 == 0;
               boolean useRotationx = (stripRowCount + colCount) % 2 == 0;
               boolean placedx = false;
               int lgLx = largeHouseLength;
               int lgWx = largeHouseWidth;
               int lgTx = largeHouseType;
               if (villageType == 1) {
                  lgTx = RANDOM_SHIG_LARGE_TYPES[rand.nextInt(RANDOM_SHIG_LARGE_TYPES.length)];
                  int[] fp = getShigLargeFootprint(lgTx);
                  lgLx = fp[0];
                  lgWx = fp[1];
               }

               int[][][] structureOptions = tryLargeFirstx
                  ? new int[][][]{{{lgLx, lgWx, lgTx}}, {{smallHouseLength, smallHouseWidth, -1}}, {{7, 7, 6}}}
                  : new int[][][]{{{smallHouseLength, smallHouseWidth, -1}}, {{lgLx, lgWx, lgTx}}, {{7, 7, 6}}};

               for (int[][] structOption : structureOptions) {
                  if (placedx) {
                     break;
                  }

                  int baseLength = structOption[0][0];
                  int baseWidth = structOption[0][1];
                  int structType = structOption[0][2];

                  for (int rotIdx = 0; rotIdx < 2; rotIdx++) {
                     boolean rotatex = rotIdx == 1 ^ useRotationx;
                     int lengthx = rotatex ? baseWidth : baseLength;
                     int widthx = rotatex ? baseLength : baseWidth;
                     int minXx;
                     int maxXx;
                     if (xDir > 0) {
                        minXx = currentX;
                        maxXx = currentX + lengthx - 1;
                     } else {
                        maxXx = currentX;
                        minXx = currentX - lengthx + 1;
                     }

                     int minZx;
                     int maxZx;
                     if (zDir > 0) {
                        minZx = currentZ;
                        maxZx = currentZ + widthx - 1;
                     } else {
                        maxZx = currentZ;
                        minZx = currentZ - widthx + 1;
                     }

                     int[] bounds = new int[]{minXx, minZx, maxXx, maxZx};
                     if (canPlace.apply(bounds)) {
                        int structCenterX = (minXx + maxXx) / 2;
                        int structCenterZ = (minZx + maxZx) / 2;
                        int structY = 64
                           + (int)Math.round(this.getHeightAt(structCenterX, structCenterZ))
                           + 1
                           + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(structCenterX, structCenterZ, centerX, centerZ)) : 0);
                        int rotationId = rotatex ? 1 : 0;
                        int placeX = rotatex ? maxXx : minXx;
                        int finalType = structType == -1
                           ? houseTypesPool[rand.nextInt(houseTypesPool.length)]
                           : (structType == -2 ? RANDOM_SHIG_LARGE_TYPES[rand.nextInt(RANDOM_SHIG_LARGE_TYPES.length)] : structType);
                        layout.add(new int[]{finalType, placeX, structY, minZx, rotationId, minXx, minZx, maxXx, maxZx});
                        placedRects.add(new int[]{minXx, minZx, maxXx, maxZx});
                        placedx = true;
                        if (zDir > 0) {
                           currentZ = maxZx + gap + 1;
                        } else {
                           currentZ = minZx - gap - 1;
                        }
                        break;
                     }
                  }
               }

               if (!placedx) {
                  currentZ += zDir * 8;
               }
            }

            currentX += xDir * rowStep;
         }
      }

      int gateZoneStart = 100;
      int gateZoneEnd = wallLimit - 5;

      for (int side = 0; side < 2; side++) {
         int xDir = side == 0 ? 1 : -1;

         for (int zSide = 0; zSide < 2; zSide++) {
            int zDir = zSide == 0 ? 1 : -1;
            int startZ = centerZ + zDir * (pathHalf + gap + 1);

            for (int xOff = gateZoneStart; xOff < gateZoneEnd; xOff += rowStep) {
               int currentX = centerX + xDir * xOff;
               int currentZ = startZ;

               for (int attempts = 0; attempts < 8 && Math.abs(currentX - centerX) <= fHalfX - 6 && Math.abs(currentZ - centerZ) <= fHalfZ - 6; attempts++) {
                  boolean placedxx = false;
                  boolean useRotationxx = attempts % 2 == 0;
                  int[][] structureOptions = new int[][]{{smallHouseLength, smallHouseWidth, -1}, {7, 7, 6}};

                  for (int[] opt : structureOptions) {
                     if (placedxx) {
                        break;
                     }

                     int baseLength = opt[0];
                     int baseWidth = opt[1];
                     int structType = opt[2];

                     for (int rotIdx = 0; rotIdx < 2; rotIdx++) {
                        boolean rotatexx = rotIdx == 1 ^ useRotationxx;
                        int lengthxx = rotatexx ? baseWidth : baseLength;
                        int widthxx = rotatexx ? baseLength : baseWidth;
                        int minXxx;
                        int maxXxx;
                        if (xDir > 0) {
                           minXxx = currentX;
                           maxXxx = currentX + lengthxx - 1;
                        } else {
                           maxXxx = currentX;
                           minXxx = currentX - lengthxx + 1;
                        }

                        int minZxx;
                        int maxZxx;
                        if (zDir > 0) {
                           minZxx = currentZ;
                           maxZxx = currentZ + widthxx - 1;
                        } else {
                           maxZxx = currentZ;
                           minZxx = currentZ - widthxx + 1;
                        }

                        int[] bounds = new int[]{minXxx, minZxx, maxXxx, maxZxx};
                        if (canPlace.apply(bounds)) {
                           int structCenterX = (minXxx + maxXxx) / 2;
                           int structCenterZ = (minZxx + maxZxx) / 2;
                           int structY = 64
                              + (int)Math.round(this.getHeightAt(structCenterX, structCenterZ))
                              + 1
                              + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(structCenterX, structCenterZ, centerX, centerZ)) : 0);
                           int rotationId = rotatexx ? 1 : 0;
                           int placeX = rotatexx ? maxXxx : minXxx;
                           int finalType = structType == -1
                              ? houseTypesPool[rand.nextInt(houseTypesPool.length)]
                              : (structType == -2 ? RANDOM_SHIG_LARGE_TYPES[rand.nextInt(RANDOM_SHIG_LARGE_TYPES.length)] : structType);
                           layout.add(new int[]{finalType, placeX, structY, minZxx, rotationId, minXxx, minZxx, maxXxx, maxZxx});
                           placedRects.add(new int[]{minXxx, minZxx, maxXxx, maxZxx});
                           placedxx = true;
                           if (zDir > 0) {
                              currentZ = maxZxx + gap + 1;
                           } else {
                              currentZ = minZxx - gap - 1;
                           }
                           break;
                        }
                     }
                  }

                  if (!placedxx) {
                     currentZ += zDir * 10;
                  }
               }
            }
         }
      }

      for (int side = 0; side < 2; side++) {
         int zDir = side == 0 ? 1 : -1;

         for (int xSide = 0; xSide < 2; xSide++) {
            int xDir = xSide == 0 ? 1 : -1;
            int startX = centerX + xDir * (pathHalf + gap + 1);

            for (int zOff = gateZoneStart; zOff < gateZoneEnd; zOff += rowStep) {
               int currentZ = centerZ + zDir * zOff;
               int currentX = startX;

               for (int attempts = 0; attempts < 8 && Math.abs(currentX - centerX) <= fHalfX - 6 && Math.abs(currentZ - centerZ) <= fHalfZ - 6; attempts++) {
                  boolean placedxx = false;
                  boolean useRotationxx = attempts % 2 == 1;
                  int[][] structureOptions = new int[][]{{smallHouseLength, smallHouseWidth, -1}, {7, 7, 6}};

                  for (int[] opt : structureOptions) {
                     if (placedxx) {
                        break;
                     }

                     int baseLength = opt[0];
                     int baseWidth = opt[1];
                     int structType = opt[2];

                     for (int rotIdx = 0; rotIdx < 2; rotIdx++) {
                        boolean rotatexxx = rotIdx == 1 ^ useRotationxx;
                        int lengthxxx = rotatexxx ? baseWidth : baseLength;
                        int widthxxx = rotatexxx ? baseLength : baseWidth;
                        int minXxxx;
                        int maxXxxx;
                        if (xDir > 0) {
                           minXxxx = currentX;
                           maxXxxx = currentX + lengthxxx - 1;
                        } else {
                           maxXxxx = currentX;
                           minXxxx = currentX - lengthxxx + 1;
                        }

                        int minZxxx;
                        int maxZxxx;
                        if (zDir > 0) {
                           minZxxx = currentZ;
                           maxZxxx = currentZ + widthxxx - 1;
                        } else {
                           maxZxxx = currentZ;
                           minZxxx = currentZ - widthxxx + 1;
                        }

                        int[] bounds = new int[]{minXxxx, minZxxx, maxXxxx, maxZxxx};
                        if (canPlace.apply(bounds)) {
                           int structCenterX = (minXxxx + maxXxxx) / 2;
                           int structCenterZ = (minZxxx + maxZxxx) / 2;
                           int structY = 64
                              + (int)Math.round(this.getHeightAt(structCenterX, structCenterZ))
                              + 1
                              + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(structCenterX, structCenterZ, centerX, centerZ)) : 0);
                           int rotationId = rotatexxx ? 1 : 0;
                           int placeX = rotatexxx ? maxXxxx : minXxxx;
                           int finalType = structType == -1
                              ? houseTypesPool[rand.nextInt(houseTypesPool.length)]
                              : (structType == -2 ? RANDOM_SHIG_LARGE_TYPES[rand.nextInt(RANDOM_SHIG_LARGE_TYPES.length)] : structType);
                           layout.add(new int[]{finalType, placeX, structY, minZxxx, rotationId, minXxxx, minZxxx, maxXxxx, maxZxxx});
                           placedRects.add(new int[]{minXxxx, minZxxx, maxXxxx, maxZxxx});
                           placedxx = true;
                           if (xDir > 0) {
                              currentX = maxXxxx + gap + 1;
                           } else {
                              currentX = minXxxx - gap - 1;
                           }
                           break;
                        }
                     }
                  }

                  if (!placedxx) {
                     currentX += xDir * 10;
                  }
               }
            }
         }
      }

      for (int quadrant = 0; quadrant < 4; quadrant++) {
         int xDir = quadrant != 0 && quadrant != 2 ? -1 : 1;
         int zDir = quadrant != 0 && quadrant != 1 ? -1 : 1;

         for (int xOff = 35; xOff < 130; xOff += 15) {
            for (int zOff = 35; zOff < 130; zOff += 15) {
               int checkX = centerX + xDir * xOff;
               int checkZ = centerZ + zDir * zOff;
               if (Math.abs(checkX - centerX) <= fHalfX - 7 && Math.abs(checkZ - centerZ) <= fHalfZ - 7) {
                  int minXxxxx = checkX - 3;
                  int maxXxxxx = checkX + 3;
                  int minZxxxx = checkZ - 3;
                  int maxZxxxx = checkZ + 3;
                  int[] bounds = new int[]{minXxxxx, minZxxxx, maxXxxxx, maxZxxxx};
                  if (canPlace.apply(bounds)) {
                     int towerY = 64
                        + (int)Math.round(this.getHeightAt(checkX, checkZ))
                        + 1
                        + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(checkX, checkZ, centerX, centerZ)) : 0);
                     layout.add(new int[]{6, minXxxxx, towerY, minZxxxx, 0, minXxxxx, minZxxxx, maxXxxxx, maxZxxxx});
                     placedRects.add(bounds);
                  }
               }
            }
         }
      }

      return layout;
   }

   private static String getStructureNameFromType(int structType) {
      switch (structType) {
         case 0:
            return "castle";
         case 1:
            return "gate";
         case 2:
            return "house1";
         case 3:
            return "house2";
         case 4:
            return "house3";
         case 5:
            return "house4";
         case 6:
            return "tower";
         case 7:
            return "house5";
         case 8:
            return "shighouse1";
         case 9:
            return "shighouse2";
         case 10:
            return "shighouse3";
         case 11:
            return "shighouse4";
         case 12:
            return "big_shig1";
         case 13:
            return "big_shig2";
         case 14:
            return "big_shig3";
         case 15:
            return "big_shig4";
         default:
            return "house1";
      }
   }

   private static BlockRotation getRotationFromId(int rotationId) {
      switch (rotationId) {
         case 1:
            return BlockRotation.CLOCKWISE_90;
         case 2:
            return BlockRotation.CLOCKWISE_180;
         case 3:
            return BlockRotation.COUNTERCLOCKWISE_90;
         default:
            return BlockRotation.NONE;
      }
   }

   public static List<int[]> getVillageHousePositions(int centerX, int centerZ) {
      String key = centerX + "," + centerZ;
      return villageHousePositions.get(key);
   }

   private static void storeHousePosition(int centerX, int centerZ, int minX, int minZ, int maxX, int maxZ, int structY) {
      String key = centerX + "," + centerZ;
      villageHousePositions.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(new int[]{minX, minZ, maxX, maxZ, structY});
   }

   public static void clearStoredHousePositions() {
      villageHousePositions.clear();
      caveEntrancePositions.clear();
      villageLayoutCache.clear();
      villageChunkIndex.clear();
      villagePlacedRectsCache.clear();
   }

   private static void storeCaveEntrance(int entranceX, int entranceZ, double direction, int length) {
      String key = entranceX + "," + entranceZ;
      caveEntrancePositions.put(key, new int[]{entranceX, entranceZ, (int)(direction * 1000.0), length});
   }

   private boolean isNearCaveEntrance(int worldX, int worldZ) {
      int centerChunkX = worldX >> 4;
      int centerChunkZ = worldZ >> 4;

      for (int cx = centerChunkX - 3; cx <= centerChunkX + 3; cx++) {
         for (int cz = centerChunkZ - 3; cz <= centerChunkZ + 3; cz++) {
            long entranceSeed = worldSeed ^ cx * 987654321L ^ cz * 123456789L;
            Random entranceRandom = Random.create(entranceSeed);
            if (entranceRandom.nextInt(2000) < 1) {
               int entranceX = cx * 16 + entranceRandom.nextInt(16);
               int entranceZ = cz * 16 + entranceRandom.nextInt(16);
               if (!this.isInsideVillageWalls(entranceX, entranceZ) && !this.isNearVillage(entranceX, entranceZ, WALL_OUTER_RADIUS + 100)) {
                  double eDirection = entranceRandom.nextDouble() * Math.PI * 2.0;
                  entranceRandom.nextDouble();
                  entranceRandom.nextDouble();
                  int entranceLength = 30 + entranceRandom.nextInt(40);
                  double distFromEntrance = Math.sqrt(Math.pow(worldX - entranceX, 2.0) + Math.pow(worldZ - entranceZ, 2.0));
                  if (distFromEntrance < 8.0) {
                     return true;
                  }

                  double pathLength = Math.min(entranceLength, 25);

                  for (double d = 0.0; d < pathLength; d += 2.0) {
                     double pathX = entranceX + Math.cos(eDirection) * d;
                     double pathZ = entranceZ + Math.sin(eDirection) * d;
                     double distFromPath = Math.sqrt(Math.pow(worldX - pathX, 2.0) + Math.pow(worldZ - pathZ, 2.0));
                     if (distFromPath < 6.0) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   public ParadisChunkGenerator(
      RegistryEntry<Biome> titanCountry,
      RegistryEntry<Biome> giantForest,
      RegistryEntry<Biome> titanHighlands,
      Optional<RegistryEntry<Biome>> titanHills,
      Optional<RegistryEntry<Biome>> titanMountains
   ) {
      super(new ParadisBiomeSource(titanCountry, giantForest, titanHighlands, titanHills, titanMountains));
      this.titanCountry = titanCountry;
      this.giantForest = giantForest;
      this.titanHighlands = titanHighlands;
      this.titanHillsOpt = titanHills;
      this.titanMountainsOpt = titanMountains;
   }

   private static double hash(int x, int z) {
      long seedMix = worldSeed * 6364136223846793005L + 1442695040888963407L;
      int seedX = (int)(seedMix & 65535L);
      int seedZ = (int)(seedMix >> 16 & 65535L);
      int n = (x + seedX) * 374761393 + (z + seedZ) * 668265263;
      n = (n ^ n >> 13) * 1274126177;
      return (n & 2147483647) / 2.147483647E9;
   }

   private static double smoothstep(double t) {
      return t * t * (3.0 - 2.0 * t);
   }

   private static double valueNoise(double x, double z) {
      int ix = (int)Math.floor(x);
      int iz = (int)Math.floor(z);
      double fx = smoothstep(x - ix);
      double fz = smoothstep(z - iz);
      double v00 = hash(ix, iz);
      double v10 = hash(ix + 1, iz);
      double v01 = hash(ix, iz + 1);
      double v11 = hash(ix + 1, iz + 1);
      double i1 = v00 + (v10 - v00) * fx;
      double i2 = v01 + (v11 - v01) * fx;
      return i1 + (i2 - i1) * fz;
   }

   private static double fractalNoise(double x, double z, int octaves) {
      double value = 0.0;
      double amplitude = 1.0;
      double frequency = 1.0;
      double maxValue = 0.0;

      for (int i = 0; i < octaves; i++) {
         value += valueNoise(x * frequency, z * frequency) * amplitude;
         maxValue += amplitude;
         amplitude *= 0.5;
         frequency *= 2.0;
      }

      return value / maxValue;
   }

   private double getHeightAt(int worldX, int worldZ) {
      double noise1 = fractalNoise(worldX * 0.003, worldZ * 0.002, 3);
      double noise2 = fractalNoise(worldX * 0.0015 + 100.0, worldZ * 0.0025 + 100.0, 2);
      double noise3 = fractalNoise(worldX * 0.005 + worldZ * 0.001, worldZ * 0.004 - worldX * 0.001, 2);
      double combinedNoise = noise1 * 0.5 + noise2 * 0.3 + noise3 * 0.2;
      double height = (combinedNoise * 2.0 - 1.0) * 10.0;
      double biomeNoise = fractalNoise(worldX * 3.0E-4, worldZ * 3.0E-4, 2);
      double forestFactor = 0.0;
      if (biomeNoise > 0.55) {
         if (biomeNoise > 0.65) {
            forestFactor = 1.0;
         } else {
            forestFactor = (biomeNoise - 0.55) / 0.1;
         }

         double forestNoise1 = fractalNoise(worldX * 0.008, worldZ * 0.008, 3);
         double forestNoise2 = fractalNoise(worldX * 0.015 + 50.0, worldZ * 0.012 + 50.0, 2);
         double forestDepthNoise = forestNoise1 * 0.7 + forestNoise2 * 0.3;
         double extraDepth = (forestDepthNoise * 2.0 - 1.0) * 15.0 * forestFactor;
         height += extraDepth;
      }

      if (biomeNoise < 0.6) {
         double titanFactor = biomeNoise < 0.5 ? 1.0 : (0.6 - biomeNoise) / 0.1;
         double hillRegionNoise = fractalNoise(worldX * 8.0E-4 + 200.0, worldZ * 8.0E-4 + 200.0, 2);
         if (hillRegionNoise > 0.55) {
            double hillStrength = Math.min(1.0, (hillRegionNoise - 0.55) / 0.15) * titanFactor;
            double hillShape1 = fractalNoise(worldX * 0.006 + 300.0, worldZ * 0.005 + 300.0, 3);
            double hillShape2 = fractalNoise(worldX * 0.014 + 400.0, worldZ * 0.011 + 400.0, 2);
            double hillShape = hillShape1 * 0.65 + hillShape2 * 0.35;
            height += (hillShape * 2.0 - 1.0) * 20.0 * hillStrength;
         }
      }

      double highlandNoise = fractalNoise(worldX * 8.0E-4 + 500.0, worldZ * 8.0E-4 + 500.0, 2);
      if (highlandNoise > 0.55) {
         double highlandFactor = highlandNoise > 0.65 ? 1.0 : (highlandNoise - 0.55) / 0.1;
         double forestFadeOut = biomeNoise <= 0.55 ? 1.0 : (biomeNoise >= 0.65 ? 0.0 : 1.0 - (biomeNoise - 0.55) / 0.1);
         highlandFactor *= forestFadeOut;
         if (highlandFactor > 0.0) {
            height += 18.0 * highlandFactor;
            double hRoll1 = fractalNoise(worldX * 0.007 + 700.0, worldZ * 0.006 + 700.0, 3);
            double hRoll2 = fractalNoise(worldX * 0.018 + 800.0, worldZ * 0.015 + 800.0, 2);
            double hRoll = hRoll1 * 0.6 + hRoll2 * 0.4;
            height += (hRoll * 2.0 - 1.0) * 10.0 * highlandFactor;
         }
      }

      if (DMNK) {
         double dist = distToWallsAndDistricts(worldX, worldZ);
         double hillF = elevatedFactor(hillsMaskNoise(worldX, worldZ), 0.7, dist);
         double mtnF = elevatedFactor(mountainsMaskNoise(worldX, worldZ), 0.72, dist);
         if (hillF > 0.0 || mtnF > 0.0) {
            double hr1 = ridgedNoise(fractalNoise(worldX * 0.0016 + 1300.0, worldZ * 0.0016 + 1300.0, 4));
            double hr2 = ridgedNoise(fractalNoise(worldX * 0.004 + 1700.0, worldZ * 0.004 + 1700.0, 3));
            double hRidge = hr1 * 0.72 + hr2 * 0.28;
            double hillsOffset = 25.0 + hRidge * 161.0;
            double mr1 = jaggedRidge(fractalNoise(worldX * 0.0022 + 2300.0, worldZ * 0.0022 + 2300.0, 5));
            double mr2 = jaggedRidge(fractalNoise(worldX * 0.0055 + 2700.0, worldZ * 0.0055 + 2700.0, 4));
            double mr3 = jaggedRidge(fractalNoise(worldX * 0.012 + 3100.0, worldZ * 0.012 + 3100.0, 3));
            double mRidge = mr1 * 0.58 + mr2 * 0.3 + mr3 * 0.12;
            double mountainsOffset = 40.0 + mRidge * 271.0;
            double hEff = hillF * (1.0 - mtnF);
            double totalW = hEff + mtnF;
            height = height * (1.0 - totalW) + hillsOffset * hEff + mountainsOffset * mtnF;
         }
      }

      return height;
   }

   private int[] getNearestVillageCenter(int worldX, int worldZ) {
      return getNearestVillageCenterStatic(worldX, worldZ);
   }

   private static long positionSeed(int x, int z) {
      return worldSeed ^ x * 341873128712L + z * 132897987541L;
   }

   public static void setWorldSeed(long seed) {
      worldSeed = seed;
   }

   private java.util.Random getBlockRandom(int x, int y, int z) {
      return new java.util.Random(positionSeed(x * 1000 + y, z));
   }

   private BlockState getWallBrickBlock(int x, int y, int z) {
      java.util.Random rand = this.getBlockRandom(x, y, z);
      return rand.nextFloat() < 0.2F ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
   }

   private BlockState getWallStoneBlock(int x, int y, int z) {
      java.util.Random rand = this.getBlockRandom(x, y, z);
      return rand.nextFloat() < 0.5F ? Blocks.ANDESITE.getDefaultState() : Blocks.STONE.getDefaultState();
   }

   private BlockState getVillageGroundBlock(int x, int z) {
      java.util.Random rand = new java.util.Random(positionSeed(x, z));
      float roll = rand.nextFloat();
      if (roll < 0.375F) {
         return Blocks.GRAVEL.getDefaultState();
      } else if (roll < 0.75F) {
         return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
      } else {
         return roll < 0.775F ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.STONE.getDefaultState();
      }
   }

   private BlockState getCrossroadPathBlock(int x, int z) {
      java.util.Random rand = new java.util.Random(positionSeed(x, z));
      float roll = rand.nextFloat();
      if (roll < 0.2F) {
         return Blocks.COARSE_DIRT.getDefaultState();
      } else if (roll < 0.7F) {
         return Blocks.PACKED_MUD.getDefaultState();
      } else {
         return roll < 0.8F ? Blocks.GRAVEL.getDefaultState() : Blocks.ROOTED_DIRT.getDefaultState();
      }
   }

   private BlockState getInnerPathBlock(int x, int z) {
      return this.getVillageGroundBlock(x, z);
   }

   private BlockState getOuterPathBlock(int x, int z) {
      return this.getCrossroadPathBlock(x, z);
   }

   private BlockState getPathEdgeBlock(int x, int z) {
      java.util.Random rand = new java.util.Random(positionSeed(x, z));
      return rand.nextFloat() < 0.6F ? Blocks.GRASS_BLOCK.getDefaultState() : this.getOuterPathBlock(x, z);
   }

   private BlockState getCrossroadSlabBlock(int x, int z) {
      java.util.Random rand = new java.util.Random(positionSeed(x, z));
      float roll = rand.nextFloat();
      if (roll < 0.4F) {
         return Blocks.STONE_SLAB.getDefaultState();
      } else {
         return roll < 0.75F ? Blocks.STONE_BRICK_SLAB.getDefaultState() : Blocks.ANDESITE_SLAB.getDefaultState();
      }
   }

   private boolean shouldSpawnTreeCluster(int gridX, int gridZ) {
      java.util.Random random = new java.util.Random(positionSeed(gridX * 7919, gridZ * 7927));
      return random.nextDouble() < 0.15;
   }

   private boolean isMegaCluster(int clusterCenterX, int clusterCenterZ) {
      java.util.Random random = new java.util.Random(positionSeed(clusterCenterX * 6143, clusterCenterZ * 6151));
      return random.nextDouble() < 0.2;
   }

   private int[][] getTreePositionsInCluster(int clusterCenterX, int clusterCenterZ) {
      java.util.Random random = new java.util.Random(positionSeed(clusterCenterX, clusterCenterZ));
      boolean mega = this.isMegaCluster(clusterCenterX, clusterCenterZ);
      int numTrees;
      double lineLength;
      double perpSpread;
      if (mega) {
         numTrees = 15 + random.nextInt(26);
         lineLength = 80.0 + random.nextDouble() * 120.0;
         perpSpread = 40.0;
      } else {
         numTrees = 3 + random.nextInt(6);
         lineLength = 20.0 + random.nextDouble() * 30.0;
         perpSpread = 10.0;
      }

      int[][] positions = new int[numTrees][2];
      double angle = random.nextDouble() * Math.PI;

      for (int i = 0; i < numTrees; i++) {
         double t = random.nextDouble();
         double perpOffset = (random.nextDouble() - 0.5) * perpSpread;
         positions[i][0] = clusterCenterX + (int)(Math.cos(angle) * lineLength * t + Math.sin(angle) * perpOffset);
         positions[i][1] = clusterCenterZ + (int)(Math.sin(angle) * lineLength * t - Math.cos(angle) * perpOffset);
      }

      return positions;
   }

   private boolean isInChunk(int worldX, int worldZ, int chunkX, int chunkZ) {
      int minX = chunkX * 16;
      int maxX = minX + 15;
      int minZ = chunkZ * 16;
      int maxZ = minZ + 15;
      return worldX >= minX && worldX <= maxX && worldZ >= minZ && worldZ <= maxZ;
   }

   private int[] getClusterCenter(int gridX, int gridZ, int gridSpacing) {
      if (!this.shouldSpawnTreeCluster(gridX, gridZ)) {
         return null;
      } else {
         java.util.Random gridRandom = new java.util.Random(positionSeed(gridX * 3571, gridZ * 3581));
         return new int[]{gridX * gridSpacing + gridRandom.nextInt(gridSpacing), gridZ * gridSpacing + gridRandom.nextInt(gridSpacing)};
      }
   }

   private static int getVillageType(int centerX, int centerZ) {
      java.util.Random rand = new java.util.Random(positionSeed(centerX * 6131, centerZ * 6143));
      return rand.nextInt(2);
   }

   private double getShiganshinaMoundHeight(int worldX, int worldZ, int centerX, int centerZ) {
      return this.getShiganshinaMoundHeight(worldX, worldZ, centerX, centerZ, this.getHeightAt(worldX, worldZ));
   }

   private double getShiganshinaMoundHeight(int worldX, int worldZ, int centerX, int centerZ, double localHeight) {
      double dx = worldX - centerX;
      double dz = worldZ - centerZ;
      double distSq = dx * dx + dz * dz;
      if (distSq >= 77284.0) {
         return 0.0;
      } else {
         double dist = Math.sqrt(distSq);
         double blend = 0.5 * (1.0 + Math.cos(Math.PI * dist / 278.0));
         double cosineHeight = 10.0 * blend;
         if (centerX != this.cachedMoundCenterX || centerZ != this.cachedMoundCenterZ) {
            this.cachedMoundCenterX = centerX;
            this.cachedMoundCenterZ = centerZ;
            this.cachedMoundCenterHeight = this.getHeightAt(centerX, centerZ);
         }

         double terrainCorrection = blend * (this.cachedMoundCenterHeight - localHeight);
         return cosineHeight + terrainCorrection;
      }
   }

   private boolean shouldSpawnVillage(int gridX, int gridZ) {
      return false;
   }

   private int[] getVillageCenter(int gridX, int gridZ) {
      int cellMinX = gridX * 6000;
      int cellMaxX = cellMinX + 6000;
      int cellMinZ = gridZ * 6000;
      int cellMaxZ = cellMinZ + 6000;

      for (int[] d : DISTRICTS) {
         int cx = d[2];
         int cz = d[3];
         if (cx >= cellMinX && cx < cellMaxX && cz >= cellMinZ && cz < cellMaxZ) {
            return new int[]{cx, cz};
         }
      }

      return null;
   }

   private static boolean isInsideDistrictInterior(int worldX, int worldZ) {
      return isInsideDistrictInterior(worldX, worldZ, 0);
   }

   private static boolean isInsideDistrictInterior(int worldX, int worldZ, int padding) {
      long dx = worldX - 0;
      long dz = worldZ - 0;
      long distSq = dx * dx + dz * dz;
      int smallest = Math.max(0, RING_RADII[0] - padding);
      int largest = RING_RADII[RING_RADII.length - 1] + 600 + padding;
      if (distSq < (long)smallest * smallest) {
         return false;
      } else if (distSq > (long)(largest + 2) * (largest + 2)) {
         return false;
      } else {
         double theta = Math.atan2(dz, dx);
         double r = Math.sqrt(distSq);

         for (int[] d : DISTRICTS) {
            int ringIdx = d[0];
            int innerR = Math.max(0, RING_RADII[ringIdx] - padding);
            int outerR = RING_RADII[ringIdx] + 600 + padding;
            if (!(r < innerR) && !(r > outerR)) {
               double theta0 = CARDINAL_ANGLES[d[1]];
               double dTheta = Math.abs(normalizeAngle(theta - theta0));
               double halfArc = DISTRICT_HALF_ARC[ringIdx] + (r > 0.001 ? padding / r : 0.0);
               if (dTheta <= halfArc) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static boolean isOnOrNearAnyRing(int worldX, int worldZ, int padding) {
      long dx = worldX - 0;
      long dz = worldZ - 0;
      long distSq = dx * dx + dz * dz;

      for (int r = 0; r < RING_RADII.length; r++) {
         int outer = RING_RADII[r] + padding;
         int inner = RING_RADII[r] - RING_THICKNESS_BASE - padding;
         if (distSq <= (long)outer * outer && distSq >= (long)inner * inner) {
            return true;
         }
      }

      return false;
   }

   private boolean isInsideVillageWalls(int worldX, int worldZ) {
      return isInsideDistrictInterior(worldX, worldZ);
   }

   private boolean isInsideOrOnVillageWalls(int worldX, int worldZ) {
      if (DMNK) {
         return isOnOrNearAnyRing(worldX, worldZ, 2);
      } else if (isInsideDistrictInterior(worldX, worldZ)) {
         return true;
      } else {
         return isOnOrNearAnyRing(worldX, worldZ, 2) ? true : this.isOnRadialCrossroad(worldX, worldZ);
      }
   }

   private boolean isWithinVillageRadius(int worldX, int worldZ, int radius) {
      if (isInsideDistrictInterior(worldX, worldZ)) {
         return true;
      } else if (isOnOrNearAnyRing(worldX, worldZ, radius)) {
         return true;
      } else {
         long radiusSq = (long)radius * radius;

         for (int[] d : DISTRICTS) {
            int cx = d[2];
            int cz = d[3];
            int dirX = d[4];
            int halfW;
            int halfD;
            if (dirX != 0) {
               halfW = 300;
               halfD = 300;
            } else {
               halfW = 300;
               halfD = 300;
            }

            long edgeDx = Math.max(0L, Math.abs((long)(worldX - cx)) - halfW);
            long edgeDz = Math.max(0L, Math.abs((long)(worldZ - cz)) - halfD);
            if (edgeDx * edgeDx + edgeDz * edgeDz < radiusSq) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean shouldSpawnVillageStatic(int gridX, int gridZ) {
      return false;
   }

   private static boolean isGiantForestBiomeStatic(int worldX, int worldZ) {
      return isGiantForestBiomeGlobal(worldX, worldZ);
   }

   private static int[] getVillageCenterStatic(int gridX, int gridZ) {
      int cellMinX = gridX * 6000;
      int cellMaxX = cellMinX + 6000;
      int cellMinZ = gridZ * 6000;
      int cellMaxZ = cellMinZ + 6000;

      for (int[] d : DISTRICTS) {
         int cx = d[2];
         int cz = d[3];
         if (cx >= cellMinX && cx < cellMaxX && cz >= cellMinZ && cz < cellMaxZ) {
            return new int[]{cx, cz};
         }
      }

      return null;
   }

   public static boolean isPositionInsideAnyVillage(int worldX, int worldZ) {
      return isInsideDistrictInterior(worldX, worldZ, DISTRICT_WALL_THICKNESS + 2) ? true : isOnOrNearAnyRing(worldX, worldZ, 3);
   }

   public static boolean isDmnkActive() {
      return DMNK;
   }

   public static boolean isOnMegaRingWall(int worldX, int worldZ) {
      return isOnOrNearAnyRing(worldX, worldZ, 3);
   }

   public static int districtIndexAt(int worldX, int worldZ) {
      int padding = DISTRICT_WALL_THICKNESS + 2;
      long dx = worldX - 0;
      long dz = worldZ - 0;
      long distSq = dx * dx + dz * dz;
      int smallest = Math.max(0, RING_RADII[0] - padding);
      int largest = RING_RADII[RING_RADII.length - 1] + 600 + padding;
      if (distSq < (long)smallest * smallest) {
         return -1;
      } else if (distSq > (long)(largest + 2) * (largest + 2)) {
         return -1;
      } else {
         double theta = Math.atan2(dz, dx);
         double r = Math.sqrt(distSq);

         for (int i = 0; i < DISTRICTS.length; i++) {
            int[] d = DISTRICTS[i];
            int ringIdx = d[0];
            int innerR = Math.max(0, RING_RADII[ringIdx] - padding);
            int outerR = RING_RADII[ringIdx] + 600 + padding;
            if (!(r < innerR) && !(r > outerR)) {
               double theta0 = CARDINAL_ANGLES[d[1]];
               double dTheta = Math.abs(normalizeAngle(theta - theta0));
               double halfArc = DISTRICT_HALF_ARC[ringIdx] + (r > 0.001 ? padding / r : 0.0);
               if (dTheta <= halfArc) {
                  return i;
               }
            }
         }

         return -1;
      }
   }

   public static int spawnBandIndex(int worldX, int worldZ) {
      long dx = worldX - 0;
      long dz = worldZ - 0;
      long distSq = dx * dx + dz * dz;

      for (int r = 0; r < RING_RADII.length; r++) {
         if (distSq < (long)RING_RADII[r] * RING_RADII[r]) {
            return r;
         }
      }

      return -1;
   }

   public static int[] getNearestVillageCenterStatic(int worldX, int worldZ) {
      int[] nearest = null;
      long nearestSq = Long.MAX_VALUE;

      for (int[] d : DISTRICTS) {
         long dx = worldX - d[2];
         long dz = worldZ - d[3];
         long sq = dx * dx + dz * dz;
         if (sq < nearestSq) {
            nearestSq = sq;
            nearest = new int[]{d[2], d[3]};
         }
      }

      return nearest;
   }

   public static int getVillageRadius() {
      return WALL_OUTER_RADIUS;
   }

   public static boolean isNearPathStatic(int worldX, int worldZ) {
      int centerGridX = worldX / 6000;
      int centerGridZ = worldZ / 6000;
      int halfWidth = 5;
      double maxDistSq = (double)(halfWidth + 20) * (halfWidth + 20);

      for (int gridX = centerGridX - 1; gridX <= centerGridX + 1; gridX++) {
         for (int gridZ = centerGridZ - 1; gridZ <= centerGridZ + 1; gridZ++) {
            long seedPart = (worldSeed & 65535L) << 48;
            long cacheKey = seedPart | (long)(gridX & 65535) << 32 | (long)(gridZ & 65535) << 16 | (gridX ^ gridZ) & 65535;
            List<int[]> segments = pathSegmentCache.get(cacheKey);
            if (segments != null) {
               for (int[] seg : segments) {
                  int startX = seg[0];
                  int startZ = seg[1];
                  int endX = seg[2];
                  int endZ = seg[3];
                  int bMinX = Math.min(startX, endX) - 10 - 30;
                  int bMaxX = Math.max(startX, endX) + 10 + 30;
                  int bMinZ = Math.min(startZ, endZ) - 10 - 30;
                  int bMaxZ = Math.max(startZ, endZ) + 10 + 30;
                  if (worldX >= bMinX && worldX <= bMaxX && worldZ >= bMinZ && worldZ <= bMaxZ) {
                     double pathDx = endX - startX;
                     double pathDz = endZ - startZ;
                     double pathLenSq = pathDx * pathDx + pathDz * pathDz;
                     if (pathLenSq != 0.0) {
                        double pathLen = Math.sqrt(pathLenSq);
                        double ndx = pathDx / pathLen;
                        double ndz = pathDz / pathLen;
                        double px = worldX - startX;
                        double pz = worldZ - startZ;
                        double t = Math.max(0.0, Math.min(1.0, (px * ndx + pz * ndz) / pathLen));
                        double pathPointX = startX + t * pathLen * ndx;
                        double pathPointZ = startZ + t * pathLen * ndz;
                        int seed = startX * 31 + startZ * 17 + endX * 13 + endZ * 7;
                        double seedD = seed * 12345.0;
                        double noise1 = Math.sin((pathPointX + pathPointZ + seedD) * 0.008) * 12.0;
                        double noise2 = Math.sin((pathPointX - pathPointZ + seedD) * 0.005) * 8.0;
                        double rawOffset = noise1 + noise2;
                        double taperZone = 0.15;
                        double taper = 1.0;
                        if (t < taperZone) {
                           taper = t / taperZone;
                        } else if (t > 1.0 - taperZone) {
                           taper = (1.0 - t) / taperZone;
                        }

                        taper = taper * taper * (3.0 - 2.0 * taper);
                        double curveOffset = rawOffset * taper;
                        double curvedX = pathPointX + -ndz * curveOffset;
                        double curvedZ = pathPointZ + ndx * curveOffset;
                        double ddx = worldX - curvedX;
                        double ddz = worldZ - curvedZ;
                        if (ddx * ddx + ddz * ddz <= maxDistSq) {
                           return true;
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private InputStream openStructureStream(String name) throws IOException {
      if (DMNK && DMNK_TREE_SET.contains(name)) {
         Optional<ModContainer> mc = FabricLoader.getInstance().getModContainer("dmnk");
         if (mc.isPresent()) {
            Optional<Path> p = mc.get().findPath("dmnk-trees/" + name + ".nbt");
            if (p.isPresent()) {
               return Files.newInputStream(p.get());
            }
         }

         return this.getClass().getResourceAsStream("/dmnk-trees/" + name + ".nbt");
      } else {
         return this.getClass().getResourceAsStream("/data/dannys-aot/structure/" + name + ".nbt");
      }
   }

   private int dmnkForestTier(int x, int z, double edgeShrink, java.util.Random rand) {
      double maturity = fractalNoise(x * 0.0012 + 7000.0, z * 0.0012 + 7000.0, 3);
      double score = maturity * 0.62 + rand.nextDouble() * 0.28 - edgeShrink * 0.55;
      double patch = fractalNoise(x * 0.006 + 3100.0, z * 0.006 + 3100.0, 2);
      int maxTier = 4;
      if (patch < 0.3) {
         maxTier = 1;
      } else if (patch < 0.4) {
         maxTier = 2;
      }

      int tier;
      if (score < 0.2) {
         tier = 0;
      } else if (score < 0.42) {
         tier = 1;
      } else if (score < 0.68) {
         tier = 2;
      } else if (score < 0.88) {
         tier = 3;
      } else {
         tier = 4;
      }

      if (tier < 0) {
         tier = 0;
      }

      if (tier > maxTier) {
         tier = maxTier;
      }

      return tier;
   }

   private String dmnkTreeName(int tier, java.util.Random rand) {
      String[] pool = DMNK_TREE_TIERS[tier];
      return pool[rand.nextInt(pool.length)];
   }

   private StructureTemplate loadStructure(ChunkRegion region, String name) {
      if (templateCache.containsKey(name)) {
         StructureTemplate cached = templateCache.get(name);
         if (cached != null) {
            return cached;
         }
      }

      try {
         StructureTemplate var6;
         try (InputStream stream = this.openStructureStream(name)) {
            if (stream == null) {
               templateCache.put(name, null);
               return null;
            }

            NbtCompound nbt = NbtIo.readCompressed(stream);
            StructureTemplate template = new StructureTemplate();
            template.readNbt(region.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK), nbt);
            templateCache.put(name, template);
            var6 = template;
         }

         return var6;
      } catch (Exception var9) {
         templateCache.put(name, null);
         return null;
      }
   }

   private void placeStructure(ChunkRegion region, String name, int x, int y, int z) {
      this.placeStructure(region, name, x, y, z, BlockRotation.NONE);
   }

   private void placeStructure(ChunkRegion region, String name, int x, int y, int z, BlockRotation rotation) {
      StructureTemplate template = this.loadStructure(region, name);
      if (template != null) {
         Vec3i size = template.getSize();
         if (size.getX() != 0 && size.getY() != 0 && size.getZ() != 0) {
            StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setIgnoreEntities(false);
            BlockPos pos = new BlockPos(x, y, z);
            template.place(region, pos, pos, settings, region.getRandom(), 2);
         }
      }
   }

   private int[] getStructureFootprint(ChunkRegion region, String name, int originX, int originZ, BlockRotation rotation) {
      StructureTemplate template = this.loadStructure(region, name);
      if (template == null) {
         return null;
      } else {
         Vec3i size = template.getSize();
         if (size.getX() != 0 && size.getZ() != 0) {
            StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE);
            BlockBox box = template.calculateBoundingBox(settings, new BlockPos(originX, 0, originZ));
            return new int[]{box.getMinX(), box.getMinZ(), box.getMaxX(), box.getMaxZ()};
         } else {
            return null;
         }
      }
   }

   private void placeStructureChunkAware(ChunkRegion region, String name, int originX, int originY, int originZ, BlockRotation rotation, int chunkX, int chunkZ) {
      StructureTemplate template = this.loadStructure(region, name);
      if (template != null) {
         Vec3i size = template.getSize();
         if (size.getX() != 0 && size.getY() != 0 && size.getZ() != 0) {
            BlockPos origin = new BlockPos(originX, originY, originZ);
            StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE).setIgnoreEntities(false);
            BlockBox box = template.calculateBoundingBox(settings, origin);
            int chunkMinX = chunkX << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMinZ = chunkZ << 4;
            int chunkMaxZ = chunkMinZ + 15;
            if (box.getMaxX() >= chunkMinX && box.getMinX() <= chunkMaxX && box.getMaxZ() >= chunkMinZ && box.getMinZ() <= chunkMaxZ) {
               List<PalettedBlockInfoList> palettes = ((StructureTemplateAccessor)template).getPalettes();
               if (!palettes.isEmpty()) {
                  List<StructureBlockInfo> blocks = palettes.get(0).getAll();
                  int localMinX;
                  int localMaxX;
                  int localMinZ;
                  int localMaxZ;
                  switch (rotation) {
                     case CLOCKWISE_90:
                        localMinX = chunkMinZ - originZ;
                        localMaxX = chunkMaxZ - originZ;
                        localMinZ = originX - chunkMaxX;
                        localMaxZ = originX - chunkMinX;
                        break;
                     case CLOCKWISE_180:
                        localMinX = originX - chunkMaxX;
                        localMaxX = originX - chunkMinX;
                        localMinZ = originZ - chunkMaxZ;
                        localMaxZ = originZ - chunkMinZ;
                        break;
                     case COUNTERCLOCKWISE_90:
                        localMinX = originZ - chunkMaxZ;
                        localMaxX = originZ - chunkMinZ;
                        localMinZ = chunkMinX - originX;
                        localMaxZ = chunkMaxX - originX;
                        break;
                     default:
                        localMinX = chunkMinX - originX;
                        localMaxX = chunkMaxX - originX;
                        localMinZ = chunkMinZ - originZ;
                        localMaxZ = chunkMaxZ - originZ;
                  }

                  Mutable mutable = new Mutable();

                  for (StructureBlockInfo blockInfo : blocks) {
                     BlockPos localPos = blockInfo.pos();
                     int lx = localPos.getX();
                     int ly = localPos.getY();
                     int lz = localPos.getZ();
                     if (lx >= localMinX && lx <= localMaxX && lz >= localMinZ && lz <= localMaxZ) {
                        int tx;
                        int tz;
                        switch (rotation) {
                           case CLOCKWISE_90:
                              tx = -lz;
                              tz = lx;
                              break;
                           case CLOCKWISE_180:
                              tx = -lx;
                              tz = -lz;
                              break;
                           case COUNTERCLOCKWISE_90:
                              tx = lz;
                              tz = -lx;
                              break;
                           default:
                              tx = lx;
                              tz = lz;
                        }

                        int worldX = originX + tx;
                        int worldZ = originZ + tz;
                        BlockState state = blockInfo.state();
                        if (!state.isAir()) {
                           int worldY = originY + ly;
                           BlockState rotatedState = state.rotate(rotation);
                           mutable.set(worldX, worldY, worldZ);
                           region.setBlockState(mutable, rotatedState, 2);
                           Block block = rotatedState.getBlock();
                           if (block == Blocks.CHEST || block == Blocks.BARREL) {
                              this.assignContainerLoot(region, mutable.toImmutable(), worldX, worldZ);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void assignContainerLoot(ChunkRegion region, BlockPos pos, int worldX, int worldZ) {
      if (region.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
         long seed = positionSeed(worldX, worldZ) ^ pos.getY() * 987654321L;
         java.util.Random lootRand = new java.util.Random(seed);
         Identifier[] villageTables = new Identifier[]{
            LootTables.VILLAGE_PLAINS_CHEST,
            LootTables.VILLAGE_PLAINS_CHEST,
            LootTables.VILLAGE_PLAINS_CHEST,
            LootTables.VILLAGE_WEAPONSMITH_CHEST,
            LootTables.VILLAGE_TOOLSMITH_CHEST
         };
         container.setLootTable(villageTables[lootRand.nextInt(villageTables.length)], lootRand.nextLong());
         if (lootRand.nextFloat() < 0.15F) {
            int slot = lootRand.nextInt(27);
            float roll = lootRand.nextFloat();
            if (roll < 0.3F) {
               container.setStack(slot, new ItemStack(DannysAot.BLADE_COMPONENT, 1 + lootRand.nextInt(2)));
            } else if (roll < 0.55F) {
               container.setStack(slot, new ItemStack(DannysAot.ICE_BURST_CLUSTER, 1 + lootRand.nextInt(3)));
            } else if (roll < 0.8F) {
               container.setStack(slot, new ItemStack(DannysAot.EMPTY_SYRINGE));
            } else {
               container.setStack(slot, new ItemStack(DannysAot.SYRINGE));
            }
         }
      }
   }

   private boolean overlapsExisting(int minX, int minZ, int maxX, int maxZ) {
      for (int[] bounds : CHUNK_CACHE.get().houseBounds) {
         if (minX < bounds[2] && maxX > bounds[0] && minZ < bounds[3] && maxZ > bounds[1]) {
            return true;
         }
      }

      return false;
   }

   private void placeStructureFoundation(
      ChunkRegion region,
      int structureCenterX,
      int structureCenterZ,
      int structureY,
      int halfWidth,
      int halfLength,
      int chunkX,
      int chunkZ,
      boolean insideVillage
   ) {
      if (!insideVillage) {
         BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
         BlockState dirt = Blocks.DIRT.getDefaultState();
         int foundationExtend = 1;
         int smoothRadius = 4;
         int minX = structureCenterX - halfWidth - foundationExtend - smoothRadius;
         int maxX = structureCenterX + halfWidth + foundationExtend + smoothRadius;
         int minZ = structureCenterZ - halfLength - foundationExtend - smoothRadius;
         int maxZ = structureCenterZ + halfLength + foundationExtend + smoothRadius;
         int flatFoundationY = structureY - 1;

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               if (this.isInChunk(x, z, chunkX, chunkZ)) {
                  int distFromEdgeX = Math.max(0, Math.abs(x - structureCenterX) - halfWidth);
                  int distFromEdgeZ = Math.max(0, Math.abs(z - structureCenterZ) - halfLength);
                  double distFromEdge = Math.max(distFromEdgeX, distFromEdgeZ);
                  int terrainY = 64 + (int)Math.round(this.getHeightAt(x, z));
                  int foundationY;
                  if (distFromEdge <= foundationExtend) {
                     foundationY = flatFoundationY;
                  } else {
                     if (!(distFromEdge <= foundationExtend + smoothRadius)) {
                        continue;
                     }

                     double t = (distFromEdge - foundationExtend) / smoothRadius;
                     t = t * t * (3.0 - 2.0 * t);
                     foundationY = (int)Math.round(flatFoundationY * (1.0 - t) + terrainY * t);
                  }

                  BlockPos grassPos = new BlockPos(x, foundationY, z);
                  region.setBlockState(grassPos, grass, 2);

                  for (int y = foundationY - 1; y >= Math.max(terrainY - 2, foundationY - 6); y--) {
                     BlockPos dirtPos = new BlockPos(x, y, z);
                     region.setBlockState(dirtPos, dirt, 2);
                  }
               }
            }
         }
      }
   }

   private void placeVillageHouseFoundation(
      ChunkRegion region, int structureCenterX, int structureCenterZ, int structureY, int halfWidth, int halfLength, int chunkX, int chunkZ
   ) {
      int foundationExtend = 1;
      int foundationDepth = 4;
      int minX = structureCenterX - halfWidth - foundationExtend;
      int maxX = structureCenterX + halfWidth + foundationExtend;
      int minZ = structureCenterZ - halfLength - foundationExtend;
      int maxZ = structureCenterZ + halfLength + foundationExtend;
      BlockState surface = Blocks.COARSE_DIRT.getDefaultState();
      BlockState below = Blocks.DIRT.getDefaultState();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (this.isInChunk(x, z, chunkX, chunkZ)) {
               int terrainY = 64 + (int)Math.round(this.getHeightAt(x, z));
               BlockPos surfacePos = new BlockPos(x, terrainY, z);
               region.setBlockState(surfacePos, surface, 2);

               for (int y = terrainY - 1; y >= terrainY - foundationDepth; y--) {
                  BlockPos dirtPos = new BlockPos(x, y, z);
                  region.setBlockState(dirtPos, below, 2);
               }
            }
         }
      }
   }

   private void placeVillageHouseFoundationFromBounds(
      ChunkRegion region,
      int structMinX,
      int structMinZ,
      int structMaxX,
      int structMaxZ,
      int structY,
      int chunkX,
      int chunkZ,
      int villageType,
      int villageCenterX,
      int villageCenterZ
   ) {
      int foundationExtend = 1;
      int maxDepth = villageType == 1 ? 16 : 6;
      int minX = structMinX - foundationExtend;
      int maxX = structMaxX + foundationExtend;
      int minZ = structMinZ - foundationExtend;
      int maxZ = structMaxZ + foundationExtend;
      int foundationY = structY - 1;
      boolean isShinganshina = villageType == 1;
      BlockState industrialSurface = Blocks.COARSE_DIRT.getDefaultState();
      BlockState below = Blocks.DIRT.getDefaultState();
      Mutable mutable = new Mutable();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (this.isInChunk(x, z, chunkX, chunkZ)) {
               int terrainY = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ));
               mutable.set(x, foundationY, z);
               BlockState surface = isShinganshina ? this.getVillageGroundBlock(x, z) : industrialSurface;
               region.setBlockState(mutable, surface, 2);
               int bottomY = Math.max(terrainY - 2, foundationY - maxDepth);

               for (int y = foundationY - 1; y >= bottomY; y--) {
                  mutable.set(x, y, z);
                  region.setBlockState(mutable, below, 2);
               }
            }
         }
      }

      if (isShinganshina) {
         int slabMinX = minX - 1;
         int slabMaxX = maxX + 1;
         int slabMinZ = minZ - 1;
         int slabMaxZ = maxZ + 1;

         for (int x = slabMinX; x <= slabMaxX; x++) {
            for (int zx = slabMinZ; zx <= slabMaxZ; zx++) {
               if ((x < minX || x > maxX || zx < minZ || zx > maxZ) && this.isInChunk(x, zx, chunkX, chunkZ)) {
                  double slabCH = this.getCachedHeight(x, zx, chunkX, chunkZ);
                  int moundGroundY = 64
                     + (int)Math.round(slabCH)
                     + (int)Math.floor(this.getShiganshinaMoundHeight(x, zx, villageCenterX, villageCenterZ, slabCH));
                  if (foundationY > moundGroundY) {
                     mutable.set(x, moundGroundY + 1, zx);
                     if (region.getBlockState(mutable).isAir()) {
                        region.setBlockState(mutable, this.getCrossroadSlabBlock(x, zx), 2);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isOnVillagePath(int x, int z, int centerX, int centerZ, int ringRadius) {
      int pathHalfWidth = 5;
      int ringPathWidth = 8;
      int dx = x - centerX;
      int dz = z - centerZ;
      boolean onNSRoad = Math.abs(dx) <= pathHalfWidth;
      boolean onEWRoad = Math.abs(dz) <= pathHalfWidth;
      if (!onNSRoad && !onEWRoad) {
         long distSq = (long)dx * dx + (long)dz * dz;
         double halfRingWidth = ringPathWidth / 2.0;
         long ringInnerSq = (long)(ringRadius - halfRingWidth) * (long)(ringRadius - halfRingWidth);
         long ringOuterSq = (long)(ringRadius + halfRingWidth) * (long)(ringRadius + halfRingWidth);
         return distSq >= ringInnerSq && distSq <= ringOuterSq;
      } else {
         return true;
      }
   }

   private void generateVillageStructures(ChunkRegion region, int centerX, int centerZ, int chunkX, int chunkZ) {
      String villageKey = centerX + "," + centerZ;
      List<int[]> layout = this.getVillageLayout(centerX, centerZ);
      Map<Long, int[]> chunkIndex = villageChunkIndex.computeIfAbsent(villageKey, k -> buildChunkStructureIndex(layout));
      int[][] allPlacedRects = villagePlacedRectsCache.computeIfAbsent(villageKey, k -> buildPlacedRects(layout));
      int pathHalf = 5;
      int wallLimit = 282;
      int[] districtForTrees = findDistrictByCenter(centerX, centerZ);
      int treeHalfX;
      int treeHalfZ;
      if (districtForTrees != null) {
         int dirX = districtForTrees[4];
         if (dirX != 0) {
            treeHalfX = 295;
            treeHalfZ = 295;
         } else {
            treeHalfX = 295;
            treeHalfZ = 295;
         }
      } else {
         treeHalfX = 295;
         treeHalfZ = 295;
      }

      long chunkKey = packChunkPos(chunkX, chunkZ);
      int[] structureIndices = chunkIndex.get(chunkKey);
      if (structureIndices != null) {
         for (int idx : structureIndices) {
            int[] data = layout.get(idx);
            int structType = data[0];
            int placeX = data[1];
            int placeY = data[2];
            int placeZ = data[3];
            int rotationId = data[4];
            int minX = data[5];
            int minZ = data[6];
            int maxX = data[7];
            int maxZ = data[8];
            String structureName = getStructureNameFromType(structType);
            BlockRotation rotation = getRotationFromId(rotationId);
            if (structType == 0) {
               if (!DMNK) {
                  this.placeStructureFoundation(region, centerX, centerZ, placeY, 24, 24, chunkX, chunkZ, true);
                  this.placeStructureChunkAware(region, structureName, placeX, placeY, placeZ, rotation, chunkX, chunkZ);
                  CHUNK_CACHE.get().houseBounds.add(new int[]{centerX, centerZ, minX, minZ, maxX, maxZ, placeY, centerX, centerZ});
               }
            } else if (structType == 1) {
               String var71 = switch (minX) {
                  case 0 -> "gate_sina";
                  case 1 -> "gate_rose";
                  default -> "gate_maria";
               };
               StructureTemplate gateTemplate = this.loadStructure(region, var71);
               if (gateTemplate != null) {
                  StructurePlacementData settings = new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE);
                  BlockBox box = gateTemplate.calculateBoundingBox(settings, BlockPos.ORIGIN);
                  int gatePlaceX = placeX - box.getBlockCountX() / 2 - box.getMinX();
                  int gatePlaceZ = placeZ - box.getBlockCountZ() / 2 - box.getMinZ();
                  BlockBox worldBox = gateTemplate.calculateBoundingBox(settings, new BlockPos(gatePlaceX, placeY, gatePlaceZ));
                  int carveMinX = Math.max(worldBox.getMinX(), chunkX << 4);
                  int carveMaxX = Math.min(worldBox.getMaxX(), (chunkX << 4) + 15);
                  int carveMinZ = Math.max(worldBox.getMinZ(), chunkZ << 4);
                  int carveMaxZ = Math.min(worldBox.getMaxZ(), (chunkZ << 4) + 15);
                  if (carveMinX <= carveMaxX && carveMinZ <= carveMaxZ) {
                     BlockState air = Blocks.AIR.getDefaultState();
                     Mutable cp = new Mutable();

                     for (int cx = carveMinX; cx <= carveMaxX; cx++) {
                        for (int cz = carveMinZ; cz <= carveMaxZ; cz++) {
                           for (int cy = worldBox.getMinY(); cy <= worldBox.getMaxY(); cy++) {
                              cp.set(cx, cy, cz);
                              region.setBlockState(cp, air, 2);
                           }
                        }
                     }
                  }

                  this.placeStructureChunkAware(region, var71, gatePlaceX, placeY, gatePlaceZ, rotation, chunkX, chunkZ);
               }
            } else if (!DMNK) {
               int structCenterX = (minX + maxX) / 2;
               int structCenterZ = (minZ + maxZ) / 2;
               int halfL = (maxX - minX) / 2 + 1;
               int halfW = (maxZ - minZ) / 2 + 1;
               this.placeStructureFoundation(region, structCenterX, structCenterZ, placeY, halfL, halfW, chunkX, chunkZ, true);
               BlockRotation correctedRotation = rotation;
               int correctedPlaceX = placeX;
               int correctedPlaceZ = placeZ;
               if (structType >= 2 && structType <= 4 || structType == 7) {
                  if (rotation == BlockRotation.NONE) {
                     correctedRotation = BlockRotation.CLOCKWISE_90;
                     correctedPlaceX = maxX;
                     correctedPlaceZ = minZ;
                  } else if (rotation == BlockRotation.CLOCKWISE_90) {
                     correctedRotation = BlockRotation.CLOCKWISE_180;
                     correctedPlaceX = maxX;
                     correctedPlaceZ = maxZ;
                  }
               }

               this.placeStructureChunkAware(region, structureName, correctedPlaceX, placeY, correctedPlaceZ, correctedRotation, chunkX, chunkZ);
               if (structType >= 2 && structType <= 4 || structType == 7 || structType >= 8 && structType <= 16) {
                  if (rotationId == 0) {
                     int streetY = Math.max(placeY, 64 + (int)Math.round(this.getCachedHeight(maxX + 1, structCenterZ, chunkX, chunkZ)) + 1);
                     this.placeStructureChunkAware(region, "streetlight", maxX + 1, streetY, structCenterZ - 2, BlockRotation.NONE, chunkX, chunkZ);
                  } else {
                     int streetY = Math.max(placeY, 64 + (int)Math.round(this.getCachedHeight(structCenterX, maxZ + 1, chunkX, chunkZ)) + 1);
                     this.placeStructureChunkAware(region, "streetlight", structCenterX + 2, streetY, maxZ + 1, BlockRotation.CLOCKWISE_90, chunkX, chunkZ);
                  }
               }

               CHUNK_CACHE.get().houseBounds.add(new int[]{structCenterX, structCenterZ, minX, minZ, maxX, maxZ, placeY, centerX, centerZ});
               storeHousePosition(centerX, centerZ, minX, minZ, maxX, maxZ, placeY);
            }
         }
      }

      if (!DMNK) {
         int villageType = getVillageType(centerX, centerZ);
         int treeSpacing = villageType == 1 ? 21 : 30;
         int treeMargin = 2;
         int maxTxOff = Math.max(treeHalfX, treeHalfZ);

         for (int txOff = 35; txOff < maxTxOff; txOff += treeSpacing) {
            for (int tzOff = 35; tzOff < maxTxOff; tzOff += treeSpacing) {
               for (int quadrant = 0; quadrant < 4; quadrant++) {
                  int xDir = quadrant != 0 && quadrant != 2 ? -1 : 1;
                  int zDir = quadrant != 0 && quadrant != 1 ? -1 : 1;
                  int treeX = centerX + xDir * txOff;
                  int treeZ = centerZ + zDir * tzOff;
                  java.util.Random treeRand = new java.util.Random(positionSeed(treeX * 7717, treeZ * 7723));
                  treeX += treeRand.nextInt(21) - 10;
                  treeZ += treeRand.nextInt(21) - 10;
                  if (Math.abs(treeX - centerX) <= treeHalfX && Math.abs(treeZ - centerZ) <= treeHalfZ) {
                     int dx = Math.abs(treeX - centerX);
                     int dz = Math.abs(treeZ - centerZ);
                     if (dx > pathHalf + 3 && dz > pathHalf + 3 && (dx > 29 || dz > 29) && !(treeRand.nextFloat() > 0.7F)) {
                        String pineVariant = PINE_TREE_VARIANTS[treeRand.nextInt(PINE_TREE_VARIANTS.length)];
                        BlockRotation pineRot = BlockRotation.values()[treeRand.nextInt(4)];
                        int[] treeBox = this.getStructureFootprint(region, pineVariant, treeX, treeZ, pineRot);
                        if (treeBox != null) {
                           int tbMinX = treeBox[0];
                           int tbMinZ = treeBox[1];
                           int tbMaxX = treeBox[2];
                           int tbMaxZ = treeBox[3];
                           boolean overlapsBuilding = false;

                           for (int[] rect : allPlacedRects) {
                              int rMinX = rect[0] - treeMargin;
                              int rMinZ = rect[1] - treeMargin;
                              int rMaxX = rect[2] + treeMargin;
                              int rMaxZ = rect[3] + treeMargin;
                              if (tbMaxX >= rMinX && tbMinX <= rMaxX && tbMaxZ >= rMinZ && tbMinZ <= rMaxZ) {
                                 overlapsBuilding = true;
                                 break;
                              }
                           }

                           if (!overlapsBuilding) {
                              int treeY = 64
                                 + (int)Math.round(this.getHeightAt(treeX, treeZ))
                                 + 1
                                 + (villageType == 1 ? (int)Math.round(this.getShiganshinaMoundHeight(treeX, treeZ, centerX, centerZ)) : 0);
                              this.placeStructureChunkAware(region, pineVariant, treeX, treeY, treeZ, pineRot, chunkX, chunkZ);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isInsideBuilding(int x, int z, List<int[]> houses, int skipIndex) {
      for (int i = 0; i < houses.size(); i++) {
         if (i != skipIndex) {
            int[] bounds = houses.get(i);
            int bMinX = bounds[2];
            int bMinZ = bounds[3];
            int bMaxX = bounds[4];
            int bMaxZ = bounds[5];
            if (x >= bMinX && x <= bMaxX && z >= bMinZ && z <= bMaxZ) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isLineBlocked(int x1, int z1, int x2, int z2, List<int[]> houses, int skipIndex) {
      int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
      if (steps == 0) {
         return this.isInsideBuilding(x1, z1, houses, skipIndex);
      } else {
         for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int z = z1 + (z2 - z1) * i / steps;
            if (this.isInsideBuilding(x, z, houses, skipIndex)) {
               return true;
            }
         }

         return false;
      }
   }

   private int[] findDetourWaypoint(int houseX, int houseZ, int targetX, int targetZ, List<int[]> houses, int skipIndex) {
      int dx = targetX - houseX;
      int dz = targetZ - houseZ;
      int dirX = Integer.signum(dx);
      int dirZ = Integer.signum(dz);

      for (int offset = 8; offset <= 40; offset += 8) {
         int perpX = -dirZ;
         int wp1X = houseX + perpX * offset;
         int wp1Z = houseZ + dirX * offset;
         if (!this.isLineBlocked(houseX, houseZ, wp1X, wp1Z, houses, skipIndex) && !this.isLineBlocked(wp1X, wp1Z, targetX, targetZ, houses, skipIndex)) {
            return new int[]{wp1X, wp1Z};
         }

         int wp2X = houseX - perpX * offset;
         int wp2Z = houseZ - dirX * offset;
         if (!this.isLineBlocked(houseX, houseZ, wp2X, wp2Z, houses, skipIndex) && !this.isLineBlocked(wp2X, wp2Z, targetX, targetZ, houses, skipIndex)) {
            return new int[]{wp2X, wp2Z};
         }
      }

      return null;
   }

   private void generateMiniPaths(ChunkRegion region, int centerX, int centerZ, int chunkX, int chunkZ, List<int[]> houses, int ringRadius) {
      int pathHalfWidth = 5;
      int miniPathWidth = 4;

      for (int i = 1; i < houses.size(); i++) {
         int[] house = houses.get(i);
         int houseX = house[0];
         int houseZ = house[1];
         if (house.length > 6) {
            int var10000 = house[6];
         } else {
            int var34 = 64 + (int)Math.round(this.getHeightAt(houseX, houseZ)) + 1;
         }

         int relX = houseX - centerX;
         int relZ = houseZ - centerZ;
         double distFromCenter = Math.sqrt(relX * relX + relZ * relZ);
         double distToNS = Math.abs(relX);
         double distToEW = Math.abs(relZ);
         double distToRing = Math.abs(distFromCenter - ringRadius);
         int targetX;
         int targetZ;
         if (distToNS <= distToEW && distToNS <= distToRing) {
            targetX = centerX + (relX > 0 ? pathHalfWidth + 1 : -pathHalfWidth - 1);
            targetZ = houseZ;
         } else if (distToEW <= distToRing) {
            targetX = houseX;
            targetZ = centerZ + (relZ > 0 ? pathHalfWidth + 1 : -pathHalfWidth - 1);
         } else {
            double angle = Math.atan2(relZ, relX);
            targetX = centerX + (int)(Math.cos(angle) * ringRadius);
            targetZ = centerZ + (int)(Math.sin(angle) * ringRadius);
         }

         boolean pathDrawn = false;
         if (!this.isLineBlocked(houseX, houseZ, targetX, houseZ, houses, i) && !this.isLineBlocked(targetX, houseZ, targetX, targetZ, houses, i)) {
            this.drawMiniPathSegment(region, houseX, houseZ, targetX, houseZ, miniPathWidth, chunkX, chunkZ, houses, i);
            this.drawMiniPathSegment(region, targetX, houseZ, targetX, targetZ, miniPathWidth, chunkX, chunkZ, houses, i);
            pathDrawn = true;
         }

         if (!pathDrawn && !this.isLineBlocked(houseX, houseZ, houseX, targetZ, houses, i) && !this.isLineBlocked(houseX, targetZ, targetX, targetZ, houses, i)
            )
          {
            this.drawMiniPathSegment(region, houseX, houseZ, houseX, targetZ, miniPathWidth, chunkX, chunkZ, houses, i);
            this.drawMiniPathSegment(region, houseX, targetZ, targetX, targetZ, miniPathWidth, chunkX, chunkZ, houses, i);
            pathDrawn = true;
         }

         if (!pathDrawn) {
            int[] waypoint = this.findDetourWaypoint(houseX, houseZ, targetX, targetZ, houses, i);
            if (waypoint != null) {
               this.drawMiniPathSegment(region, houseX, houseZ, waypoint[0], waypoint[1], miniPathWidth, chunkX, chunkZ, houses, i);
               this.drawMiniPathSegment(region, waypoint[0], waypoint[1], targetX, targetZ, miniPathWidth, chunkX, chunkZ, houses, i);
               pathDrawn = true;
            }
         }
      }
   }

   private void drawMiniPathSegment(
      ChunkRegion region, int x1, int z1, int x2, int z2, int pathWidth, int chunkX, int chunkZ, List<int[]> houses, int skipIndex
   ) {
      int halfWidth = pathWidth / 2;
      int dx = Integer.compare(x2, x1);
      int dz = Integer.compare(z2, z1);
      if (dx == 0 || dz == 0) {
         if (dx != 0) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);

            for (int x = minX; x <= maxX; x++) {
               for (int wz = -halfWidth; wz <= halfWidth; wz++) {
                  int z = z1 + wz;
                  this.placeMiniPathBlock(region, x, z, halfWidth, Math.abs(wz), chunkX, chunkZ, houses, skipIndex);
               }
            }
         } else if (dz != 0) {
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);

            for (int z = minZ; z <= maxZ; z++) {
               for (int wx = -halfWidth; wx <= halfWidth; wx++) {
                  int x = x1 + wx;
                  this.placeMiniPathBlock(region, x, z, halfWidth, Math.abs(wx), chunkX, chunkZ, houses, skipIndex);
               }
            }
         }
      }
   }

   private void placeMiniPathBlock(
      ChunkRegion region, int x, int z, int halfWidth, int distFromCenter, int chunkX, int chunkZ, List<int[]> houses, int skipIndex
   ) {
      if (this.isInChunk(x, z, chunkX, chunkZ)) {
         if (this.isInsideVillageWalls(x, z)) {
            if (!this.isInsideBuilding(x, z, houses, skipIndex)) {
               int terrainY = 64 + (int)Math.round(this.getHeightAt(x, z));
               BlockPos pos = new BlockPos(x, terrainY, z);
               Block existingBlock = region.getBlockState(pos).getBlock();
               if (existingBlock != Blocks.GRAVEL
                  && existingBlock != Blocks.STONE_BRICKS
                  && existingBlock != Blocks.COBBLESTONE
                  && existingBlock != Blocks.DIRT_PATH
                  && existingBlock != Blocks.COARSE_DIRT) {
                  BlockState pathBlock;
                  if (distFromCenter >= halfWidth) {
                     pathBlock = this.getPathEdgeBlock(x, z);
                  } else {
                     pathBlock = this.getInnerPathBlock(x, z);
                  }

                  region.setBlockState(pos, pathBlock, 2);
                  if (pathBlock.getBlock() == Blocks.DIRT_PATH) {
                     BlockPos below = new BlockPos(x, terrainY - 1, z);
                     region.setBlockState(below, Blocks.DIRT.getDefaultState(), 2);
                  }
               }
            }
         }
      }
   }

   private void generateLargeOakTree(ChunkRegion region, int x, int y, int z, java.util.Random rand, int chunkX, int chunkZ) {
      BlockState oakLog = Blocks.OAK_LOG.getDefaultState();
      BlockState oakLeaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.DISTANCE, 1).with(LeavesBlock.PERSISTENT, false);
      int trunkHeight = 8 + rand.nextInt(6);
      if (this.isInChunk(x, z, chunkX, chunkZ)) {
         for (int dy = 0; dy < trunkHeight; dy++) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            region.setBlockState(pos, oakLog, 2);
         }
      }

      int numBranches = 3 + rand.nextInt(3);
      int branchStartY = y + trunkHeight - 4 - rand.nextInt(2);

      for (int b = 0; b < numBranches; b++) {
         double angle = b * 2.0 * Math.PI / numBranches + rand.nextDouble() * 0.5;
         int branchLength = 3 + rand.nextInt(3);
         int branchY = branchStartY + rand.nextInt(3);

         for (int i = 1; i <= branchLength; i++) {
            int bx = x + (int)(Math.cos(angle) * i);
            int bz = z + (int)(Math.sin(angle) * i);
            int by = branchY + i / 2;
            if (this.isInChunk(bx, bz, chunkX, chunkZ)) {
               BlockPos branchPos = new BlockPos(bx, by, bz);
               region.setBlockState(branchPos, oakLog, 2);
            }

            if (i >= branchLength - 1 || i > 1 && rand.nextFloat() < 0.5F) {
               this.generateLeafCluster(region, bx, by + 1, bz, 2 + rand.nextInt(2), oakLeaves, chunkX, chunkZ, rand);
            }
         }
      }

      int canopyY = y + trunkHeight;
      this.generateLeafCluster(region, x, canopyY, z, 3 + rand.nextInt(2), oakLeaves, chunkX, chunkZ, rand);

      for (int i = 0; i < 4; i++) {
         int cx = x + rand.nextInt(5) - 2;
         int cz = z + rand.nextInt(5) - 2;
         int cy = canopyY - 1 + rand.nextInt(3);
         this.generateLeafCluster(region, cx, cy, cz, 2, oakLeaves, chunkX, chunkZ, rand);
      }
   }

   private void generateLeafCluster(ChunkRegion region, int cx, int cy, int cz, int radius, BlockState leaves, int chunkX, int chunkZ, java.util.Random rand) {
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
               if (!(dist > radius + 0.5) && (!(dist > radius - 0.5) || !(rand.nextFloat() < 0.4F))) {
                  int lx = cx + dx;
                  int ly = cy + dy;
                  int lz = cz + dz;
                  if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(lx, ly, lz);
                     BlockState existing = region.getBlockState(pos);
                     if (existing.isAir() || existing.isOf(Blocks.GRASS_BLOCK) || existing.isOf(Blocks.TALL_GRASS)) {
                        region.setBlockState(pos, leaves, 2);
                     }
                  }
               }
            }
         }
      }
   }

   private void generateOakTree(ChunkRegion region, int x, int y, int z, java.util.Random rand, int chunkX, int chunkZ) {
      BlockState oakLog = Blocks.OAK_LOG.getDefaultState();
      BlockState oakLeaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.DISTANCE, 1).with(LeavesBlock.PERSISTENT, false);
      int trunkHeight = 4 + rand.nextInt(3);
      if (this.isInChunk(x, z, chunkX, chunkZ)) {
         for (int dy = 0; dy < trunkHeight; dy++) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            region.setBlockState(pos, oakLog, 2);
         }
      }

      int leafY = y + trunkHeight - 2;

      for (int layer = 0; layer < 2; layer++) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               if (Math.abs(dx) != 2 || Math.abs(dz) != 2) {
                  int lx = x + dx;
                  int lz = z + dz;
                  if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(lx, leafY + layer, lz);
                     if (region.getBlockState(pos).isAir()) {
                        region.setBlockState(pos, oakLeaves, 2);
                     }
                  }
               }
            }
         }
      }

      for (int layer = 2; layer < 4; layer++) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dzx = -1; dzx <= 1; dzx++) {
               if (layer != 3 || Math.abs(dx) != 1 || Math.abs(dzx) != 1) {
                  int lx = x + dx;
                  int lz = z + dzx;
                  if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(lx, leafY + layer, lz);
                     if (region.getBlockState(pos).isAir()) {
                        region.setBlockState(pos, oakLeaves, 2);
                     }
                  }
               }
            }
         }
      }
   }

   private static double hillsMaskNoise(int worldX, int worldZ) {
      double nx = worldX * 9.0E-4 + worldZ * 3.5E-4;
      double nz = worldZ * 3.0E-4 - worldX * 1.2E-4;
      return fractalNoise(nx + 900.0, nz + 900.0, 2);
   }

   private static double mountainsMaskNoise(int worldX, int worldZ) {
      double nx = worldX * 8.5E-4 - worldZ * 3.3E-4;
      double nz = worldZ * 2.8E-4 + worldX * 1.3E-4;
      return fractalNoise(nx + 4500.0, nz + 4500.0, 2);
   }

   private static double distToWallsAndDistricts(int worldX, int worldZ) {
      long dx = worldX - 0;
      long dz = worldZ - 0;
      double r = Math.sqrt(dx * dx + dz * dz);
      double best = Double.MAX_VALUE;

      for (int i = 0; i < RING_RADII.length; i++) {
         best = Math.min(best, Math.abs(r - RING_RADII[i]));
      }

      for (int[] d : DISTRICTS) {
         int cx = d[2];
         int cz = d[3];
         int dirX = d[4];
         int halfW;
         int halfD;
         if (dirX != 0) {
            halfW = 300;
            halfD = 300;
         } else {
            halfW = 300;
            halfD = 300;
         }

         double edx = Math.max(0.0, Math.abs((double)(worldX - cx)) - halfW);
         double edz = Math.max(0.0, Math.abs((double)(worldZ - cz)) - halfD);
         best = Math.min(best, Math.sqrt(edx * edx + edz * edz));
      }

      return best;
   }

   public static boolean isMountainsBiomeGlobal(int worldX, int worldZ) {
      if (!DMNK) {
         return false;
      } else {
         return distToWallsAndDistricts(worldX, worldZ) < 2000.0 ? false : mountainsMaskNoise(worldX, worldZ) > 0.72;
      }
   }

   public static boolean isHillsBiomeGlobal(int worldX, int worldZ) {
      if (!DMNK) {
         return false;
      } else if (distToWallsAndDistricts(worldX, worldZ) < 2000.0) {
         return false;
      } else {
         return isMountainsBiomeGlobal(worldX, worldZ) ? false : hillsMaskNoise(worldX, worldZ) > 0.7;
      }
   }

   private static double elevatedFactor(double maskNoise, double threshold, double dist) {
      if (dist < 2000.0) {
         return 0.0;
      } else {
         double lo = threshold - 0.08;
         if (maskNoise <= lo) {
            return 0.0;
         } else {
            double maskT = Math.min(1.0, (maskNoise - lo) / (threshold + 0.05 - lo));
            maskT = maskT * maskT * (3.0 - 2.0 * maskT);
            double bufT = Math.min(1.0, (dist - 2000.0) / 400.0);
            bufT = bufT * bufT * (3.0 - 2.0 * bufT);
            return maskT * bufT;
         }
      }
   }

   private static double ridgedNoise(double n) {
      double v = 1.0 - Math.abs(2.0 * n - 1.0);
      return v * v;
   }

   private static double jaggedRidge(double n) {
      double v = 1.0 - Math.abs(2.0 * n - 1.0);
      return v * v * v;
   }

   public static boolean isGiantForestBiomeGlobal(int worldX, int worldZ) {
      long dx = worldX - 0;
      long dz = worldZ - 0;
      if (dx * dx + dz * dz <= CITY_FOREST_EXCLUSION_RADIUS_SQ) {
         return false;
      } else {
         double biomeNoise = fractalNoise(worldX * 3.0E-4, worldZ * 3.0E-4, 2);
         return biomeNoise > 0.65;
      }
   }

   private boolean isGiantForestBiome(int worldX, int worldZ) {
      return isGiantForestBiomeGlobal(worldX, worldZ);
   }

   private boolean isHighlandBiome(int worldX, int worldZ) {
      double biomeNoise = fractalNoise(worldX * 3.0E-4, worldZ * 3.0E-4, 2);
      if (biomeNoise > 0.65) {
         return false;
      } else {
         double highlandNoise = fractalNoise(worldX * 8.0E-4 + 500.0, worldZ * 8.0E-4 + 500.0, 2);
         return highlandNoise > 0.65;
      }
   }

   private static double getHighlandNoise(int worldX, int worldZ) {
      return fractalNoise(worldX * 8.0E-4 + 500.0, worldZ * 8.0E-4 + 500.0, 2);
   }

   private static boolean isHighlandBiomeStatic(int worldX, int worldZ) {
      double biomeNoise = fractalNoise(worldX * 3.0E-4, worldZ * 3.0E-4, 2);
      if (biomeNoise > 0.65) {
         return false;
      } else {
         double highlandNoise = fractalNoise(worldX * 8.0E-4 + 500.0, worldZ * 8.0E-4 + 500.0, 2);
         return highlandNoise > 0.65;
      }
   }

   private boolean shouldSpawnGiantTree(int gridX, int gridZ) {
      java.util.Random random = new java.util.Random(positionSeed(gridX * 8887, gridZ * 8893));
      return random.nextDouble() < 0.85;
   }

   private boolean isOnOrNearPath(int x, int z, int radius) {
      for (int dx = -radius; dx <= radius; dx += radius) {
         for (int dz = -radius; dz <= radius; dz += radius) {
            if (this.isOnPathFast(x + dx, z + dz)) {
               return true;
            }
         }
      }

      return false;
   }

   private int[] getGiantTreeCenter(int gridX, int gridZ) {
      if (!this.shouldSpawnGiantTree(gridX, gridZ)) {
         return null;
      } else {
         java.util.Random gridRandom = new java.util.Random(positionSeed(gridX * 9511, gridZ * 9521));
         return new int[]{gridX * 38 + gridRandom.nextInt(38), gridZ * 38 + gridRandom.nextInt(38)};
      }
   }

   private int getGiantTreeRadius(int treeX, int treeZ) {
      java.util.Random rand = new java.util.Random(positionSeed(treeX * 6661, treeZ * 6673));
      return 3 + rand.nextInt(3);
   }

   private int getGiantTreeHeight(int treeX, int treeZ) {
      java.util.Random rand = new java.util.Random(positionSeed(treeX * 7753, treeZ * 7757));
      return rand.nextBoolean() ? 120 + rand.nextInt(15) - 7 : 220 + rand.nextInt(20) - 10;
   }

   private static int[] findDistrictByCenter(int centerX, int centerZ) {
      for (int[] d : DISTRICTS) {
         if (d[2] == centerX && d[3] == centerZ) {
            return d;
         }
      }

      return null;
   }

   private int wallColumnTopY(int fullTopY, int insetFromOuterFace) {
      return fullTopY;
   }

   private int[] buildDistrictSegmentTops(int[] d) {
      int ringIdx = d[0];
      int centerX = d[2];
      int centerZ = d[3];
      int dirX = d[4];
      int dirZ = d[5];
      int tangX = -dirZ;
      int tangZ = dirX;
      int halfWidth = 300;
      int halfDepth = 300;
      int sideWallLength = 600 - halfWidth;
      double halfTh = DISTRICT_WALL_THICKNESS / 2.0;
      int innerExt = DISTRICT_INNER_EXTENSION[ringIdx];
      int spacing = RIDGE_ARC_SPACING;
      int minSeg = Math.floorDiv(-innerExt, spacing);
      int maxSeg = Math.floorDiv(sideWallLength, spacing);
      double arcStep = (double)spacing / halfWidth;
      int capMax = (int)Math.floor((Math.PI / 2) / arcStep) + 1;
      int nSide = maxSeg - minSeg + 1;
      int nCap = 2 * capMax + 1;
      double capR = halfWidth + halfTh;
      int[] tops = new int[2 * nSide + nCap];
      int p = 0;

      for (int s = minSeg; s <= maxSeg; s++) {
         tops[p++] = this.rawDistrictTop(centerX, centerZ, dirX, dirZ, tangX, tangZ, (double)s * spacing - halfDepth, -(halfWidth + halfTh));
      }

      for (int c = -capMax; c <= capMax; c++) {
         double a = c * arcStep;
         tops[p++] = this.rawDistrictTop(centerX, centerZ, dirX, dirZ, tangX, tangZ, sideWallLength + capR * Math.cos(a) - halfDepth, capR * Math.sin(a));
      }

      for (int s = maxSeg; s >= minSeg; s--) {
         tops[p++] = this.rawDistrictTop(centerX, centerZ, dirX, dirZ, tangX, tangZ, (double)s * spacing - halfDepth, halfWidth + halfTh);
      }

      limitAdjacentDelta(tops, 1, false);
      return tops;
   }

   private int rawDistrictTop(int centerX, int centerZ, int dirX, int dirZ, int tangX, int tangZ, double localR, double tLocal) {
      int ax = centerX + (int)Math.round(localR * dirX + tLocal * tangX);
      int az = centerZ + (int)Math.round(localR * dirZ + tLocal * tangZ);
      return 64 + (int)Math.round(this.getHeightAt(ax, az)) + RING_HEIGHT - 1;
   }

   private int districtPathIndex(int ringIdx, int region, int seg) {
      int halfWidth = 300;
      int sideWallLength = 600 - halfWidth;
      int innerExt = DISTRICT_INNER_EXTENSION[ringIdx];
      int spacing = RIDGE_ARC_SPACING;
      int minSeg = Math.floorDiv(-innerExt, spacing);
      int maxSeg = Math.floorDiv(sideWallLength, spacing);
      double arcStep = (double)spacing / halfWidth;
      int capMax = (int)Math.floor((Math.PI / 2) / arcStep) + 1;
      int nSide = maxSeg - minSeg + 1;
      int nCap = 2 * capMax + 1;
      if (region == 1) {
         int c = Math.max(-capMax, Math.min(capMax, seg));
         return nSide + c + capMax;
      } else if (region == 2) {
         int s = Math.max(minSeg, Math.min(maxSeg, seg));
         return nSide + nCap + (maxSeg - s);
      } else {
         int s = Math.max(minSeg, Math.min(maxSeg, seg));
         return s - minSeg;
      }
   }

   private int districtSegTop(int[] d, int region, int seg) {
      int[] tops = this.districtSegmentTops.computeIfAbsent((long)d[2] << 32 | d[3] & 4294967295L, k -> this.buildDistrictSegmentTops(d));
      int p = this.districtPathIndex(d[0], region, seg);
      if (p < 0) {
         p = 0;
      }

      if (p >= tops.length) {
         p = tops.length - 1;
      }

      return tops[p];
   }

   private void placeWalkwayStepBridge(
      ChunkRegion region,
      int bx0,
      int bz0,
      double alongX,
      double alongZ,
      double acrossX,
      double acrossZ,
      int halfThickness,
      int lowTop,
      int diff,
      int chunkMinX,
      int chunkMaxX,
      int chunkMinZ,
      int chunkMaxZ
   ) {
      if (diff >= 1) {
         int reach = halfThickness + 2;
         Mutable bp = new Mutable();
         BlockState slabState = Blocks.STONE_BRICK_SLAB.getDefaultState();

         for (int bx = bx0 - reach; bx <= bx0 + reach; bx++) {
            for (int bz = bz0 - reach; bz <= bz0 + reach; bz++) {
               if (bx >= chunkMinX && bx <= chunkMaxX && bz >= chunkMinZ && bz <= chunkMaxZ) {
                  double ddx = bx - bx0;
                  double ddz = bz - bz0;
                  if (!(Math.abs(ddx * alongX + ddz * alongZ) > 1.0) && !(Math.abs(ddx * acrossX + ddz * acrossZ) > halfThickness + 0.6)) {
                     bp.set(bx, lowTop + 1, bz);
                     if (diff >= 2) {
                        region.setBlockState(bp, this.getWallBrickBlock(bx, lowTop + 1, bz), 2);
                        bp.set(bx, lowTop + 2, bz);
                        region.setBlockState(bp, slabState, 2);
                     } else {
                        region.setBlockState(bp, slabState, 2);
                     }
                  }
               }
            }
         }
      }
   }

   private int segmentTopForIndex(int ringIdx, int segIdx) {
      int[] tops = this.ringSegmentTops.computeIfAbsent(ringIdx, this::computeRingSegmentTops);
      int count = tops.length;
      return tops[(segIdx % count + count) % count];
   }

   private int[] computeRingSegmentTops(int ringIdx) {
      int count = RING_RIDGE_COUNT[ringIdx];
      double step = RING_RIDGE_ANGLE_STEP[ringIdx];
      double wallCenterR = RING_RADII[ringIdx] - RING_THICKNESS_BASE / 2.0;
      int[] tops = new int[count];

      for (int s = 0; s < count; s++) {
         double a = s * step;
         int wx = 0 + (int)Math.round(wallCenterR * Math.cos(a));
         int wz = 0 + (int)Math.round(wallCenterR * Math.sin(a));
         tops[s] = 64 + (int)Math.round(this.getHeightAt(wx, wz)) + RING_HEIGHT - 1;
      }

      limitAdjacentDelta(tops, 1, true);
      return tops;
   }

   private static void limitAdjacentDelta(int[] v, int maxStep, boolean circular) {
      int n = v.length;
      if (n >= 2) {
         boolean changed = true;
         int guard = 0;
         int cap = Math.min(n, 512);

         while (changed && guard++ <= cap) {
            changed = false;

            for (int i = 0; i < n; i++) {
               int pi = i == 0 ? (circular ? n - 1 : -1) : i - 1;
               if (pi >= 0 && v[i] > v[pi] + maxStep) {
                  v[i] = v[pi] + maxStep;
                  changed = true;
               }
            }

            for (int ix = n - 1; ix >= 0; ix--) {
               int ni = ix == n - 1 ? (circular ? 0 : -1) : ix + 1;
               if (ni >= 0 && v[ix] > v[ni] + maxStep) {
                  v[ix] = v[ni] + maxStep;
                  changed = true;
               }
            }
         }
      }
   }

   private int segmentFlatTopY(int ringIdx, double theta) {
      double step = RING_RIDGE_ANGLE_STEP[ringIdx];
      double t = theta;
      if (theta < 0.0) {
         t = theta + (Math.PI * 2);
      }

      int segIdx = (int)Math.floor(t / step);
      return this.segmentTopForIndex(ringIdx, segIdx);
   }

   private void placeWallColumn(ChunkRegion region, int x, int z, int wallStartY, boolean gateGap, int terrainY, int visibleTopY) {
      this.placeWallColumn(region, x, z, wallStartY, gateGap, terrainY, visibleTopY, false, false);
   }

   private void placeWallColumn(ChunkRegion region, int x, int z, int wallStartY, boolean gateGap, int terrainY, int visibleTopY, boolean edgeChamfer) {
      this.placeWallColumn(region, x, z, wallStartY, gateGap, terrainY, visibleTopY, edgeChamfer, false);
   }

   private boolean isWallBodyColumn(int x, int z, double wallCenterR, double bodyHalf) {
      double dx = x - 0;
      double dz = z - 0;
      double an = Math.abs(Math.sqrt(dx * dx + dz * dz) - wallCenterR);
      return an <= bodyHalf;
   }

   private boolean isOutermostBodyColumn(int x, int z, double wallCenterR, double bodyHalf) {
      return !this.isWallBodyColumn(x, z, wallCenterR, bodyHalf)
         ? false
         : !this.isWallBodyColumn(x + 1, z, wallCenterR, bodyHalf)
            || !this.isWallBodyColumn(x - 1, z, wallCenterR, bodyHalf)
            || !this.isWallBodyColumn(x, z + 1, wallCenterR, bodyHalf)
            || !this.isWallBodyColumn(x, z - 1, wallCenterR, bodyHalf);
   }

   private void placeWallColumn(
      ChunkRegion region, int x, int z, int wallStartY, boolean gateGap, int terrainY, int visibleTopY, boolean edgeChamfer, boolean interiorCore
   ) {
      BlockState air = Blocks.AIR.getDefaultState();
      BlockState ultraHardened = DannysAot.ULTRA_HARDENED_BLOCK.getDefaultState();
      Mutable mutable = new Mutable();
      int wallTopY = visibleTopY;

      for (int y = wallStartY; y <= wallTopY; y++) {
         if (gateGap && y >= terrainY && y < terrainY + 15) {
            mutable.set(x, y, z);
            if (y == terrainY) {
               region.setBlockState(mutable, this.getInnerPathBlock(x, z), 2);
            } else {
               region.setBlockState(mutable, air, 2);
            }
         } else {
            mutable.set(x, y, z);
            int heightAboveBase = y - wallStartY;
            BlockState blockToPlace;
            if (edgeChamfer && y == wallTopY) {
               blockToPlace = Blocks.STONE_BRICK_SLAB.getDefaultState();
            } else if (interiorCore && y != wallTopY) {
               blockToPlace = ultraHardened;
            } else if (y != wallStartY && y != wallTopY && heightAboveBase != RING_BASE_HEIGHT - 1 && heightAboveBase != RING_BASE_HEIGHT) {
               blockToPlace = this.getWallStoneBlock(x, y, z);
            } else {
               blockToPlace = this.getWallBrickBlock(x, y, z);
            }

            region.setBlockState(mutable, blockToPlace, 2);
         }
      }
   }

   private void placeRidgeDecoration(
      ChunkRegion region,
      int centerX,
      int centerZ,
      int wallStartY,
      int wallTopY,
      double nxD,
      double nzD,
      double txD,
      double tzD,
      int ridgeTangentialHalf,
      int halfThickness,
      double centerBiasNormal
   ) {
      double nLen = Math.sqrt(nxD * nxD + nzD * nzD);
      double tLen = Math.sqrt(txD * txD + tzD * tzD);
      if (!(nLen < 1.0E-6) && !(tLen < 1.0E-6)) {
         nxD /= nLen;
         nzD /= nLen;
         txD /= tLen;
         tzD /= tLen;
         BlockState slab = Blocks.STONE_BRICK_SLAB.getDefaultState();
         Mutable pos = new Mutable();
         Set<Long> extColKeys = new HashSet<>();
         if (DMNK) {
            double bodyHalf = halfThickness + 0.5;
            double ribOut = 2.0;
            double footOut = 3.0;
            int flareH = 10;
            int terrainY = wallStartY + WALL_BURY_DEPTH;
            int reach = halfThickness + (int)Math.ceil(3.0) + 1;

            for (int wx = centerX - reach; wx <= centerX + reach; wx++) {
               for (int wz = centerZ - reach; wz <= centerZ + reach; wz++) {
                  double dxf = wx - centerX;
                  double dzf = wz - centerZ;
                  double tproj = dxf * txD + dzf * tzD;
                  if (!(Math.abs(tproj) > ridgeTangentialHalf + 0.4)) {
                     double an = Math.abs(dxf * nxD + dzf * nzD - centerBiasNormal);
                     double outPast = an - bodyHalf;
                     if (!(outPast > 3.4)) {
                        boolean isTooth = outPast <= 2.4;
                        int colTop = isTooth ? wallTopY : Math.min(wallTopY, terrainY + 10);
                        if (colTop >= wallStartY) {
                           for (int y = wallStartY; y <= colTop; y++) {
                              int hab = y - wallStartY;
                              BlockState b = y != wallStartY && y != colTop && hab != RING_BASE_HEIGHT - 1 && hab != RING_BASE_HEIGHT
                                 ? this.getWallStoneBlock(wx, y, wz)
                                 : this.getWallBrickBlock(wx, y, wz);
                              pos.set(wx, y, wz);
                              region.setBlockState(pos, b, 2);
                           }

                           if (isTooth) {
                              pos.set(wx, wallTopY + 1, wz);
                              region.setBlockState(pos, this.getWallBrickBlock(wx, wallTopY + 1, wz), 2);
                           }
                        }
                     }
                  }
               }
            }
         } else {
            for (int tang = -ridgeTangentialHalf; tang <= ridgeTangentialHalf; tang++) {
               for (int sign = -1; sign <= 1; sign += 2) {
                  for (int d = 1; d <= 2; d++) {
                     int norm = sign * (halfThickness + d);
                     int bx = centerX + (int)Math.round(tang * txD + norm * nxD);
                     int bz = centerZ + (int)Math.round(tang * tzD + norm * nzD);
                     long key = (long)bx << 32 | bz & 4294967295L;
                     if (extColKeys.add(key)) {
                        for (int y = wallStartY; y <= wallTopY; y++) {
                           int heightAboveBase = y - wallStartY;
                           BlockState blockToPlace;
                           if (y != wallStartY && y != wallTopY && heightAboveBase != RING_BASE_HEIGHT - 1 && heightAboveBase != RING_BASE_HEIGHT) {
                              blockToPlace = this.getWallStoneBlock(bx, y, bz);
                           } else {
                              blockToPlace = this.getWallBrickBlock(bx, y, bz);
                           }

                           pos.set(bx, y, bz);
                           region.setBlockState(pos, blockToPlace, 2);
                        }

                        if (d == 1) {
                           pos.set(bx, wallTopY + 1, bz);
                           region.setBlockState(pos, slab, 2);
                        }
                     }
                  }
               }
            }

            Set<Long> raisedTopKeys = new HashSet<>();

            for (int tang = -ridgeTangentialHalf; tang <= ridgeTangentialHalf; tang++) {
               for (int norm = -halfThickness; norm <= halfThickness; norm++) {
                  int bx = centerX + (int)Math.round(tang * txD + norm * nxD);
                  int bz = centerZ + (int)Math.round(tang * tzD + norm * nzD);
                  long key = (long)bx << 32 | bz & 4294967295L;
                  if (!extColKeys.contains(key) && raisedTopKeys.add(key)) {
                     pos.set(bx, wallTopY + 1, bz);
                     region.setBlockState(pos, this.getWallBrickBlock(bx, wallTopY + 1, bz), 2);
                  }
               }
            }
         }
      }
   }

   private void generateVillageWalls(ChunkRegion region, int centerX, int centerZ, int chunkX, int chunkZ) {
      int[] d = findDistrictByCenter(centerX, centerZ);
      if (d != null) {
         int ringIdx = d[0];
         int dirX = d[4];
         int dirZ = d[5];
         int tangX = -dirZ;
         int tangZ = dirX;
         int thickness = DISTRICT_WALL_THICKNESS;
         int halfWidth = 300;
         int halfDepth = 300;
         int sideWallLength = 600 - halfWidth;
         int gateHalf = 3;
         int innerExt = DISTRICT_INNER_EXTENSION[ringIdx];
         int chunkMinX = chunkX << 4;
         int chunkMaxX = chunkMinX + 15;
         int chunkMinZ = chunkZ << 4;
         int chunkMaxZ = chunkMinZ + 15;
         int footTang = halfWidth + thickness + 3;
         int footRad = 600 + thickness + 3;
         int boundHalfX;
         int boundHalfZ;
         if (dirX != 0) {
            boundHalfX = footRad;
            boundHalfZ = footTang;
         } else {
            boundHalfX = footTang;
            boundHalfZ = footRad;
         }

         if (chunkMaxX >= centerX - boundHalfX && chunkMinX <= centerX + boundHalfX) {
            if (chunkMaxZ >= centerZ - boundHalfZ && chunkMinZ <= centerZ + boundHalfZ) {
               for (int x = chunkMinX; x <= chunkMaxX; x++) {
                  for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                     int ddx = x - centerX;
                     int ddz = z - centerZ;
                     int r = ddx * dirX + ddz * dirZ;
                     int t = ddx * tangX + ddz * tangZ;
                     int rLocal = r + halfDepth;
                     boolean inLeftWall = false;
                     boolean inRightWall = false;
                     boolean inCapWall = false;
                     double capAngle = 0.0;
                     int outerInset = 0;
                     if (rLocal >= -innerExt && rLocal <= sideWallLength) {
                        if (t > halfWidth && t <= halfWidth + thickness) {
                           inRightWall = true;
                           outerInset = halfWidth + thickness - t;
                        } else if (t < -halfWidth && t >= -halfWidth - thickness) {
                           inLeftWall = true;
                           outerInset = halfWidth + thickness - -t;
                        }
                     } else if (rLocal > sideWallLength) {
                        double capDx = rLocal - sideWallLength;
                        double capDist = Math.sqrt(capDx * capDx + (double)t * t);
                        if (capDist >= halfWidth - 0.5 && capDist <= halfWidth + thickness + 0.5 && capDx >= -0.5) {
                           inCapWall = true;
                           capAngle = Math.atan2(t, capDx);
                           outerInset = halfWidth + thickness - (int)Math.round(capDist);
                        }
                     }

                     if (inLeftWall || inRightWall || inCapWall) {
                        if (outerInset < 0) {
                           outerInset = 0;
                        }

                        boolean gateGap = false;
                        if (inCapWall && Math.abs(t) <= gateHalf && Math.abs(capAngle) < 0.25) {
                           gateGap = true;
                        }

                        int terrainY = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ));
                        int wallStartY = terrainY - WALL_BURY_DEPTH;
                        int fullTopY;
                        if (DMNK) {
                           if (inCapWall) {
                              double arcStep = (double)RIDGE_ARC_SPACING / halfWidth;
                              int segIdx = (int)Math.floor(capAngle / arcStep);
                              fullTopY = this.districtSegTop(d, 1, segIdx);
                           } else {
                              int segIdx = Math.floorDiv(rLocal, RIDGE_ARC_SPACING);
                              fullTopY = this.districtSegTop(d, inRightWall ? 2 : 0, segIdx);
                           }
                        } else {
                           fullTopY = terrainY + RING_HEIGHT - 1;
                        }

                        boolean edgeChamfer = DMNK && (outerInset <= 0 || outerInset >= thickness - 1);
                        boolean interiorCore = DMNK && !edgeChamfer;
                        this.placeWallColumn(region, x, z, wallStartY, gateGap, terrainY, fullTopY, edgeChamfer, interiorCore);
                     }
                  }
               }

               this.placeDistrictWallRidges(
                  region,
                  d,
                  centerX,
                  centerZ,
                  dirX,
                  dirZ,
                  tangX,
                  tangZ,
                  thickness,
                  halfWidth,
                  halfDepth,
                  sideWallLength,
                  gateHalf,
                  chunkMinX,
                  chunkMaxX,
                  chunkMinZ,
                  chunkMaxZ
               );
            }
         }
      }
   }

   private void placeDistrictWallRidges(
      ChunkRegion region,
      int[] d,
      int centerX,
      int centerZ,
      int dirX,
      int dirZ,
      int tangX,
      int tangZ,
      int thickness,
      int halfWidth,
      int halfDepth,
      int sideWallLength,
      int gateHalf,
      int chunkMinX,
      int chunkMaxX,
      int chunkMinZ,
      int chunkMaxZ
   ) {
      int halfThickness = thickness / 2;
      int ridgeTangentialHalf = 1;
      int pad = halfThickness + 4;
      double wallCenterRFromSide = halfWidth + thickness / 2.0;
      double wallCapRadius = halfWidth + thickness / 2.0;
      int maxStraightIdx = (sideWallLength - 4) / RIDGE_ARC_SPACING;

      for (int idx = 0; idx <= maxStraightIdx; idx++) {
         if (DMNK || idx % 2 == 0) {
            int rLocal = idx * RIDGE_ARC_SPACING;
            if (rLocal > 4) {
               int localR = rLocal - halfDepth;

               for (int side = 0; side < 2; side++) {
                  int sign = side == 0 ? -1 : 1;
                  double tLocal = sign * wallCenterRFromSide;
                  int sx = centerX + (int)Math.round(localR * dirX + tLocal * tangX);
                  int sz = centerZ + (int)Math.round(localR * dirZ + tLocal * tangZ);
                  if (sx + pad >= chunkMinX && sx - pad <= chunkMaxX && sz + pad >= chunkMinZ && sz - pad <= chunkMaxZ) {
                     int ridgeTerrainY = 64 + (int)Math.round(this.getHeightAt(sx, sz));
                     int wallStartY = ridgeTerrainY - WALL_BURY_DEPTH;
                     int sideRegion = sign > 0 ? 2 : 0;
                     int wallTopY;
                     if (DMNK) {
                        wallTopY = Math.max(this.districtSegTop(d, sideRegion, idx), this.districtSegTop(d, sideRegion, idx - 1));
                     } else {
                        wallTopY = ridgeTerrainY + RING_HEIGHT - 1;
                     }

                     double nxD = sign * tangX;
                     double nzD = sign * tangZ;
                     double txD = dirX;
                     double tzD = dirZ;
                     double centerBias = (centerX + localR * dirX + tLocal * tangX - sx) * nxD + (centerZ + localR * dirZ + tLocal * tangZ - sz) * nzD;
                     this.placeRidgeDecoration(region, sx, sz, wallStartY, wallTopY, nxD, nzD, txD, tzD, ridgeTangentialHalf, halfThickness, centerBias);
                     if (DMNK) {
                        int topThis = this.districtSegTop(d, sideRegion, idx);
                        int topPrev = this.districtSegTop(d, sideRegion, idx - 1);
                        int diff = Math.abs(topThis - topPrev);
                        if (diff >= 1) {
                           boolean lowerIsPrev = topPrev < topThis;
                           int lowTop = Math.min(topThis, topPrev);
                           int off = lowerIsPrev ? -2 : 2;
                           int bx0 = centerX + (int)Math.round((localR + off) * dirX + tLocal * tangX);
                           int bz0 = centerZ + (int)Math.round((localR + off) * dirZ + tLocal * tangZ);
                           this.placeWalkwayStepBridge(
                              region, bx0, bz0, dirX, dirZ, nxD, nzD, halfThickness, lowTop, diff, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ
                           );
                        }
                     }
                  }
               }
            }
         }
      }

      double arcStep = (double)RIDGE_ARC_SPACING / halfWidth;
      int capRidgeMax = (int)Math.floor((Math.PI / 2) / arcStep);

      for (int n = -capRidgeMax; n <= capRidgeMax; n++) {
         if (DMNK || (n % 2 + 2) % 2 == 0) {
            double capAngle = n * arcStep;
            if (!(Math.abs(capAngle) < 0.25)) {
               double capDx = wallCapRadius * Math.cos(capAngle);
               double tLocal = wallCapRadius * Math.sin(capAngle);
               double localR = sideWallLength + capDx - halfDepth;
               int sx = centerX + (int)Math.round(localR * dirX + tLocal * tangX);
               int sz = centerZ + (int)Math.round(localR * dirZ + tLocal * tangZ);
               if (sx + pad >= chunkMinX && sx - pad <= chunkMaxX && sz + pad >= chunkMinZ && sz - pad <= chunkMaxZ) {
                  int ridgeTerrainYx = 64 + (int)Math.round(this.getHeightAt(sx, sz));
                  int wallStartYx = ridgeTerrainYx - WALL_BURY_DEPTH;
                  int wallTopYx;
                  if (DMNK) {
                     wallTopYx = Math.max(this.districtSegTop(d, 1, n), this.districtSegTop(d, 1, n - 1));
                  } else {
                     wallTopYx = ridgeTerrainYx + RING_HEIGHT - 1;
                  }

                  double cos = Math.cos(capAngle);
                  double sin = Math.sin(capAngle);
                  double nxD = cos * dirX + sin * tangX;
                  double nzD = cos * dirZ + sin * tangZ;
                  double txD = -sin * dirX + cos * tangX;
                  double tzD = -sin * dirZ + cos * tangZ;
                  double centerBias = (centerX + localR * dirX + tLocal * tangX - sx) * nxD + (centerZ + localR * dirZ + tLocal * tangZ - sz) * nzD;
                  this.placeRidgeDecoration(region, sx, sz, wallStartYx, wallTopYx, nxD, nzD, txD, tzD, ridgeTangentialHalf, halfThickness, centerBias);
                  if (DMNK) {
                     int topThis = this.districtSegTop(d, 1, n);
                     int topPrev = this.districtSegTop(d, 1, n - 1);
                     int diff = Math.abs(topThis - topPrev);
                     if (diff >= 1) {
                        boolean lowerIsPrev = topPrev < topThis;
                        int lowTop = Math.min(topThis, topPrev);
                        int dirSign = lowerIsPrev ? -1 : 1;
                        int bx0 = sx + (int)Math.round(dirSign * 2 * txD);
                        int bz0 = sz + (int)Math.round(dirSign * 2 * tzD);
                        this.placeWalkwayStepBridge(
                           region, bx0, bz0, txD, tzD, nxD, nzD, halfThickness, lowTop, diff, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private void generateMegaWalls(ChunkRegion region, int chunkX, int chunkZ) {
      int chunkMinX = chunkX << 4;
      int chunkMaxX = chunkMinX + 15;
      int chunkMinZ = chunkZ << 4;
      int chunkMaxZ = chunkMinZ + 15;
      long[] cornerDistsSq = new long[]{
         (long)(chunkMinX - 0) * (chunkMinX - 0) + (long)(chunkMinZ - 0) * (chunkMinZ - 0),
         (long)(chunkMaxX - 0) * (chunkMaxX - 0) + (long)(chunkMinZ - 0) * (chunkMinZ - 0),
         (long)(chunkMinX - 0) * (chunkMinX - 0) + (long)(chunkMaxZ - 0) * (chunkMaxZ - 0),
         (long)(chunkMaxX - 0) * (chunkMaxX - 0) + (long)(chunkMaxZ - 0) * (chunkMaxZ - 0)
      };
      long minCorner = Math.min(Math.min(cornerDistsSq[0], cornerDistsSq[1]), Math.min(cornerDistsSq[2], cornerDistsSq[3]));
      long maxCorner = Math.max(Math.max(cornerDistsSq[0], cornerDistsSq[1]), Math.max(cornerDistsSq[2], cornerDistsSq[3]));

      for (int ringIdx = 0; ringIdx < RING_RADII.length; ringIdx++) {
         int ringOuter = RING_RADII[ringIdx];
         int ringInner = ringOuter - RING_THICKNESS_BASE;
         long outerBoundSq = (long)(ringOuter + 3) * (ringOuter + 3);
         long innerBoundSq = (long)(ringInner - 3) * (ringInner - 3);
         if (maxCorner >= innerBoundSq && minCorner <= outerBoundSq) {
            int footExtra = DMNK ? 1 : 0;
            double wallCenterR = ringOuter - RING_THICKNESS_BASE / 2.0;
            double bodyHalf = RING_THICKNESS_BASE / 2.0;
            long scanOuterSq = (long)(ringOuter + footExtra) * (ringOuter + footExtra);
            long scanInnerSq = (long)(ringInner - footExtra) * (ringInner - footExtra);
            double gateHalfArc = RING_GATE_HALF_ARC[ringIdx];
            double ridgeStep = RING_RIDGE_ANGLE_STEP[ringIdx];

            for (int x = chunkMinX; x <= chunkMaxX; x++) {
               for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                  long dx = x - 0;
                  long dz = z - 0;
                  long distSq = dx * dx + dz * dz;
                  if (distSq <= scanOuterSq && distSq >= scanInnerSq) {
                     double theta = Math.atan2(dz, dx);
                     boolean gateGap = false;

                     for (int c = 0; c < 4; c++) {
                        double dTheta = Math.abs(normalizeAngle(theta - CARDINAL_ANGLES[c]));
                        if (dTheta <= gateHalfArc) {
                           gateGap = true;
                           break;
                        }
                     }

                     int terrainY = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ));
                     int wallStartY = terrainY - WALL_BURY_DEPTH;
                     int fullTopY = DMNK ? this.segmentFlatTopY(ringIdx, theta) : terrainY + RING_HEIGHT - 1;
                     int visTop = fullTopY;
                     if (DMNK) {
                        double an = Math.abs(Math.sqrt(distSq) - wallCenterR);
                        boolean edgeChamfer = false;
                        boolean interiorCore = false;
                        if (an > bodyHalf) {
                           visTop = fullTopY - (RING_HEIGHT - 1 - RING_BASE_HEIGHT + WALL_BURY_DEPTH);
                        } else if (this.isOutermostBodyColumn(x, z, wallCenterR, bodyHalf)) {
                           edgeChamfer = true;
                        } else {
                           interiorCore = true;
                        }

                        this.placeWallColumn(region, x, z, wallStartY, gateGap, terrainY, visTop, edgeChamfer, interiorCore);
                     } else {
                        this.placeWallColumn(region, x, z, wallStartY, gateGap, terrainY, fullTopY);
                     }
                  }
               }
            }

            this.placeMegaWallRidges(region, ringIdx, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
         }
      }
   }

   private void placeMegaWallRidges(ChunkRegion region, int ringIdx, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
      int ringOuter = RING_RADII[ringIdx];
      double wallCenterR = ringOuter - RING_THICKNESS_BASE / 2.0;
      double ridgeStep = RING_RIDGE_ANGLE_STEP[ringIdx];
      double gateHalfArc = RING_GATE_HALF_ARC[ringIdx];
      int halfThickness = RING_THICKNESS_BASE / 2;
      int ridgeTangentialHalf = 1;
      int pad = halfThickness + 4;
      int count = RING_RIDGE_COUNT[ringIdx];
      int step = DMNK ? 1 : 2;

      for (int i = 0; i < count; i += step) {
         double theta = i * ridgeStep;
         double thetaN = normalizeAngle(theta);
         boolean inGate = false;

         for (int c = 0; c < 4; c++) {
            double dTheta = Math.abs(normalizeAngle(thetaN - CARDINAL_ANGLES[c]));
            if (dTheta <= gateHalfArc) {
               inGate = true;
               break;
            }
         }

         if (!inGate) {
            double cos = Math.cos(thetaN);
            double sin = Math.sin(thetaN);
            int wx = 0 + (int)Math.round(wallCenterR * cos);
            int wz = 0 + (int)Math.round(wallCenterR * sin);
            if (wx + pad >= chunkMinX && wx - pad <= chunkMaxX && wz + pad >= chunkMinZ && wz - pad <= chunkMaxZ) {
               int ridgeTerrainY = 64 + (int)Math.round(this.getHeightAt(wx, wz));
               int wallStartY = ridgeTerrainY - WALL_BURY_DEPTH;
               int wallTopY = DMNK ? Math.max(this.segmentTopForIndex(ringIdx, i - 1), this.segmentTopForIndex(ringIdx, i)) : ridgeTerrainY + RING_HEIGHT - 1;
               double centerBias = (0.0 + wallCenterR * cos - wx) * cos + (0.0 + wallCenterR * sin - wz) * sin;
               this.placeRidgeDecoration(region, wx, wz, wallStartY, wallTopY, cos, sin, -sin, cos, ridgeTangentialHalf, halfThickness, centerBias);
               if (DMNK) {
                  int topPrev = this.segmentTopForIndex(ringIdx, i - 1);
                  int topThis = this.segmentTopForIndex(ringIdx, i);
                  int diff = Math.abs(topThis - topPrev);
                  if (diff >= 1) {
                     boolean lowerIsPrev = topPrev < topThis;
                     int lowTop = Math.min(topPrev, topThis);
                     double bAngle = thetaN + (lowerIsPrev ? -1.0 : 1.0) * 2.0 / wallCenterR;
                     double ncos = Math.cos(bAngle);
                     double nsin = Math.sin(bAngle);
                     int bx0 = 0 + (int)Math.round(wallCenterR * ncos);
                     int bz0 = 0 + (int)Math.round(wallCenterR * nsin);
                     this.placeWalkwayStepBridge(
                        region, bx0, bz0, -nsin, ncos, ncos, nsin, halfThickness, lowTop, diff, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ
                     );
                  }
               }
            }
         }
      }
   }

   private static double normalizeAngle(double a) {
      while (a > Math.PI) {
         a -= Math.PI * 2;
      }

      while (a < -Math.PI) {
         a += Math.PI * 2;
      }

      return a;
   }

   private boolean isOnRadialCrossroad(int worldX, int worldZ) {
      int pathHalfEdge = (int)Math.round(7.5) + 2;
      int maxRadius = RING_RADII[RING_RADII.length - 1] + 600 + 60;
      double curveAmp = 6.0;
      double curveFreq = 0.02;
      long dx = worldX - 0;
      long dz = worldZ - 0;
      if (Math.abs(dz) <= maxRadius) {
         double curve = curveAmp * Math.sin(dz * curveFreq);
         if (Math.abs(dx - curve) <= pathHalfEdge) {
            return true;
         }
      }

      if (Math.abs(dx) <= maxRadius) {
         double curve = curveAmp * Math.sin(dx * curveFreq);
         if (Math.abs(dz - curve) <= pathHalfEdge) {
            return true;
         }
      }

      return false;
   }

   private void generateRadialCrossroads(ChunkRegion region, int chunkX, int chunkZ) {
      int chunkMinX = chunkX << 4;
      int chunkMaxX = chunkMinX + 15;
      int chunkMinZ = chunkZ << 4;
      int chunkMaxZ = chunkMinZ + 15;
      int pathHalfCenter = (int)Math.round(7.5) - 2;
      int pathHalfEdge = (int)Math.round(7.5);
      int maxRadius = RING_RADII[RING_RADII.length - 1] + 600 + 60;
      double curveAmp = 6.0;
      double curveFreq = 0.02;
      int maxOffset = pathHalfEdge + (int)Math.ceil(curveAmp) + 1;
      boolean touchesNS = chunkMinX - 0 <= maxOffset && chunkMaxX - 0 >= -maxOffset;
      boolean touchesEW = chunkMinZ - 0 <= maxOffset && chunkMaxZ - 0 >= -maxOffset;
      if (touchesNS || touchesEW) {
         Mutable pos = new Mutable();
         BlockState air = Blocks.AIR.getDefaultState();

         for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
               long dx = x - 0;
               long dz = z - 0;
               double distNS = Double.MAX_VALUE;
               if (touchesNS && Math.abs(dz) <= maxRadius) {
                  double curve = curveAmp * Math.sin(dz * curveFreq);
                  distNS = Math.abs(dx - curve);
               }

               double distEW = Double.MAX_VALUE;
               if (touchesEW && Math.abs(dx) <= maxRadius) {
                  double curve = curveAmp * Math.sin(dx * curveFreq);
                  distEW = Math.abs(dz - curve);
               }

               double minDist = Math.min(distNS, distEW);
               if (!(minDist > pathHalfEdge)) {
                  long distSq = dx * dx + dz * dz;
                  boolean insideSolidRing = false;

                  for (int ringIdx = 0; ringIdx < RING_RADII.length; ringIdx++) {
                     int ro = RING_RADII[ringIdx];
                     int ri = ro - RING_THICKNESS_BASE;
                     if (distSq <= (long)ro * ro && distSq >= (long)ri * ri) {
                        double theta = Math.atan2(dz, dx);
                        double gateHalfArc = RING_GATE_HALF_ARC[ringIdx];
                        boolean inGateGap = false;

                        for (int c = 0; c < 4; c++) {
                           if (Math.abs(normalizeAngle(theta - CARDINAL_ANGLES[c])) <= gateHalfArc) {
                              inGateGap = true;
                              break;
                           }
                        }

                        if (!inGateGap) {
                           insideSolidRing = true;
                           break;
                        }
                     }
                  }

                  if (!insideSolidRing) {
                     int terrainY = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ));
                     BlockState pathBlock = minDist <= pathHalfCenter ? this.getCrossroadPathBlock(x, z) : this.getPathEdgeBlock(x, z);
                     pos.set(x, terrainY, z);
                     region.setBlockState(pos, pathBlock, 2);
                     pos.set(x, terrainY + 1, z);
                     region.setBlockState(pos, air, 2);
                     pos.set(x, terrainY + 2, z);
                     region.setBlockState(pos, air, 2);
                  }
               }
            }
         }
      }
   }

   private boolean isGatePosition(int dx, int dz, long distSq, long gateInnerSq, long gateOuterSq) {
      return false;
   }

   private int[][] getVillageGates(int centerX, int centerZ) {
      int[] d = findDistrictByCenter(centerX, centerZ);
      if (d == null) {
         return new int[0][];
      } else {
         int dirX = d[4];
         int dirZ = d[5];
         int cardinal = d[1];
         int ringIdx = d[0];
         int gateIndex = CARDINAL_TO_GATE_INDEX[cardinal];
         int ringRadius = RING_RADII[ringIdx];
         int outerArcRadius = ringRadius + 600;
         int thickness = DISTRICT_WALL_THICKNESS;
         int outerX = 0 + dirX * (outerArcRadius + thickness);
         int outerZ = 0 + dirZ * (outerArcRadius + thickness);
         int innerX = 0 + dirX * ringRadius;
         int innerZ = 0 + dirZ * ringRadius;
         return new int[][]{{outerX, outerZ, dirX, dirZ, gateIndex}, {innerX, innerZ, dirX, dirZ, gateIndex}};
      }
   }

   private int getBranchDistance(int gateX, int gateZ) {
      java.util.Random rand = new java.util.Random(positionSeed(gateX * 17, gateZ * 31));
      return 30 + rand.nextInt(20);
   }

   private double angleBetween(double dx1, double dz1, double dx2, double dz2) {
      double len1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);
      double len2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);
      if (len1 != 0.0 && len2 != 0.0) {
         double dot = (dx1 * dx2 + dz1 * dz2) / (len1 * len2);
         dot = Math.max(-1.0, Math.min(1.0, dot));
         return Math.acos(dot);
      } else {
         return 0.0;
      }
   }

   private List<int[]> getPathSegments(int gridX, int gridZ) {
      long seedPart = (worldSeed & 65535L) << 48;
      long cacheKey = seedPart | (long)(gridX & 65535) << 32 | (long)(gridZ & 65535) << 16 | (gridX ^ gridZ) & 65535;
      List<int[]> cached = pathSegmentCache.get(cacheKey);
      if (cached != null) {
         return cached;
      } else {
         List<int[]> segments = new ArrayList<>();
         int[] center1 = this.getVillageCenter(gridX, gridZ);
         if (center1 == null) {
            pathSegmentCache.put(cacheKey, segments);
            return segments;
         } else {
            int[][] gates1 = this.getVillageGates(center1[0], center1[1]);
            List<int[]>[] outgoingConnections = new List[4];

            for (int i = 0; i < 4; i++) {
               outgoingConnections[i] = new ArrayList<>();
            }

            for (int dgx = -1; dgx <= 1; dgx++) {
               for (int dgz = -1; dgz <= 1; dgz++) {
                  if (dgx != 0 || dgz != 0) {
                     int neighborGridX = gridX + dgx;
                     int neighborGridZ = gridZ + dgz;
                     int[] center2 = this.getVillageCenter(neighborGridX, neighborGridZ);
                     if (center2 != null) {
                        int[][] gates2 = this.getVillageGates(center2[0], center2[1]);
                        double minDist = Double.MAX_VALUE;
                        int bestG1Idx = -1;
                        int[] bestG2 = null;

                        for (int g1Idx = 0; g1Idx < gates1.length; g1Idx++) {
                           int[] g1 = gates1[g1Idx];

                           for (int[] g2 : gates2) {
                              double dist = Math.sqrt((g1[0] - g2[0]) * (g1[0] - g2[0]) + (g1[1] - g2[1]) * (g1[1] - g2[1]));
                              int midX = (g1[0] + g2[0]) / 2;
                              int midZ = (g1[1] + g2[1]) / 2;
                              boolean pathThroughForest = this.isGiantForestBiome(midX, midZ);
                              int maxAllowedDist = pathThroughForest ? 6000 : 3500;
                              if (dist < minDist && dist < maxAllowedDist) {
                                 minDist = dist;
                                 bestG1Idx = g1Idx;
                                 bestG2 = g2;
                              }
                           }
                        }

                        if (bestG1Idx >= 0 && bestG2 != null && outgoingConnections[bestG1Idx].size() < 2) {
                           boolean thisVillageOwnsPath = gridX < neighborGridX || gridX == neighborGridX && gridZ < neighborGridZ;
                           if (thisVillageOwnsPath) {
                              outgoingConnections[bestG1Idx].add(new int[]{bestG2[0], bestG2[1], neighborGridX, neighborGridZ});
                           }
                        }
                     }
                  }
               }
            }

            for (int gateIdx = 0; gateIdx < 4; gateIdx++) {
               List<int[]> connections = outgoingConnections[gateIdx];
               if (!connections.isEmpty()) {
                  int[] gate = gates1[gateIdx];
                  int gateX = gate[0];
                  int gateZ = gate[1];
                  int branchDist = this.getBranchDistance(gateX, gateZ);
                  List<int[]> effectiveDests = new ArrayList<>();

                  for (int[] conn : connections) {
                     effectiveDests.add(new int[]{conn[0], conn[1]});
                  }

                  if (effectiveDests.size() == 1) {
                     int[] dest = effectiveDests.get(0);
                     segments.add(new int[]{gateX, gateZ, dest[0], dest[1]});
                  } else {
                     int[] dest1 = effectiveDests.get(0);
                     int[] dest2 = effectiveDests.get(1);
                     double dx1 = dest1[0] - gateX;
                     double dz1 = dest1[1] - gateZ;
                     double dx2 = dest2[0] - gateX;
                     double dz2 = dest2[1] - gateZ;
                     double angle = this.angleBetween(dx1, dz1, dx2, dz2);
                     if (!(angle >= Math.PI / 7)) {
                        for (int[] dest : effectiveDests) {
                           segments.add(new int[]{gateX, gateZ, dest[0], dest[1]});
                        }
                     } else {
                        double avgDirX = 0.0;
                        double avgDirZ = 0.0;

                        for (int[] dest : effectiveDests) {
                           double dx = dest[0] - gateX;
                           double dz = dest[1] - gateZ;
                           double len = Math.sqrt(dx * dx + dz * dz);
                           if (len > 0.0) {
                              avgDirX += dx / len;
                              avgDirZ += dz / len;
                           }
                        }

                        double avgLen = Math.sqrt(avgDirX * avgDirX + avgDirZ * avgDirZ);
                        if (avgLen > 0.0) {
                           avgDirX /= avgLen;
                           avgDirZ /= avgLen;
                        }

                        int branchX = gateX + (int)(avgDirX * branchDist);
                        int branchZ = gateZ + (int)(avgDirZ * branchDist);
                        segments.add(new int[]{gateX, gateZ, branchX, branchZ});

                        for (int[] destx : effectiveDests) {
                           segments.add(new int[]{branchX, branchZ, destx[0], destx[1]});
                        }
                     }
                  }
               }
            }

            pathSegmentCache.put(cacheKey, segments);
            return segments;
         }
      }
   }

   private double getPathCurveOffset(double x, double z, int pathSeed, double t) {
      double seed = pathSeed * 12345.0;
      double noise1 = Math.sin((x + z + seed) * 0.008) * 12.0;
      double noise2 = Math.sin((x - z + seed) * 0.005) * 8.0;
      double rawOffset = noise1 + noise2;
      double taperZone = 0.15;
      double taper = 1.0;
      if (t < taperZone) {
         taper = t / taperZone;
      } else if (t > 1.0 - taperZone) {
         taper = (1.0 - t) / taperZone;
      }

      taper = taper * taper * (3.0 - 2.0 * taper);
      return rawOffset * taper;
   }

   private int[] getPathInfo(int worldX, int worldZ) {
      int centerGridX = worldX / 6000;
      int centerGridZ = worldZ / 6000;
      double minDistSqToPath = Double.MAX_VALUE;
      double closestDistSqFromGate = Double.MAX_VALUE;
      int halfWidth = 5;
      double maxPathDistSq = (double)(halfWidth + 2) * (halfWidth + 2);
      double wallRadiusLow = WALL_OUTER_RADIUS - 5;
      double wallRadiusHigh = WALL_OUTER_RADIUS + 5;
      double wallRadiusLowSq = wallRadiusLow * wallRadiusLow;
      double wallRadiusHighSq = wallRadiusHigh * wallRadiusHigh;
      double innerPathLenSq = 225.0;

      for (int gridX = centerGridX - 1; gridX <= centerGridX + 1; gridX++) {
         for (int gridZ = centerGridZ - 1; gridZ <= centerGridZ + 1; gridZ++) {
            for (int[] seg : this.getPathSegments(gridX, gridZ)) {
               int startX = seg[0];
               int startZ = seg[1];
               int endX = seg[2];
               int endZ = seg[3];
               int minX = Math.min(startX, endX) - 10 - 20;
               int maxX = Math.max(startX, endX) + 10 + 20;
               int minZ = Math.min(startZ, endZ) - 10 - 20;
               int maxZ = Math.max(startZ, endZ) + 10 + 20;
               if (worldX >= minX && worldX <= maxX && worldZ >= minZ && worldZ <= maxZ) {
                  double pathDx = endX - startX;
                  double pathDz = endZ - startZ;
                  double pathLenSq = pathDx * pathDx + pathDz * pathDz;
                  if (pathLenSq != 0.0) {
                     double pathLen = Math.sqrt(pathLenSq);
                     double ndx = pathDx / pathLen;
                     double ndz = pathDz / pathLen;
                     double px = worldX - startX;
                     double pz = worldZ - startZ;
                     double t = (px * ndx + pz * ndz) / pathLen;
                     t = Math.max(0.0, Math.min(1.0, t));
                     double pathPointX = startX + t * pathLen * ndx;
                     double pathPointZ = startZ + t * pathLen * ndz;
                     int seed = startX * 31 + startZ * 17 + endX * 13 + endZ * 7;
                     double curveOffset = this.getPathCurveOffset(pathPointX, pathPointZ, seed, t);
                     double perpX = -ndz;
                     double curvedX = pathPointX + perpX * curveOffset;
                     double curvedZ = pathPointZ + ndx * curveOffset;
                     double ddx = worldX - curvedX;
                     double ddz = worldZ - curvedZ;
                     double distSq = ddx * ddx + ddz * ddz;
                     if (distSq < minDistSqToPath) {
                        minDistSqToPath = distSq;
                     }

                     if (!(distSq > maxPathDistSq)) {
                        for (int vgx = centerGridX - 1; vgx <= centerGridX + 1; vgx++) {
                           for (int vgz = centerGridZ - 1; vgz <= centerGridZ + 1; vgz++) {
                              int[] villageCenter = this.getVillageCenter(vgx, vgz);
                              if (villageCenter != null) {
                                 double scdx = startX - villageCenter[0];
                                 double scdz = startZ - villageCenter[1];
                                 double distStartSq = scdx * scdx + scdz * scdz;
                                 if (distStartSq >= wallRadiusLowSq && distStartSq <= wallRadiusHighSq) {
                                    double gdx = worldX - startX;
                                    double gdz = worldZ - startZ;
                                    double distToGateSq = gdx * gdx + gdz * gdz;
                                    if (distToGateSq < closestDistSqFromGate) {
                                       closestDistSqFromGate = distToGateSq;
                                    }
                                 }

                                 double ecdx = endX - villageCenter[0];
                                 double ecdz = endZ - villageCenter[1];
                                 double distEndSq = ecdx * ecdx + ecdz * ecdz;
                                 if (distEndSq >= wallRadiusLowSq && distEndSq <= wallRadiusHighSq) {
                                    double gdx = worldX - endX;
                                    double gdz = worldZ - endZ;
                                    double distToGateSq = gdx * gdx + gdz * gdz;
                                    if (distToGateSq < closestDistSqFromGate) {
                                       closestDistSqFromGate = distToGateSq;
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

      if (minDistSqToPath > maxPathDistSq) {
         return null;
      } else {
         double minDistToPath = Math.sqrt(minDistSqToPath);
         int pathType;
         if (minDistToPath > halfWidth - 2) {
            pathType = 0;
         } else if (closestDistSqFromGate <= innerPathLenSq) {
            pathType = 2;
         } else {
            pathType = 1;
         }

         return new int[]{pathType, (int)Math.sqrt(closestDistSqFromGate)};
      }
   }

   private boolean chunkIntersectsAnyPath(int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
      int centerGridX = (chunkMinX + chunkMaxX) / 2 / 6000;
      int centerGridZ = (chunkMinZ + chunkMaxZ) / 2 / 6000;
      int minGridX = centerGridX - 1;
      int maxGridX = centerGridX + 1;
      int minGridZ = centerGridZ - 1;
      int maxGridZ = centerGridZ + 1;
      boolean anyVillageNearby = false;

      for (int gridX = minGridX; gridX <= maxGridX && !anyVillageNearby; gridX++) {
         for (int gridZ = minGridZ; gridZ <= maxGridZ && !anyVillageNearby; gridZ++) {
            if (this.getVillageCenter(gridX, gridZ) != null) {
               anyVillageNearby = true;
            }
         }
      }

      if (!anyVillageNearby) {
         return false;
      } else {
         int margin = 35;
         int expandedMinX = chunkMinX - margin;
         int expandedMaxX = chunkMaxX + margin;
         int expandedMinZ = chunkMinZ - margin;
         int expandedMaxZ = chunkMaxZ + margin;

         for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
            for (int gridZx = minGridZ; gridZx <= maxGridZ; gridZx++) {
               for (int[] seg : this.getPathSegments(gridX, gridZx)) {
                  int startX = seg[0];
                  int startZ = seg[1];
                  int endX = seg[2];
                  int endZ = seg[3];
                  int segMinX = Math.min(startX, endX);
                  int segMaxX = Math.max(startX, endX);
                  int segMinZ = Math.min(startZ, endZ);
                  int segMaxZ = Math.max(startZ, endZ);
                  if (segMaxX >= expandedMinX && segMinX <= expandedMaxX && segMaxZ >= expandedMinZ && segMinZ <= expandedMaxZ) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private boolean isChunkNearAnyVillage(int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
      int pathHalfEdge = (int)Math.round(7.5) + 8;
      int maxRadius = RING_RADII[RING_RADII.length - 1] + 600 + 60;
      if (chunkMinX - 0 <= pathHalfEdge && chunkMaxX - 0 >= -pathHalfEdge && chunkMaxZ - 0 >= -maxRadius && chunkMinZ - 0 <= maxRadius) {
         return true;
      } else if (chunkMinZ - 0 <= pathHalfEdge && chunkMaxZ - 0 >= -pathHalfEdge && chunkMaxX - 0 >= -maxRadius && chunkMinX - 0 <= maxRadius) {
         return true;
      } else {
         int halfFootprint = 1400 + DISTRICT_WALL_THICKNESS + 4;

         for (int[] d : DISTRICTS) {
            int cx = d[2];
            int cz = d[3];
            if (cx + halfFootprint >= chunkMinX && cx - halfFootprint <= chunkMaxX && cz + halfFootprint >= chunkMinZ && cz - halfFootprint <= chunkMaxZ) {
               return true;
            }
         }

         int[][] corners = new int[][]{{chunkMinX, chunkMinZ}, {chunkMaxX, chunkMinZ}, {chunkMinX, chunkMaxZ}, {chunkMaxX, chunkMaxZ}};
         long[] cornerSq = new long[4];

         for (int i = 0; i < 4; i++) {
            long dx = corners[i][0] - 0;
            long dz = corners[i][1] - 0;
            cornerSq[i] = dx * dx + dz * dz;
         }

         long minSq = Math.min(Math.min(cornerSq[0], cornerSq[1]), Math.min(cornerSq[2], cornerSq[3]));
         long maxSq = Math.max(Math.max(cornerSq[0], cornerSq[1]), Math.max(cornerSq[2], cornerSq[3]));

         for (int r = 0; r < RING_RADII.length; r++) {
            long ro = (long)(RING_RADII[r] + 4) * (RING_RADII[r] + 4);
            long ri = (long)(RING_RADII[r] - RING_THICKNESS_BASE - 4) * (RING_RADII[r] - RING_THICKNESS_BASE - 4);
            if (ri <= maxSq && minSq <= ro) {
               return true;
            }
         }

         return false;
      }
   }

   private void generatePaths(ChunkRegion region, int chunkX, int chunkZ) {
      int chunkMinX = chunkX * 16;
      int chunkMaxX = chunkMinX + 15;
      int chunkMinZ = chunkZ * 16;
      int chunkMaxZ = chunkMinZ + 15;
      if (this.chunkIntersectsAnyPath(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
         Mutable mutable = new Mutable();

         for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
               if (!this.isInsideVillageWalls(x, z)) {
                  int[] pathInfo = this.getPathInfo(x, z);
                  if (pathInfo != null) {
                     int pathType = pathInfo[0];
                     int terrainY = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ));
                     BlockState pathBlock;
                     if (pathType == 0) {
                        pathBlock = this.getPathEdgeBlock(x, z);
                     } else if (pathType == 2) {
                        pathBlock = this.getInnerPathBlock(x, z);
                     } else {
                        pathBlock = this.getOuterPathBlock(x, z);
                     }

                     mutable.set(x, terrainY, z);
                     region.setBlockState(mutable, pathBlock, 2);
                     if (pathBlock.getBlock() == Blocks.DIRT_PATH) {
                        mutable.set(x, terrainY - 1, z);
                        region.setBlockState(mutable, Blocks.DIRT.getDefaultState(), 2);
                     }

                     if (pathBlock.getBlock() == Blocks.GRASS_BLOCK) {
                        java.util.Random grassRand = new java.util.Random(positionSeed(x * 7, z * 13));
                        if (grassRand.nextFloat() < 0.3F) {
                           mutable.set(x, terrainY + 1, z);
                           region.setBlockState(mutable, Blocks.GRASS.getDefaultState(), 2);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void generateVillageGround(ChunkRegion region, int centerX, int centerZ, int chunkX, int chunkZ) {
      int chunkMinX = chunkX * 16;
      int chunkMaxX = chunkMinX + 15;
      int chunkMinZ = chunkZ * 16;
      int chunkMaxZ = chunkMinZ + 15;
      long innerRadiusSq = 84100L;
      long[] cornerDistsSq = new long[]{
         (long)(chunkMinX - centerX) * (chunkMinX - centerX) + (long)(chunkMinZ - centerZ) * (chunkMinZ - centerZ),
         (long)(chunkMaxX - centerX) * (chunkMaxX - centerX) + (long)(chunkMinZ - centerZ) * (chunkMinZ - centerZ),
         (long)(chunkMinX - centerX) * (chunkMinX - centerX) + (long)(chunkMaxZ - centerZ) * (chunkMaxZ - centerZ),
         (long)(chunkMaxX - centerX) * (chunkMaxX - centerX) + (long)(chunkMaxZ - centerZ) * (chunkMaxZ - centerZ)
      };
      long minCornerDistSq = Math.min(Math.min(cornerDistsSq[0], cornerDistsSq[1]), Math.min(cornerDistsSq[2], cornerDistsSq[3]));
      if (minCornerDistSq <= innerRadiusSq) {
         int villageType = getVillageType(centerX, centerZ);
         boolean isShinganshina = villageType == 1;
         List<int[]> layout = isShinganshina ? this.getVillageLayout(centerX, centerZ) : null;
         Mutable mutable = new Mutable();
         BlockState dirtState = Blocks.DIRT.getDefaultState();

         for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
               long dx = x - centerX;
               long dz = z - centerZ;
               if (dx * dx + dz * dz <= innerRadiusSq) {
                  double cachedH = this.getCachedHeight(x, z, chunkX, chunkZ);
                  int terrainY = 64 + (int)Math.round(cachedH);
                  int groundY = terrainY;
                  if (isShinganshina) {
                     double exactMound = this.getShiganshinaMoundHeight(x, z, centerX, centerZ, cachedH);
                     int moundOffset = (int)Math.floor(exactMound);
                     groundY = terrainY + moundOffset;

                     for (int[] data : layout) {
                        if (data[0] != 1 && data[5] != -1 && x >= data[5] - 1 && x <= data[7] + 1 && z >= data[6] - 1 && z <= data[8] + 1) {
                           groundY = Math.min(groundY, data[2] - 2);
                           break;
                        }
                     }

                     for (int y = terrainY + 1; y < groundY; y++) {
                        mutable.set(x, y, z);
                        region.setBlockState(mutable, dirtState, 2);
                     }
                  }

                  mutable.set(x, groundY, z);
                  BlockState groundBlock;
                  if (isShinganshina) {
                     java.util.Random grassRand = new java.util.Random(positionSeed(x * 3, z * 5));
                     groundBlock = grassRand.nextFloat() < 0.025F ? Blocks.GRASS_BLOCK.getDefaultState() : this.getCrossroadPathBlock(x, z);
                  } else {
                     groundBlock = this.getVillageGroundBlock(x, z);
                  }

                  region.setBlockState(mutable, groundBlock, 2);
                  if (groundBlock.getBlock() == Blocks.GRASS_BLOCK) {
                     int foliageY = groundY + 1;
                     mutable.set(x, foliageY, z);
                     BlockState aboveState = region.getBlockState(mutable);
                     boolean canPlace = aboveState.isAir() || aboveState.getBlock() == Blocks.GRASS || aboveState.getBlock() == Blocks.TALL_GRASS;
                     if (canPlace) {
                        boolean useShortGrass = (x * 31 + z * 17 & 1) == 0;
                        if (useShortGrass) {
                           region.setBlockState(mutable, Blocks.GRASS.getDefaultState(), 2);
                        } else {
                           Mutable above2 = new Mutable(x, foliageY + 1, z);
                           BlockState above2State = region.getBlockState(above2);
                           boolean canPlaceTall = above2State.isAir()
                              || above2State.getBlock() == Blocks.GRASS
                              || above2State.getBlock() == Blocks.TALL_GRASS;
                           if (canPlaceTall) {
                              region.setBlockState(mutable, Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
                              region.setBlockState(above2, Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
                           } else {
                              region.setBlockState(mutable, Blocks.GRASS.getDefaultState(), 2);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void generateVillageCrossroads(ChunkRegion region, int centerX, int centerZ, int chunkX, int chunkZ) {
      int chunkMinX = chunkX * 16;
      int chunkMaxX = chunkMinX + 15;
      int chunkMinZ = chunkZ * 16;
      int chunkMaxZ = chunkMinZ + 15;
      int pathHalfWidth = 5;
      boolean intersectsNSPath = chunkMinX <= centerX + pathHalfWidth && chunkMaxX >= centerX - pathHalfWidth;
      boolean intersectsEWPath = chunkMinZ <= centerZ + pathHalfWidth && chunkMaxZ >= centerZ - pathHalfWidth;
      if (intersectsNSPath || intersectsEWPath) {
         int villageType = getVillageType(centerX, centerZ);
         boolean isShinganshina = villageType == 1;
         long innerRadiusSq = 84100L;
         Mutable mutable = new Mutable();

         for (int x = chunkMinX; x <= chunkMaxX; x++) {
            for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
               int dx = x - centerX;
               int dz = z - centerZ;
               if ((long)dx * dx + (long)dz * dz <= innerRadiusSq && (Math.abs(dx) > 24 || Math.abs(dz) > 24)) {
                  boolean onNSRoad = Math.abs(dx) <= pathHalfWidth;
                  boolean onEWRoad = Math.abs(dz) <= pathHalfWidth;
                  if (onNSRoad || onEWRoad) {
                     int distFromCenterX = Math.abs(dx);
                     int distFromCenterZ = Math.abs(dz);
                     boolean isEdge = false;
                     if (onNSRoad && !onEWRoad) {
                        isEdge = distFromCenterX >= pathHalfWidth - 1;
                     } else if (onEWRoad && !onNSRoad) {
                        isEdge = distFromCenterZ >= pathHalfWidth - 1;
                     }

                     double cachedH = this.getCachedHeight(x, z, chunkX, chunkZ);
                     int naturalTerrainY = 64 + (int)Math.round(cachedH);
                     int moundOffset = isShinganshina ? (int)Math.floor(this.getShiganshinaMoundHeight(x, z, centerX, centerZ, cachedH)) : 0;
                     int terrainY = naturalTerrainY + moundOffset;
                     if (!isShinganshina) {
                        int elevatedY = terrainY + 1;
                        if (isEdge) {
                           mutable.set(x, elevatedY, z);
                           region.setBlockState(mutable, this.getCrossroadSlabBlock(x, z), 2);
                        } else {
                           BlockState pathBlock = this.getCrossroadPathBlock(x, z);
                           mutable.set(x, elevatedY, z);
                           region.setBlockState(mutable, pathBlock, 2);
                           mutable.set(x, terrainY, z);
                           region.setBlockState(mutable, Blocks.DIRT.getDefaultState(), 2);
                        }
                     } else {
                        int groundY = terrainY;

                        for (int y = naturalTerrainY + 1; y < groundY; y++) {
                           mutable.set(x, y, z);
                           region.setBlockState(mutable, Blocks.DIRT.getDefaultState(), 2);
                        }

                        BlockState pathBlock = this.getVillageGroundBlock(x, z);
                        mutable.set(x, groundY, z);
                        region.setBlockState(mutable, pathBlock, 2);
                        if (pathBlock.getBlock() == Blocks.GRASS_BLOCK) {
                           mutable.set(x, groundY + 1, z);
                           if (region.getBlockState(mutable).isAir()) {
                              boolean useShortGrass = (x * 31 + z * 17 & 1) == 0;
                              if (useShortGrass) {
                                 region.setBlockState(mutable, Blocks.GRASS.getDefaultState(), 2);
                              } else {
                                 Mutable above2 = new Mutable(x, groundY + 2, z);
                                 if (region.getBlockState(above2).isAir()) {
                                    region.setBlockState(mutable, Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
                                    region.setBlockState(above2, Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
                                 } else {
                                    region.setBlockState(mutable, Blocks.GRASS.getDefaultState(), 2);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (isShinganshina) {
            for (int x = chunkMinX; x <= chunkMaxX; x++) {
               for (int zx = chunkMinZ; zx <= chunkMaxZ; zx++) {
                  long cdx = x - centerX;
                  long cdz = zx - centerZ;
                  if (cdx * cdx + cdz * cdz <= innerRadiusSq) {
                     double ch = this.getCachedHeight(x, zx, chunkX, chunkZ);
                     int groundY = 64 + (int)Math.round(ch) + (int)Math.floor(this.getShiganshinaMoundHeight(x, zx, centerX, centerZ, ch));
                     int dx = x - centerX;
                     int dz = zx - centerZ;
                     boolean onNSRoad = Math.abs(dx) <= pathHalfWidth;
                     boolean onEWRoad = Math.abs(dz) <= pathHalfWidth;
                     boolean onPath = (onNSRoad || onEWRoad) && (Math.abs(dx) > 24 || Math.abs(dz) > 24);
                     int[][] neighbors = new int[][]{{x + 1, zx}, {x - 1, zx}, {x, zx + 1}, {x, zx - 1}};

                     for (int[] n : neighbors) {
                        int nx = n[0];
                        int nz = n[1];
                        if (this.isInChunk(nx, nz, chunkX, chunkZ)) {
                           double nch = this.getCachedHeight(nx, nz, chunkX, chunkZ);
                           int nGroundY = 64 + (int)Math.round(nch) + (int)Math.floor(this.getShiganshinaMoundHeight(nx, nz, centerX, centerZ, nch));
                           int ndx = nx - centerX;
                           int ndz = nz - centerZ;
                           boolean nOnNSRoad = Math.abs(ndx) <= pathHalfWidth;
                           boolean nOnEWRoad = Math.abs(ndz) <= pathHalfWidth;
                           boolean neighborOnPath = (nOnNSRoad || nOnEWRoad) && (Math.abs(ndx) > 24 || Math.abs(ndz) > 24);
                           if (onPath && nGroundY < groundY) {
                              mutable.set(nx, nGroundY + 1, nz);
                              if (region.getBlockState(mutable).isAir()) {
                                 region.setBlockState(mutable, this.getCrossroadSlabBlock(nx, nz), 2);
                              }
                           }

                           if (neighborOnPath && nGroundY > groundY) {
                              mutable.set(x, groundY + 1, zx);
                              if (region.getBlockState(mutable).isAir()) {
                                 region.setBlockState(mutable, this.getCrossroadSlabBlock(x, zx), 2);
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

   @Override
   protected Codec<? extends ChunkGenerator> getCodec() {
      return CODEC;
   }

   @Override
   public void carve(
      ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, Carver carverStep
   ) {
      worldSeed = noiseConfig.getOrCreateRandomDeriver(new Identifier("paradis_seed")).split(0, 0, 0).nextLong();
      if (carverStep == Carver.AIR) {
         ChunkPos chunkPos = chunk.getPos();
         int chunkX = chunkPos.x;
         int chunkZ = chunkPos.z;
         int caveCheckRadius = 8;
         Mutable carvePos = new Mutable();

         for (int offsetX = -caveCheckRadius; offsetX <= caveCheckRadius; offsetX++) {
            for (int offsetZ = -caveCheckRadius; offsetZ <= caveCheckRadius; offsetZ++) {
               int sourceChunkX = chunkX + offsetX;
               int sourceChunkZ = chunkZ + offsetZ;
               long chunkSeed = worldSeed ^ sourceChunkX * 341873128712L ^ sourceChunkZ * 132897987541L;
               int quickCheck = (int)((chunkSeed ^ chunkSeed >>> 32) * -7046029254386353131L >>> 57);
               if (quickCheck < 19) {
                  Random chunkRandom = Random.create(chunkSeed);
                  int numCaves = chunkRandom.nextInt(100) < 15 ? (chunkRandom.nextInt(100) < 50 ? 1 : 2) : 0;
                  List<int[]> regularCaveCarvedPositions = new ArrayList<>();

                  for (int caveIndex = 0; caveIndex < numCaves; caveIndex++) {
                     int startX = sourceChunkX * 16 + chunkRandom.nextInt(16);
                     int startZ = sourceChunkZ * 16 + chunkRandom.nextInt(16);
                     int startY = chunkRandom.nextInt(100) - 50;
                     if (!this.isInsideVillageWalls(startX, startZ) && !this.isNearVillage(startX, startZ, WALL_OUTER_RADIUS + 50)) {
                        double caveLength = 50.0 + chunkRandom.nextDouble() * 150.0;
                        double direction = chunkRandom.nextDouble() * Math.PI * 2.0;
                        double verticalAngle = (chunkRandom.nextDouble() - 0.5) * 0.5;
                        double radius = 1.5 + chunkRandom.nextDouble() * 2.5;
                        double x = startX;
                        double y = startY;
                        double z = startZ;

                        for (int i = 0; i < caveLength; i++) {
                           x += Math.cos(direction) * Math.cos(verticalAngle);
                           z += Math.sin(direction) * Math.cos(verticalAngle);
                           y += Math.sin(verticalAngle);
                           direction += (chunkRandom.nextDouble() - 0.5) * 0.3;
                           verticalAngle += (chunkRandom.nextDouble() - 0.5) * 0.1;
                           verticalAngle = Math.max(-0.6, Math.min(0.6, verticalAngle));
                           radius += (chunkRandom.nextDouble() - 0.5) * 0.3;
                           radius = Math.max(1.0, Math.min(4.5, radius));
                           int blockX = (int)Math.round(x);
                           int blockY = (int)Math.round(y);
                           int blockZ = (int)Math.round(z);
                           if (blockX >= chunkX * 16 - 5 && blockX < chunkX * 16 + 21 && blockZ >= chunkZ * 16 - 5 && blockZ < chunkZ * 16 + 21) {
                              int iRadius = (int)Math.ceil(radius);

                              for (int dx = -iRadius; dx <= iRadius; dx++) {
                                 for (int dy = -iRadius; dy <= iRadius; dy++) {
                                    for (int dz = -iRadius; dz <= iRadius; dz++) {
                                       if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                                          int carveX = blockX + dx;
                                          int carveY = blockY + dy;
                                          int carveZ = blockZ + dz;
                                          if (carveX >= chunkX * 16
                                             && carveX < chunkX * 16 + 16
                                             && carveZ >= chunkZ * 16
                                             && carveZ < chunkZ * 16 + 16
                                             && carveY > -59
                                             && carveY < 74
                                             && !this.isInsideVillageWalls(carveX, carveZ)) {
                                             carvePos.set(carveX, carveY, carveZ);
                                             BlockState current = chunk.getBlockState(carvePos);
                                             if (this.isCarveableBlock(current)) {
                                                chunk.setBlockState(carvePos, Blocks.CAVE_AIR.getDefaultState(), false);
                                                if (carveY >= 44) {
                                                   regularCaveCarvedPositions.add(new int[]{carveX, carveY, carveZ});
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

                  if (!regularCaveCarvedPositions.isEmpty()) {
                     Set<Long> checkedPositions = new HashSet<>();
                     int surfaceY = 79;

                     for (int[] pos : regularCaveCarvedPositions) {
                        int carveX = pos[0];
                        int carveY = pos[1];
                        int carveZ = pos[2];

                        for (int dx = -2; dx <= 2; dx++) {
                           for (int dzx = -2; dzx <= 2; dzx++) {
                              int checkX = carveX + dx;
                              int checkZ = carveZ + dzx;
                              if (checkX >= chunkX * 16 && checkX < chunkX * 16 + 16 && checkZ >= chunkZ * 16 && checkZ < chunkZ * 16 + 16) {
                                 for (int checkY = carveY; checkY <= surfaceY; checkY++) {
                                    long posKey = (long)checkX << 40 | (long)checkZ << 20 | checkY;
                                    if (!checkedPositions.contains(posKey)) {
                                       checkedPositions.add(posKey);
                                       carvePos.set(checkX, checkY, checkZ);
                                       BlockState blockState = chunk.getBlockState(carvePos);
                                       carvePos.set(checkX, checkY - 1, checkZ);
                                       BlockState belowState = chunk.getBlockState(carvePos);
                                       if ((belowState.isAir() || belowState.isOf(Blocks.CAVE_AIR))
                                          && (
                                             blockState.isOf(Blocks.GRASS)
                                                || blockState.isOf(Blocks.TALL_GRASS)
                                                || blockState.isOf(Blocks.FERN)
                                                || blockState.isOf(Blocks.LARGE_FERN)
                                                || blockState.isOf(Blocks.POPPY)
                                                || blockState.isOf(Blocks.DANDELION)
                                                || blockState.isOf(Blocks.CORNFLOWER)
                                                || blockState.isOf(Blocks.OXEYE_DAISY)
                                                || blockState.isOf(Blocks.AZURE_BLUET)
                                                || blockState.isOf(Blocks.LILY_OF_THE_VALLEY)
                                                || blockState.isOf(Blocks.ALLIUM)
                                                || blockState.isOf(Blocks.BLUE_ORCHID)
                                                || blockState.isOf(Blocks.RED_TULIP)
                                                || blockState.isOf(Blocks.ORANGE_TULIP)
                                                || blockState.isOf(Blocks.WHITE_TULIP)
                                                || blockState.isOf(Blocks.PINK_TULIP)
                                                || blockState.isOf(Blocks.DEAD_BUSH)
                                                || blockState.isOf(Blocks.BROWN_MUSHROOM)
                                                || blockState.isOf(Blocks.RED_MUSHROOM)
                                                || blockState.isOf(Blocks.MOSS_CARPET)
                                                || blockState.isOf(Blocks.GRASS_BLOCK)
                                                || blockState.isOf(Blocks.DIRT)
                                                || blockState.isOf(Blocks.COARSE_DIRT)
                                                || blockState.isOf(Blocks.PODZOL)
                                          )) {
                                          carvePos.set(checkX, checkY, checkZ);
                                          chunk.setBlockState(carvePos, Blocks.AIR.getDefaultState(), false);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  long entranceSeed = worldSeed ^ sourceChunkX * 987654321L ^ sourceChunkZ * 123456789L;
                  Random entranceRandom = Random.create(entranceSeed);
                  if (entranceRandom.nextInt(2000) < 1) {
                     int entranceX = sourceChunkX * 16 + entranceRandom.nextInt(16);
                     int entranceZ = sourceChunkZ * 16 + entranceRandom.nextInt(16);
                     if (!this.isInsideVillageWalls(entranceX, entranceZ) && !this.isNearVillage(entranceX, entranceZ, WALL_OUTER_RADIUS + 100)) {
                        int surfaceY = 64 + (int)Math.round(this.getHeightAt(entranceX, entranceZ));
                        List<int[]> carvedPositions = new ArrayList<>();
                        double ex = entranceX;
                        double ey = surfaceY;
                        double ez = entranceZ;
                        double eDirection = entranceRandom.nextDouble() * Math.PI * 2.0;
                        double eVertical = -0.4 - entranceRandom.nextDouble() * 0.3;
                        double eRadius = 2.0 + entranceRandom.nextDouble() * 1.5;
                        int entranceLength = 30 + entranceRandom.nextInt(40);
                        storeCaveEntrance(entranceX, entranceZ, eDirection, entranceLength);

                        for (int ix = 0; ix < entranceLength; ix++) {
                           ex += Math.cos(eDirection) * Math.cos(eVertical) * 0.8;
                           ez += Math.sin(eDirection) * Math.cos(eVertical) * 0.8;
                           ey += Math.sin(eVertical);
                           if (ey < surfaceY - 15) {
                              eVertical += 0.02;
                              eVertical = Math.min(0.1, eVertical);
                           }

                           eDirection += (entranceRandom.nextDouble() - 0.5) * 0.2;
                           eRadius += (entranceRandom.nextDouble() - 0.5) * 0.2;
                           eRadius = Math.max(1.5, Math.min(3.5, eRadius));
                           int blockX = (int)Math.round(ex);
                           int blockY = (int)Math.round(ey);
                           int blockZ = (int)Math.round(ez);
                           if (blockX >= chunkX * 16 - 4 && blockX < chunkX * 16 + 20 && blockZ >= chunkZ * 16 - 4 && blockZ < chunkZ * 16 + 20) {
                              int iRadius = (int)Math.ceil(eRadius);

                              for (int dx = -iRadius; dx <= iRadius; dx++) {
                                 for (int dy = -iRadius; dy <= iRadius; dy++) {
                                    for (int dzxx = -iRadius; dzxx <= iRadius; dzxx++) {
                                       if (dx * dx + dy * dy + dzxx * dzxx <= eRadius * eRadius) {
                                          int carveX = blockX + dx;
                                          int carveY = blockY + dy;
                                          int carveZ = blockZ + dzxx;
                                          if (carveX >= chunkX * 16
                                             && carveX < chunkX * 16 + 16
                                             && carveZ >= chunkZ * 16
                                             && carveZ < chunkZ * 16 + 16
                                             && carveY > 5
                                             && carveY <= surfaceY + 2) {
                                             carvePos.set(carveX, carveY, carveZ);
                                             BlockState current = chunk.getBlockState(carvePos);
                                             if (this.isCarveableBlock(current)) {
                                                chunk.setBlockState(carvePos, Blocks.CAVE_AIR.getDefaultState(), false);
                                                carvedPositions.add(new int[]{carveX, carveY, carveZ});
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }

                        Set<Long> checkedPositions = new HashSet<>();

                        for (int[] pos : carvedPositions) {
                           int carveX = pos[0];
                           int carveY = pos[1];
                           int carveZ = pos[2];

                           for (int dx = -2; dx <= 2; dx++) {
                              for (int dzxxx = -2; dzxxx <= 2; dzxxx++) {
                                 int checkX = carveX + dx;
                                 int checkZ = carveZ + dzxxx;
                                 if (checkX >= chunkX * 16 && checkX < chunkX * 16 + 16 && checkZ >= chunkZ * 16 && checkZ < chunkZ * 16 + 16) {
                                    for (int checkYx = carveY; checkYx <= surfaceY + 5; checkYx++) {
                                       long posKey = (long)checkX << 40 | (long)checkZ << 20 | checkYx;
                                       if (!checkedPositions.contains(posKey)) {
                                          checkedPositions.add(posKey);
                                          carvePos.set(checkX, checkYx, checkZ);
                                          BlockState blockState = chunk.getBlockState(carvePos);
                                          carvePos.set(checkX, checkYx - 1, checkZ);
                                          BlockState belowState = chunk.getBlockState(carvePos);
                                          if ((belowState.isAir() || belowState.isOf(Blocks.CAVE_AIR))
                                             && (
                                                blockState.isOf(Blocks.GRASS)
                                                   || blockState.isOf(Blocks.TALL_GRASS)
                                                   || blockState.isOf(Blocks.FERN)
                                                   || blockState.isOf(Blocks.LARGE_FERN)
                                                   || blockState.isOf(Blocks.POPPY)
                                                   || blockState.isOf(Blocks.DANDELION)
                                                   || blockState.isOf(Blocks.CORNFLOWER)
                                                   || blockState.isOf(Blocks.OXEYE_DAISY)
                                                   || blockState.isOf(Blocks.AZURE_BLUET)
                                                   || blockState.isOf(Blocks.LILY_OF_THE_VALLEY)
                                                   || blockState.isOf(Blocks.ALLIUM)
                                                   || blockState.isOf(Blocks.BLUE_ORCHID)
                                                   || blockState.isOf(Blocks.RED_TULIP)
                                                   || blockState.isOf(Blocks.ORANGE_TULIP)
                                                   || blockState.isOf(Blocks.WHITE_TULIP)
                                                   || blockState.isOf(Blocks.PINK_TULIP)
                                                   || blockState.isOf(Blocks.DEAD_BUSH)
                                                   || blockState.isOf(Blocks.BROWN_MUSHROOM)
                                                   || blockState.isOf(Blocks.RED_MUSHROOM)
                                                   || blockState.isOf(Blocks.MOSS_CARPET)
                                                   || blockState.isOf(Blocks.GRASS_BLOCK)
                                                   || blockState.isOf(Blocks.DIRT)
                                                   || blockState.isOf(Blocks.COARSE_DIRT)
                                                   || blockState.isOf(Blocks.PODZOL)
                                             )) {
                                             carvePos.set(checkX, checkYx, checkZ);
                                             chunk.setBlockState(carvePos, Blocks.AIR.getDefaultState(), false);
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
         }
      }
   }

   private boolean isNearVillage(int worldX, int worldZ, int distance) {
      long distSq = (long)distance * distance;

      for (int[] d : DISTRICTS) {
         long dx = worldX - d[2];
         long dz = worldZ - d[3];
         if (dx * dx + dz * dz < distSq) {
            return true;
         }
      }

      return false;
   }

   private boolean hasCaveBelowSurface(ChunkRegion region, int x, int surfaceY, int z) {
      Mutable mutable = new Mutable();

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            int checkX = x + dx;
            int checkZ = z + dz;
            int checkSurfaceY = surfaceY;

            for (int checkY = surfaceY - 1; checkY > checkSurfaceY - 8 && checkY > -60; checkY--) {
               mutable.set(checkX, checkY, checkZ);
               BlockState state = region.getBlockState(mutable);
               if (state.isOf(Blocks.CAVE_AIR) || state.isAir() && checkY < checkSurfaceY - 1) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean isCarveableBlock(BlockState state) {
      return state.isOf(Blocks.STONE)
         || state.isOf(Blocks.DIRT)
         || state.isOf(Blocks.GRASS_BLOCK)
         || state.isOf(Blocks.DEEPSLATE)
         || state.isOf(Blocks.GRAVEL)
         || state.isOf(Blocks.ANDESITE)
         || state.isOf(Blocks.DIORITE)
         || state.isOf(Blocks.GRANITE)
         || state.isOf(Blocks.PODZOL)
         || state.isOf(Blocks.COARSE_DIRT)
         || state.isOf(Blocks.COAL_ORE)
         || state.isOf(Blocks.IRON_ORE)
         || state.isOf(Blocks.COPPER_ORE)
         || state.isOf(Blocks.GOLD_ORE)
         || state.isOf(Blocks.REDSTONE_ORE)
         || state.isOf(Blocks.LAPIS_ORE)
         || state.isOf(Blocks.DIAMOND_ORE)
         || state.isOf(Blocks.EMERALD_ORE)
         || state.isOf(Blocks.DEEPSLATE_COAL_ORE)
         || state.isOf(Blocks.DEEPSLATE_IRON_ORE)
         || state.isOf(Blocks.DEEPSLATE_COPPER_ORE)
         || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
         || state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE)
         || state.isOf(Blocks.DEEPSLATE_LAPIS_ORE)
         || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
         || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE);
   }

   @Override
   public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
      int chunkX = chunk.getPos().x;
      int chunkZ = chunk.getPos().z;
      int chunkCenterX = chunkX * 16 + 8;
      int chunkCenterZ = chunkZ * 16 + 8;
      boolean isGiantForest = this.isGiantForestBiome(chunkCenterX, chunkCenterZ);
      boolean isHighland = !isGiantForest && this.isHighlandBiome(chunkCenterX, chunkCenterZ);
      int chunkMinX = chunkX * 16;
      int chunkMaxX = chunkMinX + 15;
      int chunkMinZ = chunkZ * 16;
      int chunkMaxZ = chunkMinZ + 15;
      boolean villagesPathsWallsEnabled = DMNK || ModConfig.get().enableVillagesPathsWalls;
      boolean chunkNearVillage = villagesPathsWallsEnabled && this.isChunkNearAnyVillage(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ);
      if (villagesPathsWallsEnabled) {
      }

      boolean chunkNearPath = false;
      if (villagesPathsWallsEnabled) {
         CHUNK_CACHE.get().houseBounds.clear();
         if (!isGiantForest && !DMNK) {
            this.generateRadialCrossroads(region, chunkX, chunkZ);
         }

         if (!isGiantForest) {
            this.generateMegaWalls(region, chunkX, chunkZ);
         }

         List<int[]> villageCenters = new ArrayList<>();
         int halfFootprint = 1400 + DISTRICT_WALL_THICKNESS + 4;

         for (int[] d : DISTRICTS) {
            int cx = d[2];
            int cz = d[3];
            if (cx + halfFootprint >= chunkMinX
               && cx - halfFootprint <= chunkMaxX
               && cz + halfFootprint >= chunkMinZ
               && cz - halfFootprint <= chunkMaxZ
               && !this.isGiantForestBiome(cx, cz)) {
               villageCenters.add(new int[]{cx, cz});
            }
         }

         for (int[] villageCenter : villageCenters) {
            this.generateVillageWalls(region, villageCenter[0], villageCenter[1], chunkX, chunkZ);
            if (!DMNK) {
               this.generateVillageGround(region, villageCenter[0], villageCenter[1], chunkX, chunkZ);
            }

            this.generateVillageStructures(region, villageCenter[0], villageCenter[1], chunkX, chunkZ);
         }

         if (!DMNK) {
            for (int[] villageCenter : villageCenters) {
               this.generateVillageCrossroads(region, villageCenter[0], villageCenter[1], chunkX, chunkZ);
            }
         }

         for (int[] bounds : CHUNK_CACHE.get().houseBounds) {
            int minX = bounds[2];
            int minZ = bounds[3];
            int maxX = bounds[4];
            int maxZ = bounds[5];
            int structY = bounds[6];
            int villageCenterX = bounds[7];
            int villageCenterZ = bounds[8];
            int villageType = getVillageType(villageCenterX, villageCenterZ);
            this.placeVillageHouseFoundationFromBounds(region, minX, minZ, maxX, maxZ, structY, chunkX, chunkZ, villageType, villageCenterX, villageCenterZ);
         }
      }

      int searchRadius = 55;
      int giantMinGridX = (chunkX * 16 - searchRadius) / 38 - 1;
      int giantMaxGridX = (chunkX * 16 + 16 + searchRadius) / 38 + 1;
      int giantMinGridZ = (chunkZ * 16 - searchRadius) / 38 - 1;
      int giantMaxGridZ = (chunkZ * 16 + 16 + searchRadius) / 38 + 1;

      for (int gridX = giantMinGridX; gridX <= giantMaxGridX; gridX++) {
         for (int gridZ = giantMinGridZ; gridZ <= giantMaxGridZ; gridZ++) {
            int[] treeCenter = this.getGiantTreeCenter(gridX, gridZ);
            if (treeCenter != null
               && this.isGiantForestBiome(treeCenter[0], treeCenter[1])
               && (!DMNK || !isMountainsBiomeGlobal(treeCenter[0], treeCenter[1]) && !isHillsBiomeGlobal(treeCenter[0], treeCenter[1]))) {
               int treeRadius = this.getGiantTreeRadius(treeCenter[0], treeCenter[1]);
               if ((!chunkNearPath || !this.isOnOrNearPath(treeCenter[0], treeCenter[1], treeRadius + 2))
                  && (!DMNK || !isInsideDistrictInterior(treeCenter[0], treeCenter[1]))) {
                  int surfaceY = 64 + (int)Math.round(this.getHeightAt(treeCenter[0], treeCenter[1]));
                  this.generateGiantTree(region, treeCenter[0], surfaceY + 1, treeCenter[1], chunkX, chunkZ);
                  this.addGiantTreeGroundVegetation(region, treeCenter[0], treeCenter[1], chunkX, chunkZ);
               }
            }
         }
      }

      if (!isGiantForest && !isHighland) {
         searchRadius = 80;
         giantMinGridX = 250;
         giantMaxGridX = (chunkX * 16 - giantMinGridX) / searchRadius - 1;
         giantMinGridZ = (chunkX * 16 + 16 + giantMinGridX) / searchRadius + 1;
         giantMaxGridZ = (chunkZ * 16 - giantMinGridX) / searchRadius - 1;
         int maxGridZ = (chunkZ * 16 + 16 + giantMinGridX) / searchRadius + 1;

         for (int gridX = giantMaxGridX; gridX <= giantMinGridZ; gridX++) {
            for (int gridZx = giantMaxGridZ; gridZx <= maxGridZ; gridZx++) {
               int[] center = this.getClusterCenter(gridX, gridZx, searchRadius);
               if (center != null && !this.isGiantForestBiome(center[0], center[1]) && !this.isHighlandBiome(center[0], center[1])) {
                  int[][] treePositions = this.getTreePositionsInCluster(center[0], center[1]);

                  for (int[] treePos : treePositions) {
                     int treeX = treePos[0];
                     int treeZ = treePos[1];
                     if ((!chunkNearVillage || !this.isInsideOrOnVillageWalls(treeX, treeZ))
                        && (!DMNK || !chunkNearVillage || !isInsideDistrictInterior(treeX, treeZ))
                        && (!chunkNearPath || !this.isOnPathFast(treeX, treeZ))
                        && !this.isNearCaveEntranceCached(treeX, treeZ, chunkX, chunkZ)) {
                        int surfaceY = 64 + (int)Math.round(this.getCachedHeight(treeX, treeZ, chunkX, chunkZ));
                        if (!DMNK || surfaceY < 150) {
                           java.util.Random treeVariantRand = new java.util.Random(positionSeed(treeX, treeZ));
                           String treeVariant = DMNK
                              ? this.dmnkTreeName(this.dmnkForestTier(treeX, treeZ, 0.0, treeVariantRand), treeVariantRand)
                              : PINE_TREE_VARIANTS[treeVariantRand.nextInt(PINE_TREE_VARIANTS.length)];
                           BlockRotation treeRotation = BlockRotation.values()[treeVariantRand.nextInt(4)];
                           this.placeStructureChunkAware(region, treeVariant, treeX, surfaceY + 1, treeZ, treeRotation, chunkX, chunkZ);
                        }
                     }
                  }

                  this.addClusterVegetation(region, treePositions, chunkX, chunkZ, chunkNearVillage, chunkNearPath);
               }
            }
         }
      }

      searchRadius = 8;
      giantMinGridX = 40;
      giantMaxGridX = (chunkX * 16 - giantMinGridX) / searchRadius - 1;
      giantMinGridZ = (chunkX * 16 + 16 + giantMinGridX) / searchRadius + 1;
      giantMaxGridZ = (chunkZ * 16 - giantMinGridX) / searchRadius - 1;
      int htMaxGridZ = (chunkZ * 16 + 16 + giantMinGridX) / searchRadius + 1;

      for (int gridX = giantMaxGridX; gridX <= giantMinGridZ; gridX++) {
         for (int gridZxx = giantMaxGridZ; gridZxx <= htMaxGridZ; gridZxx++) {
            java.util.Random htRand = new java.util.Random(positionSeed(gridX * 5147, gridZxx * 5153));
            int treeX = gridX * searchRadius + htRand.nextInt(searchRadius);
            int treeZ = gridZxx * searchRadius + htRand.nextInt(searchRadius);
            double hlBiomeNoise = fractalNoise(treeX * 3.0E-4, treeZ * 3.0E-4, 2);
            if (!(hlBiomeNoise > 0.65)) {
               double hlNoise = fractalNoise(treeX * 8.0E-4 + 500.0, treeZ * 8.0E-4 + 500.0, 2);
               if (!(hlNoise <= 0.55)) {
                  double transitionFactor = Math.min(1.0, (hlNoise - 0.55) / 0.2);
                  double patchNoise = fractalNoise(treeX * 0.008 + 900.0, treeZ * 0.008 + 900.0, 2);
                  double spawnChance = transitionFactor * (0.3 + patchNoise * 0.7);
                  if (!(htRand.nextDouble() > spawnChance)
                     && (!chunkNearVillage || !this.isInsideOrOnVillageWalls(treeX, treeZ))
                     && (!DMNK || !chunkNearVillage || !isInsideDistrictInterior(treeX, treeZ))
                     && (!chunkNearPath || !this.isOnPathFast(treeX, treeZ))
                     && !this.isNearCaveEntranceCached(treeX, treeZ, chunkX, chunkZ)) {
                     int surfaceY = 64 + (int)Math.round(this.getCachedHeight(treeX, treeZ, chunkX, chunkZ));
                     java.util.Random varRand = new java.util.Random(positionSeed(treeX * 3313, treeZ * 3331));
                     String variant;
                     if (DMNK) {
                        variant = this.dmnkTreeName(Math.min(2, this.dmnkForestTier(treeX, treeZ, 0.0, varRand)), varRand);
                     } else if (varRand.nextDouble() < 0.7) {
                        variant = varRand.nextBoolean() ? "pine_tree_tall" : "pine_tree_medium";
                     } else {
                        variant = varRand.nextBoolean() ? "pine_tree_small" : "pine_tree_small2";
                     }

                     BlockRotation htRotation = BlockRotation.values()[varRand.nextInt(4)];
                     this.placeStructureChunkAware(region, variant, treeX, surfaceY + 1, treeZ, htRotation, chunkX, chunkZ);
                  }
               }
            }
         }
      }

      this.generateIronBambooPatches(region, chunkX, chunkZ);
      this.addVegetation(region, chunkX, chunkZ, chunkNearVillage, chunkNearPath);
   }

   private void generateSmallPonds(ChunkRegion region, int chunkX, int chunkZ) {
      java.util.Random random = new java.util.Random(positionSeed(chunkX * 9991, chunkZ * 8881));
      if (!(random.nextDouble() > 0.1)) {
         int pondX = chunkX * 16 + 3 + random.nextInt(10);
         int pondZ = chunkZ * 16 + 3 + random.nextInt(10);
         if (!this.isOnPathCached(pondX, pondZ, chunkX, chunkZ)) {
            if (!this.isOnOrNearPath(pondX, pondZ, 5)) {
               int nearestTreeDist = this.getNearestGiantTreeDistance(pondX, pondZ);
               if (nearestTreeDist >= 15) {
                  int surfaceY = 64 + (int)Math.round(this.getCachedHeight(pondX, pondZ, chunkX, chunkZ));
                  int pondRadius = 3 + random.nextInt(3);
                  int pondDepth = 2 + random.nextInt(2);
                  BlockState water = Blocks.WATER.getDefaultState();
                  BlockState dirt = Blocks.DIRT.getDefaultState();
                  BlockState clay = Blocks.CLAY.getDefaultState();
                  BlockState gravel = Blocks.GRAVEL.getDefaultState();
                  BlockState lilyPad = Blocks.LILY_PAD.getDefaultState();

                  for (int dx = -pondRadius - 1; dx <= pondRadius + 1; dx++) {
                     for (int dz = -pondRadius - 1; dz <= pondRadius + 1; dz++) {
                        int px = pondX + dx;
                        int pz = pondZ + dz;
                        if (this.isInChunk(px, pz, chunkX, chunkZ)) {
                           double dist = Math.sqrt(dx * dx + dz * dz);
                           double noisyDist = dist + (random.nextDouble() - 0.5) * 1.5;
                           if (noisyDist <= pondRadius) {
                              int localDepth = (int)(pondDepth * (1.0 - noisyDist / pondRadius));
                              localDepth = Math.max(1, localDepth);

                              for (int dy = 0; dy < localDepth; dy++) {
                                 int py = surfaceY - dy;
                                 BlockPos waterPos = new BlockPos(px, py, pz);
                                 region.setBlockState(waterPos, water, 2);
                              }

                              int bottomY = surfaceY - localDepth;
                              BlockPos bottomPos = new BlockPos(px, bottomY, pz);
                              double bottomRoll = random.nextDouble();
                              if (bottomRoll < 0.5) {
                                 region.setBlockState(bottomPos, dirt, 2);
                              } else if (bottomRoll < 0.8) {
                                 region.setBlockState(bottomPos, clay, 2);
                              } else {
                                 region.setBlockState(bottomPos, gravel, 2);
                              }

                              if (random.nextDouble() < 0.15 && dist < pondRadius - 0.5) {
                                 BlockPos lilyPos = new BlockPos(px, surfaceY + 1, pz);
                                 if (region.testBlockState(lilyPos, state -> state.isAir())) {
                                    region.setBlockState(lilyPos, lilyPad, 2);
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

   private void generateIronBambooPatches(ChunkRegion region, int chunkX, int chunkZ) {
      int searchRadius = 36;
      int minGridX = (chunkX * 16 - searchRadius) / 200 - 1;
      int maxGridX = (chunkX * 16 + 16 + searchRadius) / 200 + 1;
      int minGridZ = (chunkZ * 16 - searchRadius) / 200 - 1;
      int maxGridZ = (chunkZ * 16 + 16 + searchRadius) / 200 + 1;

      for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
         for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
            int[] patchCenter = this.getIronBambooPatchCenter(gridX, gridZ);
            if (patchCenter != null
               && !this.isInsideOrOnVillageWalls(patchCenter[0], patchCenter[1])
               && !this.isOnPathFast(patchCenter[0], patchCenter[1])
               && this.isGiantForestBiome(patchCenter[0], patchCenter[1])) {
               this.generateIronBambooPatch(region, patchCenter[0], patchCenter[1], chunkX, chunkZ);
            }
         }
      }
   }

   private int[] getIronBambooPatchCenter(int gridX, int gridZ) {
      java.util.Random random = new java.util.Random(positionSeed(gridX * 7919, gridZ * 7927));
      if (random.nextDouble() >= 0.2) {
         return null;
      } else {
         int margin = 21;
         int centerX = gridX * 200 + margin + random.nextInt(Math.max(1, 200 - 2 * margin));
         int centerZ = gridZ * 200 + margin + random.nextInt(Math.max(1, 200 - 2 * margin));
         return new int[]{centerX, centerZ};
      }
   }

   private void generateIronBambooPatch(ChunkRegion region, int centerX, int centerZ, int chunkX, int chunkZ) {
      java.util.Random random = new java.util.Random(positionSeed(centerX, centerZ));
      int patchRadius = 8 + random.nextInt(9);
      BlockState mossBlock = Blocks.MOSS_BLOCK.getDefaultState();
      BlockState grassBlock = Blocks.GRASS_BLOCK.getDefaultState();

      for (int dx = -patchRadius; dx <= patchRadius; dx++) {
         for (int dz = -patchRadius; dz <= patchRadius; dz++) {
            int x = centerX + dx;
            int z = centerZ + dz;
            if (!this.isInsideOrOnVillageWalls(x, z) && !this.isOnPathFast(x, z) && !this.isNearCaveEntranceCached(x, z, chunkX, chunkZ)) {
               double dist = Math.sqrt(dx * dx + dz * dz);
               java.util.Random posRandom = new java.util.Random(positionSeed(x, z));
               double noise = (posRandom.nextDouble() - 0.5) * 2.0;
               double noisyDist = dist + noise;
               if (noisyDist <= patchRadius) {
                  int surfaceY = 64 + (int)Math.round(this.getHeightAt(x, z));
                  BlockPos groundPos = new BlockPos(x, surfaceY, z);
                  boolean hasTree = false;

                  for (int checkY = 0; checkY <= 5; checkY++) {
                     BlockPos checkPos = groundPos.up(checkY);
                     if (this.isInChunk(x, z, chunkX, chunkZ)) {
                        BlockState checkState = region.getBlockState(checkPos);
                        if (checkState.isIn(BlockTags.LOGS) || checkState.isIn(BlockTags.LEAVES)) {
                           hasTree = true;
                           break;
                        }
                     }
                  }

                  if (!hasTree) {
                     double centerRatio = 1.0 - dist / patchRadius;
                     double roll = posRandom.nextDouble();
                     BlockState groundBlock;
                     if (centerRatio > 0.6) {
                        groundBlock = roll < 0.9 ? mossBlock : grassBlock;
                     } else if (centerRatio > 0.3) {
                        groundBlock = roll < 0.6 ? mossBlock : grassBlock;
                     } else {
                        groundBlock = roll < 0.3 ? mossBlock : grassBlock;
                     }

                     if (this.isInChunk(x, z, chunkX, chunkZ)) {
                        region.setBlockState(groundPos, groundBlock, 2);
                     }

                     double bambooChance = centerRatio * 0.5;
                     if (groundBlock == mossBlock) {
                        bambooChance += 0.15;
                     }

                     if (posRandom.nextDouble() < bambooChance) {
                        BlockPos bambooPos = groundPos.up();
                        boolean isFullyGrown = posRandom.nextDouble() < 0.08;
                        int bambooHeight;
                        if (isFullyGrown) {
                           bambooHeight = 10 + posRandom.nextInt(5);
                        } else {
                           bambooHeight = 3 + posRandom.nextInt(6);
                        }

                        int stage = 0;
                        if (isFullyGrown || bambooHeight >= 11 || bambooHeight >= 5 && posRandom.nextFloat() < 0.25F) {
                           stage = 1;
                        }

                        if (this.isInChunk(x, z, chunkX, chunkZ)) {
                           for (int h = 0; h < bambooHeight; h++) {
                              BlockPos stalkPos = bambooPos.up(h);
                              if (!region.getBlockState(stalkPos).isAir()) {
                                 break;
                              }

                              int fromTop = bambooHeight - 1 - h;
                              BambooLeaves leaves;
                              if (fromTop == 0) {
                                 leaves = BambooLeaves.LARGE;
                              } else if (fromTop == 1) {
                                 leaves = BambooLeaves.SMALL;
                              } else {
                                 leaves = BambooLeaves.NONE;
                              }

                              BlockState bambooState = DannysAot.IRON_BAMBOO
                                 .getDefaultState()
                                 .with(IronBambooBlock.LEAVES, leaves)
                                 .with(IronBambooBlock.STAGE, stage)
                                 .with(IronBambooBlock.AGE, 0);
                              region.setBlockState(stalkPos, bambooState, 2);
                           }
                        }

                        if (isFullyGrown) {
                           BlockPos topPos = bambooPos.up(bambooHeight);
                           BlockState bambooLeavesState = DannysAot.BAMBOO_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
                           if (this.isInChunk(topPos.getX(), topPos.getZ(), chunkX, chunkZ) && region.getBlockState(topPos).isAir()) {
                              region.setBlockState(topPos, bambooLeavesState, 2);
                           }

                           int[][] leafPattern = new int[][]{
                              {1, 0, 0},
                              {-1, 0, 0},
                              {0, 0, 1},
                              {0, 0, -1},
                              {1, -1, 0},
                              {-1, -1, 0},
                              {0, -1, 1},
                              {0, -1, -1},
                              {1, -1, 1},
                              {1, -1, -1},
                              {-1, -1, 1},
                              {-1, -1, -1},
                              {1, -2, 0},
                              {-1, -2, 0},
                              {0, -2, 1},
                              {0, -2, -1},
                              {1, -3, 0},
                              {-1, -3, 0},
                              {0, -3, 1},
                              {0, -3, -1}
                           };
                           java.util.Random leafRandom = new java.util.Random(positionSeed(x * 31, z * 37));

                           for (int[] offset : leafPattern) {
                              double chance = offset[1] == 0 ? 0.85 : (offset[1] == -1 ? 0.75 : (offset[1] == -2 ? 0.6 : 0.4));
                              if (leafRandom.nextDouble() < chance) {
                                 BlockPos leafPos = topPos.add(offset[0], offset[1], offset[2]);
                                 if (this.isInChunk(leafPos.getX(), leafPos.getZ(), chunkX, chunkZ) && region.getBlockState(leafPos).isAir()) {
                                    region.setBlockState(leafPos, bambooLeavesState, 2);
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

   private int getNearestGiantTreeDistance(int x, int z) {
      int gridX = x / 38;
      int gridZ = z / 38;
      int minDist = Integer.MAX_VALUE;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            int[] center = this.getGiantTreeCenter(gridX + dx, gridZ + dz);
            if (center != null && this.isGiantForestBiome(center[0], center[1])) {
               int dist = (int)Math.sqrt(Math.pow(x - center[0], 2.0) + Math.pow(z - center[1], 2.0));
               minDist = Math.min(minDist, dist);
            }
         }
      }

      return minDist;
   }

   @Override
   public void populateEntities(ChunkRegion region) {
   }

   @Override
   public int getWorldHeight() {
      return 384;
   }

   @Override
   public CompletableFuture<Chunk> populateNoise(java.util.concurrent.Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
      worldSeed = noiseConfig.getOrCreateRandomDeriver(new Identifier("paradis_seed")).split(0, 0, 0).nextLong();
      int chunkX = chunk.getPos().x;
      int chunkZ = chunk.getPos().z;
      BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
      BlockState podzol = Blocks.PODZOL.getDefaultState();
      BlockState coarseDirt = Blocks.COARSE_DIRT.getDefaultState();
      BlockState dirt = Blocks.DIRT.getDefaultState();
      BlockState stone = Blocks.STONE.getDefaultState();
      BlockState bedrock = Blocks.BEDROCK.getDefaultState();
      BlockState deepslate = Blocks.DEEPSLATE.getDefaultState();
      BlockState granite = Blocks.GRANITE.getDefaultState();
      BlockState diorite = Blocks.DIORITE.getDefaultState();
      BlockState andesite = Blocks.ANDESITE.getDefaultState();
      BlockState gravel = Blocks.GRAVEL.getDefaultState();
      BlockState snowBlock = Blocks.SNOW_BLOCK.getDefaultState();
      BlockState coalOre = Blocks.COAL_ORE.getDefaultState();
      BlockState ironOre = Blocks.IRON_ORE.getDefaultState();
      BlockState copperOre = Blocks.COPPER_ORE.getDefaultState();
      BlockState goldOre = Blocks.GOLD_ORE.getDefaultState();
      BlockState redstoneOre = Blocks.REDSTONE_ORE.getDefaultState();
      BlockState lapisOre = Blocks.LAPIS_ORE.getDefaultState();
      BlockState diamondOre = Blocks.DIAMOND_ORE.getDefaultState();
      BlockState emeraldOre = Blocks.EMERALD_ORE.getDefaultState();
      BlockState deepslateCoalOre = Blocks.DEEPSLATE_COAL_ORE.getDefaultState();
      BlockState deepslateIronOre = Blocks.DEEPSLATE_IRON_ORE.getDefaultState();
      BlockState deepslateCopperOre = Blocks.DEEPSLATE_COPPER_ORE.getDefaultState();
      BlockState deepslateGoldOre = Blocks.DEEPSLATE_GOLD_ORE.getDefaultState();
      BlockState deepslateRedstoneOre = Blocks.DEEPSLATE_REDSTONE_ORE.getDefaultState();
      BlockState deepslateLapisOre = Blocks.DEEPSLATE_LAPIS_ORE.getDefaultState();
      BlockState deepslateDiamondOre = Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState();
      BlockState deepslateEmeraldOre = Blocks.DEEPSLATE_EMERALD_ORE.getDefaultState();
      int[][] heights = new int[16][16];
      ParadisChunkGenerator.ChunkCacheState fillCache = CHUNK_CACHE.get();
      if (fillCache.heightCache == null) {
         fillCache.heightCache = new double[256];
      }

      int baseX = chunkX << 4;
      int baseZ = chunkZ << 4;
      int GRID_STEP = 4;
      int GRID_SIZE = 5;
      double[][] coarseHeights = new double[5][5];

      for (int gx = 0; gx < 5; gx++) {
         for (int gz = 0; gz < 5; gz++) {
            coarseHeights[gx][gz] = this.getHeightAt(baseX + gx * 4, baseZ + gz * 4);
         }
      }

      for (int x = 0; x < 16; x++) {
         for (int z = 0; z < 16; z++) {
            int gx = x / 4;
            int gz = z / 4;
            double fx = x % 4 / 4.0;
            double fz = z % 4 / 4.0;
            int gx1 = Math.min(gx + 1, 4);
            int gz1 = Math.min(gz + 1, 4);
            double h = coarseHeights[gx][gz] * (1.0 - fx) * (1.0 - fz)
               + coarseHeights[gx1][gz] * fx * (1.0 - fz)
               + coarseHeights[gx][gz1] * (1.0 - fx) * fz
               + coarseHeights[gx1][gz1] * fx * fz;
            fillCache.heightCache[x * 16 + z] = h;
            heights[x][z] = 64 + (int)Math.round(h);
         }
      }

      fillCache.heightChunkX = chunkX;
      fillCache.heightChunkZ = chunkZ;
      Mutable fillPos = new Mutable();

      for (int x = 0; x < 16; x++) {
         for (int z = 0; z < 16; z++) {
            int worldX = chunkX * 16 + x;
            int worldZ = chunkZ * 16 + z;
            int surfaceY = heights[x][z];
            boolean isGiantForest = this.isGiantForestBiome(worldX, worldZ);
            boolean isHighlandHere = !isGiantForest && this.isHighlandBiome(worldX, worldZ);
            boolean rockyColumn = surfaceY >= 150;
            double snowLineHere = 0.0;
            if (rockyColumn) {
               double snowBase = isMountainsBiomeGlobal(worldX, worldZ) ? 250.0 : 190.0;
               double wander = (fractalNoise(worldX * 0.01 + 3300.0, worldZ * 0.01 + 3300.0, 3) * 2.0 - 1.0) * 22.0;
               snowLineHere = snowBase + wander;
            }

            for (int y = chunk.getBottomY(); y <= surfaceY; y++) {
               BlockState blockToPlace;
               if (y == chunk.getBottomY()) {
                  blockToPlace = bedrock;
               } else if (y == surfaceY) {
                  if (rockyColumn) {
                     double above = surfaceY - snowLineHere;
                     if (above >= 8.0) {
                        blockToPlace = snowBlock;
                     } else if (above > -8.0) {
                        double snowProb = (above + 8.0) / 16.0;
                        double patch = fractalNoise(worldX * 0.09 + 3700.0, worldZ * 0.09 + 3700.0, 2);
                        blockToPlace = patch < snowProb ? snowBlock : stone;
                     } else {
                        blockToPlace = stone;
                     }
                  } else if (surfaceY >= 125) {
                     double mNoise = fractalNoise(worldX * 0.05 + 2100.0, worldZ * 0.05 + 2100.0, 2);
                     blockToPlace = mNoise > 0.5 ? stone : grass;
                  } else if (isGiantForest) {
                     double patchNoise = this.getPatchNoise(worldX, worldZ);
                     java.util.Random surfaceRand = new java.util.Random(positionSeed(worldX, worldZ));
                     if (patchNoise > 0.3) {
                        if (surfaceRand.nextDouble() < 0.05) {
                           blockToPlace = coarseDirt;
                        } else {
                           blockToPlace = podzol;
                        }
                     } else {
                        blockToPlace = grass;
                     }
                  } else if (isHighlandHere) {
                     double hlPatchNoise = fractalNoise(worldX * 0.01 + 1100.0, worldZ * 0.01 + 1100.0, 2);
                     if (hlPatchNoise > 0.55) {
                        blockToPlace = podzol;
                     } else {
                        blockToPlace = grass;
                     }
                  } else {
                     blockToPlace = grass;
                  }
               } else if (y >= surfaceY - 3) {
                  blockToPlace = rockyColumn ? stone : dirt;
               } else {
                  boolean isDeepslateLevel = y < 0;
                  BlockState baseStone = isDeepslateLevel ? deepslate : stone;
                  if (y > -60 && y < 80) {
                     double graniteNoise = getBlobNoise(worldX, y, worldZ, 0.03, 1234L);
                     if (graniteNoise > 0.88) {
                        fillPos.set(x, y, z);
                        chunk.setBlockState(fillPos, granite, false);
                        continue;
                     }

                     double dioriteNoise = getBlobNoise(worldX, y, worldZ, 0.03, 5678L);
                     if (dioriteNoise > 0.88) {
                        fillPos.set(x, y, z);
                        chunk.setBlockState(fillPos, diorite, false);
                        continue;
                     }

                     double andesiteNoise = getBlobNoise(worldX, y, worldZ, 0.03, 9012L);
                     if (andesiteNoise > 0.88) {
                        fillPos.set(x, y, z);
                        chunk.setBlockState(fillPos, andesite, false);
                        continue;
                     }
                  }

                  if (y > -50 && y < surfaceY - 4) {
                     double dirtNoise = getBlobNoise(worldX, y, worldZ, 0.025, 3456L);
                     if (dirtNoise > 0.9) {
                        fillPos.set(x, y, z);
                        chunk.setBlockState(fillPos, dirt, false);
                        continue;
                     }

                     double gravelNoise = getBlobNoise(worldX, y, worldZ, 0.03, 7890L);
                     if (gravelNoise > 0.92) {
                        fillPos.set(x, y, z);
                        chunk.setBlockState(fillPos, gravel, false);
                        continue;
                     }
                  }

                  blockToPlace = baseStone;
               }

               fillPos.set(x, y, z);
               chunk.setBlockState(fillPos, blockToPlace, false);
            }
         }
      }

      this.generateOreVeins(chunk, chunkX, chunkZ);
      return CompletableFuture.completedFuture(chunk);
   }

   private void generateOreVeins(Chunk chunk, int chunkX, int chunkZ) {
      java.util.Random oreRandom = new java.util.Random(positionSeed(chunkX * 31337, chunkZ * 73313));

      for (int i = 0; i < 20; i++) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(256) - 64;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 4 + oreRandom.nextInt(14);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, oreRandom);
      }

      for (int i = 0; i < 10; i++) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(136) - 64;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 2 + oreRandom.nextInt(8);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, oreRandom);
      }

      for (int i = 0; i < 6; i++) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(128) - 16;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 4 + oreRandom.nextInt(7);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, oreRandom);
      }

      for (int i = 0; i < 2; i++) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(96) - 64;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 2 + oreRandom.nextInt(8);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, oreRandom);
      }

      for (int i = 0; i < 4; i++) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(80) - 64;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 2 + oreRandom.nextInt(7);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, oreRandom);
      }

      int lapisCount = oreRandom.nextInt(2) + 1;

      for (int i = 0; i < lapisCount; i++) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = (oreRandom.nextInt(32) + oreRandom.nextInt(32)) / 2 - 32;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 2 + oreRandom.nextInt(6);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, oreRandom);
      }

      if (oreRandom.nextFloat() < 0.5F) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(80) - 64;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         int veinSize = 1 + oreRandom.nextInt(8);
         this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, oreRandom);
      }

      if (oreRandom.nextFloat() < 0.15F) {
         int veinX = chunkX * 16 + oreRandom.nextInt(16);
         int veinY = oreRandom.nextInt(80) - 16;
         int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
         this.generateOreVein(chunk, veinX, veinY, veinZ, 1, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, oreRandom);
      }

      Block zincOre = getZincOre();
      Block deepslateZincOre = getDeepslateZincOre();
      if (zincOre != Blocks.AIR) {
         for (int i = 0; i < 10; i++) {
            int veinX = chunkX * 16 + oreRandom.nextInt(16);
            int veinY = oreRandom.nextInt(136) - 64;
            int veinZ = chunkZ * 16 + oreRandom.nextInt(16);
            int veinSize = 2 + oreRandom.nextInt(8);
            this.generateOreVein(chunk, veinX, veinY, veinZ, veinSize, zincOre, deepslateZincOre, oreRandom);
         }
      }
   }

   private static Block getZincOre() {
      if (cachedZincOre == null) {
         if (FabricLoader.getInstance().isModLoaded("create")) {
            cachedZincOre = Registries.BLOCK.get(new Identifier("create", "zinc_ore"));
            if (cachedZincOre == Blocks.AIR) {
               DannysAot.LOGGER.info("Create mod loaded but zinc_ore block not found");
            } else {
               DannysAot.LOGGER.info("Create zinc_ore found, will generate in Paradis");
            }
         } else {
            cachedZincOre = Blocks.AIR;
         }
      }

      return cachedZincOre;
   }

   private static Block getDeepslateZincOre() {
      if (cachedDeepslateZincOre == null) {
         if (FabricLoader.getInstance().isModLoaded("create")) {
            cachedDeepslateZincOre = Registries.BLOCK.get(new Identifier("create", "deepslate_zinc_ore"));
            if (cachedDeepslateZincOre == Blocks.AIR) {
               cachedDeepslateZincOre = getZincOre();
            }
         } else {
            cachedDeepslateZincOre = Blocks.AIR;
         }
      }

      return cachedDeepslateZincOre;
   }

   private void generateOreVein(Chunk chunk, int centerX, int centerY, int centerZ, int size, Block ore, Block deepslateOre, java.util.Random random) {
      int chunkX = chunk.getPos().x;
      int chunkZ = chunk.getPos().z;
      List<int[]> orePositions = new ArrayList<>();
      orePositions.add(new int[]{centerX, centerY, centerZ});
      Mutable orePos = new Mutable();
      int placed = 0;
      int attempts = 0;

      while (placed < size && attempts < size * 4) {
         attempts++;
         if (orePositions.isEmpty()) {
            break;
         }

         int[] base = orePositions.get(random.nextInt(orePositions.size()));
         int dx = random.nextInt(3) - 1;
         int dy = random.nextInt(3) - 1;
         int dz = random.nextInt(3) - 1;
         if (dx != 0 || dy != 0 || dz != 0) {
            int newX = base[0] + dx;
            int newY = base[1] + dy;
            int newZ = base[2] + dz;
            if (newX >= chunkX * 16 && newX < chunkX * 16 + 16 && newZ >= chunkZ * 16 && newZ < chunkZ * 16 + 16 && newY >= -63 && newY <= 319) {
               orePos.set(newX, newY, newZ);
               BlockState current = chunk.getBlockState(orePos);
               if (current.isOf(Blocks.STONE)
                  || current.isOf(Blocks.DEEPSLATE)
                  || current.isOf(Blocks.GRANITE)
                  || current.isOf(Blocks.DIORITE)
                  || current.isOf(Blocks.ANDESITE)) {
                  boolean isDeepslate = newY < 0;
                  BlockState oreState = isDeepslate ? deepslateOre.getDefaultState() : ore.getDefaultState();
                  chunk.setBlockState(orePos, oreState, false);
                  orePositions.add(new int[]{newX, newY, newZ});
                  placed++;
               }
            }
         }
      }
   }

   private double getPatchNoise(int x, int z) {
      double scale1 = 0.05;
      double scale2 = 0.12;
      double scale3 = 0.25;
      double noise1 = Math.sin(x * scale1 + 1.5) * Math.cos(z * scale1 + 2.3);
      double noise2 = Math.sin(x * scale2 + 4.7) * Math.cos(z * scale2 + 3.1) * 0.5;
      double noise3 = Math.sin(x * scale3 + 2.1) * Math.cos(z * scale3 + 5.9) * 0.25;
      return noise1 + noise2 + noise3;
   }

   private static double hash3D(int x, int y, int z, long seedOffset) {
      long h = x * 374761393L + y * 668265263L + z * 1274126177L + seedOffset + worldSeed;
      h = (h ^ h >>> 13) * 1274126177L;
      h = (h ^ h >>> 16) * 73244475L;
      return (h & 65535L) / 65536.0;
   }

   private static double getBlobNoise(int x, int y, int z, double scale, long seedOffset) {
      double sx = x * scale;
      double sy = y * scale;
      double sz = z * scale;
      int gx = sx >= 0.0 ? (int)sx : (int)sx - 1;
      int gy = sy >= 0.0 ? (int)sy : (int)sy - 1;
      int gz = sz >= 0.0 ? (int)sz : (int)sz - 1;
      double fx = sx - gx;
      double fy = sy - gy;
      double fz = sz - gz;
      fx = fx * fx * (3.0 - 2.0 * fx);
      fy = fy * fy * (3.0 - 2.0 * fy);
      fz = fz * fz * (3.0 - 2.0 * fz);
      double c000 = hash3D(gx, gy, gz, seedOffset);
      double c100 = hash3D(gx + 1, gy, gz, seedOffset);
      double c010 = hash3D(gx, gy + 1, gz, seedOffset);
      double c110 = hash3D(gx + 1, gy + 1, gz, seedOffset);
      double c001 = hash3D(gx, gy, gz + 1, seedOffset);
      double c101 = hash3D(gx + 1, gy, gz + 1, seedOffset);
      double c011 = hash3D(gx, gy + 1, gz + 1, seedOffset);
      double c111 = hash3D(gx + 1, gy + 1, gz + 1, seedOffset);
      double i00 = c000 + (c100 - c000) * fx;
      double i10 = c010 + (c110 - c010) * fx;
      double i01 = c001 + (c101 - c001) * fx;
      double i11 = c011 + (c111 - c011) * fx;
      double j0 = i00 + (i10 - i00) * fy;
      double j1 = i01 + (i11 - i01) * fy;
      return j0 + (j1 - j0) * fz;
   }

   private double getOreVeinNoise(int x, int y, int z, double scale, long seedOffset) {
      long seed = worldSeed + seedOffset;
      double ox = seed % 10000L * 0.1;
      double oy = (seed >> 16) % 10000L * 0.1;
      double oz = (seed >> 32) % 10000L * 0.1;
      double sx = x * scale + ox;
      double sy = y * scale * 1.5 + oy;
      double sz = z * scale + oz;
      double noise1 = Math.sin(sx * 1.1 + sy * 0.6 + sz * 0.9) * Math.cos(sz * 1.2 + sy * 0.4 + sx * 0.8);
      double noise2 = Math.sin(sx * 2.3 + sz * 1.7) * Math.cos(sy * 2.1 + sx * 1.4) * 0.4;
      double noise3 = Math.sin(sy * 1.9 + sx * 1.2 + sz * 0.7) * Math.cos(sx * 1.6 + sz * 2.0) * 0.2;
      return (noise1 + noise2 + noise3 + 1.0) / 2.0;
   }

   private void generatePineTree(ChunkRegion region, int x, int baseY, int z, long seed, int chunkX, int chunkZ) {
      java.util.Random random = new java.util.Random(seed);
      BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
      BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
      int treeHeight = 12 + random.nextInt(9);

      for (int y = 0; y < treeHeight; y++) {
         BlockPos pos = new BlockPos(x, baseY + y, z);
         if (this.isInChunk(x, z, chunkX, chunkZ)
            && region.testBlockState(
               pos,
               state -> state.isAir()
                  || state.isOf(Blocks.GRASS_BLOCK)
                  || state.isOf(Blocks.SPRUCE_LEAVES)
                  || state.isOf(Blocks.FERN)
                  || state.isOf(Blocks.GRASS)
            )) {
            region.setBlockState(pos, log, 2);
         }
      }

      int foliageStart = 2 + random.nextInt(2);
      int foliageHeight = treeHeight - foliageStart;
      int maxRadius = 3 + treeHeight / 6;

      for (int yx = 0; yx < foliageHeight; yx++) {
         int currentY = baseY + foliageStart + yx;
         double progress = (double)yx / foliageHeight;
         int baseRadius = (int)(maxRadius * (1.0 - progress * 0.9));
         boolean isBulge = yx % 3 == 0 || yx % 3 == 1;
         int layerRadius = isBulge ? baseRadius + 1 : Math.max(1, baseRadius);
         if (layerRadius >= 1 || !(progress < 0.9)) {
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
               for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                  if (dx != 0 || dz != 0) {
                     double dist = Math.sqrt(dx * dx + dz * dz);
                     double effectiveRadius = layerRadius + 0.5;
                     double noise = hash((x + dx) * 47, (z + dz) * 53) * 0.8;
                     effectiveRadius += noise - 0.4;
                     if (dist <= effectiveRadius) {
                        int lx = x + dx;
                        int lz = z + dz;
                        if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                           BlockPos leafPos = new BlockPos(lx, currentY, lz);
                           if (region.testBlockState(leafPos, state -> state.isAir())) {
                              double density = dist <= layerRadius * 0.6 ? 0.95 : 0.8;
                              double leafHash = hash(lx * 6317 + currentY * 2819, lz * 4513);
                              if (leafHash < density) {
                                 region.setBlockState(leafPos, leaves, 2);
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (isBulge && layerRadius >= 2) {
               for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                  for (int dzx = -layerRadius; dzx <= layerRadius; dzx++) {
                     double dist = Math.sqrt(dx * dx + dzx * dzx);
                     if (dist > layerRadius - 1 && dist <= layerRadius + 0.5) {
                        int lx = x + dx;
                        int lz = z + dzx;
                        if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                           double hangHash = hash(lx * 3821 + currentY * 2143, lz * 4517);
                           int hangLen = 1 + (hangHash < 0.3 ? 1 : 0);

                           for (int h = 1; h <= hangLen; h++) {
                              BlockPos hangPos = new BlockPos(lx, currentY - h, lz);
                              if (region.testBlockState(hangPos, state -> state.isAir())) {
                                 double placeHash = hash(lx * 5623 + (currentY - h) * 6917, lz * 7219);
                                 if (placeHash < 0.6) {
                                    region.setBlockState(hangPos, leaves, 2);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            for (int dx = -1; dx <= 1; dx++) {
               for (int dzxx = -1; dzxx <= 1; dzxx++) {
                  if (dx != 0 || dzxx != 0) {
                     int lx = x + dx;
                     int lz = z + dzxx;
                     if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                        BlockPos fillPos = new BlockPos(lx, currentY, lz);
                        if (region.testBlockState(fillPos, state -> state.isAir())) {
                           region.setBlockState(fillPos, leaves, 2);
                        }
                     }
                  }
               }
            }
         }
      }

      int tipY = baseY + treeHeight;

      for (int i = 0; i < 3; i++) {
         if (this.isInChunk(x, z, chunkX, chunkZ)) {
            BlockPos tipPos = new BlockPos(x, tipY + i, z);
            region.setBlockState(tipPos, leaves, 2);
         }
      }

      if (this.isInChunk(x, z, chunkX, chunkZ)) {
         for (int d = -1; d <= 1; d++) {
            if (d != 0) {
               if (this.isInChunk(x + d, z, chunkX, chunkZ)) {
                  BlockPos sidePos = new BlockPos(x + d, tipY, z);
                  if (region.testBlockState(sidePos, state -> state.isAir())) {
                     region.setBlockState(sidePos, leaves, 2);
                  }
               }

               if (this.isInChunk(x, z + d, chunkX, chunkZ)) {
                  BlockPos sidePos = new BlockPos(x, tipY, z + d);
                  if (region.testBlockState(sidePos, state -> state.isAir())) {
                     region.setBlockState(sidePos, leaves, 2);
                  }
               }
            }
         }
      }
   }

   private void generateGiantTree(ChunkRegion region, int centerX, int baseY, int centerZ, int chunkX, int chunkZ) {
      int treeRadius = this.getGiantTreeRadius(centerX, centerZ);
      int treeHeight = this.getGiantTreeHeight(centerX, centerZ);
      java.util.Random random = new java.util.Random(positionSeed(centerX, centerZ));
      BlockState log = Blocks.ACACIA_LOG.getDefaultState();
      BlockState wood = Blocks.ACACIA_WOOD.getDefaultState();
      BlockState leaves = Blocks.DARK_OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
      BlockState canopyLeaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
      int numRoots = 5 + random.nextInt(4);
      int rootHeight = 12 + random.nextInt(8);

      for (int rootIdx = 0; rootIdx < numRoots; rootIdx++) {
         double rootAngle = rootIdx * 2 * Math.PI / numRoots + random.nextDouble() * 0.3;
         int rootExtension = 3 + random.nextInt(3);

         for (int y = 0; y < rootHeight; y++) {
            int currentY = baseY + y;
            double heightRatio = (double)y / rootHeight;
            double curveAmount = Math.pow(1.0 - heightRatio, 1.5);
            double rootDist = treeRadius + rootExtension * curveAmount;
            double rootThickness = 2.0 * (1.0 - heightRatio * 0.7);

            for (double angleDelta = -0.15; angleDelta <= 0.15; angleDelta += 0.05) {
               double angle = rootAngle + angleDelta;

               for (double d = treeRadius - 0.5; d <= rootDist; d += 0.5) {
                  int bx = centerX + (int)(Math.cos(angle) * d);
                  int bz = centerZ + (int)(Math.sin(angle) * d);
                  if (this.isInChunk(bx, bz, chunkX, chunkZ)) {
                     double mainRootX = centerX + Math.cos(rootAngle) * d;
                     double mainRootZ = centerZ + Math.sin(rootAngle) * d;
                     double distFromMain = Math.sqrt(Math.pow(bx - mainRootX, 2.0) + Math.pow(bz - mainRootZ, 2.0));
                     if (distFromMain <= rootThickness) {
                        BlockPos pos = new BlockPos(bx, currentY, bz);
                        region.setBlockState(pos, wood, 2);
                     }
                  }
               }
            }
         }
      }

      boolean hasTilt = random.nextDouble() < 0.4;
      double tiltAngle = random.nextDouble() * Math.PI * 2.0;
      double tiltAmount = hasTilt ? 0.005 + random.nextDouble() * 0.005 : 0.0;

      for (int y = 0; y < treeHeight; y++) {
         int currentY = baseY + y;
         double tiltProgress = (double)y / treeHeight;
         int tiltOffsetX = (int)(Math.cos(tiltAngle) * tiltAmount * y);
         int tiltOffsetZ = (int)(Math.sin(tiltAngle) * tiltAmount * y);

         for (int dx = -treeRadius; dx <= treeRadius; dx++) {
            for (int dz = -treeRadius; dz <= treeRadius; dz++) {
               double dist = Math.sqrt(dx * dx + dz * dz);
               if (dist <= treeRadius + 0.3) {
                  int blockX = centerX + dx + tiltOffsetX;
                  int blockZ = centerZ + dz + tiltOffsetZ;
                  if (this.isInChunk(blockX, blockZ, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(blockX, currentY, blockZ);
                     if (dist > treeRadius - 1.0) {
                        region.setBlockState(pos, wood, 2);
                     } else {
                        region.setBlockState(pos, log, 2);
                     }
                  }
               }
            }
         }
      }

      double[] cornerAngles = new double[]{Math.PI / 4, Math.PI * 3.0 / 4.0, Math.PI * 5.0 / 4.0, Math.PI * 7.0 / 4.0};

      for (double cornerAngle : cornerAngles) {
         int ridgesAtCorner = 2 + random.nextInt(3);

         for (int r = 0; r < ridgesAtCorner; r++) {
            int ridgeStartY = rootHeight + 8 + random.nextInt((int)(treeHeight * 0.75) - rootHeight);
            int ridgeLength = 10 + random.nextInt(11);
            double ridgeExtension = 1.0 + random.nextDouble() * 1.0;

            for (int ry = 0; ry < ridgeLength; ry++) {
               int currentY = baseY + ridgeStartY + ry;
               if (currentY >= baseY + treeHeight) {
                  break;
               }

               int ridgeYRelative = ridgeStartY + ry;
               int ridgeTiltX = (int)(Math.cos(tiltAngle) * tiltAmount * ridgeYRelative);
               int ridgeTiltZ = (int)(Math.sin(tiltAngle) * tiltAmount * ridgeYRelative);
               double taperFactor = 1.0;
               if (ry < 2) {
                  taperFactor = 0.5 + ry * 0.25;
               } else if (ry > ridgeLength - 2) {
                  taperFactor = 0.5 + (ridgeLength - ry - 1) * 0.25;
               }

               double currentExtension = ridgeExtension * taperFactor;

               for (double dx = treeRadius - 0.5; dx <= treeRadius + currentExtension; dx += 0.5) {
                  int rx = centerX + ridgeTiltX + (int)(Math.cos(cornerAngle) * dx);
                  int rz = centerZ + ridgeTiltZ + (int)(Math.sin(cornerAngle) * dx);
                  if (this.isInChunk(rx, rz, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(rx, currentY, rz);
                     region.setBlockState(pos, wood, 2);
                  }
               }

               int cornerX = centerX + ridgeTiltX + (int)(Math.cos(cornerAngle) * treeRadius);
               int cornerZ = centerZ + ridgeTiltZ + (int)(Math.sin(cornerAngle) * treeRadius);

               for (int adj = -1; adj <= 1; adj++) {
                  int adjX = cornerX + (int)(Math.cos(cornerAngle + (Math.PI / 2)) * adj * 0.7);
                  int adjZ = cornerZ + (int)(Math.sin(cornerAngle + (Math.PI / 2)) * adj * 0.7);
                  if (this.isInChunk(adjX, adjZ, chunkX, chunkZ)) {
                     BlockPos adjPos = new BlockPos(adjX, currentY, adjZ);
                     region.setBlockState(adjPos, wood, 2);
                  }
               }
            }
         }
      }

      int structuralBranchStart = 25;
      int foliageStart = (int)(treeHeight * 0.6);

      for (int tierBaseY = structuralBranchStart; tierBaseY < foliageStart; tierBaseY += 20 + random.nextInt(12)) {
         int numBranches = 1 + random.nextInt(2);
         double branchOffset = random.nextDouble() * Math.PI * 2.0 / numBranches;

         for (int b = 0; b < numBranches; b++) {
            double angle = b * 2 * Math.PI / numBranches + branchOffset;
            int branchLen = 8 + random.nextInt(8);
            int branchYOffset = random.nextInt(9) - 4;
            int branchY = tierBaseY + branchYOffset;
            int structTiltX = (int)(Math.cos(tiltAngle) * tiltAmount * branchY);
            int structTiltZ = (int)(Math.sin(tiltAngle) * tiltAmount * branchY);

            for (double i = 0.0; i < branchLen; i += 0.5) {
               int droop = (int)(i / 4.0);
               double bx = centerX + structTiltX + Math.cos(angle) * (treeRadius + i);
               double bz = centerZ + structTiltZ + Math.sin(angle) * (treeRadius + i);
               int by = baseY + branchY - droop;

               for (int ox = -1; ox <= 1; ox++) {
                  for (int oz = -1; oz <= 1; oz++) {
                     if (Math.abs(ox) != 1 || Math.abs(oz) != 1) {
                        int bx1 = (int)bx + ox;
                        int bz1 = (int)bz + oz;
                        if (this.isInChunk(bx1, bz1, chunkX, chunkZ)) {
                           region.setBlockState(new BlockPos(bx1, by, bz1), wood, 2);
                           if (i > 1.0 && i < branchLen - 1) {
                              region.setBlockState(new BlockPos(bx1, by + 1, bz1), wood, 2);
                           }
                        }
                     }
                  }
               }
            }

            int tipX = centerX + structTiltX + (int)(Math.cos(angle) * (treeRadius + branchLen));
            int tipZ = centerZ + structTiltZ + (int)(Math.sin(angle) * (treeRadius + branchLen));
            int tipY = baseY + branchY - branchLen / 4;
            int clusterRadius = 4;

            for (int dxx = -clusterRadius; dxx <= clusterRadius; dxx++) {
               for (int dzx = -clusterRadius; dzx <= clusterRadius; dzx++) {
                  for (int dy = -3; dy <= 3; dy++) {
                     double baseDist = Math.sqrt(dxx * dxx + dzx * dzx + dy * dy * 0.6);
                     double noiseVal = hash((tipX + dxx) * 71 + dy, (tipZ + dzx) * 97);
                     double irregularity = (noiseVal - 0.5) * 2.0;
                     if (baseDist <= clusterRadius + irregularity) {
                        int lx = tipX + dxx;
                        int lz = tipZ + dzx;
                        if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                           BlockPos leafPos = new BlockPos(lx, tipY + dy, lz);
                           if (region.testBlockState(leafPos, state -> state.isAir())) {
                              double leafHash = hash(lx * 6173 + (tipY + dy) * 2731, lz * 5419);
                              if (leafHash < 0.7) {
                                 region.setBlockState(leafPos, leaves, 2);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      int branchZone = treeHeight - foliageStart - 10;
      int numBranchTiers = 2 + random.nextInt(2);

      for (int tier = 0; tier < numBranchTiers; tier++) {
         double tierProgress = (double)tier / numBranchTiers;
         int tierBaseY = baseY + foliageStart + (int)(tierProgress * branchZone);
         int branchesInTier = 2 + random.nextInt(2);
         double tierAngleOffset = random.nextDouble() * Math.PI * 2.0 / branchesInTier;
         int maxBranchLen = (int)((1.0 - tierProgress * 0.5) * (7 + random.nextInt(5)));

         for (int b = 0; b < branchesInTier; b++) {
            double branchAngle = b * 2 * Math.PI / branchesInTier + tierAngleOffset;
            int branchLen = maxBranchLen - random.nextInt(3);
            int branchYOffset = random.nextInt(11) - 5;
            int tierY = tierBaseY + branchYOffset;
            int branchYRelative = tierY - baseY;
            int branchTiltX = (int)(Math.cos(tiltAngle) * tiltAmount * branchYRelative);
            int branchTiltZ = (int)(Math.sin(tiltAngle) * tiltAmount * branchYRelative);
            boolean thinBranch = branchLen > 8;
            int branchThickness = thinBranch ? 0 : 1;

            for (double i = 0.0; i < branchLen; i += 0.5) {
               int droop = (int)(Math.pow(i, 1.8) * 0.03);
               double bxd = centerX + branchTiltX + Math.cos(branchAngle) * (treeRadius + i);
               double bzd = centerZ + branchTiltZ + Math.sin(branchAngle) * (treeRadius + i);
               int by = tierY - droop;

               for (int ox = -branchThickness; ox <= branchThickness; ox++) {
                  for (int ozx = -branchThickness; ozx <= branchThickness; ozx++) {
                     if (branchThickness <= 0 || Math.abs(ox) != 1 || Math.abs(ozx) != 1) {
                        int bx = (int)bxd + ox;
                        int bz = (int)bzd + ozx;
                        if (this.isInChunk(bx, bz, chunkX, chunkZ)) {
                           region.setBlockState(new BlockPos(bx, by, bz), wood, 2);
                           if (!thinBranch && i > 1.0 && i < branchLen - 1) {
                              region.setBlockState(new BlockPos(bx, by + 1, bz), wood, 2);
                           }
                        }
                     }
                  }
               }

               if (i >= 4.0 && i % 3.0 == 0.0) {
                  int bx = (int)bxd;
                  int bz = (int)bzd;
                  this.generateThickDreadlock(
                     region, bx, by, bz, 6 + random.nextInt(8), 2, branchAngle + (random.nextDouble() - 0.5) * 0.6, random, leaves, chunkX, chunkZ
                  );
               }
            }

            int tipX = centerX + branchTiltX + (int)(Math.cos(branchAngle) * (treeRadius + branchLen));
            int tipZ = centerZ + branchTiltZ + (int)(Math.sin(branchAngle) * (treeRadius + branchLen));
            int tipDroop = (int)(Math.pow(branchLen, 1.8) * 0.03);
            int tipY = tierY - tipDroop;
            int leafRadius = thinBranch ? 3 : 4;

            for (int dxx = -leafRadius; dxx <= leafRadius; dxx++) {
               for (int dzx = -leafRadius; dzx <= leafRadius; dzx++) {
                  for (int dyx = -3; dyx <= 3; dyx++) {
                     double baseDist = Math.sqrt(dxx * dxx + dzx * dzx + dyx * dyx * 0.6);
                     double noiseVal = hash((tipX + dxx) * 83 + dyx, (tipZ + dzx) * 67);
                     double irregularity = (noiseVal - 0.5) * 1.8;
                     if (baseDist <= leafRadius + irregularity) {
                        int lx = tipX + dxx;
                        int lz = tipZ + dzx;
                        if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                           BlockPos leafPos = new BlockPos(lx, tipY + dyx, lz);
                           if (region.testBlockState(leafPos, state -> state.isAir())) {
                              double leafHash = hash(lx * 7321 + (tipY + dyx) * 3119, lz * 4729);
                              if (leafHash < 0.75) {
                                 region.setBlockState(leafPos, leaves, 2);
                              }
                           }
                        }
                     }
                  }
               }
            }

            int numTipDreads = 3 + random.nextInt(3);

            for (int dxx = 0; dxx < numTipDreads; dxx++) {
               double dreadAngle = branchAngle + (random.nextDouble() - 0.5) * 1.2;
               int dreadLen = 8 + random.nextInt(10);
               this.generateThickDreadlock(region, tipX, tipY - 1, tipZ, dreadLen, 2, dreadAngle, random, leaves, chunkX, chunkZ);
            }
         }
      }

      int topY = baseY + treeHeight - 30;
      int topHeight = 50 + random.nextInt(20);
      if (topY + topHeight > 315) {
         topHeight = 315 - topY;
      }

      int numCanopyTiers = 8 + random.nextInt(4);

      for (int tier = 0; tier < numCanopyTiers; tier++) {
         double tierProgress = (double)tier / (numCanopyTiers - 1);
         int tierY = topY + (int)(tierProgress * topHeight);
         int tierYRelative = treeHeight - 30 + (int)(tierProgress * topHeight);
         int tierTiltX = (int)(Math.cos(tiltAngle) * tiltAmount * tierYRelative);
         int tierTiltZ = (int)(Math.sin(tiltAngle) * tiltAmount * tierYRelative);
         double domeShape = 1.0 - Math.pow(tierProgress * 2.0 - 1.0, 2.0);
         int maxBranchLen = (int)(18.0 + domeShape * 12.0);
         if (tierProgress > 0.85) {
            maxBranchLen = (int)(maxBranchLen * (1.0 - (tierProgress - 0.85) * 4.0));
         }

         int numBranches = 6 + random.nextInt(3);
         double angleOffset = tier * 0.5 + random.nextDouble() * 0.3;

         for (int b = 0; b < numBranches; b++) {
            double branchAngle = b * 2 * Math.PI / numBranches + angleOffset;
            int branchLen = maxBranchLen - random.nextInt(6);

            for (int i = 3; i <= branchLen; i++) {
               int bx = centerX + tierTiltX + (int)(Math.cos(branchAngle) * i);
               int bz = centerZ + tierTiltZ + (int)(Math.sin(branchAngle) * i);
               if (this.isInChunk(bx, bz, chunkX, chunkZ) && (i % 2 == 0 || i >= branchLen - 2)) {
                  int blobRadius = 2 + random.nextInt(2);

                  for (int dxx = -blobRadius; dxx <= blobRadius; dxx++) {
                     for (int dzx = -blobRadius; dzx <= blobRadius; dzx++) {
                        for (int dyxx = -2; dyxx <= 2; dyxx++) {
                           double baseDist = Math.sqrt(dxx * dxx + dzx * dzx + dyxx * dyxx * 0.7);
                           double noiseOffset = (hash((bx + dxx) * 73 + dyxx, (bz + dzx) * 91) - 0.5) * 1.5;
                           if (baseDist <= blobRadius + noiseOffset) {
                              int lx = bx + dxx;
                              int lz = bz + dzx;
                              int ly = tierY + dyxx;
                              if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                                 BlockPos pos = new BlockPos(lx, ly, lz);
                                 if (region.testBlockState(pos, state -> state.isAir())) {
                                    double leafHash = hash(lx * 8191 + ly * 4093, lz * 6151);
                                    if (leafHash < 0.85) {
                                       region.setBlockState(pos, canopyLeaves, 2);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (random.nextDouble() < 0.7) {
               int dreadX = centerX + tierTiltX + (int)(Math.cos(branchAngle) * branchLen);
               int dreadZ = centerZ + tierTiltZ + (int)(Math.sin(branchAngle) * branchLen);
               int dreadLen = 8 + random.nextInt(12);
               this.generateThickDreadlock(region, dreadX, tierY, dreadZ, dreadLen, 2, branchAngle, random, canopyLeaves, chunkX, chunkZ);
            }
         }

         int centerRadius = 3 + (int)(domeShape * 4.0);

         for (int dxx = -centerRadius; dxx <= centerRadius; dxx++) {
            for (int dzx = -centerRadius; dzx <= centerRadius; dzx++) {
               double dist = Math.sqrt(dxx * dxx + dzx * dzx);
               if (dist <= centerRadius) {
                  int lx = centerX + tierTiltX + dxx;
                  int lz = centerZ + tierTiltZ + dzx;
                  if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(lx, tierY, lz);
                     if (region.testBlockState(pos, state -> state.isAir())) {
                        double leafHash = hash(lx * 5231 + tierY * 3517, lz * 7127);
                        if (leafHash < 0.9) {
                           region.setBlockState(pos, canopyLeaves, 2);
                        }
                     }
                  }
               }
            }
         }
      }

      int crownY = topY + topHeight;
      int crownTiltX = (int)(Math.cos(tiltAngle) * tiltAmount * (treeHeight + topHeight));
      int crownTiltZ = (int)(Math.sin(tiltAngle) * tiltAmount * (treeHeight + topHeight));

      for (int dyxxx = 0; dyxxx < 6; dyxxx++) {
         int tipY = crownY + dyxxx;
         if (tipY > 318) {
            break;
         }

         int crownRadius = Math.max(1, 4 - dyxxx);

         for (int dxx = -crownRadius; dxx <= crownRadius; dxx++) {
            for (int dzxx = -crownRadius; dzxx <= crownRadius; dzxx++) {
               double dist = Math.sqrt(dxx * dxx + dzxx * dzxx);
               if (dist <= crownRadius + 0.3) {
                  int lx = centerX + crownTiltX + dxx;
                  int lz = centerZ + crownTiltZ + dzxx;
                  if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                     BlockPos pos = new BlockPos(lx, tipY, lz);
                     if (region.testBlockState(pos, state -> state.isAir())) {
                        region.setBlockState(pos, canopyLeaves, 2);
                     }
                  }
               }
            }
         }
      }
   }

   private void generateThickDreadlock(
      ChunkRegion region,
      int startX,
      int startY,
      int startZ,
      int length,
      int thickness,
      double angle,
      java.util.Random random,
      BlockState leaves,
      int chunkX,
      int chunkZ
   ) {
      for (int s = 0; s < length; s++) {
         double sway = Math.sin(s * 0.4 + angle * 3.0) * 2.0;
         int centerSX = startX + (int)(Math.cos(angle) * (s * 0.15 + sway * 0.3));
         int centerSZ = startZ + (int)(Math.sin(angle) * (s * 0.15 + sway * 0.3));
         int sy = startY - s;

         for (int dx = -thickness; dx <= thickness; dx++) {
            for (int dz = -thickness; dz <= thickness; dz++) {
               double dist = Math.sqrt(dx * dx + dz * dz);
               if (dist <= thickness) {
                  int sx = centerSX + dx;
                  int sz = centerSZ + dz;
                  if (this.isInChunk(sx, sz, chunkX, chunkZ)) {
                     BlockPos leafPos = new BlockPos(sx, sy, sz);
                     if (region.testBlockState(leafPos, state -> state.isAir())) {
                        double density = 0.9;
                        if (dist > thickness * 0.6) {
                           density = 0.7;
                        }

                        if (s > length * 0.7) {
                           density *= 0.8;
                        }

                        double leafHash = hash(sx * 4391 + sy * 2917, sz * 6113);
                        if (leafHash < density) {
                           region.setBlockState(leafPos, leaves, 2);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addGiantTreeGroundVegetation(ChunkRegion region, int treeX, int treeZ, int chunkX, int chunkZ) {
      int treeRadius = this.getGiantTreeRadius(treeX, treeZ);
      java.util.Random random = new java.util.Random(positionSeed(treeX * 1111, treeZ * 2222));
      BlockState shortGrass = Blocks.GRASS.getDefaultState();
      BlockState fern = Blocks.FERN.getDefaultState();
      BlockState oakLeaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
      int vegetationRadius = treeRadius + 20;

      for (int i = 0; i < 150; i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double dist = treeRadius + 1 + random.nextDouble() * (vegetationRadius - treeRadius - 1);
         int vx = treeX + (int)(Math.cos(angle) * dist);
         int vz = treeZ + (int)(Math.sin(angle) * dist);
         if (this.isInChunk(vx, vz, chunkX, chunkZ) && !this.isOnPathCached(vx, vz, chunkX, chunkZ)) {
            int vy = 64 + (int)Math.round(this.getCachedHeight(vx, vz, chunkX, chunkZ));
            if (!this.hasCaveBelowSurface(region, vx, vy, vz)) {
               BlockPos pos = new BlockPos(vx, vy + 1, vz);
               BlockPos below = pos.down();
               if (region.testBlockState(pos, state -> state.isAir())
                  && region.testBlockState(below, state -> state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.COARSE_DIRT))) {
                  double r = random.nextDouble();
                  if (r < 0.35) {
                     region.setBlockState(pos, shortGrass, 2);
                  } else if (r < 0.5) {
                     region.setBlockState(pos, fern, 2);
                  } else if (r < 0.7) {
                     BlockPos above = pos.up();
                     if (region.testBlockState(above, state -> state.isAir())) {
                        TallPlantBlock.placeAt(region, Blocks.TALL_GRASS.getDefaultState(), pos, 2);
                     }
                  } else if (r < 0.9) {
                     BlockPos above = pos.up();
                     if (region.testBlockState(above, state -> state.isAir())) {
                        TallPlantBlock.placeAt(region, Blocks.LARGE_FERN.getDefaultState(), pos, 2);
                     }
                  } else {
                     this.generateBush(region, vx, vy + 1, vz, chunkX, chunkZ, oakLeaves, random);
                  }
               }
            }
         }
      }

      java.util.Random pineCountRandom = new java.util.Random(positionSeed(treeX * 9991, treeZ * 8881));
      int numPineTrees = 3 + pineCountRandom.nextInt(4);

      for (int ix = 0; ix < numPineTrees; ix++) {
         java.util.Random pineRandom = new java.util.Random(positionSeed(treeX * 7789 + ix * 1013, treeZ * 8887 + ix * 2027));
         double angle = pineRandom.nextDouble() * Math.PI * 2.0;
         double dist = treeRadius + 8 + pineRandom.nextDouble() * 15.0;
         int px = treeX + (int)(Math.cos(angle) * dist);
         int pz = treeZ + (int)(Math.sin(angle) * dist);
         if (!this.isOnPathFast(px, pz)) {
            int py = 64 + (int)Math.round(this.getCachedHeight(px, pz, chunkX, chunkZ));
            java.util.Random pineVarRand = new java.util.Random(positionSeed(px * 4441, pz * 5551));
            String variant = PINE_TREE_VARIANTS[pineVarRand.nextInt(PINE_TREE_VARIANTS.length)];
            BlockRotation pineRotation = BlockRotation.values()[pineVarRand.nextInt(4)];
            this.placeStructureChunkAware(region, variant, px, py + 1, pz, pineRotation, chunkX, chunkZ);
         }
      }

      int numBushes = 3 + random.nextInt(3);

      for (int ixx = 0; ixx < numBushes; ixx++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double dist = treeRadius + 2 + random.nextDouble() * 10.0;
         int bx = treeX + (int)(Math.cos(angle) * dist);
         int bz = treeZ + (int)(Math.sin(angle) * dist);
         if (this.isInChunk(bx, bz, chunkX, chunkZ) && !this.isOnPathCached(bx, bz, chunkX, chunkZ)) {
            int by = 64 + (int)Math.round(this.getCachedHeight(bx, bz, chunkX, chunkZ));
            BlockPos pos = new BlockPos(bx, by + 1, bz);
            BlockPos below = pos.down();
            if (region.testBlockState(pos, state -> state.isAir())
               && region.testBlockState(below, state -> state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.COARSE_DIRT))) {
               this.generateBush(region, bx, by + 1, bz, chunkX, chunkZ, oakLeaves, random);
            }
         }
      }
   }

   private void generateBush(ChunkRegion region, int x, int y, int z, int chunkX, int chunkZ, BlockState leaves, java.util.Random random) {
      int bushHeight = 2 + random.nextInt(2);
      int bushRadius = 2 + random.nextInt(2);

      for (int dy = 0; dy < bushHeight; dy++) {
         double heightRatio = (double)dy / bushHeight;
         int currentRadius = Math.max(1, (int)(bushRadius * (1.0 - heightRatio * 0.6)));

         for (int dx = -currentRadius; dx <= currentRadius; dx++) {
            for (int dz = -currentRadius; dz <= currentRadius; dz++) {
               double dist = Math.sqrt(dx * dx + dz * dz);
               if (dist <= currentRadius + 0.3) {
                  int bx = x + dx;
                  int bz = z + dz;
                  int by = y + dy;
                  if (this.isInChunk(bx, bz, chunkX, chunkZ)) {
                     BlockPos bushPos = new BlockPos(bx, by, bz);
                     if (region.testBlockState(bushPos, state -> state.isAir()) && random.nextDouble() < 0.8) {
                        region.setBlockState(bushPos, leaves, 2);
                     }
                  }
               }
            }
         }
      }
   }

   private void generateSmallPineTree(ChunkRegion region, int x, int baseY, int z, long seed, int chunkX, int chunkZ) {
      java.util.Random random = new java.util.Random(seed);
      BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
      BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
      int treeHeight = 12 + random.nextInt(9);

      for (int y = 0; y < treeHeight; y++) {
         BlockPos pos = new BlockPos(x, baseY + y, z);
         if (this.isInChunk(x, z, chunkX, chunkZ)
            && region.testBlockState(
               pos,
               state -> state.isAir()
                  || state.isOf(Blocks.GRASS_BLOCK)
                  || state.isOf(Blocks.PODZOL)
                  || state.isOf(Blocks.COARSE_DIRT)
                  || state.isOf(Blocks.SPRUCE_LEAVES)
                  || state.isOf(Blocks.FERN)
                  || state.isOf(Blocks.GRASS)
            )) {
            region.setBlockState(pos, log, 2);
         }
      }

      int foliageStart = 2 + random.nextInt(2);
      int foliageHeight = treeHeight - foliageStart;
      int maxRadius = 3 + treeHeight / 6;

      for (int yx = 0; yx < foliageHeight; yx++) {
         int currentY = baseY + foliageStart + yx;
         double progress = (double)yx / foliageHeight;
         int baseRadius = (int)(maxRadius * (1.0 - progress * 0.9));
         boolean isBulge = yx % 3 == 0 || yx % 3 == 1;
         int layerRadius = isBulge ? baseRadius + 1 : Math.max(1, baseRadius);
         if (layerRadius >= 1 || !(progress < 0.9)) {
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
               for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                  if (dx != 0 || dz != 0) {
                     double dist = Math.sqrt(dx * dx + dz * dz);
                     double effectiveRadius = layerRadius + 0.5;
                     double noise = hash((x + dx) * 47, (z + dz) * 53) * 0.8;
                     effectiveRadius += noise - 0.4;
                     if (dist <= effectiveRadius) {
                        int lx = x + dx;
                        int lz = z + dz;
                        if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                           BlockPos leafPos = new BlockPos(lx, currentY, lz);
                           if (region.testBlockState(leafPos, state -> state.isAir())) {
                              double density = dist <= layerRadius * 0.6 ? 0.95 : 0.8;
                              double leafHash = hash(lx * 7919 + currentY * 1231, lz * 6173);
                              if (leafHash < density) {
                                 region.setBlockState(leafPos, leaves, 2);
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (isBulge && layerRadius >= 2) {
               for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                  for (int dzx = -layerRadius; dzx <= layerRadius; dzx++) {
                     double dist = Math.sqrt(dx * dx + dzx * dzx);
                     if (dist > layerRadius - 1 && dist <= layerRadius + 0.5) {
                        int lx = x + dx;
                        int lz = z + dzx;
                        if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                           double hangHash = hash(lx * 3571 + currentY * 2341, lz * 4219);
                           int hangLen = 1 + (hangHash < 0.3 ? 1 : 0);

                           for (int h = 1; h <= hangLen; h++) {
                              BlockPos hangPos = new BlockPos(lx, currentY - h, lz);
                              if (region.testBlockState(hangPos, state -> state.isAir())) {
                                 double placeHash = hash(lx * 5431 + (currentY - h) * 6781, lz * 7321);
                                 if (placeHash < 0.6) {
                                    region.setBlockState(hangPos, leaves, 2);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            for (int dx = -1; dx <= 1; dx++) {
               for (int dzxx = -1; dzxx <= 1; dzxx++) {
                  if (dx != 0 || dzxx != 0) {
                     int lx = x + dx;
                     int lz = z + dzxx;
                     if (this.isInChunk(lx, lz, chunkX, chunkZ)) {
                        BlockPos fillPos = new BlockPos(lx, currentY, lz);
                        if (region.testBlockState(fillPos, state -> state.isAir())) {
                           region.setBlockState(fillPos, leaves, 2);
                        }
                     }
                  }
               }
            }
         }
      }

      int tipY = baseY + treeHeight;

      for (int i = 0; i < 3; i++) {
         if (this.isInChunk(x, z, chunkX, chunkZ)) {
            BlockPos tipPos = new BlockPos(x, tipY + i, z);
            region.setBlockState(tipPos, leaves, 2);
         }
      }

      if (this.isInChunk(x, z, chunkX, chunkZ)) {
         for (int d = -1; d <= 1; d++) {
            if (d != 0) {
               if (this.isInChunk(x + d, z, chunkX, chunkZ)) {
                  BlockPos sidePos = new BlockPos(x + d, tipY, z);
                  if (region.testBlockState(sidePos, state -> state.isAir())) {
                     region.setBlockState(sidePos, leaves, 2);
                  }
               }

               if (this.isInChunk(x, z + d, chunkX, chunkZ)) {
                  BlockPos sidePos = new BlockPos(x, tipY, z + d);
                  if (region.testBlockState(sidePos, state -> state.isAir())) {
                     region.setBlockState(sidePos, leaves, 2);
                  }
               }
            }
         }
      }
   }

   private int getSmallPineTreeHeight(int x, int z) {
      java.util.Random random = new java.util.Random(positionSeed(x * 4441, z * 5551));
      return 12 + random.nextInt(9);
   }

   private int getSmallPineTreeRadius(int treeHeight) {
      return 3 + treeHeight / 6 + 2;
   }

   private void addClusterVegetation(ChunkRegion region, int[][] treePositions, int chunkX, int chunkZ, boolean chunkNearVillage, boolean chunkNearPath) {
      BlockState shortGrass = Blocks.GRASS.getDefaultState();
      BlockState fern = Blocks.FERN.getDefaultState();
      BlockState deadBush = Blocks.DEAD_BUSH.getDefaultState();

      for (int[] treePos : treePositions) {
         int treeX = treePos[0];
         int treeZ = treePos[1];
         java.util.Random random = new java.util.Random(positionSeed(treeX * 1337, treeZ * 7331));
         int radius = 10;
         int vegetationCount = 55;

         for (int i = 0; i < vegetationCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 2.0 + random.nextDouble() * (radius - 2);
            int x = treeX + (int)(Math.cos(angle) * dist);
            int z = treeZ + (int)(Math.sin(angle) * dist);
            if (this.isInChunk(x, z, chunkX, chunkZ)
               && (!chunkNearVillage || !this.isInsideOrOnVillageWalls(x, z))
               && (!chunkNearPath || !this.isOnPathCached(x, z, chunkX, chunkZ))
               && !this.isNearCaveEntranceCached(x, z, chunkX, chunkZ)) {
               int y = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ)) + 1;
               if (!this.hasCaveBelowSurface(region, x, y - 1, z)) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockPos below = pos.down();
                  if (region.testBlockState(pos, state -> state.isAir()) && region.testBlockState(below, state -> state.isOf(Blocks.GRASS_BLOCK))) {
                     double r = random.nextDouble();
                     if (r < 0.15) {
                        region.setBlockState(pos, shortGrass, 2);
                     } else if (r < 0.25) {
                        region.setBlockState(pos, fern, 2);
                     } else if (r < 0.55) {
                        BlockPos above = pos.up();
                        if (region.testBlockState(above, state -> state.isAir())) {
                           TallPlantBlock.placeAt(region, Blocks.TALL_GRASS.getDefaultState(), pos, 2);
                        }
                     } else if (r < 0.95) {
                        BlockPos above = pos.up();
                        if (region.testBlockState(above, state -> state.isAir())) {
                           TallPlantBlock.placeAt(region, Blocks.LARGE_FERN.getDefaultState(), pos, 2);
                        }
                     } else {
                        region.setBlockState(pos, deadBush, 2);
                     }
                  }
               }
            }
         }
      }
   }

   private void addVegetation(ChunkRegion region, int chunkX, int chunkZ, boolean chunkNearVillage, boolean chunkNearPath) {
      java.util.Random random = new java.util.Random(positionSeed(chunkX, chunkZ));
      int chunkCenterX = chunkX * 16 + 8;
      int chunkCenterZ = chunkZ * 16 + 8;
      boolean isGiantForest = this.isGiantForestBiome(chunkCenterX, chunkCenterZ);
      BlockState shortGrass = Blocks.GRASS.getDefaultState();
      BlockState fern = Blocks.FERN.getDefaultState();
      BlockState deadBush = Blocks.DEAD_BUSH.getDefaultState();
      BlockState brownMushroom = Blocks.BROWN_MUSHROOM.getDefaultState();
      BlockState redMushroom = Blocks.RED_MUSHROOM.getDefaultState();
      BlockState mossCarpet = Blocks.MOSS_CARPET.getDefaultState();

      for (int i = 0; i < 80; i++) {
         int x = chunkX * 16 + random.nextInt(16);
         int z = chunkZ * 16 + random.nextInt(16);
         if ((!chunkNearVillage || !this.isInsideOrOnVillageWalls(x, z))
            && (!chunkNearPath || !this.isOnPathCached(x, z, chunkX, chunkZ))
            && !this.isNearCaveEntranceCached(x, z, chunkX, chunkZ)) {
            int y = 64 + (int)Math.round(this.getCachedHeight(x, z, chunkX, chunkZ)) + 1;
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.down();
            if (!this.hasCaveBelowSurface(region, x, y - 1, z) && region.testBlockState(pos, state -> state.isAir())) {
               boolean isPodzolBelow = region.testBlockState(below, state -> state.isOf(Blocks.PODZOL) || state.isOf(Blocks.COARSE_DIRT));
               boolean isGrassBelow = region.testBlockState(below, state -> state.isOf(Blocks.GRASS_BLOCK));
               if (isPodzolBelow) {
                  double r = random.nextDouble();
                  if (r < 0.1) {
                     region.setBlockState(pos, deadBush, 2);
                  } else if (r < 0.15) {
                     region.setBlockState(pos, brownMushroom, 2);
                  } else if (r < 0.18) {
                     region.setBlockState(pos, redMushroom, 2);
                  } else if (r < 0.5) {
                     region.setBlockState(pos, fern, 2);
                  } else if (r < 0.75) {
                     BlockPos above = pos.up();
                     if (region.testBlockState(above, state -> state.isAir())) {
                        TallPlantBlock.placeAt(region, Blocks.LARGE_FERN.getDefaultState(), pos, 2);
                     }
                  }
               } else if (isGrassBelow) {
                  double r = random.nextDouble();
                  if (r < 0.65) {
                     region.setBlockState(pos, shortGrass, 2);
                  } else if (r < 0.8) {
                     region.setBlockState(pos, fern, 2);
                  } else if (r < 0.9) {
                     BlockPos above = pos.up();
                     if (region.testBlockState(above, state -> state.isAir())) {
                        TallPlantBlock.placeAt(region, Blocks.TALL_GRASS.getDefaultState(), pos, 2);
                     }
                  } else {
                     BlockPos above = pos.up();
                     if (region.testBlockState(above, state -> state.isAir())) {
                        TallPlantBlock.placeAt(region, Blocks.LARGE_FERN.getDefaultState(), pos, 2);
                     }
                  }
               }
            }
         }
      }

      if (isGiantForest) {
         for (int ix = 0; ix < 30; ix++) {
            int patchCenterX = chunkX * 16 + random.nextInt(16);
            int patchCenterZ = chunkZ * 16 + random.nextInt(16);
            double mossNoise = this.getPatchNoise(patchCenterX + 500, patchCenterZ + 500);
            if (!(mossNoise < 0.4)) {
               int patchSize = 2 + random.nextInt(3);

               for (int dx = -patchSize; dx <= patchSize; dx++) {
                  for (int dz = -patchSize; dz <= patchSize; dz++) {
                     if (Math.abs(dx) + Math.abs(dz) <= patchSize + 1 && !(random.nextDouble() > 0.6)) {
                        int mx = patchCenterX + dx;
                        int mz = patchCenterZ + dz;
                        if (this.isInChunk(mx, mz, chunkX, chunkZ)
                           && !this.isInsideOrOnVillageWalls(mx, mz)
                           && !this.isOnPathCached(mx, mz, chunkX, chunkZ)
                           && !this.isNearCaveEntranceCached(mx, mz, chunkX, chunkZ)) {
                           int my = 64 + (int)Math.round(this.getCachedHeight(mx, mz, chunkX, chunkZ)) + 1;
                           if (!this.hasCaveBelowSurface(region, mx, my - 1, mz)) {
                              BlockPos mossPos = new BlockPos(mx, my, mz);
                              BlockPos belowMoss = mossPos.down();
                              if (region.testBlockState(mossPos, state -> state.isAir())
                                 && region.testBlockState(belowMoss, state -> state.isOf(Blocks.GRASS_BLOCK))) {
                                 region.setBlockState(mossPos, mossCarpet, 2);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (isGiantForest && random.nextDouble() < 0.15) {
         int patchX = chunkX * 16 + random.nextInt(16);
         int patchZ = chunkZ * 16 + random.nextInt(16);
         int patchY = 64 + (int)Math.round(this.getCachedHeight(patchX, patchZ, chunkX, chunkZ));
         if (!this.isInsideOrOnVillageWalls(patchX, patchZ)
            && !this.isOnPathFast(patchX, patchZ)
            && !this.isNearCaveEntranceCached(patchX, patchZ, chunkX, chunkZ)
            && !this.hasCaveBelowSurface(region, patchX, patchY, patchZ)) {
            BlockState[] flowers = new BlockState[]{
               Blocks.DANDELION.getDefaultState(), Blocks.POPPY.getDefaultState(), Blocks.OXEYE_DAISY.getDefaultState(), Blocks.CORNFLOWER.getDefaultState()
            };
            BlockState flower = flowers[random.nextInt(flowers.length)];

            for (int f = 0; f < 3 + random.nextInt(4); f++) {
               int fx = patchX + random.nextInt(5) - 2;
               int fz = patchZ + random.nextInt(5) - 2;
               if (this.isInChunk(fx, fz, chunkX, chunkZ) && !this.isNearCaveEntranceCached(fx, fz, chunkX, chunkZ)) {
                  int fy = 64 + (int)Math.round(this.getCachedHeight(fx, fz, chunkX, chunkZ)) + 1;
                  if (!this.hasCaveBelowSurface(region, fx, fy - 1, fz)) {
                     BlockPos flowerPos = new BlockPos(fx, fy, fz);
                     BlockPos belowFlower = flowerPos.down();
                     if (region.testBlockState(flowerPos, state -> state.isAir())
                        && region.testBlockState(
                           belowFlower, state -> state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.COARSE_DIRT)
                        )) {
                        region.setBlockState(flowerPos, flower, 2);
                     }
                  }
               }
            }
         }
      }

      if (!isGiantForest) {
         double meadowNoise = fractalNoise(chunkCenterX * 0.0012 + 600.0, chunkCenterZ * 0.0012 + 600.0, 2);
         boolean inMeadow = meadowNoise > 0.58;
         int flowerPatches = inMeadow ? 2 + random.nextInt(4) : (random.nextDouble() < 0.12 ? 1 : 0);
         BlockState[] titanFlowers = new BlockState[]{
            Blocks.DANDELION.getDefaultState(),
            Blocks.POPPY.getDefaultState(),
            Blocks.OXEYE_DAISY.getDefaultState(),
            Blocks.CORNFLOWER.getDefaultState(),
            Blocks.AZURE_BLUET.getDefaultState(),
            Blocks.ALLIUM.getDefaultState()
         };

         for (int p = 0; p < flowerPatches; p++) {
            int patchX = chunkX * 16 + random.nextInt(16);
            int patchZ = chunkZ * 16 + random.nextInt(16);
            int patchY = 64 + (int)Math.round(this.getCachedHeight(patchX, patchZ, chunkX, chunkZ));
            if ((!chunkNearVillage || !this.isInsideOrOnVillageWalls(patchX, patchZ))
               && (!chunkNearPath || !this.isOnPathFast(patchX, patchZ))
               && !this.isNearCaveEntranceCached(patchX, patchZ, chunkX, chunkZ)
               && !this.hasCaveBelowSurface(region, patchX, patchY, patchZ)) {
               BlockState flower = titanFlowers[random.nextInt(titanFlowers.length)];
               int patchSize = inMeadow ? 5 + random.nextInt(8) : 3 + random.nextInt(4);
               int patchRadius = inMeadow ? 4 : 2;

               for (int fx = 0; fx < patchSize; fx++) {
                  int fxx = patchX + random.nextInt(patchRadius * 2 + 1) - patchRadius;
                  int fz = patchZ + random.nextInt(patchRadius * 2 + 1) - patchRadius;
                  if (this.isInChunk(fxx, fz, chunkX, chunkZ) && !this.isNearCaveEntranceCached(fxx, fz, chunkX, chunkZ)) {
                     int fy = 64 + (int)Math.round(this.getCachedHeight(fxx, fz, chunkX, chunkZ)) + 1;
                     if (!this.hasCaveBelowSurface(region, fxx, fy - 1, fz)) {
                        BlockPos flowerPos = new BlockPos(fxx, fy, fz);
                        BlockPos belowFlower = flowerPos.down();
                        if (region.testBlockState(flowerPos, state -> state.isAir())
                           && region.testBlockState(belowFlower, state -> state.isOf(Blocks.GRASS_BLOCK))) {
                           if (inMeadow && random.nextDouble() < 0.15) {
                              BlockPos above = flowerPos.up();
                              if (region.testBlockState(above, state -> state.isAir())) {
                                 BlockState tallFlower = random.nextBoolean() ? Blocks.SUNFLOWER.getDefaultState() : Blocks.LILAC.getDefaultState();
                                 TallPlantBlock.placeAt(region, tallFlower, flowerPos, 2);
                              }
                           } else {
                              region.setBlockState(flowerPos, flower, 2);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public int getSeaLevel() {
      return -63;
   }

   @Override
   public int getMinimumY() {
      return -64;
   }

   @Override
   public int getHeight(int x, int z, Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
      worldSeed = noiseConfig.getOrCreateRandomDeriver(new Identifier("paradis_seed")).split(0, 0, 0).nextLong();
      return 64 + (int)Math.round(this.getHeightAt(x, z)) + 1;
   }

   @Override
   public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
      int height = this.getHeight(x, z, Type.WORLD_SURFACE_WG, world, noiseConfig);
      BlockState[] states = new BlockState[world.getHeight()];
      BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
      BlockState dirt = Blocks.DIRT.getDefaultState();
      BlockState stone = Blocks.STONE.getDefaultState();

      for (int y = 0; y < states.length; y++) {
         int worldY = world.getBottomY() + y;
         if (worldY < height - 3) {
            states[y] = stone;
         } else if (worldY < height) {
            states[y] = dirt;
         } else if (worldY == height) {
            states[y] = grass;
         } else {
            states[y] = Blocks.AIR.getDefaultState();
         }
      }

      return new VerticalBlockSample(world.getBottomY(), states);
   }

   @Override
   public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
      text.add("Paradis Dimension - Titan Country");
   }

   static {
      for (int r = 0; r < RING_RADII.length; r++) {
         DISTRICT_HALF_ARC[r] = 300.0 / RING_RADII[r];
      }

      DISTRICT_INNER_EXTENSION = new int[RING_RADII.length];
      int hw = 300;

      for (int r = 0; r < RING_RADII.length; r++) {
         int rr = RING_RADII[r];
         int ext = (int)Math.ceil(rr - Math.sqrt((double)rr * rr - (double)hw * hw)) + 4;
         DISTRICT_INNER_EXTENSION[r] = ext;
      }

      RING_GATE_HALF_ARC = new double[RING_RADII.length];

      for (int r = 0; r < RING_RADII.length; r++) {
         RING_GATE_HALF_ARC[r] = 4.0 / RING_RADII[r];
      }

      RIDGE_ARC_SPACING = DMNK ? 28 : 22;
      RING_RIDGE_COUNT = new int[RING_RADII.length];
      RING_RIDGE_ANGLE_STEP = new double[RING_RADII.length];

      for (int r = 0; r < RING_RADII.length; r++) {
         int count = Math.max(12, (int)Math.round((Math.PI * 2) * RING_RADII[r] / RIDGE_ARC_SPACING));
         RING_RIDGE_COUNT[r] = count;
         RING_RIDGE_ANGLE_STEP[r] = (Math.PI * 2) / count;
      }

      WALL_OUTER_RADIUS = 290 + DISTRICT_WALL_THICKNESS;
      WALL_OUTER_RADIUS_BASE = WALL_OUTER_RADIUS + 1;
      WALL_HEIGHT = RING_HEIGHT;
      WALL_BASE_HEIGHT = RING_BASE_HEIGHT;
      PINE_TREE_VARIANTS = new String[]{"pine_tree_tall", "pine_tree_medium", "pine_tree_small", "pine_tree_small2"};
      DMNK_TREE_TIERS = new String[][]{
         {"mini_tree_1", "mini_tree_2", "mini_tree_3", "mini_tree_4"},
         {"small_tree_1", "small_tree_2", "small_tree_3", "small_tree_4"},
         {"medium_tree_1", "medium_tree_2", "medium_tree_3", "medium_tree_4"},
         {"large_tree_1", "large_tree_2", "large_tree_3"},
         {"massive_tree_1", "massive_tree_2", "massive_tree_3"}
      };
      DMNK_TREE_SET = new HashSet<>();

      for (String[] tier : DMNK_TREE_TIERS) {
         Collections.addAll(DMNK_TREE_SET, tier);
      }

      RANDOM_HOUSE_TYPES = new int[]{2, 3, 4, 7};
      RANDOM_SHIG_HOUSE_TYPES = new int[]{8, 9, 10};
      RANDOM_SHIG_LARGE_TYPES = new int[]{11, 12, 13, 14, 15};
      villageHousePositions = new ConcurrentHashMap<>();
      caveEntrancePositions = new ConcurrentHashMap<>();
      villageLayoutCache = new ConcurrentHashMap<>();
      villageCenterCache = new ConcurrentHashMap<>();
      NO_VILLAGE = new int[0];
      villageChunkIndex = new ConcurrentHashMap<>();
      villagePlacedRectsCache = new ConcurrentHashMap<>();
      DISTRICTS = new int[12][];
      hw = 0;

      for (int r = 0; r < RING_RADII.length; r++) {
         for (int c = 0; c < 4; c++) {
            int dx = CARDINAL_DIRS[c][0];
            int dz = CARDINAL_DIRS[c][1];
            int ringRadius = RING_RADII[r];
            int centerDist = ringRadius + 300;
            int centerX = 0 + dx * centerDist;
            int centerZ = 0 + dz * centerDist;
            int innerX = 0 + dx * ringRadius;
            int innerZ = 0 + dz * ringRadius;
            DISTRICTS[hw++] = new int[]{r, c, centerX, centerZ, dx, dz, innerX, innerZ};
         }
      }

      RING_OUTER_SQ = new long[RING_RADII.length];
      RING_INNER_BASE_SQ = new long[RING_RADII.length];
      RING_INNER_UPPER_SQ = new long[RING_RADII.length];

      for (int r = 0; r < RING_RADII.length; r++) {
         int outer = RING_RADII[r];
         RING_OUTER_SQ[r] = (long)outer * outer;
         int innerBase = outer - RING_THICKNESS_BASE;
         int innerUpper = outer - RING_THICKNESS_UPPER;
         RING_INNER_BASE_SQ[r] = (long)innerBase * innerBase;
         RING_INNER_UPPER_SQ[r] = (long)innerUpper * innerUpper;
      }

      WALL_OUTER_RADIUS_SQ = (long)WALL_OUTER_RADIUS * WALL_OUTER_RADIUS;
      WALL_OUTER_RADIUS_BASE_SQ = (long)WALL_OUTER_RADIUS_BASE * WALL_OUTER_RADIUS_BASE;
      RIDGE_ANGLES = new double[]{22.5, 45.0, 67.5, 112.5, 135.0, 157.5, 202.5, 225.0, 247.5, 292.5, 315.0, 337.5};
      RIDGE_COS = new double[12];
      RIDGE_SIN = new double[12];
      RIDGE_OUTER_SQ = (long)(WALL_OUTER_RADIUS_BASE + 3) * (WALL_OUTER_RADIUS_BASE + 3);

      for (int i = 0; i < 12; i++) {
         double rad = Math.toRadians(RIDGE_ANGLES[i]);
         RIDGE_COS[i] = Math.cos(rad);
         RIDGE_SIN[i] = Math.sin(rad);
      }

      double sinTol = Math.sin(Math.toRadians(1.5));
      RIDGE_SIN_SQ_TOLERANCE = sinTol * sinTol;
      CHUNK_CACHE = ThreadLocal.withInitial(ParadisChunkGenerator.ChunkCacheState::new);
      templateCache = new HashMap<>();
      CITY_FOREST_EXCLUSION_RADIUS_SQ = (long)(RING_RADII[RING_RADII.length - 1] + 600 + 500) * (RING_RADII[RING_RADII.length - 1] + 600 + 500);
      pathSegmentCache = new ConcurrentHashMap<>();
      CARDINAL_TO_GATE_INDEX = new int[]{3, 0, 2, 1};
   }

   private static class ChunkCacheState {
      double[] heightCache;
      int heightChunkX = Integer.MIN_VALUE;
      int heightChunkZ = Integer.MIN_VALUE;
      boolean[] pathMask;
      int pathMaskChunkX = Integer.MIN_VALUE;
      int pathMaskChunkZ = Integer.MIN_VALUE;
      boolean[] caveMask;
      int caveMaskChunkX = Integer.MIN_VALUE;
      int caveMaskChunkZ = Integer.MIN_VALUE;
      final List<int[]> houseBounds = new ArrayList<>();
   }
}
