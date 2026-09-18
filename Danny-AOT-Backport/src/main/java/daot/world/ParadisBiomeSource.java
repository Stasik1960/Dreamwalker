package daot.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler;

public class ParadisBiomeSource extends BiomeSource {
   public static final Codec<ParadisBiomeSource> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Biome.REGISTRY_CODEC.fieldOf("titan_country").forGetter(source -> source.titanCountry),
            Biome.REGISTRY_CODEC.fieldOf("giant_forest").forGetter(source -> source.giantForest),
            Biome.REGISTRY_CODEC.fieldOf("titan_highlands").forGetter(source -> source.titanHighlands),
            Biome.REGISTRY_CODEC.optionalFieldOf("titan_hills").forGetter(source -> source.titanHillsOpt),
            Biome.REGISTRY_CODEC.optionalFieldOf("titan_mountains").forGetter(source -> source.titanMountainsOpt)
         )
         .apply(instance, ParadisBiomeSource::new)
   );
   private final RegistryEntry<Biome> titanCountry;
   private final RegistryEntry<Biome> giantForest;
   private final RegistryEntry<Biome> titanHighlands;
   private final RegistryEntry<Biome> titanHills;
   private final RegistryEntry<Biome> titanMountains;
   private final Optional<RegistryEntry<Biome>> titanHillsOpt;
   private final Optional<RegistryEntry<Biome>> titanMountainsOpt;

   public ParadisBiomeSource(
      RegistryEntry<Biome> titanCountry,
      RegistryEntry<Biome> giantForest,
      RegistryEntry<Biome> titanHighlands,
      Optional<RegistryEntry<Biome>> titanHills,
      Optional<RegistryEntry<Biome>> titanMountains
   ) {
      this.titanCountry = titanCountry;
      this.giantForest = giantForest;
      this.titanHighlands = titanHighlands;
      this.titanHills = titanHills.orElse(titanHighlands);
      this.titanMountains = titanMountains.orElse(this.titanHills);
      this.titanHillsOpt = titanHills;
      this.titanMountainsOpt = titanMountains;
   }

   @Override
   protected Codec<? extends BiomeSource> getCodec() {
      return CODEC;
   }

   @Override
   protected Stream<RegistryEntry<Biome>> biomeStream() {
      return Stream.of(this.titanCountry, this.giantForest, this.titanHighlands, this.titanHills, this.titanMountains);
   }

   @Override
   public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseSampler noise) {
      int blockX = x * 4;
      int blockZ = z * 4;
      if (ParadisChunkGenerator.isMountainsBiomeGlobal(blockX, blockZ)) {
         return this.titanMountains;
      } else if (ParadisChunkGenerator.isHillsBiomeGlobal(blockX, blockZ)) {
         return this.titanHills;
      } else if (this.isGiantForestBiome(blockX, blockZ)) {
         return this.giantForest;
      } else {
         return this.isHighlandBiome(blockX, blockZ) ? this.titanHighlands : this.titanCountry;
      }
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

   private boolean isGiantForestBiome(int worldX, int worldZ) {
      return ParadisChunkGenerator.isGiantForestBiomeGlobal(worldX, worldZ);
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
}
