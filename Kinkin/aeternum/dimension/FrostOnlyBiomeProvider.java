package Kinkin.aeternum.dimension;

import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

public final class FrostOnlyBiomeProvider extends BiomeProvider {
   private static final Biome[] COLD_BIOMES = new Biome[]{
      Biome.SNOWY_PLAINS,
      Biome.SNOWY_TAIGA,
      Biome.ICE_SPIKES,
      Biome.FROZEN_RIVER,
      Biome.FROZEN_OCEAN,
      Biome.DEEP_FROZEN_OCEAN,
      Biome.FROZEN_PEAKS,
      Biome.JAGGED_PEAKS,
      Biome.SNOWY_SLOPES
   };
   private static final List<Biome> COLD_BIOMES_LIST = List.of(COLD_BIOMES);
   private final long seed;

   public FrostOnlyBiomeProvider(long seed) {
      this.seed = seed;
   }

   @NotNull
   public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
      long h = this.seed;
      h ^= x * 341873128712L;
      h ^= z * 132897987541L;
      h ^= h >>> 32;
      int idx = (int)((h & Long.MAX_VALUE) % COLD_BIOMES.length);
      return COLD_BIOMES[idx];
   }

   @NotNull
   public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
      return COLD_BIOMES_LIST;
   }
}
