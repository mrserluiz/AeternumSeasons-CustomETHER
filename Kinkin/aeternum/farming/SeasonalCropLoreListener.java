package Kinkin.aeternum.farming;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

public final class SeasonalCropLoreListener implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final SeasonalCropConfig config;
   private final CropLoreService lore;

   public SeasonalCropLoreListener(AeternumSeasonsPlugin plugin, SeasonalCropConfig config) {
      this.plugin = plugin;
      this.config = config;
      this.lore = new CropLoreService(plugin, config);
   }

   public void register() {
      if (this.config.isEnabled()) {
         Bukkit.getPluginManager().registerEvents(this, this.plugin);
      }
   }

   public void unregister() {
      ItemSpawnEvent.getHandlerList().unregister(this);
      EntityPickupItemEvent.getHandlerList().unregister(this);
      CraftItemEvent.getHandlerList().unregister(this);
      LootGenerateEvent.getHandlerList().unregister(this);
      PlayerJoinEvent.getHandlerList().unregister(this);
   }

   @EventHandler(ignoreCancelled = true)
   public void onItemSpawn(ItemSpawnEvent e) {
      if (this.config.isEnabled()) {
         Item it = e.getEntity();
         ItemStack stack = it.getItemStack();
         if (this.lore.apply(stack, null)) {
            it.setItemStack(stack);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPickup(EntityPickupItemEvent e) {
      if (this.config.isEnabled()) {
         if (e.getEntity() instanceof Player p) {
            ItemStack var4 = e.getItem().getItemStack();
            if (this.lore.apply(var4, p)) {
               e.getItem().setItemStack(var4);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onCraft(CraftItemEvent e) {
      if (this.config.isEnabled()) {
         if (e.getWhoClicked() instanceof Player p) {
            ItemStack var4 = e.getCurrentItem();
            this.lore.apply(var4, p);
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onLoot(LootGenerateEvent e) {
      if (this.config.isEnabled()) {
         e.getLoot().forEach(stack -> this.lore.apply(stack, null));
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent e) {
      if (this.config.isEnabled()) {
         Player p = e.getPlayer();

         for (ItemStack stack : p.getInventory().getContents()) {
            this.lore.apply(stack, p);
         }
      }
   }
}
