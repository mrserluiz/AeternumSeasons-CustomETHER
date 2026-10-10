package Kinkin.aeternum.calendar;

import java.util.Locale;
import java.util.Set;

/** A climate interpretation only: never a replacement for a stored world biome. */
public record WorldClimateProfile(String season, String climateBiome) {
    public WorldClimateProfile {
        season = season.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("SPRING", "SUMMER", "AUTUMN", "WINTER").contains(season))
            throw new IllegalArgumentException("Invalid fixed climate season: " + season);
        climateBiome = climateBiome.trim().toLowerCase(Locale.ROOT);
        if(!climateBiome.contains(":")) climateBiome = "minecraft:" + climateBiome;
        if(!climateBiome.matches("minecraft:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Climate reference must be a vanilla biome: " + climateBiome);
    }
    public boolean winter() { return season.equals("WINTER"); }
}
