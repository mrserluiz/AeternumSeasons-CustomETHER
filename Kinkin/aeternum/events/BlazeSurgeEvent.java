package Kinkin.aeternum.events;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BlazeSurgeEvent implements SeasonalEvent {
   private final AeternumSeasonsPlugin plugin;
   private boolean enabled;
   private int minDur;
   private int maxDur;
   private double baseChance;
   private double extraRodChance;
   private int extraRodMin;
   private int extraRodMax;
   private double buffedBlazeChance;

   public BlazeSurgeEvent(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.reloadFromConfig();
   }

   private void reloadFromConfig() {
      FileConfiguration y = YamlEvents.get(this.plugin);
      this.enabled = y.getBoolean("events.blaze_surge.enabled", true);
      this.minDur = y.getInt("events.blaze_surge.min_duration_days", 1);
      this.maxDur = y.getInt("events.blaze_surge.max_duration_days", 2);
      this.baseChance = y.getDouble("events.blaze_surge.base_chance_per_day", 0.08);
      this.extraRodChance = y.getDouble("events.blaze_surge.extra_rod_chance", 0.35);
      this.extraRodMin = y.getInt("events.blaze_surge.extra_rod_min", 1);
      this.extraRodMax = y.getInt("events.blaze_surge.extra_rod_max", 2);
      this.buffedBlazeChance = y.getDouble("events.blaze_surge.buffed_blaze_chance", 0.3);
   }

   @Override
   public String getId() {
      return "blaze_surge";
   }

   @Override
   public String getDisplayName() {
      return "Blaze Surge";
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

      boolean someoneInNether = Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getWorld().getEnvironment() == Environment.NETHER);
      return !someoneInNether ? false : Math.random() < this.baseChance;
   }

   @Override
   public void onStart(CalendarState st, EventContext ctx) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.getWorld().getEnvironment() == Environment.NETHER) {
            p.sendTitle(this.plugin.lang.tr(p, "event.blaze_surge.title"), this.plugin.lang.tr(p, "event.blaze_surge.subtitle"), 20, 80, 40);
         }
      }
   }

   @Override
   public void onEnd(CalendarState st, EventContext ctx) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.getWorld().getEnvironment() == Environment.NETHER) {
            p.sendMessage(this.plugin.lang.tr(p, "event.blaze_surge.end"));
         }
      }
   }

   @Override
   public void onDayTick(CalendarState st, EventContext ctx) {
   }

   @Override
   public void onTick(CalendarState st, EventContext ctx) {
   }

   @EventHandler
   public void onSpawn(CreatureSpawnEvent e) {
      if (e.getEntity() instanceof Blaze blaze) {
         if (blaze.getWorld().getEnvironment() == Environment.NETHER) {
            if (!(Math.random() > this.buffedBlazeChance)) {
               blaze.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3600, 0, true, false, true));
               blaze.setGlowing(true);
               AttributeInstance maxHp = blaze.getAttribute(Attribute.GENERIC_MAX_HEALTH);
               if (maxHp != null) {
                  maxHp.setBaseValue(Math.max(maxHp.getBaseValue(), 24.0));
                  blaze.setHealth(Math.min(blaze.getHealth(), (float)maxHp.getBaseValue()));
               }
            }
         }
      }
   }

   @EventHandler
   public void onDeath(EntityDeathEvent e) {
      if (e.getEntity() instanceof Blaze blaze) {
         if (blaze.getWorld().getEnvironment() == Environment.NETHER) {
            if (blaze.getKiller() != null) {
               if (!(Math.random() > this.extraRodChance)) {
                  ThreadLocalRandom rnd = ThreadLocalRandom.current();
                  int amt = this.extraRodMax <= this.extraRodMin ? this.extraRodMin : rnd.nextInt(this.extraRodMin, this.extraRodMax + 1);
                  e.getDrops().add(new ItemStack(Material.BLAZE_ROD, Math.max(1, amt)));
                  blaze.getWorld().spawnParticle(Particle.FLAME, blaze.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.01);
               }
            }
         }
      }
   }
}
