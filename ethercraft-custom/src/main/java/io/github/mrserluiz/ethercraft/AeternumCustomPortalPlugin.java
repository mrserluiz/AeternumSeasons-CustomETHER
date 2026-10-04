package io.github.mrserluiz.ethercraft;

import java.nio.file.Files;
import java.util.Objects;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AeternumCustomPortalPlugin extends JavaPlugin implements CommandExecutor {
    private PortalService portals;
    private PortalDefinitions definitions;
    private Messages messages;
    Messages messages() { return messages; }
    @Override public void onEnable() {
        try {
            if (getServer().getPluginManager().isPluginEnabled("EtherCraftCustom"))
                throw new IllegalStateException("Remova o JAR EtherCraftCustom antigo e reinicie para evitar dois controladores de portal.");
            migrateFiles();
            saveDefaultConfig();
            // Add new settings to existing installations without overwriting their values.
            boolean settingsAdded = false;
            var additions = java.util.Map.<String, Object>of("debug.enabled", false, "debug.log-to-console", true,
                "messages.player-feedback", "ACTION_BAR", "language.default", "pt_BR", "language.use-client-locale", false, "portal-placement.max-creation-radius", 128);
            for (var entry : additions.entrySet()) if (!getConfig().contains(entry.getKey(), true)) {
                getConfig().set(entry.getKey(), entry.getValue()); settingsAdded = true;
            }
            if (settingsAdded) saveConfig();
            messages = new Messages(this);
            definitions = new PortalDefinitions(this);
            portals = new PortalService(this, definitions);
            portals.reconcileAll();
            Objects.requireNonNull(getCommand("aeternumportal")).setExecutor(this);
            getServer().getPluginManager().registerEvents(portals, this);
            var bridge = new AeternumPortalBridge(this);
            bridge.reconcile();
            getServer().getPluginManager().registerEvents(bridge, this);
            getServer().getScheduler().runTaskTimer(this, bridge::reconcile, 20L, 20L);
            getLogger().info("AeternumCustomPortal 0.5.2: mundos existentes, portais controlados por portal-types.yaml.");
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
        if (args.length == 0) { messages.send(sender, "help", getDescription().getVersion()); return true; }
        try {
            if (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("languages")) {
                if (!sender.hasPermission("aeternumcustomportal.language")) { messages.send(sender, "permission"); return true; }
                if (args[0].equalsIgnoreCase("languages")) {
                    messages.send(sender, "language-list");
                    LanguageCatalog.SUPPORTED.forEach((id, name) -> sender.sendMessage(id + " — " + name));
                } else if (args.length == 1) messages.send(sender, "language-current", messages.locale(sender));
                else if (args.length != 2) messages.send(sender, "language-invalid");
                else {
                    Player player = player(sender);
                    String locale = LanguageCatalog.normalize(args[1]);
                    boolean automatic = args[1].equalsIgnoreCase("auto");
                    if (locale == null && !automatic) messages.send(sender, "language-invalid");
                    else {
                        messages.setLanguage(player, automatic ? null : locale);
                        messages.send(sender, automatic ? "language-auto" : "language-set", messages.locale(sender));
                    }
                }
                return true;
            }
            if (!sender.hasPermission("aeternumcustomportal.admin")) { messages.send(sender, "permission"); return true; }
            if (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable")) {
                if (args.length != 2) throw new IllegalArgumentException(messages.text(sender, "usage-type"));
                var file = getDataFolder().toPath().resolve("portal-types.yaml");
                byte[] previous = Files.readAllBytes(file);
                try {
                    PortalTypeYaml.setEnabled(file.toFile(), args[1], args[0].equalsIgnoreCase("enable"), sender instanceof Player p ? p.getWorld().getName() : null);
                    portals.reloadTypes();
                } catch (Exception error) { PortalTypeYaml.write(file, previous); throw error; }
                messages.send(sender, "config-saved", getDescription().getVersion());
                portals.describe(sender); return true;
            }
            if (args.length != 1) { messages.send(sender, "help", getDescription().getVersion()); return true; }
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "status" -> messages.send(sender, "status", getDescription().getVersion(), definitions.size(), portals.size());
                case "worlds" -> getServer().getWorlds().forEach(w -> sender.sendMessage(w.getName() + " | " + w.getKey() + " | " + w.getUID() + " | " + w.getWorldPath()));
                case "types" -> portals.describe(sender);
                case "reload" -> {
                    var configCheck = new org.bukkit.configuration.file.YamlConfiguration();
                    configCheck.load(getDataFolder().toPath().resolve("config.yml").toFile());
                    var languageCheck = messages.prepareReload();
                    portals.reloadTypes(); reloadConfig(); messages.applyReload(languageCheck); new AeternumPortalBridge(this).reconcile();
                    messages.send(sender, "reloaded", getDescription().getVersion());
                    portals.describe(sender);
                }
                case "select" -> { portals.select(player(sender)); messages.send(sender, "selected"); }
                case "link" -> { portals.link(player(sender)); messages.send(sender, "linked"); }
                case "remove" -> { portals.remove(player(sender)); messages.send(sender, "removed"); }
                case "unlink" -> { portals.unlink(player(sender)); messages.send(sender, "unlinked"); }
                default -> { messages.send(sender, "help", getDescription().getVersion()); }
            }
        } catch (Exception error) {
            messages.send(sender, "operation-failed"); messages.debug(sender, "debug-detail", error.getMessage());
            getLogger().warning("Command /acp failed: " + error.getMessage());
        }
        return true;
    }
    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        var commands = new java.util.ArrayList<String>();
        if (sender.hasPermission("aeternumcustomportal.language")) commands.addAll(java.util.List.of("language", "languages"));
        if (sender.hasPermission("aeternumcustomportal.admin")) commands.addAll(java.util.List.of("status", "worlds", "types", "reload", "enable", "disable", "select", "link", "unlink", "remove"));
        java.util.List<String> choices = args.length == 1 ? commands
            : args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang")) && sender.hasPermission("aeternumcustomportal.language")
                ? java.util.stream.Stream.concat(LanguageCatalog.SUPPORTED.keySet().stream(), java.util.stream.Stream.of("auto")).toList()
            : args.length == 2 && sender.hasPermission("aeternumcustomportal.admin") && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))
                ? definitions.all().stream().map(type -> type.spec().id()).toList() : java.util.List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)).toList();
    }
    private Player player(CommandSender sender) {
        if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.text(sender, "players-only"));
        return p;
    }
}
