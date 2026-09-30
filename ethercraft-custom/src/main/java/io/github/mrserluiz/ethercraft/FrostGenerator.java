package io.github.mrserluiz.ethercraft;

import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Vanilla terrain with deterministic cold biome regions; no copied Aeternum code. */
public final class FrostGenerator extends ChunkGenerator {
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            private final List<Biome> biomes = List.of(Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA,
                Biome.ICE_SPIKES, Biome.FROZEN_RIVER, Biome.FROZEN_OCEAN,
                Biome.DEEP_FROZEN_OCEAN, Biome.FROZEN_PEAKS, Biome.JAGGED_PEAKS, Biome.SNOWY_SLOPES);
            @Override public Biome getBiome(WorldInfo world, int x, int y, int z) {
                // Stable 256-block regions, including negative coordinates.
                long h = world.getSeed() ^ (Math.floorDiv(x, 256) * 0x9E3779B97F4A7C15L)
                    ^ (Math.floorDiv(z, 256) * 0xC2B2AE3D27D4EB4FL);
                h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
                h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
                return biomes.get((int) Math.floorMod(h ^ (h >>> 31), biomes.size()));
            }
            @Override public List<Biome> getBiomes(WorldInfo world) { return biomes; }
        };
    }
    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return true; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return true; }
    @Override public boolean shouldGenerateStructures() { return true; }
}
