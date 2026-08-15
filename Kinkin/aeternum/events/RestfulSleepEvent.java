package Kinkin.aeternum.events;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class RestfulSleepEvent implements SeasonalEvent {
   private final AeternumSeasonsPlugin plugin;
   private boolean enabled;
   private int minDur;
   private int maxDur;
   private double baseChance;
   private int healthBoostSeconds;

   public RestfulSleepEvent(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.reloadFromConfig();
   }

   private void reloadFromConfig() {
      FileConfiguration y = YamlEvents.get(this.plugin);
      this.enabled = y.getBoolean("events.restful_sleep.enabled", true);
      this.minDur = y.getInt("events.restful_sleep.min_duration_days", 2);
      this.maxDur = y.getInt("events.restful_sleep.max_duration_days", 4);
      this.baseChance = y.getDouble("events.restful_sleep.base_chance_per_day", 0.1);
      this.healthBoostSeconds = y.getInt("events.restful_sleep.health_boost_seconds", 600);
   }

   @Override
   public String getId() {
      return "restful_sleep";
   }

   @Override
   public String getDisplayName() {
      return "Restful Sleep";
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
      return !this.enabled ? false : Math.random() < this.baseChance;
   }

   @Override
   public void onStart(CalendarState st, EventContext ctx) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         String title = this.plugin.lang.tr(p, "event.restful_sleep.title");
         String sub = this.plugin.lang.tr(p, "event.restful_sleep.subtitle");
         p.sendTitle(title, sub, 20, 80, 40);
      }
   }

   @Override
   public void onEnd(CalendarState st, EventContext ctx) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         p.sendMessage(this.plugin.lang.tr(p, "event.restful_sleep.end"));
         p.removePotionEffect(PotionEffectType.HEALTH_BOOST);
      }
   }

   @Override
   public void onDayTick(CalendarState st, EventContext ctx) {
   }

   @Override
   public void onTick(CalendarState st, EventContext ctx) {
   }

   @EventHandler
   public void onBedEnter(PlayerBedEnterEvent e) {
      if (!e.isCancelled()) {
         Player p = e.getPlayer();
         if (p.getWorld().getEnvironment() == Environment.NORMAL) {
            p.sendMessage(this.plugin.lang.tr(p, "event.restful_sleep.sleeping"));
         }
      }
   }

   @EventHandler
   public void onBedLeave(PlayerBedLeaveEvent e) {
      Player p = e.getPlayer();
      if (p.getGameMode() != GameMode.SPECTATOR) {
         p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, this.healthBoostSeconds * 20, 0, true, false, true));
         p.sendMessage(this.plugin.lang.tr(p, "event.restful_sleep.buffed"));
      }
   }

   @EventHandler
   public void onDamage(EntityDamageEvent e) {
      if (e.getEntity() instanceof Player p) {
         if (p.hasPotionEffect(PotionEffectType.HEALTH_BOOST)) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (p.getHealth() <= 10.0) {
                  p.removePotionEffect(PotionEffectType.HEALTH_BOOST);
                  p.sendMessage(this.plugin.lang.tr(p, "event.restful_sleep.lost"));
               }
            }, 1L);
         }
      }
   }
}
