package Kinkin.aeternum.food;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.lang.LanguageManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class SeasonFoods implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final LanguageManager lang;
   private final NamespacedKey FOOD_ID_KEY;
   private static final String ID_TOMATO = "tomato";
   private static final String ID_ONION = "onion";
   private static final String ID_RICE = "rice";
   private static final String ID_TOMATO_SALAD = "tomato_salad";
   private static final String ID_VEGETABLE_BREAD = "vegetable_bread";
   private static final String ID_MEAT_SANDWICH = "meat_sandwich";
   private static final String ID_BEEF_RICE_STEW = "beef_rice_stew";
   private static final String ID_COFFEE = "coffee";
   private static final String ID_HERBAL_TEA = "herbal_tea";
   private static final String ID_ENERGY_DRINK = "energy_drink";
   private static final String ID_HOT_CHOCOLATE = "hot_chocolate";
   private static final int CMD_TOMATO = 2301;
   private static final int CMD_ONION = 2302;
   private static final int CMD_RICE = 2303;
   private static final int CMD_TOMATO_SALAD = 2304;
   private static final int CMD_VEGETABLE_BREAD = 2305;
   private static final int CMD_MEAT_SANDWICH = 2306;
   private static final int CMD_BEEF_RICE_STEW = 2307;
   private static final int CMD_COFFEE = 2310;
   private static final int CMD_HERBAL_TEA = 2311;
   private static final int CMD_ENERGY_DRINK = 2312;
   private static final int CMD_HOT_CHOCOLATE = 2313;
   private static final Set<String> DISH_IDS = new HashSet<>(
      Arrays.asList("tomato_salad", "vegetable_bread", "meat_sandwich", "beef_rice_stew", "coffee", "herbal_tea", "energy_drink", "hot_chocolate")
   );
   private static final String[] RECIPE_KEYS = new String[]{
      "tomato_salad", "vegetable_bread", "meat_sandwich", "beef_rice_stew", "coffee", "herbal_tea", "energy_drink", "hot_chocolate"
   };
   private ItemStack tomatoProto;
   private ItemStack onionProto;
   private ItemStack riceProto;
   private final Map<String, String> customCrops = new HashMap<>();
   private final Set<String> riceBases = Collections.synchronizedSet(new HashSet<>());
   private final File cropsFile;
   private BukkitTask saveTask;
   private BukkitTask visualRefreshTask;
   private volatile boolean cropsDirty = false;
   private final Map<String, EnumSet<Season>> growthSeasons = Map.of(
      "tomato",
      EnumSet.of(Season.SUMMER, Season.AUTUMN),
      "onion",
      EnumSet.of(Season.SPRING, Season.SUMMER),
      "rice",
      EnumSet.of(Season.SPRING, Season.SUMMER, Season.AUTUMN)
   );
   private static final double CROP_VISUAL_RANGE_SQUARED = 16384.0;

   private void removeRecipes() {
      for (String k : RECIPE_KEYS) {
         Bukkit.removeRecipe(new NamespacedKey(this.plugin, k));
      }
   }

   public SeasonFoods(AeternumSeasonsPlugin plugin, LanguageManager lang) {
      this.plugin = plugin;
      this.lang = lang;
      this.FOOD_ID_KEY = new NamespacedKey(plugin, "food_id");
      this.cropsFile = new File(plugin.getDataFolder(), "seasonfoods_crops.yml");
   }

   public void register() {
      this.buildIngredientPrototypes();
      this.registerRecipes();
      this.loadCrops();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.startVisualRefresh();

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.removeFoodGlint(player);
      }
   }

   public void unregister() {
      if (this.visualRefreshTask != null) {
         this.visualRefreshTask.cancel();
         this.visualRefreshTask = null;
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.restoreRealCropBlocks(player);
      }

      HandlerList.unregisterAll(this);
      this.removeRecipes();
      this.saveCropsNow();
      if (this.saveTask != null) {
         this.saveTask.cancel();
         this.saveTask = null;
      }
   }

   private void buildIngredientPrototypes() {
      this.tomatoProto = this.buildIngredient("tomato", Material.BEETROOT_SEEDS, 2301);
      this.onionProto = this.buildIngredient("onion", Material.WHEAT_SEEDS, 2302);
      this.riceProto = this.buildIngredient("rice", Material.BEETROOT_SEEDS, 2303);
   }

   private ItemStack buildIngredient(String id, Material base, int cmd) {
      ItemStack item = new ItemStack(base);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }

      String baseKey = "items.food." + id;
      meta.setDisplayName(this.lang.trServer(baseKey + ".name"));
      List<String> lore = new ArrayList<>();
      lore.add(this.lang.trServer(baseKey + ".lore_1"));
      lore.add(this.lang.trServer(baseKey + ".lore_2"));
      this.appendGrowthLore(null, lore, id);
      meta.setLore(lore);
      meta.setCustomModelData(cmd);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS});
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(this.FOOD_ID_KEY, PersistentDataType.STRING, id);
      item.setItemMeta(meta);
      return item;
   }

   private void appendGrowthLore(Player p, List<String> lore, String cropId) {
      EnumSet<Season> ss = this.growthSeasons.get(cropId);
      if (ss != null && !ss.isEmpty()) {
         if (lore != null) {
            String seasonsText = ss.stream()
               .map(s -> p != null ? this.lang.tr(p, "season." + s.name()) : this.lang.trServer("season." + s.name()))
               .collect(Collectors.joining(", "));
            String template = p != null ? this.lang.tr(p, "items.food.grows_in") : this.lang.trServer("items.food.grows_in");
            String line = template.replace("{seasons}", seasonsText);
            if (!lore.contains(line)) {
               lore.add(line);
            }
         }
      }
   }

   private void registerRecipes() {
      ItemStack tomatoSalad = this.buildDishBase("tomato_salad", Material.MUSHROOM_STEW, 2304);
      NamespacedKey saladKey = new NamespacedKey(this.plugin, "tomato_salad");
      ShapelessRecipe saladRecipe = new ShapelessRecipe(saladKey, tomatoSalad);
      saladRecipe.addIngredient(Material.BEETROOT_SEEDS);
      saladRecipe.addIngredient(Material.BEETROOT_SEEDS);
      saladRecipe.addIngredient(Material.BOWL);
      Bukkit.addRecipe(saladRecipe);
      ItemStack vegBread = this.buildDishBase("vegetable_bread", Material.BREAD, 2305);
      NamespacedKey vegKey = new NamespacedKey(this.plugin, "vegetable_bread");
      ShapedRecipe vegRecipe = new ShapedRecipe(vegKey, vegBread);
      vegRecipe.shape(new String[]{" T ", "BO ", "   "});
      vegRecipe.setIngredient('B', Material.BREAD);
      vegRecipe.setIngredient('T', Material.BEETROOT_SEEDS);
      vegRecipe.setIngredient('O', Material.WHEAT_SEEDS);
      Bukkit.addRecipe(vegRecipe);
      ItemStack meatSandwich = this.buildDishBase("meat_sandwich", Material.BREAD, 2306);
      NamespacedKey sandwichKey = new NamespacedKey(this.plugin, "meat_sandwich");
      ShapelessRecipe sandwichRecipe = new ShapelessRecipe(sandwichKey, meatSandwich);
      sandwichRecipe.addIngredient(Material.BREAD);
      sandwichRecipe.addIngredient(
         new MaterialChoice(
            new Material[]{
               Material.COOKED_BEEF,
               Material.COOKED_MUTTON,
               Material.COOKED_PORKCHOP,
               Material.COOKED_CHICKEN,
               Material.COOKED_RABBIT,
               Material.COOKED_SALMON,
               Material.COOKED_COD
            }
         )
      );
      sandwichRecipe.addIngredient(Material.BEETROOT_SEEDS);
      sandwichRecipe.addIngredient(Material.WHEAT_SEEDS);
      Bukkit.addRecipe(sandwichRecipe);
      ItemStack beefRiceStew = this.buildDishBase("beef_rice_stew", Material.RABBIT_STEW, 2307);
      NamespacedKey stewKey = new NamespacedKey(this.plugin, "beef_rice_stew");
      ShapelessRecipe stewRecipe = new ShapelessRecipe(stewKey, beefRiceStew);
      stewRecipe.addIngredient(Material.BOWL);
      stewRecipe.addIngredient(
         new MaterialChoice(new Material[]{Material.COOKED_BEEF, Material.COOKED_MUTTON, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN})
      );
      stewRecipe.addIngredient(Material.BEETROOT_SEEDS);
      Bukkit.addRecipe(stewRecipe);
      ItemStack coffee = this.buildDishBase("coffee", Material.HONEY_BOTTLE, 2310);
      NamespacedKey coffeeKey = new NamespacedKey(this.plugin, "coffee");
      ShapelessRecipe coffeeRecipe = new ShapelessRecipe(coffeeKey, coffee);
      coffeeRecipe.addIngredient(Material.GLASS_BOTTLE);
      coffeeRecipe.addIngredient(Material.COCOA_BEANS);
      coffeeRecipe.addIngredient(Material.SUGAR);
      Bukkit.addRecipe(coffeeRecipe);
      ItemStack herbalTea = this.buildDishBase("herbal_tea", Material.HONEY_BOTTLE, 2311);
      NamespacedKey teaKey = new NamespacedKey(this.plugin, "herbal_tea");
      ShapelessRecipe teaRecipe = new ShapelessRecipe(teaKey, herbalTea);
      teaRecipe.addIngredient(Material.GLASS_BOTTLE);
      teaRecipe.addIngredient(
         new MaterialChoice(
            new Material[]{
               Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.BLUE_ORCHID, Material.ALLIUM, Material.CORNFLOWER, Material.OXEYE_DAISY
            }
         )
      );
      Bukkit.addRecipe(teaRecipe);
      ItemStack energyDrink = this.buildDishBase("energy_drink", Material.HONEY_BOTTLE, 2312);
      NamespacedKey energyKey = new NamespacedKey(this.plugin, "energy_drink");
      ShapelessRecipe energyRecipe = new ShapelessRecipe(energyKey, energyDrink);
      energyRecipe.addIngredient(Material.GLASS_BOTTLE);
      energyRecipe.addIngredient(Material.SUGAR);
      energyRecipe.addIngredient(Material.REDSTONE);
      energyRecipe.addIngredient(Material.GLOWSTONE_DUST);
      Bukkit.addRecipe(energyRecipe);
      ItemStack hotChocolate = this.buildDishBase("hot_chocolate", Material.HONEY_BOTTLE, 2313);
      NamespacedKey chocoKey = new NamespacedKey(this.plugin, "hot_chocolate");
      ShapelessRecipe chocoRecipe = new ShapelessRecipe(chocoKey, hotChocolate);
      chocoRecipe.addIngredient(Material.GLASS_BOTTLE);
      chocoRecipe.addIngredient(Material.COCOA_BEANS);
      chocoRecipe.addIngredient(Material.MILK_BUCKET);
      Bukkit.addRecipe(chocoRecipe);
   }

   private ItemStack buildDishBase(String id, Material base, int cmd) {
      ItemStack item = new ItemStack(base);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }

      String baseKey = "items.food." + id;
      meta.setDisplayName(this.lang.trServer(baseKey + ".name"));
      meta.setLore(Arrays.asList(this.lang.trServer(baseKey + ".lore_1"), this.lang.trServer(baseKey + ".lore_2")));
      meta.setCustomModelData(cmd);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS});
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(this.FOOD_ID_KEY, PersistentDataType.STRING, id);
      item.setItemMeta(meta);
      return item;
   }

   private String getFoodId(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return null;
         }

         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         return (String)pdc.get(this.FOOD_ID_KEY, PersistentDataType.STRING);
      } else {
         return null;
      }
   }

   private void applyDishLocalization(Player p, ItemStack item, String foodId) {
      if (foodId != null && item != null && DISH_IDS.contains(foodId)) {
         if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
               meta.removeEnchant(Enchantment.LURE);
               String baseKey = "items.food." + foodId;
               meta.setDisplayName(this.lang.tr(p, baseKey + ".name"));
               meta.setLore(Arrays.asList(this.lang.tr(p, baseKey + ".lore_1"), this.lang.tr(p, baseKey + ".lore_2")));
               item.setItemMeta(meta);
            }
         }
      }
   }

   private String locKey(Block b) {
      return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
   }

   private void loadCrops() {
      try {
         this.customCrops.clear();
         this.riceBases.clear();
         if (!this.cropsFile.exists()) {
            return;
         }

         YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.cropsFile);
         ConfigurationSection cc = cfg.getConfigurationSection("custom_crops");
         if (cc != null) {
            for (String k : cc.getKeys(false)) {
               String id = cc.getString(k);
               if (id != null) {
                  this.customCrops.put(k, id);
               }
            }
         }

         List<String> rb = cfg.getStringList("rice_bases");
         if (rb != null && !rb.isEmpty()) {
            this.riceBases.addAll(rb);
         }
      } catch (Exception ex) {
         this.plugin.getLogger().warning("[SeasonFoods] Could not load seasonfoods_crops.yml: " + ex.getMessage());
      }
   }

   private void markCropsDirty() {
      this.cropsDirty = true;
      if (this.saveTask == null) {
         this.saveTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.saveTask = null;
            this.saveCropsNow();
         }, 100L);
      }
   }

   private void saveCropsNow() {
      if (this.cropsDirty) {
         try {
            this.plugin.getDataFolder().mkdirs();
            YamlConfiguration cfg = new YamlConfiguration();

            for (Entry<String, String> e : this.customCrops.entrySet()) {
               cfg.set("custom_crops." + e.getKey(), e.getValue());
            }

            cfg.set("rice_bases", new ArrayList<>(this.riceBases));
            cfg.save(this.cropsFile);
            this.cropsDirty = false;
         } catch (IOException ex) {
            this.plugin.getLogger().warning("[SeasonFoods] Could not save seasonfoods_crops.yml: " + ex.getMessage());
            this.cropsDirty = true;
         }
      }
   }

   @EventHandler
   public void onCraft(CraftItemEvent e) {
      if (e.getWhoClicked() instanceof Player p) {
         ItemStack result = e.getCurrentItem();
         if (result != null) {
            String foodId = this.getFoodId(result);
            if (foodId != null) {
               this.applyDishLocalization(p, result, foodId);
            }
         }
      }
   }

   @EventHandler
   public void onPrepareCraft(PrepareItemCraftEvent e) {
      if (e.getView().getPlayer() instanceof Player p) {
         CraftingInventory inv = e.getInventory();
         ItemStack result = inv.getResult();
         if (result != null) {
            String resultId = this.getFoodId(result);
            if (resultId != null) {
               ItemStack[] matrix = inv.getMatrix();
               switch (resultId) {
                  case "tomato_salad":
                     int tomatoCount = 0;
                     int bowls = 0;
                     ItemStack[] var28 = matrix;
                     int var31 = var28.length;
                     int var34 = 0;

                     for (; var34 < var31; var34++) {
                        ItemStack stack = var28[var34];
                        if (stack != null && stack.getType() != Material.AIR) {
                           if (stack.getType() == Material.BOWL) {
                              if (++bowls > 1) {
                                 inv.setResult(null);
                                 return;
                              }
                           } else {
                              String id = this.getFoodId(stack);
                              if (!"tomato".equals(id)) {
                                 inv.setResult(null);
                                 return;
                              }

                              tomatoCount += stack.getAmount();
                           }
                        }
                     }

                     if (bowls != 1 || tomatoCount < 2) {
                        inv.setResult(null);
                        return;
                     }

                     this.applyDishLocalization(p, result, "tomato_salad");
                     inv.setResult(result);
                     break;
                  case "vegetable_bread":
                     int bread = 0;
                     int tomato = 0;
                     int onion = 0;

                     for (ItemStack stack : matrix) {
                        if (stack != null && stack.getType() != Material.AIR) {
                           Material t = stack.getType();
                           if (t == Material.BREAD) {
                              bread++;
                           } else {
                              String id = this.getFoodId(stack);
                              if ("tomato".equals(id)) {
                                 tomato++;
                              } else {
                                 if (!"onion".equals(id)) {
                                    inv.setResult(null);
                                    return;
                                 }

                                 onion++;
                              }
                           }
                        }
                     }

                     if (bread != 1 || tomato < 1 || onion < 1) {
                        inv.setResult(null);
                        return;
                     }

                     this.applyDishLocalization(p, result, "vegetable_bread");
                     inv.setResult(result);
                     break;
                  case "meat_sandwich":
                     int bread = 0;
                     int tomato = 0;
                     int onion = 0;
                     int meat = 0;

                     for (ItemStack stack : matrix) {
                        if (stack != null && stack.getType() != Material.AIR) {
                           Material t = stack.getType();
                           if (t == Material.BREAD) {
                              bread++;
                           } else if (this.isCookedMeat(t)) {
                              meat++;
                           } else {
                              String id = this.getFoodId(stack);
                              if ("tomato".equals(id)) {
                                 tomato++;
                              } else {
                                 if (!"onion".equals(id)) {
                                    inv.setResult(null);
                                    return;
                                 }

                                 onion++;
                              }
                           }
                        }
                     }

                     if (bread != 1 || tomato < 1 || onion < 1 || meat < 1) {
                        inv.setResult(null);
                        return;
                     }

                     this.applyDishLocalization(p, result, "meat_sandwich");
                     inv.setResult(result);
                     break;
                  case "beef_rice_stew":
                     int bowl = 0;
                     int rice = 0;
                     int meat = 0;

                     for (ItemStack stack : matrix) {
                        if (stack != null && stack.getType() != Material.AIR) {
                           Material t = stack.getType();
                           if (t == Material.BOWL) {
                              bowl++;
                           } else if (this.isCookedMeat(t)) {
                              meat++;
                           } else {
                              String id = this.getFoodId(stack);
                              if (!"rice".equals(id)) {
                                 inv.setResult(null);
                                 return;
                              }

                              rice++;
                           }
                        }
                     }

                     if (bowl < 1 || rice < 1 || meat < 1) {
                        inv.setResult(null);
                        return;
                     }

                     this.applyDishLocalization(p, result, "beef_rice_stew");
                     inv.setResult(result);
                     break;
                  default:
                     this.applyDishLocalization(p, result, resultId);
                     inv.setResult(result);
               }
            }
         }
      }
   }

   private boolean isCookedMeat(Material m) {
      return m == Material.COOKED_BEEF
         || m == Material.COOKED_MUTTON
         || m == Material.COOKED_PORKCHOP
         || m == Material.COOKED_CHICKEN
         || m == Material.COOKED_RABBIT
         || m == Material.COOKED_SALMON
         || m == Material.COOKED_COD;
   }

   @EventHandler(ignoreCancelled = true)
   public void onConsumeFood(PlayerItemConsumeEvent e) {
      String foodId = this.getFoodId(e.getItem());
      if (foodId != null && DISH_IDS.contains(foodId)) {
         Player p = e.getPlayer();
         int targetFood = Math.min(20, p.getFoodLevel() + this.foodPoints(foodId));
         float targetSaturation = Math.min(20.0F, p.getSaturation() + this.saturationPoints(foodId));
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (p.isOnline()) {
               p.setFoodLevel(targetFood);
               p.setSaturation(Math.min(targetFood, targetSaturation));
               this.applyConsumedFoodEffects(p, foodId);
            }
         });
      }
   }

   private int foodPoints(String foodId) {
      return switch (foodId) {
         case "tomato_salad" -> 4;
         case "vegetable_bread" -> 7;
         case "meat_sandwich" -> 8;
         case "beef_rice_stew" -> 10;
         case "coffee", "herbal_tea" -> 3;
         case "energy_drink" -> 2;
         case "hot_chocolate" -> 5;
         default -> 0;
      };
   }

   private float saturationPoints(String foodId) {
      return switch (foodId) {
         case "tomato_salad" -> 4.0F;
         case "vegetable_bread" -> 6.0F;
         case "meat_sandwich" -> 7.0F;
         case "beef_rice_stew" -> 8.0F;
         case "coffee", "herbal_tea" -> 3.0F;
         case "energy_drink" -> 2.0F;
         case "hot_chocolate" -> 5.0F;
         default -> 0.0F;
      };
   }

   private void applyConsumedFoodEffects(Player p, String foodId) {
      switch (foodId) {
         case "tomato_salad":
            this.eatTomatoSalad(p);
            break;
         case "vegetable_bread":
            this.eatVegetableBread(p);
            break;
         case "meat_sandwich":
            this.eatMeatSandwich(p);
            break;
         case "beef_rice_stew":
            this.eatBeefRiceStew(p);
            break;
         case "coffee":
            this.drinkCoffee(p);
            break;
         case "herbal_tea":
            this.drinkHerbalTea(p);
            break;
         case "energy_drink":
            this.drinkEnergy(p);
            break;
         case "hot_chocolate":
            this.drinkHotChocolate(p);
      }
   }

   private void consumeOneFromHand(Player p, ItemStack inHand) {
      int amount = inHand.getAmount();
      if (amount <= 1) {
         p.getInventory().setItemInMainHand(null);
      } else {
         inHand.setAmount(amount - 1);
      }
   }

   private void eatTomatoSalad(Player p) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, true, true));
   }

   private void eatVegetableBread(Player p) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 300, 0, false, true, true));
   }

   private void eatMeatSandwich(Player p) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0, false, true, true));
      p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0, false, true, true));
   }

   private void eatBeefRiceStew(Player p) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, false, true, true));
      p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60, 0, false, true, true));
   }

   private void drinkCoffee(Player p) {
      p.setFreezeTicks(0);
      p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 900, 0, false, true, true));
      p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 0, false, true, true));
   }

   private void drinkHerbalTea(Player p) {
      p.setFreezeTicks(0);
      p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0, false, true, true));
      p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 600, 0, false, true, true));
   }

   private void drinkEnergy(Player p) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 500, 1, false, true, true));
      p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 500, 1, false, true, true));
   }

   private void drinkHotChocolate(Player p) {
      p.setFreezeTicks(0);
      p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 0, false, true, true));
      p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1, false, true, true));
   }

   @EventHandler
   public void onLootGenerate(LootGenerateEvent e) {
      LootTable table = e.getLootTable();
      if (table != null) {
         NamespacedKey key = table.getKey();
         if (key != null) {
            String path = key.getKey();
            if (path.startsWith("chests/village/")) {
               List<ItemStack> loot = e.getLoot();
               Random rnd = ThreadLocalRandom.current();
               Player p = e.getEntity() instanceof Player pl ? pl : null;
               if (rnd.nextDouble() < 0.35) {
                  ItemStack t = this.tomatoProto.clone();
                  this.applyFoodLocalization(p, t, "tomato");
                  loot.add(t);
               }

               if (rnd.nextDouble() < 0.35) {
                  ItemStack o = this.onionProto.clone();
                  this.applyFoodLocalization(p, o, "onion");
                  loot.add(o);
               }

               if (rnd.nextDouble() < 0.3) {
                  ItemStack r = this.riceProto.clone();
                  this.applyFoodLocalization(p, r, "rice");
                  loot.add(r);
               }
            }
         }
      }
   }

   @EventHandler
   public void onZombieDeath(EntityDeathEvent e) {
      LivingEntity entity = e.getEntity();
      EntityType type = entity.getType();
      if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
         Random rnd = ThreadLocalRandom.current();
         if (!(rnd.nextDouble() > 0.1)) {
            int roll = rnd.nextInt(3);
            String foodId;
            ItemStack drop;
            if (roll == 0) {
               drop = this.tomatoProto.clone();
               foodId = "tomato";
            } else if (roll == 1) {
               drop = this.onionProto.clone();
               foodId = "onion";
            } else {
               drop = this.riceProto.clone();
               foodId = "rice";
            }

            Player killer = entity.getKiller();
            this.applyFoodLocalization(killer, drop, foodId);
            drop.setAmount(1);
            e.getDrops().add(drop);
         }
      }
   }

   private void startVisualRefresh() {
      if (this.visualRefreshTask != null) {
         this.visualRefreshTask.cancel();
      }

      this.visualRefreshTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            this.refreshCropVisuals(player);
         }
      }, 20L, 40L);
   }

   @EventHandler
   public void onCropVisualJoin(PlayerJoinEvent e) {
      this.removeFoodGlint(e.getPlayer());
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.refreshCropVisuals(e.getPlayer()), 10L);
   }

   @EventHandler
   public void onCropVisualWorldChange(PlayerChangedWorldEvent e) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.refreshCropVisuals(e.getPlayer()), 2L);
   }

   @EventHandler
   public void onCropVisualMove(PlayerMoveEvent e) {
      if (e.getTo() != null) {
         if (e.getFrom().getBlockX() >> 4 != e.getTo().getBlockX() >> 4
            || e.getFrom().getBlockZ() >> 4 != e.getTo().getBlockZ() >> 4
            || !e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.refreshCropVisuals(e.getPlayer()));
         }
      }
   }

   @EventHandler
   public void onCustomCropGrow(BlockGrowEvent e) {
      Block changed = e.getBlock();
      String cropId = this.customCrops.get(this.locKey(changed));
      Material newType = e.getNewState().getType();
      if (cropId != null || newType == Material.KELP || newType == Material.KELP_PLANT) {
         Bukkit.getScheduler().runTask(this.plugin, () -> this.refreshVisualAt(changed));
      }
   }

   @EventHandler
   public void onCustomCropFertilize(BlockFertilizeEvent e) {
      List<Block> affectedCustomCrops = new ArrayList<>();
      e.getBlocks().forEach(state -> {
         Block blockx = state.getBlock();
         if (this.customCrops.containsKey(this.locKey(blockx))) {
            affectedCustomCrops.add(blockx);
         } else {
            Material type = state.getType();
            if (type == Material.KELP || type == Material.KELP_PLANT) {
               Block base = this.findKelpBase(blockx.getRelative(BlockFace.DOWN));
               if (base != null && this.riceBases.contains(this.locKey(base))) {
                  affectedCustomCrops.add(blockx);
               }
            }
         }
      });
      if (!affectedCustomCrops.isEmpty()) {
         for (Block block : affectedCustomCrops) {
            this.scheduleVisualRefresh(block);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBoneMealVisualRefresh(PlayerInteractEvent e) {
      if (e.getHand() == EquipmentSlot.HAND) {
         if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getItem() != null && e.getItem().getType() == Material.BONE_MEAL) {
               Block clicked = e.getClickedBlock();
               if (clicked != null) {
                  if (this.customCrops.containsKey(this.locKey(clicked))) {
                     this.scheduleVisualRefresh(clicked);
                  } else {
                     Block riceBase = this.findKelpBase(clicked);
                     if (riceBase != null && this.riceBases.contains(this.locKey(riceBase))) {
                        this.scheduleVisualRefresh(clicked);
                     }
                  }
               }
            }
         }
      }
   }

   private void scheduleVisualRefresh(Block block) {
      Location location = block.getLocation();
      long[] delays = new long[]{1L, 3L, 8L, 20L};

      for (long delay : delays) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.refreshVisualAt(location.getBlock()), delay);
      }
   }

   private void refreshVisualAt(Block changed) {
      String cropId = this.customCrops.get(this.locKey(changed));
      if (cropId != null) {
         this.broadcastLandCropVisual(changed, cropId);
      } else {
         Block base = this.findKelpBase(changed);
         if (base != null && this.riceBases.contains(this.locKey(base))) {
            this.broadcastRiceVisual(base);
         } else {
            Block below = changed.getRelative(BlockFace.DOWN);
            base = this.findKelpBase(below);
            if (base != null && this.riceBases.contains(this.locKey(base))) {
               this.broadcastRiceVisual(base);
            }
         }
      }
   }

   private void refreshCropVisuals(Player player) {
      for (Entry<String, String> entry : this.customCrops.entrySet()) {
         Block block = this.blockFromKey(entry.getKey());
         if (block != null && this.isNear(player, block)) {
            Material expected = this.cropMaterialFor(entry.getValue());
            if (expected == block.getType()) {
               this.sendLandCropVisual(player, block, entry.getValue());
            }
         }
      }

      List<String> riceKeys;
      synchronized (this.riceBases) {
         riceKeys = new ArrayList<>(this.riceBases);
      }

      for (String key : riceKeys) {
         Block base = this.blockFromKey(key);
         if (base != null && this.isNear(player, base) && (base.getType() == Material.KELP || base.getType() == Material.KELP_PLANT)) {
            this.sendRiceVisual(player, base);
         }
      }
   }

   private void restoreRealCropBlocks(Player player) {
      for (String key : this.customCrops.keySet()) {
         Block block = this.blockFromKey(key);
         if (block != null && block.getWorld().equals(player.getWorld())) {
            player.sendBlockChange(block.getLocation(), block.getBlockData());
         }
      }

      List<String> riceKeys;
      synchronized (this.riceBases) {
         riceKeys = new ArrayList<>(this.riceBases);
      }

      for (String key : riceKeys) {
         Block base = this.blockFromKey(key);
         if (base != null && base.getWorld().equals(player.getWorld())) {
            Block current = base;

            for (int guard = 0; this.isKelp(current) && guard++ < 64; current = current.getRelative(BlockFace.UP)) {
               player.sendBlockChange(current.getLocation(), current.getBlockData());
            }
         }
      }
   }

   private Block blockFromKey(String key) {
      String[] parts = key.split(";", 4);
      if (parts.length != 4) {
         return null;
      }

      World world = Bukkit.getWorld(parts[0]);
      if (world == null) {
         return null;
      }

      try {
         int x = Integer.parseInt(parts[1]);
         int y = Integer.parseInt(parts[2]);
         int z = Integer.parseInt(parts[3]);
         return !world.isChunkLoaded(x >> 4, z >> 4) ? null : world.getBlockAt(x, y, z);
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   private boolean isNear(Player player, Block block) {
      return !player.getWorld().equals(block.getWorld()) ? false : player.getLocation().distanceSquared(block.getLocation()) <= 16384.0;
   }

   private int cropStage(Block block) {
      if (block.getBlockData() instanceof Ageable ageable) {
         int max = ageable.getMaximumAge();
         return max <= 0 ? 0 : Math.min(3, ageable.getAge() * 4 / (max + 1));
      } else {
         return 0;
      }
   }

   private BlockData landCropVisualData(String cropId, int stage) {
      int variant = Math.max(0, Math.min(3, stage));
      if ("onion".equals(cropId)) {
         variant += 4;
      }

      boolean east = (variant & 1) != 0;
      boolean north = (variant & 2) != 0;
      boolean south = (variant & 4) != 0;
      boolean west = (variant & 8) != 0;
      String data = "minecraft:tripwire[attached=false,disarmed=true,east=" + east + ",north=" + north + ",powered=true,south=" + south + ",west=" + west + "]";
      return Bukkit.createBlockData(data);
   }

   private BlockData riceVisualData(int stage) {
      int age = Math.max(0, Math.min(3, stage));
      return Bukkit.createBlockData("minecraft:mangrove_propagule[age=" + age + ",hanging=true,stage=1,waterlogged=true]");
   }

   private void sendLandCropVisual(Player player, Block block, String cropId) {
      player.sendBlockChange(block.getLocation(), this.landCropVisualData(cropId, this.cropStage(block)));
   }

   private void broadcastLandCropVisual(Block block, String cropId) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.isNear(player, block)) {
            this.sendLandCropVisual(player, block, cropId);
         }
      }
   }

   private void sendRiceVisual(Player player, Block base) {
      List<Block> column = this.riceColumn(base);
      int height = column.size();

      for (int i = 0; i < height; i++) {
         int visualStage;
         if (height == 1) {
            visualStage = 0;
         } else if (i == height - 1) {
            visualStage = height >= 3 ? 3 : 2;
         } else {
            visualStage = 1;
         }

         Block segment = column.get(i);
         player.sendBlockChange(segment.getLocation(), this.riceVisualData(visualStage));
      }
   }

   private void broadcastRiceVisual(Block base) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.isNear(player, base)) {
            this.sendRiceVisual(player, base);
         }
      }
   }

   private List<Block> riceColumn(Block base) {
      List<Block> result = new ArrayList<>();
      Block current = base;

      for (int guard = 0; this.isKelp(current) && guard++ < 64; current = current.getRelative(BlockFace.UP)) {
         result.add(current);
      }

      return result;
   }

   private boolean isKelp(Block block) {
      return block.getType() == Material.KELP || block.getType() == Material.KELP_PLANT;
   }

   private Block findKelpBase(Block start) {
      if (start != null && this.isKelp(start)) {
         Block base = start;
         int guard = 0;

         while (this.isKelp(base.getRelative(BlockFace.DOWN)) && guard++ < 64) {
            base = base.getRelative(BlockFace.DOWN);
         }

         return base;
      } else {
         return null;
      }
   }

   private void broadcastRealBlockLater(Location location) {
      Location copy = location.clone();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         Block block = copy.getBlock();

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.isNear(player, block)) {
               player.sendBlockChange(block.getLocation(), block.getBlockData());
            }
         }
      });
   }

   @EventHandler
   public void onRicePlant(PlayerInteractEvent e) {
      if (e.getHand() == EquipmentSlot.HAND) {
         if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item != null) {
               String foodId = this.getFoodId(item);
               if ("rice".equals(foodId)) {
                  Block clicked = e.getClickedBlock();
                  if (clicked != null) {
                     Block waterBlock = clicked.getType() == Material.WATER ? clicked : clicked.getRelative(BlockFace.UP);
                     if (waterBlock.getType() == Material.WATER && waterBlock.getBlockData() instanceof Levelled lvl && lvl.getLevel() == 0) {
                        e.setCancelled(true);
                        this.consumeOneFromHand(e.getPlayer(), item);
                        waterBlock.setType(Material.KELP, false);
                        this.riceBases.add(this.locKey(waterBlock));
                        this.markCropsDirty();
                        this.broadcastRiceVisual(waterBlock);
                        this.scheduleVisualRefresh(waterBlock);
                     } else {
                        if (clicked.getType() == Material.FARMLAND && clicked.getRelative(BlockFace.UP).getType().isAir()) {
                           e.setCancelled(true);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onCustomSeedPlant(PlayerInteractEvent e) {
      if (e.getHand() == EquipmentSlot.HAND) {
         if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item != null) {
               String id = this.getFoodId(item);
               if (id != null) {
                  if ("tomato".equals(id) || "onion".equals(id)) {
                     this.tryPlantCustomCrop(e, id);
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onCustomCropBreak(BlockBreakEvent e) {
      Block broken = e.getBlock();
      Block cropBlock = broken;
      String cropId = this.customCrops.remove(this.locKey(cropBlock));
      boolean brokeCropDirectly = cropId != null;
      if (!brokeCropDirectly) {
         cropBlock = broken.getRelative(BlockFace.UP);
         cropId = this.customCrops.remove(this.locKey(cropBlock));
      }

      if (cropId != null) {
         this.markCropsDirty();
         this.broadcastRealBlockLater(cropBlock.getLocation());
         if (brokeCropDirectly) {
            e.setDropItems(false);
         }

         World w = cropBlock.getWorld();
         Location dropLoc = cropBlock.getLocation().add(0.5, 0.5, 0.5);
         Random rnd = ThreadLocalRandom.current();
         ItemStack seedProto = cropId.equals("tomato") ? this.tomatoProto.clone() : this.onionProto.clone();
         int amount = 1;
         if (cropBlock.getBlockData() instanceof Ageable age) {
            int a = age.getAge();
            int max = age.getMaximumAge();
            if (a >= max) {
               amount = 2 + rnd.nextInt(4);
            } else if (a > 0) {
               amount = 1 + rnd.nextInt(2);
            }
         }

         seedProto.setAmount(amount);
         Player breaker = e.getPlayer();
         this.applyFoodLocalization(breaker, seedProto, cropId);
         if (!brokeCropDirectly) {
            cropBlock.setType(Material.AIR, false);
            Set<Material> vanillaDrops = cropId.equals("tomato")
               ? EnumSet.of(Material.BEETROOT_SEEDS, Material.BEETROOT)
               : EnumSet.of(Material.WHEAT_SEEDS, Material.WHEAT);
            this.scheduleVanillaCropDropCleanup(dropLoc, vanillaDrops);
         }

         w.dropItemNaturally(dropLoc, seedProto);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onRiceBreak(BlockBreakEvent e) {
      Block broken = e.getBlock();
      boolean brokeRiceBlock = this.isKelp(broken);
      Block base;
      if (brokeRiceBlock) {
         base = this.findKelpBase(broken);
         if (base == null) {
            return;
         }
      } else {
         base = broken.getRelative(BlockFace.UP);
      }

      String key = this.locKey(base);
      if (this.riceBases.contains(key)) {
         if (brokeRiceBlock) {
            e.setDropItems(false);
         }

         World w = base.getWorld();
         Location dropLoc = base.getLocation().add(0.5, 0.5, 0.5);
         int segments = 0;
         List<Location> clearedLocations = new ArrayList<>();

         for (Block current = base; current.getType() == Material.KELP || current.getType() == Material.KELP_PLANT; current = current.getRelative(BlockFace.UP)) {
            segments++;
            clearedLocations.add(current.getLocation());
            current.setType(Material.WATER, false);
         }

         this.riceBases.remove(key);
         this.markCropsDirty();
         if (!brokeRiceBlock) {
            this.scheduleVanillaCropDropCleanup(dropLoc, EnumSet.of(Material.KELP));
         }

         int amount = Math.max(1, segments);
         ItemStack seeds = this.riceProto.clone();
         seeds.setAmount(amount);
         Player breaker = e.getPlayer();
         this.applyFoodLocalization(breaker, seeds, "rice");
         w.dropItemNaturally(dropLoc, seeds);

         for (Location location : clearedLocations) {
            this.broadcastRealBlockLater(location);
         }
      }
   }

   private void scheduleVanillaCropDropCleanup(Location cropDropLocation, Set<Material> vanillaDrops) {
      Location center = cropDropLocation.clone();
      long[] delays = new long[]{1L, 3L};

      for (long delay : delays) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            World world = center.getWorld();
            if (world != null) {
               for (Entity entity : world.getNearbyEntities(center, 1.25, 1.25, 1.25)) {
                  if (entity instanceof Item dropped && dropped.getTicksLived() <= 10) {
                     ItemStack stack = dropped.getItemStack();
                     if (vanillaDrops.contains(stack.getType()) && this.getFoodId(stack) == null) {
                        dropped.remove();
                     }
                  }
               }
            }
         }, delay);
      }
   }

   private void applyFoodLocalization(Player p, ItemStack item, String foodId) {
      if (item != null && foodId != null) {
         ItemMeta meta = item.getItemMeta();
         if (meta != null) {
            if (this.isIngredientId(foodId)) {
               meta.removeEnchant(Enchantment.LURE);
            }

            String baseKey = "items.food." + foodId;
            String name = p != null ? this.lang.tr(p, baseKey + ".name") : this.lang.trServer(baseKey + ".name");
            String lore1 = p != null ? this.lang.tr(p, baseKey + ".lore_1") : this.lang.trServer(baseKey + ".lore_1");
            String lore2 = p != null ? this.lang.tr(p, baseKey + ".lore_2") : this.lang.trServer(baseKey + ".lore_2");
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add(lore1);
            lore.add(lore2);
            this.appendGrowthLore(p, lore, foodId);
            meta.setLore(lore);
            item.setItemMeta(meta);
         }
      }
   }

   private boolean isIngredientId(String foodId) {
      return "tomato".equals(foodId) || "onion".equals(foodId) || "rice".equals(foodId);
   }

   private boolean isFoodId(String foodId) {
      return this.isIngredientId(foodId) || DISH_IDS.contains(foodId);
   }

   private void removeFoodGlint(Player player) {
      for (ItemStack item : player.getInventory().getContents()) {
         String foodId = this.getFoodId(item);
         if (this.isFoodId(foodId) && item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchant(Enchantment.LURE)) {
               meta.removeEnchant(Enchantment.LURE);
               item.setItemMeta(meta);
            }
         }
      }
   }

   public String getArtificialCropId(Block block) {
      return block == null ? null : this.customCrops.get(this.locKey(block));
   }

   public boolean isArtificialSeed(ItemStack item, String cropId) {
      return item != null && cropId != null && cropId.equals(this.getFoodId(item));
   }

   public boolean isAnyArtificialSeed(ItemStack item) {
      return item != null && this.isIngredientId(this.getFoodId(item));
   }

   public Material getArtificialSeedMaterial(String cropId) {
      return switch (cropId) {
         case "tomato" -> Material.BEETROOT_SEEDS;
         case "onion" -> Material.WHEAT_SEEDS;
         default -> null;
      };
   }

   public ItemStack createArtificialCropDrop(String cropId, Player player, int amount) {
      ItemStack prototype = switch (cropId) {
         case "tomato" -> this.tomatoProto;
         case "onion" -> this.onionProto;
         default -> null;
      };
      if (prototype == null) {
         return null;
      }

      ItemStack drop = prototype.clone();
      drop.setAmount(Math.max(1, amount));
      this.applyFoodLocalization(player, drop, cropId);
      return drop;
   }

   public void finishArtificialHarvest(Block block, String cropId, boolean replant) {
      if (block != null && cropId != null) {
         this.customCrops.remove(this.locKey(block));
         if (replant) {
            Material cropMaterial = this.cropMaterialFor(cropId);
            if (cropMaterial != null) {
               block.setType(cropMaterial, false);
               if (block.getBlockData() instanceof Ageable ageable) {
                  ageable.setAge(0);
                  block.setBlockData(ageable, false);
               }

               this.customCrops.put(this.locKey(block), cropId);
               this.broadcastLandCropVisual(block, cropId);
               this.scheduleVisualRefresh(block);
            }
         } else {
            this.broadcastRealBlockLater(block.getLocation());
         }

         this.markCropsDirty();
      }
   }

   private Material cropMaterialFor(String id) {
      return switch (id) {
         case "tomato" -> Material.BEETROOTS;
         case "onion" -> Material.WHEAT;
         default -> null;
      };
   }

   private boolean tryPlantCustomCrop(PlayerInteractEvent e, String cropId) {
      Block clicked = e.getClickedBlock();
      if (clicked == null) {
         return false;
      }

      if (clicked.getType() != Material.FARMLAND) {
         return false;
      }

      Block plantAt = clicked.getRelative(BlockFace.UP);
      if (!plantAt.getType().isAir()) {
         return false;
      }

      Material cropMat = this.cropMaterialFor(cropId);
      if (cropMat == null) {
         return false;
      }

      e.setCancelled(true);
      this.consumeOneFromHand(e.getPlayer(), e.getItem());
      plantAt.setType(cropMat, false);
      this.customCrops.put(this.locKey(plantAt), cropId);
      this.markCropsDirty();
      this.broadcastLandCropVisual(plantAt, cropId);
      this.scheduleVisualRefresh(plantAt);
      return true;
   }
}
