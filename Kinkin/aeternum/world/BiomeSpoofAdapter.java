package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarChannel;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.calendar.SeasonService;
import Kinkin.aeternum.calendar.SeasonUpdateEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class BiomeSpoofAdapter implements Listener, Runnable {
   private final AeternumSeasonsPlugin plugin;
   private final SeasonService seasons;
   private final BiomePacketSender biomePacketSender;
   private BiomeSpoofAdapter.Mode mode;
   private int radiusChunksCfg;
   private int budgetPerTick;
   private static volatile BiomeSpoofAdapter INSTANCE;
   private boolean revertOnSeasonChange;
   private final EnumMap<Season, Biome> seasonTarget = new EnumMap<>(Season.class);
   private final Set<Biome> excludedBiomes = new HashSet<>();
   private final Set<Biome> protectedEcologyBiomes = new HashSet<>();
   private final Map<Long, Boolean> excludedCache = new ConcurrentHashMap<>();
   private boolean respectNaturallySnowyBiomes;
   private boolean protectWaterColorBiomes;
   private boolean oceansEnabled;
   private boolean oceansAffectShores;
   private boolean oceansKeepDeepVariants;
   private boolean oceansWinterForceSnow;
   private Biome oceansWinterForceSnowBiome = Biome.SNOWY_PLAINS;
   private final EnumMap<Season, Biome> oceanTarget = new EnumMap<>(Season.class);
   private boolean spawnGuardEnabled;
   private boolean spawnGuardOnlySpoofedChunks;
   private int spawnGuardNearWaterRadius;
   private static final EnumSet<EntityType> WATER_REQUIRED_MOBS = EnumSet.of(
      EntityType.COD,
      EntityType.SALMON,
      EntityType.PUFFERFISH,
      EntityType.TROPICAL_FISH,
      EntityType.SQUID,
      EntityType.GLOW_SQUID,
      EntityType.DOLPHIN,
      EntityType.GUARDIAN,
      EntityType.ELDER_GUARDIAN,
      EntityType.AXOLOTL,
      EntityType.TADPOLE
   );
   private static final EnumSet<EntityType> CAPPED_AQUATIC_MOBS = EnumSet.of(
      EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH, EntityType.TROPICAL_FISH, EntityType.DOLPHIN
   );
   private static final int AQUATIC_CAP_RADIUS_XZ = 32;
   private static final int AQUATIC_CAP_RADIUS_Y = 16;
   private static final int MAX_FISH_PER_AREA = 48;
   private static final int MAX_DOLPHINS_PER_AREA = 6;
   private final Map<Long, BiomeSpoofAdapter.AppliedBiomeState> lastApplied = new ConcurrentHashMap<>();
   private final Map<Long, BiomeSpoofAdapter.Family> familyCache = new ConcurrentHashMap<>();
   private BukkitTask task;
   private final Deque<BiomeSpoofAdapter.ChunkWork> pendingChunks = new ArrayDeque<>();
   private final Set<BiomeSpoofAdapter.ChunkWork> queuedChunks = new HashSet<>();
   private final Set<BiomeSpoofAdapter.ChunkWork> backupLookupsPending = new HashSet<>();
   private final Set<BiomeSpoofAdapter.ChunkWork> backupLookupsCompleted = new HashSet<>();
   private final Deque<BiomeSpoofAdapter.ChunkWork> clientRefreshQueue = new ArrayDeque<>();
   private final Set<BiomeSpoofAdapter.ChunkWork> queuedClientRefreshes = new HashSet<>();
   private final Map<UUID, BiomeSpoofAdapter.PlayerScanState> playerScanStates = new HashMap<>();
   private final Map<Integer, List<BiomeSpoofAdapter.Offset>> offsetsByRadius = new HashMap<>();
   private double chunkCredits;
   private int optimizedTickCounter;
   private int clientRefreshTickCounter;
   private double preTransitionRateMultiplier = 1.0;
   private Set<String> disabledWorldNames = Set.of();
   private int clientRefreshIntervalTicks = 1;
   private int clientRefreshChunksPerInterval = 12;
   private int clientFullRefreshChunksPerInterval = 2;
   private boolean clientUseBiomePackets = true;
   private boolean clientRefreshNudge;
   private static final long SPOOF_WORK_BUDGET_NANOS = 2000000L;
   private static final int LEGACY_SCHEDULE_PERIOD_TICKS = 10;
   private final Map<Long, Biome[]> backups = new ConcurrentHashMap<>();
   private final Map<Long, BitSet> modifiedBiomeCells = new ConcurrentHashMap<>();
   private final Set<Long> spoofed = ConcurrentHashMap.newKeySet();
   private static final Set<Long> COLD_CHUNKS = ConcurrentHashMap.newKeySet();
   private final Set<Long> warmChunks = new HashSet<>();
   private static final int NUDGES_PER_TICK = 8;
   private static final long NUDGE_COOLDOWN_MS = 3000L;
   private static final Material NUDGE_FAKE = Material.BARRIER;
   private static final int STEP_XZ = 4;
   private static final int STEP_Y = 4;
   private final Map<UUID, ArrayDeque<BiomeSpoofAdapter.NudgeTarget>> nudgeQueue = new HashMap<>();
   private final Map<BiomeSpoofAdapter.NudgeCooldownKey, Long> nudgeLast = new HashMap<>();
   private static final long TRANSITION_WINDOW_MS = 5000L;
   private static final int TRANSITION_BUDGET_MULTIPLIER = 3;
   private volatile long seasonTransitionUntil = 0L;
   private static final int PRE_TRANSITION_DAYS = 3;
   private boolean preTransitionEnabled;
   private boolean preTransitionAffectOceans;
   private final BiomeBackupStore diskBackups;
   private volatile boolean diskBackupEnabled = true;

   public static BiomeSpoofAdapter instanceOrNull() {
      return INSTANCE;
   }

   public BiomeSpoofAdapter(AeternumSeasonsPlugin plugin, SeasonService seasons) {
      this.plugin = plugin;
      this.seasons = seasons;
      this.biomePacketSender = new BiomePacketSender(plugin);
      this.diskBackups = new BiomeBackupStore(plugin);
      this.reloadFromConfig();
      INSTANCE = this;
   }

   private void reloadFromConfig() {
      boolean enabled = this.plugin.cfg.climate.getBoolean("biome_spoof.enabled", true);
      if (!enabled) {
         this.mode = BiomeSpoofAdapter.Mode.OFF;
      } else {
         String m = this.plugin.cfg.climate.getString("biome_spoof.mode", "GLOBAL_RING");
         if ("OFF".equalsIgnoreCase(m)) {
            this.mode = BiomeSpoofAdapter.Mode.OFF;
         } else {
            this.mode = BiomeSpoofAdapter.Mode.GLOBAL_RING;
         }

         this.radiusChunksCfg = Math.max(1, this.plugin.cfg.climate.getInt("biome_spoof.radius_chunks", 8));
         this.budgetPerTick = Math.max(2, this.plugin.cfg.climate.getInt("biome_spoof.budget_chunks_per_tick", 16));
         this.clientRefreshIntervalTicks = Math.max(1, this.plugin.cfg.climate.getInt("biome_spoof.client_refresh.interval_ticks", 1));
         this.clientRefreshChunksPerInterval = Math.max(1, Math.min(64, this.plugin.cfg.climate.getInt("biome_spoof.client_refresh.chunks_per_interval", 12)));
         this.clientFullRefreshChunksPerInterval = Math.max(
            1, Math.min(4, this.plugin.cfg.climate.getInt("biome_spoof.client_refresh.full_chunk_fallback_chunks_per_interval", 2))
         );
         this.clientUseBiomePackets = this.plugin.cfg.climate.getBoolean("biome_spoof.client_refresh.use_biome_packets", true);
         this.clientRefreshNudge = this.plugin.cfg.climate.getBoolean("biome_spoof.client_refresh.extra_nudge", false);
         this.revertOnSeasonChange = this.plugin.cfg.climate.getBoolean("biome_spoof.revert_on_non_winter", true);
         this.respectNaturallySnowyBiomes = this.plugin.cfg.climate.getBoolean("real_snow.melt.respect_naturally_snowy_biomes", true);
         this.protectWaterColorBiomes = this.plugin
            .cfg
            .climate
            .getBoolean("biome_spoof.oceans.protect_water_color_biomes", this.plugin.cfg.climate.getBoolean("biome_spoof.protect_water_color_biomes", true));
         this.preTransitionEnabled = this.plugin.cfg.climate.getBoolean("biome_spoof.pre_transition.enabled", true);
         this.preTransitionAffectOceans = this.plugin.cfg.climate.getBoolean("biome_spoof.pre_transition.affect_oceans", false);
         this.diskBackupEnabled = this.plugin.cfg.climate.getBoolean("biome_spoof.disk_backup.enabled", true);
         this.seasonTarget.put(Season.SPRING, this.readBiome("biome_spoof.seasons.SPRING", Biome.FLOWER_FOREST));
         this.seasonTarget.put(Season.SUMMER, this.readBiome("biome_spoof.seasons.SUMMER", Biome.PLAINS));
         this.seasonTarget.put(Season.AUTUMN, this.readBiome("biome_spoof.seasons.AUTUMN", Biome.WINDSWEPT_SAVANNA));
         this.seasonTarget.put(Season.WINTER, this.readBiome("biome_spoof.seasons.WINTER", Biome.SNOWY_PLAINS));
         this.oceansEnabled = this.plugin.cfg.climate.getBoolean("biome_spoof.oceans.enabled", true);
         this.oceansAffectShores = this.plugin.cfg.climate.getBoolean("biome_spoof.oceans.affect_shores", true);
         this.oceansKeepDeepVariants = this.plugin.cfg.climate.getBoolean("biome_spoof.oceans.keep_deep_variants", true);
         this.oceansWinterForceSnow = this.plugin.cfg.climate.getBoolean("biome_spoof.oceans.winter_force_snow", false);
         this.oceansWinterForceSnowBiome = this.readBiome("biome_spoof.oceans.winter_force_snow_biome", Biome.SNOWY_PLAINS);
         this.oceanTarget.put(Season.SPRING, this.readBiome("biome_spoof.oceans.seasons.SPRING", Biome.LUKEWARM_OCEAN));
         this.oceanTarget.put(Season.SUMMER, this.readBiome("biome_spoof.oceans.seasons.SUMMER", Biome.WARM_OCEAN));
         this.oceanTarget.put(Season.AUTUMN, this.readBiome("biome_spoof.oceans.seasons.AUTUMN", Biome.OCEAN));
         this.oceanTarget.put(Season.WINTER, this.readBiome("biome_spoof.oceans.seasons.WINTER", Biome.FROZEN_OCEAN));
         this.spawnGuardEnabled = this.plugin.cfg.climate.getBoolean("biome_spoof.spawn_guard.enabled", false);
         this.spawnGuardOnlySpoofedChunks = this.plugin.cfg.climate.getBoolean("biome_spoof.spawn_guard.only_spoofed_chunks", true);
         this.spawnGuardNearWaterRadius = Math.max(0, this.plugin.cfg.climate.getInt("biome_spoof.spawn_guard.near_water_radius", 2));
         this.excludedBiomes.clear();
         this.protectedEcologyBiomes.clear();
         this.readBiomeSet("biome_spoof.excluded_biomes", this.excludedBiomes);
         if (this.plugin.cfg.climate.getBoolean("biome_spoof.protected_ecology.enabled", true)) {
            this.readBiomeSet("biome_spoof.protected_ecology.biomes", this.protectedEcologyBiomes);
         }

         this.excludedCache.clear();
      }
   }

   private void readBiomeSet(String path, Set<Biome> destination) {
      for (String raw : this.plugin.cfg.climate.getStringList(path)) {
         if (raw != null && !raw.isBlank()) {
            try {
               destination.add(Biome.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
               this.plugin.getLogger().warning("[BiomeSpoof] Invalid biome '" + raw + "' at " + path + " (ignored)");
            }
         }
      }
   }

   private Biome readBiome(String path, Biome def) {
      String s = this.plugin.cfg.climate.getString(path, def.name());

      try {
         return Biome.valueOf(s.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
         this.plugin.getLogger().warning("[BiomeSpoof] Invalid biome '" + s + "' at " + path + ", using " + def);
         return def;
      }
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      if (this.task != null) {
         this.task.cancel();
      }

      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::runOptimized, 40L, 1L);
   }

   public void unregister() {
      if (this.task != null) {
         this.task.cancel();
      }

      HandlerList.unregisterAll(this);
      this.revertAll();
      this.nudgeQueue.clear();
      this.nudgeLast.clear();
      COLD_CHUNKS.clear();
      this.warmChunks.clear();
      this.lastApplied.clear();
      this.familyCache.clear();
      this.pendingChunks.clear();
      this.queuedChunks.clear();
      this.backupLookupsPending.clear();
      this.backupLookupsCompleted.clear();
      this.clientRefreshQueue.clear();
      this.queuedClientRefreshes.clear();
      this.clientRefreshTickCounter = 0;
      this.playerScanStates.clear();
      this.offsetsByRadius.clear();
      this.chunkCredits = 0.0;
      this.excludedCache.clear();
      if (INSTANCE == this) {
         INSTANCE = null;
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onCreatureSpawn(CreatureSpawnEvent e) {
      if (this.mode != BiomeSpoofAdapter.Mode.OFF) {
         if (this.spawnGuardEnabled) {
            SpawnReason reason = e.getSpawnReason();
            if (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN) {
               Location loc = e.getLocation();
               World w = loc.getWorld();
               if (w != null) {
                  if (this.spawnGuardOnlySpoofedChunks) {
                     long ck = this.key(w, loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                     if (!this.spoofed.contains(ck)) {
                        return;
                     }
                  }

                  EntityType type = e.getEntityType();
                  if (CAPPED_AQUATIC_MOBS.contains(type) && this.exceedsAquaticCap(loc, type)) {
                     e.setCancelled(true);
                  } else if (WATER_REQUIRED_MOBS.contains(type)) {
                     if (!this.isInWaterLikeBlock(loc)) {
                        e.setCancelled(true);
                     }
                  } else {
                     if (type == EntityType.DROWNED) {
                        if (this.isInWaterLikeBlock(loc)) {
                           return;
                        }

                        int r = this.spawnGuardNearWaterRadius;
                        if (r > 0 && this.hasWaterNearby(loc, r)) {
                           return;
                        }

                        e.setCancelled(true);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isInWaterLikeBlock(Location loc) {
      Block b = loc.getBlock();
      if (this.isWaterLike(b)) {
         return true;
      }

      Block below = b.getRelative(0, -1, 0);
      return this.isWaterLike(below);
   }

   private boolean isWaterLike(Block b) {
      Material t = b.getType();
      if (t != Material.WATER && t != Material.BUBBLE_COLUMN) {
         BlockData data = b.getBlockData();
         return data instanceof Waterlogged ? ((Waterlogged)data).isWaterlogged() : false;
      } else {
         return true;
      }
   }

   private boolean hasWaterNearby(Location loc, int radius) {
      World w = loc.getWorld();
      if (w == null) {
         return false;
      }

      int bx = loc.getBlockX();
      int by = loc.getBlockY();
      int bz = loc.getBlockZ();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx != 0 || dz != 0) {
               Block b = w.getBlockAt(bx + dx, by, bz + dz);
               if (this.isWaterLike(b)) {
                  return true;
               }

               Block below = w.getBlockAt(bx + dx, by - 1, bz + dz);
               if (this.isWaterLike(below)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean exceedsAquaticCap(Location loc, EntityType spawningType) {
      World w = loc.getWorld();
      if (w == null) {
         return false;
      }

      int max = this.maxAquaticAllowed(spawningType);
      if (max <= 0) {
         return false;
      }

      int count = 0;

      for (Entity entity : w.getNearbyEntities(loc, 32.0, 16.0, 32.0)) {
         if (entity.getType() == spawningType) {
            if (++count >= max) {
               return true;
            }
         }
      }

      return false;
   }

   private int maxAquaticAllowed(EntityType type) {
      return type == EntityType.DOLPHIN ? 6 : 48;
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent e) {
      BiomeSpoofAdapter.ChunkWork work = new BiomeSpoofAdapter.ChunkWork(e.getChunk().getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ());
      this.backupLookupsPending.remove(work);
      this.backupLookupsCompleted.remove(work);
      long k = this.key(e.getChunk());
      this.spoofed.remove(k);
      this.modifiedBiomeCells.remove(k);
      this.lastApplied.remove(k);
      this.familyCache.remove(k);
      this.warmChunks.remove(k);
      COLD_CHUNKS.remove(k);
      this.excludedCache.remove(k);
      if (this.mode != BiomeSpoofAdapter.Mode.OFF && this.isInsideAnyActivePlayerArea(e.getChunk())) {
         this.enqueueChunk(e.getChunk().getWorld(), e.getChunk().getX(), e.getChunk().getZ());
      }
   }

   @EventHandler
   public void onChunkUnload(ChunkUnloadEvent e) {
      Chunk ch = e.getChunk();
      long k = this.key(ch);
      BiomeSpoofAdapter.ChunkWork work = new BiomeSpoofAdapter.ChunkWork(ch.getWorld().getUID(), ch.getX(), ch.getZ());
      this.queuedChunks.remove(work);
      this.queuedClientRefreshes.remove(work);
      this.backupLookupsPending.remove(work);
      this.backupLookupsCompleted.remove(work);
      if (this.spoofed.remove(k)) {
         this.revertChunk(ch, false);
      }

      this.backups.remove(k);
      this.modifiedBiomeCells.remove(k);
      this.warmChunks.remove(k);
      COLD_CHUNKS.remove(k);
      BiomeSpoofAdapter.NudgeTarget unloaded = new BiomeSpoofAdapter.NudgeTarget(ch.getWorld().getUID(), ch.getX(), ch.getZ());

      for (ArrayDeque<BiomeSpoofAdapter.NudgeTarget> queue : this.nudgeQueue.values()) {
         queue.remove(unloaded);
      }

      this.excludedCache.remove(k);
   }

   @EventHandler
   public void onSeasonChange(SeasonUpdateEvent e) {
      if (this.mode != BiomeSpoofAdapter.Mode.OFF) {
         this.seasonTransitionUntil = System.currentTimeMillis() + 5000L;
         this.playerScanStates.clear();
         this.clientRefreshQueue.clear();
         this.queuedClientRefreshes.clear();
         this.clientRefreshTickCounter = 0;
         if (this.revertOnSeasonChange) {
         }
      }
   }

   private void runOptimized() {
      if (this.mode != BiomeSpoofAdapter.Mode.OFF) {
         this.optimizedTickCounter++;
         if (this.optimizedTickCounter >= 10) {
            this.optimizedTickCounter = 0;
            this.enqueuePlayerAreas();
            if (this.clientRefreshNudge) {
               this.flushNudges();
            }
         }

         double multiplier = System.currentTimeMillis() < this.seasonTransitionUntil ? 3.0 : this.preTransitionRateMultiplier;
         double ratePerTick = Math.max(1.0, this.budgetPerTick * multiplier);
         double creditCap = Math.max(1.0, ratePerTick * 2.0);
         this.chunkCredits = Math.min(creditCap, this.chunkCredits + ratePerTick);
         int allowance = (int)Math.floor(this.chunkCredits);
         if (allowance > 0 && !this.pendingChunks.isEmpty()) {
            long deadline = System.nanoTime() + 2000000L;
            int processed = 0;

            while (processed < allowance && !this.pendingChunks.isEmpty()) {
               BiomeSpoofAdapter.ChunkWork work = this.pendingChunks.pollFirst();
               if (this.queuedChunks.remove(work)) {
                  this.processQueuedChunk(work);
                  this.chunkCredits = Math.max(0.0, this.chunkCredits - 1.0);
                  processed++;
                  if (System.nanoTime() >= deadline) {
                     break;
                  }
               }
            }
         }

         this.flushClientRefreshQueue();
      }
   }

   private void enqueuePlayerAreas() {
      long cooldownCutoff = System.currentTimeMillis() - 3000L;
      this.nudgeLast.entrySet().removeIf(entry -> entry.getValue() < cooldownCutoff);
      Set<String> disabled = new HashSet<>();

      for (String worldName : this.plugin.getConfig().getStringList("worlds.disabled_season_fx")) {
         if (worldName != null) {
            disabled.add(worldName.toLowerCase(Locale.ROOT));
         }
      }

      this.disabledWorldNames = Set.copyOf(disabled);
      Set<UUID> online = new HashSet<>();
      double maxPreTransition = 0.0;
      int radius = Math.max(this.radiusChunksCfg, Bukkit.getViewDistance() + 1);

      for (Player player : Bukkit.getOnlinePlayers()) {
         UUID playerId = player.getUniqueId();
         online.add(playerId);
         World world = player.getWorld();
         if (world.getEnvironment() == Environment.NORMAL && !this.isWorldDisabled(world)) {
            CalendarChannel channel = this.seasons.resolveChannel(world);
            if (channel != null && this.seasons.isChannelEnabled(channel)) {
               boolean permanentWinter = this.seasons.isPermanentWinterWorld(world);
               CalendarState state = this.seasons.getStateCopy(world);
               int dayInPeriod = this.computeDayInPeriod(channel, state);
               double preFactor = this.preTransitionEnabled && !permanentWinter ? this.computePreTransitionFactor(channel, state, dayInPeriod) : 0.0;
               maxPreTransition = Math.max(maxPreTransition, preFactor);
               Location location = player.getLocation();
               int centerX = location.getBlockX() >> 4;
               int centerZ = location.getBlockZ() >> 4;
               long visualStamp = this.visualStamp(state);
               BiomeSpoofAdapter.PlayerScanState previous = this.playerScanStates.get(playerId);
               BiomeSpoofAdapter.PlayerScanState current = new BiomeSpoofAdapter.PlayerScanState(world.getUID(), centerX, centerZ, radius, visualStamp);
               if (!current.equals(previous)) {
                  boolean enqueueOnlyNewRing = previous != null
                     && previous.worldId().equals(current.worldId())
                     && previous.radius() == current.radius()
                     && previous.visualStamp() == current.visualStamp();

                  for (BiomeSpoofAdapter.Offset offset : this.offsetsForRadius(radius)) {
                     int chunkX = centerX + offset.dx;
                     int chunkZ = centerZ + offset.dz;
                     if (!enqueueOnlyNewRing
                        || Math.abs(chunkX - previous.chunkX()) > previous.radius()
                        || Math.abs(chunkZ - previous.chunkZ()) > previous.radius()) {
                        this.enqueueChunk(world, chunkX, chunkZ);
                     }
                  }

                  this.playerScanStates.put(playerId, current);
               }
            } else {
               this.playerScanStates.remove(playerId);
            }
         } else {
            this.playerScanStates.remove(playerId);
         }
      }

      this.playerScanStates.keySet().retainAll(online);
      this.preTransitionRateMultiplier = 1.0 + maxPreTransition * 2.0;
   }

   private List<BiomeSpoofAdapter.Offset> offsetsForRadius(int radius) {
      return this.offsetsByRadius
         .computeIfAbsent(
            radius,
            value -> {
               List<BiomeSpoofAdapter.Offset> offsets = new ArrayList<>((value * 2 + 1) * (value * 2 + 1));

               for (int dx = -value; dx <= value; dx++) {
                  for (int dz = -value; dz <= value; dz++) {
                     int distance = Math.max(Math.abs(dx), Math.abs(dz));
                     offsets.add(new BiomeSpoofAdapter.Offset(dx, dz, distance, 0.0));
                  }
               }

               offsets.sort(
                  Comparator.<BiomeSpoofAdapter.Offset>comparingInt(offset -> offset.dist)
                     .thenComparingInt(offset -> offset.dx * offset.dx + offset.dz * offset.dz)
               );
               return List.copyOf(offsets);
            }
         );
   }

   private long visualStamp(CalendarState state) {
      long stamp = (long)state.year << 32 ^ (long)state.day << 16;
      stamp ^= (long)state.season.ordinal() << 8;
      if (state.monthsEnabled) {
         stamp ^= state.monthIndex + 1L;
      }

      return stamp;
   }

   private void enqueueChunk(World world, int chunkX, int chunkZ) {
      if (world.isChunkLoaded(chunkX, chunkZ)) {
         BiomeSpoofAdapter.ChunkWork work = new BiomeSpoofAdapter.ChunkWork(world.getUID(), chunkX, chunkZ);
         if (this.queuedChunks.add(work)) {
            this.pendingChunks.addLast(work);
         }
      }
   }

   private void enqueueClientRefresh(World world, int chunkX, int chunkZ) {
      BiomeSpoofAdapter.ChunkWork work = new BiomeSpoofAdapter.ChunkWork(world.getUID(), chunkX, chunkZ);
      if (this.queuedClientRefreshes.add(work)) {
         this.clientRefreshQueue.addLast(work);
      }
   }

   private void flushClientRefreshQueue() {
      this.clientRefreshTickCounter++;
      if (this.clientRefreshTickCounter >= this.clientRefreshIntervalTicks) {
         this.clientRefreshTickCounter = 0;
         boolean useBiomePackets = this.clientUseBiomePackets && this.biomePacketSender.isSupported();
         int allowance = useBiomePackets ? this.clientRefreshChunksPerInterval : this.clientFullRefreshChunksPerInterval;
         List<BiomeSpoofAdapter.ChunkWork> batch = new ArrayList<>(allowance);
         int examined = 0;

         while (batch.size() < allowance && examined++ < Math.max(64, allowance * 4) && !this.clientRefreshQueue.isEmpty()) {
            BiomeSpoofAdapter.ChunkWork work = this.clientRefreshQueue.pollFirst();
            if (this.queuedClientRefreshes.contains(work)) {
               World world = Bukkit.getWorld(work.worldId());
               if (world != null && world.isChunkLoaded(work.chunkX(), work.chunkZ()) && this.hasClientViewer(world, work.chunkX(), work.chunkZ())) {
                  batch.add(work);
               } else {
                  this.queuedClientRefreshes.remove(work);
               }
            }
         }

         if (!batch.isEmpty()) {
            if (useBiomePackets && this.sendBiomePacketBatch(batch)) {
               this.queuedClientRefreshes.removeAll(batch);
            } else {
               int refreshed = 0;
               int firstUnsent = batch.size();

               for (int i = 0; i < batch.size(); i++) {
                  BiomeSpoofAdapter.ChunkWork work = batch.get(i);
                  if (refreshed >= this.clientFullRefreshChunksPerInterval) {
                     firstUnsent = i;
                     break;
                  }

                  World world = Bukkit.getWorld(work.worldId());
                  if (world != null && world.isChunkLoaded(work.chunkX(), work.chunkZ())) {
                     world.refreshChunk(work.chunkX(), work.chunkZ());
                     if (this.clientRefreshNudge) {
                        this.nudgeViewers(world, work.chunkX(), work.chunkZ());
                     }

                     refreshed++;
                  }

                  this.queuedClientRefreshes.remove(work);
               }

               for (int i = batch.size() - 1; i >= firstUnsent; i--) {
                  this.clientRefreshQueue.addFirst(batch.get(i));
               }
            }
         }
      }
   }

   private boolean sendBiomePacketBatch(List<BiomeSpoofAdapter.ChunkWork> batch) {
      Map<UUID, List<Chunk>> chunksByWorld = new LinkedHashMap<>();

      for (BiomeSpoofAdapter.ChunkWork work : batch) {
         World world = Bukkit.getWorld(work.worldId());
         if (world != null && world.isChunkLoaded(work.chunkX(), work.chunkZ())) {
            chunksByWorld.computeIfAbsent(work.worldId(), ignored -> new ArrayList<>()).add(world.getChunkAt(work.chunkX(), work.chunkZ()));
         }
      }

      for (Entry<UUID, List<Chunk>> entry : chunksByWorld.entrySet()) {
         World world = Bukkit.getWorld(entry.getKey());
         if (world != null && !this.biomePacketSender.send(world, entry.getValue())) {
            return false;
         }
      }

      return true;
   }

   private boolean hasClientViewer(World world, int chunkX, int chunkZ) {
      int view = Bukkit.getViewDistance() + 1;

      for (Player player : world.getPlayers()) {
         Location location = player.getLocation();
         int playerChunkX = location.getBlockX() >> 4;
         int playerChunkZ = location.getBlockZ() >> 4;
         if (Math.abs(playerChunkX - chunkX) <= view && Math.abs(playerChunkZ - chunkZ) <= view) {
            return true;
         }
      }

      return false;
   }

   private boolean isInsideAnyActivePlayerArea(Chunk chunk) {
      int radius = Math.max(this.radiusChunksCfg, Bukkit.getViewDistance() + 1);

      for (Player player : chunk.getWorld().getPlayers()) {
         Location location = player.getLocation();
         int centerX = location.getBlockX() >> 4;
         int centerZ = location.getBlockZ() >> 4;
         if (Math.abs(chunk.getX() - centerX) <= radius && Math.abs(chunk.getZ() - centerZ) <= radius) {
            return true;
         }
      }

      return false;
   }

   private boolean isWorldDisabled(World world) {
      return this.disabledWorldNames.contains(world.getName().toLowerCase(Locale.ROOT));
   }

   private void processQueuedChunk(BiomeSpoofAdapter.ChunkWork work) {
      World world = Bukkit.getWorld(work.worldId());
      if (world != null && world.getEnvironment() == Environment.NORMAL && !this.isWorldDisabled(world) && world.isChunkLoaded(work.chunkX(), work.chunkZ())) {
         CalendarChannel channel = this.seasons.resolveChannel(world);
         if (channel != null && this.seasons.isChannelEnabled(channel)) {
            boolean permanentWinter = this.seasons.isPermanentWinterWorld(world);
            CalendarState state = this.seasons.getStateCopy(world);
            Season season = state.season;
            int dayInPeriod = this.computeDayInPeriod(channel, state);
            double preTransitionFactor = this.preTransitionEnabled && !permanentWinter ? this.computePreTransitionFactor(channel, state, dayInPeriod) : 0.0;
            Season nextSeason = permanentWinter ? Season.WINTER : this.resolveNextVisualSeason(channel, state);
            Biome currentTarget = this.seasonTarget.getOrDefault(season, Biome.PLAINS);
            Biome nextTarget = this.seasonTarget.getOrDefault(nextSeason, currentTarget);
            Biome currentOceanTarget = this.oceanTarget.getOrDefault(season, Biome.OCEAN);
            Biome nextOceanTarget = this.oceanTarget.getOrDefault(nextSeason, currentOceanTarget);
            Chunk chunk = world.getChunkAt(work.chunkX(), work.chunkZ());
            long chunkKey = this.key(chunk);
            if (this.ensureOriginalBackupLoaded(work, world, chunkKey)) {
               if (this.isChunkExcludedByConfig(chunk)) {
                  if (this.spoofed.remove(chunkKey)) {
                     this.revertChunk(chunk, false);
                     this.enqueueClientRefresh(world, work.chunkX(), work.chunkZ());
                  }

                  this.lastApplied.remove(chunkKey);
                  this.familyCache.remove(chunkKey);
               } else {
                  BiomeSpoofAdapter.Family family = this.familyCache.computeIfAbsent(chunkKey, ignored -> this.classifyOriginalFamily(chunk));
                  Biome target = this.chooseTargetBiomeForChunk(
                     chunkKey, family, currentTarget, nextTarget, currentOceanTarget, nextOceanTarget, preTransitionFactor, chunk, season, nextSeason
                  );
                  if (!this.shouldSkipSpoofForChunk(chunk, season)) {
                     BiomeSpoofAdapter.AppliedBiomeState last = this.lastApplied.get(chunkKey);
                     if (last == null || !last.matches(target, season)) {
                        BiomeSpoofAdapter.BiomeApplyResult result = this.captureAndApply(chunk, target, season);
                        if (!result.successful()) {
                           this.lastApplied.remove(chunkKey);
                        } else {
                           if (result.original() != null && !this.backups.containsKey(chunkKey)) {
                              this.backups.put(chunkKey, result.original());
                           }

                           this.rememberModifiedCells(chunkKey, result);
                           this.spoofed.add(chunkKey);
                           this.lastApplied.put(chunkKey, new BiomeSpoofAdapter.AppliedBiomeState(target, season));
                           if (result.changed()) {
                              this.enqueueClientRefresh(world, work.chunkX(), work.chunkZ());
                           }
                        }
                     }
                  } else {
                     BiomeSpoofAdapter.AppliedBiomeState last = this.lastApplied.get(chunkKey);
                     boolean alreadyValidated = last != null && last.matches(target, season);
                     boolean wasSpoofed = this.spoofed.remove(chunkKey);
                     if (!alreadyValidated) {
                        boolean restored = this.revertChunk(chunk, false);
                        this.lastApplied.put(chunkKey, new BiomeSpoofAdapter.AppliedBiomeState(target, season));
                        if (restored || wasSpoofed) {
                           this.enqueueClientRefresh(world, work.chunkX(), work.chunkZ());
                        }
                     } else if (wasSpoofed) {
                        this.enqueueClientRefresh(world, work.chunkX(), work.chunkZ());
                     }

                     this.familyCache.remove(chunkKey);
                  }
               }
            }
         }
      }
   }

   private boolean ensureOriginalBackupLoaded(BiomeSpoofAdapter.ChunkWork work, World world, long chunkKey) {
      if (this.backups.containsKey(chunkKey) || this.backupLookupsCompleted.contains(work)) {
         return true;
      }

      if (!this.backupLookupsPending.add(work)) {
         return false;
      }

      this.diskBackups.loadOriginalGridAsync(world, work.chunkX(), work.chunkZ(), 4, 4, original -> {
         if (this.backupLookupsPending.remove(work)) {
            World loadedWorld = Bukkit.getWorld(work.worldId());
            if (loadedWorld != null && loadedWorld.isChunkLoaded(work.chunkX(), work.chunkZ())) {
               if (original != null && original.length > 0) {
                  this.backups.putIfAbsent(chunkKey, original);
               }

               this.backupLookupsCompleted.add(work);
               this.enqueueChunk(loadedWorld, work.chunkX(), work.chunkZ());
            }
         }
      });
      return false;
   }

   @Override
   public void run() {
      this.runOptimized();
   }

   private void runLegacy() {
      if (this.mode != BiomeSpoofAdapter.Mode.OFF) {
         long now = System.currentTimeMillis();
         int baseBudget = this.budgetPerTick;
         int budget = baseBudget;
         if (budget > 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
               if (budget <= 0) {
                  break;
               }

               World w = p.getWorld();
               if (w.getEnvironment() == Environment.NORMAL) {
                  List<String> disabled = this.plugin.getConfig().getStringList("worlds.disabled_season_fx");
                  if (disabled == null || !disabled.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(w.getName()))) {
                     CalendarChannel channel = this.seasons.resolveChannel(w);
                     if (channel != null && this.seasons.isChannelEnabled(channel)) {
                        boolean permanentWinter = this.seasons.isPermanentWinterWorld(w);
                        CalendarState st = this.seasons.getStateCopy(w);
                        Season season = st.season;
                        int dayInPeriod = this.computeDayInPeriod(channel, st);
                        double preTransitionFactor = this.preTransitionEnabled && !permanentWinter
                           ? this.computePreTransitionFactor(channel, st, dayInPeriod)
                           : 0.0;
                        Season nextVisualSeason = permanentWinter ? Season.WINTER : this.resolveNextVisualSeason(channel, st);
                        Biome currentTarget = this.seasonTarget.getOrDefault(season, Biome.PLAINS);
                        Biome nextTarget = this.seasonTarget.getOrDefault(nextVisualSeason, currentTarget);
                        Biome currentOceanTarget = this.oceanTarget.getOrDefault(season, Biome.OCEAN);
                        Biome nextOceanTarget = this.oceanTarget.getOrDefault(nextVisualSeason, currentOceanTarget);
                        int effectiveBudget = baseBudget;
                        if (now < this.seasonTransitionUntil) {
                           effectiveBudget = Math.max(1, baseBudget * 3);
                        } else if (preTransitionFactor > 0.0) {
                           double extra = 1.0 + preTransitionFactor * 2.0;
                           effectiveBudget = (int)Math.max(1L, Math.round(baseBudget * extra));
                        }

                        budget = Math.min(budget, effectiveBudget);
                        int view = Bukkit.getViewDistance();
                        int radius = Math.max(this.radiusChunksCfg, view + 1);
                        Location loc = p.getLocation();
                        int pcx = loc.getBlockX() >> 4;
                        int pcz = loc.getBlockZ() >> 4;
                        Vector look = loc.getDirection().clone();
                        look.setY(0);
                        if (look.lengthSquared() < 1.0E-4) {
                           look = new Vector(0, 0, 1);
                        } else {
                           look.normalize();
                        }

                        if (w.isChunkLoaded(pcx, pcz) && budget > 0) {
                           Chunk center = w.getChunkAt(pcx, pcz);
                           long ck = this.key(center);
                           boolean centerExcluded = this.isChunkExcludedByConfig(center);
                           if (centerExcluded) {
                              if (this.spoofed.remove(ck)) {
                                 this.revertChunk(center);
                              }

                              this.lastApplied.remove(ck);
                              this.familyCache.remove(ck);
                           } else {
                              BiomeSpoofAdapter.Family fam = this.familyCache.computeIfAbsent(ck, kk -> this.classifyOriginalFamily(center));
                              Biome chunkTarget = this.chooseTargetBiomeForChunk(
                                 ck, fam, currentTarget, nextTarget, currentOceanTarget, nextOceanTarget, preTransitionFactor, center, season, nextVisualSeason
                              );
                              if (this.shouldSkipSpoofForChunk(center, season)) {
                                 BiomeSpoofAdapter.AppliedBiomeState last = this.lastApplied.get(ck);
                                 boolean alreadyValidated = last != null && last.matches(chunkTarget, season);
                                 this.spoofed.remove(ck);
                                 if (!alreadyValidated) {
                                    this.revertChunk(center);
                                    this.lastApplied.put(ck, new BiomeSpoofAdapter.AppliedBiomeState(chunkTarget, season));
                                 }

                                 this.familyCache.remove(ck);
                              } else {
                                 BiomeSpoofAdapter.AppliedBiomeState last = this.lastApplied.get(ck);
                                 if (last == null || !last.matches(chunkTarget, season)) {
                                    if (last == null && this.isChunkAtTarget(center, chunkTarget)) {
                                       this.lastApplied.put(ck, new BiomeSpoofAdapter.AppliedBiomeState(chunkTarget, season));
                                    } else {
                                       BiomeSpoofAdapter.BiomeApplyResult apply = this.captureAndApply(center, chunkTarget, season);
                                       if (!apply.successful()) {
                                          this.lastApplied.remove(ck);
                                          continue;
                                       }

                                       if (apply.original() != null && !this.backups.containsKey(ck)) {
                                          this.backups.put(ck, apply.original());
                                       }

                                       this.rememberModifiedCells(ck, apply);
                                       this.spoofed.add(ck);
                                       this.lastApplied.put(ck, new BiomeSpoofAdapter.AppliedBiomeState(chunkTarget, season));
                                       this.nudgeViewers(w, pcx, pcz);
                                       budget--;
                                    }
                                 }
                              }
                           }
                        }

                        if (budget <= 0) {
                           break;
                        }

                        List<BiomeSpoofAdapter.Offset> offsets = new ArrayList<>();

                        for (int dx = -radius; dx <= radius; dx++) {
                           for (int dz = -radius; dz <= radius; dz++) {
                              if (dx != 0 || dz != 0) {
                                 int dist = Math.max(Math.abs(dx), Math.abs(dz));
                                 Vector dir = new Vector(dx, 0, dz);
                                 double forwardScore;
                                 if (dir.lengthSquared() < 1.0E-4) {
                                    forwardScore = 0.0;
                                 } else {
                                    dir.normalize();
                                    forwardScore = look.dot(dir);
                                 }

                                 offsets.add(new BiomeSpoofAdapter.Offset(dx, dz, dist, forwardScore));
                              }
                           }
                        }

                        offsets.sort(Comparator.<BiomeSpoofAdapter.Offset>comparingInt(o -> o.dist).thenComparingDouble(o -> -o.forwardScore));

                        for (BiomeSpoofAdapter.Offset off : offsets) {
                           if (budget <= 0) {
                              break;
                           }

                           int cx = pcx + off.dx;
                           int cz = pcz + off.dz;
                           if (w.isChunkLoaded(cx, cz)) {
                              Chunk ch = w.getChunkAt(cx, cz);
                              long k = this.key(ch);
                              if (this.isChunkExcludedByConfig(ch)) {
                                 if (this.spoofed.remove(k)) {
                                    this.revertChunk(ch);
                                 }

                                 this.lastApplied.remove(k);
                                 this.familyCache.remove(k);
                              } else {
                                 BiomeSpoofAdapter.Family fam = this.familyCache.computeIfAbsent(k, kk -> this.classifyOriginalFamily(ch));
                                 Biome chunkTarget = this.chooseTargetBiomeForChunk(
                                    k, fam, currentTarget, nextTarget, currentOceanTarget, nextOceanTarget, preTransitionFactor, ch, season, nextVisualSeason
                                 );
                                 if (!this.shouldSkipSpoofForChunk(ch, season)) {
                                    BiomeSpoofAdapter.AppliedBiomeState last = this.lastApplied.get(k);
                                    if (last == null || !last.matches(chunkTarget, season)) {
                                       if (last == null && this.isChunkAtTarget(ch, chunkTarget)) {
                                          this.lastApplied.put(k, new BiomeSpoofAdapter.AppliedBiomeState(chunkTarget, season));
                                       } else {
                                          BiomeSpoofAdapter.BiomeApplyResult apply = this.captureAndApply(ch, chunkTarget, season);
                                          if (!apply.successful()) {
                                             this.lastApplied.remove(k);
                                          } else {
                                             if (apply.original() != null && !this.backups.containsKey(k)) {
                                                this.backups.put(k, apply.original());
                                             }

                                             this.rememberModifiedCells(k, apply);
                                             this.spoofed.add(k);
                                             this.lastApplied.put(k, new BiomeSpoofAdapter.AppliedBiomeState(chunkTarget, season));
                                             this.nudgeViewers(w, cx, cz);
                                             budget--;
                                          }
                                       }
                                    }
                                 } else {
                                    BiomeSpoofAdapter.AppliedBiomeState last = this.lastApplied.get(k);
                                    boolean alreadyValidated = last != null && last.matches(chunkTarget, season);
                                    this.spoofed.remove(k);
                                    if (!alreadyValidated) {
                                       this.revertChunk(ch);
                                       this.lastApplied.put(k, new BiomeSpoofAdapter.AppliedBiomeState(chunkTarget, season));
                                    }

                                    this.familyCache.remove(k);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            this.flushNudges();
         }
      }
   }

   private boolean isChunkExcludedByConfig(Chunk ch) {
      if (this.excludedBiomes.isEmpty()) {
         return false;
      }

      long k = this.key(ch);
      Boolean cached = this.excludedCache.get(k);
      if (cached != null) {
         return cached;
      }

      boolean result = true;
      boolean foundBiome = false;
      Biome[] old = this.backups.get(k);
      if (old != null && old.length > 0) {
         for (Biome b : old) {
            if (b != null) {
               foundBiome = true;
               if (!this.shouldProtectBiomeFromSpoof(b)) {
                  result = false;
                  break;
               }
            }
         }

         result = foundBiome && result;
         this.excludedCache.put(k, result);
         return result;
      } else {
         World w = ch.getWorld();
         int bx = ch.getX() << 4;
         int bz = ch.getZ() << 4;
         int minY = w.getMinHeight();
         int maxY = w.getMaxHeight();

         for (int x = 0; x < 16; x += 8) {
            for (int z = 0; z < 16; z += 8) {
               for (int y = minY; y < maxY; y += 32) {
                  Biome b = w.getBiome(bx + x, y, bz + z);
                  if (b != null) {
                     foundBiome = true;
                     if (!this.shouldProtectBiomeFromSpoof(b)) {
                        result = false;
                     }
                  }
               }
            }
         }

         result = foundBiome && result;
         this.excludedCache.put(k, result);
         return result;
      }
   }

   private boolean shouldProtectBiomeFromSpoof(Biome biome) {
      return biome == null ? false : this.excludedBiomes.contains(biome) || this.protectedEcologyBiomes.contains(biome);
   }

   private boolean isRiverBiome(Biome biome) {
      if (biome == null) {
         return false;
      }

      String name = biome.name();
      return name.equals("RIVER") || name.equals("FROZEN_RIVER");
   }

   private boolean isWaterColorSensitiveBiome(Biome biome) {
      String name = biome.name();
      return name.equals("SWAMP") || name.equals("MANGROVE_SWAMP") || name.equals("RIVER") || name.equals("FROZEN_RIVER");
   }

   private Biome getRepresentativeOriginalOceanBiome(Chunk ch) {
      long k = this.key(ch);
      Biome[] old = this.backups.get(k);
      if (old != null && old.length > 0) {
         for (Biome b : old) {
            if (this.isOceanBiome(b)) {
               return b;
            }
         }

         return old[0];
      } else {
         return this.getRepresentativeOriginalBiome(ch);
      }
   }

   private Biome chooseTargetFor(Season season, Biome original) {
      Biome global = this.seasonTarget.get(season);
      return global == null ? original : global;
   }

   private boolean isChunkAtTarget(Chunk ch, Biome target) {
      World w = ch.getWorld();
      int bx = ch.getX() << 4;
      int bz = ch.getZ() << 4;
      int minY = w.getMinHeight();
      int maxY = w.getMaxHeight();

      for (int x = 0; x < 16; x += 8) {
         for (int z = 0; z < 16; z += 8) {
            for (int y = minY; y < maxY; y += 32) {
               if (w.getBiome(bx + x, y, bz + z) != target) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private boolean isColdBiome(Biome biome) {
      if (biome == Biome.CHERRY_GROVE) {
         return false;
      }

      String name = biome.name();
      return name.contains("SNOW")
         || name.contains("FROZEN")
         || name.contains("ICE")
         || name.equals("GROVE")
         || name.contains("SNOWY_TAIGA")
         || name.contains("PEAK")
         || name.contains("MOUNTAIN");
   }

   private boolean isOceanBiome(Biome b) {
      return b.name().contains("OCEAN");
   }

   private boolean isDeepOcean(Biome b) {
      return b.name().contains("DEEP_") && this.isOceanBiome(b);
   }

   private boolean isShoreBiome(Biome b) {
      String n = b.name();
      return n.contains("BEACH") || n.contains("SHORE");
   }

   private BiomeSpoofAdapter.Family classifyOriginalFamily(Chunk ch) {
      long k = this.key(ch);
      Biome[] old = this.backups.get(k);
      if (old != null && old.length > 0) {
         this.cacheOriginalTemperature(k, Arrays.asList(old));
         return this.classifyFamilyFromSamples(Arrays.asList(old));
      }

      World w = ch.getWorld();
      int bx = ch.getX() << 4;
      int bz = ch.getZ() << 4;
      int minY = w.getMinHeight();
      int maxY = w.getMaxHeight();
      List<Biome> samples = new ArrayList<>(32);

      for (int x = 0; x < 16; x += 8) {
         for (int z = 0; z < 16; z += 8) {
            for (int y = minY; y < maxY; y += 32) {
               samples.add(w.getBiome(bx + x, y, bz + z));
            }
         }
      }

      this.cacheOriginalTemperature(k, samples);
      return this.classifyFamilyFromSamples(samples);
   }

   private void cacheOriginalTemperature(long chunkKey, Iterable<Biome> samples) {
      for (Biome biome : samples) {
         if (this.isColdBiome(biome)) {
            COLD_CHUNKS.add(chunkKey);
            this.warmChunks.remove(chunkKey);
            return;
         }
      }

      this.warmChunks.add(chunkKey);
   }

   private BiomeSpoofAdapter.Family classifyFamilyFromSamples(Iterable<Biome> samples) {
      int oceanCount = 0;
      int shoreCount = 0;
      int landCount = 0;

      for (Biome b : samples) {
         if (b != null) {
            if (this.oceansEnabled && this.isOceanBiome(b)) {
               oceanCount++;
            } else if (this.oceansEnabled && this.oceansAffectShores && this.isShoreBiome(b)) {
               shoreCount++;
            } else {
               landCount++;
            }
         }
      }

      int oceanish = oceanCount + shoreCount;
      if (this.oceansEnabled && oceanish > 0) {
         if (oceanCount > 0 && oceanish > landCount) {
            return BiomeSpoofAdapter.Family.OCEAN;
         }

         if (oceanCount >= 2 && oceanish >= landCount) {
            return BiomeSpoofAdapter.Family.OCEAN;
         }
      }

      return BiomeSpoofAdapter.Family.LAND;
   }

   private Biome getRepresentativeOriginalBiome(Chunk ch) {
      long k = this.key(ch);
      Biome[] old = this.backups.get(k);
      if (old != null && old.length > 0) {
         return old[0];
      }

      World w = ch.getWorld();
      int bx = ch.getX() << 4;
      int bz = ch.getZ() << 4;
      int minY = w.getMinHeight();
      int maxY = w.getMaxHeight();

      for (int x = 0; x < 16; x += 8) {
         for (int z = 0; z < 16; z += 8) {
            int y = minY;
            if (y < maxY) {
               return w.getBiome(bx + x, y, bz + z);
            }
         }
      }

      return Biome.OCEAN;
   }

   private Biome applyOceanVariant(Biome baseTarget, Biome originalOcean) {
      if (!this.oceansKeepDeepVariants) {
         return baseTarget;
      }

      boolean origDeep = this.isDeepOcean(originalOcean);
      String baseName = baseTarget.name();
      if (origDeep) {
         if (baseName.startsWith("DEEP_")) {
            return baseTarget;
         }

         try {
            return Biome.valueOf("DEEP_" + baseName);
         } catch (IllegalArgumentException ignored) {
            return baseTarget;
         }
      } else if (baseName.startsWith("DEEP_")) {
         String shallow = baseName.substring("DEEP_".length());

         try {
            return Biome.valueOf(shallow);
         } catch (IllegalArgumentException ignored) {
            return baseTarget;
         }
      } else {
         return baseTarget;
      }
   }

   private Biome chooseTargetBiomeForChunk(
      long chunkKey,
      BiomeSpoofAdapter.Family family,
      Biome currentLandTarget,
      Biome nextLandTarget,
      Biome currentOceanTarget,
      Biome nextOceanTarget,
      double preTransitionFactor,
      Chunk ch,
      Season currentSeason,
      Season nextSeason
   ) {
      if (family == BiomeSpoofAdapter.Family.OCEAN && this.oceansEnabled) {
         Biome origOcean = this.getRepresentativeOriginalOceanBiome(ch);
         if (currentSeason == Season.WINTER && this.oceansWinterForceSnow) {
            Biome safeWinterTarget = this.isOceanBiome(this.oceansWinterForceSnowBiome)
               ? this.oceansWinterForceSnowBiome
               : this.oceanTarget.getOrDefault(Season.WINTER, Biome.FROZEN_OCEAN);
            return this.isOceanBiome(origOcean) ? this.applyOceanVariant(safeWinterTarget, origOcean) : safeWinterTarget;
         } else {
            double oceanPreTransitionFactor = this.preTransitionAffectOceans ? preTransitionFactor : 0.0;
            Biome base = this.chooseTargetBiomeForChunk(chunkKey, currentOceanTarget, nextOceanTarget, oceanPreTransitionFactor);
            return this.isOceanBiome(origOcean) ? this.applyOceanVariant(base, origOcean) : base;
         }
      } else {
         Biome orig = this.getRepresentativeOriginalBiome(ch);
         Biome landCur = currentLandTarget;
         Biome landNext = nextLandTarget;
         if (landCur == orig && landNext == orig) {
            return orig;
         } else {
            return landCur == landNext ? landCur : this.chooseTargetBiomeForChunk(chunkKey, landCur, landNext, preTransitionFactor);
         }
      }
   }

   private BiomeSpoofAdapter.BiomeApplyResult captureAndApply(Chunk ch, Biome target, Season season) {
      try {
         World w = ch.getWorld();
         int bx = ch.getX() << 4;
         int bz = ch.getZ() << 4;
         int minY = w.getMinHeight();
         int maxY = w.getMaxHeight();
         int seaLevel = w.getSeaLevel();
         long k = this.key(ch);
         Biome[] existing = this.backups.get(k);
         List<Biome> prevs = existing == null ? new ArrayList<>() : null;
         boolean anyChange = false;
         BitSet modifiedCells = new BitSet();
         int sampleIndex = 0;

         for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
               int wx = bx + x;
               int wz = bz + z;
               boolean waterColumn = this.protectWaterColorBiomes && this.isWaterSurfaceColumn(w, wx, wz);

               for (int y = minY; y < maxY; y += 4) {
                  int wy = y;
                  if (prevs == null) {
                     if (sampleIndex >= existing.length) {
                        break;
                     }

                     int gridIndex = sampleIndex;
                     Biome original = existing[sampleIndex++];
                     Biome desired = this.resolveDesiredBiome(original, target, season, waterColumn, wy, seaLevel);
                     if (desired != original) {
                        modifiedCells.set(gridIndex);
                     }

                     Biome current = w.getBiome(wx, wy, wz);
                     if (current != desired) {
                        w.setBiome(wx, wy, wz, desired);
                        anyChange = true;
                     }
                  } else {
                     Biome current = w.getBiome(wx, wy, wz);
                     prevs.add(current);
                     int gridIndex = sampleIndex++;
                     Biome desired = this.resolveDesiredBiome(current, target, season, waterColumn, wy, seaLevel);
                     if (desired != current) {
                        modifiedCells.set(gridIndex);
                     }

                     if (current != desired) {
                        anyChange = true;
                        w.setBiome(wx, wy, wz, desired);
                     }
                  }
               }
            }
         }

         if (prevs != null) {
            boolean cold = false;
            boolean fullyExcluded = true;
            boolean foundBiome = false;

            for (Biome b : prevs) {
               if (b != null) {
                  foundBiome = true;
                  if (this.isColdBiome(b)) {
                     cold = true;
                  }

                  if (!this.shouldProtectBiomeFromSpoof(b)) {
                     fullyExcluded = false;
                  }
               }
            }

            if (cold) {
               COLD_CHUNKS.add(k);
               this.warmChunks.remove(k);
            } else {
               this.warmChunks.add(k);
            }

            this.excludedCache.put(k, foundBiome && fullyExcluded);
            this.familyCache.put(k, this.classifyFamilyFromSamples(prevs));
         }

         if (prevs != null) {
            Biome[] arr = prevs.toArray(new Biome[0]);
            if (this.diskBackupEnabled) {
               this.diskBackups.saveFirstTouch(ch, arr, 4, 4);
            }

            return new BiomeSpoofAdapter.BiomeApplyResult(arr, modifiedCells, anyChange, true);
         } else {
            return new BiomeSpoofAdapter.BiomeApplyResult(null, modifiedCells, anyChange, true);
         }
      } catch (Throwable t) {
         this.plugin.getLogger().warning("[BiomeSpoof] spoof error " + ch.getX() + "," + ch.getZ() + ": " + t.getMessage());
         return new BiomeSpoofAdapter.BiomeApplyResult(null, null, false, false);
      }
   }

   private void rememberModifiedCells(long chunkKey, BiomeSpoofAdapter.BiomeApplyResult result) {
      BitSet modifiedCells = result.modifiedCells();
      if (modifiedCells != null) {
         this.modifiedBiomeCells.put(chunkKey, (BitSet)modifiedCells.clone());
      }
   }

   private boolean shouldPreserveWaterColor(Biome original, boolean waterColumn) {
      return this.protectWaterColorBiomes && waterColumn && this.isWaterColorSensitiveBiome(original);
   }

   private Biome resolveDesiredBiome(Biome original, Biome chunkTarget, Season season, boolean waterColumn, int sampleY, int seaLevel) {
      if (original == null) {
         return chunkTarget;
      }

      if (this.isRiverBiome(original)) {
         return original;
      }

      if (this.shouldProtectBiomeFromSpoof(original)) {
         return original;
      }

      if (season == Season.WINTER) {
         if (this.isShoreBiome(original)) {
            return Biome.SNOWY_BEACH;
         }

         if (this.isOceanBiome(original)) {
            return sampleY >= seaLevel ? Biome.SNOWY_PLAINS : Biome.FROZEN_OCEAN;
         }
      }

      return this.shouldPreserveWaterColor(original, waterColumn) ? original : chunkTarget;
   }

   private boolean isWaterSurfaceColumn(World world, int x, int z) {
      int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      int minY = world.getMinHeight();

      for (int depth = 0; depth < 4 && surfaceY - depth >= minY; depth++) {
         Block block = world.getBlockAt(x, surfaceY - depth, z);
         Material type = block.getType();
         if (this.isWaterLike(block)
            || type == Material.ICE
            || type == Material.FROSTED_ICE
            || type == Material.KELP
            || type == Material.KELP_PLANT
            || type == Material.SEAGRASS
            || type == Material.TALL_SEAGRASS) {
            return true;
         }

         if (type.isSolid() && type != Material.LILY_PAD) {
            return false;
         }
      }

      return false;
   }

   private void revertChunk(Chunk ch) {
      this.revertChunk(ch, true);
   }

   private boolean revertChunk(Chunk ch, boolean refreshClientImmediately) {
      long k = this.key(ch);
      Biome[] old = this.backups.get(k);
      if (old == null) {
         this.modifiedBiomeCells.remove(k);
         this.lastApplied.remove(k);
         this.familyCache.remove(k);
         return false;
      }

      BitSet modifiedCells = this.modifiedBiomeCells.get(k);
      boolean restoreAll = modifiedCells == null;
      boolean changed = false;
      boolean completed = false;

      try {
         World w = ch.getWorld();
         int bx = ch.getX() << 4;
         int bz = ch.getZ() << 4;
         int minY = w.getMinHeight();
         int maxY = w.getMaxHeight();
         int i = 0;

         label65:
         for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
               for (int y = minY; y < maxY; y += 4) {
                  if (i >= old.length) {
                     break label65;
                  }

                  Biome original = old[i];
                  if (restoreAll || modifiedCells.get(i)) {
                     w.setBiome(bx + x, y, bz + z, original);
                     changed = true;
                  }

                  i++;
               }
            }
         }

         completed = true;
         if (changed && refreshClientImmediately) {
            w.refreshChunk(ch.getX(), ch.getZ());
            if (this.clientRefreshNudge) {
               this.nudgeViewers(w, ch.getX(), ch.getZ());
            }
         }
      } catch (Throwable t) {
         this.plugin.getLogger().warning("[BiomeSpoof] revert error " + ch.getX() + "," + ch.getZ() + ": " + t.getMessage());
      }

      if (completed) {
         this.modifiedBiomeCells.remove(k);
      }

      this.lastApplied.remove(k);
      this.familyCache.remove(k);
      return changed;
   }

   private long key(Chunk ch) {
      return this.key(ch.getWorld(), ch.getX(), ch.getZ());
   }

   private long key(World w, int cx, int cz) {
      long k = (cx & 4294967295L) << 32 | cz & 4294967295L;
      return k ^ w.getUID().getMostSignificantBits() ^ w.getUID().getLeastSignificantBits();
   }

   public static boolean isChunkNaturallySnowy(World w, int cx, int cz) {
      long k = (cx & 4294967295L) << 32 | cz & 4294967295L;
      k ^= w.getUID().getMostSignificantBits() ^ w.getUID().getLeastSignificantBits();
      return COLD_CHUNKS.contains(k);
   }

   private void revertAll() {
      for (World w : Bukkit.getWorlds()) {
         for (Chunk ch : w.getLoadedChunks()) {
            long k = this.key(ch);
            if (this.spoofed.contains(k)) {
               this.revertChunk(ch);
            }
         }
      }

      this.spoofed.clear();
      this.backups.clear();
      this.modifiedBiomeCells.clear();
      this.nudgeQueue.clear();
      this.nudgeLast.clear();
      this.lastApplied.clear();
      this.familyCache.clear();
      this.excludedCache.clear();
   }

   private boolean shouldSkipSpoofForChunk(Chunk ch, Season currentSeason) {
      if (!this.respectNaturallySnowyBiomes) {
         return false;
      }

      if (currentSeason == Season.WINTER) {
         return false;
      }

      long k = this.key(ch);
      if (COLD_CHUNKS.contains(k)) {
         return true;
      }

      if (this.warmChunks.contains(k)) {
         return false;
      }

      if (this.backups.containsKey(k)) {
         return false;
      }

      World w = ch.getWorld();
      int bx = ch.getX() << 4;
      int bz = ch.getZ() << 4;
      int minY = w.getMinHeight();
      int maxY = w.getMaxHeight();

      for (int x = 0; x < 16; x += 4) {
         for (int z = 0; z < 16; z += 4) {
            for (int y = minY; y < maxY; y += 32) {
               Biome b = w.getBiome(bx + x, y, bz + z);
               if (this.isColdBiome(b)) {
                  COLD_CHUNKS.add(k);
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void nudgeViewers(World w, int cx, int cz) {
      for (Player viewer : w.getPlayers()) {
         int vcx = viewer.getLocation().getBlockX() >> 4;
         int vcz = viewer.getLocation().getBlockZ() >> 4;
         int view = Bukkit.getViewDistance() + 1;
         if (Math.abs(vcx - cx) <= view && Math.abs(vcz - cz) <= view) {
            this.enqueueNudge(viewer, w, cx, cz);
         }
      }
   }

   private void enqueueNudge(Player p, World w, int cx, int cz) {
      BiomeSpoofAdapter.NudgeTarget target = new BiomeSpoofAdapter.NudgeTarget(w.getUID(), cx, cz);
      BiomeSpoofAdapter.NudgeCooldownKey cooldownKey = new BiomeSpoofAdapter.NudgeCooldownKey(p.getUniqueId(), target);
      long now = System.currentTimeMillis();
      Long last = this.nudgeLast.get(cooldownKey);
      if (last == null || now - last >= 3000L) {
         this.nudgeLast.put(cooldownKey, now);
         this.nudgeQueue.computeIfAbsent(p.getUniqueId(), id -> new ArrayDeque<>()).add(target);
      }
   }

   private void flushNudges() {
      int sent = 0;
      Iterator<Entry<UUID, ArrayDeque<BiomeSpoofAdapter.NudgeTarget>>> it = this.nudgeQueue.entrySet().iterator();

      while (it.hasNext() && sent < 8) {
         Entry<UUID, ArrayDeque<BiomeSpoofAdapter.NudgeTarget>> e = it.next();
         UUID pid = e.getKey();
         ArrayDeque<BiomeSpoofAdapter.NudgeTarget> q = e.getValue();
         Player p = Bukkit.getPlayer(pid);
         if (p == null || !p.isOnline()) {
            it.remove();
         } else if (q.isEmpty()) {
            it.remove();
         } else {
            BiomeSpoofAdapter.NudgeTarget target = q.poll();
            sent++;
            World w = Bukkit.getWorld(target.worldId());
            if (w != null && p.getWorld() == w && w.isChunkLoaded(target.chunkX(), target.chunkZ())) {
               int minY = w.getMinHeight();
               Location loc = new Location(w, target.chunkX() << 4, minY, target.chunkZ() << 4);
               BlockData fake = NUDGE_FAKE.createBlockData();
               BlockData real = w.getBlockAt(loc).getBlockData();
               p.sendBlockChange(loc, fake);
               Bukkit.getScheduler().runTask(this.plugin, () -> {
                  if (p.isOnline()) {
                     p.sendBlockChange(loc, real);
                  }
               });
            }
         }
      }
   }

   private int computeDayInPeriod(CalendarChannel channel, CalendarState st) {
      int d = st != null ? st.day : 1;
      int max;
      if (st != null && st.monthsEnabled && st.daysInMonth > 0) {
         max = st.daysInMonth;
      } else if (channel != null) {
         max = Math.max(1, this.seasons.getCurrentPeriodLength(channel));
      } else {
         max = 1;
      }

      if (d < 1) {
         d = 1;
      }

      if (d > max) {
         d = max;
      }

      return d;
   }

   private double computePreTransitionFactor(CalendarChannel channel, CalendarState st, int dayInPeriod) {
      int periodLength;
      if (st != null && st.monthsEnabled && st.daysInMonth > 0) {
         periodLength = st.daysInMonth;
      } else if (channel != null) {
         periodLength = Math.max(1, this.seasons.getCurrentPeriodLength(channel));
      } else {
         periodLength = 1;
      }

      int window = Math.min(3, periodLength);
      int start = periodLength - window + 1;
      if (dayInPeriod < start) {
         return 0.0;
      }

      int step = dayInPeriod - start;
      double factor = (double)(step + 1) / window;
      if (factor < 0.0) {
         factor = 0.0;
      }

      if (factor > 1.0) {
         factor = 1.0;
      }

      return factor;
   }

   private Season nextSeason(Season s) {
      return switch (s) {
         case SPRING -> Season.SUMMER;
         case SUMMER -> Season.AUTUMN;
         case AUTUMN -> Season.WINTER;
         case WINTER -> Season.SPRING;
      };
   }

   public Biome getOriginalBiomeApprox(World w, int x, int y, int z) {
      Chunk ch = w.getChunkAt(x >> 4, z >> 4);
      Biome[] old = this.backups.get(this.key(ch));
      if (old != null && old.length != 0) {
         int minY = w.getMinHeight();
         int maxY = w.getMaxHeight();
         int yy = Math.max(minY, Math.min(maxY - 1, y));
         int xCount = 4;
         int zCount = 4;
         int yCount = (maxY - minY) / 4;
         int lx = (x & 15) / 4;
         int lz = (z & 15) / 4;
         int ly = (yy - minY) / 4;
         int idx = (lx * zCount + lz) * yCount + ly;
         return idx >= 0 && idx < old.length ? old[idx] : old[0];
      } else {
         return w.getBiome(x, y, z);
      }
   }

   public Biome getOriginalBiomeApproxOrNull(World w, int x, int y, int z) {
      Chunk ch = w.getChunkAt(x >> 4, z >> 4);
      Biome[] old = this.backups.get(this.key(ch));
      if (old != null && old.length != 0) {
         int minY = w.getMinHeight();
         int maxY = w.getMaxHeight();
         int yy = Math.max(minY, Math.min(maxY - 1, y));
         int xCount = 4;
         int zCount = 4;
         int yCount = (maxY - minY) / 4;
         int lx = (x & 15) / 4;
         int lz = (z & 15) / 4;
         int ly = (yy - minY) / 4;
         int idx = (lx * zCount + lz) * yCount + ly;
         return idx >= 0 && idx < old.length ? old[idx] : old[0];
      } else {
         return null;
      }
   }

   private Biome chooseTargetBiomeForChunk(long chunkKey, Biome currentTarget, Biome nextTarget, double preTransitionFactor) {
      if (nextTarget == null || currentTarget == nextTarget || preTransitionFactor <= 0.0) {
         return currentTarget;
      }

      if (preTransitionFactor >= 1.0) {
         return nextTarget;
      }

      long h = chunkKey * 1103515245L + 12345L;
      h ^= h >>> 16;
      int bucket = (int)(h & 65535L);
      double threshold = preTransitionFactor * 65536.0;
      return bucket < threshold ? nextTarget : currentTarget;
   }

   public synchronized void setEnabled(boolean enabled) {
      if (enabled) {
         this.mode = BiomeSpoofAdapter.Mode.GLOBAL_RING;
         if (this.task == null || this.task.isCancelled()) {
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::runOptimized, 40L, 1L);
         }
      } else {
         this.mode = BiomeSpoofAdapter.Mode.OFF;
         if (this.task != null) {
            this.task.cancel();
            this.task = null;
         }

         this.pendingChunks.clear();
         this.queuedChunks.clear();
         this.backupLookupsPending.clear();
         this.backupLookupsCompleted.clear();
         this.clientRefreshQueue.clear();
         this.queuedClientRefreshes.clear();
         this.playerScanStates.clear();
         this.chunkCredits = 0.0;
         this.clientRefreshTickCounter = 0;
      }
   }

   private Season resolveNextVisualSeason(CalendarChannel channel, CalendarState st) {
      if (st == null) {
         return Season.SPRING;
      }

      if (st.monthsEnabled && channel != null && st.monthId != null && !st.monthId.isBlank()) {
         String base = "calendar.channels." + channel.name() + ".months";
         ConfigurationSection monthsSec = this.plugin.cfg.calendar.getConfigurationSection(base);
         if (monthsSec != null && monthsSec.getBoolean("enabled", false)) {
            List<String> order = monthsSec.getStringList("order");
            if (order != null && !order.isEmpty()) {
               int idx = -1;

               for (int i = 0; i < order.size(); i++) {
                  String id = order.get(i);
                  if (id != null && id.equalsIgnoreCase(st.monthId)) {
                     idx = i;
                     break;
                  }
               }

               if (idx < 0) {
                  return this.nextSeason(st.season);
               }

               String nextMonthId = order.get((idx + 1) % order.size());
               ConfigurationSection defs = monthsSec.getConfigurationSection("definitions");
               if (defs == null) {
                  return this.nextSeason(st.season);
               }

               ConfigurationSection nextDef = defs.getConfigurationSection(nextMonthId);
               if (nextDef == null) {
                  return this.nextSeason(st.season);
               }

               String raw = nextDef.getString("season", st.season.name());

               try {
                  return Season.valueOf(raw.trim().toUpperCase(Locale.ROOT));
               } catch (IllegalArgumentException ex) {
                  return st.season;
               }
            } else {
               return this.nextSeason(st.season);
            }
         } else {
            return this.nextSeason(st.season);
         }
      } else {
         return this.nextSeason(st.season);
      }
   }

   public boolean isEnabled() {
      return this.mode != BiomeSpoofAdapter.Mode.OFF;
   }

   public boolean isDiskBackupEnabled() {
      return this.diskBackupEnabled;
   }

   public void setDiskBackupEnabled(boolean enabled) {
      this.diskBackupEnabled = enabled;
   }

   public BiomeBackupStore getDiskBackups() {
      return this.diskBackups;
   }

   private record AppliedBiomeState(Biome target, Season season) {
      private boolean matches(Biome expectedTarget, Season expectedSeason) {
         return this.target == expectedTarget && this.season == expectedSeason;
      }
   }

   private record BiomeApplyResult(Biome[] original, BitSet modifiedCells, boolean changed, boolean successful) {
   }

   private record ChunkWork(UUID worldId, int chunkX, int chunkZ) {
   }

   private enum Family {
      LAND,
      OCEAN;
   }

   public enum Mode {
      GLOBAL_RING,
      OFF;
   }

   private record NudgeCooldownKey(UUID playerId, BiomeSpoofAdapter.NudgeTarget target) {
   }

   private record NudgeTarget(UUID worldId, int chunkX, int chunkZ) {
   }

   private static final class Offset {
      final int dx;
      final int dz;
      final int dist;
      final double forwardScore;

      Offset(int dx, int dz, int dist, double forwardScore) {
         this.dx = dx;
         this.dz = dz;
         this.dist = dist;
         this.forwardScore = forwardScore;
      }
   }

   private record PlayerScanState(UUID worldId, int chunkX, int chunkZ, int radius, long visualStamp) {
   }
}
