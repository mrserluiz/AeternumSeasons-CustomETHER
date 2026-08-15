package Kinkin.aeternum.compat;

import java.lang.reflect.Field;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldCompat {
   private static boolean warnedDaylightMissing = false;
   private static boolean warnedWeatherMissing = false;
   private static volatile boolean daylightRuleResolved = false;
   private static volatile boolean weatherRuleResolved = false;
   private static GameRule<Boolean> cachedDaylightRule;
   private static GameRule<Boolean> cachedWeatherRule;

   private WorldCompat() {
   }

   private static GameRule<Boolean> findBooleanGameRule(String... fieldNames) {
      String[] ownerNames = new String[]{"org.bukkit.GameRules", "org.bukkit.GameRule"};

      for (String ownerName : ownerNames) {
         try {
            Class<?> owner = Class.forName(ownerName);

            for (String name : fieldNames) {
               try {
                  Field f = owner.getField(name);
                  if (f.get(null) instanceof GameRule<?> rule) {
                     return (GameRule<Boolean>)rule;
                  }
               } catch (NoSuchFieldException var14) {
               }
            }
         } catch (ClassNotFoundException var15) {
         } catch (Throwable var16) {
         }
      }

      return null;
   }

   public static GameRule<Boolean> daylightCycleRule() {
      if (!daylightRuleResolved) {
         synchronized (WorldCompat.class) {
            if (!daylightRuleResolved) {
               cachedDaylightRule = findBooleanGameRule("ADVANCE_TIME", "DO_DAYLIGHT_CYCLE");
               daylightRuleResolved = true;
            }
         }
      }

      return cachedDaylightRule;
   }

   public static GameRule<Boolean> weatherCycleRule() {
      if (!weatherRuleResolved) {
         synchronized (WorldCompat.class) {
            if (!weatherRuleResolved) {
               cachedWeatherRule = findBooleanGameRule("ADVANCE_WEATHER", "DO_WEATHER_CYCLE");
               weatherRuleResolved = true;
            }
         }
      }

      return cachedWeatherRule;
   }

   public static boolean isTimeAdvancementEnabled(World world) {
      if (!supportsManualTime(world)) {
         return true;
      }

      GameRule<Boolean> rule = daylightCycleRule();
      if (rule == null) {
         return true;
      }

      try {
         Boolean value = (Boolean)world.getGameRuleValue(rule);
         return value == null || value;
      } catch (Throwable ignored) {
         return true;
      }
   }

   public static boolean disableDaylightCycle(World world, JavaPlugin plugin) {
      if (world == null) {
         return false;
      }

      GameRule<Boolean> rule = daylightCycleRule();
      if (rule == null) {
         if (!warnedDaylightMissing) {
            warnedDaylightMissing = true;
            plugin.getLogger().warning("[Compat] No se encontró GameRule de daylight cycle (ni vieja ni nueva API).");
         }

         return false;
      } else {
         try {
            return world.setGameRule(rule, false);
         } catch (Throwable t) {
            plugin.getLogger().warning("[Compat] No se pudo desactivar daylight cycle en " + world.getName() + ": " + t.getMessage());
            return false;
         }
      }
   }

   public static boolean disableWeatherCycle(World world, JavaPlugin plugin) {
      if (world == null) {
         return false;
      }

      GameRule<Boolean> rule = weatherCycleRule();
      if (rule == null) {
         if (!warnedWeatherMissing) {
            warnedWeatherMissing = true;
            plugin.getLogger().warning("[Compat] No se encontró GameRule de weather cycle (ni vieja ni nueva API).");
         }

         return false;
      } else {
         try {
            return world.setGameRule(rule, false);
         } catch (Throwable t) {
            plugin.getLogger().warning("[Compat] No se pudo desactivar weather cycle en " + world.getName() + ": " + t.getMessage());
            return false;
         }
      }
   }

   public static boolean supportsManualTime(World world) {
      return world == null ? false : world.getEnvironment() == Environment.NORMAL;
   }

   public static boolean trySetTime(World world, long time, JavaPlugin plugin) {
      if (!supportsManualTime(world)) {
         return false;
      }

      try {
         world.setTime(time);
         return true;
      } catch (IllegalArgumentException ex) {
         plugin.getLogger().warning("[Compat] No se pudo aplicar setTime en " + world.getName() + ": " + ex.getMessage());
         return false;
      } catch (Throwable t) {
         plugin.getLogger().warning("[Compat] Error aplicando setTime en " + world.getName() + ": " + t.getMessage());
         return false;
      }
   }

   public static boolean trySetFullTime(World world, long fullTime, JavaPlugin plugin) {
      if (!supportsManualTime(world)) {
         return false;
      }

      try {
         world.setFullTime(fullTime);
         return true;
      } catch (IllegalArgumentException ex) {
         plugin.getLogger().warning("[Compat] No se pudo aplicar setFullTime en " + world.getName() + ": " + ex.getMessage());
         return false;
      } catch (Throwable t) {
         plugin.getLogger().warning("[Compat] Error aplicando setFullTime en " + world.getName() + ": " + t.getMessage());
         return false;
      }
   }
}
