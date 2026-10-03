package io.github.mrserluiz.ethercraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Egg;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

/** Registered frames only. No world creation or destination construction during teleport. */
public final class PortalService implements Listener {
    private final EtherCraftPlugin plugin;
    private final DimensionService dimensions;
    private final PortalDefinitions definitions;
    private final ProtectionService protections;
    private final Map<String, PortalFrame> frames = new LinkedHashMap<>();
    private final Map<String, String> links = new HashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    public PortalService(EtherCraftPlugin plugin, DimensionService dimensions, PortalDefinitions definitions) throws Exception {
        this.plugin = plugin; this.dimensions = dimensions; this.definitions = definitions; this.protections = new ProtectionService(plugin);
        var file = plugin.getDataFolder().toPath().resolve("portals.yml");
        if (!Files.exists(file)) return;
        YamlConfiguration y = new YamlConfiguration(); y.load(file.toFile());
        int schema = y.getInt("schema");
        if (schema != 1 && schema != 2) throw new IOException("Esquema de portais incompatível.");
        for (Map<?, ?> row : y.getMapList("portals")) {
            PortalFrame frame = new PortalFrame(UUID.fromString(row.get("world").toString()),
                ((Number) row.get("x")).intValue(), ((Number) row.get("y")).intValue(),
                ((Number) row.get("z")).intValue(), Axis.valueOf(row.get("axis").toString()),
                schema == 1 ? "frost" : Objects.toString(row.get("type"), ""),
                schema == 1 ? Material.BLUE_ICE : Material.matchMaterial(Objects.toString(row.get("frame-block"), "")));
            if (frame.frameMaterial() == null || !frame.frameMaterial().isBlock() || !frame.frameMaterial().isSolid()
                || !frame.typeId().matches("[a-z][a-z0-9_-]{0,39}")) throw new IOException("Registro de tipo/bloco inválido.");
            if (frame.axis() == Axis.Y || frames.putIfAbsent(frame.key(), frame) != null)
                throw new IOException("Registro de portal inválido/duplicado.");
            if (row.get("link") != null) links.put(frame.key(), row.get("link").toString());
        }
        for (var entry : links.entrySet())
            if (!frames.containsKey(entry.getValue()) || entry.getKey().equals(entry.getValue())
                || !entry.getKey().equals(links.get(entry.getValue())))
                throw new IOException("Vínculos de portal inconsistentes; arquivo preservado.");
    }
    public int size() { return frames.size(); }
    public void describe(org.bukkit.command.CommandSender sender) {
        for (var type : definitions.all()) sender.sendMessage(type.spec().id() + " [" + (type.spec().enabled() ? "ativo" : "desativado")
            + "]: " + type.frame() + " / " + type.item() + " / " + type.spec().mode() + " -> " + type.spec().destinationWorld());
    }
    public void reloadTypes() throws Exception { definitions.reload(); selected.clear(); }
    private PortalDefinitions.Definition definition(PortalFrame frame) {
        var type = definitions.get(frame.typeId());
        return type != null && type.spec().enabled() && type.frame() == frame.frameMaterial() ? type : null;
    }
    private boolean allowed(PortalDefinitions.Definition type, World world) {
        if (type == null || world == null || !type.spec().acceptsWorld(world.getName())) return false;
        return !world.getName().equals(dimensions.name()) || dimensions.owns(world);
    }
    private boolean route(PortalFrame a, PortalFrame b) {
        if (!a.typeId().equals(b.typeId()) || definition(a) == null || definition(b) == null) return false;
        var type = definition(a);
        World origin = Bukkit.getWorld(a.worldId()), dest = Bukkit.getWorld(b.worldId());
        return allowed(type, origin) && allowed(type, dest) && type.spec().permitsPair(origin.getName(), dest.getName());
    }
    private void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration(); y.set("schema", 2);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PortalFrame f : frames.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("world", f.worldId().toString()); row.put("x", f.x()); row.put("y", f.y());
            row.put("z", f.z()); row.put("axis", f.axis().name());
            row.put("type", f.typeId()); row.put("frame-block", f.frameMaterial().name());
            if (links.containsKey(f.key())) row.put("link", links.get(f.key()));
            rows.add(row);
        }
        y.set("portals", rows);
        var folder = plugin.getDataFolder().toPath(); Files.createDirectories(folder);
        var tmp = Files.createTempFile(folder, "portals-", ".tmp");
        try {
            y.save(tmp.toFile());
            try { Files.move(tmp, folder.resolve("portals.yml"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, folder.resolve("portals.yml"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }
    private PortalFrame at(Block b, boolean border) {
        return frames.values().stream().filter(f -> f.contains(b, border)).findFirst().orElse(null);
    }
    public void select(Player player) {
        Block target = player.getTargetBlockExact(8);
        PortalFrame frame = target == null ? null : at(target, true);
        if (frame == null || !frame.valid(true)) throw new IllegalArgumentException("Olhe para um portal customizado ativo, a até 8 blocos.");
        selected.put(player.getUniqueId(), frame.key());
    }
    public void link(Player player) throws IOException {
        String source = selected.get(player.getUniqueId());
        Block target = player.getTargetBlockExact(8);
        PortalFrame dest = target == null ? null : at(target, true);
        PortalFrame origin = frames.get(source);
        if (origin == null || dest == null || !origin.valid(true) || !dest.valid(true))
            throw new IllegalArgumentException("Selecione a origem e olhe para um segundo portal ativo.");
        if (origin.equals(dest)) throw new IllegalArgumentException("Escolha outro portal.");
        if (!route(origin, dest))
            throw new IllegalArgumentException("Os portais devem ter o mesmo tipo e unir uma origem ao destino definido no YAML.");
        if (links.containsKey(source) || links.containsKey(dest.key()))
            throw new IllegalArgumentException("Desvincule os portais antes de criar outro par.");
        links.put(source, dest.key()); links.put(dest.key(), source);
        try { save(); } catch (IOException e) { links.remove(source); links.remove(dest.key()); throw e; }
        selected.remove(player.getUniqueId());
    }
    public void unlink(Player player) throws IOException {
        Block target = player.getTargetBlockExact(8);
        PortalFrame frame = target == null ? null : at(target, true);
        if (frame == null) throw new IllegalArgumentException("Olhe para um portal registrado.");
        String partner = links.remove(frame.key());
        if (partner != null) links.remove(partner);
        try { save(); } catch (IOException e) {
            if (partner != null) { links.put(frame.key(), partner); links.put(partner, frame.key()); }
            throw e;
        }
    }
    public void remove(Player player) throws IOException {
        Block target = player.getTargetBlockExact(8);
        PortalFrame frame = target == null ? null : at(target, true);
        if (frame == null) throw new IllegalArgumentException("Olhe para um portal registrado.");
        String denied = protections.denial(player, frame, true);
        if (denied != null) throw new IllegalArgumentException("Remoção bloqueada: " + denied);
        String partner = links.remove(frame.key());
        if (partner != null) links.remove(partner);
        frames.remove(frame.key());
        try { save(); } catch (IOException e) {
            frames.put(frame.key(), frame);
            if (partner != null) { links.put(frame.key(), partner); links.put(partner, frame.key()); }
            throw e;
        }
        for (var cell : FrameGeometry.interior()) {
            Block b = frame.block(cell.u(), cell.v());
            if (b.getType() == Material.NETHER_PORTAL) b.setType(Material.AIR, false);
        }
        selected.values().removeIf(frame.key()::equals);
    }
    private boolean activate(Player player, Block hit, Material item, PortalTypeSpec.ActivationMode mode) throws IOException {
        if (!player.hasPermission("ethercraft.portal.activate")) return false;
        PortalFrame frame = null;
        for (var type : definitions.all()) {
            if (type.item() != item || type.spec().mode() != mode || !allowed(type, hit.getWorld())) continue;
            PortalFrame found = PortalFrame.detect(hit, type.spec().id(), type.frame());
            if (found != null) {
                if (frame != null) throw new IllegalArgumentException("Estrutura de ativação ambígua.");
                frame = found;
            }
        }
        if (frame == null) return false;
        if (frames.containsKey(frame.key())) return true;
        String denied = protections.denial(player, frame, false);
        if (denied != null) {
            player.sendPlainMessage("Ativação bloqueada: " + denied); return true;
        }
        for (PortalFrame existing : frames.values())
            for (var c : FrameGeometry.interior()) if (existing.contains(frame.block(c.u(), c.v()), true))
                throw new IllegalArgumentException("Portal sobreposto a outro registro.");
        // Persist before editing blocks; rollback if persistence fails.
        frames.put(frame.key(), frame);
        try { save(); } catch (IOException e) { frames.remove(frame.key()); throw e; }
        Orientable data = (Orientable) Bukkit.createBlockData(Material.NETHER_PORTAL); data.setAxis(frame.axis());
        for (var c : FrameGeometry.interior()) frame.block(c.u(), c.v()).setBlockData(data, false);
        player.sendPlainMessage("Portal " + frame.typeId() + " ativado. Use /ethercraft select e /ethercraft link para vincular.");
        return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectile(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player player) || e.getHitBlock() == null) return;
        Material item = e.getEntity() instanceof Snowball ? Material.SNOWBALL : e.getEntity() instanceof Egg ? Material.EGG : null;
        if (item == null) return;
        try { if (activate(player, e.getHitBlock(), item, PortalTypeSpec.ActivationMode.PROJECTILE)) e.setCancelled(true); }
        catch (Exception error) { e.setCancelled(true); player.sendPlainMessage("Falha ao ativar: " + error.getMessage()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent e) {
        // Vanilla may deny block-use for an inert frame even when the item is usable.
        // Respect item-use denial; configured protection plugins also block activation.
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
            || e.getClickedBlock() == null || e.getItem() == null
            || e.useItemInHand() == Event.Result.DENY) return;
        try {
            if (activate(e.getPlayer(), e.getClickedBlock(), e.getItem().getType(), PortalTypeSpec.ActivationMode.INTERACT))
                e.setCancelled(true); // Do not also ignite/place/use the activation item through vanilla.
        } catch (Exception error) { e.setCancelled(true); e.getPlayer().sendPlainMessage("Falha ao ativar: " + error.getMessage()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent e) {
        // Nether portal physics requires obsidian; only preserve intact registered custom frames.
        PortalFrame frame = at(e.getBlock(), false);
        if (frame != null && frame.valid(true)) e.setCancelled(true);
    }
    private PortalFrame near(Location loc) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int dy = 0; dy <= 1; dy++) {
                PortalFrame f = at(loc.clone().add(dx, dy, dz).getBlock(), false);
                if (f != null) return f;
            }
        return null;
    }
    private Location safeExit(PortalFrame frame, Location from) {
        for (int side : new int[]{1, -1}) for (int u = 0; u < 2; u++) {
            Block inside = frame.block(u, 0);
            Block feet = inside.getRelative(frame.axis() == Axis.X ? 0 : side, 0, frame.axis() == Axis.X ? side : 0);
            Block floor = feet.getRelative(0, -1, 0), head = feet.getRelative(0, 1, 0);
            if (feet.getType().isAir() && head.getType().isAir() && floor.getType().isSolid()
                && floor.getType() != Material.MAGMA_BLOCK && floor.getType() != Material.CACTUS
                && floor.getType() != Material.CAMPFIRE && floor.getType() != Material.SOUL_CAMPFIRE
                && floor.getType() != Material.POWDER_SNOW) {
                Location exit = feet.getLocation().add(0.5, 0, 0.5);
                if (exit.getWorld().getWorldBorder().isInside(exit)) {
                    exit.setYaw(from.getYaw()); exit.setPitch(from.getPitch()); return exit;
                }
            }
        }
        return null;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        PortalFrame source = near(e.getFrom());
        if (source == null) {
            if (e.getTo() != null && near(e.getTo()) != null) { e.setCancelled(true); e.setCanCreatePortal(false); }
            return;
        }
        e.setCancelled(true); e.setCanCreatePortal(false);
        Player player = e.getPlayer();
        if (!player.hasPermission("ethercraft.portal.use") || cooldown.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        PortalFrame dest = frames.get(links.get(source.key()));
        if (!source.valid(true) || dest == null || !dest.valid(true)
            || !route(source, dest)) {
            player.sendPlainMessage("Portal sem destino ativo/carregado e autorizado."); return;
        }
        Location exit = safeExit(dest, e.getFrom());
        if (exit == null) { player.sendPlainMessage("Prepare uma saída segura nos dois lados do portal de destino."); return; }
        // Next tick permits later protection handlers to run. TeleportEvent remains cancellable.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !source.valid(true) || !dest.valid(true)
                || near(player.getLocation()) != source || !route(source, dest)
                || !dest.key().equals(links.get(source.key()))) return;
            Location checked = safeExit(dest, player.getLocation());
            if (checked != null && player.teleport(checked, PlayerTeleportEvent.TeleportCause.PLUGIN))
                cooldown.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPortal(EntityPortalEvent e) {
        if (near(e.getFrom()) != null || (e.getTo() != null && near(e.getTo()) != null)) e.setCancelled(true);
    }
    // Registered frames remain protected until explicitly removed by an admin.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) { if (at(e.getBlock(), true) != null) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent e) { if (at(e.getBlock(), true) != null) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) { e.blockList().removeIf(b -> at(b, true) != null); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent e) { e.blockList().removeIf(b -> at(b, true) != null); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> at(b, true) != null || at(b.getRelative(e.getDirection()), true) != null)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> at(b, true) != null || at(b.getRelative(e.getDirection()), true) != null)) e.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { selected.remove(e.getPlayer().getUniqueId()); cooldown.remove(e.getPlayer().getUniqueId()); }
}
