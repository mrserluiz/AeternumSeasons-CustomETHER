package Kinkin.aeternum.heat;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HeatVariantListener implements Listener {
   private static final String HEAT_WORLD_NAME = "aeternum_heat";
   private final AeternumSeasonsPlugin plugin;
   private final NamespacedKey VARIANT_KEY;
   private final NamespacedKey ARROW_KEY;
   private final Random random = new Random();

   public HeatVariantListener(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.VARIANT_KEY = new NamespacedKey(plugin, "heat_variant");
      this.ARROW_KEY = new NamespacedKey(plugin, "heat_arrow");
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
   }

   public void unregister() {
      HandlerList.unregisterAll(this);
   }

   private boolean inHeatWorld(Entity e) {
      World w = e.getWorld();
      return w != null && w.getName().equalsIgnoreCase("aeternum_heat");
   }

   private boolean isHostile(LivingEntity e) {
      return e instanceof Monster || e instanceof Slime || e instanceof Phantom;
   }

   private void markVariant(LivingEntity e) {
      PersistentDataContainer pdc = e.getPersistentDataContainer();
      pdc.set(this.VARIANT_KEY, PersistentDataType.BYTE, (byte)1);
   }

   private boolean isVariant(Entity e) {
      PersistentDataContainer pdc = e.getPersistentDataContainer();
      Byte b = (Byte)pdc.get(this.VARIANT_KEY, PersistentDataType.BYTE);
      return b != null && b == 1;
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
   public void onSpawn(CreatureSpawnEvent e) {
      LivingEntity le = e.getEntity();
      if (this.inHeatWorld(le)) {
         if (this.isHostile(le)) {
            if (!this.isVariant(le)) {
               this.markVariant(le);
               le.getWorld().spawnParticle(Particle.FLAME, le.getLocation().add(0.0, 1.0, 0.0), 16, 0.4, 0.4, 0.4, 0.02);
            }

            AttributeInstance hp = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null && hp.getBaseValue() < 1.0) {
               hp.setBaseValue(20.0);
               le.setHealth(20.0);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
   public void onShoot(EntityShootBowEvent e) {
      if (e.getEntity() instanceof Skeleton sk) {
         if (this.inHeatWorld(sk) && this.isVariant(sk)) {
            if (e.getProjectile() instanceof Arrow arrow) {
               arrow.setFireTicks(200);
               arrow.setCritical(true);
               PersistentDataContainer pdc = arrow.getPersistentDataContainer();
               pdc.set(this.ARROW_KEY, PersistentDataType.BYTE, (byte)1);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
   public void onArrowHit(ProjectileHitEvent e) {
      if (e.getEntity() instanceof Arrow arrow) {
         PersistentDataContainer pdc = arrow.getPersistentDataContainer();
         Byte tag = (Byte)pdc.get(this.ARROW_KEY, PersistentDataType.BYTE);
         if (tag != null && tag == 1) {
            World w = arrow.getWorld();
            Location hitLoc = arrow.getLocation();
            Block b = w.getBlockAt(hitLoc);
            if (b.getType().isAir() || b.isPassable()) {
               b.setType(Material.FIRE, false);
            }

            if (e.getHitEntity() instanceof LivingEntity victim) {
               victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
               victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, true, true));
            }

            w.spawnParticle(Particle.FLAME, hitLoc, 24, 0.4, 0.4, 0.4, 0.02);
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
   public void onHit(EntityDamageByEntityEvent e) {
      LivingEntity attacker = null;
      if (e.getDamager() instanceof LivingEntity le) {
         attacker = le;
      } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le2) {
         attacker = le2;
      }

      if (attacker != null) {
         if (this.inHeatWorld(attacker) && this.isVariant(attacker)) {
            if (e.getEntity() instanceof LivingEntity victim) {
               this.applyBaseFireHit(attacker, victim, e);
               if (attacker instanceof Zombie || attacker instanceof PigZombie || attacker instanceof Piglin || attacker instanceof PiglinBrute) {
                  this.applyBruteIgnis(victim, attacker);
               } else if (attacker instanceof Spider || attacker instanceof CaveSpider) {
                  this.applyMagmaSpider(victim);
               } else if (attacker instanceof Enderman) {
                  this.applyEmberEnderman(victim);
               }
            }
         }
      }
   }

   private void applyBaseFireHit(LivingEntity attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
      if (victim instanceof Player pl) {
         GameMode gm = pl.getGameMode();
         if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return;
         }
      }

      victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
      e.setDamage(e.getDamage() * 1.25);
      victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.3, 0.3, 0.01);
   }

   private void applyBruteIgnis(LivingEntity victim, LivingEntity attacker) {
      World w = victim.getWorld();
      Location loc = victim.getLocation();
      double radius = 1.5;
      w.spawnParticle(Particle.EXPLOSION, loc.clone().add(0.0, 0.5, 0.0), 1, 0.0, 0.0, 0.0, 0.0);

      for (Entity nearby : w.getNearbyEntities(loc, radius, radius, radius)) {
         if (nearby instanceof LivingEntity le && !le.equals(attacker)) {
            if (nearby instanceof Player pl) {
               GameMode gm = pl.getGameMode();
               if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                  continue;
               }
            }

            le.setFireTicks(Math.max(le.getFireTicks(), 60));
         }
      }
   }

   private void applyMagmaSpider(LivingEntity victim) {
      World w = victim.getWorld();
      Location loc = victim.getLocation();
      int count = 6 + this.random.nextInt(5);

      for (int i = 0; i < count; i++) {
         double dx = (this.random.nextDouble() - 0.5) * 2.0;
         double dz = (this.random.nextDouble() - 0.5) * 2.0;
         Location spot = loc.clone().add(dx, 0.0, dz);
         Block b = w.getBlockAt(spot);
         if (b.getType().isAir() || b.isPassable()) {
            b.setType(Material.FIRE, false);
         }
      }

      w.spawnParticle(Particle.LAVA, loc.add(0.0, 0.2, 0.0), 12, 0.4, 0.2, 0.4, 0.02);
   }

   private void applyEmberEnderman(LivingEntity victim) {
      World w = victim.getWorld();
      Location loc = victim.getLocation();
      victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
      victim.damage(0.5);
      w.spawnParticle(Particle.FLAME, loc.add(0.0, 1.0, 0.0), 24, 0.6, 0.6, 0.6, 0.02);
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
   public void onExplode(EntityExplodeEvent e) {
      if (e.getEntity() instanceof Creeper creeper) {
         if (this.inHeatWorld(creeper) && this.isVariant(creeper)) {
            Location loc = creeper.getLocation();
            World w = loc.getWorld();
            e.blockList().clear();
            double radius = 3.0;

            for (double x = -radius; x <= radius; x++) {
               for (double z = -radius; z <= radius; z++) {
                  if (!(x * x + z * z > radius * radius)) {
                     Location spot = loc.clone().add(x, 0.0, z);
                     Block b = w.getBlockAt(spot);
                     if (b.getType().isAir() || b.isPassable()) {
                        if (this.random.nextDouble() < 0.25) {
                           b.setType(Material.MAGMA_BLOCK, false);
                        } else {
                           b.setType(Material.FIRE, false);
                        }
                     }
                  }
               }
            }

            w.spawnParticle(Particle.LAVA, loc.add(0.0, 0.5, 0.0), 40, 1.2, 0.6, 1.2, 0.05);
         }
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
   public void onTeleport(EntityTeleportEvent e) {
      if (e.getEntity() instanceof Enderman ender) {
         if (this.inHeatWorld(ender) && this.isVariant(ender)) {
            World w = ender.getWorld();
            Location from = e.getFrom();
            Location to = e.getTo();
            if (from != null) {
               w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, from.clone().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.02);
            }

            if (to != null) {
               w.spawnParticle(Particle.FLAME, to.clone().add(0.0, 1.0, 0.0), 30, 0.6, 0.6, 0.6, 0.02);
               double radius = 3.0;

               for (Entity ent : w.getNearbyEntities(to, radius, radius, radius)) {
                  if (ent instanceof Player p && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                     p.setFireTicks(Math.max(p.getFireTicks(), 80));
                     p.damage(1.0);
                  }
               }
            }
         }
      }
   }
}
