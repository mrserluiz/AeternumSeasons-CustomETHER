package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.block.Block;

/** Takes over only the audited Aeternum portal listeners; keeps world generators intact. */
public final class AeternumPortalBridge implements Listener {
    private static final Set<String> CLASSES = Set.of(
        "Kinkin.aeternum.portal.FrostOverworldPortals", "Kinkin.aeternum.portal.HeatOverworldPortals",
        "Kinkin.aeternum.portal.HeatNetherPortals", "Kinkin.aeternum.portal.VanillaPortalIsolation");
    private final AeternumCustomPortalPlugin plugin;
    private static final String FLORA = "Kinkin.aeternum.world.SeasonalFloraController";
    private final Map<Listener, NativeFloraProtection<Block>> flora = new IdentityHashMap<>();
    private final Set<String> warned = new HashSet<>();
    public AeternumPortalBridge(AeternumCustomPortalPlugin plugin) { this.plugin = plugin; }
    public void reconcile() {
        protectFlowers();
        if (!plugin.getConfig().getBoolean("aeternum.take-over-portals", true)) return;
        var aeternum = plugin.getServer().getPluginManager().getPlugin("AeternumSeasons");
        if (aeternum == null || !aeternum.isEnabled()) return;
        Set<Listener> listeners = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var handler : HandlerList.getRegisteredListeners(aeternum))
            if (CLASSES.contains(handler.getListener().getClass().getName())) listeners.add(handler.getListener());
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        if (!listeners.isEmpty()) plugin.getLogger().info("Controle de portais assumido: " + listeners.size() + " listeners do Aeternum. Mundos e demais sistemas preservados.");
    }
    static String key(Block block) { return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ(); }
    public void protectFlowers() {
        var aeternum = plugin.getServer().getPluginManager().getPlugin("AeternumSeasons");
        Set<Listener> active = Collections.newSetFromMap(new IdentityHashMap<>());
        if (aeternum != null && aeternum.isEnabled() && plugin.getConfig().getBoolean("aeternum.protect-portal-flowers", true)) {
            for (var handler : HandlerList.getRegisteredListeners(aeternum))
                if (handler.getListener().getClass().getName().equals(FLORA)) active.add(handler.getListener());
        }
        for (var iterator = flora.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (!active.contains(entry.getKey())) {
                try { entry.getValue().release(); } catch (ReflectiveOperationException ignored) { }
                iterator.remove();
            }
        }
        var flowers = plugin.portalFlowers();
        for (Listener listener : active) {
            try {
                var access = flora.get(listener);
                if (access == null) { access = new NativeFloraProtection<>(listener, Block.class); flora.put(listener, access); }
                if (!access.available() && !flowers.isEmpty() && warned.add("disabled"))
                    plugin.getLogger().warning("Flores de portal: no Aeternum, seasonal_flora.protect_player_placed deve ser true. Nenhuma opção do Aeternum foi alterada.");
                access.refresh(flowers);
            } catch (ReflectiveOperationException | RuntimeException error) {
                if (warned.add(listener.getClass().getName()))
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Integração de flores indisponível nesta versão do Aeternum; hook auditado para 4.5.", error);
            }
        }
    }
    public void releaseFlowers() {
        for (var access : flora.values()) try { access.release(); } catch (ReflectiveOperationException ignored) { }
        flora.clear();
    }
    private void playerEdit(Block block) { for (var access : flora.values()) access.forget(key(block)); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placed(BlockPlaceEvent event) { playerEdit(event.getBlockPlaced()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent event) { playerEdit(event.getBlock()); }
    @EventHandler public void enable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("AeternumSeasons")) plugin.getServer().getScheduler().runTask(plugin, this::reconcile);
    }
}
