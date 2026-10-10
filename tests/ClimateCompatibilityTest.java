import Kinkin.aeternum.calendar.WorldClimateProfile;
import Kinkin.aeternum.world.BiomePaletteResolver;
import java.util.Map;

public class ClimateCompatibilityTest {
    public static void main(String[] args) throws Exception {
        var winter = new WorldClimateProfile(" winter ", "SNOWY_PLAINS");
        require(winter.winter()); require(winter.climateBiome().equals("minecraft:snowy_plains"));
        require(!new WorldClimateProfile("summer", "DESERT").winter());
        rejects(() -> new WorldClimateProfile("typo", "SNOWY_PLAINS"));
        rejects(() -> new WorldClimateProfile("winter", "terra:hydraxia/custom"));
        var registry = Map.of("minecraft:plains", "vanilla", "terra:hydraxia/hydraxia/snowswept_meadows", "custom");
        var palette = BiomePaletteResolver.resolve(new String[]{"PLAINS", "terra:hydraxia/hydraxia/snowswept_meadows"}, registry::get);
        require(palette.equals(java.util.List.of("vanilla", "custom")));
        try {
            BiomePaletteResolver.resolve(new String[]{"PLAINS", "terra:missing"}, registry::get);
            throw new AssertionError("Missing biome must not resolve to PLAINS");
        } catch(BiomePaletteResolver.UnresolvedBiome expected) { require(expected.key().equals("terra:missing")); }
        require(BiomePaletteResolver.key("FROZEN_OCEAN").equals("minecraft:frozen_ocean"));
        System.out.println("CLIMATE_COMPATIBILITY_TESTS_OK");
    }
    static void require(boolean value) { if(!value) throw new AssertionError(); }
    static void rejects(Runnable test) { try { test.run(); } catch(IllegalArgumentException expected) { return; } throw new AssertionError(); }
}
