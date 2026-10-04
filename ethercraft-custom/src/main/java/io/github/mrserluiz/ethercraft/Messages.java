package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;

final class Messages {
    private final AeternumCustomPortalPlugin plugin;
    private LanguageCatalog catalog;
    private final LanguagePreferences preferences;
    private final Map<UUID, Map<String, Long>> timestamps = new HashMap<>();
    Messages(AeternumCustomPortalPlugin plugin) throws Exception {
        this.plugin = plugin;
        catalog = prepareReload();
        preferences = new LanguagePreferences(plugin.getDataFolder().toPath().resolve("player-languages.yml"));
    }
    LanguageCatalog prepareReload() throws Exception { return LanguageCatalog.load(plugin.getDataFolder().toPath().resolve("languages")); }
    void applyReload(LanguageCatalog loaded) { catalog = loaded; timestamps.clear(); }
    String locale(CommandSender sender) {
        return LanguageCatalog.resolve(sender instanceof Player p ? preferences.get(p.getUniqueId()) : null,
            sender instanceof Player p ? p.getLocale() : null, plugin.getConfig().getString("language.default", "pt_BR"),
            sender instanceof Player && plugin.getConfig().getBoolean("language.use-client-locale", false));
    }
    String text(CommandSender sender, String key, Object... args) { return catalog.text(locale(sender), key, args); }
    void send(CommandSender sender, String key, Object... args) { sender.sendMessage("[ACP] " + text(sender, key, args)); }
    void setLanguage(Player player, String language) throws java.io.IOException { preferences.set(player.getUniqueId(), language); }
    boolean ready(Player player, String key) {
        var times = timestamps.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long now = System.currentTimeMillis();
        if (times.getOrDefault(key, 0L) > now) return false;
        times.put(key, now + 3000L); return true;
    }
    void feedback(Player player, String key, Object... args) {
        String route = MessagePolicy.feedback(plugin.getConfig().getString("messages.player-feedback", "ACTION_BAR"));
        if (route.equals("OFF") || !ready(player, "feedback:" + key)) return;
        if (route.equals("CHAT")) send(player, key, args);
        else player.sendActionBar(Component.text(text(player, key, args)));
    }
    void debug(CommandSender sender, String key, Object... args) {
        if (!plugin.getConfig().getBoolean("debug.enabled", false)) return;
        if (MessagePolicy.diagnostics(true, sender.isOp()) && (!(sender instanceof Player p) || ready(p, "debug:" + key)))
            send(sender, key, args);
        if (plugin.getConfig().getBoolean("debug.log-to-console", true) && sender instanceof Player p && ready(p, "console:" + key))
            plugin.getLogger().info("[DEBUG] " + catalog.text("en_US", key, args));
    }
    void failure(Player player, String key, Exception error) {
        feedback(player, key); debug(player, "debug-detail", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }
    void forget(UUID id) { timestamps.remove(id); }
}
