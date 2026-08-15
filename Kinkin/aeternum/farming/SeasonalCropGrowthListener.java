package Kinkin.aeternum.farming;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.calendar.CalendarState;
import Kinkin.aeternum.calendar.Season;
import Kinkin.aeternum.calendar.SeasonService;
import java.util.EnumSet;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SeasonalCropGrowthListener implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final SeasonService seasons;
   private final SeasonalCropConfig config;
   private final GreenhouseService greenhouse;
   private final CropGrowthService cropGrowth;
   private final SeasonalCropLoreListener loreListener;

   public SeasonalCropGrowthListener(AeternumSeasonsPlugin plugin, SeasonService seasons) {
      this.plugin = plugin;
      this.seasons = seasons;
      this.config = new SeasonalCropConfig(plugin);
      this.greenhouse = new GreenhouseService(this.config);
      this.cropGrowth = new CropGrowthService(this.config, this.greenhouse, seasons);
      this.loreListener = new SeasonalCropLoreListener(plugin, this.config);
   }

   public void register() {
      if (this.config.isEnabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
         this.loreListener.register();
      }
   }

   public void unregister() {
      BlockGrowEvent.getHandlerList().unregister(this);
      PlayerInteractEvent.getHandlerList().unregister(this);
      this.loreListener.unregister();
   }

   public void reloadFromConfig() {
      this.config.reload();
   }

   @EventHandler
   public void onBlockSpread(BlockGrowEvent e) {
      Material type = e.getNewState().getType();
      if (type != Material.MELON && type == Material.PUMPKIN) {
      }
   }

   @EventHandler
   public void onGrow(BlockGrowEvent e) {
      if (this.config.isEnabled()) {
         Block block = e.getBlock();
         Material newType = e.getNewState().getType();
         if (newType != Material.MELON && newType != Material.PUMPKIN) {
            if (e.getNewState().getBlockData() instanceof Ageable age) {
               CropGrowthService.GrowthDecision decision = this.cropGrowth.evaluate(block);
               if (decision.cancel() && Math.random() > this.config.getOffSeasonMultiplier()) {
                  e.setCancelled(true);
               } else {
                  int extra = decision.extraAges();
                  if (extra > 0) {
                     int newAge = Math.min(age.getMaximumAge(), age.getAge() + extra);
                     age.setAge(newAge);
                     e.getNewState().setBlockData(age);
                  }
               }
            }
         } else if (!this.isStemInSeason(block) && Math.random() > this.config.getOffSeasonMultiplier()) {
            e.setCancelled(true);
         }
      }
   }

   private boolean isStemInSeason(Block fruitBlock) {
      Material[] stems = new Material[]{Material.MELON_STEM, Material.ATTACHED_MELON_STEM, Material.PUMPKIN_STEM, Material.ATTACHED_PUMPKIN_STEM};

      for (BlockFace face : BlockFace.values()) {
         if (face.isCartesian()) {
            Block adjacent = fruitBlock.getRelative(face);

            for (Material stemType : stems) {
               if (adjacent.getType() == stemType) {
                  if (this.greenhouse.isInGreenhouse(adjacent)) {
                     return true;
                  }

                  EnumSet<Season> allowed = this.config.getAllowedSeasons(adjacent.getType());
                  return allowed != null && allowed.contains(this.seasons.getStateCopy(adjacent.getWorld()).season);
               }
            }
         }
      }

      return true;
   }

   @EventHandler(ignoreCancelled = true)
   public void onDebugStick(PlayerInteractEvent e) {
      if (this.config.isEnabled() && this.config.isDebugEnabled()) {
         if (e.getHand() == EquipmentSlot.HAND) {
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
               if (e.getClickedBlock() != null) {
                  if (e.getItem() != null) {
                     if (e.getItem().getType() == this.config.getDebugStickMaterial()) {
                        Block block = e.getClickedBlock();
                        if (this.config.isManagedCrop(block.getType())) {
                           boolean inGreenhouse = this.greenhouse.isInGreenhouse(block);
                           Player p = e.getPlayer();
                           CalendarState st = this.seasons.getStateCopy(block.getWorld());
                           if (inGreenhouse) {
                              p.sendMessage(ChatColor.GREEN + "This crop is protected by the greenhouse.(" + st.season.name() + ").");
                           } else {
                              p.sendMessage(ChatColor.RED + "This crop is NOT protected by the greenhouse (" + st.season.name() + ").");
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
