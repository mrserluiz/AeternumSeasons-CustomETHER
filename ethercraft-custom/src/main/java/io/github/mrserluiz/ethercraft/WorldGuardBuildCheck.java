package io.github.mrserluiz.ethercraft;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Optional WG7 classes are confined here; never loaded when WorldGuard is absent. */
public final class WorldGuardBuildCheck {
    private final LocalPlayer player;
    private final RegionQuery query;
    private final boolean bypass;
    public WorldGuardBuildCheck(Player bukkitPlayer) {
        player = WorldGuardPlugin.inst().wrapPlayer(bukkitPlayer);
        var platform = WorldGuard.getInstance().getPlatform();
        query = platform.getRegionContainer().createQuery();
        bypass = platform.getSessionManager().hasBypass(player, BukkitAdapter.adapt(bukkitPlayer.getWorld()));
    }
    public String denial(Block block, boolean removing) {
        if (bypass) return null;
        boolean allowed = query.testBuild(BukkitAdapter.adapt(block.getLocation()), player,
            removing ? Flags.BLOCK_BREAK : Flags.BLOCK_PLACE);
        return allowed ? null : "você não tem permissão para " + (removing ? "remover" : "construir")
            + " neste ponto do portal (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ").";
    }
}
