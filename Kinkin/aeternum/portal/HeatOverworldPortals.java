package Kinkin.aeternum.portal;

import Kinkin.aeternum.AeternumSeasonsPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.jetbrains.annotations.Nullable;

public final class HeatOverworldPortals implements Listener {
   private static final String HEAT_WORLD_NAME = "aeternum_heat";
   private static final int SAFE_SPACE_RADIUS = 2;
   private final Map<HeatOverworldPortals.PortalLocation, HeatOverworldPortals.PortalLocation> portalConnections = new ConcurrentHashMap<>();
   private final AeternumSeasonsPlugin plugin;
   private final File portalDataFile;

   public HeatOverworldPortals(AeternumSeasonsPlugin plugin) {
      this.plugin = plugin;
      this.portalDataFile = new File(plugin.getDataFolder(), "portal_links.yml");
      this.loadPortalLinks();
   }

   public void register() {
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
   }

   public void unregister() {
      HandlerList.unregisterAll(this);
      if (!this.plugin.shouldLetWorldsHandlePortals()) {
         this.savePortalLinks();
      }
   }

   private void loadPortalLinks() {
      this.portalConnections.clear();
      if (this.portalDataFile.exists()) {
         FileConfiguration config = YamlConfiguration.loadConfiguration(this.portalDataFile);

         for (String key : config.getKeys(false)) {
            String destinationString = config.getString(key);
            if (destinationString != null && !destinationString.isBlank()) {
               HeatOverworldPortals.PortalLocation source = this.parsePortalString(key);
               HeatOverworldPortals.PortalLocation destination = this.parsePortalString(destinationString);
               if (source != null && destination != null) {
                  this.portalConnections.put(source, destination);
               }
            }
         }

         this.plugin.getLogger().info("[HeatOverworldPortals] Cargados " + this.portalConnections.size() + " enlaces de portal.");
      }
   }

   private void savePortalLinks() {
      FileConfiguration config = new YamlConfiguration();

      for (Entry<HeatOverworldPortals.PortalLocation, HeatOverworldPortals.PortalLocation> entry : this.portalConnections.entrySet()) {
         config.set(this.formatPortalString(entry.getKey()), this.formatPortalString(entry.getValue()));
      }

      try {
         config.save(this.portalDataFile);
         this.plugin.getLogger().info("[HeatOverworldPortals] Guardados " + this.portalConnections.size() + " enlaces de portal.");
      } catch (IOException e) {
         this.plugin.getLogger().severe("[HeatOverworldPortals] No se pudo guardar portal_links.yml: " + e.getMessage());
      }
   }

   private String formatPortalString(HeatOverworldPortals.PortalLocation loc) {
      return loc.x + "_" + loc.y + "_" + loc.z + "_" + loc.worldName;
   }

   @Nullable
   private HeatOverworldPortals.PortalLocation parsePortalString(String s) {
      String[] parts = s.split("_", 4);
      if (parts.length != 4) {
         return null;
      }

      try {
         int x = Integer.parseInt(parts[0]);
         int y = Integer.parseInt(parts[1]);
         int z = Integer.parseInt(parts[2]);
         String worldName = parts[3];
         return new HeatOverworldPortals.PortalLocation(worldName, x, y, z);
      } catch (NumberFormatException ex) {
         return null;
      }
   }

   private HeatOverworldPortals.PortalLocation toPortalLocation(Location loc) {
      return new HeatOverworldPortals.PortalLocation(Objects.requireNonNull(loc.getWorld()).getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
   }

   @Nullable
   public Location findLinkedPortal(Location from) {
      int searchRadius = 2;
      World world = from.getWorld();
      if (world == null) {
         return null;
      }

      for (int dx = -searchRadius; dx <= searchRadius; dx++) {
         for (int dy = -searchRadius; dy <= searchRadius; dy++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
               HeatOverworldPortals.PortalLocation key = new HeatOverworldPortals.PortalLocation(
                  world.getName(), from.getBlockX() + dx, from.getBlockY() + dy, from.getBlockZ() + dz
               );
               HeatOverworldPortals.PortalLocation dest = this.portalConnections.get(key);
               if (dest != null) {
                  World destWorld = Bukkit.getWorld(dest.worldName);
                  if (destWorld != null) {
                     return new Location(destWorld, dest.x + 0.5, dest.y, dest.z + 0.5);
                  }
               }
            }
         }
      }

      return null;
   }

   public void registerPortalLink(Location loc1, Location loc2) {
      HeatOverworldPortals.PortalLocation p1 = this.toPortalLocation(loc1);
      HeatOverworldPortals.PortalLocation p2 = this.toPortalLocation(loc2);
      this.portalConnections.put(p1, p2);
      this.portalConnections.put(p2, p1);
      this.savePortalLinks();
   }

   public void clearPortalLinks() {
      this.portalConnections.clear();
      this.savePortalLinks();
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onPlayerPortal(PlayerPortalEvent event) {
   }

   private Location createSafePortalExit(Location portalLocation) {
      World world = portalLocation.getWorld();
      if (world == null) {
         return portalLocation;
      }

      int baseX = portalLocation.getBlockX();
      int baseY = portalLocation.getBlockY();
      int baseZ = portalLocation.getBlockZ();

      for (int yOffset = 0; yOffset <= 2; yOffset++) {
         for (int xOffset = -2; xOffset <= 2; xOffset++) {
            for (int zOffset = -2; zOffset <= 2; zOffset++) {
               int checkX = baseX + xOffset;
               int checkY = baseY + yOffset;
               int checkZ = baseZ + zOffset;
               Block block = world.getBlockAt(checkX, checkY, checkZ);
               Block blockAbove = world.getBlockAt(checkX, checkY + 1, checkZ);
               Block blockBelow = world.getBlockAt(checkX, checkY - 1, checkZ);
               if (this.isSafeSpace(block, blockAbove, blockBelow)) {
                  return new Location(world, checkX + 0.5, checkY, checkZ + 0.5);
               }
            }
         }
      }

      return this.createSafeSpace(portalLocation);
   }

   private boolean isSafeSpace(Block block, Block blockAbove, Block blockBelow) {
      return block.getType().isAir() && blockAbove.getType().isAir() && !blockBelow.getType().isAir() && blockBelow.getType().isSolid();
   }

   private Location createSafeSpace(Location portalLocation) {
      World world = portalLocation.getWorld();
      if (world == null) {
         return portalLocation;
      }

      int baseX = portalLocation.getBlockX();
      int baseY = portalLocation.getBlockY();
      int baseZ = portalLocation.getBlockZ();

      for (int yOffset = 0; yOffset <= 2; yOffset++) {
         for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
               int checkX = baseX + xOffset;
               int checkY = baseY + yOffset;
               int checkZ = baseZ + zOffset;
               if (this.canCreateSafeSpace(world, checkX, checkY, checkZ)) {
                  this.clearSpace(world, checkX, checkY, checkZ);
                  this.ensureSolidGround(world, checkX, checkY - 1, checkZ);
                  world.playSound(new Location(world, checkX, checkY, checkZ), Sound.BLOCK_STONE_BREAK, 0.5F, 1.0F);
                  world.spawnParticle(
                     Particle.BLOCK, new Location(world, checkX + 0.5, checkY, checkZ + 0.5), 10, 0.3, 0.3, 0.3, 0.1, Material.STONE.createBlockData()
                  );
                  return new Location(world, checkX + 0.5, checkY, checkZ + 0.5);
               }
            }
         }
      }

      return portalLocation;
   }

   private boolean canCreateSafeSpace(World world, int x, int y, int z) {
      Block block = world.getBlockAt(x, y, z);
      Block blockAbove = world.getBlockAt(x, y + 1, z);
      return block.getType() != Material.BEDROCK
         && block.getType() != Material.OBSIDIAN
         && block.getType() != Material.WATER
         && block.getType() != Material.LAVA;
   }

   private void clearSpace(World world, int x, int y, int z) {
      for (int dy = 0; dy < 2; dy++) {
         Block block = world.getBlockAt(x, y + dy, z);
         if (!block.getType().isAir()) {
            block.setType(Material.AIR);
         }
      }
   }

   private void ensureSolidGround(World world, int x, int y, int z) {
      Block ground = world.getBlockAt(x, y, z);
      if (ground.getType().isAir() || !ground.getType().isSolid()) {
         ground.setType(Material.STONE);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
   public void onInteract(PlayerInteractEvent e) {
      if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
         if (e.getClickedBlock() != null) {
            Material item = e.getItem() != null ? e.getItem().getType() : Material.AIR;
            if (item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE) {
               Block clicked = e.getClickedBlock();
               BlockFace face = e.getBlockFace();
               Block inside = clicked.getRelative(face);
               if (inside.getType().isAir()) {
                  HeatOverworldPortals.HeatPortalShape shape = HeatOverworldPortals.HeatPortalShape.detect(inside);
                  if (shape != null) {
                     e.setCancelled(true);
                     this.lightPortal(shape);
                  }
               }
            }
         }
      }
   }

   private void lightPortal(HeatOverworldPortals.HeatPortalShape shape) {
      World w = shape.world;
      Orientable data = (Orientable)Material.NETHER_PORTAL.createBlockData();
      data.setAxis(shape.axis);

      for (int x = 0; x < shape.width; x++) {
         for (int y = 0; y < shape.height; y++) {
            Block b = shape.getInteriorBlock(x, y);
            b.setBlockData(data, false);
         }
      }

      w.playSound(shape.getCenter(), Sound.BLOCK_PORTAL_TRAVEL, 1.0F, 1.0F);
      w.spawnParticle(Particle.PORTAL, shape.getCenter(), 60, 0.5, 1.0, 0.5, 0.1);
   }

   public boolean isHeatWorld(World world) {
      return world != null && world.getName().equalsIgnoreCase("aeternum_heat");
   }

   public boolean isWartPortal(Block portalBlock) {
      for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
         Block b = portalBlock.getRelative(face);
         if (b.getType() == Material.NETHER_WART_BLOCK) {
            return true;
         }
      }

      return false;
   }

   private static final class HeatPortalShape {
      final World world;
      final int x0;
      final int y0;
      final int z0;
      final int width;
      final int height;
      final Axis axis;

      private HeatPortalShape(World world, int x0, int y0, int z0, int width, int height, Axis axis) {
         this.world = world;
         this.x0 = x0;
         this.y0 = y0;
         this.z0 = z0;
         this.width = width;
         this.height = height;
         this.axis = axis;
      }

      @Nullable
      static HeatOverworldPortals.HeatPortalShape detect(Block inside) {
         if (!inside.getType().isAir() && inside.getType() != Material.FIRE) {
            return null;
         }

         World w = inside.getWorld();
         int cx = inside.getX();
         int cy = inside.getY();
         int cz = inside.getZ();
         HeatOverworldPortals.HeatPortalShape shape = detectWithOrientation(w, cx, cy, cz, Axis.X);
         return shape != null ? shape : detectWithOrientation(w, cx, cy, cz, Axis.Z);
      }

      @Nullable
      private static HeatOverworldPortals.HeatPortalShape detectWithOrientation(World w, int cx, int cy, int cz, Axis axis) {
         int width = 2;
         int height = 3;
         if (axis == Axis.X) {
            int z0 = cz;

            for (int x0 = cx - 1; x0 <= cx; x0++) {
               for (int y0 = cy - 2; y0 <= cy; y0++) {
                  if (cx >= x0 && cx < x0 + width && cy >= y0 && cy < y0 + height && isValidFrameX(w, x0, y0, z0, width, height)) {
                     return new HeatOverworldPortals.HeatPortalShape(w, x0, y0, z0, width, height, Axis.X);
                  }
               }
            }
         } else {
            int x0 = cx;

            for (int z0 = cz - 1; z0 <= cz; z0++) {
               for (int y0 = cy - 2; y0 <= cy; y0++) {
                  if (cz >= z0 && cz < z0 + width && cy >= y0 && cy < y0 + height && isValidFrameZ(w, x0, y0, z0, width, height)) {
                     return new HeatOverworldPortals.HeatPortalShape(w, x0, y0, z0, width, height, Axis.Z);
                  }
               }
            }
         }

         return null;
      }

      private static boolean isValidFrameX(World w, int x0, int y0, int z0, int width, int height) {
         for (int x = -1; x <= width; x++) {
            for (int y = -1; y <= height; y++) {
               Block b = w.getBlockAt(x0 + x, y0 + y, z0);
               boolean frame = x == -1 || x == width || y == -1 || y == height;
               if (frame) {
                  if (b.getType() != Material.NETHER_WART_BLOCK) {
                     return false;
                  }
               } else if (!b.getType().isAir() && b.getType() != Material.FIRE) {
                  return false;
               }
            }
         }

         return true;
      }

      private static boolean isValidFrameZ(World w, int x0, int y0, int z0, int width, int height) {
         for (int z = -1; z <= width; z++) {
            for (int y = -1; y <= height; y++) {
               Block b = w.getBlockAt(x0, y0 + y, z0 + z);
               boolean frame = z == -1 || z == width || y == -1 || y == height;
               if (frame) {
                  if (b.getType() != Material.NETHER_WART_BLOCK) {
                     return false;
                  }
               } else if (!b.getType().isAir() && b.getType() != Material.FIRE) {
                  return false;
               }
            }
         }

         return true;
      }

      Block getInteriorBlock(int dx, int dy) {
         return this.axis == Axis.X ? this.world.getBlockAt(this.x0 + dx, this.y0 + dy, this.z0) : this.world.getBlockAt(this.x0, this.y0 + dy, this.z0 + dx);
      }

      Location getCenter() {
         double cx;
         double cz;
         if (this.axis == Axis.X) {
            cx = this.x0 + this.width / 2.0 + 0.5;
            cz = this.z0 + 0.5;
         } else {
            cx = this.x0 + 0.5;
            cz = this.z0 + this.width / 2.0 + 0.5;
         }

         double cy = this.y0 + 1.0;
         return new Location(this.world, cx, cy, cz);
      }
   }

   private static final class PortalLocation {
      final String worldName;
      final int x;
      final int y;
      final int z;

      private PortalLocation(String worldName, int x, int y, int z) {
         this.worldName = worldName;
         this.x = x;
         this.y = y;
         this.z = z;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return !(obj instanceof HeatOverworldPortals.PortalLocation other)
               ? false
               : this.x == other.x && this.y == other.y && this.z == other.z && Objects.equals(this.worldName, other.worldName);
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.worldName, this.x, this.y, this.z);
      }
   }
}
