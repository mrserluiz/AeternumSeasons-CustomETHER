package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class IllusionerSpawnListener implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private boolean enabled;
   private double mansionReplacementChance;
   private double outpostReplacementChance;
   private int structureSearchRadiusChunks;
   private long globalSpawnCooldownMs;
   private int maxStructureSearchesPerSecond;
   private long cacheTtlPositiveMs;
   private long cacheTtlNegativeMs;
   private EnumSet<SpawnReason> allowedPillagerReasons;
   private EnumSet<SpawnReason> allowedMansionReasons;
   private final Map<IllusionerSpawnListener.CacheKey, ConcurrentHashMap<Long, IllusionerSpawnListener.CacheEntry>> chunkCache = new ConcurrentHashMap<>();
   private volatile int tokens;
   private volatile long tokenSecond;
   private static volatile long lastIllusionerSpawnTime = 0L;
   private boolean debug;

   public IllusionerSpawnListener(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.reloadFromConfig();
      this.resetRateLimiter();
   }

   public void reloadFromConfig() {
      ConfigurationSection sec = this.plugin.cfg.climate.getConfigurationSection("illusioner_spawning");
      if (sec == null) {
         this.enabled = false;
      } else {
         this.enabled = sec.getBoolean("enabled", false);
         this.mansionReplacementChance = sec.getDouble("mansion_replacement_chance", 0.1);
         this.outpostReplacementChance = sec.contains("outpost_replacement_chance")
            ? sec.getDouble("outpost_replacement_chance", 0.005)
            : sec.getDouble("village_spawn_chance", 0.005);
         this.structureSearchRadiusChunks = sec.contains("structure_check_radius_chunks")
            ? sec.getInt("structure_check_radius_chunks", 4)
            : sec.getInt("village_check_radius", 4);
         this.globalSpawnCooldownMs = sec.getLong("global_spawn_cooldown_ms", 2500L);
         this.maxStructureSearchesPerSecond = sec.getInt("max_structure_searches_per_second", 2);
         this.cacheTtlPositiveMs = sec.getLong("cache_ttl_positive_ms", 600000L);
         this.cacheTtlNegativeMs = sec.getLong("cache_ttl_negative_ms", 120000L);
         this.debug = sec.getBoolean("debug", false);
         this.allowedPillagerReasons = EnumSet.of(SpawnReason.NATURAL, SpawnReason.PATROL, SpawnReason.RAID);
         this.allowedMansionReasons = EnumSet.of(SpawnReason.NATURAL, SpawnReason.CHUNK_GEN);
         this.plugin
            .getLogger()
            .info(
               "[IllusionerSpawn] Enabled="
                  + this.enabled
                  + ", MansionChance="
                  + this.mansionReplacementChance
                  + ", OutpostChance="
                  + this.outpostReplacementChance
                  + ", RadiusChunks="
                  + this.structureSearchRadiusChunks
                  + ", CooldownMs="
                  + this.globalSpawnCooldownMs
                  + ", SearchesPerSec="
                  + this.maxStructureSearchesPerSecond
                  + ", CachePosMs="
                  + this.cacheTtlPositiveMs
                  + ", CacheNegMs="
                  + this.cacheTtlNegativeMs
                  + ", Debug="
                  + this.debug
            );
      }
   }

   private void resetRateLimiter() {
      this.tokenSecond = System.currentTimeMillis() / 1000L;
      this.tokens = this.maxStructureSearchesPerSecond;
   }

   @EventHandler(ignoreCancelled = true)
   public void onCreatureSpawn(CreatureSpawnEvent e) {
      if (this.enabled) {
         Location loc = e.getLocation();
         World w = loc.getWorld();
         if (w != null) {
            SpawnReason reason = e.getSpawnReason();
            EntityType type = e.getEntityType();
            if (type != EntityType.VINDICATOR && type != EntityType.EVOKER) {
               if (type == EntityType.PILLAGER) {
                  if (!this.allowedPillagerReasons.contains(reason)) {
                     return;
                  }

                  long now = System.currentTimeMillis();
                  if (now - lastIllusionerSpawnTime < this.globalSpawnCooldownMs) {
                     return;
                  }

                  if (ThreadLocalRandom.current().nextDouble() >= this.outpostReplacementChance) {
                     return;
                  }

                  if (this.isInStructureCached(loc, StructureType.PILLAGER_OUTPOST, this.structureSearchRadiusChunks)) {
                     e.setCancelled(true);
                     this.spawnIllusioner(loc, reason);
                     lastIllusionerSpawnTime = now;
                  }
               }
            } else if (this.allowedMansionReasons.contains(reason)) {
               if (!(ThreadLocalRandom.current().nextDouble() >= this.mansionReplacementChance)) {
                  if (this.isInStructureCached(loc, StructureType.WOODLAND_MANSION, this.structureSearchRadiusChunks)) {
                     e.setCancelled(true);
                     this.spawnIllusioner(loc, reason);
                  }
               }
            }
         }
      }
   }

   private void spawnIllusioner(Location loc, SpawnReason reason) {
      World w = loc.getWorld();
      if (w != null) {
         try {
            Entity ent = w.spawnEntity(loc, EntityType.ILLUSIONER, reason);
            if (this.debug) {
               this.plugin
                  .getLogger()
                  .info(
                     "[IllusionerSpawn] Illusioner generado en "
                        + loc.getBlockX()
                        + ", "
                        + loc.getBlockY()
                        + ", "
                        + loc.getBlockZ()
                        + " (Razón: "
                        + reason.name()
                        + "), ent="
                        + ent.getUniqueId()
                  );
            }
         } catch (Throwable ex) {
            this.plugin
               .getLogger()
               .warning(
                  "[IllusionerSpawn] Falló al generar Illusioner: " + ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "")
               );
         }
      }
   }

   private boolean isInStructureCached(Location loc, StructureType structureType, int radiusChunks) {
      World w = loc.getWorld();
      if (w == null) {
         return false;
      }

      Chunk c = loc.getChunk();
      long chunkKey = packChunkKey(c.getX(), c.getZ());
      IllusionerSpawnListener.CacheKey key = new IllusionerSpawnListener.CacheKey(w.getUID(), structureType);
      final ConcurrentHashMap<Long, IllusionerSpawnListener.CacheEntry> map = this.chunkCache.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
      long now = System.currentTimeMillis();
      IllusionerSpawnListener.CacheEntry cached = map.get(chunkKey);
      if (cached != null && cached.expiresAtMs > now) {
         return cached.inStructure;
      }

      if (!this.tryConsumeToken(now)) {
         if (this.debug) {
            this.plugin.getLogger().info("[IllusionerSpawn] RateLimit bloqueó locateNearestStructure (" + structureType + ")");
         }

         map.put(chunkKey, new IllusionerSpawnListener.CacheEntry(false, now + 10000L));
         return false;
      } else {
         boolean result = this.isNearStructureSlow(loc, structureType, radiusChunks);
         long ttl = result ? this.cacheTtlPositiveMs : this.cacheTtlNegativeMs;
         map.put(chunkKey, new IllusionerSpawnListener.CacheEntry(result, now + ttl));
         if (map.size() > 20000) {
            (new BukkitRunnable() {
               public void run() {
                  long t = System.currentTimeMillis();
                  map.entrySet().removeIf(en -> en.getValue().expiresAtMs <= t);
               }
            }).runTask(this.plugin);
         }

         return result;
      }
   }

   private boolean isNearStructureSlow(Location loc, StructureType structureType, int radiusChunks) {
      World w = loc.getWorld();
      if (w == null) {
         return false;
      }

      try {
         Location struct = w.locateNearestStructure(loc, structureType, radiusChunks, false);
         if (struct == null) {
            return false;
         }

         double maxDistBlocks = radiusChunks * 16.0;
         return struct.getWorld().equals(w) && struct.distanceSquared(loc) <= maxDistBlocks * maxDistBlocks;
      } catch (Throwable ex) {
         if (this.debug) {
            this.plugin
               .getLogger()
               .warning(
                  "[IllusionerSpawn] locateNearestStructure falló ("
                     + structureType
                     + "): "
                     + ex.getClass().getSimpleName()
                     + (ex.getMessage() != null ? ": " + ex.getMessage() : "")
               );
         }

         return false;
      }
   }

   private boolean tryConsumeToken(long nowMs) {
      long sec = nowMs / 1000L;
      if (sec != this.tokenSecond) {
         this.tokenSecond = sec;
         this.tokens = Math.max(0, this.maxStructureSearchesPerSecond);
      }

      if (this.tokens <= 0) {
         return false;
      }

      this.tokens--;
      return true;
   }

   private static long packChunkKey(int cx, int cz) {
      return (long)cx << 32 ^ cz & 4294967295L;
   }

   @EventHandler(ignoreCancelled = true)
   public void onEntityDeath(EntityDeathEvent e) {
      if (e.getEntityType() == EntityType.ILLUSIONER) {
         e.getDrops().clear();
         int totemCount = ThreadLocalRandom.current().nextInt(2) + 1;
         e.getDrops().add(new ItemStack(Material.TOTEM_OF_UNDYING, totemCount));
      }
   }

   private static final class CacheEntry {
      final boolean inStructure;
      final long expiresAtMs;

      CacheEntry(boolean inStructure, long expiresAtMs) {
         this.inStructure = inStructure;
         this.expiresAtMs = expiresAtMs;
      }
   }

   private record CacheKey(UUID worldId, StructureType type) {
   }
}
