package io.github.mrserluiz.ethercraft;

import java.util.Set;

/** Limited terrain fallback; containers, obsidian, wood and other builds are excluded. */
public final class PortalTerrain {
    private PortalTerrain() {}
    private static final Set<String> REPLACEABLE = Set.of("AIR", "CAVE_AIR", "VOID_AIR", "SNOW", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
        "STONE", "DEEPSLATE", "NETHERRACK", "DIRT", "GRASS_BLOCK", "COARSE_DIRT", "ROOTED_DIRT", "SAND", "RED_SAND", "GRAVEL", "SNOW_BLOCK",
        "ICE", "PACKED_ICE", "BLUE_ICE", "BASALT", "BLACKSTONE", "END_STONE", "TUFF", "CALCITE", "ANDESITE", "DIORITE", "GRANITE");
    public static boolean canReplace(String material) { return REPLACEABLE.contains(material); }
}
