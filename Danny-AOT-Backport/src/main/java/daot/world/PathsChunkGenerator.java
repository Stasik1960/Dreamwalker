package daot.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import daot.DannysAot;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.GenerationStep.Carver;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

public class PathsChunkGenerator extends ChunkGenerator {
   public static final Codec<PathsChunkGenerator> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(Biome.REGISTRY_CODEC.fieldOf("biome").forGetter(gen -> gen.biome)).apply(instance, PathsChunkGenerator::new)
   );
   private final RegistryEntry<Biome> biome;
   private static final int BASE_HEIGHT = 64;
   private static final int MAX_DUNE_HEIGHT = 24;

   public PathsChunkGenerator(RegistryEntry<Biome> biome) {
      super(new FixedBiomeSource(biome));
      this.biome = biome;
   }

   private static double hash(int x, int z) {
      int n = x * 374761393 + z * 668265263;
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
      double wiggle = Math.sin(worldZ * 0.003) * 40.0 + Math.sin(worldZ * 0.008) * 20.0;
      double adjustedX = worldX + wiggle;
      double noise = fractalNoise(adjustedX * 0.003, worldZ * 0.008, 3);
      double height = (noise * 2.0 - 1.0) * 24.0;
      double blobNoise = fractalNoise(worldX * 0.02, worldZ * 0.02, 2);
      double xRadius = 120.0 + blobNoise * 40.0;
      double zRadius = 80.0 + blobNoise * 30.0;
      double normalizedDist = Math.sqrt(worldX * worldX / (xRadius * xRadius) + worldZ * worldZ / (zRadius * zRadius));
      if (normalizedDist < 1.0) {
         double factor = (Math.cos(normalizedDist * Math.PI) + 1.0) / 2.0;
         factor = Math.pow(factor, 0.8);
         double specialHeight = 26.400000000000002;
         height = height * (1.0 - factor) + specialHeight * factor;
      }

      double pathsDuneX = 0.0;
      double pathsDuneZ = 200.0;
      double pathsDuneRadius = 38.0;
      double pathsDuneDepth = -15.0;
      double dxPaths = worldX - pathsDuneX;
      double dzPaths = worldZ - pathsDuneZ;
      double pathsDist = Math.sqrt(dxPaths * dxPaths + dzPaths * dzPaths);
      if (pathsDist < pathsDuneRadius) {
         double edgeWidth = 25.0;
         if (pathsDist > pathsDuneRadius - edgeWidth) {
            double blendFactor = (pathsDuneRadius - pathsDist) / edgeWidth;
            blendFactor = smoothstep(blendFactor);
            height = height * (1.0 - blendFactor) + pathsDuneDepth * blendFactor;
         } else {
            height = pathsDuneDepth;
         }
      }

      return height;
   }

   @Override
   protected Codec<? extends ChunkGenerator> getCodec() {
      return CODEC;
   }

   @Override
   public void carve(
      ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, Carver carverStep
   ) {
   }

   @Override
   public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
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
      int chunkX = chunk.getPos().x;
      int chunkZ = chunk.getPos().z;
      BlockState pathSand = DannysAot.PATH_SAND.getDefaultState();
      BlockState darkPathSand = DannysAot.DARK_PATH_SAND.getDefaultState();
      BlockState pathSlab = DannysAot.PATH_SAND_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
      BlockState darkSlab = DannysAot.DARK_PATH_SAND_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
      BlockState lightBlock = Blocks.LIGHT.getDefaultState();
      double lightCenterX = 0.0;
      double lightCenterZ = 200.0;
      double lightRadius = 35.0;
      int lightHeight = 5;
      int[][] heights = new int[18][18];
      double[][] rawHeights = new double[18][18];

      for (int x = -1; x <= 16; x++) {
         for (int z = -1; z <= 16; z++) {
            int worldX = chunkX * 16 + x;
            int worldZ = chunkZ * 16 + z;
            double h = this.getHeightAt(worldX, worldZ);
            rawHeights[x + 1][z + 1] = h;
            heights[x + 1][z + 1] = 64 + (int)Math.round(h);
         }
      }

      for (int x = 0; x < 16; x++) {
         for (int z = 0; z < 16; z++) {
            int surfaceY = heights[x + 1][z + 1];
            int heightN = heights[x + 1][z];
            int heightS = heights[x + 1][z + 2];
            int heightE = heights[x + 2][z + 1];
            int heightW = heights[x][z + 1];
            int heightNE = heights[x + 2][z];
            int heightNW = heights[x][z];
            int heightSE = heights[x + 2][z + 2];
            int heightSW = heights[x][z + 2];
            boolean needsSlab = surfaceY < heightN
               || surfaceY < heightS
               || surfaceY < heightE
               || surfaceY < heightW
               || surfaceY < heightNE
               || surfaceY < heightNW
               || surfaceY < heightSE
               || surfaceY < heightSW;
            double slopeZ = rawHeights[x + 1][z + 2] - rawHeights[x + 1][z];
            boolean isShadowSide = slopeZ > 0.5;

            for (int y = chunk.getBottomY(); y <= surfaceY; y++) {
               BlockState blockToPlace;
               if (y >= surfaceY - 1) {
                  blockToPlace = isShadowSide ? darkPathSand : pathSand;
               } else {
                  blockToPlace = pathSand;
               }

               chunk.setBlockState(new BlockPos(x, y, z), blockToPlace, false);
            }

            if (needsSlab) {
               BlockState slab = isShadowSide ? darkSlab : pathSlab;
               chunk.setBlockState(new BlockPos(x, surfaceY + 1, z), slab, false);
            }

            int worldX = chunkX * 16 + x;
            int worldZ = chunkZ * 16 + z;
            double dxLight = worldX - lightCenterX;
            double dzLight = worldZ - lightCenterZ;
            double lightDist = Math.sqrt(dxLight * dxLight + dzLight * dzLight);
            if (lightDist <= lightRadius) {
               for (int ly = 0; ly < lightHeight; ly++) {
                  chunk.setBlockState(new BlockPos(x, surfaceY + 1 + ly, z), lightBlock, false);
               }
            }
         }
      }

      return CompletableFuture.completedFuture(chunk);
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
      return 64 + (int)Math.round(this.getHeightAt(x, z)) + 1;
   }

   @Override
   public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
      int height = this.getHeight(x, z, Type.WORLD_SURFACE_WG, world, noiseConfig);
      BlockState[] states = new BlockState[world.getHeight()];
      BlockState pathSand = DannysAot.PATH_SAND.getDefaultState();

      for (int y = 0; y < states.length; y++) {
         int worldY = world.getBottomY() + y;
         if (worldY <= height) {
            states[y] = pathSand;
         } else {
            states[y] = Blocks.AIR.getDefaultState();
         }
      }

      return new VerticalBlockSample(world.getBottomY(), states);
   }

   @Override
   public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
      text.add("Paths Dimension");
   }
}
