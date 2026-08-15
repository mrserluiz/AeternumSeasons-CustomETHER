package Kinkin.aeternum.dimension;

import java.util.Arrays;
import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

public final class HeatBiomeProvider extends BiomeProvider {
   private final long seed;
   private final List<Biome> heatBiomes = Arrays.asList(
      Biome.NETHER_WASTES, Biome.CRIMSON_FOREST, Biome.WARPED_FOREST, Biome.SOUL_SAND_VALLEY, Biome.BASALT_DELTAS
   );

   public HeatBiomeProvider(long seed) {
      this.seed = seed;
   }

   @NotNull
   public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
      long h = x * 341873128712L + z * 132897987541L + this.seed;
      int idx = Math.floorMod(h, this.heatBiomes.size());
      return this.heatBiomes.get(idx);
   }

   @NotNull
   public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
      return this.heatBiomes;
   }
}
