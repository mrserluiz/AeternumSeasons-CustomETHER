package Kinkin.aeternum.crafting;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class NaturalTreeAxe implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final NamespacedKey itemTagKey;
   private final NamespacedKey recipeKey;
   private static final int MAX_LOGS_PER_USE = 220;
   private static final int MAX_LEAVES_PER_USE = 520;
   private static final int MAX_TREE_HEIGHT = 56;
   private static final int FALL_ANIM_TICKS = 80;
   private static final int MAX_DISPLAYS_TOTAL = 240;
   private final Map<UUID, NaturalTreeAxe.FellSession> active = new HashMap<>();
   private static final Set<Material> NATURAL_SOILS = EnumSet.of(
      Material.DIRT,
      Material.GRASS_BLOCK,
      Material.COARSE_DIRT,
      Material.ROOTED_DIRT,
      Material.PODZOL,
      Material.MUD,
      Material.MUDDY_MANGROVE_ROOTS,
      Material.MOSS_BLOCK,
      Material.MYCELIUM,
      Material.SAND,
      Material.RED_SAND
   );
   private static final int[][] LOG_NEIGHBOR_OFFSETS = new int[][]{
      {1, 0, 0},
      {-1, 0, 0},
      {0, 1, 0},
      {0, -1, 0},
      {0, 0, 1},
      {0, 0, -1},
      {1, 1, 0},
      {-1, 1, 0},
      {1, -1, 0},
      {-1, -1, 0},
      {0, 1, 1},
      {0, 1, -1},
      {0, -1, 1},
      {0, -1, -1},
      {1, 0, 1},
      {1, 0, -1},
      {-1, 0, 1},
      {-1, 0, -1},
      {1, 1, 1},
      {1, 1, -1},
      {-1, 1, 1},
      {-1, 1, -1},
      {1, -1, 1},
      {1, -1, -1},
      {-1, -1, 1},
      {-1, -1, -1}
   };

   public NaturalTreeAxe(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.itemTagKey = new NamespacedKey(plugin, "natural_tree_axe_item");
      this.recipeKey = new NamespacedKey(plugin, "natural_tree_axe");
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.registerRecipe();
   }

   public void unregister() {
      HandlerList.unregisterAll(this);
      Bukkit.removeRecipe(this.recipeKey);

      for (NaturalTreeAxe.FellSession s : this.active.values()) {
         try {
            s.cancel();
         } catch (Throwable var4) {
         }
      }

      this.active.clear();
   }

   private void registerRecipe() {
      ItemStack dummy = new ItemStack(Material.IRON_AXE);
      ShapedRecipe recipe = new ShapedRecipe(this.recipeKey, dummy);
      recipe.shape(new String[]{"LAL", "S S", "L L"});
      Material[] axeMats = Arrays.stream(Material.values()).filter(Material::isItem).filter(m -> m.name().endsWith("_AXE")).toArray(Material[]::new);
      RecipeChoice axeChoice = new MaterialChoice(axeMats);
      RecipeChoice leavesChoice = new MaterialChoice(
         new Material[]{
            Material.OAK_LEAVES,
            Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES,
            Material.CHERRY_LEAVES,
            Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES
         }
      );
      RecipeChoice naturalChoice = new MaterialChoice(
         new Material[]{
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG,
            Material.CHERRY_LOG,
            Material.MOSSY_COBBLESTONE,
            Material.HANGING_ROOTS
         }
      );
      recipe.setIngredient('A', axeChoice);
      recipe.setIngredient('L', leavesChoice);
      recipe.setIngredient('S', naturalChoice);
      Bukkit.addRecipe(recipe);
   }

   @EventHandler
   public void onPrepareCraft(PrepareItemCraftEvent e) {
      if (e.getRecipe() != null) {
         if (e.getRecipe() instanceof ShapedRecipe shaped) {
            if (this.recipeKey.equals(shaped.getKey())) {
               ItemStack[] matrix = e.getInventory().getMatrix();
               ItemStack foundAxe = null;
               int leaves = 0;
               int naturals = 0;

               for (ItemStack stack : matrix) {
                  if (stack != null && stack.getType() != Material.AIR) {
                     Material t = stack.getType();
                     if (isAxe(t)) {
                        if (foundAxe != null) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        foundAxe = stack;
                     } else if (!this.isLeavesMaterial(t) && !this.isNetherFoliageMaterial(t)) {
                        if (!this.isNaturalCraftBlock(t)) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        naturals += stack.getAmount();
                     } else {
                        leaves += stack.getAmount();
                     }
                  }
               }

               if (foundAxe != null && leaves >= 4 && naturals >= 2) {
                  Player crafter = e.getView().getPlayer() instanceof Player pl ? pl : null;
                  ItemStack result = new ItemStack(foundAxe.getType());
                  ItemMeta meta = result.getItemMeta();
                  if (meta != null) {
                     meta.getPersistentDataContainer().set(this.itemTagKey, PersistentDataType.BYTE, (byte)1);

                     try {
                        String name = this.plugin.lang.tr(crafter, "item.natural_tree_axe.name");
                        String lore1 = this.plugin.lang.tr(crafter, "item.natural_tree_axe.lore1");
                        String lore2 = this.plugin.lang.tr(crafter, "item.natural_tree_axe.lore2");
                        String lore3 = this.plugin.lang.tr(crafter, "item.natural_tree_axe.lore3");
                        meta.setDisplayName(name);
                        meta.setLore(Arrays.asList(lore1, lore2, lore3));
                     } catch (Throwable var14) {
                     }

                     result.setItemMeta(meta);
                  }

                  e.getInventory().setResult(result);
               } else {
                  e.getInventory().setResult(null);
               }
            }
         }
      }
   }

   private static boolean isAxe(Material m) {
      if (m == null) {
         return false;
      }

      if (m.name().endsWith("_AXE")) {
         return true;
      }

      NamespacedKey k = m.getKey();
      return k != null && "minecraft".equals(k.getNamespace()) && k.getKey().endsWith("_axe");
   }

   private static boolean isAxe(ItemStack it) {
      return it != null && isAxe(it.getType());
   }

   private boolean isNaturalTreeAxe(ItemStack stack) {
      if (stack == null) {
         return false;
      }

      if (!isAxe(stack.getType())) {
         return false;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return false;
      }

      Byte flag = (Byte)meta.getPersistentDataContainer().get(this.itemTagKey, PersistentDataType.BYTE);
      return flag != null && flag == 1;
   }

   private boolean isNaturalCraftBlock(Material m) {
      return m == Material.MOSSY_COBBLESTONE || m == Material.HANGING_ROOTS || this.isTrunkMaterial(m);
   }

   private boolean isTrunkMaterial(Material m) {
      if (m == null) {
         return false;
      }

      try {
         if (Tag.LOGS.isTagged(m)) {
            return true;
         }
      } catch (Throwable var3) {
      }

      String n = m.name();
      if (n.startsWith("STRIPPED_")) {
         n = n.substring("STRIPPED_".length());
      }

      return n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE");
   }

   private boolean isLeavesMaterial(Material m) {
      if (m == null) {
         return false;
      }

      try {
         if (Tag.LEAVES.isTagged(m)) {
            return true;
         }
      } catch (Throwable var3) {
      }

      return m.name().endsWith("_LEAVES");
   }

   private boolean isNetherFoliageMaterial(Material m) {
      return m == null ? false : m == Material.NETHER_WART_BLOCK || m == Material.WARPED_WART_BLOCK;
   }

   private static String trunkFamilyKey(Material m) {
      if (m == null) {
         return "";
      }

      String n = m.name();
      if (n.startsWith("STRIPPED_")) {
         n = n.substring("STRIPPED_".length());
      }

      String[] suffixes = new String[]{"_LOG", "_WOOD", "_STEM", "_HYPHAE"};

      for (String s : suffixes) {
         if (n.endsWith(s)) {
            return n.substring(0, n.length() - s.length());
         }
      }

      return n;
   }

   private boolean isSameTrunkFamily(Material m, String familyKey) {
      if (m == null) {
         return false;
      }

      if (!this.isTrunkMaterial(m)) {
         return false;
      }

      String fk = trunkFamilyKey(m);
      return !familyKey.isEmpty() && familyKey.equals(fk);
   }

   private boolean isFoliageBlock(Block b) {
      if (b == null) {
         return false;
      } else {
         Material m = b.getType();
         if (this.isLeavesMaterial(m)) {
            return b.getBlockData() instanceof Leaves l ? !l.isPersistent() : true;
         } else {
            return this.isNetherFoliageMaterial(m);
         }
      }
   }

   private boolean isNaturalSoil(Material m) {
      if (m == null) {
         return false;
      }

      if (NATURAL_SOILS.contains(m)) {
         return true;
      }

      String n = m.name();
      return n.contains("DIRT") || n.contains("GRASS") || n.contains("PODZOL") || n.contains("MUD") || n.contains("MOSS") || n.contains("MYCELIUM");
   }

   private Block findTrunkBase(Block origin, String familyKey) {
      Block b = origin;

      for (int i = 0; i < 12; i++) {
         Block below = b.getRelative(0, -1, 0);
         if (!this.isSameTrunkFamily(below.getType(), familyKey)) {
            break;
         }

         b = below;
         if (b.getY() <= b.getWorld().getMinHeight()) {
            break;
         }
      }

      return b;
   }

   private boolean quickLooksLikeTree(Block origin) {
      return this.hasFoliageNearby(origin, 4, 6);
   }

   private boolean hasFoliageNearby(Block origin, int radius, int min) {
      World w = origin.getWorld();
      int found = 0;
      Block cur = origin;
      String fk = trunkFamilyKey(origin.getType());

      for (int step = 0; step < 18 && this.isSameTrunkFamily(cur.getType(), fk); step++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (dx * dx + dz * dz <= radius * radius) {
                  for (int dy = -1; dy <= 3; dy++) {
                     Block b = w.getBlockAt(cur.getX() + dx, cur.getY() + dy, cur.getZ() + dz);
                     if (this.isFoliageBlock(b)) {
                        if (++found >= min) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         cur = cur.getRelative(0, 1, 0);
      }

      return false;
   }

   private int trunkHeightFromBase(Block base, String familyKey) {
      int h = 0;
      Block cur = base;

      for (int i = 0; i < 56 && this.isSameTrunkFamily(cur.getType(), familyKey); i++) {
         h++;
         cur = cur.getRelative(0, 1, 0);
      }

      return h;
   }

   private NaturalTreeAxe.TreeRadii computeRadii(Material trunkType, int trunkHeight, boolean is2x2) {
      int leafR = Math.min(18, Math.max(6, trunkHeight / 3 + 3));
      if (is2x2) {
         leafR = Math.min(18, leafR + 2);
      }

      int scanR = Math.min(8, Math.max(3, leafR / 2 + 2));
      int branchR = Math.min(8, Math.max(4, leafR / 2 + 1));
      String n = trunkType.name();
      if (n.contains("JUNGLE")) {
         leafR = Math.max(leafR, 10);
         scanR = Math.max(scanR, 6);
         branchR = Math.max(branchR, 6);
      }

      if (n.contains("SPRUCE")) {
         leafR = Math.max(leafR, 9);
         scanR = Math.max(scanR, 5);
         branchR = Math.max(branchR, 5);
      }

      if (n.contains("DARK_OAK")) {
         leafR = Math.max(leafR, 8);
         scanR = Math.max(scanR, 5);
         branchR = Math.max(branchR, 5);
      }

      if (n.contains("MANGROVE")) {
         leafR = Math.max(leafR, 9);
         scanR = Math.max(scanR, 5);
         branchR = Math.max(branchR, 5);
      }

      if (n.contains("CRIMSON") || n.contains("WARPED")) {
         leafR = Math.max(leafR, 9);
         scanR = Math.max(scanR, 5);
         branchR = Math.max(branchR, 5);
      }

      leafR = Math.min(14, leafR);
      scanR = Math.min(8, scanR);
      branchR = Math.min(8, branchR);
      return new NaturalTreeAxe.TreeRadii(branchR, scanR, leafR);
   }

   private NaturalTreeAxe.TreeCenter findTreeCenter(Block base, String familyKey) {
      World w = base.getWorld();
      int y = base.getY();

      for (int ox = -1; ox <= 0; ox++) {
         for (int oz = -1; oz <= 0; oz++) {
            Block b00 = w.getBlockAt(base.getX() + ox, y, base.getZ() + oz);
            Block b10 = w.getBlockAt(base.getX() + ox + 1, y, base.getZ() + oz);
            Block b01 = w.getBlockAt(base.getX() + ox, y, base.getZ() + oz + 1);
            Block b11 = w.getBlockAt(base.getX() + ox + 1, y, base.getZ() + oz + 1);
            if (this.isSameTrunkFamily(b00.getType(), familyKey)
               && this.isSameTrunkFamily(b10.getType(), familyKey)
               && this.isSameTrunkFamily(b01.getType(), familyKey)
               && this.isSameTrunkFamily(b11.getType(), familyKey)) {
               Set<Block> fp = new LinkedHashSet<>();
               fp.add(b00);
               fp.add(b10);
               fp.add(b01);
               fp.add(b11);
               int cx = base.getX() + ox + 1;
               int cz = base.getZ() + oz + 1;
               return new NaturalTreeAxe.TreeCenter(cx, cz, fp);
            }
         }
      }

      Set<Block> fp = new LinkedHashSet<>();
      fp.add(base);
      return new NaturalTreeAxe.TreeCenter(base.getX(), base.getZ(), fp);
   }

   private long blockKey(Block b) {
      long x = b.getX() & 67108863L;
      long z = b.getZ() & 67108863L;
      long y = b.getY() & 4095L;
      return x << 38 | z << 12 | y;
   }

   private void filterLeavesByNearestLogStrict(Set<Block> leaves, Set<Block> myLogs, int radius) {
      if (!leaves.isEmpty() && !myLogs.isEmpty()) {
         Set<Long> myLogKeys = new HashSet<>(myLogs.size() * 2);

         for (Block b : myLogs) {
            myLogKeys.add(this.blockKey(b));
         }

         leaves.removeIf(leaf -> {
            int distMine = this.nearestLogDist(leaf, radius, myLogKeys);
            if (distMine == Integer.MAX_VALUE) {
               return true;
            }

            int distOther = this.nearestLogDistExcluding(leaf, radius, myLogKeys);
            return distOther != Integer.MAX_VALUE && distMine >= distOther;
         });
      }
   }

   private int nearestLogDist(Block center, int radius, Set<Long> onlyTheseLogsOrNull) {
      World w = center.getWorld();
      int cx = center.getX();
      int cy = center.getY();
      int cz = center.getZ();
      int best = Integer.MAX_VALUE;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int d2 = dx * dx + dy * dy + dz * dz;
               if (d2 <= radius * radius) {
                  Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                  if (this.isTrunkMaterial(b.getType()) && (onlyTheseLogsOrNull == null || onlyTheseLogsOrNull.contains(this.blockKey(b)))) {
                     int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                     if (dist < best) {
                        best = dist;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private int nearestLogDistExcluding(Block center, int radius, Set<Long> excludeLogs) {
      World w = center.getWorld();
      int cx = center.getX();
      int cy = center.getY();
      int cz = center.getZ();
      int best = Integer.MAX_VALUE;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int d2 = dx * dx + dy * dy + dz * dz;
               if (d2 <= radius * radius) {
                  Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                  if (this.isTrunkMaterial(b.getType()) && (excludeLogs == null || !excludeLogs.contains(this.blockKey(b)))) {
                     int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                     if (dist < best) {
                        best = dist;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private NaturalTreeAxe.ScanResult scanTree(Block origin) {
      World w = origin.getWorld();
      Material trunkType = origin.getType();
      if (!this.isTrunkMaterial(trunkType)) {
         return null;
      }

      String familyKey = trunkFamilyKey(trunkType);
      Block base = this.findTrunkBase(origin, familyKey);
      NaturalTreeAxe.TreeCenter center = this.findTreeCenter(base, familyKey);
      boolean is2x2 = center.footprint.size() == 4;
      int height = this.trunkHeightFromBase(base, familyKey);
      NaturalTreeAxe.TreeRadii r = this.computeRadii(trunkType, height, is2x2);
      int maxLogRadius = Math.min(24, Math.max(r.leafTreeRadius + 6, 14));
      int maxLogRadius2 = maxLogRadius * maxLogRadius;
      int coreR = 2;
      int coreR2 = 4;
      Block soil = base.getRelative(0, -1, 0);
      if (!this.isNaturalSoil(soil.getType())) {
         String s = soil.getType().name();
         boolean nylium = s.endsWith("_NYLIUM") || s.contains("NYLIUM");
         if (!nylium) {
            return null;
         }
      }

      NaturalTreeAxe.ScanResult res = new NaturalTreeAxe.ScanResult();
      res.trunkType = trunkType;
      res.center = center;
      res.radii = r;
      Queue<Block> q = new ArrayDeque<>();
      Set<Long> visited = new HashSet<>();

      for (Block fp : center.footprint) {
         Block cur = fp;

         for (int i = 0; i < 56 && this.isSameTrunkFamily(cur.getType(), familyKey); i++) {
            int dx = cur.getX() - center.x;
            int dz = cur.getZ() - center.z;
            if (dx * dx + dz * dz > maxLogRadius2) {
               break;
            }

            long key = this.blockKey(cur);
            if (visited.add(key)) {
               q.add(cur);
               res.logs.add(cur);
               Block above = cur.getRelative(0, 1, 0);
               if (above.getType() == Material.SNOW) {
                  res.snow.add(above);
               }
            }

            cur = cur.getRelative(0, 1, 0);
         }
      }

      int baseY = base.getY();

      while (!q.isEmpty() && res.logs.size() < 220) {
         Block b = q.poll();

         for (int[] off : LOG_NEIGHBOR_OFFSETS) {
            int ox = off[0];
            int oy = off[1];
            int oz = off[2];
            Block nb = b.getRelative(ox, oy, oz);
            if (this.isSameTrunkFamily(nb.getType(), familyKey) && nb.getY() >= baseY - 1 && nb.getY() <= baseY + 56) {
               int dx = nb.getX() - center.x;
               int dz = nb.getZ() - center.z;
               int d2 = dx * dx + dz * dz;
               if (d2 <= maxLogRadius2) {
                  boolean inCore = d2 <= 4;
                  boolean lowTrunk = nb.getY() <= baseY + 2;
                  if (inCore || lowTrunk || this.logHasNearbyFoliage(nb)) {
                     boolean isDiagXZ = oy == 0 && ox != 0 && oz != 0;
                     if (!isDiagXZ || !lowTrunk && this.logHasNearbyFoliageTight(nb)) {
                        boolean isDiag3D = oy != 0 && ox != 0 && oz != 0;
                        if (!isDiag3D || !lowTrunk && oy >= 0 && this.logHasNearbyFoliageTight(nb) && this.logHasNearbyFoliageTight(b)) {
                           long k = this.blockKey(nb);
                           if (visited.add(k)) {
                              res.logs.add(nb);
                              q.add(nb);
                              Block above = nb.getRelative(0, 1, 0);
                              if (above.getType() == Material.SNOW) {
                                 res.snow.add(above);
                              }

                              if (res.logs.size() >= 220) {
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

      if (res.logs.size() < 2) {
         return null;
      }

      res.leaves.clear();
      this.seedFoliageTouchingMyLogs(res.logs, res.leaves, baseY, center, r.leafTreeRadius);
      this.expandFoliageFloodBounded(res.leaves, 520, center, r.leafTreeRadius);
      this.filterLeavesByNearestLogStrictVanilla7(res.leaves, res.logs);

      for (Block leaf : res.leaves) {
         Block above = leaf.getRelative(0, 1, 0);
         if (above.getType() == Material.SNOW) {
            res.snow.add(above);
         }
      }

      if (res.leaves.size() > 520) {
         Set<Block> cut = new LinkedHashSet<>();
         int i = 0;

         for (Block b : res.leaves) {
            cut.add(b);
            if (++i >= 520) {
               break;
            }
         }

         res.leaves.clear();
         res.leaves.addAll(cut);
      }

      res.pickAnimated(240);
      return res;
   }

   private boolean logHasNearbyFoliageTight(Block log) {
      World w = log.getWorld();
      int lx = log.getX();
      int ly = log.getY();
      int lz = log.getZ();

      for (int dy = -2; dy <= 2; dy++) {
         int y = ly + dy;

         for (int dx = -2; dx <= 2; dx++) {
            int x = lx + dx;

            for (int dz = -2; dz <= 2; dz++) {
               int z = lz + dz;
               Material m = w.getBlockAt(x, y, z).getType();
               if (this.isLeavesMaterial(m) || this.isNetherFoliageMaterial(m)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void seedFoliageTouchingMyLogs(Set<Block> myLogs, Set<Block> out, int baseY, NaturalTreeAxe.TreeCenter center, int leafTreeRadius) {
      int r2 = leafTreeRadius * leafTreeRadius;

      for (Block log : myLogs) {
         Block[] neigh = new Block[]{
            log.getRelative(1, 0, 0),
            log.getRelative(-1, 0, 0),
            log.getRelative(0, 1, 0),
            log.getRelative(0, -1, 0),
            log.getRelative(0, 0, 1),
            log.getRelative(0, 0, -1)
         };

         for (Block nb : neigh) {
            int y = nb.getY();
            if (y >= baseY - 2 && y <= baseY + 56) {
               int rx = nb.getX() - center.x;
               int rz = nb.getZ() - center.z;
               if (rx * rx + rz * rz <= r2 && this.isFoliageBlock(nb)) {
                  out.add(nb);
                  if (out.size() >= 520) {
                     return;
                  }
               }
            }
         }
      }
   }

   private void filterLeavesByNearestLogStrictVanilla7(Set<Block> leaves, Set<Block> myLogs) {
      if (!leaves.isEmpty() && !myLogs.isEmpty()) {
         Set<Long> myLogKeys = new HashSet<>(myLogs.size() * 2);

         for (Block b : myLogs) {
            myLogKeys.add(this.blockKey(b));
         }

         int R = 7;
         int r2 = 49;
         leaves.removeIf(leaf -> {
            if (leaf.getBlockData() instanceof Leaves lv) {
               if (lv.isPersistent()) {
                  return true;
               }

               int dist = lv.getDistance();
               if (dist >= 7) {
                  return true;
               }
            }

            int distMine = this.nearestLogDistWithin(leaf, 7, myLogKeys, true);
            if (distMine == Integer.MAX_VALUE) {
               return true;
            }

            int distOther = this.nearestLogDistWithin(leaf, 7, myLogKeys, false);
            return distOther != Integer.MAX_VALUE && distMine >= distOther;
         });
      }
   }

   private int nearestLogDistWithin(Block center, int radius, Set<Long> myLogKeys, boolean modeOnlyMine) {
      World w = center.getWorld();
      int cx = center.getX();
      int cy = center.getY();
      int cz = center.getZ();
      int best = Integer.MAX_VALUE;
      int r2 = radius * radius;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int d2 = dx * dx + dy * dy + dz * dz;
               if (d2 <= r2) {
                  Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                  if (this.isTrunkMaterial(b.getType())) {
                     long key = this.blockKey(b);
                     boolean isMine = myLogKeys.contains(key);
                     if (modeOnlyMine ? isMine : !isMine) {
                        int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (dist < best) {
                           best = dist;
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private Set<Block> keepOnlyFoliageConnectedToMyLogs(Set<Block> candidates, Set<Block> myLogs, NaturalTreeAxe.TreeCenter center, int leafTreeRadius, int cap) {
      if (!candidates.isEmpty() && !myLogs.isEmpty()) {
         int r2 = leafTreeRadius * leafTreeRadius;
         Set<Long> candKeys = new HashSet<>(candidates.size() * 2);

         for (Block b : candidates) {
            candKeys.add(this.blockKey(b));
         }

         ArrayDeque<Block> q = new ArrayDeque<>();
         Set<Long> seen = new HashSet<>(candidates.size() * 2);

         for (Block log : myLogs) {
            Block[] neigh = new Block[]{
               log.getRelative(1, 0, 0),
               log.getRelative(-1, 0, 0),
               log.getRelative(0, 1, 0),
               log.getRelative(0, -1, 0),
               log.getRelative(0, 0, 1),
               log.getRelative(0, 0, -1)
            };

            for (Block nb : neigh) {
               long k = this.blockKey(nb);
               if (candKeys.contains(k) && this.isFoliageBlock(nb) && seen.add(k)) {
                  int rx = nb.getX() - center.x;
                  int rz = nb.getZ() - center.z;
                  if (rx * rx + rz * rz <= r2) {
                     q.add(nb);
                  }
               }
            }
         }

         LinkedHashSet<Block> kept = new LinkedHashSet<>();

         while (!q.isEmpty() && kept.size() < cap) {
            Block b = q.poll();
            kept.add(b);

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 1; dy++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     if (dx != 0 || dy != 0 || dz != 0) {
                        Block nb = b.getRelative(dx, dy, dz);
                        int rx = nb.getX() - center.x;
                        int rz = nb.getZ() - center.z;
                        if (rx * rx + rz * rz <= r2) {
                           long k = this.blockKey(nb);
                           if (candKeys.contains(k) && seen.add(k) && this.isFoliageBlock(nb)) {
                              q.add(nb);
                           }
                        }
                     }
                  }
               }
            }
         }

         return kept;
      } else {
         return candidates;
      }
   }

   private void seedFoliageAroundLog(Block log, Set<Block> out, int scanRadius, int baseY, int leafTreeRadius, NaturalTreeAxe.TreeCenter center) {
      World w = log.getWorld();
      int cx = log.getX();
      int cy = log.getY();
      int cz = log.getZ();

      for (int dx = -scanRadius; dx <= scanRadius; dx++) {
         for (int dz = -scanRadius; dz <= scanRadius; dz++) {
            for (int dy = -2; dy <= 5; dy++) {
               int x = cx + dx;
               int y = cy + dy;
               int z = cz + dz;
               if (y >= w.getMinHeight() && y <= w.getMaxHeight() - 1 && y >= baseY - 2 && y <= baseY + 56) {
                  int rx = x - center.x;
                  int rz = z - center.z;
                  if (rx * rx + rz * rz <= leafTreeRadius * leafTreeRadius) {
                     Block b = w.getBlockAt(x, y, z);
                     if (this.isFoliageBlock(b)) {
                        out.add(b);
                        if (out.size() >= 520) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean logHasNearbyFoliage(Block log) {
      for (int dx = -2; dx <= 2; dx++) {
         for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
               if ((dx != 0 || dy != 0 || dz != 0) && this.isFoliageBlock(log.getRelative(dx, dy, dz))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void expandFoliageFloodBounded(Set<Block> leaves, int cap, NaturalTreeAxe.TreeCenter center, int leafTreeRadius) {
      if (!leaves.isEmpty()) {
         int r2 = leafTreeRadius * leafTreeRadius;
         Queue<Block> q = new ArrayDeque<>(leaves);
         Set<Long> seen = new HashSet<>(leaves.size() * 2);

         for (Block b : leaves) {
            seen.add(this.blockKey(b));
         }

         while (!q.isEmpty() && leaves.size() < cap) {
            Block b = q.poll();

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 1; dy++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     if (dx != 0 || dy != 0 || dz != 0) {
                        Block nb = b.getRelative(dx, dy, dz);
                        int rx = nb.getX() - center.x;
                        int rz = nb.getZ() - center.z;
                        if (rx * rx + rz * rz <= r2) {
                           long k = this.blockKey(nb);
                           if (seen.add(k) && this.isFoliageBlock(nb)) {
                              leaves.add(nb);
                              q.add(nb);
                              if (leaves.size() >= cap) {
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

   private Location computePivot(Set<Block> logs) {
      Block lowest = null;

      for (Block b : logs) {
         if (lowest == null || b.getY() < lowest.getY()) {
            lowest = b;
         }
      }

      return lowest == null ? logs.iterator().next().getLocation().add(0.5, 0.5, 0.5) : lowest.getLocation().add(0.5, 0.5, 0.5);
   }

   private Vector computeFallDir(Location pivot, Player p) {
      Vector dir = pivot.toVector().subtract(p.getLocation().toVector());
      dir.setY(0);
      if (dir.lengthSquared() < 1.0E-4) {
         dir = p.getLocation().getDirection();
         dir.setY(0);
      }

      if (dir.lengthSquared() < 1.0E-4) {
         dir = new Vector(1, 0, 0);
      }

      return dir.normalize();
   }

   private Vector computeAxis(Vector fallDir) {
      Vector axis = new Vector(0, 1, 0).crossProduct(fallDir);
      return axis.lengthSquared() < 1.0E-4 ? new Vector(1, 0, 0) : axis.normalize();
   }

   private static Vector rotateVector(Vector v, Quaternionf q) {
      Vector3f in = new Vector3f((float)v.getX(), (float)v.getY(), (float)v.getZ());
      q.transform(in);
      return new Vector(in.x, in.y, in.z);
   }

   private NaturalTreeAxe.DisplayEntry spawnBlockDisplayFor(World w, Block b) {
      try {
         BlockData data = b.getBlockData();
         Location at = b.getLocation().add(0.5, 0.5, 0.5);
         BlockDisplay d = (BlockDisplay)w.spawn(at, BlockDisplay.class, ent -> {
            ent.setBlock(data);
            ent.setViewRange(0.85F);
            ent.setShadowRadius(0.0F);
            ent.setShadowStrength(0.0F);
            ent.setInterpolationDelay(0);
            ent.setInterpolationDuration(2);
            ent.setTeleportDuration(2);
         });
         return new NaturalTreeAxe.DisplayEntry(d, at.toVector());
      } catch (Throwable t) {
         return null;
      }
   }

   private List<ItemStack> safeDrops(Block b, ItemStack tool, Player p) {
      try {
         Collection<ItemStack> drops = b.getDrops(tool, p);
         return drops == null ? Collections.emptyList() : new ArrayList<>(drops);
      } catch (Throwable t) {
         return Collections.emptyList();
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
   public void onBlockBreak(BlockBreakEvent e) {
      Player p = e.getPlayer();
      if (p != null) {
         if (this.active.containsKey(p.getUniqueId())) {
            Material bt = e.getBlock().getType();
            if (this.isTrunkMaterial(bt) || this.isLeavesMaterial(bt) || this.isNetherFoliageMaterial(bt)) {
               e.setCancelled(true);
            }
         } else {
            ItemStack axe = p.getInventory().getItemInMainHand();
            if (this.isNaturalTreeAxe(axe)) {
               if (!p.isSneaking()) {
                  Block origin = e.getBlock();
                  Material t = origin.getType();
                  if (this.isTrunkMaterial(t)) {
                     if (this.quickLooksLikeTree(origin)) {
                        NaturalTreeAxe.ScanResult scan = this.scanTree(origin);
                        if (scan != null) {
                           e.setCancelled(true);
                           this.startFellDisplayAnimation(p, axe, origin, scan);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void startFellDisplayAnimation(Player p, ItemStack axe, Block origin, NaturalTreeAxe.ScanResult scan) {
      World w = origin.getWorld();
      List<ItemStack> allDrops = new ArrayList<>(scan.logs.size() * 2 + scan.leaves.size());

      for (Block b : scan.logs) {
         allDrops.addAll(this.safeDrops(b, axe, p));
      }

      for (Block b : scan.leaves) {
         allDrops.addAll(this.safeDrops(b, axe, p));
      }

      allDrops.removeIf(it -> it == null || it.getType() == Material.AIR);
      Location pivot = this.computePivot(scan.logs);
      Vector fallDir = this.computeFallDir(pivot, p);
      Vector axis = this.computeAxis(fallDir);
      List<NaturalTreeAxe.DisplayEntry> entries = new ArrayList<>(scan.animLogs.size() + scan.animLeaves.size());

      for (Block b : scan.animLogs) {
         NaturalTreeAxe.DisplayEntry de = this.spawnBlockDisplayFor(w, b);
         if (de != null) {
            entries.add(de);
         }
      }

      for (Block b : scan.animLeaves) {
         NaturalTreeAxe.DisplayEntry de = this.spawnBlockDisplayFor(w, b);
         if (de != null) {
            entries.add(de);
         }
      }

      for (Block b : scan.logs) {
         b.setType(Material.AIR, false);
      }

      for (Block b : scan.leaves) {
         b.setType(Material.AIR, false);
      }

      for (Block s : scan.snow) {
         if (s.getType() == Material.SNOW) {
            s.setType(Material.AIR, false);
         }
      }

      w.playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.9F, 0.95F);
      NaturalTreeAxe.FellSession session = new NaturalTreeAxe.FellSession(p.getUniqueId(), w, pivot, fallDir, axis, entries, allDrops, scan.logs.size());
      this.active.put(p.getUniqueId(), session);
      session.start();
   }

   private void damageAxe(Player p, ItemStack axe, int logsBroken) {
      if (axe != null) {
         if (axe.getItemMeta() instanceof Damageable dmg) {
            int damage = dmg.getDamage();
            int toAdd = Math.max(1, logsBroken);
            int newDamage = damage + toAdd;
            short max = axe.getType().getMaxDurability();
            if (newDamage >= max) {
               p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
               p.getInventory().setItemInMainHand(null);
            } else {
               dmg.setDamage(newDamage);
               axe.setItemMeta(dmg);
               p.getInventory().setItemInMainHand(axe);
            }
         }
      }
   }

   private static final class DisplayEntry {
      final BlockDisplay display;
      final Vector originalCenter;

      DisplayEntry(BlockDisplay display, Vector originalCenter) {
         this.display = display;
         this.originalCenter = originalCenter;
      }
   }

   private final class FellSession {
      private final UUID playerId;
      private final World world;
      private final Location pivot;
      private final Vector fallDir;
      private final Vector axis;
      private final List<NaturalTreeAxe.DisplayEntry> entries;
      private final List<ItemStack> drops;
      private final int logsCountForDurability;
      private int tick = 0;
      private BukkitRunnable task;

      FellSession(
         UUID playerId,
         World world,
         Location pivot,
         Vector fallDir,
         Vector axis,
         List<NaturalTreeAxe.DisplayEntry> entries,
         List<ItemStack> drops,
         int logsCountForDurability
      ) {
         this.playerId = playerId;
         this.world = world;
         this.pivot = pivot;
         this.fallDir = fallDir;
         this.axis = axis;
         this.entries = entries;
         this.drops = drops;
         this.logsCountForDurability = logsCountForDurability;
      }

      void start() {
         this.task = new BukkitRunnable() {
            public void run() {
               Player p = Bukkit.getPlayer(FellSession.this.playerId);
               if (p != null && p.isOnline() && p.getWorld() == FellSession.this.world) {
                  FellSession.this.tick++;
                  float t = Math.min(1.0F, FellSession.this.tick / 80.0F);
                  float ease = 1.0F - (float)Math.pow(1.0F - t, 2.2F);
                  float angleRad = (float)(ease * (Math.PI / 2));
                  Quaternionf rot = new Quaternionf()
                     .fromAxisAngleRad((float)FellSession.this.axis.getX(), (float)FellSession.this.axis.getY(), (float)FellSession.this.axis.getZ(), angleRad);
                  Vector pivotV = FellSession.this.pivot.toVector();

                  for (NaturalTreeAxe.DisplayEntry de : FellSession.this.entries) {
                     BlockDisplay d = de.display;
                     if (d != null && !d.isDead()) {
                        Vector rel = de.originalCenter.clone().subtract(pivotV);
                        Vector rotated = NaturalTreeAxe.rotateVector(rel, rot);
                        Vector newPos = pivotV.clone().add(rotated);
                        Location loc = new Location(FellSession.this.world, newPos.getX(), newPos.getY(), newPos.getZ());
                        d.teleport(loc);
                        Transformation tr = new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), rot, new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf());
                        d.setTransformation(tr);
                        if (FellSession.this.tick % 5 == 0) {
                           Material m = d.getBlock().getMaterial();
                           if (m.name().endsWith("_LOG") || m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE")) {
                              FellSession.this.world.spawnParticle(Particle.BLOCK, loc, 2, 0.08, 0.08, 0.08, 0.0, d.getBlock());
                           } else if (m.name().endsWith("_LEAVES") || m == Material.NETHER_WART_BLOCK || m == Material.WARPED_WART_BLOCK) {
                              FellSession.this.world.spawnParticle(Particle.BLOCK, loc, 1, 0.08, 0.08, 0.08, 0.0, d.getBlock());
                           }
                        }
                     }
                  }

                  if (FellSession.this.tick % 6 == 0) {
                     FellSession.this.world.playSound(FellSession.this.pivot, Sound.BLOCK_WOOD_HIT, 0.55F, 0.85F);
                  }

                  if (t >= 1.0F) {
                     this.cancel();
                     FellSession.this.finish(p);
                  }
               } else {
                  this.cancel();
                  FellSession.this.cleanup(true);
               }
            }
         };
         this.task.runTaskTimer(NaturalTreeAxe.this.plugin, 1L, 1L);
      }

      void finish(Player p) {
         this.cleanup(false);
         Location dropAt = this.pivot.clone().add(this.fallDir.clone().multiply(1.4)).add(0.0, 0.2, 0.0);

         for (ItemStack it : this.drops) {
            if (it != null && it.getType() != Material.AIR) {
               this.world.dropItemNaturally(dropAt, it);
            }
         }

         this.world.playSound(dropAt, Sound.BLOCK_WOOD_BREAK, 1.0F, 0.7F);
         this.world.playSound(dropAt, Sound.ENTITY_GENERIC_EXPLODE, 0.25F, 1.4F);
         ItemStack axeNow = p.getInventory().getItemInMainHand();
         if (axeNow != null && NaturalTreeAxe.this.isNaturalTreeAxe(axeNow)) {
            NaturalTreeAxe.this.damageAxe(p, axeNow, this.logsCountForDurability);
         }

         NaturalTreeAxe.this.active.remove(this.playerId);
      }

      void cleanup(boolean removeSession) {
         for (NaturalTreeAxe.DisplayEntry de : this.entries) {
            try {
               if (de.display != null && !de.display.isDead()) {
                  de.display.remove();
               }
            } catch (Throwable var5) {
            }
         }

         if (removeSession) {
            NaturalTreeAxe.this.active.remove(this.playerId);
         }
      }

      void cancel() {
         try {
            if (this.task != null) {
               this.task.cancel();
            }
         } catch (Throwable var2) {
         }
      }
   }

   private static final class ScanResult {
      final Set<Block> logs = new LinkedHashSet<>();
      Set<Block> leaves = new LinkedHashSet<>();
      final Set<Block> snow = new LinkedHashSet<>();
      final Set<Block> animLogs = new LinkedHashSet<>();
      final Set<Block> animLeaves = new LinkedHashSet<>();
      Material trunkType;
      NaturalTreeAxe.TreeCenter center;
      NaturalTreeAxe.TreeRadii radii;

      void pickAnimated(int maxTotal) {
         int left = maxTotal;

         for (Block b : this.logs) {
            if (left-- <= 0) {
               break;
            }

            this.animLogs.add(b);
         }

         for (Block b : this.leaves) {
            if (left-- <= 0) {
               break;
            }

            this.animLeaves.add(b);
         }
      }
   }

   private static final class TreeCenter {
      final int x;
      final int z;
      final Set<Block> footprint;

      TreeCenter(int x, int z, Set<Block> footprint) {
         this.x = x;
         this.z = z;
         this.footprint = footprint;
      }
   }

   private static final class TreeRadii {
      final int branchRadius;
      final int leafScanRadius;
      final int leafTreeRadius;

      TreeRadii(int branchRadius, int leafScanRadius, int leafTreeRadius) {
         this.branchRadius = branchRadius;
         this.leafScanRadius = leafScanRadius;
         this.leafTreeRadius = leafTreeRadius;
      }
   }
}
