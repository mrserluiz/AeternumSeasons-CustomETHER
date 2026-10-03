package io.github.mrserluiz.ethercraft;

import org.bukkit.*;
import org.bukkit.block.Block;

/** Uses loaded API identities only. No WorldCreator or filesystem world lookup. */
public final class LoadedWorlds {
    private LoadedWorlds() {}
    public static World resolve(String reference) {
        World found = null;
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().equals(reference) && !world.getKey().toString().equals(reference)) continue;
            if (found != null && !found.getUID().equals(world.getUID())) return null;
            found = world;
        }
        return found;
    }
    public static Location safeSpawn(World world, Location facing) {
        Location spawn = world.getSpawnLocation();
        for (int radius = 0; radius <= 4; radius++)
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4})
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int y = spawn.getBlockY() + dy;
                    if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
                    Block feet = world.getBlockAt(spawn.getBlockX() + dx, y, spawn.getBlockZ() + dz);
                    if (!feet.getType().isAir() || !feet.getRelative(0, 1, 0).getType().isAir()
                        || !safeFloor(feet.getRelative(0, -1, 0).getType())) continue;
                    Location exit = feet.getLocation().add(0.5, 0, 0.5);
                    if (!world.getWorldBorder().isInside(exit)) continue;
                    exit.setYaw(facing.getYaw()); exit.setPitch(facing.getPitch()); return exit;
                }
        return null;
    }
    private static boolean safeFloor(Material material) {
        return material.isSolid() && material != Material.MAGMA_BLOCK && material != Material.CACTUS
            && material != Material.CAMPFIRE && material != Material.SOUL_CAMPFIRE
            && material != Material.POWDER_SNOW && material != Material.NETHER_PORTAL;
    }
}
