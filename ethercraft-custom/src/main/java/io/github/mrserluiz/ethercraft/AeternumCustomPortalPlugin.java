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
            portals.reconcileAll();
            Objects.requireNonNull(getCommand("aeternumportal")).setExecutor(this);
            getServer().getPluginManager().registerEvents(portals, this);
            var bridge = new AeternumPortalBridge(this);
            bridge.reconcile();
            getServer().getPluginManager().registerEvents(bridge, this);
            getServer().getScheduler().runTaskTimer(this, bridge::reconcile, 20L, 20L);
            getLogger().info("AeternumCustomPortal 0.3.3: mundos existentes, portais controlados por portal-types.yaml.");
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
        if (args.length == 0) { sender.sendMessage("AeternumCustomPortal " + getDescription().getVersion() + " — /acp reload | /acp types | /acp enable heat | /acp worlds"); return true; }
        try {
            if (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")) {
                if (args.length != 2) throw new IllegalArgumentException("Use /acp enable <tipo> ou /acp disable <tipo>.");
                var file = getDataFolder().toPath().resolve("portal-types.yaml");
                byte[] previous = Files.readAllBytes(file);
                try {
                    PortalTypeYaml.setEnabled(file.toFile(), args[1], args[0].equalsIgnoreCase("enable"), sender instanceof Player p ? p.getWorld().getName() : null);
                    portals.reloadTypes();
                } catch (Exception error) { PortalTypeYaml.write(file, previous); throw error; }
                sender.sendMessage("Configuração salva e aplicada — AeternumCustomPortal " + getDescription().getVersion());
                portals.describe(sender); return true;
            }
            if (args.length != 1) return false;
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "status" -> sender.sendMessage("AeternumCustomPortal 0.3.3; tipos: " + definitions.size() + "; portais: " + portals.size());
                case "worlds" -> getServer().getWorlds().forEach(w -> sender.sendMessage(w.getName() + " | " + w.getKey() + " | " + w.getUID() + " | " + w.getWorldPath()));
                case "types" -> portals.describe(sender);
                case "reload" -> {
                    var configCheck = new org.bukkit.configuration.file.YamlConfiguration();
                    configCheck.load(getDataFolder().toPath().resolve("config.yml").toFile());
                    portals.reloadTypes(); reloadConfig(); new AeternumPortalBridge(this).reconcile();
                    sender.sendMessage("AeternumCustomPortal " + getDescription().getVersion() + " — YAML recarregado: " + getDataFolder().toPath().resolve("portal-types.yaml"));
                    portals.describe(sender);
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
    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aeternumcustomportal.admin")) return java.util.List.of();
        java.util.List<String> choices = args.length == 1
            ? java.util.List.of("status", "worlds", "types", "reload", "enable", "disable", "select", "link", "unlink", "remove")
            : args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))
                ? definitions.all().stream().map(type -> type.spec().id()).toList() : java.util.List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }
    private Player player(CommandSender sender) {
        if (!(sender instanceof Player p)) throw new IllegalArgumentException("Execute este comando dentro do jogo.");
        return p;
    }
}
