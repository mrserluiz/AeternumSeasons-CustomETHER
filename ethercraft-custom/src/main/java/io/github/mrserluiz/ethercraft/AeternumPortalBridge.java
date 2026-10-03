package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginEnableEvent;

/** Takes over only the audited Aeternum portal listeners; keeps world generators intact. */
public final class AeternumPortalBridge implements Listener {
    private static final Set<String> CLASSES = Set.of(
        "Kinkin.aeternum.portal.FrostOverworldPortals", "Kinkin.aeternum.portal.HeatOverworldPortals",
        "Kinkin.aeternum.portal.HeatNetherPortals", "Kinkin.aeternum.portal.VanillaPortalIsolation");
    private final AeternumCustomPortalPlugin plugin;
    public AeternumPortalBridge(AeternumCustomPortalPlugin plugin) { this.plugin = plugin; }
    public void reconcile() {
        if (!plugin.getConfig().getBoolean("aeternum.take-over-portals", true)) return;
        var aeternum = plugin.getServer().getPluginManager().getPlugin("AeternumSeasons");
        if (aeternum == null || !aeternum.isEnabled()) return;
        Set<Listener> listeners = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var handler : HandlerList.getRegisteredListeners(aeternum))
            if (CLASSES.contains(handler.getListener().getClass().getName())) listeners.add(handler.getListener());
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        if (!listeners.isEmpty()) plugin.getLogger().info("Controle de portais assumido: " + listeners.size() + " listeners do Aeternum. Mundos e demais sistemas preservados.");
    }
    @EventHandler public void enable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("AeternumSeasons")) plugin.getServer().getScheduler().runTask(plugin, this::reconcile);
    }
}
