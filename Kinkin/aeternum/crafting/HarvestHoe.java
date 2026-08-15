package Kinkin.aeternum.crafting;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.food.SeasonFoods;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HarvestHoe implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final NamespacedKey itemTagKey;
   private final NamespacedKey recipeKey;
   private static final int HARVEST_RADIUS = 2;

   public HarvestHoe(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.itemTagKey = new NamespacedKey(plugin, "harvest_hoe_item");
      this.recipeKey = new NamespacedKey(plugin, "harvest_hoe");
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.registerRecipe();
   }

   public void unregister() {
      HandlerList.unregisterAll(this);
      Bukkit.removeRecipe(this.recipeKey);
   }

   private void registerRecipe() {
      ItemStack dummy = new ItemStack(Material.IRON_HOE);
      ShapedRecipe recipe = new ShapedRecipe(this.recipeKey, dummy);
      recipe.shape(new String[]{"WWW", "EHE", "EEE"});
      Material[] hoeMats = Arrays.stream(Material.values()).filter(Material::isItem).filter(m -> m.name().endsWith("_HOE")).toArray(Material[]::new);
      RecipeChoice hoeChoice = new MaterialChoice(hoeMats);
      recipe.setIngredient('W', Material.WHEAT);
      recipe.setIngredient('H', hoeChoice);
      recipe.setIngredient('E', Material.EMERALD);
      Bukkit.addRecipe(recipe);
   }

   @EventHandler
   public void onPrepareCraft(PrepareItemCraftEvent e) {
      if (e.getRecipe() != null) {
         if (e.getRecipe() instanceof ShapedRecipe shaped) {
            if (this.recipeKey.equals(shaped.getKey())) {
               ItemStack[] matrix = e.getInventory().getMatrix();
               ItemStack foundHoe = null;
               int wheat = 0;
               int emerald = 0;

               for (ItemStack stack : matrix) {
                  if (stack != null && stack.getType() != Material.AIR) {
                     Material t = stack.getType();
                     if (this.isHoe(t)) {
                        if (foundHoe != null) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        foundHoe = stack;
                     } else if (t == Material.WHEAT) {
                        wheat += stack.getAmount();
                     } else {
                        if (t != Material.EMERALD) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        emerald += stack.getAmount();
                     }
                  }
               }

               if (foundHoe != null && wheat >= 1 && emerald >= 1) {
                  Player crafter = null;
                  if (e.getView().getPlayer() instanceof Player pl) {
                     crafter = pl;
                  }

                  ItemStack result = new ItemStack(foundHoe.getType());
                  ItemMeta meta = result.getItemMeta();
                  if (meta != null) {
                     PersistentDataContainer pdc = meta.getPersistentDataContainer();
                     pdc.set(this.itemTagKey, PersistentDataType.BYTE, (byte)1);

                     try {
                        String name = this.plugin.lang.tr(crafter, "item.harvest_hoe.name");
                        String lore1 = this.plugin.lang.tr(crafter, "item.harvest_hoe.lore1");
                        String lore2 = this.plugin.lang.tr(crafter, "item.harvest_hoe.lore2");
                        String lore3 = this.plugin.lang.tr(crafter, "item.harvest_hoe.lore3");
                        meta.setDisplayName(name);
                        meta.setLore(Arrays.asList(lore1, lore2, lore3));
                     } catch (Throwable var15) {
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

   private boolean isHoe(Material m) {
      if (m == null) {
         return false;
      }

      if (m.name().endsWith("_HOE")) {
         return true;
      }

      NamespacedKey k = m.getKey();
      return k != null && "minecraft".equals(k.getNamespace()) && k.getKey().endsWith("_hoe");
   }

   private boolean isHarvestHoe(ItemStack stack) {
      if (stack == null) {
         return false;
      }

      if (!this.isHoe(stack.getType())) {
         return false;
      }

      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return false;
      }

      Byte flag = (Byte)meta.getPersistentDataContainer().get(this.itemTagKey, PersistentDataType.BYTE);
      return flag != null && flag == 1;
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
   public void onBlockBreak(BlockBreakEvent e) {
      Player p = e.getPlayer();
      if (p != null) {
         ItemStack hoe = p.getInventory().getItemInMainHand();
         if (this.isHarvestHoe(hoe)) {
            Block center = e.getBlock();
            if (center.getBlockData() instanceof Ageable ageableCenter) {
               if (this.isMature(ageableCenter)) {
                  e.setCancelled(true);
                  this.harvestArea(p, hoe, center);
               }
            }
         }
      }
   }

   private boolean isMature(Ageable data) {
      return data.getAge() >= data.getMaximumAge();
   }

   private void harvestArea(Player p, ItemStack hoe, Block center) {
      World w = center.getWorld();
      List<HarvestHoe.HarvestTarget> targets = new ArrayList<>();
      this.addIfMature(targets, center);

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (dx != 0 || dz != 0) {
               Block b = w.getBlockAt(center.getX() + dx, center.getY(), center.getZ() + dz);
               this.addIfMature(targets, b);
            }
         }
      }

      if (!targets.isEmpty()) {
         Map<HarvestHoe.SeedKey, Integer> need = new HashMap<>();

         for (HarvestHoe.HarvestTarget t : targets) {
            if (t.canReplant) {
               need.merge(t.seed, 1, Integer::sum);
            }
         }

         Map<HarvestHoe.SeedKey, Integer> availableForPlant = new HashMap<>();

         for (Entry<HarvestHoe.SeedKey, Integer> e : need.entrySet()) {
            HarvestHoe.SeedKey seed = e.getKey();
            int required = e.getValue();
            int removed = this.removeUpToFromPlayer(p, seed, required);
            availableForPlant.put(seed, removed);
         }

         int harvested = 0;

         for (HarvestHoe.HarvestTarget t : targets) {
            Block b = t.block;
            int seedsLeft = t.canReplant ? availableForPlant.getOrDefault(t.seed, 0) : 0;
            boolean replant = seedsLeft > 0;
            SeasonFoods seasonFoods = this.plugin.getSeasonFoods();
            Collection<ItemStack> drops;
            if (t.artificialCropId != null && seasonFoods != null) {
               ItemStack customDrop = seasonFoods.createArtificialCropDrop(t.artificialCropId, p, 2 + ThreadLocalRandom.current().nextInt(4));
               drops = customDrop != null ? List.of(customDrop) : List.of();
            } else {
               drops = b.getDrops(hoe, p);
            }

            b.setType(Material.AIR, false);

            for (ItemStack drop : drops) {
               if (drop != null && drop.getType() != Material.AIR && drop.getAmount() > 0) {
                  w.dropItemNaturally(b.getLocation().add(0.5, 0.2, 0.5), drop);
               }
            }

            if (t.artificialCropId != null && seasonFoods != null) {
               seasonFoods.finishArtificialHarvest(b, t.artificialCropId, replant);
            } else if (replant) {
               b.setType(t.cropType, false);
               if (b.getBlockData() instanceof Ageable newAge) {
                  newAge.setAge(0);
                  b.setBlockData(newAge, false);
               }
            }

            if (replant) {
               availableForPlant.put(t.seed, seedsLeft - 1);
               w.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.4, 0.5), 4, 0.2, 0.2, 0.2);
            }

            harvested++;
         }

         if (harvested > 0) {
            this.damageHoe(p, hoe, harvested);
            w.playSound(center.getLocation(), Sound.ITEM_HOE_TILL, 0.8F, 1.1F);
         }
      }
   }

   private void addIfMature(List<HarvestHoe.HarvestTarget> out, Block b) {
      if (b.getBlockData() instanceof Ageable age) {
         if (this.isMature(age)) {
            Material crop = b.getType();
            SeasonFoods seasonFoods = this.plugin.getSeasonFoods();
            String artificialCropId = seasonFoods != null ? seasonFoods.getArtificialCropId(b) : null;
            if (artificialCropId != null) {
               Material seedMaterial = seasonFoods.getArtificialSeedMaterial(artificialCropId);
               if (seedMaterial != null) {
                  Block below = b.getRelative(0, -1, 0);
                  out.add(
                     new HarvestHoe.HarvestTarget(
                        b, crop, new HarvestHoe.SeedKey(seedMaterial, artificialCropId), below.getType() == Material.FARMLAND, artificialCropId
                     )
                  );
               }
            } else {
               Material seed = this.seedForCrop(crop);
               if (seed == null) {
                  out.add(new HarvestHoe.HarvestTarget(b, crop, new HarvestHoe.SeedKey(Material.AIR, null), false, null));
               } else {
                  Block below = b.getRelative(0, -1, 0);
                  boolean can = this.canReplantOn(crop, below.getType());
                  out.add(new HarvestHoe.HarvestTarget(b, crop, new HarvestHoe.SeedKey(seed, null), can, null));
               }
            }
         }
      }
   }

   private boolean canReplantOn(Material crop, Material below) {
      return crop == Material.NETHER_WART ? below == Material.SOUL_SAND : below == Material.FARMLAND;
   }

   private Material seedForCrop(Material crop) {
      switch (crop) {
         case WHEAT:
            return Material.WHEAT_SEEDS;
         case CARROTS:
            return Material.CARROT;
         case POTATOES:
            return Material.POTATO;
         case BEETROOTS:
            return Material.BEETROOT_SEEDS;
         case NETHER_WART:
            return Material.NETHER_WART;
         default:
            return null;
      }
   }

   private int removeUpToFromPlayer(Player p, HarvestHoe.SeedKey seed, int amount) {
      if (amount <= 0) {
         return 0;
      }

      PlayerInventory inv = p.getInventory();
      int removed = 0;
      ItemStack[] storage = inv.getStorageContents();
      boolean changed = false;

      for (int i = 0; i < storage.length && removed < amount; i++) {
         ItemStack s = storage[i];
         if (this.matchesSeed(s, seed)) {
            int take = Math.min(s.getAmount(), amount - removed);
            s.setAmount(s.getAmount() - take);
            removed += take;
            changed = true;
            if (s.getAmount() <= 0) {
               storage[i] = null;
            }
         }
      }

      if (changed) {
         inv.setStorageContents(storage);
      }

      if (removed < amount) {
         ItemStack off = inv.getItemInOffHand();
         if (this.matchesSeed(off, seed)) {
            int take = Math.min(off.getAmount(), amount - removed);
            off.setAmount(off.getAmount() - take);
            removed += take;
            if (off.getAmount() <= 0) {
               inv.setItemInOffHand(null);
            } else {
               inv.setItemInOffHand(off);
            }
         }
      }

      return removed;
   }

   private boolean matchesSeed(ItemStack stack, HarvestHoe.SeedKey seed) {
      if (stack != null && stack.getType() == seed.material) {
         SeasonFoods seasonFoods = this.plugin.getSeasonFoods();
         return seed.artificialCropId != null
            ? seasonFoods != null && seasonFoods.isArtificialSeed(stack, seed.artificialCropId)
            : seasonFoods == null || !seasonFoods.isAnyArtificialSeed(stack);
      } else {
         return false;
      }
   }

   private void damageHoe(Player p, ItemStack hoe, int harvestedBlocks) {
      if (hoe != null) {
         if (hoe.getItemMeta() instanceof Damageable dmg) {
            int damage = dmg.getDamage();
            int toAdd = Math.max(1, harvestedBlocks);
            int newDamage = damage + toAdd;
            short max = hoe.getType().getMaxDurability();
            if (newDamage >= max) {
               p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
               p.getInventory().setItemInMainHand(null);
            } else {
               dmg.setDamage(newDamage);
               hoe.setItemMeta(dmg);
               p.getInventory().setItemInMainHand(hoe);
            }
         }
      }
   }

   private record HarvestTarget(Block block, Material cropType, HarvestHoe.SeedKey seed, boolean canReplant, String artificialCropId) {
   }

   private record SeedKey(Material material, String artificialCropId) {
   }
}
