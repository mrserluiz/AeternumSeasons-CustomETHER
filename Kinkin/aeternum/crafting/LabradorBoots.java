package Kinkin.aeternum.crafting;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class LabradorBoots implements Listener {
   private final AeternumSeasonsPlugin plugin;
   private final NamespacedKey recipeKey;
   private final NamespacedKey tagKey;
   private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
   private static final Set<Material> SEED_CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);

   public LabradorBoots(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.recipeKey = new NamespacedKey(plugin, "labrador_boots");
      this.tagKey = new NamespacedKey(plugin, "labrador_boots");
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
      this.registerRecipe();
   }

   public void unregister() {
      HandlerList.unregisterAll(this);
      Bukkit.removeRecipe(this.recipeKey);
   }

   private void registerRecipe() {
      ItemStack dummy = new ItemStack(Material.LEATHER_BOOTS);
      ShapedRecipe recipe = new ShapedRecipe(this.recipeKey, dummy);
      recipe.shape(new String[]{"WBW", "WSW", "WWW"});
      List<Material> bootsList = new ArrayList<>();

      for (Material m : Material.values()) {
         if (m != null && m.name().endsWith("_BOOTS")) {
            bootsList.add(m);
         }
      }

      RecipeChoice bootsChoice = new MaterialChoice(bootsList);
      List<Material> shovelList = new ArrayList<>();

      for (Material m : Material.values()) {
         if (m != null && m.name().endsWith("_SHOVEL")) {
            shovelList.add(m);
         }
      }

      RecipeChoice shovelChoice = new MaterialChoice(shovelList);
      recipe.setIngredient('B', bootsChoice);
      recipe.setIngredient('W', Material.WHEAT);
      recipe.setIngredient('S', shovelChoice);
      Bukkit.addRecipe(recipe);
   }

   @EventHandler
   public void onPrepareCraft(PrepareItemCraftEvent e) {
      if (e.getRecipe() != null) {
         if (e.getRecipe() instanceof ShapedRecipe shaped) {
            if (this.recipeKey.equals(shaped.getKey())) {
               ItemStack[] matrix = e.getInventory().getMatrix();
               ItemStack boots = null;
               int wheatCount = 0;
               boolean hasShovel = false;

               for (ItemStack stack : matrix) {
                  if (stack != null && stack.getType() != Material.AIR) {
                     Material t = stack.getType();
                     if (this.isBoots(t)) {
                        if (boots != null) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        boots = stack.clone();
                     } else if (t == Material.WHEAT) {
                        wheatCount += stack.getAmount();
                     } else {
                        if (!this.isShovel(t)) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        if (hasShovel) {
                           e.getInventory().setResult(null);
                           return;
                        }

                        hasShovel = true;
                     }
                  }
               }

               if (boots != null && hasShovel && wheatCount >= 6) {
                  Player crafter = null;
                  if (e.getView().getPlayer() instanceof Player pl) {
                     crafter = pl;
                  }

                  ItemMeta meta = boots.getItemMeta();
                  if (meta != null) {
                     PersistentDataContainer pdc = meta.getPersistentDataContainer();
                     pdc.set(this.tagKey, PersistentDataType.BYTE, (byte)1);

                     try {
                        String name = this.plugin.lang.tr(crafter, "item.labrador_boots.name");
                        String lore1 = this.plugin.lang.tr(crafter, "item.labrador_boots.lore1");
                        String lore2 = this.plugin.lang.tr(crafter, "item.labrador_boots.lore2");
                        String lore3 = this.plugin.lang.tr(crafter, "item.labrador_boots.lore3");
                        meta.setDisplayName(name);
                        meta.setLore(Arrays.asList(lore1, lore2, lore3));
                     } catch (Throwable var14) {
                     }

                     boots.setItemMeta(meta);
                  }

                  e.getInventory().setResult(boots);
               } else {
                  e.getInventory().setResult(null);
               }
            }
         }
      }
   }

   private boolean isBoots(Material m) {
      if (m == null) {
         return false;
      }

      if (m.name().endsWith("_BOOTS")) {
         return true;
      }

      NamespacedKey k = m.getKey();
      return k != null && "minecraft".equals(k.getNamespace()) && k.getKey().endsWith("_boots");
   }

   private boolean isShovel(Material m) {
      if (m == null) {
         return false;
      }

      if (m.name().endsWith("_SHOVEL")) {
         return true;
      }

      NamespacedKey k = m.getKey();
      return k != null && "minecraft".equals(k.getNamespace()) && k.getKey().endsWith("_shovel");
   }

   private boolean hasLabradorBoots(Player p) {
      ItemStack boots = p.getInventory().getBoots();
      if (boots != null && boots.getType() != Material.AIR) {
         ItemMeta meta = boots.getItemMeta();
         if (meta == null) {
            return false;
         }

         Byte flag = (Byte)meta.getPersistentDataContainer().get(this.tagKey, PersistentDataType.BYTE);
         return flag != null && flag == 1;
      } else {
         return false;
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
   public void onFarmlandTrample(EntityChangeBlockEvent e) {
      if (e.getEntity() instanceof Player p) {
         if (e.getBlock().getType() == Material.FARMLAND) {
            if (this.hasLabradorBoots(p)) {
               e.setCancelled(true);
               e.getBlock().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getBlock().getLocation().add(0.5, 1.0, 0.5), 6, 0.3, 0.2, 0.3);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent e) {
      if (e.getTo() != null) {
         if (e.getFrom().getBlockX() != e.getTo().getBlockX()
            || e.getFrom().getBlockY() != e.getTo().getBlockY()
            || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            Player p = e.getPlayer();
            if (this.hasLabradorBoots(p)) {
               Block below = e.getTo().clone().subtract(0.0, 0.1, 0.0).getBlock();
               if (below.getType() == Material.FARMLAND) {
                  p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10, 0, true, false, true));
               }
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onCropBreak(BlockBreakEvent e) {
      Player p = e.getPlayer();
      if (this.hasLabradorBoots(p)) {
         Block b = e.getBlock();
         Material type = b.getType();
         if (SEED_CROPS.contains(type)) {
            if (b.getBlockData() instanceof Ageable ageable) {
               if (ageable.getAge() >= ageable.getMaximumAge()) {
                  if (this.rnd.nextDouble() < 0.35) {
                     int extra = this.rnd.nextInt(1, 3);

                     Material seedType = switch (type) {
                        case WHEAT -> Material.WHEAT_SEEDS;
                        case CARROTS -> Material.CARROT;
                        case POTATOES -> Material.POTATO;
                        case BEETROOTS -> Material.BEETROOT_SEEDS;
                        default -> null;
                     };
                     if (seedType != null) {
                        ItemStack extraSeeds = new ItemStack(seedType, extra);
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.2, 0.5), extraSeeds);
                     }
                  }
               }
            }
         }
      }
   }
}
