package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public final class BiomeSpoofSpawnGuard implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final BiomeSpoofAdapter spoof;
   private boolean enabled = true;

   public BiomeSpoofSpawnGuard(AeternumSeasonsPlugin plugin, BiomeSpoofAdapter spoof) {
      this.plugin = plugin;
      this.spoof = spoof;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
   }

   public void unregister() {
      HandlerList.unregisterAll(this);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (this.enabled) {
         SpawnReason reason = event.getSpawnReason();
         if (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN) {
            EntityType type = event.getEntityType();
            if (this.hasBiomeRestrictedNaturalSpawn(type)) {
               Location location = event.getLocation();
               World world = location.getWorld();
               if (world != null && world.getEnvironment() == Environment.NORMAL) {
                  Biome original = this.spoof.getOriginalBiomeApprox(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                  if (!isOriginalHabitat(type, original)) {
                     event.setCancelled(true);
                  }
               }
            }
         }
      }
   }

   private boolean hasBiomeRestrictedNaturalSpawn(EntityType type) {
      return switch (type) {
         case OCELOT, PARROT, PANDA, MOOSHROOM, POLAR_BEAR, GOAT, FROG, AXOLOTL, TROPICAL_FISH, PUFFERFISH, TURTLE, ARMADILLO, HUSK, STRAY, BOGGED, DROWNED -> true;
         default -> false;
      };
   }

   public static boolean isOriginalHabitat(EntityType type, Biome biome) {
      if (biome == null) {
         return false;
      }

      String name = biome.name().toUpperCase(Locale.ROOT);

      return switch (type) {
         case OCELOT, PARROT, PANDA -> name.contains("JUNGLE");
         case MOOSHROOM -> name.equals("MUSHROOM_FIELDS");
         case POLAR_BEAR -> name.equals("SNOWY_PLAINS") || name.equals("ICE_SPIKES") || name.contains("FROZEN_OCEAN");
         case GOAT -> name.equals("SNOWY_SLOPES") || name.equals("JAGGED_PEAKS") || name.equals("FROZEN_PEAKS");
         case FROG, BOGGED -> name.contains("SWAMP") || name.contains("MANGROVE");
         case AXOLOTL -> name.equals("LUSH_CAVES");
         case TROPICAL_FISH -> name.equals("LUSH_CAVES") || name.contains("WARM_OCEAN") || name.contains("LUKEWARM_OCEAN");
         case PUFFERFISH -> name.contains("WARM_OCEAN") || name.contains("LUKEWARM_OCEAN");
         case TURTLE -> name.contains("BEACH");
         case ARMADILLO -> name.contains("SAVANNA") || name.contains("BADLANDS");
         case HUSK -> name.contains("DESERT");
         case STRAY -> name.contains("SNOW") || name.contains("FROZEN") || name.equals("ICE_SPIKES") || name.equals("GROVE");
         case DROWNED -> name.contains("OCEAN") || name.contains("RIVER");
         default -> true;
      };
   }
}
