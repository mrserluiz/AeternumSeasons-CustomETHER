package io.github.mrserluiz.ethercraft;

import java.util.Objects;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EtherCraftPlugin extends JavaPlugin implements CommandExecutor {
    private DimensionService dimensions;
    private PortalService portals;
    private PortalDefinitions definitions;
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            dimensions = new DimensionService(this);
            definitions = new PortalDefinitions(this);
            portals = new PortalService(this, dimensions, definitions);
            Objects.requireNonNull(getCommand("ethercraft")).setExecutor(this);
            getServer().getPluginManager().registerEvents(portals, this);
            getLogger().info("EtherCraft Custom 0.2.1: nenhuma criação automática de mundo.");
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Inicialização bloqueada; dados preservados.", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ethercraft.admin")) return true;
        if (args.length != 1) return false;
        try {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "status" -> sender.sendMessage("Frost: " + dimensions.name() + "; tipos: " + definitions.size() + "; portais: " + portals.size());
                case "types" -> portals.describe(sender);
                case "reload" -> { portals.reloadTypes(); sender.sendMessage("Tipos de portal recarregados: " + definitions.size()); }
                case "create" -> sender.sendMessage("Frost criado/carregado: " + dimensions.createOrLoad().getName());
                case "visit" -> {
                    Player p = player(sender);
                    var world = getServer().getWorld(dimensions.name());
                    if (!dimensions.owns(world)) throw new IllegalArgumentException("Use /ethercraft create primeiro.");
                    // Admin exploration only; does not build or alter a spawn platform.
                    if (!p.teleport(world.getSpawnLocation())) throw new IllegalArgumentException("Teleporte cancelado.");
                }
                case "select" -> { portals.select(player(sender)); sender.sendMessage("Origem selecionada."); }
                case "link" -> { portals.link(player(sender)); sender.sendMessage("Par de portais vinculado nos dois sentidos."); }
                case "remove" -> { portals.remove(player(sender)); sender.sendMessage("Portal removido; frame liberado para edição."); }
                case "unlink" -> { portals.unlink(player(sender)); sender.sendMessage("Vínculo removido nos dois sentidos."); }
                default -> { return false; }
            }
        } catch (Exception error) { sender.sendMessage("Operação bloqueada: " + error.getMessage()); }
        return true;
    }
    private Player player(CommandSender sender) {
        if (!(sender instanceof Player p)) throw new IllegalArgumentException("Execute este comando dentro do jogo.");
        return p;
    }
}
