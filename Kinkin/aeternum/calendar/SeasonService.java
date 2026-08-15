package Kinkin.aeternum.calendar;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.compat.WorldCompat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

public final class SeasonService implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private static final String FROST_WORLD_NAME = "aeternum_frost";
   private static final String HEAT_WORLD_NAME = "aeternum_heat";
   private static final long CUSTOM_CLOCK_UPDATE_PERIOD_TICKS = 5L;
   private final EnumMap<CalendarChannel, SeasonService.ChannelRuntime> runtimes = new EnumMap<>(CalendarChannel.class);
   private final Map<String, CalendarChannel> explicitWorldChannels = new HashMap<>();
   private boolean registered;
   private long lastFrostManualAdvanceMs = 0L;
   private final Map<UUID, SeasonService.SleepSnapshot> sleepSnapshots = new HashMap<>();
   private final Map<UUID, SeasonService.ManualTimeCommand> pendingManualTimeCommands = new HashMap<>();

   public SeasonService(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.rebuildFromConfig();
   }

   public void register() {
      if (!this.registered) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
         this.registered = true;
      }

      for (SeasonService.ChannelRuntime rt : this.runtimes.values()) {
         rt.registerTasks();
      }
   }

   public void unregister() {
      for (SeasonService.ChannelRuntime rt : this.runtimes.values()) {
         rt.unregisterTasks();
      }

      this.sleepSnapshots.clear();
      this.pendingManualTimeCommands.clear();
      HandlerList.unregisterAll(this);
      this.registered = false;
   }

   public synchronized void reloadCalendarSettings() {
      boolean wasRegistered = this.registered;
      if (wasRegistered) {
         for (SeasonService.ChannelRuntime rt : this.runtimes.values()) {
            rt.unregisterTasks();
         }
      }

      this.rebuildFromConfig();
      if (wasRegistered) {
         for (SeasonService.ChannelRuntime rt : this.runtimes.values()) {
            rt.registerTasks();
         }
      }
   }

   private void rebuildFromConfig() {
      this.runtimes.clear();
      this.explicitWorldChannels.clear();
      SeasonService.ChannelRuntime overworld = this.buildRuntime(CalendarChannel.OVERWORLD);
      SeasonService.ChannelRuntime nether = this.buildRuntime(CalendarChannel.NETHER);
      this.runtimes.put(CalendarChannel.OVERWORLD, overworld);
      this.runtimes.put(CalendarChannel.NETHER, nether);
      this.indexWorldMappings(overworld);
      this.indexWorldMappings(nether);
   }

   private void indexWorldMappings(SeasonService.ChannelRuntime rt) {
      if (rt != null && rt.enabled) {
         for (String name : rt.worldNames) {
            if (name != null && !name.isBlank()) {
               this.explicitWorldChannels.put(name.trim().toLowerCase(Locale.ROOT), rt.channel);
            }
         }
      }
   }

   private SeasonService.ChannelRuntime buildRuntime(CalendarChannel channel) {
      ConfigurationSection section = this.getChannelSection(channel);
      boolean legacyOverworld = channel == CalendarChannel.OVERWORLD && section == null;
      boolean enabled = legacyOverworld || this.getBoolean(section, "enabled", channel == CalendarChannel.OVERWORLD);
      CalendarMode mode = CalendarMode.fromString(this.getString(section, "mode", "SEASONS"), CalendarMode.SEASONS);
      Set<String> worlds = new LinkedHashSet<>();

      for (String s : this.getStringList(section, "worlds")) {
         if (s != null && !s.isBlank()) {
            worlds.add(s.trim());
         }
      }

      if (legacyOverworld && worlds.isEmpty()) {
         worlds.add("world");
      }

      int daysPerSeason = Math.max(4, this.getInt(section, "days_per_season", 28));
      EnumMap<Season, Integer> bySeason = new EnumMap<>(Season.class);
      ConfigurationSection bySeasonSec = this.getSubSection(section, "days_per_season_by_season");
      if (legacyOverworld && bySeasonSec == null) {
         bySeasonSec = this.readLegacySection("days_per_season_by_season", "calendar.days_per_season_by_season");
      }

      if (bySeasonSec != null) {
         for (String key : bySeasonSec.getKeys(false)) {
            Season s;
            try {
               s = Season.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
               continue;
            }

            bySeason.put(s, Math.max(4, bySeasonSec.getInt(key, daysPerSeason)));
         }
      }

      ConfigurationSection advance = this.getSubSection(section, "advance");
      if (legacyOverworld && advance == null) {
         advance = this.readLegacySection("advance", "calendar.advance");
      }

      boolean followWorldTime = this.getBoolean(advance, "follow_overworld_time", legacyOverworld);
      boolean advanceOnSleep = this.getBoolean(advance, "on_sleep", true);
      int seasonDaylightMinutes = Math.max(0, this.getInt(advance, "daylight_minutes", 0));
      int seasonNightMinutes = Math.max(0, this.getInt(advance, "night_minutes", 0));
      boolean requirePlayersOnServer = this.getBoolean(advance, "require_players_on_server", false);
      String timeAnchorWorld = this.getString(advance, "time_anchor_world", channel == CalendarChannel.NETHER ? "aeternum_heat" : "world");
      int maxCatchupDays = Math.max(0, this.getInt(advance, "max_catchup_days", 2));
      List<CalendarMonthDefinition> months = new ArrayList<>();
      ConfigurationSection monthsSection = this.getSubSection(section, "months");
      boolean monthsEnabled = false;
      if (monthsSection != null && monthsSection.getBoolean("enabled", false)) {
         monthsEnabled = true;
         List<String> order = monthsSection.getStringList("order");
         ConfigurationSection defs = monthsSection.getConfigurationSection("definitions");
         if (defs != null) {
            for (String monthId : order) {
               if (monthId != null && !monthId.isBlank()) {
                  ConfigurationSection def = defs.getConfigurationSection(monthId);
                  if (def != null) {
                     Season season;
                     try {
                        season = Season.valueOf(def.getString("season", "SPRING").toUpperCase(Locale.ROOT));
                     } catch (IllegalArgumentException ex) {
                        season = Season.SPRING;
                     }

                     String displayName = def.getString("display_name", monthId);
                     int days = Math.max(1, def.getInt("days", 30));
                     int rtMin = Math.max(0, def.getInt("real_time_minutes_per_day", 0));
                     int daylight = Math.max(0, def.getInt("daylight_minutes", 0));
                     int night = Math.max(0, def.getInt("night_minutes", 0));
                     months.add(new CalendarMonthDefinition(monthId, displayName, season, days, rtMin, daylight, night));
                  }
               }
            }
         }
      }

      if (monthsEnabled && months.isEmpty()) {
         monthsEnabled = false;
      }

      if (mode == CalendarMode.MONTHS && !monthsEnabled) {
         mode = CalendarMode.SEASONS;
      }

      List<CalendarMonthDefinition> effectiveMonths = monthsEnabled ? months : List.of();
      File stateFile = new File(this.plugin.getDataFolder(), "data/calendars/" + channel.id() + ".yml");
      CalendarState state = this.loadState(channel, mode, effectiveMonths, stateFile);
      return new SeasonService.ChannelRuntime(
         channel,
         enabled,
         mode,
         worlds,
         daysPerSeason,
         bySeason,
         advanceOnSleep,
         seasonDaylightMinutes,
         seasonNightMinutes,
         followWorldTime,
         requirePlayersOnServer,
         timeAnchorWorld,
         maxCatchupDays,
         effectiveMonths,
         stateFile,
         state
      );
   }

   private ConfigurationSection getChannelSection(CalendarChannel channel) {
      ConfigurationSection root = this.plugin.cfg.calendar.getConfigurationSection("calendar.channels");
      return root == null ? null : root.getConfigurationSection(channel.name());
   }

   private ConfigurationSection readLegacySection(String plainPath, String calendarPath) {
      if (this.plugin.cfg.calendar.isConfigurationSection(plainPath)) {
         return this.plugin.cfg.calendar.getConfigurationSection(plainPath);
      } else {
         return this.plugin.cfg.calendar.isConfigurationSection(calendarPath) ? this.plugin.cfg.calendar.getConfigurationSection(calendarPath) : null;
      }
   }

   private ConfigurationSection getSubSection(ConfigurationSection root, String path) {
      return root == null ? null : root.getConfigurationSection(path);
   }

   private String getString(ConfigurationSection sec, String path, String def) {
      return sec == null ? def : sec.getString(path, def);
   }

   private int getInt(ConfigurationSection sec, String path, int def) {
      return sec == null ? def : sec.getInt(path, def);
   }

   private boolean getBoolean(ConfigurationSection sec, String path, boolean def) {
      return sec == null ? def : sec.getBoolean(path, def);
   }

   private List<String> getStringList(ConfigurationSection sec, String path) {
      return sec == null ? List.of() : sec.getStringList(path);
   }

   private CalendarState loadState(CalendarChannel channel, CalendarMode mode, List<CalendarMonthDefinition> months, File file) {
      if (!file.exists()) {
         if (mode == CalendarMode.MONTHS && !months.isEmpty()) {
            CalendarMonthDefinition first = months.get(0);
            CalendarState st = new CalendarState(1, 1, first.getSeason());
            st.monthsEnabled = true;
            st.monthId = first.getId();
            st.monthDisplayName = first.getDisplayName();
            st.monthIndex = 0;
            st.daysInMonth = first.getDays();
            return st;
         } else {
            return new CalendarState(1, 1, channel == CalendarChannel.NETHER ? Season.SUMMER : Season.SPRING);
         }
      } else {
         YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
         int year = Math.max(1, y.getInt("year", 1));
         int day = Math.max(1, y.getInt("day", 1));

         Season season;
         try {
            season = Season.valueOf(y.getString("season", channel == CalendarChannel.NETHER ? "SUMMER" : "SPRING"));
         } catch (IllegalArgumentException ex) {
            season = channel == CalendarChannel.NETHER ? Season.SUMMER : Season.SPRING;
         }

         CalendarState st = new CalendarState(year, day, season);
         if (mode == CalendarMode.MONTHS && !months.isEmpty()) {
            String monthId = y.getString("month", months.get(0).getId());
            int idx = this.findMonthIndex(months, monthId);
            if (idx < 0) {
               idx = 0;
            }

            CalendarMonthDefinition def = months.get(idx);
            st.monthsEnabled = true;
            st.monthId = def.getId();
            st.monthDisplayName = def.getDisplayName();
            st.monthIndex = idx;
            st.daysInMonth = def.getDays();
            st.season = def.getSeason();
            if (st.day > def.getDays()) {
               st.day = def.getDays();
            }
         }

         return st;
      }
   }

   public CalendarChannel resolveChannel(World world) {
      if (world == null) {
         return null;
      } else {
         CalendarChannel explicit = this.explicitWorldChannels.get(world.getName().toLowerCase(Locale.ROOT));
         if (explicit != null && this.isChannelEnabled(explicit)) {
            return explicit;
         } else if (world.getName().equalsIgnoreCase("aeternum_heat") && this.isChannelEnabled(CalendarChannel.NETHER)) {
            return CalendarChannel.NETHER;
         } else if (world.getEnvironment() == Environment.NETHER && this.isChannelEnabled(CalendarChannel.NETHER)) {
            return CalendarChannel.NETHER;
         } else {
            return world.getEnvironment() == Environment.NORMAL && this.isChannelEnabled(CalendarChannel.OVERWORLD) ? CalendarChannel.OVERWORLD : null;
         }
      }
   }

   public boolean isPermanentWinterWorld(World world) {
      return world != null && world.getName().equalsIgnoreCase("aeternum_frost");
   }

   public boolean isChannelEnabled(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null && rt.enabled;
   }

   public List<World> getWorlds(CalendarChannel channel) {
      return Bukkit.getWorlds().stream().filter(w -> this.resolveChannel(w) == channel).collect(Collectors.toList());
   }

   @EventHandler
   public void onWorldLoad(WorldLoadEvent e) {
      this.markRuntimeWorldCachesDirty();
   }

   @EventHandler
   public void onWorldUnload(WorldUnloadEvent e) {
      for (SeasonService.ChannelRuntime runtime : this.runtimes.values()) {
         runtime.evictCachedWorld(e.getWorld());
      }
   }

   private void markRuntimeWorldCachesDirty() {
      for (SeasonService.ChannelRuntime runtime : this.runtimes.values()) {
         runtime.markWorldCacheDirty();
      }
   }

   public synchronized CalendarState getStateCopy() {
      return this.getStateCopy(CalendarChannel.OVERWORLD);
   }

   public synchronized CalendarState getStateCopy(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt == null ? new CalendarState(1, 1, Season.SPRING) : rt.state.copy();
   }

   public synchronized CalendarState getStateCopy(World world) {
      CalendarChannel channel = this.resolveChannel(world);
      CalendarState state = channel != null ? this.getStateCopy(channel) : this.getStateCopy(CalendarChannel.OVERWORLD);
      if (this.isPermanentWinterWorld(world)) {
         state.season = Season.WINTER;
      }

      return state;
   }

   public synchronized CalendarState peekTomorrowState(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt == null ? null : rt.peekTomorrow();
   }

   public int getDefaultDaysPerSeason() {
      return this.getDefaultDaysPerSeason(CalendarChannel.OVERWORLD);
   }

   public int getDefaultDaysPerSeason(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null ? rt.daysPerSeason : 28;
   }

   public int getDaysPerSeason() {
      return this.getDaysPerSeason(CalendarChannel.OVERWORLD);
   }

   public int getDaysPerSeason(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null ? rt.getCurrentPeriodLength() : 28;
   }

   public int getCurrentPeriodLength(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null ? rt.getCurrentPeriodLength() : 28;
   }

   public String getCurrentMonthId(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null ? rt.state.monthId : null;
   }

   public String getCurrentMonthDisplayName(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null ? rt.state.monthDisplayName : null;
   }

   public boolean isMonthsEnabled(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null && rt.mode == CalendarMode.MONTHS;
   }

   public CalendarMode getMode(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      return rt != null ? rt.mode : CalendarMode.SEASONS;
   }

   public synchronized void nextDay() {
      this.nextDay(CalendarChannel.OVERWORLD);
   }

   public synchronized void nextDay(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      if (rt != null) {
         rt.nextDay(false);
      }
   }

   public synchronized void setSeason(Season season) {
      this.setSeason(CalendarChannel.OVERWORLD, season);
   }

   public synchronized void setSeason(CalendarChannel channel, Season season) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      if (rt != null) {
         rt.setSeason(season);
      }
   }

   public synchronized void setDay(int day) {
      this.setDay(CalendarChannel.OVERWORLD, day);
   }

   public synchronized void setDay(CalendarChannel channel, int day) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      if (rt != null) {
         rt.setDay(day);
      }
   }

   public synchronized void setYear(int year) {
      this.setYear(CalendarChannel.OVERWORLD, year);
   }

   public synchronized void setYear(CalendarChannel channel, int year) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      if (rt != null) {
         rt.setYear(year);
      }
   }

   private boolean hasAnyOnlinePlayer() {
      return !Bukkit.getOnlinePlayers().isEmpty();
   }

   private boolean isNightTime(long worldTime) {
      long t = worldTime % 24000L;
      if (t < 0L) {
         t += 24000L;
      }

      return t >= 12541L || t <= 1000L;
   }

   private static String getCompatSkipReasonName(TimeSkipEvent e) {
      if (e == null) {
         return "";
      }

      try {
         Object reason = e.getClass().getMethod("getSkipReason").invoke(e);
         if (reason instanceof Enum) {
            return ((Enum)reason).name();
         } else {
            return reason != null ? String.valueOf(reason) : "";
         }
      } catch (Throwable ignored) {
         return "";
      }
   }

   private SeasonService.ManualTimeCommand parseManualTimeCommand(String raw, long currentWorldTime) {
      if (raw == null) {
         return null;
      }

      String cmd = raw.trim();
      if (cmd.isEmpty()) {
         return null;
      }

      if (cmd.charAt(0) == '/') {
         cmd = cmd.substring(1).trim();
      }

      if (cmd.isEmpty()) {
         return null;
      }

      String[] split = cmd.split("\\s+");
      if (split.length == 0) {
         return null;
      }

      String root = split[0].toLowerCase(Locale.ROOT);
      if (root.contains(":")) {
         root = root.substring(root.indexOf(58) + 1);
      }

      if (root.equals("day")) {
         return new SeasonService.ManualTimeCommand(0L, 1);
      }

      if (root.equals("night")) {
         long current = this.normalizeTime(currentWorldTime);
         return new SeasonService.ManualTimeCommand(14000L, 14000L < current ? 1 : 0);
      }

      if (root.equals("time") && split.length >= 3) {
         String action = split[1].toLowerCase(Locale.ROOT);
         String value = split[2].toLowerCase(Locale.ROOT);
         if (value.contains(":")) {
            value = value.substring(value.indexOf(58) + 1);
         }

         if (action.equals("add")) {
            Long ticks = this.parseTimeTicks(value);
            if (ticks == null) {
               return null;
            }

            long current = this.normalizeTime(currentWorldTime);

            long total;
            try {
               total = Math.addExact(current, ticks);
            } catch (ArithmeticException overflow) {
               return null;
            }

            long crossings = ticks > 0L ? Math.floorDiv(total, 24000L) : 0L;
            int days = (int)Math.min(2147483647L, Math.max(0L, crossings));
            return new SeasonService.ManualTimeCommand(Math.floorMod(total, 24000L), days);
         } else {
            if (!action.equals("set")) {
               return null;
            }

            long current = this.normalizeTime(currentWorldTime);

            return switch (value) {
               case "day", "morning" -> this.manualSetCommand(current, 1000L);
               case "night" -> this.manualSetCommand(current, 13000L);
               case "noon" -> this.manualSetCommand(current, 6000L);
               case "midnight" -> this.manualSetCommand(current, 18000L);
               default -> {
                  Long ticks = this.parseTimeTicks(value);
                  yield ticks != null ? this.manualSetCommand(current, Math.floorMod(ticks, 24000L)) : null;
               }
            };
         }
      } else {
         return null;
      }
   }

   private SeasonService.ManualTimeCommand manualSetCommand(long currentWorldTime, long targetWorldTime) {
      long current = this.normalizeTime(currentWorldTime);
      long target = this.normalizeTime(targetWorldTime);
      return new SeasonService.ManualTimeCommand(target, target < current ? 1 : 0);
   }

   private long normalizeTime(long time) {
      long normalized = time % 24000L;
      return normalized < 0L ? normalized + 24000L : normalized;
   }

   private Long parseTimeTicks(String raw) {
      if (raw != null && !raw.isBlank()) {
         String value = raw.trim().toLowerCase(Locale.ROOT);
         double multiplier = 1.0;
         char suffix = value.charAt(value.length() - 1);
         if (suffix == 'd' || suffix == 's' || suffix == 't') {
            value = value.substring(0, value.length() - 1);
            multiplier = suffix == 'd' ? 24000.0 : (suffix == 's' ? 20.0 : 1.0);
         }

         try {
            double amount = Double.parseDouble(value);
            return !Double.isFinite(amount) ? null : Math.round(amount * multiplier);
         } catch (NumberFormatException ignored) {
            return null;
         }
      } else {
         return null;
      }
   }

   private void scheduleManualTimeCommand(World world, SeasonService.ManualTimeCommand cmd) {
      if (world != null && cmd != null) {
         UUID worldId = world.getUID();
         this.pendingManualTimeCommands.put(worldId, cmd);
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (this.pendingManualTimeCommands.remove(worldId, cmd)) {
               CalendarChannel channel = this.resolveChannel(world);
               if (channel != null) {
                  SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
                  if (rt != null && rt.enabled) {
                     if (!rt.requirePlayersOnServer || this.hasAnyOnlinePlayer()) {
                        if (world.getEnvironment() == Environment.NORMAL) {
                           rt.applyManualCommand(cmd.targetWorldTime, cmd.daysToAdvance);
                        }
                     }
                  }
               }
            }
         });
      }
   }

   private World getConsoleDefaultWorld() {
      SeasonService.ChannelRuntime rt = this.runtimes.get(CalendarChannel.OVERWORLD);
      if (rt != null) {
         World anchor = rt.getAnchorWorld();
         if (anchor != null) {
            return anchor;
         }
      }

      return Bukkit.getWorld("world");
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
      World world = e.getPlayer().getWorld();
      SeasonService.ManualTimeCommand cmd = this.parseManualTimeCommand(e.getMessage(), world.getTime());
      if (cmd != null) {
         this.scheduleManualTimeCommand(world, cmd);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onServerCommand(ServerCommandEvent e) {
      World world = this.getConsoleDefaultWorld();
      if (world != null) {
         SeasonService.ManualTimeCommand cmd = this.parseManualTimeCommand(e.getCommand(), world.getTime());
         if (cmd != null) {
            this.scheduleManualTimeCommand(world, cmd);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPlayerBedEnter(PlayerBedEnterEvent e) {
      if (e.getBedEnterResult() == BedEnterResult.OK) {
         Player p = e.getPlayer();
         World w = p.getWorld();
         this.sleepSnapshots.put(p.getUniqueId(), new SeasonService.SleepSnapshot(w.getUID(), System.currentTimeMillis(), w.getTime()));
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onTimeSkip(TimeSkipEvent e) {
      World w = e.getWorld();
      CalendarChannel channel = this.resolveChannel(w);
      if (channel != null) {
         SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
         if (rt != null && rt.enabled) {
            if (!rt.requirePlayersOnServer || this.hasAnyOnlinePlayer()) {
               if (w.getEnvironment() == Environment.NORMAL) {
                  String reason = getCompatSkipReasonName(e);
                  long cur = w.getTime();
                  long skip = e.getSkipAmount();
                  long newTime = (cur + skip) % 24000L;
                  if (newTime < 0L) {
                     newTime += 24000L;
                  }

                  if ("NIGHT_SKIP".equals(reason)) {
                     if (rt.advanceOnSleep) {
                        long now = System.currentTimeMillis();
                        if (now - rt.lastOverworldSleepRecoveryMs >= 1200L) {
                           rt.lastOverworldSleepRecoveryMs = now;
                           rt.applyManualCommand(0L, 1);
                        }
                     }
                  } else {
                     if ("COMMAND".equals(reason) || "CUSTOM".equals(reason)) {
                        SeasonService.ManualTimeCommand pending = this.pendingManualTimeCommands.remove(w.getUID());
                        if (pending != null) {
                           rt.applyManualCommand(pending.targetWorldTime, pending.daysToAdvance);
                           return;
                        }
                     }

                     if ("COMMAND".equals(reason)) {
                        long total;
                        try {
                           total = Math.addExact(this.normalizeTime(cur), skip);
                        } catch (ArithmeticException overflow) {
                           return;
                        }

                        long crossings = skip > 0L ? Math.floorDiv(total, 24000L) : (newTime < this.normalizeTime(cur) ? 1L : 0L);
                        int days = (int)Math.min(2147483647L, Math.max(0L, crossings));
                        rt.applyManualCommand(newTime, days);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPlayerBedLeave(PlayerBedLeaveEvent e) {
      Player p = e.getPlayer();
      World w = p.getWorld();
      SeasonService.SleepSnapshot snapshot = this.sleepSnapshots.remove(p.getUniqueId());
      CalendarChannel channel = this.resolveChannel(w);
      if (channel != null) {
         SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
         if (rt != null && rt.enabled) {
            if (rt.advanceOnSleep) {
               if (!rt.requirePlayersOnServer || this.hasAnyOnlinePlayer()) {
                  if (w.getEnvironment() == Environment.NORMAL) {
                     long now = System.currentTimeMillis();
                     if (now - rt.lastOverworldSleepRecoveryMs >= 1200L) {
                        boolean sleptLongEnough = snapshot != null && snapshot.worldId.equals(w.getUID()) && now - snapshot.enteredAtMs >= 1500L;
                        boolean enteredAtNight = snapshot != null && this.isNightTime(snapshot.enteredWorldTime);
                        if (rt.hasCustomClock() && sleptLongEnough && enteredAtNight) {
                           rt.lastOverworldSleepRecoveryMs = now;
                           rt.lastWorldDayIdx = w.getFullTime() / 24000L;
                           rt.applyManualCommand(0L, 1);
                        } else if (!rt.followWorldTime) {
                           rt.lastOverworldSleepRecoveryMs = now;
                           rt.applyManualCommand(0L, 1);
                        } else if (w.getTime() <= 1000L) {
                           World anchor = rt.getAnchorWorld();
                           if (anchor != null) {
                              if (anchor.getUID().equals(w.getUID())) {
                                 rt.lastOverworldSleepRecoveryMs = now;
                                 rt.lastWorldDayIdx = w.getFullTime() / 24000L;
                                 rt.applyManualCommand(0L, 1);
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

   private int findMonthIndex(List<CalendarMonthDefinition> months, String monthId) {
      if (monthId == null) {
         return -1;
      }

      for (int i = 0; i < months.size(); i++) {
         if (months.get(i).getId().equalsIgnoreCase(monthId)) {
            return i;
         }
      }

      return -1;
   }

   public synchronized void persistNow() {
      for (SeasonService.ChannelRuntime rt : this.runtimes.values()) {
         if (rt != null) {
            rt.persistNow();
         }
      }
   }

   public synchronized void persistNow(CalendarChannel channel) {
      SeasonService.ChannelRuntime rt = this.runtimes.get(channel);
      if (rt != null) {
         rt.persistNow();
      }
   }

   private final class ChannelRuntime {
      private final CalendarChannel channel;
      private final boolean enabled;
      private final CalendarMode mode;
      private final Set<String> worldNames;
      private final int daysPerSeason;
      private final EnumMap<Season, Integer> daysPerSeasonBySeason;
      private final boolean advanceOnSleep;
      private final int seasonDaylightMinutes;
      private final int seasonNightMinutes;
      private final boolean followWorldTime;
      private final boolean requirePlayersOnServer;
      private final String timeAnchorWorldName;
      private final int maxCatchupDays;
      private final List<CalendarMonthDefinition> months;
      private final File stateFile;
      private CalendarState state;
      private BukkitTask worldClockTask;
      private BukkitTask customClockTask;
      private long clockStartMs = Long.MIN_VALUE;
      private long customClockPausedAtMs = Long.MIN_VALUE;
      private String clockPhaseKey = null;
      private final Map<UUID, Long> customClockWorldDays = new HashMap<>();
      private List<World> cachedManualTimeWorlds = List.of();
      private World cachedAnchorWorld;
      private boolean worldCacheDirty = true;
      private long lastWorldDayIdx = Long.MIN_VALUE;
      private long lastDayAdvanceMs = 0L;
      private long lastOverworldSleepRecoveryMs = 0L;
      private long lastManualCommandSyncMs = 0L;
      private long lastManualCommandTarget = Long.MIN_VALUE;
      private int lastManualCommandDaysAdvanced = 0;

      private ChannelRuntime(
         CalendarChannel channel,
         boolean enabled,
         CalendarMode mode,
         Set<String> worldNames,
         int daysPerSeason,
         EnumMap<Season, Integer> daysPerSeasonBySeason,
         boolean advanceOnSleep,
         int seasonDaylightMinutes,
         int seasonNightMinutes,
         boolean followWorldTime,
         boolean requirePlayersOnServer,
         String timeAnchorWorldName,
         int maxCatchupDays,
         List<CalendarMonthDefinition> months,
         File stateFile,
         CalendarState state
      ) {
         this.channel = channel;
         this.enabled = enabled;
         this.mode = mode;
         this.worldNames = worldNames;
         this.daysPerSeason = daysPerSeason;
         this.daysPerSeasonBySeason = daysPerSeasonBySeason;
         this.advanceOnSleep = advanceOnSleep;
         this.seasonDaylightMinutes = seasonDaylightMinutes;
         this.seasonNightMinutes = seasonNightMinutes;
         this.followWorldTime = followWorldTime;
         this.requirePlayersOnServer = requirePlayersOnServer;
         this.timeAnchorWorldName = timeAnchorWorldName;
         this.maxCatchupDays = maxCatchupDays;
         this.months = months;
         this.stateFile = stateFile;
         this.state = state;
         this.clampState();
      }

      private void registerTasks() {
         this.unregisterTasks();
         if (this.enabled) {
            if (this.hasCustomClock()) {
               this.clockStartMs = Long.MIN_VALUE;
               this.customClockPausedAtMs = Long.MIN_VALUE;
               this.clockPhaseKey = null;
               this.customClockWorldDays.clear();
               this.refreshWorldCache();
               long initialDelay = this.channel == CalendarChannel.NETHER ? 3L : 1L;
               this.customClockTask = Bukkit.getScheduler().runTaskTimer(SeasonService.this.plugin, this::tickCustomClock, initialDelay, 5L);
            } else {
               if (this.followWorldTime) {
                  this.lastWorldDayIdx = Long.MIN_VALUE;
                  this.worldClockTask = Bukkit.getScheduler().runTaskTimer(SeasonService.this.plugin, this::tickWorldClock, 40L, 10L);
               }
            }
         }
      }

      private void unregisterTasks() {
         if (this.worldClockTask != null) {
            this.worldClockTask.cancel();
         }

         if (this.customClockTask != null) {
            this.customClockTask.cancel();
         }

         this.worldClockTask = null;
         this.customClockTask = null;
         this.clockStartMs = Long.MIN_VALUE;
         this.customClockPausedAtMs = Long.MIN_VALUE;
         this.clockPhaseKey = null;
         this.customClockWorldDays.clear();
         this.cachedManualTimeWorlds = List.of();
         this.cachedAnchorWorld = null;
         this.worldCacheDirty = true;
      }

      private int getEffectiveDaylightMinutes() {
         if (this.mode == CalendarMode.MONTHS) {
            CalendarMonthDefinition def = this.getCurrentMonth();
            return def != null ? def.getDaylightMinutes() : 0;
         } else {
            return this.seasonDaylightMinutes;
         }
      }

      private int getEffectiveNightMinutes() {
         if (this.mode == CalendarMode.MONTHS) {
            CalendarMonthDefinition def = this.getCurrentMonth();
            return def != null ? def.getNightMinutes() : 0;
         } else {
            return this.seasonNightMinutes;
         }
      }

      private boolean hasCustomClock() {
         return this.getEffectiveDaylightMinutes() > 0 && this.getEffectiveNightMinutes() > 0;
      }

      private int getDaysPerSeasonFor(Season season) {
         Integer v = this.daysPerSeasonBySeason.get(season);
         return v != null ? v : this.daysPerSeason;
      }

      private int getCurrentPeriodLength() {
         if (this.mode == CalendarMode.MONTHS) {
            CalendarMonthDefinition def = this.getCurrentMonth();
            return def != null ? def.getDays() : 1;
         } else {
            return this.getDaysPerSeasonFor(this.state.season);
         }
      }

      private CalendarMonthDefinition getCurrentMonth() {
         if (this.mode == CalendarMode.MONTHS && !this.months.isEmpty()) {
            int idx = this.state.monthIndex;
            if (idx < 0 || idx >= this.months.size()) {
               idx = SeasonService.this.findMonthIndex(this.months, this.state.monthId);
            }

            if (idx < 0 || idx >= this.months.size()) {
               idx = 0;
            }

            return this.months.get(idx);
         } else {
            return null;
         }
      }

      private String getCurrentClockPhaseKey() {
         return this.mode == CalendarMode.MONTHS && this.getCurrentMonth() != null
            ? "MONTH_" + this.getCurrentMonth().getId()
            : "SEASON_" + this.state.season.name();
      }

      private long normalizeWorldTime(long time) {
         long t = time % 24000L;
         if (t < 0L) {
            t += 24000L;
         }

         return t;
      }

      private long worldTimeToElapsedMs(long worldTime, int daylightMinutes, int nightMinutes) {
         long t = this.normalizeWorldTime(worldTime);
         long dayMs = daylightMinutes * 60000L;
         long nightMs = nightMinutes * 60000L;
         if (t < 12000L) {
            return Math.round(t / 12000.0 * dayMs);
         }

         long nightTicks = t - 12000L;
         return dayMs + Math.round(nightTicks / 12000.0 * nightMs);
      }

      private long elapsedMsToWorldTime(long elapsedMs, int daylightMinutes, int nightMinutes) {
         long dayMs = daylightMinutes * 60000L;
         long nightMs = nightMinutes * 60000L;
         if (elapsedMs < dayMs) {
            return Math.max(0L, Math.min(11999L, Math.round((double)elapsedMs / dayMs * 12000.0)));
         }

         long nightElapsed = elapsedMs - dayMs;
         return 12000L + Math.max(0L, Math.min(11999L, Math.round((double)nightElapsed / nightMs * 12000.0)));
      }

      private void markWorldCacheDirty() {
         this.worldCacheDirty = true;
      }

      private void evictCachedWorld(World world) {
         if (world != null) {
            UUID worldId = world.getUID();
            this.cachedManualTimeWorlds = this.cachedManualTimeWorlds.stream().filter(cached -> !cached.getUID().equals(worldId)).toList();
            if (this.cachedAnchorWorld != null && this.cachedAnchorWorld.getUID().equals(worldId)) {
               this.cachedAnchorWorld = null;
            }

            this.customClockWorldDays.remove(worldId);
            this.worldCacheDirty = true;
         }
      }

      private void ensureWorldCache() {
         if (this.worldCacheDirty) {
            this.refreshWorldCache();
         }
      }

      private void refreshWorldCache() {
         List<World> channelWorlds = new ArrayList<>();
         List<World> manualTimeWorlds = new ArrayList<>();

         for (World world : Bukkit.getWorlds()) {
            if (SeasonService.this.resolveChannel(world) == this.channel) {
               channelWorlds.add(world);
               if (WorldCompat.supportsManualTime(world)) {
                  manualTimeWorlds.add(world);
               }
            }
         }

         World anchor = Bukkit.getWorld(this.timeAnchorWorldName);
         if (anchor == null && !channelWorlds.isEmpty()) {
            anchor = channelWorlds.get(0);
         }

         if (anchor == null) {
            anchor = Bukkit.getWorld(this.channel == CalendarChannel.NETHER ? "aeternum_heat" : "world");
         }

         this.cachedManualTimeWorlds = List.copyOf(manualTimeWorlds);
         this.cachedAnchorWorld = anchor;
         this.worldCacheDirty = false;
      }

      private void applyWorldTimeToChannelWorlds(long worldTime) {
         this.applyWorldTimeToChannelWorlds(worldTime, false);
      }

      private void applyWorldTimeToChannelWorlds(long worldTime, boolean advanceDay) {
         long t = this.normalizeWorldTime(worldTime);
         this.ensureWorldCache();

         for (World w : this.cachedManualTimeWorlds) {
            if (WorldCompat.isTimeAdvancementEnabled(w)) {
               UUID worldId = w.getUID();
               long currentFullTime = w.getFullTime();
               long currentRelativeTime = Math.floorMod(currentFullTime, 24000L);
               long currentWorldDay = Math.floorDiv(currentFullTime, 24000L);
               long targetWorldDay = this.customClockWorldDays.computeIfAbsent(worldId, ignored -> currentWorldDay);
               if (advanceDay) {
                  this.customClockWorldDays.put(worldId, ++targetWorldDay);
               }

               long targetFullTime = targetWorldDay * 24000L + t;
               if (targetFullTime != currentFullTime) {
                  if (!advanceDay && targetWorldDay == currentWorldDay && t > currentRelativeTime) {
                     WorldCompat.trySetTime(w, t, SeasonService.this.plugin);
                  } else {
                     WorldCompat.trySetFullTime(w, targetFullTime, SeasonService.this.plugin);
                  }
               }
            }
         }
      }

      private void realignCustomClockToCurrentWorldTime() {
         if (!this.hasCustomClock()) {
            this.clockStartMs = Long.MIN_VALUE;
            this.clockPhaseKey = null;
         } else {
            World anchor = this.getAnchorWorld();
            if (anchor != null) {
               int daylight = this.getEffectiveDaylightMinutes();
               int night = this.getEffectiveNightMinutes();
               if (daylight > 0 && night > 0) {
                  long elapsedMs = this.worldTimeToElapsedMs(anchor.getTime(), daylight, night);
                  this.clockStartMs = System.currentTimeMillis() - elapsedMs;
                  this.clockPhaseKey = this.getCurrentClockPhaseKey();
               }
            }
         }
      }

      private void resetCustomClockToDawn() {
         if (!this.hasCustomClock()) {
            this.clockStartMs = Long.MIN_VALUE;
            this.clockPhaseKey = null;
         } else {
            this.clockStartMs = System.currentTimeMillis();
            this.clockPhaseKey = this.getCurrentClockPhaseKey();
            this.applyWorldTimeToChannelWorlds(0L, true);
         }
      }

      private void setWorldTimeToChannelWorlds(long worldTime) {
         long t = this.normalizeWorldTime(worldTime);
         this.ensureWorldCache();

         for (World w : this.cachedManualTimeWorlds) {
            WorldCompat.trySetTime(w, t, SeasonService.this.plugin);
         }
      }

      private void realignCustomClockToManualWorldTime(long requestedWorldTime) {
         if (!this.hasCustomClock()) {
            this.clockStartMs = Long.MIN_VALUE;
            this.clockPhaseKey = null;
         } else {
            int daylight = this.getEffectiveDaylightMinutes();
            int night = this.getEffectiveNightMinutes();
            if (daylight > 0 && night > 0) {
               long target = this.normalizeWorldTime(requestedWorldTime);
               long elapsedMs = this.worldTimeToElapsedMs(target, daylight, night);
               this.clockStartMs = System.currentTimeMillis() - elapsedMs;
               this.clockPhaseKey = this.getCurrentClockPhaseKey();
            }
         }
      }

      private synchronized void applyManualCommand(long requestedWorldTime, int daysToAdvance) {
         long target = this.normalizeWorldTime(requestedWorldTime);
         long now = System.currentTimeMillis();
         int days = Math.max(0, daysToAdvance);
         if (now - this.lastManualCommandSyncMs >= 250L || target != this.lastManualCommandTarget || days != this.lastManualCommandDaysAdvanced) {
            this.lastManualCommandSyncMs = now;
            this.lastManualCommandTarget = target;
            this.lastManualCommandDaysAdvanced = days;
            if (days <= 0) {
               this.handleManualTimeSet(target);
            } else {
               for (int i = 0; i < days; i++) {
                  this.handleManualSetToDay(target);
               }
            }
         }
      }

      private synchronized void handleManualSetToDay(long requestedWorldTime) {
         long target = this.normalizeWorldTime(requestedWorldTime);
         long now = System.currentTimeMillis();
         this.advance(this.state);
         if (this.hasCustomClock()) {
            int daylight = this.getEffectiveDaylightMinutes();
            int night = this.getEffectiveNightMinutes();
            if (daylight > 0 && night > 0) {
               long elapsedMs = this.worldTimeToElapsedMs(target, daylight, night);
               this.clockStartMs = now - elapsedMs;
               this.clockPhaseKey = this.getCurrentClockPhaseKey();
               this.applyWorldTimeToChannelWorlds(target, true);
            }
         } else {
            this.setWorldTimeToChannelWorlds(target);
         }

         this.lastDayAdvanceMs = now;
         this.persistNow();
         Bukkit.getPluginManager().callEvent(new SeasonUpdateEvent(SeasonService.this, this.channel, this.state.copy(), true));
      }

      private synchronized void handleManualTimeSet(long requestedWorldTime) {
         long target = this.normalizeWorldTime(requestedWorldTime);
         if (this.hasCustomClock()) {
            this.realignCustomClockToManualWorldTime(target);
            this.applyWorldTimeToChannelWorlds(target);
         } else {
            this.setWorldTimeToChannelWorlds(target);
         }
      }

      private void tickCustomClock() {
         int daylight = this.getEffectiveDaylightMinutes();
         int night = this.getEffectiveNightMinutes();
         if (daylight > 0 && night > 0) {
            if (!this.requirePlayersOnServer || SeasonService.this.hasAnyOnlinePlayer()) {
               long now = System.currentTimeMillis();
               World anchor = this.getAnchorWorld();
               if (anchor != null) {
                  if (!WorldCompat.isTimeAdvancementEnabled(anchor)) {
                     if (this.customClockPausedAtMs == Long.MIN_VALUE) {
                        this.customClockPausedAtMs = now;
                     }
                  } else {
                     if (this.customClockPausedAtMs != Long.MIN_VALUE) {
                        if (this.clockStartMs != Long.MIN_VALUE) {
                           this.clockStartMs = this.clockStartMs + (now - this.customClockPausedAtMs);
                        }

                        this.customClockPausedAtMs = Long.MIN_VALUE;
                     }

                     String phaseKey = this.getCurrentClockPhaseKey();
                     if (this.clockStartMs == Long.MIN_VALUE || !Objects.equals(this.clockPhaseKey, phaseKey)) {
                        long elapsedMs = this.worldTimeToElapsedMs(anchor.getTime(), daylight, night);
                        this.clockStartMs = now - elapsedMs;
                        this.clockPhaseKey = phaseKey;
                     }

                     long totalMs = ((long)daylight + night) * 60000L;

                     long elapsedMs;
                     for (elapsedMs = now - this.clockStartMs; elapsedMs >= totalMs; this.clockStartMs = now - elapsedMs) {
                        elapsedMs -= totalMs;
                        this.nextDay(true);
                        daylight = this.getEffectiveDaylightMinutes();
                        night = this.getEffectiveNightMinutes();
                        if (daylight <= 0 || night <= 0) {
                           return;
                        }

                        totalMs = ((long)daylight + night) * 60000L;
                        this.clockPhaseKey = this.getCurrentClockPhaseKey();
                     }

                     long targetWorldTime = this.elapsedMsToWorldTime(elapsedMs, daylight, night);
                     this.applyWorldTimeToChannelWorlds(targetWorldTime);
                  }
               }
            }
         }
      }

      private CalendarState peekTomorrow() {
         CalendarState copy = this.state.copy();
         this.advance(copy);
         return copy;
      }

      private void tickWorldClock() {
         World anchor = this.getAnchorWorld();
         if (anchor != null) {
            long idx = anchor.getFullTime() / 24000L;
            if (this.lastWorldDayIdx == Long.MIN_VALUE) {
               this.lastWorldDayIdx = idx;
            } else if (idx < this.lastWorldDayIdx) {
               this.lastWorldDayIdx = idx;
            } else {
               long daysElapsed = idx - this.lastWorldDayIdx;
               if (daysElapsed != 0L) {
                  if (this.requirePlayersOnServer && !SeasonService.this.hasAnyOnlinePlayer()) {
                     this.lastWorldDayIdx = idx;
                  } else if (this.maxCatchupDays > 0 && daysElapsed > this.maxCatchupDays) {
                     this.lastWorldDayIdx = idx;
                  } else {
                     this.lastWorldDayIdx = idx;

                     for (int i = 0; i < daysElapsed; i++) {
                        this.nextDay(true);
                     }
                  }
               }
            }
         }
      }

      private World getAnchorWorld() {
         this.ensureWorldCache();
         return this.cachedAnchorWorld;
      }

      private synchronized void nextDay(boolean bypassDebounce) {
         long now = System.currentTimeMillis();
         if (!bypassDebounce) {
            if (now - this.lastDayAdvanceMs < 500L) {
               return;
            }

            this.lastDayAdvanceMs = now;
         }

         this.advance(this.state);
         this.resetCustomClockToDawn();
         this.persistNow();
         Bukkit.getPluginManager().callEvent(new SeasonUpdateEvent(SeasonService.this, this.channel, this.state.copy(), true));
      }

      private synchronized void setSeason(Season season) {
         if (this.mode == CalendarMode.MONTHS) {
            for (int i = 0; i < this.months.size(); i++) {
               CalendarMonthDefinition def = this.months.get(i);
               if (def.getSeason() == season) {
                  this.state.season = def.getSeason();
                  this.state.monthId = def.getId();
                  this.state.monthDisplayName = def.getDisplayName();
                  this.state.monthIndex = i;
                  this.state.daysInMonth = def.getDays();
                  if (this.state.day > def.getDays()) {
                     this.state.day = def.getDays();
                  }

                  if (this.state.day < 1) {
                     this.state.day = 1;
                  }

                  this.realignCustomClockToCurrentWorldTime();
                  this.persistNow();
                  Bukkit.getPluginManager().callEvent(new SeasonUpdateEvent(SeasonService.this, this.channel, this.state.copy(), false));
                  return;
               }
            }
         }

         this.state.season = season;
         this.clampState();
         this.realignCustomClockToCurrentWorldTime();
         this.persistNow();
         Bukkit.getPluginManager().callEvent(new SeasonUpdateEvent(SeasonService.this, this.channel, this.state.copy(), false));
      }

      private synchronized void setDay(int day) {
         int max = this.getCurrentPeriodLength();
         if (day < 1) {
            day = 1;
         }

         if (day > max) {
            day = max;
         }

         this.state.day = day;
         this.realignCustomClockToCurrentWorldTime();
         this.persistNow();
         Bukkit.getPluginManager().callEvent(new SeasonUpdateEvent(SeasonService.this, this.channel, this.state.copy(), false));
      }

      private synchronized void setYear(int year) {
         if (year < 1) {
            year = 1;
         }

         this.state.year = year;
         this.realignCustomClockToCurrentWorldTime();
         this.persistNow();
         Bukkit.getPluginManager().callEvent(new SeasonUpdateEvent(SeasonService.this, this.channel, this.state.copy(), false));
      }

      private void clampState() {
         if (this.state.year < 1) {
            this.state.year = 1;
         }

         if (this.state.day < 1) {
            this.state.day = 1;
         }

         if (this.mode == CalendarMode.MONTHS && !this.months.isEmpty()) {
            int idx = this.state.monthIndex;
            if (idx < 0 || idx >= this.months.size()) {
               idx = SeasonService.this.findMonthIndex(this.months, this.state.monthId);
            }

            if (idx < 0 || idx >= this.months.size()) {
               idx = 0;
            }

            CalendarMonthDefinition def = this.months.get(idx);
            this.state.monthsEnabled = true;
            this.state.monthId = def.getId();
            this.state.monthDisplayName = def.getDisplayName();
            this.state.monthIndex = idx;
            this.state.daysInMonth = def.getDays();
            this.state.season = def.getSeason();
            if (this.state.day > def.getDays()) {
               this.state.day = def.getDays();
            }
         } else {
            this.state.monthsEnabled = false;
            this.state.monthId = null;
            this.state.monthDisplayName = null;
            this.state.monthIndex = -1;
            this.state.daysInMonth = 0;
            int max = this.getDaysPerSeasonFor(this.state.season);
            if (this.state.day > max) {
               this.state.day = max;
            }
         }
      }

      private void advance(CalendarState target) {
         if (this.mode == CalendarMode.MONTHS && !this.months.isEmpty()) {
            int idx = target.monthIndex;
            if (idx < 0 || idx >= this.months.size()) {
               idx = SeasonService.this.findMonthIndex(this.months, target.monthId);
            }

            if (idx < 0 || idx >= this.months.size()) {
               idx = 0;
            }

            CalendarMonthDefinition current = this.months.get(idx);
            target.monthsEnabled = true;
            target.monthId = current.getId();
            target.monthDisplayName = current.getDisplayName();
            target.monthIndex = idx;
            target.daysInMonth = current.getDays();
            target.season = current.getSeason();
            target.day++;
            if (target.day > current.getDays()) {
               if (++idx >= this.months.size()) {
                  idx = 0;
                  target.year++;
               }

               CalendarMonthDefinition next = this.months.get(idx);
               target.day = 1;
               target.season = next.getSeason();
               target.monthId = next.getId();
               target.monthDisplayName = next.getDisplayName();
               target.monthIndex = idx;
               target.daysInMonth = next.getDays();
            }
         } else {
            target.day++;
            int max = this.getDaysPerSeasonFor(target.season);
            if (target.day > max) {
               target.day = 1;
               target.season = this.nextSeason(target.season);
               if (target.season == Season.SPRING) {
                  target.year++;
               }
            }

            target.monthsEnabled = false;
            target.monthId = null;
            target.monthDisplayName = null;
            target.monthIndex = -1;
            target.daysInMonth = 0;
         }
      }

      private void persistNow() {
         try {
            if (!this.stateFile.getParentFile().exists()) {
               this.stateFile.getParentFile().mkdirs();
            }

            YamlConfiguration y = new YamlConfiguration();
            y.set("year", this.state.year);
            y.set("day", this.state.day);
            y.set("season", this.state.season.name());
            y.set("mode", this.mode.name());
            if (this.mode == CalendarMode.MONTHS) {
               y.set("month", this.state.monthId);
            }

            y.save(this.stateFile);
         } catch (IOException ex) {
            SeasonService.this.plugin.getLogger().warning("No se pudo guardar calendario de canal " + this.channel.name() + ": " + ex.getMessage());
         }
      }

      private Season nextSeason(Season s) {
         return switch (s) {
            case SPRING -> Season.SUMMER;
            case SUMMER -> Season.AUTUMN;
            case AUTUMN -> Season.WINTER;
            case WINTER -> Season.SPRING;
         };
      }
   }

   private static final class ManualTimeCommand {
      private final long targetWorldTime;
      private final int daysToAdvance;

      private ManualTimeCommand(long targetWorldTime, int daysToAdvance) {
         this.targetWorldTime = targetWorldTime;
         this.daysToAdvance = Math.max(0, daysToAdvance);
      }
   }

   private static final class SleepSnapshot {
      private final UUID worldId;
      private final long enteredAtMs;
      private final long enteredWorldTime;

      private SleepSnapshot(UUID worldId, long enteredAtMs, long enteredWorldTime) {
         this.worldId = worldId;
         this.enteredAtMs = enteredAtMs;
         this.enteredWorldTime = enteredWorldTime;
      }
   }
}
