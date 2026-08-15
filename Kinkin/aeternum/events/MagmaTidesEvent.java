package Kinkin.aeternum.events;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class MagmaTidesEvent implements SeasonalEvent {
   private final AeternumSeasonsPlugin plugin;
   private boolean enabled;
   private int minDur;
   private int maxDur;
   private double baseChance;
   private double biggerCubeChance;
   private double extraCreamChance;

   public MagmaTidesEvent(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.reloadFromConfig();
   }

   private void reloadFromConfig() {
      FileConfiguration y = YamlEvents.get(this.plugin);
      this.enabled = y.getBoolean("events.magma_tides.enabled", true);
      this.minDur = y.getInt("events.magma_tides.min_duration_days", 1);
      this.maxDur = y.getInt("events.magma_tides.max_duration_days", 2);
      this.baseChance = y.getDouble("events.magma_tides.base_chance_per_day", 0.1);
      this.biggerCubeChance = y.getDouble("events.magma_tides.bigger_cube_chance", 0.3);
      this.extraCreamChance = y.getDouble("events.magma_tides.extra_cream_chance", 0.4);
   }

   @Override
   public String getId() {
      return "magma_tides";
   }

   @Override
   public String getDisplayName() {
      return "Magma Tides";
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
            p.sendTitle(this.plugin.lang.tr(p, "event.magma_tides.title"), this.plugin.lang.tr(p, "event.magma_tides.subtitle"), 20, 80, 40);
         }
      }
   }

   @Override
   public void onEnd(CalendarState st, EventContext ctx) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.getWorld().getEnvironment() == Environment.NETHER) {
            p.sendMessage(this.plugin.lang.tr(p, "event.magma_tides.end"));
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
      if (e.getEntity() instanceof MagmaCube cube) {
         if (cube.getWorld().getEnvironment() == Environment.NETHER) {
            if (!(Math.random() > this.biggerCubeChance)) {
               int s = cube.getSize();
               cube.setSize(Math.min(4, s + 1));
               cube.getWorld().spawnParticle(Particle.LAVA, cube.getLocation().add(0.0, 0.8, 0.0), 10, 0.4, 0.3, 0.4, 0.01);
            }
         }
      }
   }

   @EventHandler
   public void onDeath(EntityDeathEvent e) {
      if (e.getEntity() instanceof MagmaCube cube) {
         if (cube.getWorld().getEnvironment() == Environment.NETHER) {
            if (cube.getKiller() != null) {
               if (!(Math.random() > this.extraCreamChance)) {
                  int amt = 1 + ThreadLocalRandom.current().nextInt(2);
                  e.getDrops().add(new ItemStack(Material.MAGMA_CREAM, amt));
               }
            }
         }
      }
   }
}
