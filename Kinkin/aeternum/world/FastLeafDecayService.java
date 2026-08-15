package Kinkin.aeternum.world;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;

public final class FastLeafDecayService implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final Set<FastLeafDecayService.DecayArea> pendingAreas = new HashSet<>();
   private boolean registered;
   private final Set<Material> logTypes = EnumSet.of(
      Material.OAK_LOG,
      Material.SPRUCE_LOG,
      Material.BIRCH_LOG,
      Material.DARK_OAK_LOG,
      Material.JUNGLE_LOG,
      Material.ACACIA_LOG,
      Material.CHERRY_LOG,
      Material.MANGROVE_LOG
   );
   private final Set<Material> leafTypes = EnumSet.of(
      Material.OAK_LEAVES,
      Material.SPRUCE_LEAVES,
      Material.BIRCH_LEAVES,
      Material.DARK_OAK_LEAVES,
      Material.JUNGLE_LEAVES,
      Material.ACACIA_LEAVES,
      Material.CHERRY_LEAVES,
      Material.MANGROVE_LEAVES
   );

   public FastLeafDecayService(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
   }

   public void register() {
      this.registered = true;
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
   }

   public void unregister() {
      this.registered = false;
      this.pendingAreas.clear();
      BlockBreakEvent.getHandlerList().unregister(this);
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onLogBreak(BlockBreakEvent event) {
      Block block = event.getBlock();
      if (this.logTypes.contains(block.getType())) {
         FastLeafDecayService.DecayArea area = FastLeafDecayService.DecayArea.from(block);
         if (this.pendingAreas.add(area)) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               try {
                  if (this.registered) {
                     this.fastDecayAround(block);
                  }
               } finally {
                  this.pendingAreas.remove(area);
               }
            }, 20L);
         }
      }
   }

   private void fastDecayAround(Block originLog) {
      World w = originLog.getWorld();
      if (w != null) {
         AutumnSoilPainter autumnPainter = AutumnSoilPainter.instanceOrNull();
         int radius = 7;
         int ox = originLog.getX();
         int oy = originLog.getY();
         int oz = originLog.getZ();
         int minY = Math.max(w.getMinHeight(), oy - radius);
         int maxY = Math.min(w.getMaxHeight() - 1, oy + radius);

         for (int x = ox - radius; x <= ox + radius; x++) {
            for (int z = oz - radius; z <= oz + radius; z++) {
               if (w.isChunkLoaded(x >> 4, z >> 4)) {
                  for (int y = minY; y <= maxY; y++) {
                     Block leaf = w.getBlockAt(x, y, z);
                     if (this.leafTypes.contains(leaf.getType())
                        && leaf.getBlockData() instanceof Leaves leaves
                        && !leaves.isPersistent()
                        && leaves.getDistance() >= 7
                        && !(ThreadLocalRandom.current().nextDouble() >= 0.6)) {
                        if (leaf.getType() == Material.ACACIA_LEAVES && autumnPainter != null) {
                           autumnPainter.restoreOriginalLeafForDecay(leaf);
                           if (!(leaf.getBlockData() instanceof Leaves refreshedLeaves) || refreshedLeaves.isPersistent() || refreshedLeaves.getDistance() < 7) {
                              continue;
                           }
                        }

                        LeavesDecayEvent decayEvent = new LeavesDecayEvent(leaf);
                        Bukkit.getPluginManager().callEvent(decayEvent);
                        if (!decayEvent.isCancelled() && this.leafTypes.contains(leaf.getType())) {
                           leaf.breakNaturally();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private record DecayArea(UUID worldId, int sectionX, int sectionY, int sectionZ) {
      private static FastLeafDecayService.DecayArea from(Block block) {
         return new FastLeafDecayService.DecayArea(block.getWorld().getUID(), block.getX() >> 3, block.getY() >> 3, block.getZ() >> 3);
      }
   }
}
