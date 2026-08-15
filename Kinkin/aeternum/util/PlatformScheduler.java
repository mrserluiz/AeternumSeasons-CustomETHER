package Kinkin.aeternum.util;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PlatformScheduler {
   private PlatformScheduler() {
   }

   private static boolean hasMethod(Class<?> c, String name, Class<?>... params) {
      try {
         c.getMethod(name, params);
         return true;
      } catch (NoSuchMethodException ex) {
         return false;
      }
   }

   public static boolean hasGlobalRegionScheduler() {
      return hasMethod(Bukkit.class, "getGlobalRegionScheduler");
   }

   private static Object getGlobalScheduler() {
      try {
         Method m = Bukkit.class.getMethod("getGlobalRegionScheduler");
         return m.invoke(null);
      } catch (Throwable t) {
         return null;
      }
   }

   private static Object getRegionScheduler() {
      try {
         Method m = Bukkit.class.getMethod("getRegionScheduler");
         return m.invoke(null);
      } catch (Throwable t) {
         return null;
      }
   }

   public static void executeGlobal(Plugin plugin, Runnable run) {
      Object gs = getGlobalScheduler();
      if (gs != null) {
         try {
            Method exec = gs.getClass().getMethod("execute", Plugin.class, Runnable.class);
            exec.invoke(gs, plugin, run);
            return;
         } catch (Throwable var4) {
         }
      }

      Bukkit.getScheduler().runTask(plugin, run);
   }

   public static PlatformScheduler.TaskHandle runGlobalTimer(Plugin plugin, Runnable run, long delayTicks, long periodTicks) {
      Object gs = getGlobalScheduler();
      if (gs != null) {
         try {
            Method m = gs.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Consumer<Object> c = ignored -> run.run();
            Object task = m.invoke(gs, plugin, c, delayTicks, periodTicks);
            return new PlatformScheduler.ReflectTaskHandle(task);
         } catch (Throwable var10) {
         }
      }

      return new PlatformScheduler.BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, run, delayTicks, periodTicks));
   }

   public static void executeAtLocation(Plugin plugin, Location loc, Runnable run) {
      if (loc != null) {
         Object rs = getRegionScheduler();
         if (rs != null) {
            try {
               Method exec = rs.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class);
               exec.invoke(rs, plugin, loc, run);
               return;
            } catch (Throwable var5) {
            }
         }

         Bukkit.getScheduler().runTask(plugin, run);
      }
   }

   public static PlatformScheduler.TaskHandle runGlobalLater(Plugin plugin, Runnable run, long delayTicks) {
      Object gs = getGlobalScheduler();
      if (gs != null) {
         try {
            Method m = gs.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            Consumer<Object> c = ignored -> run.run();
            Object task = m.invoke(gs, plugin, c, delayTicks);
            return new PlatformScheduler.ReflectTaskHandle(task);
         } catch (Throwable var8) {
         }
      }

      return new PlatformScheduler.BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, run, delayTicks));
   }

   private static final class BukkitTaskHandle implements PlatformScheduler.TaskHandle {
      private final BukkitTask task;

      private BukkitTaskHandle(BukkitTask task) {
         this.task = task;
      }

      @Override
      public void cancel() {
         if (this.task != null) {
            this.task.cancel();
         }
      }
   }

   private static final class ReflectTaskHandle implements PlatformScheduler.TaskHandle {
      private final Object task;

      private ReflectTaskHandle(Object task) {
         this.task = task;
      }

      @Override
      public void cancel() {
         if (this.task != null) {
            try {
               Method m = this.task.getClass().getMethod("cancel");
               m.invoke(this.task);
            } catch (Throwable var2) {
            }
         }
      }
   }

   public interface TaskHandle {
      void cancel();
   }
}
