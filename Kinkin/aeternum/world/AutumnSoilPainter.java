package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.calendar.SeasonService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class AutumnSoilPainter implements Runnable, Listener {
   private final AeternumSeasonsPlugin plugin;
   private final SeasonService seasons;
   private static volatile AutumnSoilPainter INSTANCE;
   private BukkitTask task;
   private final Random random = new Random();
   private int chunksPerTick;
   private int radiusChunks;
   private double leafChancePerBlock;
   private final Set<Material> leafTypes = EnumSet.of(Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES);
   private final Map<String, Material> paintedLeaves = new ConcurrentHashMap<>();
   private static final int DAYS_PER_SEASON = 28;
   private static final int PRE_AUTUMN_START_DAY = 26;
   private static final int PRE_AUTUMN_RAMP_DAYS = 3;
   private boolean gridFlip = false;
   private Set<String> disabledFxWorlds = Collections.emptySet();

   public AutumnSoilPainter(AeternumSeasonsPlugin plugin, SeasonService seasons) {
      this.plugin = plugin;
      this.seasons = seasons;
      this.reloadFromConfig();
      INSTANCE = this;
   }

   public void reloadFromConfig() {
      FileConfiguration y = this.plugin.cfg.climate;
      this.chunksPerTick = Math.max(2, y.getInt("autumn_soil.attempts_per_tick", 4));
      this.radiusChunks = Math.max(2, y.getInt("autumn_soil.radius_chunks", 4));
      this.leafChancePerBlock = y.getDouble("autumn_soil.leaf_chance_per_block", 1.0);
      if (this.leafChancePerBlock < 0.0) {
         this.leafChancePerBlock = 0.0;
      }

      if (this.leafChancePerBlock > 1.0) {
         this.leafChancePerBlock = 1.0;
      }

      List<String> list = this.plugin.getConfig().getStringList("worlds.disabled_season_fx");
      Set<String> s = new HashSet<>();

      for (String w : list) {
         if (w != null) {
            String name = w.trim();
            if (!name.isEmpty()) {
               s.add(name.toLowerCase(Locale.ROOT));
            }
         }
      }

      this.disabledFxWorlds = s;
   }

   public void register() {
      if (this.task != null) {
         this.task.cancel();
      }

      if (this.plugin.cfg.climate.getBoolean("autumn_soil.enabled", false)) {
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this, 60L, 5L);
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
      }
   }

   public void unregister() {
      if (this.task != null) {
         this.task.cancel();
      }

      this.task = null;
      HandlerList.unregisterAll(this);
      if (INSTANCE == this) {
         INSTANCE = null;
      }
   }

   @Override
   public void run() {
      CalendarState st = this.seasons.getStateCopy();
      this.gridFlip = !this.gridFlip;
      int budget = this.chunksPerTick;
      if (budget > 0) {
         Season season = st.season;
         int dayInSeason = this.computeDayInSeason(st);
         double paintFactor = 0.0;
         if (season == Season.AUTUMN) {
            if (dayInSeason >= 3) {
               paintFactor = 1.0;
            } else if (dayInSeason == 2) {
               paintFactor = 0.65;
            } else if (dayInSeason == 1) {
               paintFactor = 0.35;
            }
         }

         if (paintFactor <= 0.0) {
            int revertBudget = this.paintedLeaves.isEmpty() ? 4096 : 20000;
            this.revertSomeLeaves(revertBudget);
            if (this.paintedLeaves.isEmpty()) {
               this.healResidualAutumnLeavesAroundPlayers();
            }
         } else {
            boolean matureAutumn = season == Season.AUTUMN && dayInSeason >= 3;

            for (Player p : Bukkit.getOnlinePlayers()) {
               if (budget <= 0) {
                  break;
               }

               World w = p.getWorld();
               if (w.getEnvironment() == Environment.NORMAL && !this.isFxDisabled(w) && !this.seasons.isPermanentWinterWorld(w)) {
                  Location loc = p.getLocation();
                  int pcx = loc.getBlockX() >> 4;
                  int pcz = loc.getBlockZ() >> 4;
                  Vector look = loc.getDirection().clone();
                  look.setY(0);
                  if (look.lengthSquared() < 1.0E-4) {
                     look = new Vector(0, 0, 1);
                  } else {
                     look.normalize();
                  }

                  int radius = this.radiusChunks;
                  List<AutumnSoilPainter.Offset> offsets = new ArrayList<>();

                  for (int dx = -radius; dx <= radius; dx++) {
                     for (int dz = -radius; dz <= radius; dz++) {
                        int dist = Math.max(Math.abs(dx), Math.abs(dz));
                        if (dist != 0) {
                           Vector dir = new Vector(dx, 0, dz);
                           double forward;
                           if (dir.lengthSquared() < 1.0E-4) {
                              forward = 0.0;
                           } else {
                              dir.normalize();
                              forward = look.dot(dir);
                           }

                           offsets.add(new AutumnSoilPainter.Offset(dx, dz, dist, forward));
                        }
                     }
                  }

                  offsets.sort(Comparator.<AutumnSoilPainter.Offset>comparingInt(o -> o.dist).thenComparingDouble(o -> -o.forwardScore));
                  if (budget > 0 && w.isChunkLoaded(pcx, pcz)) {
                     this.processChunk(w, pcx, pcz, matureAutumn, paintFactor);
                     budget--;
                  }

                  for (AutumnSoilPainter.Offset off : offsets) {
                     if (budget <= 0) {
                        break;
                     }

                     int cx = pcx + off.dx;
                     int cz = pcz + off.dz;
                     if (w.isChunkLoaded(cx, cz)) {
                        this.processChunk(w, cx, cz, matureAutumn, paintFactor);
                        budget--;
                     }
                  }
               }
            }
         }
      }
   }

   private void healResidualAutumnLeavesAroundPlayers() {
      int budget = this.chunksPerTick;
      if (budget <= 0) {
         budget = 2;
      }

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (budget <= 0) {
            break;
         }

         World w = p.getWorld();
         if (w.getEnvironment() == Environment.NORMAL && !this.isFxDisabled(w)) {
            Location loc = p.getLocation();
            int pcx = loc.getBlockX() >> 4;
            int pcz = loc.getBlockZ() >> 4;
            int radius = this.radiusChunks;

            for (int cx = pcx - radius; cx <= pcx + radius && budget > 0; cx++) {
               for (int cz = pcz - radius; cz <= pcz + radius && budget > 0; cz++) {
                  if (w.isChunkLoaded(cx, cz)) {
                     this.fixChunkResidualLeaves(w, cx, cz);
                     budget--;
                  }
               }
            }
         }
      }
   }

   private void fixChunkResidualLeaves(World w, int cx, int cz) {
      int minY = w.getMinHeight();
      int maxY = w.getMaxHeight();
      int baseX = cx << 4;
      int baseZ = cz << 4;

      for (int lx = 0; lx < 16; lx++) {
         for (int lz = 0; lz < 16; lz++) {
            int wx = baseX + lx;
            int wz = baseZ + lz;
            int yTop = w.getHighestBlockYAt(wx, wz) - 1;
            if (yTop >= minY) {
               int scanMinY = Math.max(minY, yTop - 12);
               int scanMaxY = Math.min(maxY, yTop + 8);

               for (int y = scanMinY; y <= scanMaxY; y++) {
                  Block b = w.getBlockAt(wx, y, wz);
                  if (b.getType() == Material.ACACIA_LEAVES) {
                     Material guessed = this.guessOriginalFromWorld(b);
                     if (guessed != null && guessed != Material.ACACIA_LEAVES) {
                        BlockData current = b.getBlockData();
                        int distance = 1;
                        if (current instanceof Leaves leaves) {
                           distance = leaves.getDistance();
                        }

                        Leaves backLeaves = (Leaves)guessed.createBlockData();
                        backLeaves.setDistance(distance);
                        backLeaves.setPersistent(false);
                        b.setBlockData(backLeaves, false);
                     }
                  }
               }
            }
         }
      }
   }

   private Material guessOriginalFromWorld(Block leaf) {
      World w = leaf.getWorld();
      Biome biome = leaf.getBiome();
      int radius = 6;
      int lx = leaf.getX();
      int ly = leaf.getY();
      int lz = leaf.getZ();
      int spruce = 0;
      int birch = 0;
      int cherry = 0;
      int acacia = 0;

      for (int x = lx - radius; x <= lx + radius; x++) {
         for (int y = ly - radius; y <= ly + radius; y++) {
            for (int z = lz - radius; z <= lz + radius; z++) {
               Material t = w.getBlockAt(x, y, z).getType();
               if (t == Material.SPRUCE_LOG || t == Material.SPRUCE_WOOD || t == Material.STRIPPED_SPRUCE_LOG || t == Material.STRIPPED_SPRUCE_WOOD) {
                  spruce++;
               }

               if (t == Material.BIRCH_LOG || t == Material.BIRCH_WOOD || t == Material.STRIPPED_BIRCH_LOG || t == Material.STRIPPED_BIRCH_WOOD) {
                  birch++;
               }

               if (t == Material.CHERRY_LOG || t == Material.CHERRY_WOOD || t == Material.STRIPPED_CHERRY_LOG || t == Material.STRIPPED_CHERRY_WOOD) {
                  cherry++;
               }

               if (t == Material.ACACIA_LOG || t == Material.ACACIA_WOOD || t == Material.STRIPPED_ACACIA_LOG || t == Material.STRIPPED_ACACIA_WOOD) {
                  acacia++;
               }
            }
         }
      }

      if (spruce == 0 && birch == 0 && cherry == 0) {
         return null;
      } else if (biome == Biome.CHERRY_GROVE && cherry > 0) {
         return Material.CHERRY_LEAVES;
      } else if (cherry >= birch && cherry >= spruce) {
         return Material.CHERRY_LEAVES;
      } else {
         return spruce >= birch ? Material.SPRUCE_LEAVES : Material.BIRCH_LEAVES;
      }
   }

   private boolean isTaigaBiome(Biome biome) {
      return biome == Biome.TAIGA || biome == Biome.SNOWY_TAIGA || biome == Biome.OLD_GROWTH_SPRUCE_TAIGA || biome == Biome.OLD_GROWTH_PINE_TAIGA;
   }

   private boolean isBirchBiome(Biome biome) {
      return biome == Biome.BIRCH_FOREST || biome == Biome.OLD_GROWTH_BIRCH_FOREST;
   }

   private void processChunk(World w, int cx, int cz, boolean highDetail, double paintFactor) {
      int minY = w.getMinHeight();
      int maxY = w.getMaxHeight();
      int baseX = cx << 4;
      int baseZ = cz << 4;
      double effectiveChance = this.leafChancePerBlock * paintFactor;
      if (!(effectiveChance <= 0.0)) {
         int stepXZ = highDetail ? 1 : 2;
         int startOffset = 0;
         if (stepXZ == 2) {
            startOffset = this.gridFlip ? 0 : 1;
         }

         for (int lx = startOffset; lx < 16; lx += stepXZ) {
            for (int lz = startOffset; lz < 16; lz += stepXZ) {
               int wx = baseX + lx;
               int wz = baseZ + lz;
               int yTop = w.getHighestBlockYAt(wx, wz) - 1;
               if (yTop >= minY) {
                  int scanMinY = Math.max(minY, yTop - 12);
                  int scanMaxY = Math.min(maxY, yTop + 8);

                  for (int y = scanMinY; y <= scanMaxY; y++) {
                     Block b = w.getBlockAt(wx, y, wz);
                     Material type = b.getType();
                     if (this.leafTypes.contains(type)
                        && b.getBlockData() instanceof Leaves leaves
                        && !leaves.isPersistent()
                        && (!(effectiveChance < 1.0) || !(this.random.nextDouble() > effectiveChance))) {
                        this.paintLeaf(b, type);
                     }
                  }
               }
            }
         }
      }
   }

   private void paintLeaf(Block b, Material originalType) {
      if (b.getType() != Material.ACACIA_LEAVES) {
         String k = this.key(b.getWorld(), b.getX(), b.getY(), b.getZ());
         this.paintedLeaves.putIfAbsent(k, originalType);
         BlockData oldData = b.getBlockData();
         int distance = 1;
         if (oldData instanceof Leaves leavesOld) {
            distance = leavesOld.getDistance();
         }

         Leaves newLeaves = (Leaves)Material.ACACIA_LEAVES.createBlockData();
         newLeaves.setDistance(distance);
         newLeaves.setPersistent(false);
         b.setBlockData(newLeaves, false);
      }
   }

   public boolean restoreOriginalLeafForDecay(Block b) {
      if (b == null) {
         return false;
      }

      if (b.getType() != Material.ACACIA_LEAVES) {
         return false;
      }

      World w = b.getWorld();
      if (w == null) {
         return false;
      }

      String k = this.key(w, b.getX(), b.getY(), b.getZ());
      Material original = this.paintedLeaves.remove(k);
      if (original != null && original != Material.ACACIA_LEAVES) {
         BlockData currentData = b.getBlockData();
         int distance = 1;
         boolean persistent = false;
         if (currentData instanceof Leaves currentLeaves) {
            distance = currentLeaves.getDistance();
            persistent = currentLeaves.isPersistent();
         }

         if (original.createBlockData() instanceof Leaves backLeaves) {
            backLeaves.setDistance(distance);
            backLeaves.setPersistent(persistent);
            b.setBlockData(backLeaves, false);
         } else {
            b.setType(original, false);
         }

         return true;
      } else {
         return false;
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   public void onSaplingSpawn(ItemSpawnEvent event) {
      Item item = event.getEntity();
      ItemStack st = item.getItemStack();
      if (st.getType() == Material.ACACIA_SAPLING) {
         Location loc = item.getLocation();
         World w = loc.getWorld();
         if (w != null) {
            Block leafBlock = this.findNearbyAcaciaLeaf(w, loc);
            Material original = null;
            if (leafBlock != null) {
               String k = this.key(w, leafBlock.getX(), leafBlock.getY(), leafBlock.getZ());
               original = this.paintedLeaves.get(k);
               if (original == null) {
                  original = this.guessOriginalFromWorld(leafBlock);
               }

               if (original == null) {
                  original = this.guessOriginalFromNearbyLogItems(item);
               }
            } else {
               original = this.guessOriginalFromWorld(w.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
               if (original == null) {
                  original = this.guessOriginalFromNearbyLogItems(item);
               }
            }

            if (original != null) {
               Material correct = this.saplingFor(original);
               if (correct != null && correct != Material.ACACIA_SAPLING) {
                  ItemStack newStack = new ItemStack(correct, st.getAmount());
                  newStack.setItemMeta(st.getItemMeta());
                  item.setItemStack(newStack);
               }
            }
         }
      }
   }

   private Material guessOriginalFromNearbyLogItems(Item saplingItem) {
      Location loc = saplingItem.getLocation();
      World w = loc.getWorld();
      if (w == null) {
         return null;
      }

      double r = 6.0;
      int spruce = 0;
      int birch = 0;
      int cherry = 0;

      for (Entity e : w.getNearbyEntities(loc, r, r, r)) {
         if (e instanceof Item it) {
            Material t = it.getItemStack().getType();
            if (t == Material.SPRUCE_LOG || t == Material.SPRUCE_WOOD || t == Material.STRIPPED_SPRUCE_LOG || t == Material.STRIPPED_SPRUCE_WOOD) {
               spruce++;
            }

            if (t == Material.BIRCH_LOG || t == Material.BIRCH_WOOD || t == Material.STRIPPED_BIRCH_LOG || t == Material.STRIPPED_BIRCH_WOOD) {
               birch++;
            }

            if (t == Material.CHERRY_LOG || t == Material.CHERRY_WOOD || t == Material.STRIPPED_CHERRY_LOG || t == Material.STRIPPED_CHERRY_WOOD) {
               cherry++;
            }
         }
      }

      if (spruce == 0 && birch == 0 && cherry == 0) {
         return null;
      } else if (spruce >= birch && spruce >= cherry) {
         return Material.SPRUCE_LEAVES;
      } else {
         return birch >= spruce && birch >= cherry ? Material.BIRCH_LEAVES : Material.CHERRY_LEAVES;
      }
   }

   private Block findNearbyAcaciaLeaf(World w, Location loc) {
      int bx = loc.getBlockX();
      int by = loc.getBlockY();
      int bz = loc.getBlockZ();
      int rXZ = 2;
      int rY = 3;

      for (int dy = -rY; dy <= rY; dy++) {
         for (int dx = -rXZ; dx <= rXZ; dx++) {
            for (int dz = -rXZ; dz <= rXZ; dz++) {
               Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
               if (b.getType() == Material.ACACIA_LEAVES) {
                  return b;
               }
            }
         }
      }

      return null;
   }

   private void revertSomeLeaves(int maxBlocks) {
      if (maxBlocks > 0) {
         Iterator<Entry<String, Material>> it = this.paintedLeaves.entrySet().iterator();

         while (it.hasNext() && maxBlocks > 0) {
            Entry<String, Material> entry = it.next();
            String key = entry.getKey();
            Material original = entry.getValue();
            String[] parts = key.split(":");
            if (parts.length != 4) {
               it.remove();
            } else {
               UUID worldId;
               try {
                  worldId = UUID.fromString(parts[0]);
               } catch (IllegalArgumentException ex) {
                  it.remove();
                  continue;
               }

               World w = Bukkit.getWorld(worldId);
               if (w == null) {
                  it.remove();
               } else {
                  int x;
                  int y;
                  int z;
                  try {
                     x = Integer.parseInt(parts[1]);
                     y = Integer.parseInt(parts[2]);
                     z = Integer.parseInt(parts[3]);
                  } catch (NumberFormatException ex) {
                     it.remove();
                     continue;
                  }

                  if (w.isChunkLoaded(x >> 4, z >> 4)) {
                     Block b = w.getBlockAt(x, y, z);
                     BlockData currentData = b.getBlockData();
                     int distance = 1;
                     if (currentData instanceof Leaves leavesCurrent) {
                        distance = leavesCurrent.getDistance();
                     }

                     if (original.createBlockData() instanceof Leaves backLeaves) {
                        backLeaves.setDistance(distance);
                        backLeaves.setPersistent(false);
                        b.setBlockData(backLeaves, false);
                     } else {
                        b.setType(original, false);
                     }

                     it.remove();
                     maxBlocks--;
                  }
               }
            }
         }
      }
   }

   private Material saplingFor(Material originalLeaves) {
      return switch (originalLeaves) {
         case SPRUCE_LEAVES -> Material.SPRUCE_SAPLING;
         case BIRCH_LEAVES -> Material.BIRCH_SAPLING;
         case OAK_LEAVES -> Material.OAK_SAPLING;
         case DARK_OAK_LEAVES -> Material.DARK_OAK_SAPLING;
         case JUNGLE_LEAVES -> Material.JUNGLE_SAPLING;
         case ACACIA_LEAVES -> Material.ACACIA_SAPLING;
         case CHERRY_LEAVES -> Material.CHERRY_SAPLING;
         default -> null;
      };
   }

   private String key(World w, int x, int y, int z) {
      return w.getUID() + ":" + x + ":" + y + ":" + z;
   }

   private int computeDayInSeason(CalendarState st) {
      try {
         try {
            Method m = st.getClass().getMethod("getDayInSeason");
            if (m.invoke(st) instanceof Number n) {
               return this.clampDayInSeason(n.intValue());
            }
         } catch (NoSuchMethodException ignored) {
            Method m = st.getClass().getMethod("dayInSeason");
            if (m.invoke(st) instanceof Number n) {
               return this.clampDayInSeason(n.intValue());
            }
         }
      } catch (Exception var11) {
      }

      try {
         for (Field f : st.getClass().getDeclaredFields()) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.contains("dayinseason")) {
               f.setAccessible(true);
               if (f.get(st) instanceof Number n) {
                  return this.clampDayInSeason(n.intValue());
               }
            }
         }
      } catch (Exception var9) {
      }

      Integer dayGlobal = this.tryGetIntField(st, "dayOfYear", "day_of_year", "day");
      if (dayGlobal != null) {
         int d = (dayGlobal - 1) % 28 + 1;
         return this.clampDayInSeason(d);
      } else {
         return 1;
      }
   }

   private boolean isFxDisabled(World w) {
      return w != null && this.disabledFxWorlds.contains(w.getName().toLowerCase(Locale.ROOT));
   }

   private Integer tryGetIntField(CalendarState st, String... candidates) {
      try {
         for (String raw : candidates) {
            String name = raw;
            String getter = "get" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);

            try {
               Method m = st.getClass().getMethod(getter);
               if (m.invoke(st) instanceof Number n) {
                  return n.intValue();
               }
            } catch (NoSuchMethodException var15) {
            }

            try {
               Method m2 = st.getClass().getMethod(name);
               if (m2.invoke(st) instanceof Number n2) {
                  return n2.intValue();
               }
            } catch (NoSuchMethodException var14) {
            }
         }

         for (Field f : st.getClass().getDeclaredFields()) {
            String fn = f.getName();

            for (String cand : candidates) {
               if (fn.equalsIgnoreCase(cand)) {
                  f.setAccessible(true);
                  if (f.get(st) instanceof Number n) {
                     return n.intValue();
                  }
               }
            }
         }
      } catch (Exception var16) {
      }

      return null;
   }

   private int clampDayInSeason(int d) {
      int max = Math.max(1, this.seasons.getDaysPerSeason());
      if (d < 1) {
         d = 1;
      }

      if (d > max) {
         d = max;
      }

      return d;
   }

   private double computePreAutumnFactor(int dayInSeason) {
      if (dayInSeason < 26) {
         return 0.0;
      }

      int step = dayInSeason - 26;
      double factor = (step + 1) / 3.0;
      if (factor < 0.0) {
         factor = 0.0;
      }

      if (factor > 1.0) {
         factor = 1.0;
      }

      return factor;
   }

   public static AutumnSoilPainter instanceOrNull() {
      return INSTANCE;
   }

   private static final class Offset {
      final int dx;
      final int dz;
      final int dist;
      final double forwardScore;

      Offset(int dx, int dz, int dist, double forwardScore) {
         this.dx = dx;
         this.dz = dz;
         this.dist = dist;
         this.forwardScore = forwardScore;
      }
   }
}
