package io.github.mrserluiz.ethercraft;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ProtectionService {
    private final AeternumCustomPortalPlugin plugin;
    public ProtectionService(AeternumCustomPortalPlugin plugin) { this.plugin = plugin; }
    public String denial(Player player, PortalFrame frame, boolean removing) {
        List<Block> points = new ArrayList<>();
        for (var c : frame.interior()) points.add(frame.block(c.u(), c.v()));
        if (!removing) for (var c : frame.border()) points.add(frame.block(c.u(), c.v()));
        if (frame.horizontal()) {
            for (var c : frame.border()) points.add(frame.block(c.u(), c.v()).getRelative(0, 1, 0));
            return denial(player, points, removing, frame.frameMaterial(), Material.WATER);
        }
        return denial(player, points, removing);
    }
    public String denial(Player player, List<Block> points, boolean removing) {
        return denial(player, points, removing, Material.NETHER_PORTAL);
    }
    public String denial(Player player, List<Block> points, boolean removing, Material frameMaterial) {
        return denial(player, points, removing, frameMaterial, Material.NETHER_PORTAL);
    }
    public String denial(Player player, List<Block> points, boolean removing, Material frameMaterial, Material interiorMaterial) {
        List<ProtectionGate.Provider<Block>> providers = new ArrayList<>();
        var worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard != null && worldGuard.isEnabled()) {
            try {
                var check = new WorldGuardBuildCheck(player);
                providers.add(new ProtectionGate.Provider<>("WorldGuard", b -> check.denial(b, removing)));
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Falha na integração WorldGuard.", error);
                return "WorldGuard: API indisponível/incompatível; operação bloqueada.";
            }
        }
        var griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention != null && griefPrevention.isEnabled()) {
            try {
                // GP16/17 legacy public APIs; no GP classes bundled or hard dependency.
                var method = removing
                    ? griefPrevention.getClass().getMethod("allowBreak", Player.class, Block.class, Location.class)
                    : griefPrevention.getClass().getMethod("allowBuild", Player.class, Location.class, Material.class);
                if (method.getReturnType() != String.class) return "GriefPrevention: assinatura de API incompatível.";
                providers.add(new ProtectionGate.Provider<>("GriefPrevention", b -> {
                    if (removing) return (String) method.invoke(griefPrevention, player, b, b.getLocation());
                    String denied = (String) method.invoke(griefPrevention, player, b.getLocation(), frameMaterial);
                    return denied != null || frameMaterial == interiorMaterial ? denied
                        : (String) method.invoke(griefPrevention, player, b.getLocation(), interiorMaterial);
                }));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Falha na integração GriefPrevention.", error);
                return "GriefPrevention: API indisponível/incompatível; operação bloqueada.";
            }
        }
        String reason = ProtectionGate.firstDenial(points, providers);
        if (reason != null) return reason;
        // Legacy opt-in applies only to other, still unintegrated providers.
        if (!plugin.getConfig().getBoolean("allow-unintegrated-protections"))
            for (String name : plugin.getConfig().getStringList("protection-plugins")) {
                if (name.equalsIgnoreCase("WorldGuard") || name.equalsIgnoreCase("GriefPrevention")) continue;
                if (Bukkit.getPluginManager().isPluginEnabled(name))
                    return "integração de proteção pendente (" + name + ").";
            }
        return null;
    }
}
