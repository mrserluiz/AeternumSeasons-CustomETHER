package Kinkin.aeternum.fauna;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class CreatureGeneratorService implements Listener {
   private static final int STORAGE_SLOTS = 3;
   private final AeternumSeasonsPlugin plugin;
   private final NamespacedKey trapKey;
   private final NamespacedKey usesKey;
   private final NamespacedKey invKey;
   private final NamespacedKey eggUidKey;
   private File faunaFile;
   private FileConfiguration fauna;
   private BukkitTask task;
   private BukkitTask maintenanceTask;
   private final Set<CreatureGeneratorService.TrapKey> knownTraps = new HashSet<>();
   private final Deque<CreatureGeneratorService.TrapKey> pendingTraps = new ArrayDeque<>();
   private final Set<CreatureGeneratorService.TrapKey> queuedTraps = new HashSet<>();
   private final Deque<CreatureGeneratorService.ChunkKey> discoveryQueue = new ArrayDeque<>();
   private final Set<CreatureGeneratorService.ChunkKey> queuedChunks = new HashSet<>();
   private List<CreatureGeneratorService.WeightedType> weightedTypes = List.of();
   private int weightedTotal;
   private static final long MAINTENANCE_BUDGET_NANOS = 1500000L;
   private static final int MAX_GENERATORS_PER_TICK = 8;

   public CreatureGeneratorService(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.trapKey = new NamespacedKey(plugin, "creature_trap");
      this.usesKey = new NamespacedKey(plugin, "creature_trap_uses");
      this.invKey = new NamespacedKey(plugin, "creature_trap_inv");
      this.eggUidKey = new NamespacedKey(plugin, "trap_egg_uid");
   }

   public void register() {
      this.loadFauna();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.registerRecipes();
      this.startTask();
      this.enqueueLoadedChunks();
   }

   public void unregister() {
      this.stopTask();
      HandlerList.unregisterAll(this);
      this.knownTraps.clear();
      this.pendingTraps.clear();
      this.queuedTraps.clear();
      this.discoveryQueue.clear();
      this.queuedChunks.clear();
   }

   public void reload() {
      this.loadFauna();
      this.registerRecipes();
      this.startTask();
      this.enqueueLoadedChunks();
   }

   private void loadFauna() {
      if (!this.plugin.getDataFolder().exists()) {
         this.plugin.getDataFolder().mkdirs();
      }

      this.faunaFile = new File(this.plugin.getDataFolder(), "fauna.yml");
      if (!this.faunaFile.exists()) {
         try {
            this.plugin.saveResource("fauna.yml", false);
         } catch (IllegalArgumentException ex) {
            try {
               this.faunaFile.createNewFile();
            } catch (IOException var3) {
            }
         }
      }

      this.fauna = YamlConfiguration.loadConfiguration(this.faunaFile);
      this.rebuildWeightedTypes();
   }

   private boolean enabled() {
      return this.fauna.getBoolean("creature_generator.enabled", true);
   }

   private int defaultUses() {
      return Math.max(1, this.fauna.getInt("creature_generator.uses", 10));
   }

   private int tickSeconds() {
      return Math.max(5, this.fauna.getInt("creature_generator.tick_seconds", 180));
   }

   private boolean requirePlayerNearby() {
      return this.fauna.getBoolean("creature_generator.require_player_nearby", true);
   }

   private int playerRange() {
      return Math.max(1, this.fauna.getInt("creature_generator.player_range", 26));
   }

   private List<EntityType> allowedTypes() {
      List<String> raw = this.fauna.getStringList("creature_generator.allowed");
      if (raw != null && !raw.isEmpty()) {
         List<EntityType> out = new ArrayList<>();

         for (String s : raw) {
            if (s != null) {
               try {
                  EntityType t = EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT));
                  if (t.isAlive() && t != EntityType.WITHER && t != EntityType.ENDER_DRAGON) {
                     out.add(t);
                  }
               } catch (Exception var6) {
               }
            }
         }

         if (out.isEmpty()) {
            out = List.of(EntityType.RABBIT, EntityType.FROG, EntityType.BEE, EntityType.PARROT);
         }

         return out;
      } else {
         return List.of(EntityType.RABBIT, EntityType.FROG, EntityType.BEE, EntityType.PARROT);
      }
   }

   private void registerRecipes() {
      if (this.enabled()) {
         if (this.fauna.getBoolean("creature_generator.crafting.generator", true)) {
            NamespacedKey key = new NamespacedKey(this.plugin, "creature_trap_item");

            try {
               Bukkit.removeRecipe(key);
            } catch (Throwable var4) {
            }

            ItemStack result = this.createTrapItem(this.defaultUses());
            ShapedRecipe r = new ShapedRecipe(key, result);
            r.shape(new String[]{"SIS", "ITI", "SIS"});
            r.setIngredient('S', Material.STRING);
            r.setIngredient('I', Material.IRON_INGOT);
            r.setIngredient('T', Material.STICK);
            Bukkit.addRecipe(r);
         }
      }
   }

   private EntityType pickWeightedType() {
      if (!this.weightedTypes.isEmpty() && this.weightedTotal > 0) {
         int r = ThreadLocalRandom.current().nextInt(this.weightedTotal);
         int acc = 0;

         for (CreatureGeneratorService.WeightedType entry : this.weightedTypes) {
            acc += entry.weight();
            if (r < acc) {
               return entry.type();
            }
         }

         return this.weightedTypes.get(this.weightedTypes.size() - 1).type();
      } else {
         List<EntityType> list = this.allowedTypes();
         return list.get(ThreadLocalRandom.current().nextInt(list.size()));
      }
   }

   private void rebuildWeightedTypes() {
      ConfigurationSection section = this.fauna.getConfigurationSection("creature_generator.allowed_weighted");
      if (section == null) {
         this.weightedTypes = List.of();
         this.weightedTotal = 0;
      } else {
         List<CreatureGeneratorService.WeightedType> entries = new ArrayList<>();
         int total = 0;

         for (String key : section.getKeys(false)) {
            EntityType type;
            try {
               type = EntityType.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
               continue;
            }

            if (type.isAlive() && type != EntityType.WITHER && type != EntityType.ENDER_DRAGON) {
               int weight = Math.max(0, section.getInt(key));
               if (weight > 0 && total <= Integer.MAX_VALUE - weight) {
                  entries.add(new CreatureGeneratorService.WeightedType(type, weight));
                  total += weight;
               }
            }
         }

         this.weightedTypes = List.copyOf(entries);
         this.weightedTotal = total;
      }
   }

   public ItemStack createTrapItem(int uses) {
      ItemStack it = new ItemStack(Material.SPAWNER, 1);
      ItemMeta meta = it.getItemMeta();
      if (meta == null) {
         return it;
      }

      meta.setDisplayName(color(this.plugin.lang.trServer("generator.item.name")));
      List<String> lore = new ArrayList<>();
      lore.add(color(this.plugin.lang.trServer("generator.item.lore1").replace("{uses}", String.valueOf(uses))));
      lore.add(color(this.plugin.lang.trServer("generator.item.lore2").replace("{mob}", "RANDOM")));
      lore.add(color(this.plugin.lang.trServer("generator.item.lore3")));
      meta.setLore(lore);
      meta.getPersistentDataContainer().set(this.trapKey, PersistentDataType.BYTE, (byte)1);
      meta.getPersistentDataContainer().set(this.usesKey, PersistentDataType.INTEGER, uses);
      it.setItemMeta(meta);
      return it;
   }

   private boolean isTrapItem(ItemStack it) {
      if (it != null && it.getType() == Material.SPAWNER) {
         ItemMeta meta = it.getItemMeta();
         return meta == null ? false : meta.getPersistentDataContainer().has(this.trapKey, PersistentDataType.BYTE);
      } else {
         return false;
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent e) {
      if (this.enabled()) {
         if (this.isTrapItem(e.getItemInHand())) {
            Block b = e.getBlockPlaced();
            if (b.getType() == Material.SPAWNER) {
               int uses = this.defaultUses();
               ItemMeta meta = e.getItemInHand().getItemMeta();
               if (meta != null) {
                  Integer u = (Integer)meta.getPersistentDataContainer().get(this.usesKey, PersistentDataType.INTEGER);
                  if (u != null) {
                     uses = Math.max(1, u);
                  }
               }

               BlockState st = b.getState();
               if (st instanceof TileState ts) {
                  PersistentDataContainer pdc = ts.getPersistentDataContainer();
                  pdc.set(this.trapKey, PersistentDataType.BYTE, (byte)1);
                  pdc.set(this.usesKey, PersistentDataType.INTEGER, uses);
                  this.saveInv(pdc, new ItemStack[3]);
                  ts.update(true, false);
                  if (st instanceof CreatureSpawner sp) {
                     try {
                        sp.setSpawnedType(EntityType.AREA_EFFECT_CLOUD);
                     } catch (Exception ex) {
                        sp.setSpawnedType(EntityType.BAT);
                     }

                     sp.setSpawnCount(0);
                     sp.setSpawnRange(0);
                     sp.setRequiredPlayerRange(0);
                     sp.setMaxNearbyEntities(0);
                     sp.setMaxSpawnDelay(Integer.MAX_VALUE);
                     sp.setMinSpawnDelay(Integer.MAX_VALUE);
                     sp.setDelay(Integer.MAX_VALUE);
                     sp.update(true, false);
                  }

                  this.knownTraps.add(CreatureGeneratorService.TrapKey.of(b));
                  e.getPlayer().sendMessage(color(this.plugin.lang.tr(e.getPlayer(), "generator.msg.placed").replace("{uses}", String.valueOf(uses))));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPrepareCraft(PrepareItemCraftEvent e) {
      if (e.getInventory() instanceof CraftingInventory ci) {
         ItemStack var6 = ci.getResult();
         if (this.isTrapItem(var6)) {
            if (!ci.getViewers().isEmpty()) {
               if (ci.getViewers().get(0) instanceof Player p) {
                  ci.setResult(this.createTrapItem(this.defaultUses(), p));
               }
            }
         }
      }
   }

   public ItemStack createTrapItem(int uses, Player p) {
      ItemStack it = new ItemStack(Material.SPAWNER, 1);
      ItemMeta meta = it.getItemMeta();
      if (meta == null) {
         return it;
      }

      meta.setDisplayName(color(this.plugin.lang.tr(p, "generator.item.name")));
      List<String> lore = new ArrayList<>();
      lore.add(color(this.plugin.lang.tr(p, "generator.item.lore1").replace("{uses}", String.valueOf(uses))));
      lore.add(color(this.plugin.lang.tr(p, "generator.item.lore2").replace("{mob}", "RANDOM")));
      lore.add(color(this.plugin.lang.tr(p, "generator.item.lore3")));
      meta.setLore(lore);
      meta.getPersistentDataContainer().set(this.trapKey, PersistentDataType.BYTE, (byte)1);
      meta.getPersistentDataContainer().set(this.usesKey, PersistentDataType.INTEGER, uses);
      it.setItemMeta(meta);
      return it;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockExp(BlockExpEvent e) {
      Block b = e.getBlock();
      if (b.getType() == Material.SPAWNER) {
         if (this.isTrapBlock(b)) {
            e.setExpToDrop(0);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onSpawnerSpawn(SpawnerSpawnEvent e) {
      Block b = e.getSpawner().getBlock();
      if (b.getType() == Material.SPAWNER) {
         if (this.isTrapBlock(b)) {
            e.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent e) {
      Block b = e.getBlock();
      if (b.getType() == Material.SPAWNER) {
         if (this.isTrapBlock(b)) {
            this.knownTraps.remove(CreatureGeneratorService.TrapKey.of(b));
            e.setDropItems(false);
            e.setExpToDrop(0);
            TileState ts = (TileState)b.getState();
            PersistentDataContainer pdc = ts.getPersistentDataContainer();
            ItemStack[] inv = this.loadInv(pdc);
            this.dropStored(b.getLocation(), inv);
            int uses = this.getUses(pdc);
            uses = Math.max(0, uses - 1);
            if (uses <= 0) {
               e.getPlayer().sendMessage(color(this.plugin.lang.tr(e.getPlayer(), "generator.msg.broken")));
            } else {
               b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), this.createTrapItem(uses, e.getPlayer()));
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent e) {
      if (this.enabled()) {
         if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getClickedBlock() != null) {
               Block b = e.getClickedBlock();
               if (b.getType() == Material.SPAWNER) {
                  if (this.isTrapBlock(b)) {
                     ItemStack hand = e.getItem();
                     if (hand != null && hand.getType().name().endsWith("_SPAWN_EGG")) {
                        e.setCancelled(true);
                        e.getPlayer().sendMessage(color(this.plugin.lang.tr(e.getPlayer(), "generator.msg.not_allowed")));
                     } else {
                        e.setCancelled(true);
                        this.openGui(e.getPlayer(), b);
                     }
                  }
               }
            }
         }
      }
   }

   private void openGui(Player p, Block b) {
      TileState ts = (TileState)b.getState();
      PersistentDataContainer pdc = ts.getPersistentDataContainer();
      Inventory inv = Bukkit.createInventory(new CreatureGeneratorService.TrapHolder(b.getLocation()), 9, color(this.plugin.lang.tr(p, "generator.gui.title")));
      ItemStack[] stored = this.loadInv(pdc);

      for (int i = 0; i < 3; i++) {
         inv.setItem(i, stored[i]);
      }

      ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta fm = filler.getItemMeta();
      if (fm != null) {
         fm.setDisplayName(" ");
         filler.setItemMeta(fm);
      }

      for (int i = 3; i < 9; i++) {
         inv.setItem(i, filler);
      }

      p.openInventory(inv);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onGuiClick(InventoryClickEvent e) {
      if (e.getView().getTopInventory().getHolder() instanceof CreatureGeneratorService.TrapHolder) {
         if (e.isShiftClick() && e.getClickedInventory() != null && e.getClickedInventory().getType() == InventoryType.PLAYER) {
            e.setCancelled(true);
         } else if (e.getAction() != InventoryAction.HOTBAR_SWAP
            && e.getAction() != InventoryAction.HOTBAR_MOVE_AND_READD
            && e.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
            int raw = e.getRawSlot();
            if (raw >= 0) {
               if (raw < 9) {
                  if (raw >= 3) {
                     e.setCancelled(true);
                     return;
                  }

                  ItemStack cursor = e.getCursor();
                  if (cursor != null && cursor.getType() != Material.AIR) {
                     e.setCancelled(true);
                  }
               }
            }
         } else {
            e.setCancelled(true);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onGuiDrag(InventoryDragEvent e) {
      if (e.getView().getTopInventory().getHolder() instanceof CreatureGeneratorService.TrapHolder) {
         for (int slot : e.getRawSlots()) {
            if (slot >= 0 && slot < 9) {
               e.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onGuiClose(InventoryCloseEvent e) {
      if (e.getInventory().getHolder() instanceof CreatureGeneratorService.TrapHolder h) {
         Block b = h.loc.getBlock();
         if (b.getType() == Material.SPAWNER) {
            if (this.isTrapBlock(b)) {
               TileState ts = (TileState)b.getState();
               PersistentDataContainer pdc = ts.getPersistentDataContainer();
               ItemStack[] items = new ItemStack[3];

               for (int i = 0; i < 3; i++) {
                  ItemStack it = e.getInventory().getItem(i);
                  items[i] = it != null && it.getType() != Material.AIR ? it.clone() : null;
               }

               this.saveInv(pdc, items);
               ts.update(true, false);
            }
         }
      }
   }

   private void startTask() {
      this.stopTask();
      if (this.enabled()) {
         long period = this.tickSeconds() * 20L;
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::enqueueGenerationCycle, period, period);
         this.maintenanceTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::maintenanceTick, 1L, 1L);
      }
   }

   private void stopTask() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      if (this.maintenanceTask != null) {
         this.maintenanceTask.cancel();
         this.maintenanceTask = null;
      }

      this.pendingTraps.clear();
      this.queuedTraps.clear();
      this.discoveryQueue.clear();
      this.queuedChunks.clear();
   }

   private void enqueueGenerationCycle() {
      if (this.enabled()) {
         for (CreatureGeneratorService.TrapKey key : this.knownTraps) {
            if (this.queuedTraps.add(key)) {
               this.pendingTraps.addLast(key);
            }
         }
      }
   }

   private void maintenanceTick() {
      if (this.enabled()) {
         long deadline = System.nanoTime() + 1500000L;

         for (int processed = 0; processed < 8 && !this.pendingTraps.isEmpty() && System.nanoTime() < deadline; processed++) {
            CreatureGeneratorService.TrapKey key = this.pendingTraps.pollFirst();
            this.queuedTraps.remove(key);
            this.processTrap(key);
         }

         if (!this.discoveryQueue.isEmpty() && System.nanoTime() < deadline) {
            CreatureGeneratorService.ChunkKey key = this.discoveryQueue.pollFirst();
            this.queuedChunks.remove(key);
            this.discoverChunk(key);
         }
      }
   }

   private void enqueueLoadedChunks() {
      for (World world : Bukkit.getWorlds()) {
         for (Chunk chunk : world.getLoadedChunks()) {
            this.enqueueChunk(chunk);
         }
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
      if (this.enabled()) {
         this.enqueueChunk(event.getChunk());
      }
   }

   private void enqueueChunk(Chunk chunk) {
      CreatureGeneratorService.ChunkKey key = new CreatureGeneratorService.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
      if (this.queuedChunks.add(key)) {
         this.discoveryQueue.addLast(key);
      }
   }

   private void discoverChunk(CreatureGeneratorService.ChunkKey key) {
      World world = Bukkit.getWorld(key.worldId());
      if (world != null && world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
         Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());

         for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CreatureSpawner
               && state instanceof TileState tile
               && tile.getPersistentDataContainer().has(this.trapKey, PersistentDataType.BYTE)) {
               this.knownTraps.add(CreatureGeneratorService.TrapKey.of(tile.getBlock()));
            }
         }
      }
   }

   private void processTrap(CreatureGeneratorService.TrapKey key) {
      World world = Bukkit.getWorld(key.worldId());
      if (world == null) {
         this.knownTraps.remove(key);
      } else if (world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
         Block block = world.getBlockAt(key.x(), key.y(), key.z());
         if (block.getType() != Material.SPAWNER) {
            this.knownTraps.remove(key);
         } else {
            BlockState state = block.getState();
            if (state instanceof CreatureSpawner && state instanceof TileState tile) {
               PersistentDataContainer pdc = tile.getPersistentDataContainer();
               if (!pdc.has(this.trapKey, PersistentDataType.BYTE)) {
                  this.knownTraps.remove(key);
               } else {
                  int range = this.playerRange();
                  int rangeSq = range * range;
                  if (this.requirePlayerNearby()) {
                     boolean near = false;
                     double centerX = key.x() + 0.5;
                     double centerY = key.y() + 0.5;
                     double centerZ = key.z() + 0.5;

                     for (Player player : world.getPlayers()) {
                        Location location = player.getLocation();
                        double dx = location.getX() - centerX;
                        double dy = location.getY() - centerY;
                        double dz = location.getZ() - centerZ;
                        if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                           near = true;
                           break;
                        }
                     }

                     if (!near) {
                        return;
                     }
                  }

                  int uses = this.getUses(pdc);
                  if (uses <= 0) {
                     this.breakTrap(block);
                  } else {
                     ItemStack[] inventory = this.loadInv(pdc);
                     int slot = this.firstEmpty(inventory);
                     if (slot != -1) {
                        ItemStack egg = this.createVanillaSpawnEggNoStack(this.pickWeightedType());
                        if (egg != null) {
                           inventory[slot] = egg;
                           this.saveInv(pdc, inventory);
                           pdc.set(this.usesKey, PersistentDataType.INTEGER, --uses);
                           tile.update(true, false);
                           if (uses <= 0) {
                              this.breakTrap(block);
                           }
                        }
                     }
                  }
               }
            } else {
               this.knownTraps.remove(key);
            }
         }
      }
   }

   private void tickAllLoadedTraps() {
      if (this.enabled()) {
         List<EntityType> pool = this.allowedTypes();
         if (!pool.isEmpty()) {
            int range = this.playerRange();
            int rangeSq = range * range;

            for (World w : Bukkit.getWorlds()) {
               for (Chunk c : w.getLoadedChunks()) {
                  for (BlockState st : c.getTileEntities()) {
                     if (st instanceof TileState ts && st instanceof CreatureSpawner) {
                        PersistentDataContainer pdc = ts.getPersistentDataContainer();
                        if (pdc.has(this.trapKey, PersistentDataType.BYTE)) {
                           Block b = ts.getBlock();
                           if (b.getType() == Material.SPAWNER) {
                              if (this.requirePlayerNearby()) {
                                 boolean near = false;
                                 Location at = b.getLocation().add(0.5, 0.5, 0.5);

                                 for (Player p : w.getPlayers()) {
                                    if (p.getLocation().distanceSquared(at) <= rangeSq) {
                                       near = true;
                                       break;
                                    }
                                 }

                                 if (!near) {
                                    continue;
                                 }
                              }

                              int uses = this.getUses(pdc);
                              if (uses <= 0) {
                                 this.breakTrap(b);
                              } else {
                                 ItemStack[] inv = this.loadInv(pdc);
                                 int slot = this.firstEmpty(inv);
                                 if (slot != -1) {
                                    EntityType type = this.pickWeightedType();
                                    ItemStack egg = this.createVanillaSpawnEggNoStack(type);
                                    if (egg != null) {
                                       inv[slot] = egg;
                                       this.saveInv(pdc, inv);
                                       pdc.set(this.usesKey, PersistentDataType.INTEGER, --uses);
                                       ts.update(true, false);
                                       if (uses <= 0) {
                                          this.breakTrap(b);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private int firstEmpty(ItemStack[] inv) {
      for (int i = 0; i < 3; i++) {
         ItemStack it = inv[i];
         if (it == null || it.getType() == Material.AIR) {
            return i;
         }
      }

      return -1;
   }

   private ItemStack createVanillaSpawnEggNoStack(EntityType type) {
      Material mat = Material.matchMaterial(type.name() + "_SPAWN_EGG");
      if (mat == null) {
         return null;
      }

      ItemStack it = new ItemStack(mat, 1);
      ItemMeta meta = it.getItemMeta();
      if (meta == null) {
         return it;
      }

      meta.getPersistentDataContainer().set(this.eggUidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
      it.setItemMeta(meta);
      return it;
   }

   private void breakTrap(Block b) {
      if (b.getType() == Material.SPAWNER) {
         if (this.isTrapBlock(b)) {
            this.knownTraps.remove(CreatureGeneratorService.TrapKey.of(b));
            TileState ts = (TileState)b.getState();
            PersistentDataContainer pdc = ts.getPersistentDataContainer();
            ItemStack[] inv = this.loadInv(pdc);
            this.dropStored(b.getLocation(), inv);
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.7F, 1.6F);
            b.setType(Material.AIR);
            int range = Math.max(8, this.playerRange());
            int rangeSq = range * range;
            Location at = b.getLocation().add(0.5, 0.5, 0.5);

            for (Player p : b.getWorld().getPlayers()) {
               if (p.getLocation().distanceSquared(at) <= rangeSq) {
                  p.sendMessage(color(this.plugin.lang.tr(p, "generator.msg.broken")));
               }
            }
         }
      }
   }

   private void dropStored(Location loc, ItemStack[] inv) {
      World w = loc.getWorld();
      if (w != null && inv != null) {
         Location drop = loc.clone().add(0.5, 0.5, 0.5);

         for (int i = 0; i < 3; i++) {
            ItemStack it = inv[i];
            if (it != null && it.getType() != Material.AIR) {
               w.dropItemNaturally(drop, it);
            }
         }
      }
   }

   private boolean isTrapBlock(Block b) {
      return b.getState() instanceof TileState ts ? ts.getPersistentDataContainer().has(this.trapKey, PersistentDataType.BYTE) : false;
   }

   private int getUses(PersistentDataContainer pdc) {
      Integer v = (Integer)pdc.get(this.usesKey, PersistentDataType.INTEGER);
      return v == null ? this.defaultUses() : Math.max(0, v);
   }

   private ItemStack[] loadInv(PersistentDataContainer pdc) {
      byte[] raw = (byte[])pdc.get(this.invKey, PersistentDataType.BYTE_ARRAY);
      if (raw != null && raw.length != 0) {
         try {
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(raw));

            label48: {
               ItemStack[] var11;
               try {
                  if (!(in.readObject() instanceof ItemStack[] arr)) {
                     break label48;
                  }

                  ItemStack[] fixed = new ItemStack[3];

                  for (int i = 0; i < 3; i++) {
                     fixed[i] = i < arr.length ? arr[i] : null;
                  }

                  var11 = fixed;
               } catch (Throwable var9) {
                  try {
                     in.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }

                  throw var9;
               }

               in.close();
               return var11;
            }

            in.close();
         } catch (Exception var10) {
         }

         return new ItemStack[3];
      } else {
         return new ItemStack[3];
      }
   }

   private void saveInv(PersistentDataContainer pdc, ItemStack[] items) {
      ItemStack[] fixed = new ItemStack[3];

      for (int i = 0; i < 3; i++) {
         fixed[i] = items != null && i < items.length ? items[i] : null;
      }

      try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
         BukkitObjectOutputStream out = new BukkitObjectOutputStream(bout);

         try {
            out.writeObject(fixed);
            out.flush();
            pdc.set(this.invKey, PersistentDataType.BYTE_ARRAY, bout.toByteArray());
         } catch (Throwable var10) {
            try {
               out.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }

            throw var10;
         }

         out.close();
      } catch (Exception ex) {
         this.plugin.getLogger().warning("[CreatureTrap] Failed to save inv: " + ex.getMessage());
      }
   }

   private static String color(String s) {
      return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
   }

   private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
   }

   private static final class TrapHolder implements InventoryHolder {
      final Location loc;

      TrapHolder(Location loc) {
         this.loc = loc;
      }

      public Inventory getInventory() {
         return null;
      }
   }

   private record TrapKey(UUID worldId, int x, int y, int z) {
      private static CreatureGeneratorService.TrapKey of(Block block) {
         return new CreatureGeneratorService.TrapKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
      }
   }

   private record WeightedType(EntityType type, int weight) {
   }
}
