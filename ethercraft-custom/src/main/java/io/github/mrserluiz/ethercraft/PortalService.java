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

/** Registered structures only; destination portals are built in already loaded worlds. */
public final class PortalService implements Listener {
    private final AeternumCustomPortalPlugin plugin;
    private final PortalDefinitions definitions;
    private final ProtectionService protections;
    private final Map<String, PortalFrame> frames = new LinkedHashMap<>();
    private final Map<String, String> links = new HashMap<>();
    private final Map<UUID, String> selected = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> pendingTravel = new HashSet<>();
    private final Set<String> pendingChecks = new HashSet<>();
    private boolean cleanupScheduled;
    public PortalService(AeternumCustomPortalPlugin plugin, PortalDefinitions definitions) throws Exception {
        this.plugin = plugin; this.definitions = definitions; this.protections = new ProtectionService(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::poolEffects, 20, 20);
        var file = plugin.getDataFolder().toPath().resolve("portals.yml");
        if (!Files.exists(file)) return;
        YamlConfiguration y = new YamlConfiguration(); y.load(file.toFile());
        int schema = y.getInt("schema");
        if (schema != 1 && schema != 2 && schema != 3) throw new IOException("Esquema de portais incompatível.");
        for (Map<?, ?> row : y.getMapList("portals")) {
            PortalFrame frame = new PortalFrame(UUID.fromString(row.get("world").toString()),
                ((Number) row.get("x")).intValue(), ((Number) row.get("y")).intValue(),
                ((Number) row.get("z")).intValue(), Axis.valueOf(row.get("axis").toString()),
                schema == 1 ? "frost" : Objects.toString(row.get("type"), ""),
                schema == 1 ? Material.BLUE_ICE : Material.matchMaterial(Objects.toString(row.get("frame-block"), "")));
            if (frame.frameMaterial() == null || !frame.frameMaterial().isBlock() || !frame.frameMaterial().isSolid()
                || !frame.typeId().matches("[a-z][a-z0-9_-]{0,39}")) throw new IOException("Registro de tipo/bloco inválido.");
            if (frames.putIfAbsent(frame.key(), frame) != null)
                throw new IOException("Registro de portal inválido/duplicado.");
            if (row.get("link") != null) links.put(frame.key(), row.get("link").toString());
        }
        for (var entry : links.entrySet())
            if (!frames.containsKey(entry.getValue()) || entry.getKey().equals(entry.getValue())
                || !entry.getKey().equals(links.get(entry.getValue())))
                throw new IOException("Vínculos de portal inconsistentes; arquivo preservado.");
        if (schema < 3) {
            var backup = file.resolveSibling("portals-before-0.3.3.yml");
            if (!Files.exists(backup)) Files.copy(file, backup);
            links.clear(); save();
            plugin.getLogger().info("Vínculos anteriores ao posicionamento por coordenadas descartados; frames preservados e backup criado.");
        }
    }
    public int size() { return frames.size(); }
    public void describe(org.bukkit.command.CommandSender sender) {
        for (var type : definitions.all()) sender.sendMessage(type.spec().id() + " [" + (type.spec().enabled() ? "ativo" : "desativado")
            + "]: " + type.spec().shape() + " / " + type.frame() + " / " + type.item() + " / " + type.spec().mode()
            + " | origens: " + type.spec().sourceWorlds() + " -> " + type.spec().destinationWorld()
            + " [" + (LoadedWorlds.resolve(type.spec().destinationWorld()) == null ? "destino não carregado" : "carregado") + "]");
    }
    public void reloadTypes() throws Exception {
        var oldDefinitions = definitions.snapshot();
        try {
            definitions.reload();
            Set<String> changedRoutes = new HashSet<>(oldDefinitions.keySet());
            changedRoutes.addAll(definitions.snapshot().keySet());
            changedRoutes.removeIf(id -> {
                var before = oldDefinitions.get(id); var after = definitions.get(id);
                return !PortalTypeSpec.routingChanged(before == null ? null : before.spec(), after == null ? null : after.spec());
            });
            reconcileAll(changedRoutes);
        }
        catch (Exception error) { definitions.restore(oldDefinitions); throw error; }
        selected.clear();
    }
    public void reconcileAll() throws IOException { reconcileAll(Set.of()); }
    private void reconcileAll(Set<String> changedRoutes) throws IOException {
        reconcile(frame -> {
            World world = Bukkit.getWorld(frame.worldId());
            return world != null && (definition(frame) == null || !allowed(definition(frame), world) || !frame.valid(true));
        }, changedRoutes);
    }
    private void reconcile(java.util.function.Predicate<PortalFrame> remove) throws IOException { reconcile(remove, Set.of()); }
    private void reconcile(java.util.function.Predicate<PortalFrame> remove, Set<String> changedRoutes) throws IOException {
        Map<String, PortalFrame> oldFrames = new LinkedHashMap<>(frames);
        Map<String, String> oldLinks = new HashMap<>(links);
        links.entrySet().removeIf(entry -> frames.containsKey(entry.getKey()) && changedRoutes.contains(frames.get(entry.getKey()).typeId()));
        List<PortalFrame> removed = PortalRegistryReconciler.prune(frames, links, remove, (a, b) ->
            Bukkit.getWorld(a.worldId()) == null || Bukkit.getWorld(b.worldId()) == null || route(a, b));
        if (frames.equals(oldFrames) && links.equals(oldLinks)) return;
        try { save(); }
        catch (IOException error) { frames.clear(); frames.putAll(oldFrames); links.clear(); links.putAll(oldLinks); throw error; }
        selected.values().removeIf(key -> !frames.containsKey(key));
        for (PortalFrame frame : removed) {
            if (Bukkit.getWorld(frame.worldId()) == null) continue;
            for (var c : frame.interior()) {
                Block block = frame.block(c.u(), c.v());
                if (block.getType() == Material.NETHER_PORTAL) block.setType(Material.AIR, false);
            }
        }
    }
    private void checkAfterChange(Collection<Block> blocks) {
        for (Block block : blocks) {
            PortalFrame frame = at(block, true);
            if (frame != null) pendingChecks.add(frame.key());
        }
        if (pendingChecks.isEmpty() || cleanupScheduled) return;
        cleanupScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            cleanupScheduled = false;
            Set<String> keys = new HashSet<>(pendingChecks); pendingChecks.clear();
            try { reconcile(frame -> keys.contains(frame.key()) && Bukkit.getWorld(frame.worldId()) != null && !frame.valid(true)); }
            catch (IOException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao salvar limpeza de portais; registros preservados. Use /acp reload para tentar novamente.", error); }
        });
    }
    private PortalDefinitions.Definition definition(PortalFrame frame) {
        var type = definitions.get(frame.typeId());
        return type != null && type.spec().enabled() && type.frame() == frame.frameMaterial()
            && (type.spec().shape() == PortalTypeSpec.Shape.HORIZONTAL_POOL) == frame.horizontal() ? type : null;
    }
    private boolean allowed(PortalDefinitions.Definition type, World world) {
        return type != null && world != null && type.spec().acceptsWorld(world.getName(), world.getKey().toString());
    }
    private boolean route(PortalFrame a, PortalFrame b) {
        if (a.worldId().equals(b.worldId()) || !a.typeId().equals(b.typeId()) || definition(a) == null || definition(b) == null) return false;
        World origin = Bukkit.getWorld(a.worldId()), dest = Bukkit.getWorld(b.worldId());
        return origin != null && dest != null && definition(a).spec().permitsPair(
            origin.getName(), origin.getKey().toString(), dest.getName(), dest.getKey().toString());
    }

    private void save() throws IOException {
        YamlConfiguration y = new YamlConfiguration(); y.set("schema", 3);
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
        for (var cell : frame.interior()) {
            Block b = frame.block(cell.u(), cell.v());
            if (b.getType() == Material.NETHER_PORTAL) b.setType(Material.AIR, false);
        }
        selected.values().removeIf(frame.key()::equals);
    }
    private enum Activation { IGNORED, HANDLED, ACTIVATED }
    private Activation activate(Player player, Block hit, Material item, PortalTypeSpec.ActivationMode mode) throws IOException {
        PortalFrame frame = null;
        String rejected = null;
        for (var type : definitions.all()) {
            if (type.item() != item || type.spec().mode() != mode) continue;
            PortalFrame found = PortalFrame.detect(hit, type.spec().id(), type.frame(), type.spec().shape());
            if (found != null) {
                if (!type.spec().enabled()) { rejected = "Tipo " + type.spec().id() + " desativado: configure enabled: true e use /acp reload."; continue; }
                if (!allowed(type, hit.getWorld())) { rejected = "Tipo " + type.spec().id() + ": mundo " + hit.getWorld().getName() + " não autorizado. Origens no YAML: " + type.spec().sourceWorlds(); continue; }
                if (frame != null) throw new IllegalArgumentException("Estrutura de ativação ambígua.");
                frame = found;
            }
        }
        if (frame == null) {
            if (rejected != null) { player.sendPlainMessage(rejected); return Activation.HANDLED; }
            return Activation.IGNORED;
        }
        if (!player.hasPermission("aeternumcustomportal.portal.activate")) {
            player.sendPlainMessage("Sem permissão aeternumcustomportal.portal.activate."); return Activation.HANDLED;
        }
        if (frames.containsKey(frame.key())) {
            PortalFrame existing = frames.get(frame.key());
            if (existing.equals(frame) && existing.valid(true) && definition(existing) != null) return Activation.HANDLED;
            String key = frame.key();
            reconcile(candidate -> candidate.key().equals(key));
        }
        if (LoadedWorlds.resolve(definitions.get(frame.typeId()).spec().destinationWorld()) == null)
            throw new IllegalArgumentException("Mundo de destino não carregado; carregue-o pelo Aeternum ou gerenciador de mundos.");
        String denied = protections.denial(player, frame, false);
        if (denied != null) {
            player.sendPlainMessage("Ativação bloqueada: " + denied); return Activation.HANDLED;
        }
        for (PortalFrame existing : frames.values())
            for (var c : frame.interior()) if (existing.contains(frame.block(c.u(), c.v()), true))
                throw new IllegalArgumentException("Portal sobreposto a outro registro.");
        // Persist before editing blocks; rollback if persistence fails.
        frames.put(frame.key(), frame);
        try { save(); } catch (IOException e) { frames.remove(frame.key()); throw e; }
        if (!frame.horizontal()) {
            Orientable data = (Orientable) Bukkit.createBlockData(Material.NETHER_PORTAL); data.setAxis(frame.axis());
            for (var c : frame.interior()) frame.block(c.u(), c.v()).setBlockData(data, false);
        }
        player.sendPlainMessage("Portal " + frame.typeId() + " ativado. Destino definido no YAML; select/link permitem vincular um portal específico.");
        return Activation.ACTIVATED;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectile(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player player) || e.getHitBlock() == null) return;
        Material item = e.getEntity() instanceof Snowball ? Material.SNOWBALL : e.getEntity() instanceof Egg ? Material.EGG : null;
        if (item == null) return;
        try { if (activate(player, e.getHitBlock(), item, PortalTypeSpec.ActivationMode.PROJECTILE) != Activation.IGNORED) e.setCancelled(true); }
        catch (Exception error) { e.setCancelled(true); player.sendPlainMessage("Falha ao ativar: " + error.getMessage()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR)
            || e.getHand() != EquipmentSlot.HAND || e.getItem() == null) return;
        Block hit = e.getClickedBlock();
        if (hit == null) {
            // Only horizontal interact types need a fluid ray trace; ordinary right clicks stay untouched.
            if (definitions.all().stream().noneMatch(t -> t.spec().shape() == PortalTypeSpec.Shape.HORIZONTAL_POOL
                && t.spec().mode() == PortalTypeSpec.ActivationMode.INTERACT && t.item() == e.getItem().getType())) return;
            var ray = e.getPlayer().rayTraceBlocks(6, FluidCollisionMode.SOURCE_ONLY);
            if (ray == null) return;
            hit = ray.getHitBlock();
        }
        if (hit == null) return;
        if (e.useItemInHand() == Event.Result.DENY) {
            for (var type : definitions.all())
                if (type.spec().mode() == PortalTypeSpec.ActivationMode.INTERACT && type.item() == e.getItem().getType()
                    && type.frame() == hit.getType()) {
                    e.getPlayer().sendPlainMessage("Ativação cancelada pelo servidor/proteção antes do addon."); break;
                }
            return;
        }
        try {
            Activation result = activate(e.getPlayer(), hit, e.getItem().getType(), PortalTypeSpec.ActivationMode.INTERACT);
            if (result != Activation.IGNORED) e.setCancelled(true);
            // A configured pool catalyst is consumed only after registration succeeds.
            if (result == Activation.ACTIVATED) {
                PortalFrame pool = at(hit, true);
                if (pool != null && pool.horizontal()) {
                    var held = e.getPlayer().getInventory().getItemInMainHand();
                    held.setAmount(held.getAmount() - 1);
                    e.getPlayer().getInventory().setItemInMainHand(held);
                    pool.block(0, 0).getWorld().strikeLightningEffect(pool.block(0, 0).getLocation());
                }
            }
        } catch (Exception error) { e.setCancelled(true); e.getPlayer().sendPlainMessage("Falha ao ativar: " + error.getMessage()); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) {
        var item = e.getItemDrop(); Player player = e.getPlayer();
        if (definitions.all().stream().noneMatch(t -> t.spec().mode() == PortalTypeSpec.ActivationMode.DROP_ITEM
            && t.item() == item.getItemStack().getType())) return;
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;
            @Override public void run() {
                if ((ticks += 2) > 100 || !item.isValid() || !player.isOnline() || player.getWorld() != item.getWorld()) { cancel(); return; }
                Block water = item.getLocation().getBlock();
                if (water.getType() != Material.WATER) return;
                try {
                    Activation result = activate(player, water, item.getItemStack().getType(), PortalTypeSpec.ActivationMode.DROP_ITEM);
                    if (result == Activation.IGNORED) return;
                    cancel();
                    if (result == Activation.ACTIVATED) {
                        var stack = item.getItemStack();
                        if (stack.getAmount() == 1) item.remove();
                        else { stack.setAmount(stack.getAmount() - 1); item.setItemStack(stack); }
                        water.getWorld().strikeLightningEffect(water.getLocation());
                    }
                } catch (Exception error) { cancel(); player.sendPlainMessage("Falha ao ativar: " + error.getMessage()); }
            }
        }.runTaskTimer(plugin, 1, 2);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent e) {
        // Nether portal physics requires obsidian; only preserve intact registered custom frames.
        PortalFrame frame = at(e.getBlock(), false);
        if (frame != null && !frame.horizontal() && frame.valid(true)) e.setCancelled(true);
        else checkAfterChange(List.of(e.getBlock()));
    }
    private PortalFrame near(Location loc) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            for (int dy = 0; dy <= 1; dy++) {
                PortalFrame f = at(loc.clone().add(dx, dy, dz).getBlock(), false);
                if (f != null) return f;
            }
        return null;
    }
    // Unregistered custom interiors must never fall through to vanilla Nether routing.
    private boolean unmanagedCustom(Location loc) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block block = loc.clone().add(dx, 0, dz).getBlock();
            if (block.getType() != Material.NETHER_PORTAL) continue;
            for (int down = 0; down < 64 && block.getY() > block.getWorld().getMinHeight(); down++) {
                Block below = block.getRelative(0, -1, 0);
                if (below.getType() != Material.NETHER_PORTAL) return below.getType() != Material.OBSIDIAN;
                block = below;
            }
            return true;
        }
        return false;
    }
    private Location safeExit(PortalFrame frame, Location from) {
        if (frame.horizontal()) {
            for (var c : frame.border()) {
                Block floor = frame.block(c.u(), c.v());
                Block feet = floor.getRelative(0, 1, 0), head = floor.getRelative(0, 2, 0);
                if (!LoadedWorlds.safeFloor(floor.getType()) || !LoadedWorlds.safeSpace(feet) || !LoadedWorlds.safeSpace(head)) continue;
                Location exit = feet.getLocation().add(0.5, 0, 0.5);
                if (!exit.getWorld().getWorldBorder().isInside(exit)) continue;
                exit.setYaw(from.getYaw()); exit.setPitch(from.getPitch()); return exit;
            }
            return null;
        }
        for (int side : new int[]{1, -1}) for (int u = 0; u < 2; u++) {
            Block inside = frame.block(u, 0);
            Block feet = inside.getRelative(frame.axis() == Axis.X ? 0 : side, 0, frame.axis() == Axis.X ? side : 0);
            Block floor = feet.getRelative(0, -1, 0), head = feet.getRelative(0, 1, 0);
            if (LoadedWorlds.safeSpace(feet) && LoadedWorlds.safeSpace(head) && LoadedWorlds.safeFloor(floor.getType())) {
                Location exit = feet.getLocation().add(0.5, 0, 0.5);
                if (exit.getWorld().getWorldBorder().isInside(exit)) {
                    exit.setYaw(from.getYaw()); exit.setPitch(from.getPitch()); return exit;
                }
            }
        }
        // Vanilla-style arrival inside an intact portal: its bottom frame already supports the player.
        if (frame.valid(true)) for (int u = 0; u < 2; u++) {
            if (!LoadedWorlds.safeFloor(frame.block(u, -1).getType())) continue;
            Location exit = frame.block(u, 0).getLocation().add(0.5, 0, 0.5);
            if (!exit.getWorld().getWorldBorder().isInside(exit)) continue;
            exit.setYaw(from.getYaw()); exit.setPitch(from.getPitch()); return exit;
        }
        return null;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        PortalFrame source = near(e.getFrom());
        if (source == null) {
            if (unmanagedCustom(e.getFrom()) || (e.getTo() != null && (near(e.getTo()) != null || unmanagedCustom(e.getTo())))) { e.setCancelled(true); e.setCanCreatePortal(false); }
            return;
        }
        e.setCancelled(true); e.setCanCreatePortal(false);
        queueTravel(e.getPlayer(), source);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent || e.getTo() == null) return;
        Location to = e.getTo(), from = e.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        if (to.getBlock().getType() != Material.WATER) return;
        PortalFrame source = at(to.getBlock(), false);
        if (source != null && source.horizontal()) queueTravel(e.getPlayer(), source);
    }
    private boolean stillInside(Player player, PortalFrame source) {
        return source.horizontal() ? source.contains(player.getLocation().getBlock(), false) : near(player.getLocation()) == source;
    }
    private void queueTravel(Player player, PortalFrame source) {
        UUID id = player.getUniqueId();
        if (!player.hasPermission("aeternumcustomportal.portal.use") || cooldown.getOrDefault(id, 0L) > System.currentTimeMillis()
            || pendingTravel.contains(id)) return;
        if (!source.valid(true) || definition(source) == null || !allowed(definition(source), player.getWorld())) {
            player.sendPlainMessage("Portal desativado ou não autorizado pelo YAML."); return;
        }
        pendingTravel.add(id);
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingTravel.remove(id);
            if (!player.isOnline() || frames.get(source.key()) != source || !source.valid(true)
                || definition(source) == null || !allowed(definition(source), player.getWorld()) || !stillInside(player, source)) return;
            try { ensureReturnPortal(source, player); }
            catch (Exception error) { player.sendPlainMessage("Retorno não criado: " + error.getMessage()); return; }
            Location checked = destination(source, player.getLocation());
            if (checked == null) { player.sendPlainMessage("Destino não carregado, vínculo inválido ou saída insegura."); return; }
            if (player.teleport(checked, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                player.setPortalCooldown(300);
                cooldown.put(id, System.currentTimeMillis() + 5000);
            }
        });
    }
    private void ensureReturnPortal(PortalFrame source, Player player) throws IOException {
        if (links.containsKey(source.key()) || !plugin.getConfig().getBoolean("auto-return-portal", true)) return;
        var type = definition(source);
        if (!allowed(type, player.getWorld())) throw new IOException("Tipo desativado ou mundo não autorizado.");
        // Resolve target by loaded identity; explicit links make return unambiguous even with multiple sources.
        String reference = type.spec().unlinkedTarget(player.getWorld().getName(), player.getWorld().getKey().toString());
        World world = reference == null ? null : LoadedWorlds.resolve(reference);
        if (world == null || world.getUID().equals(source.worldId())) throw new IOException("Destino não carregado ou retorno ambíguo; configure/vincule os portais.");
        Location center = corresponding(source, world);
        int searchRadius = Math.max(1, Math.min(128, plugin.getConfig().getInt("portal-placement.search-radius", world.getEnvironment() == World.Environment.NETHER ? 16 : 128)));
        PortalFrame nearest = frames.values().stream().filter(candidate -> !links.containsKey(candidate.key())
            && candidate.valid(true) && route(source, candidate)
            && PortalCoordinates.nearby(candidate.x(), candidate.z(), center.getX(), center.getZ(), searchRadius)
            && safeExit(candidate, player.getLocation()) != null)
            .min(Comparator.comparingDouble(candidate -> candidate.block(0, 0).getLocation().distanceSquared(center))).orElse(null);
        if (nearest != null) { bind(source, nearest); return; }
        int radiusLimit = Math.max(0, Math.min(16, plugin.getConfig().getInt("portal-placement.creation-radius", 8)));
        int top = Math.min(world.getMaxHeight(), world.getMinHeight() + world.getLogicalHeight()) - 5;
        int bottom = world.getMinHeight() + 2;
        int preferred = Math.max(bottom, Math.min(top, center.getBlockY()));
        String denied = null;
        // Prefer clear sites around the matching coordinates; fallback can clear limited terrain.
        for (int phase = 0; phase < 2; phase++) {
            if (phase == 1 && !plugin.getConfig().getBoolean("portal-placement.allow-terrain-clearing", true)) break;
            for (int radius = 0; radius <= radiusLimit; radius++)
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int offset = 0; offset <= 16; offset++) for (int sign : offset == 0 ? new int[]{1} : new int[]{1, -1}) {
                        int y = preferred + offset * sign;
                        if (y < bottom || y > top) continue;
                        PortalFrame target = new PortalFrame(world.getUID(), center.getBlockX() + dx, y, center.getBlockZ() + dz,
                            source.axis(), source.typeId(), type.frame());
                        List<Block> edits = target.horizontal() ? PoolGeometry.edits().stream().map(c -> poolBlock(target, c)).toList()
                            : ReturnPortalGeometry.edits().stream().map(c -> returnBlock(target, c)).toList();
                        List<Block> clearance = target.horizontal() ? PoolGeometry.clearance().stream().map(c -> poolBlock(target, c)).toList()
                            : ReturnPortalGeometry.clearance().stream().map(c -> returnBlock(target, c)).toList();
                        List<Block> all = new ArrayList<>(edits); all.addAll(clearance);
                        final int sitePhase = phase;
                        if (all.stream().anyMatch(block -> !world.getWorldBorder().isInside(block.getLocation()) || at(block, true) != null
                            || !PortalTerrain.canReplace(block.getType().name()) || (sitePhase == 0 && !LoadedWorlds.safeSpace(block)))) continue;
                        denied = protections.denial(player, all, false, type.frame(), target.horizontal() ? Material.WATER : Material.NETHER_PORTAL);
                        if (denied == null && target.horizontal()) {
                            int flowerIndex = 0;
                            for (var cell : target.border()) {
                                Material flower = Material.valueOf(PoolGeometry.returnFlowers().get(flowerIndex++ % PoolGeometry.returnFlowers().size()));
                                denied = protections.denial(player, List.of(target.block(cell.u(), cell.v()).getRelative(0, 1, 0)), false, flower, flower);
                                if (denied != null) break;
                            }
                        }
                        if (denied == null) denied = protections.denial(player, all.stream().filter(block -> !block.getType().isAir()).toList(), true);
                        if (denied != null) continue;
                        buildReturn(source, target, player, type, edits, clearance); return;
                    }
                }
        }
        throw new IOException(denied == null ? "Sem local compatível nas coordenadas correspondentes " + center.getBlockX() + ", " + center.getBlockZ() + "." : "Proteção do destino: " + denied);
    }
    private void poolEffects() {
        for (PortalFrame frame : frames.values()) {
            if (!frame.horizontal()) continue;
            World world = Bukkit.getWorld(frame.worldId());
            if (world == null || !world.isChunkLoaded((frame.x() - 1) >> 4, (frame.z() - 1) >> 4)
                || !world.isChunkLoaded((frame.x() + 2) >> 4, (frame.z() - 1) >> 4)
                || !world.isChunkLoaded((frame.x() - 1) >> 4, (frame.z() + 2) >> 4)
                || !world.isChunkLoaded((frame.x() + 2) >> 4, (frame.z() + 2) >> 4)) continue;
            if (definition(frame) == null || !allowed(definition(frame), world) || !frame.valid(true)) {
                checkAfterChange(List.of(frame.block(0, 0))); continue;
            }
            world.spawnParticle(Particle.PORTAL, frame.x() + 1, frame.y() + 0.8, frame.z() + 1, 12, 0.6, 0.1, 0.6, 0.05);
        }
    }
    private Location corresponding(PortalFrame source, World destination) {
        World origin = Bukkit.getWorld(source.worldId());
        if (origin == null) throw new IllegalStateException("Origem descarregada.");
        var border = destination.getWorldBorder(); var center = border.getCenter();
        double x = PortalCoordinates.scale(source.x() + 0.5, origin.getCoordinateScale(), destination.getCoordinateScale());
        double z = PortalCoordinates.scale(source.z() + 0.5, origin.getCoordinateScale(), destination.getCoordinateScale());
        return new Location(destination, PortalCoordinates.clamp(x, center.getX(), border.getSize(), 4), source.y(),
            PortalCoordinates.clamp(z, center.getZ(), border.getSize(), 4));
    }
    private void buildReturn(PortalFrame source, PortalFrame target, Player player, PortalDefinitions.Definition type,
                             List<Block> edits, List<Block> clearance) throws IOException {
        List<Block> all = new ArrayList<>(edits); all.addAll(clearance);
        var snapshots = all.stream().map(Block::getBlockData).toList();
        frames.put(target.key(), target); links.put(source.key(), target.key()); links.put(target.key(), source.key());
        try { save(); }
        catch (IOException error) { frames.remove(target.key()); links.remove(source.key()); links.remove(target.key()); throw error; }
        try {
            for (Block block : clearance) block.setType(Material.AIR, false);
            for (Block block : edits) block.setType(type.frame(), false);
            if (target.horizontal()) {
                int index = 0;
                for (var c : target.border()) target.block(c.u(), c.v()).getRelative(0, 1, 0).setType(
                    Material.valueOf(PoolGeometry.returnFlowers().get(index++ % PoolGeometry.returnFlowers().size())), false);
                for (var c : target.interior()) target.block(c.u(), c.v()).setType(Material.WATER, false);
            } else {
                Orientable data = (Orientable) Bukkit.createBlockData(Material.NETHER_PORTAL); data.setAxis(target.axis());
                for (var c : target.interior()) target.block(c.u(), c.v()).setBlockData(data, false);
            }
            if (!target.valid(true)) throw new IllegalStateException("Estrutura inválida após montagem.");
            if (safeExit(target, player.getLocation()) == null) throw new IllegalStateException("Saída inválida após montagem.");
        } catch (RuntimeException error) {
            for (int i = 0; i < all.size(); i++) all.get(i).setBlockData(snapshots.get(i), false);
            frames.remove(target.key()); links.remove(source.key()); links.remove(target.key());
            try { save(); } catch (IOException rollback) { error.addSuppressed(rollback); }
            throw new IOException("Falha ao montar portal; alterações revertidas.", error);
        }
        player.sendPlainMessage("Portal " + target.typeId() + " criado em " + target.x() + ", " + target.y() + ", " + target.z() + "; retorno vinculado à origem.");
    }
    private Block poolBlock(PortalFrame frame, PoolGeometry.Cell cell) {
        return frame.block(cell.u(), cell.v()).getRelative(0, cell.height(), 0);
    }
    private Block returnBlock(PortalFrame frame, ReturnPortalGeometry.Cell cell) {
        return frame.block(cell.u(), cell.v()).getRelative(frame.axis() == Axis.X ? 0 : cell.side(), 0, frame.axis() == Axis.X ? cell.side() : 0);
    }
    private void bind(PortalFrame source, PortalFrame target) throws IOException {
        links.put(source.key(), target.key()); links.put(target.key(), source.key());
        try { save(); } catch (IOException error) { links.remove(source.key()); links.remove(target.key()); throw error; }
    }
    private Location destination(PortalFrame source, Location from) {
        var type = definition(source);
        if (!allowed(type, from.getWorld())) return null;
        if (links.containsKey(source.key())) {
            PortalFrame dest = frames.get(links.get(source.key()));
            return dest != null && dest.valid(true) && route(source, dest) ? safeExit(dest, from) : null;
        }
        String target = type.spec().unlinkedTarget(from.getWorld().getName(), from.getWorld().getKey().toString());
        World world = target == null ? null : LoadedWorlds.resolve(target);
        if (world == null || world.getUID().equals(source.worldId())) return null;
        // With automatic generation disabled, only an existing corresponding portal is usable.
        Location center = corresponding(source, world);
        int radius = Math.max(1, Math.min(128, plugin.getConfig().getInt("portal-placement.search-radius", 128)));
        return frames.values().stream().filter(candidate -> candidate.valid(true) && route(source, candidate)
            && PortalCoordinates.nearby(candidate.x(), candidate.z(), center.getX(), center.getZ(), radius))
            .sorted(Comparator.comparingDouble(candidate -> candidate.block(0, 0).getLocation().distanceSquared(center)))
            .map(candidate -> safeExit(candidate, from)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPortal(EntityPortalEvent e) {
        if (near(e.getFrom()) != null || unmanagedCustom(e.getFrom()) || (e.getTo() != null && (near(e.getTo()) != null || unmanagedCustom(e.getTo())))) e.setCancelled(true);
    }
    // Observe the final event outcome; vanilla and protection plugins decide whether edits happen.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) { checkAfterChange(List.of(e.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent e) { checkAfterChange(List.of(e.getBlock())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) { checkAfterChange(e.blockList()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent e) { checkAfterChange(e.blockList()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { checkPiston(e.getBlocks(), e.getDirection()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) { checkPiston(e.getBlocks(), e.getDirection()); }
    private void checkPiston(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        List<Block> affected = new ArrayList<>(blocks);
        for (Block block : blocks) {
            affected.add(block.getRelative(direction)); affected.add(block.getRelative(direction.getOppositeFace()));
        }
        checkAfterChange(affected);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bucket(PlayerBucketFillEvent e) { checkAfterChange(List.of(e.getBlockClicked(), e.getBlockClicked().getRelative(e.getBlockFace()))); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent e) { checkAfterChange(List.of(e.getBlockClicked(), e.getBlockClicked().getRelative(e.getBlockFace()))); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void flow(BlockFromToEvent e) { checkAfterChange(List.of(e.getBlock(), e.getToBlock())); }
    @EventHandler public void worldLoaded(org.bukkit.event.world.WorldLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { reconcileAll(); }
            catch (IOException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Falha ao reconciliar portais carregados.", error); }
        });
    }
    @EventHandler public void quit(PlayerQuitEvent e) { selected.remove(e.getPlayer().getUniqueId()); cooldown.remove(e.getPlayer().getUniqueId()); }
}
