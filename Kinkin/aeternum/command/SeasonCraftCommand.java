package Kinkin.aeternum.command;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import Kinkin.aeternum.crafting.AdvancedComposter;
import Kinkin.aeternum.crafting.GolemUpgrades;
import Kinkin.aeternum.crafting.HarvestHoe;
import Kinkin.aeternum.crafting.LabradorBoots;
import Kinkin.aeternum.crafting.LunarLantern;
import Kinkin.aeternum.crafting.NaturalTreeAxe;
import Kinkin.aeternum.crafting.SnowStepBoots;
import Kinkin.aeternum.crafting.SolarTorch;
import Kinkin.aeternum.crafting.SpringBell;
import Kinkin.aeternum.crafting.SpringStrideBoots;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SeasonCraftCommand implements CommandExecutor, Listener {
   private static final String PERM = "aeternum.seasoncraft";
   private static final String CFG_ROOT = "seasoncraft";
   private static final String CFG_RECIPES = "seasoncraft.recipes";
   private final AeternumSeasonsPlugin plugin;
   private final NamespacedKey pdcIdKey;
   private final List<SeasonCraftCommand.Entry> entries = new ArrayList<>();

   public SeasonCraftCommand(
      AeternumSeasonsPlugin plugin,
      HarvestHoe harvestHoe,
      NaturalTreeAxe naturalTreeAxe,
      SnowStepBoots snowStepBoots,
      SpringStrideBoots springStrideBoots,
      LabradorBoots labradorBoots,
      SolarTorch solarTorch,
      LunarLantern lunarLantern,
      SpringBell springBell,
      AdvancedComposter advancedComposter,
      GolemUpgrades golemUpgrades
   ) {
      this.plugin = plugin;
      this.pdcIdKey = new NamespacedKey(plugin, "seasoncraft_entry_id");
      this.entries.add(new SeasonCraftCommand.Entry("harvest_hoe", "Harvest Hoe", Material.DIAMOND_HOE, () -> {
         harvestHoe.unregister();
         harvestHoe.register();
      }, harvestHoe::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("natural_tree_axe", "Natural Tree Axe", Material.DIAMOND_AXE, () -> {
         naturalTreeAxe.unregister();
         naturalTreeAxe.register();
      }, naturalTreeAxe::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("snow_step_boots", "Snow Step Boots", Material.LEATHER_BOOTS, () -> {
         snowStepBoots.unregister();
         snowStepBoots.register();
      }, snowStepBoots::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("spring_stride_boots", "Spring Stride Boots", Material.PINK_PETALS, () -> {
         springStrideBoots.unregister();
         springStrideBoots.register();
      }, springStrideBoots::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("labrador_boots", "Labrador Boots", Material.WHEAT, () -> {
         labradorBoots.unregister();
         labradorBoots.register();
      }, labradorBoots::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("solar_torch", "Solar Torch", Material.TORCH, () -> {
         solarTorch.unregister();
         solarTorch.register();
      }, solarTorch::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("lunar_lantern", "Lunar Lantern", Material.LANTERN, () -> {
         lunarLantern.unregister();
         lunarLantern.register();
      }, lunarLantern::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("spring_bell", "Spring Bell", Material.BELL, () -> {
         springBell.unregister();
         springBell.register();
      }, springBell::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("advanced_composter", "Advanced Composter", Material.COMPOSTER, () -> {
         advancedComposter.unregister();
         advancedComposter.register();
      }, advancedComposter::unregister));
      this.entries.add(new SeasonCraftCommand.Entry("golem_cores", "Golem Cores (Frost/Guardian)", Material.PACKED_ICE, () -> {
         golemUpgrades.unregister();
         golemUpgrades.register();
      }, golemUpgrades::unregister));
      this.ensureDefaultsAndApply();
   }

   private void ensureDefaultsAndApply() {
      FileConfiguration cfg = this.plugin.getConfig();
      if (!cfg.contains("seasoncraft.enabled")) {
         cfg.set("seasoncraft.enabled", true);
      }

      if (!cfg.contains("seasoncraft.gui_title")) {
         cfg.set("seasoncraft.gui_title", "&bSeasonCraft");
      }

      for (SeasonCraftCommand.Entry e : this.entries) {
         String path = "seasoncraft.recipes." + e.id;
         if (!cfg.contains(path)) {
            cfg.set(path, true);
         }
      }

      this.plugin.saveConfig();

      for (SeasonCraftCommand.Entry e : this.entries) {
         this.setEntryEnabled(e, this.isEnabled(e.id));
      }
   }

   private boolean isSystemEnabled() {
      return this.plugin.getConfig().getBoolean("seasoncraft.enabled", true);
   }

   private boolean isEnabled(String id) {
      return this.plugin.getConfig().getBoolean("seasoncraft.recipes." + id, true);
   }

   private void setEnabled(String id, boolean enabled) {
      this.plugin.getConfig().set("seasoncraft.recipes." + id, enabled);
      this.plugin.saveConfig();
   }

   private void setEntryEnabled(SeasonCraftCommand.Entry e, boolean enabled) {
      if (enabled) {
         e.enable.run();
      } else {
         e.disable.run();
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player p) {
         if (!p.hasPermission("aeternum.seasoncraft")) {
            p.sendMessage("§cNo tienes permiso.");
            return true;
         } else if (!this.isSystemEnabled()) {
            p.sendMessage("§cSeasonCraft está desactivado en config.yml.");
            return true;
         } else {
            this.openGui(p);
            return true;
         }
      } else {
         sender.sendMessage("Solo jugadores.");
         return true;
      }
   }

   private void openGui(Player p) {
      SeasonCraftCommand.Holder holder = new SeasonCraftCommand.Holder();
      int size = 27;
      Inventory inv = Bukkit.createInventory(holder, size, color(this.plugin.getConfig().getString("seasoncraft.gui_title", "&bSeasonCraft")));
      holder.inv = inv;
      ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta fm = filler.getItemMeta();
      if (fm != null) {
         fm.setDisplayName(" ");
         filler.setItemMeta(fm);
      }

      for (int i = 0; i < size; i++) {
         inv.setItem(i, filler);
      }

      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

      for (int i = 0; i < this.entries.size() && i < slots.length; i++) {
         inv.setItem(slots[i], this.buildEntryItem(this.entries.get(i)));
      }

      p.openInventory(inv);
      p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
   }

   private ItemStack buildEntryItem(SeasonCraftCommand.Entry e) {
      boolean enabled = this.isEnabled(e.id);
      ItemStack it = new ItemStack(e.icon);
      ItemMeta meta = it.getItemMeta();
      if (meta != null) {
         meta.setDisplayName("§e" + e.title);
         List<String> lore = new ArrayList<>();
         lore.add(" ");
         lore.add("§7" + (enabled ? "§aON" : "§cOFF"));
         lore.add("§8(Click)");
         meta.setLore(lore);
         if (enabled) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
         }

         meta.getPersistentDataContainer().set(this.pdcIdKey, PersistentDataType.STRING, e.id);
         it.setItemMeta(meta);
      }

      return it;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onInvClick(InventoryClickEvent e) {
      if (e.getWhoClicked() instanceof Player p) {
         if (e.getInventory().getHolder() instanceof SeasonCraftCommand.Holder) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
               ItemMeta meta = clicked.getItemMeta();
               if (meta != null) {
                  String id = (String)meta.getPersistentDataContainer().get(this.pdcIdKey, PersistentDataType.STRING);
                  if (id != null && !id.isEmpty()) {
                     SeasonCraftCommand.Entry entry = this.entries.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
                     if (entry != null) {
                        boolean newState = !this.isEnabled(id);
                        this.setEnabled(id, newState);
                        this.setEntryEnabled(entry, newState);
                        e.getInventory().setItem(e.getSlot(), this.buildEntryItem(entry));
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, newState ? 1.6F : 0.9F);
                        p.sendMessage("§bSeasonCraft§7: §e" + entry.title + "§7 -> " + (newState ? "§aON" : "§cOFF"));
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onInvClose(InventoryCloseEvent e) {
      if (e.getInventory().getHolder() instanceof SeasonCraftCommand.Holder) {
         ;
      }
   }

   private static String color(String s) {
      return s == null ? "" : s.replace("&", "§");
   }

   private static final class Entry {
      final String id;
      final String title;
      final Material icon;
      final Runnable enable;
      final Runnable disable;

      Entry(String id, String title, Material icon, Runnable enable, Runnable disable) {
         this.id = id;
         this.title = title;
         this.icon = icon;
         this.enable = enable;
         this.disable = disable;
      }
   }

   private static final class Holder implements InventoryHolder {
      private Inventory inv;

      public Inventory getInventory() {
         return this.inv;
      }
   }
}
