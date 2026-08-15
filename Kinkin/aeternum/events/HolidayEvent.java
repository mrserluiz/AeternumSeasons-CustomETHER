package Kinkin.aeternum.events;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HolidayEvent implements SeasonalEvent {
   private static final String OVERWORLD_CHANNEL_PATH = "calendar.channels.OVERWORLD.months";
   private final AeternumSeasonsPlugin plugin;
   private boolean enabled;
   private boolean globalParticlesEnabled;
   private double halloweenMobBuffRadius;
   private double halloweenPumpkinChance;
   private double halloweenBuffChance;
   private double christmasHealRadius;
   private double christmasHealAmount;
   private int christmasDay;
   private final Map<World, HolidayEvent.Mode> activeModes = new HashMap<>();

   public HolidayEvent(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.reloadFromConfig();
   }

   private void reloadFromConfig() {
      FileConfiguration y = YamlEvents.get(this.plugin);
      this.enabled = y.getBoolean("events.holiday.enabled", true);
      this.globalParticlesEnabled = y.getBoolean("events.visual_effects.particles_enabled", true);
      this.halloweenMobBuffRadius = y.getDouble("events.holiday.halloween.mob_radius", 32.0);
      this.halloweenPumpkinChance = y.getDouble("events.holiday.halloween.pumpkin_chance", 0.9);
      this.halloweenBuffChance = y.getDouble("events.holiday.halloween.buff_chance", 1.0);
      this.christmasHealRadius = y.getDouble("events.holiday.christmas.heal_radius", 12.0);
      this.christmasHealAmount = y.getDouble("events.holiday.christmas.heal_amount", 0.5);
      int daysPerSeason = this.resolveDaysPerSeason();
      this.christmasDay = Math.max(1, daysPerSeason - 3);
   }

   private int resolveDaysPerSeason() {
      int v = this.plugin.cfg.calendar.getInt("days_per_season", Integer.MIN_VALUE);
      if (v == Integer.MIN_VALUE) {
         v = this.plugin.cfg.calendar.getInt("calendar.days_per_season", 28);
      }

      if (v < 4) {
         v = 4;
      }

      return v;
   }

   @Override
   public String getId() {
      return "holiday";
   }

   @Override
   public String getDisplayName() {
      return "Festividades";
   }

   @Override
   public boolean isSeasonAllowed(Season season) {
      return true;
   }

   @Override
   public int getMinDurationDays() {
      return 1;
   }

   @Override
   public int getMaxDurationDays() {
      return 1;
   }

   private boolean isHalloweenDay(CalendarState st) {
      if (st == null) {
         return false;
      }

      if (!st.monthsEnabled) {
         return st.season == Season.AUTUMN && (st.day == 1 || st.day == 2);
      }

      String targetMonth = this.getLastConfiguredMonthForSeason(Season.AUTUMN);
      return targetMonth != null && st.monthId != null && !st.monthId.isBlank()
         ? targetMonth.equalsIgnoreCase(st.monthId) && (st.day == 1 || st.day == 2)
         : false;
   }

   private boolean isChristmasDay(CalendarState st) {
      if (st == null) {
         return false;
      }

      if (!st.monthsEnabled) {
         return st.season == Season.WINTER && st.day == this.christmasDay;
      }

      String targetMonth = this.getLastConfiguredMonthForSeason(Season.WINTER);
      if (targetMonth == null || st.monthId == null || st.monthId.isBlank()) {
         return false;
      }

      if (!targetMonth.equalsIgnoreCase(st.monthId)) {
         return false;
      }

      int maxDay = st.daysInMonth > 0 ? st.daysInMonth : 25;
      int effectiveChristmasDay = Math.max(1, Math.min(25, maxDay));
      return st.season == Season.WINTER && st.day == effectiveChristmasDay;
   }

   private String getLastConfiguredMonthForSeason(Season season) {
      ConfigurationSection monthsSec = this.plugin.cfg.calendar.getConfigurationSection("calendar.channels.OVERWORLD.months");
      if (monthsSec != null && monthsSec.getBoolean("enabled", false)) {
         List<String> order = monthsSec.getStringList("order");
         ConfigurationSection defs = monthsSec.getConfigurationSection("definitions");
         if (order != null && !order.isEmpty() && defs != null) {
            String found = null;

            for (String monthId : order) {
               if (monthId != null && !monthId.isBlank()) {
                  ConfigurationSection def = defs.getConfigurationSection(monthId);
                  if (def != null) {
                     String rawSeason = def.getString("season", "");

                     try {
                        Season s = Season.valueOf(rawSeason.trim().toUpperCase(Locale.ROOT));
                        if (s == season) {
                           found = monthId.trim();
                        }
                     } catch (IllegalArgumentException var11) {
                     }
                  }
               }
            }

            return found;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @Override
   public boolean canStartToday(CalendarState st, EventContext ctx) {
      return !this.enabled ? false : this.isHalloweenDay(st) || this.isChristmasDay(st);
   }

   @Override
   public void onStart(CalendarState st, EventContext ctx) {
      this.activeModes.clear();
      HolidayEvent.Mode mode;
      if (this.isHalloweenDay(st)) {
         mode = HolidayEvent.Mode.HALLOWEEN;
      } else {
         if (!this.isChristmasDay(st)) {
            return;
         }

         mode = HolidayEvent.Mode.CHRISTMAS;
      }

      for (World w : ctx.overworlds()) {
         this.activeModes.put(w, mode);
      }

      if (mode == HolidayEvent.Mode.HALLOWEEN) {
         for (Player p : Bukkit.getOnlinePlayers()) {
            String title = this.plugin.lang.tr(p, "event.halloween.title");
            String sub = this.plugin.lang.tr(p, "event.halloween.subtitle");
            p.sendTitle(title, sub, 20, 80, 40);
         }
      } else {
         ThreadLocalRandom rnd = ThreadLocalRandom.current();

         for (World w : ctx.overworlds()) {
            for (Player p : w.getPlayers()) {
               this.giveChristmasGiftToPlayer(p, rnd);
            }
         }

         for (Player p : Bukkit.getOnlinePlayers()) {
            String title = this.plugin.lang.tr(p, "event.christmas.title");
            String sub = this.plugin.lang.tr(p, "event.christmas.subtitle");
            p.sendTitle(title, sub, 20, 80, 40);
         }
      }
   }

   @Override
   public void onEnd(CalendarState st, EventContext ctx) {
      this.activeModes.clear();

      for (Player p : Bukkit.getOnlinePlayers()) {
         p.sendMessage(this.plugin.lang.tr(p, "event.holiday.end"));
      }
   }

   @Override
   public void onDayTick(CalendarState st, EventContext ctx) {
   }

   @Override
   public void onTick(CalendarState st, EventContext ctx) {
      ThreadLocalRandom r = ThreadLocalRandom.current();

      for (World w : ctx.overworlds()) {
         HolidayEvent.Mode mode = this.activeModes.get(w);
         if (mode != null) {
            if (mode == HolidayEvent.Mode.HALLOWEEN) {
               this.tickHalloween(w, r);
            } else {
               this.tickChristmas(w, r);
            }
         }
      }
   }

   private void tickHalloween(World w, ThreadLocalRandom r) {
      for (Player p : w.getPlayers()) {
         if (p.getGameMode() != GameMode.SPECTATOR) {
            Location loc = p.getLocation().clone().add(0.0, 1.0, 0.0);
            if (this.globalParticlesEnabled) {
               w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.getX(), loc.getY(), loc.getZ(), 10, 0.7, 0.7, 0.7, 0.01);
               w.spawnParticle(Particle.DRIPPING_LAVA, loc.getX(), loc.getY() + 0.3, loc.getZ(), 6, 0.5, 0.5, 0.5, 0.01);
            }

            for (Entity e : w.getNearbyEntities(loc, this.halloweenMobBuffRadius, this.halloweenMobBuffRadius, this.halloweenMobBuffRadius)) {
               if (e instanceof Monster mob && !mob.isDead()) {
                  if (r.nextDouble() <= this.halloweenBuffChance) {
                     mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, true, false, false));
                     mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, false, false));
                  }

                  if (r.nextDouble() <= this.halloweenPumpkinChance && mob.getEquipment() != null) {
                     ItemStack helmet = mob.getEquipment().getHelmet();
                     if (helmet == null || helmet.getType() == Material.AIR) {
                        mob.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                     }
                  }

                  if (this.globalParticlesEnabled) {
                     Location ml = mob.getLocation();
                     w.spawnParticle(Particle.SOUL, ml.getX(), ml.getY() + mob.getHeight() * 0.5, ml.getZ(), 8, 0.5, 0.5, 0.5, 0.02);
                  }
               }
            }
         }
      }
   }

   private void tickChristmas(World w, ThreadLocalRandom r) {
      for (Player p : w.getPlayers()) {
         if (p.getGameMode() != GameMode.SPECTATOR) {
            Location loc = p.getLocation().clone().add(0.0, 1.2, 0.0);
            if (this.globalParticlesEnabled) {
               w.spawnParticle(Particle.SNOWFLAKE, loc.getX(), loc.getY(), loc.getZ(), 10, 0.8, 0.8, 0.8, 0.01);
               w.spawnParticle(Particle.HAPPY_VILLAGER, loc.getX(), loc.getY(), loc.getZ(), 6, 0.6, 0.6, 0.6, 0.01);
            }

            for (Entity e : w.getNearbyEntities(loc, this.christmasHealRadius, this.christmasHealRadius, this.christmasHealRadius)) {
               if (e instanceof Player other && !(other.getHealth() <= 0.0)) {
                  double max = Objects.requireNonNull(other.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
                  double newHealth = Math.min(max, other.getHealth() + this.christmasHealAmount);
                  other.setHealth(newHealth);
               }
            }
         }
      }
   }

   private void giveChristmasGiftToPlayer(Player p, ThreadLocalRandom r) {
      ItemStack gift = new ItemStack(Material.BUNDLE);
      BundleMeta meta = (BundleMeta)gift.getItemMeta();
      String displayName = this.plugin.lang.tr(p, "event.christmas.gift_bundle_name");
      List<String> lore = Arrays.asList(
         this.plugin.lang.tr(p, "event.christmas.gift_bundle_lore_1"), this.plugin.lang.tr(p, "event.christmas.gift_bundle_lore_2")
      );
      meta.setDisplayName(displayName);
      meta.setLore(lore);
      meta.addItem(new ItemStack(Material.COOKIE, 8 + r.nextInt(9)));
      meta.addItem(new ItemStack(Material.CAKE, 1));
      if (r.nextDouble() < 0.6) {
         meta.addItem(new ItemStack(Material.GOLDEN_APPLE, 1 + r.nextInt(2)));
      }

      meta.addItem(new ItemStack(Material.EMERALD, 3 + r.nextInt(6)));
      meta.addItem(new ItemStack(Material.FIREWORK_ROCKET, 4 + r.nextInt(6)));
      meta.addItem(new ItemStack(Material.SNOWBALL, 16));
      gift.setItemMeta(meta);
      Map<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack[]{gift});
      if (!leftover.isEmpty()) {
         p.getWorld().dropItemNaturally(p.getLocation(), gift);
      }

      p.sendMessage(this.plugin.lang.tr(p, "event.christmas.gift_found"));
      p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);
   }

   private enum Mode {
      HALLOWEEN,
      CHRISTMAS;
   }
}
