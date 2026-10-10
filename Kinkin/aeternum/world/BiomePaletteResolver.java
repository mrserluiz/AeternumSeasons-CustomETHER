package Kinkin.aeternum.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Resolve the entire palette before applying any cell; never substitute PLAINS for a missing ID. */
public final class BiomePaletteResolver {
    public static final class UnresolvedBiome extends IOException {
        private final String key;
        public UnresolvedBiome(String key) { super("Biome unavailable; backup preserved: " + key); this.key = key; }
        public String key() { return key; }
    }
    public static String key(String stored) {
        String value = stored.trim().toLowerCase(Locale.ROOT);
        if(!value.contains(":")) value = "minecraft:" + value;
        return value;
    }
    public static <T> List<T> resolve(String[] palette, Function<String,T> registry) throws UnresolvedBiome {
        List<T> result = new ArrayList<>(palette.length);
        for(String stored : palette) {
            String key = key(stored); T biome = registry.apply(key);
            if(biome == null) throw new UnresolvedBiome(key);
            result.add(biome);
        }
        return List.copyOf(result);
    }
    private BiomePaletteResolver() {}
}
