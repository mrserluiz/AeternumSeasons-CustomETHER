package io.github.mrserluiz.ethercraft;

import java.nio.file.Files;
import java.util.Objects;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AeternumCustomPortalPlugin extends JavaPlugin implements CommandExecutor {
    private PortalService portals;
    private PortalDefinitions definitions;
    @Override public void onEnable() {
        try {
            if (getServer().getPluginManager().isPluginEnabled("EtherCraftCustom"))
                throw new IllegalStateException("Remova o JAR EtherCraftCustom antigo e reinicie para evitar dois controladores de portal.");
            migrateFiles();
            saveDefaultConfig();
            definitions = new PortalDefinitions(this);
            portals = new PortalService(this, definitions);
            Objects.requireNonNull(getCommand("aeternumportal")).setExecutor(this);
            getServer().getPluginManager().registerEvents(portals, this);
            var bridge = new AeternumPortalBridge(this);
            bridge.reconcile();
            getServer().getPluginManager().registerEvents(bridge, this);
            getServer().getScheduler().runTaskTimer(this, bridge::reconcile, 20L, 20L);
            getLogger().info("AeternumCustomPortal 0.3.1: mundos existentes, portais controlados por portal-types.yaml.");
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Inicialização bloqueada; dados preservados.", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private void migrateFiles() throws java.io.IOException {
        var legacy = getDataFolder().toPath().resolveSibling("EtherCraftCustom");
        var target = getDataFolder().toPath();
        // Copy once, never move/delete old data or overwrite the new plugin's configuration.
        if (Files.exists(target) || !Files.isDirectory(legacy)) return;
        Files.createDirectories(target);
        for (String name : new String[]{"config.yml", "portal-types.yaml", "portals.yml"}) {
            var file = legacy.resolve(name);
            if (Files.isRegularFile(file)) Files.copy(file, target.resolve(name));
        }
        getLogger().warning("Arquivos EtherCraftCustom copiados. Destinos personalizados preservados; ajuste frost/heat para aeternum_frost/aeternum_heat no YAML.");
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aeternumcustomportal.admin")) return true;
        if (args.length != 1) return false;
        try {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "status" -> sender.sendMessage("AeternumCustomPortal 0.3.1; tipos: " + definitions.size() + "; portais: " + portals.size());
                case "worlds" -> getServer().getWorlds().forEach(w -> sender.sendMessage(w.getName() + " | " + w.getKey() + " | " + w.getUID() + " | " + w.getWorldPath()));
                case "types" -> portals.describe(sender);
                case "reload" -> { portals.reloadTypes(); sender.sendMessage("Tipos de portal recarregados: " + definitions.size()); }
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
