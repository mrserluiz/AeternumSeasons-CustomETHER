package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.calendar.SeasonService;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class SeasonalFloraController implements Listener, Runnable {
   private final AeternumSeasonsPlugin plugin;
   private final SeasonService seasons;
   private boolean enabled;
   private int tickPeriod;
   private int innerRadiusChunksCfg;
   private int outerRadiusChunksCfg;
   private int budgetPerTick;
   private int maxChunksPerTick;
   private boolean protectPlayerPlaced;
   private int surfaceScanDepth;
   private boolean allowInView;
   private BukkitTask task;
   private final Map<String, SeasonalFloraController.FloraRule> rules = new LinkedHashMap<>();
   private final Map<Material, List<SeasonalFloraController.FloraRule>> rulesByMaterial = new EnumMap<>(Material.class);
   private final Map<Long, Set<Long>> playerPlaced = new ConcurrentHashMap<>();
   private final Map<Long, Set<Long>> pluginPlaced = new ConcurrentHashMap<>();
   private Set<String> disabledSeasonFxWorlds = Set.of();
   private static final int OFFSETS_STEP = 1;
   private static final int SAMPLES_PER_CHUNK = 28;
   private static final Object HM_MOTION_NO_LEAVES;
   private static final Method M_GET_HIGHEST_Y_HM;

   public SeasonalFloraController(AeternumSeasonsPlugin plugin, SeasonService seasons) {
      this.plugin = plugin;
      this.seasons = seasons;
      this.reloadFromConfig();
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      if (this.task != null) {
         this.task.cancel();
      }

      if (this.enabled) {
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this, 40L, this.tickPeriod);
      }
   }

   public void unregister() {
      if (this.task != null) {
         this.task.cancel();
      }

      HandlerList.unregisterAll(this);
      this.rules.clear();
      this.rulesByMaterial.clear();
      this.playerPlaced.clear();
      this.pluginPlaced.clear();
   }

   public void reloadFromConfig() {
      this.enabled = this.plugin.cfg.climate.getBoolean("seasonal_flora.enabled", true);
      this.tickPeriod = Math.max(1, this.plugin.cfg.climate.getInt("seasonal_flora.tick_period_ticks", 10));
      this.innerRadiusChunksCfg = Math.max(0, this.plugin.cfg.climate.getInt("seasonal_flora.inner_radius_chunks", 0));
      this.outerRadiusChunksCfg = Math.max(1, this.plugin.cfg.climate.getInt("seasonal_flora.outer_radius_chunks", 8));
      this.budgetPerTick = Math.max(1, this.plugin.cfg.climate.getInt("seasonal_flora.budget_blocks_per_tick", 120));
      this.maxChunksPerTick = Math.max(1, this.plugin.cfg.climate.getInt("seasonal_flora.max_chunks_per_tick", 8));
      this.protectPlayerPlaced = this.plugin.cfg.climate.getBoolean("seasonal_flora.protect_player_placed", true);
      this.allowInView = this.plugin.cfg.climate.getBoolean("seasonal_flora.allow_in_view", true);
      this.surfaceScanDepth = Math.max(1, this.plugin.cfg.climate.getInt("seasonal_flora.surface_scan_depth", 8));
      List<String> list = this.plugin.getConfig().getStringList("worlds.disabled_season_fx");
      this.disabledSeasonFxWorlds = (list == null ? List.of() : list)
         .stream()
         .filter(Objects::nonNull)
         .map(s -> s.trim().toLowerCase(Locale.ROOT))
         .filter(s -> !s.isEmpty())
         .collect(Collectors.toUnmodifiableSet());
      this.rules.clear();
      this.rulesByMaterial.clear();
      ConfigurationSection sec = this.plugin.cfg.climate.getConfigurationSection("seasonal_flora.rules");
      if (sec == null) {
         this.plugin.getLogger().info("[SeasonalFlora] No rules found at seasonal_flora.rules");
      } else {
         int loaded = 0;
         int purgeOnly = 0;

         for (String id : sec.getKeys(false)) {
            ConfigurationSection rsec = sec.getConfigurationSection(id);
            if (rsec != null) {
               SeasonalFloraController.FloraRule rule = SeasonalFloraController.FloraRule.fromConfig(id, rsec, this.plugin);
               if (rule.enabled || rule.purgeWhenDisabled) {
                  if (rule.blocks.isEmpty()) {
                     this.plugin.getLogger().warning("[SeasonalFlora] rule '" + id + "' has no blocks, skipped.");
                  } else {
                     this.rules.put(id, rule);
                     loaded++;
                     if (!rule.enabled && rule.purgeWhenDisabled) {
                        purgeOnly++;
                     }

                     for (Material m : rule.blocks) {
                        this.rulesByMaterial.computeIfAbsent(m, k -> new ArrayList<>()).add(rule);
                     }
                  }
               }
            }
         }

         this.plugin.getLogger().info("[SeasonalFlora] Loaded " + loaded + " flora rules (" + purgeOnly + " purge-only).");
      }
   }

   private boolean isWorldFxEnabled(World w) {
      return w == null ? false : !this.disabledSeasonFxWorlds.contains(w.getName().toLowerCase(Locale.ROOT));
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent e) {
      if (this.enabled && this.protectPlayerPlaced) {
         if (this.isWorldFxEnabled(e.getBlock().getWorld())) {
            Material type = e.getBlockPlaced().getType();
            if (this.rulesByMaterial.containsKey(type)) {
               Block b = e.getBlockPlaced();
               this.markPlayerPlaced(b);
               if (this.isDoublePlant(type)) {
                  Block up = b.getRelative(BlockFace.UP);
                  if (up.getType() == type) {
                     this.markPlayerPlaced(up);
                  }
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockSpread(BlockSpreadEvent e) {
      if (this.enabled) {
         Material newType = e.getNewState().getType();
         List<SeasonalFloraController.FloraRule> list = this.rulesByMaterial.get(newType);
         if (list != null) {
            Location nl = e.getNewState().getLocation();
            Season s = this.seasons.getStateCopy(nl.getWorld()).season;
            Biome nb = nl.getWorld().getBiome(nl.getBlockX(), nl.getBlockY(), nl.getBlockZ());

            for (SeasonalFloraController.FloraRule r : list) {
               if (r.enabled && (!r.spreadSeasons.contains(s) || !r.isBiomeAllowed(nb))) {
                  e.setCancelled(true);
                  return;
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent e) {
      if (this.enabled && this.protectPlayerPlaced) {
         Material type = e.getBlock().getType();
         if (this.rulesByMaterial.containsKey(type)) {
            Block b = e.getBlock();
            this.unmarkPlayerPlaced(b);
            if (this.isDoublePlant(type)) {
               Block up = b.getRelative(BlockFace.UP);
               if (up.getType() == type) {
                  this.unmarkPlayerPlaced(up);
               }

               Block down = b.getRelative(BlockFace.DOWN);
               if (down.getType() == type) {
                  this.unmarkPlayerPlaced(down);
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockFertilize(BlockFertilizeEvent e) {
      if (this.enabled) {
         for (BlockState bs : e.getBlocks()) {
            List<SeasonalFloraController.FloraRule> list = this.rulesByMaterial.get(bs.getType());
            if (list != null) {
               Location l = bs.getLocation();
               Season s = this.seasons.getStateCopy(l.getWorld()).season;
               Biome b = l.getWorld().getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());

               for (SeasonalFloraController.FloraRule r : list) {
                  if (r.enabled && (!r.spreadSeasons.contains(s) || !r.isBiomeAllowed(b))) {
                     e.setCancelled(true);
                     return;
                  }
               }
            }
         }

         if (this.protectPlayerPlaced && e.getPlayer() != null) {
            for (BlockState bs : e.getBlocks()) {
               Block placed = bs.getBlock();
               if (this.rulesByMaterial.containsKey(placed.getType())) {
                  this.markPlayerPlaced(placed);
                  if (this.isDoublePlant(placed.getType())) {
                     Block up = placed.getRelative(BlockFace.UP);
                     if (up.getType() == placed.getType()) {
                        this.markPlayerPlaced(up);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockGrow(BlockGrowEvent e) {
      if (this.enabled) {
         Material type = e.getNewState().getType();
         List<SeasonalFloraController.FloraRule> list = this.rulesByMaterial.get(type);
         if (list != null) {
            Location l = e.getBlock().getLocation();
            Season s = this.seasons.getStateCopy(l.getWorld()).season;
            Biome b = l.getWorld().getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());

            for (SeasonalFloraController.FloraRule r : list) {
               if (r.enabled && (!r.spreadSeasons.contains(s) || !r.isBiomeAllowed(b))) {
                  e.setCancelled(true);
                  return;
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPlayerInteract(PlayerInteractEvent e) {
      if (this.enabled) {
         if (this.isWorldFxEnabled(e.getPlayer().getWorld())) {
            if (e.getHand() == EquipmentSlot.HAND) {
               if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                  ItemStack it = e.getItem();
                  if (it != null && it.getType() == Material.BONE_MEAL) {
                     Block b = e.getClickedBlock();
                     if (b != null) {
                        List<SeasonalFloraController.FloraRule> list = this.rulesByMaterial.get(b.getType());
                        if (list != null) {
                           Season s = this.seasons.getStateCopy(b.getWorld()).season;

                           for (SeasonalFloraController.FloraRule r : list) {
                              if (r.enabled && !r.spreadSeasons.contains(s)) {
                                 e.setCancelled(true);
                                 return;
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

   @Override
   public void run() {
      if (this.enabled && !this.rules.isEmpty()) {
         int budget = this.budgetPerTick;
         if (budget > 0) {
            Set<Long> processedThisTick = new HashSet<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
               if (budget <= 0 || processedThisTick.size() >= this.maxChunksPerTick) {
                  break;
               }

               World w = p.getWorld();
               if (this.isWorldFxEnabled(w) && w.getEnvironment() == Environment.NORMAL) {
                  CalendarState st = this.seasons.getStateCopy(w);
                  Season season = st.season;
                  int view = Bukkit.getViewDistance();
                  int outer = Math.min(Math.max(this.outerRadiusChunksCfg, 1), view);
                  int inner = Math.min(this.innerRadiusChunksCfg, Math.max(0, outer - 1));
                  Location loc = p.getLocation();
                  int pcx = loc.getBlockX() >> 4;
                  int pcz = loc.getBlockZ() >> 4;

                  for (int dist = inner; dist <= outer && budget > 0 && processedThisTick.size() < this.maxChunksPerTick; dist++) {
                     for (int dx = -dist; dx <= dist && budget > 0 && processedThisTick.size() < this.maxChunksPerTick; dx++) {
                        for (int dz = -dist; dz <= dist && budget > 0 && processedThisTick.size() < this.maxChunksPerTick; dz++) {
                           if (Math.max(Math.abs(dx), Math.abs(dz)) == dist) {
                              int cx = pcx + dx;
                              int cz = pcz + dz;
                              if (w.isChunkLoaded(cx, cz)) {
                                 long ck = this.chunkKey(w, cx, cz);
                                 if (processedThisTick.add(ck)) {
                                    Chunk ch = w.getChunkAt(cx, cz);
                                    budget = this.processChunk(ch, season, budget);
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

   private int processChunk(Chunk ch, Season season, int budget) {
      if (budget <= 0) {
         return 0;
      }

      World w = ch.getWorld();
      int bx = ch.getX() << 4;
      int bz = ch.getZ() << 4;
      ThreadLocalRandom rnd = ThreadLocalRandom.current();

      for (int i = 0; i < 28 && budget > 0; i++) {
         int x = bx + rnd.nextInt(16);
         int z = bz + rnd.nextInt(16);
         int surfaceY = this.getSurfaceY(w, x, z);
         Block b = this.findPurgeCandidate(w, x, surfaceY, z, this.surfaceScanDepth);
         if (b != null) {
            Material type = b.getType();
            List<SeasonalFloraController.FloraRule> list = this.rulesByMaterial.get(type);
            if (list != null) {
               for (SeasonalFloraController.FloraRule r : list) {
                  boolean purgeSeason = r.enabled && r.removeSeasons.contains(season);
                  boolean purgeDisabled = !r.enabled && r.purgeWhenDisabled;
                  if ((purgeSeason || purgeDisabled) && !this.isProtectedByPlayer(b)) {
                     boolean isPlugin = this.isPlacedByPlugin(b);
                     boolean canPurge = r.purgePluginPlaced && isPlugin || r.purgeNatural && !isPlugin;
                     if (canPurge) {
                        Material replace = r.replaceWith != null ? r.replaceWith : Material.AIR;
                        int removed = this.purgeBlockRespectingShape(b, replace);
                        if (removed > 0) {
                           budget -= removed;
                        }
                        break;
                     }
                  }
               }
            }
         }
      }

      if (budget <= 0) {
         return budget;
      }

      for (SeasonalFloraController.FloraRule r : this.rules.values()) {
         if (budget <= 0) {
            break;
         }

         if (r.enabled && r.restoreSeasons.contains(season) && !(rnd.nextDouble() > r.restoreChance)) {
            if (r.maxPerChunk > 0) {
               int existing = this.countRuleBlocksInChunk(ch, r.blocks, r.maxPerChunk);
               if (existing >= r.maxPerChunk) {
                  continue;
               }
            }

            for (int tries = 0; tries < r.restoreTriesPerChunk && budget > 0; tries++) {
               int x = bx + rnd.nextInt(16);
               int z = bz + rnd.nextInt(16);
               int y = w.getHighestBlockYAt(x, z);
               Block top = w.getBlockAt(x, y, z);
               if (!top.isLiquid()) {
                  Block placeAt = top.getType().isAir() ? top : top.getRelative(BlockFace.UP);
                  if (placeAt.getType().isAir() && !this.isProtectedByPlayer(placeAt)) {
                     Biome biome = w.getBiome(x, placeAt.getY(), z);
                     if (r.isBiomeAllowed(biome)) {
                        int light = placeAt.getLightLevel();
                        if (light >= r.minLight && light <= r.maxLight) {
                           Block ground = placeAt.getRelative(BlockFace.DOWN);
                           if ((!r.requireSolidGround || ground.getType().isSolid() && (r.groundBlocks.isEmpty() || r.groundBlocks.contains(ground.getType())))
                              && (!r.forbidCanopy || this.isOpenSky(placeAt, r.canopyCheckHeight))
                              && (r.minDistanceBlocks <= 0 || !this.hasNearbyRuleBlock(placeAt, r.blocks, r.minDistanceBlocks))) {
                              boolean placed = this.placeRuleBlock(r, placeAt, rnd);
                              if (placed) {
                                 budget--;
                                 break;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return budget;
   }

   private int countRuleBlocksInChunk(Chunk ch, List<Material> mats, int cap) {
      World w = ch.getWorld();
      int bx = ch.getX() << 4;
      int bz = ch.getZ() << 4;
      int count = 0;

      for (int dx = 0; dx < 16; dx++) {
         for (int dz = 0; dz < 16; dz++) {
            int x = bx + dx;
            int z = bz + dz;
            Block b = this.findRuleBlockInColumn(w, x, z, mats);
            if (b != null && mats.contains(b.getType())) {
               if (++count >= cap) {
                  return count;
               }
            }
         }
      }

      return count;
   }

   private boolean hasNearbyRuleBlock(Block center, List<Material> mats, int radius) {
      World w = center.getWorld();
      int cx = center.getX();
      int cy = center.getY();
      int cz = center.getZ();
      int r = Math.min(radius, 8);

      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz <= r * r) {
               for (int dy = -1; dy <= 1; dy++) {
                  Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                  if (mats.contains(b.getType())) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean placeRuleBlock(SeasonalFloraController.FloraRule r, Block placeAt, ThreadLocalRandom rnd) {
      Material toPlace = r.blocks.size() == 1 ? r.blocks.get(0) : r.blocks.get(rnd.nextInt(r.blocks.size()));
      if (r.requiresAttachment && toPlace == Material.COCOA) {
         boolean ok = this.placeCocoa(r, placeAt, rnd);
         if (ok) {
            this.markPluginPlaced(placeAt);
         }

         return ok;
      } else if (r.doublePlant && this.isDoublePlant(toPlace)) {
         Block up = placeAt.getRelative(BlockFace.UP);
         if (!up.getType().isAir()) {
            return false;
         }

         this.placeDoublePlant(placeAt, toPlace);
         this.markPluginPlaced(placeAt);
         this.markPluginPlaced(up);
         return true;
      } else {
         placeAt.setType(toPlace, false);
         this.markPluginPlaced(placeAt);
         return true;
      }
   }

   private boolean placeCocoa(SeasonalFloraController.FloraRule r, Block placeAt, ThreadLocalRandom rnd) {
      List<BlockFace> faces = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
      Collections.shuffle(faces, rnd);

      for (BlockFace face : faces) {
         Block attached = placeAt.getRelative(face);
         if (r.attachBlocks.isEmpty() || r.attachBlocks.contains(attached.getType())) {
            placeAt.setType(Material.COCOA, false);
            if (placeAt.getBlockData() instanceof Cocoa cocoa) {
               cocoa.setFacing(face.getOppositeFace());
               cocoa.setAge(0);
               placeAt.setBlockData(cocoa, false);
            }

            return true;
         }
      }

      return false;
   }

   private void placeDoublePlant(Block lower, Material mat) {
      lower.setType(mat, false);
      Block upper = lower.getRelative(BlockFace.UP);
      upper.setType(mat, false);
      BlockData lowerData = lower.getBlockData();
      BlockData upperData = upper.getBlockData();
      if (lowerData instanceof Bisected bl && upperData instanceof Bisected bu) {
         bl.setHalf(Half.BOTTOM);
         bu.setHalf(Half.TOP);
         lower.setBlockData(bl, false);
         upper.setBlockData(bu, false);
      }
   }

   private boolean isOpenSky(Block placeAt, int height) {
      Block b = placeAt;

      for (int i = 0; i < height; i++) {
         b = b.getRelative(BlockFace.UP);
         Material t = b.getType();
         if (!t.isAir() && !t.isTransparent()) {
            return false;
         }
      }

      return true;
   }

   private boolean isDoublePlant(Material m) {
      String n = m.name();
      return n.equals("SUNFLOWER") || n.equals("ROSE_BUSH") || n.equals("LILAC") || n.equals("PEONY");
   }

   private boolean isBambooLike(Material m) {
      return m == Material.BAMBOO || m == Material.BAMBOO_SAPLING;
   }

   private boolean hasAnyMarkInBambooColumn(World w, int x, int y, int z, Set<Long> marks) {
      if (marks != null && !marks.isEmpty()) {
         int DOWN_CAP = 32;
         int UP_CAP = 48;
         int baseY = y;

         for (int i = 0; i < 32; i++) {
            int ny = y - i;
            if (ny < w.getMinHeight()) {
               break;
            }

            Material t = w.getBlockAt(x, ny, z).getType();
            if (!this.isBambooLike(t)) {
               break;
            }

            baseY = ny;
         }

         for (int i = 0; i < 48; i++) {
            int ny = baseY + i;
            if (ny >= w.getMaxHeight()) {
               break;
            }

            Material t = w.getBlockAt(x, ny, z).getType();
            if (!this.isBambooLike(t)) {
               break;
            }

            long bk = this.blockKey(w, x, ny, z);
            if (marks.contains(bk)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private int purgeBambooColumn(Block any, Material replaceWith) {
      World w = any.getWorld();
      int x = any.getX();
      int z = any.getZ();
      int y = any.getY();
      if (!this.isBambooLike(any.getType())) {
         return 0;
      }

      int DOWN_CAP = 32;
      int UP_CAP = 48;
      int baseY = y;

      for (int i = 0; i < 32; i++) {
         int ny = y - i;
         if (ny < w.getMinHeight()) {
            break;
         }

         Material t = w.getBlockAt(x, ny, z).getType();
         if (!this.isBambooLike(t)) {
            break;
         }

         baseY = ny;
      }

      int changed = 0;

      for (int i = 0; i < 48; i++) {
         int ny = baseY + i;
         if (ny >= w.getMaxHeight()) {
            break;
         }

         Block b = w.getBlockAt(x, ny, z);
         if (!this.isBambooLike(b.getType())) {
            break;
         }

         b.setType(replaceWith, false);
         this.unmarkPluginPlaced(b);
         this.unmarkPlayerPlaced(b);
         changed++;
      }

      return changed;
   }

   private Block findPurgeCandidate(World w, int x, int surfaceY, int z, int depth) {
      int minY = Math.max(w.getMinHeight(), surfaceY - depth);
      int startY = Math.min(w.getMaxHeight() - 1, surfaceY + 2);

      for (int y = startY; y >= minY; y--) {
         Block b = w.getBlockAt(x, y, z);
         Material t = b.getType();
         if (t != Material.AIR && t != Material.SNOW && t != Material.SNOW_BLOCK) {
            if (this.rulesByMaterial.containsKey(t)) {
               return b;
            }

            if (t.isSolid()) {
               Block up = b.getRelative(BlockFace.UP);
               if (this.rulesByMaterial.containsKey(up.getType())) {
                  return up;
               }
               break;
            }
         }
      }

      return null;
   }

   private Block findRuleBlockInColumn(World w, int x, int z, List<Material> mats) {
      int surfaceY = this.getSurfaceYCompat(w, x, z);
      int depth = Math.max(1, this.plugin.cfg.climate.getInt("seasonal_flora.surface_scan_depth", 8));
      int minY = Math.max(w.getMinHeight(), surfaceY - depth);
      int startY = Math.min(w.getMaxHeight() - 1, surfaceY + 2);

      for (int y = startY; y >= minY; y--) {
         Block b = w.getBlockAt(x, y, z);
         Material t = b.getType();
         if (t != Material.AIR && t != Material.SNOW && t != Material.SNOW_BLOCK) {
            if (mats.contains(t)) {
               return b;
            }

            if (t.isSolid()) {
               Block up = b.getRelative(BlockFace.UP);
               if (mats.contains(up.getType())) {
                  return up;
               }
               break;
            }
         }
      }

      return null;
   }

   private int getSurfaceYCompat(World w, int x, int z) {
      try {
         return w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      } catch (Throwable ignored) {
         return w.getHighestBlockYAt(x, z);
      }
   }

   private boolean isProtectedByPlayer(Block b) {
      if (!this.protectPlayerPlaced) {
         return false;
      }

      World w = b.getWorld();
      long ck = this.chunkKey(w, b.getX() >> 4, b.getZ() >> 4);
      Set<Long> set = this.playerPlaced.get(ck);
      if (set != null && !set.isEmpty()) {
         long bk = this.blockKey(w, b.getX(), b.getY(), b.getZ());
         if (set.contains(bk)) {
            return true;
         } else {
            return this.isBambooLike(b.getType()) ? this.hasAnyMarkInBambooColumn(w, b.getX(), b.getY(), b.getZ(), set) : false;
         }
      } else {
         return false;
      }
   }

   private boolean isPlacedByPlugin(Block b) {
      World w = b.getWorld();
      long ck = this.chunkKey(w, b.getX() >> 4, b.getZ() >> 4);
      Set<Long> set = this.pluginPlaced.get(ck);
      if (set != null && !set.isEmpty()) {
         long bk = this.blockKey(w, b.getX(), b.getY(), b.getZ());
         if (set.contains(bk)) {
            return true;
         } else {
            return this.isBambooLike(b.getType()) ? this.hasAnyMarkInBambooColumn(w, b.getX(), b.getY(), b.getZ(), set) : false;
         }
      } else {
         return false;
      }
   }

   private void markPluginPlaced(Block b) {
      long ck = this.chunkKey(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
      long bk = this.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ());
      this.pluginPlaced.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(bk);
   }

   private void unmarkPluginPlaced(Block b) {
      long ck = this.chunkKey(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
      long bk = this.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ());
      Set<Long> set = this.pluginPlaced.get(ck);
      if (set != null) {
         set.remove(bk);
         if (set.isEmpty()) {
            this.pluginPlaced.remove(ck);
         }
      }
   }

   private int purgeBlockRespectingShape(Block b, Material replaceWith) {
      Material type = b.getType();
      if (this.isBambooLike(type)) {
         return this.purgeBambooColumn(b, replaceWith);
      }

      int changed = 0;
      if (this.isDoublePlant(type)) {
         Block up = b.getRelative(BlockFace.UP);
         Block down = b.getRelative(BlockFace.DOWN);
         if (up.getType() == type) {
            up.setType(replaceWith, false);
            this.unmarkPluginPlaced(up);
            this.unmarkPlayerPlaced(up);
            changed++;
         }

         if (down.getType() == type) {
            down.setType(replaceWith, false);
            this.unmarkPluginPlaced(down);
            this.unmarkPlayerPlaced(down);
            changed++;
         }

         b.setType(replaceWith, false);
         this.unmarkPluginPlaced(b);
         this.unmarkPlayerPlaced(b);
         return changed + 1;
      } else {
         b.setType(replaceWith, false);
         this.unmarkPluginPlaced(b);
         this.unmarkPlayerPlaced(b);
         return 1;
      }
   }

   private void markPlayerPlaced(Block b) {
      long ck = this.chunkKey(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
      long bk = this.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ());
      this.playerPlaced.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(bk);
   }

   private void unmarkPlayerPlaced(Block b) {
      long ck = this.chunkKey(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
      long bk = this.blockKey(b.getWorld(), b.getX(), b.getY(), b.getZ());
      Set<Long> set = this.playerPlaced.get(ck);
      if (set != null) {
         set.remove(bk);
         if (set.isEmpty()) {
            this.playerPlaced.remove(ck);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockSpreadMonitor(BlockSpreadEvent e) {
      if (this.enabled && this.protectPlayerPlaced) {
         Material newType = e.getNewState().getType();
         if (this.isBambooLike(newType)) {
            Block src = e.getSource();
            Block target = e.getBlock();
            if (src != null) {
               if (src.getX() == target.getX() && src.getZ() == target.getZ()) {
                  if (target.getY() > src.getY()) {
                     if (this.isProtectedByPlayer(src)) {
                        this.markPlayerPlaced(target);
                     }
                  }
               }
            }
         }
      }
   }

   private long chunkKey(World w, int cx, int cz) {
      long k = (cx & 4294967295L) << 32 | cz & 4294967295L;
      long wh = w.getUID().getMostSignificantBits() ^ w.getUID().getLeastSignificantBits();
      return k ^ wh;
   }

   private long blockKey(World w, int x, int y, int z) {
      long wx = x & 67108863L;
      long wz = z & 67108863L;
      long wy = y + 2048L & 4095L;
      long k = wx << 38 | wz << 12 | wy;
      long wh = w.getUID().getMostSignificantBits() ^ w.getUID().getLeastSignificantBits();
      return k ^ wh;
   }

   private int getSurfaceY(World w, int x, int z) {
      if (M_GET_HIGHEST_Y_HM != null && HM_MOTION_NO_LEAVES != null) {
         try {
            return (Integer)M_GET_HIGHEST_Y_HM.invoke(w, x, z, HM_MOTION_NO_LEAVES);
         } catch (Throwable var5) {
         }
      }

      return w.getHighestBlockYAt(x, z);
   }

   static {
      Object hm = null;
      Method m = null;

      try {
         Class<?> heightMapClass = Class.forName("org.bukkit.HeightMap");
         Class<? extends Enum> enumClass = (Class<? extends Enum>)heightMapClass;
         hm = Enum.valueOf(enumClass, "MOTION_BLOCKING_NO_LEAVES");
         m = World.class.getMethod("getHighestBlockYAt", int.class, int.class, heightMapClass);
      } catch (Throwable var4) {
      }

      HM_MOTION_NO_LEAVES = hm;
      M_GET_HIGHEST_Y_HM = m;
   }

   private static final class FloraRule {
      final String id;
      final boolean enabled;
      final boolean purgeWhenDisabled;
      final List<Material> blocks;
      final EnumSet<Season> spreadSeasons;
      final EnumSet<Season> removeSeasons;
      final boolean purgeNatural;
      final boolean purgePluginPlaced;
      final Material replaceWith;
      final EnumSet<Season> restoreSeasons;
      final double restoreChance;
      final int restoreTriesPerChunk;
      final Set<Biome> biomes;
      final int minLight;
      final int maxLight;
      final boolean requireSolidGround;
      final Set<Material> groundBlocks;
      final boolean forbidCanopy;
      final int canopyCheckHeight;
      final boolean doublePlant;
      final boolean requiresAttachment;
      final Set<Material> attachBlocks;
      final int maxPerChunk;
      final int minDistanceBlocks;

      FloraRule(
         String id,
         boolean enabled,
         boolean purgeWhenDisabled,
         List<Material> blocks,
         EnumSet<Season> spreadSeasons,
         EnumSet<Season> removeSeasons,
         boolean purgeNatural,
         boolean purgePluginPlaced,
         Material replaceWith,
         EnumSet<Season> restoreSeasons,
         double restoreChance,
         int restoreTriesPerChunk,
         Set<Biome> biomes,
         int minLight,
         int maxLight,
         boolean requireSolidGround,
         Set<Material> groundBlocks,
         boolean forbidCanopy,
         int canopyCheckHeight,
         boolean doublePlant,
         boolean requiresAttachment,
         Set<Material> attachBlocks,
         int maxPerChunk,
         int minDistanceBlocks
      ) {
         this.id = id;
         this.enabled = enabled;
         this.purgeWhenDisabled = purgeWhenDisabled;
         this.blocks = blocks;
         this.spreadSeasons = spreadSeasons;
         this.removeSeasons = removeSeasons;
         this.purgeNatural = purgeNatural;
         this.purgePluginPlaced = purgePluginPlaced;
         this.replaceWith = replaceWith;
         this.restoreSeasons = restoreSeasons;
         this.restoreChance = restoreChance;
         this.restoreTriesPerChunk = restoreTriesPerChunk;
         this.biomes = biomes;
         this.minLight = minLight;
         this.maxLight = maxLight;
         this.requireSolidGround = requireSolidGround;
         this.groundBlocks = groundBlocks;
         this.forbidCanopy = forbidCanopy;
         this.canopyCheckHeight = canopyCheckHeight;
         this.doublePlant = doublePlant;
         this.requiresAttachment = requiresAttachment;
         this.attachBlocks = attachBlocks;
         this.maxPerChunk = maxPerChunk;
         this.minDistanceBlocks = minDistanceBlocks;
      }

      boolean isBiomeAllowed(Biome b) {
         return this.biomes == null || this.biomes.isEmpty() || this.biomes.contains(b);
      }

      static SeasonalFloraController.FloraRule fromConfig(String id, ConfigurationSection sec, AeternumSeasonsPlugin plugin) {
         boolean enabled = sec.getBoolean("enabled", true);
         boolean purgeWhenDisabled = sec.getBoolean("purge_when_disabled", false);
         List<Material> blocks = new ArrayList<>();

         for (String s : sec.getStringList("blocks")) {
            Material m = parseMaterial(s);
            if (m != null) {
               blocks.add(m);
            }
         }

         EnumSet<Season> spread = parseSeasons(sec.getStringList("spread_seasons"), EnumSet.allOf(Season.class));
         EnumSet<Season> remove = parseSeasons(sec.getStringList("remove_seasons"), EnumSet.noneOf(Season.class));
         boolean purge = sec.getBoolean("purge_natural", false);
         boolean purgePluginPlaced = sec.getBoolean("purge_plugin_placed", true);
         Material replaceWith = parseMaterial(sec.getString("replace_with", "AIR"));
         EnumSet<Season> restore = parseSeasons(sec.getStringList("restore_seasons"), EnumSet.noneOf(Season.class));
         double chance = clamp01(sec.getDouble("restore_chance", 0.0));
         int restoreTries = Math.max(1, sec.getInt("restore_tries_per_chunk", 6));
         Set<Biome> biomes = new HashSet<>();

         for (String s : sec.getStringList("biomes")) {
            Biome b = parseBiome(s);
            if (b != null) {
               biomes.add(b);
            }
         }

         int minLight = Math.max(0, sec.getInt("min_light", 0));
         int maxLight = Math.min(15, sec.getInt("max_light", 15));
         boolean requireSolidGround = sec.getBoolean("require_solid_ground", true);
         Set<Material> groundBlocks = new HashSet<>();

         for (String s : sec.getStringList("ground_blocks")) {
            Material m = parseMaterial(s);
            if (m != null) {
               groundBlocks.add(m);
            }
         }

         boolean forbidCanopy = sec.getBoolean("forbid_canopy", false);
         int canopyCheckHeight = Math.max(1, sec.getInt("canopy_check_height", 3));
         boolean doublePlant = sec.getBoolean("double_plant", false);
         boolean requiresAttachment = sec.getBoolean("requires_attachment", false);
         Set<Material> attachBlocks = new HashSet<>();

         for (String s : sec.getStringList("attach_blocks")) {
            Material m = parseMaterial(s);
            if (m != null) {
               attachBlocks.add(m);
            }
         }

         int maxPerChunk = Math.max(0, sec.getInt("max_per_chunk", 0));
         int minDistanceBlocks = Math.max(0, sec.getInt("min_distance_blocks", 0));
         if (blocks.isEmpty()) {
            plugin.getLogger().warning("[SeasonalFlora] rule '" + id + "' has invalid blocks.");
         }

         return new SeasonalFloraController.FloraRule(
            id,
            enabled,
            purgeWhenDisabled,
            blocks,
            spread,
            remove,
            purge,
            purgePluginPlaced,
            replaceWith,
            restore,
            chance,
            restoreTries,
            biomes,
            minLight,
            maxLight,
            requireSolidGround,
            groundBlocks,
            forbidCanopy,
            canopyCheckHeight,
            doublePlant,
            requiresAttachment,
            attachBlocks,
            maxPerChunk,
            minDistanceBlocks
         );
      }

      private static Material parseMaterial(String s) {
         if (s == null) {
            return null;
         }

         try {
            return Material.valueOf(s.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException ex) {
            return null;
         }
      }

      private static Biome parseBiome(String s) {
         if (s == null) {
            return null;
         }

         try {
            return Biome.valueOf(s.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException ex) {
            return null;
         }
      }

      private static EnumSet<Season> parseSeasons(List<String> list, EnumSet<Season> def) {
         if (list != null && !list.isEmpty()) {
            EnumSet<Season> set = EnumSet.noneOf(Season.class);

            for (String s : list) {
               try {
                  set.add(Season.valueOf(s.toUpperCase(Locale.ROOT)));
               } catch (IllegalArgumentException var6) {
               }
            }

            return set.isEmpty() ? def : set;
         } else {
            return def;
         }
      }

      private static double clamp01(double d) {
         if (d < 0.0) {
            return 0.0;
         } else {
            return d > 1.0 ? 1.0 : d;
         }
      }
   }
}
