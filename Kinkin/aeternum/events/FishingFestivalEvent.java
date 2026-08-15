package Kinkin.aeternum.events;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public final class FishingFestivalEvent implements SeasonalEvent {
   private final AeternumSeasonsPlugin plugin;
   private boolean enabled;
   private boolean globalParticlesEnabled;
   private int minDur;
   private int maxDur;
   private double baseChance;
   private int maxPerYear;
   private double treasureChance;
   private double doubleCatchChance;
   private final Map<Integer, Integer> usedPerYear = new HashMap<>();

   public FishingFestivalEvent(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.reloadFromConfig();
   }

   private void reloadFromConfig() {
      FileConfiguration y = YamlEvents.get(this.plugin);
      this.enabled = y.getBoolean("events.fishing.enabled", true);
      this.globalParticlesEnabled = y.getBoolean("events.visual_effects.particles_enabled", true);
      this.minDur = y.getInt("events.fishing.min_duration_days", 1);
      this.maxDur = y.getInt("events.fishing.max_duration_days", 2);
      this.baseChance = y.getDouble("events.fishing.base_chance_per_day", 0.1);
      this.maxPerYear = y.getInt("events.fishing.max_per_year", 3);
      this.treasureChance = y.getDouble("events.fishing.treasure_chance", 0.4);
      this.doubleCatchChance = y.getDouble("events.fishing.double_catch_chance", 0.35);
   }

   @Override
   public String getId() {
      return "fishing_festival";
   }

   @Override
   public String getDisplayName() {
      return "Fishing Festival";
   }

   @Override
   public boolean isSeasonAllowed(Season season) {
      return true;
   }

   @Override
   public int getMinDurationDays() {
      return this.minDur;
   }

   @Override
   public int getMaxDurationDays() {
      return this.maxDur;
   }

   @Override
   public boolean canStartToday(CalendarState st, EventContext ctx) {
      if (!this.enabled) {
         return false;
      }

      int used = this.usedPerYear.getOrDefault(st.year, 0);
      return used >= this.maxPerYear ? false : Math.random() < this.baseChance;
   }

   @Override
   public void onStart(CalendarState st, EventContext ctx) {
      this.usedPerYear.put(st.year, this.usedPerYear.getOrDefault(st.year, 0) + 1);

      for (Player p : Bukkit.getOnlinePlayers()) {
         String title = this.plugin.lang.tr(p, "event.fishing.title");
         String sub = this.plugin.lang.tr(p, "event.fishing.subtitle");
         p.sendTitle(title, sub, 20, 80, 40);
      }
   }

   @Override
   public void onEnd(CalendarState st, EventContext ctx) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         p.sendMessage(this.plugin.lang.tr(p, "event.fishing.end"));
      }
   }

   @Override
   public void onDayTick(CalendarState st, EventContext ctx) {
   }

   @Override
   public void onTick(CalendarState st, EventContext ctx) {
   }

   @EventHandler
   public void onFish(PlayerFishEvent e) {
      if (e.getState() == State.CAUGHT_FISH) {
         Player p = e.getPlayer();
         if (p.getWorld().getEnvironment() == Environment.NORMAL) {
            if (this.globalParticlesEnabled) {
               p.getWorld().spawnParticle(Particle.SPLASH, e.getHook().getLocation(), 16, 0.35, 0.12, 0.35, 0.03);
            }

            if (e.getCaught() instanceof Item item) {
               ItemStack original = item.getItemStack().clone();
               ItemStack rod = this.getFishingRod(p);
               int luck = 0;
               int lure = 0;
               if (rod != null) {
                  luck = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
                  lure = rod.getEnchantmentLevel(Enchantment.LURE);
               }

               double rodFactor = 1.0 + luck * 0.35 + lure * 0.2;
               rodFactor = Math.min(2.5, rodFactor);
               double treasureEffChance = Math.min(0.95, this.treasureChance * rodFactor);
               double doubleEffChance = Math.min(0.9, this.doubleCatchChance * (0.5 + 0.5 * rodFactor));
               boolean didSomething = false;
               boolean goodRod = luck + lure >= 2;
               if (Math.random() < treasureEffChance) {
                  ItemStack treasure = this.randomTreasure(goodRod);
                  p.getInventory().addItem(new ItemStack[]{treasure});
                  p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
                  didSomething = true;
               }

               if (Math.random() < doubleEffChance) {
                  p.getInventory().addItem(new ItemStack[]{original});
                  p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7F, 1.2F);
                  didSomething = true;
               }

               if (didSomething) {
                  item.remove();
               }
            }
         }
      }
   }

   private ItemStack getFishingRod(Player p) {
      ItemStack main = p.getInventory().getItemInMainHand();
      if (main != null && main.getType() == Material.FISHING_ROD) {
         return main;
      }

      ItemStack off = p.getInventory().getItemInOffHand();
      return off != null && off.getType() == Material.FISHING_ROD ? off : null;
   }

   private ItemStack randomTreasure(boolean goodRod) {
      double r = Math.random();
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      if (!goodRod) {
         if (r < 0.4) {
            return new ItemStack(Material.EXPERIENCE_BOTTLE, rng.nextInt(3, 7));
         } else if (r < 0.75) {
            return Math.random() < 0.6 ? new ItemStack(Material.NAUTILUS_SHELL, 1) : new ItemStack(Material.NAME_TAG, 1);
         } else if (r < 0.9) {
            return Math.random() < 0.7 ? new ItemStack(Material.EMERALD, rng.nextInt(1, 4)) : new ItemStack(Material.DIAMOND, 1);
         } else {
            return Math.random() < 0.7 ? new ItemStack(Material.SADDLE, 1) : new ItemStack(Material.BONE_BLOCK, rng.nextInt(1, 3));
         }
      } else if (r < 0.1) {
         return this.randomBlockOre(rng);
      } else if (r < 0.3) {
         return this.randomValuableBook(rng);
      } else if (r < 0.5) {
         return this.randomSeaStuff(rng);
      } else if (r < 0.65) {
         return this.randomOreTreasure(rng);
      } else if (r < 0.8) {
         return Math.random() < 0.8 ? this.randomEnchantedRod(rng) : new ItemStack(Material.HEART_OF_THE_SEA, 1);
      } else {
         return Math.random() < 0.6 ? new ItemStack(Material.EXPERIENCE_BOTTLE, rng.nextInt(6, 12)) : new ItemStack(Material.NAUTILUS_SHELL, rng.nextInt(1, 3));
      }
   }

   private ItemStack randomOreTreasure(ThreadLocalRandom rng) {
      double r = Math.random();
      if (r < 0.45) {
         return new ItemStack(Material.DIAMOND, rng.nextInt(1, 3));
      }

      if (r < 0.75) {
         Material metal = rng.nextBoolean() ? Material.IRON_INGOT : Material.GOLD_INGOT;
         return new ItemStack(metal, rng.nextInt(5, 13));
      }

      if (r < 0.9) {
         return new ItemStack(Material.NETHERITE_SCRAP, 1);
      }

      Material block = rng.nextBoolean() ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK;
      return new ItemStack(block, rng.nextInt(1, 3));
   }

   private ItemStack randomBlockOre(ThreadLocalRandom rng) {
      if (Math.random() < 0.15) {
         return new ItemStack(Material.ANCIENT_DEBRIS, 1);
      }

      Material[] ores = new Material[]{Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.GOLD_BLOCK, Material.IRON_BLOCK};
      Material block = ores[rng.nextInt(ores.length)];
      return new ItemStack(block, 1);
   }

   private ItemStack randomSeaStuff(ThreadLocalRandom rng) {
      double r = Math.random();
      if (r < 0.35) {
         Material sponge = Math.random() < 0.7 ? Material.SPONGE : Material.WET_SPONGE;
         return new ItemStack(sponge, 1);
      }

      if (r < 0.65) {
         Material prismarine = Math.random() < 0.5 ? Material.PRISMARINE : Material.DARK_PRISMARINE;
         return new ItemStack(prismarine, rng.nextInt(5, 11));
      }

      if (r < 0.85) {
         return new ItemStack(Material.SEA_LANTERN, rng.nextInt(1, 4));
      }

      Material[] corals = new Material[]{Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK};
      Material coral = corals[rng.nextInt(corals.length)];
      return new ItemStack(coral, rng.nextInt(3, 9));
   }

   private ItemStack randomValuableBook(ThreadLocalRandom rng) {
      ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
      EnchantmentStorageMeta meta = (EnchantmentStorageMeta)book.getItemMeta();
      if (meta == null) {
         return new ItemStack(Material.EXPERIENCE_BOTTLE, rng.nextInt(6, 12));
      }

      Map<Enchantment, int[]> enchantmentLevels = new HashMap<>();
      enchantmentLevels.put(Enchantment.MENDING, new int[]{1, 1});
      enchantmentLevels.put(Enchantment.LUCK_OF_THE_SEA, new int[]{1, 3});
      enchantmentLevels.put(Enchantment.LURE, new int[]{1, 3});
      enchantmentLevels.put(Enchantment.UNBREAKING, new int[]{1, 3});
      enchantmentLevels.put(Enchantment.AQUA_AFFINITY, new int[]{1, 1});
      enchantmentLevels.put(Enchantment.RESPIRATION, new int[]{1, 3});
      enchantmentLevels.put(Enchantment.DEPTH_STRIDER, new int[]{1, 3});
      enchantmentLevels.put(Enchantment.RIPTIDE, new int[]{1, 3});
      enchantmentLevels.put(Enchantment.PROTECTION, new int[]{1, 4});
      enchantmentLevels.put(Enchantment.PROJECTILE_PROTECTION, new int[]{1, 4});
      enchantmentLevels.put(Enchantment.VANISHING_CURSE, new int[]{1, 1});
      List<Enchantment> availableEnchants = enchantmentLevels.keySet().stream().filter(e -> !e.equals(Enchantment.MENDING)).collect(Collectors.toList());
      if (Math.random() < 0.15) {
         meta.addStoredEnchant(Enchantment.MENDING, 1, true);
      }

      if (meta.getStoredEnchants().isEmpty() || Math.random() < 0.7) {
         Enchantment ench = availableEnchants.get(rng.nextInt(availableEnchants.size()));
         int[] levels = enchantmentLevels.get(ench);
         int level = rng.nextInt(levels[0], levels[1] + 1);
         if (!meta.hasStoredEnchant(ench)) {
            meta.addStoredEnchant(ench, level, true);
         }
      }

      if (Math.random() < 0.15) {
         Enchantment ench = availableEnchants.get(rng.nextInt(availableEnchants.size()));
         int[] levels = enchantmentLevels.get(ench);
         int level = rng.nextInt(levels[0], levels[1] + 1);
         if (!meta.hasStoredEnchant(ench)) {
            meta.addStoredEnchant(ench, level, true);
         }
      }

      if (meta.getStoredEnchants().isEmpty()) {
         Enchantment ench = rng.nextBoolean() ? Enchantment.LUCK_OF_THE_SEA : Enchantment.LURE;
         meta.addStoredEnchant(ench, rng.nextInt(1, 3), true);
      }

      book.setItemMeta(meta);
      return book;
   }

   private ItemStack randomEnchantedRod(ThreadLocalRandom rng) {
      ItemStack rod = new ItemStack(Material.FISHING_ROD);
      int luckLvl = rng.nextInt(1, 3);
      int unbLvl = rng.nextInt(1, 3);
      if (Math.random() < 0.3) {
         int lureLvl = rng.nextInt(1, 3);
         rod.addUnsafeEnchantment(Enchantment.LURE, lureLvl);
      }

      rod.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, luckLvl);
      rod.addUnsafeEnchantment(Enchantment.UNBREAKING, unbLvl);
      if (Math.random() < 0.1) {
         rod.addUnsafeEnchantment(Enchantment.MENDING, 1);
      }

      return rod;
   }
}
