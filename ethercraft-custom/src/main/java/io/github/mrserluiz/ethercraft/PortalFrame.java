package io.github.mrserluiz.ethercraft;

import java.util.UUID;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Levelled;
import java.util.List;

public record PortalFrame(UUID worldId, int x, int y, int z, Axis axis, String typeId, Material frameMaterial) {
    public boolean horizontal() { return axis == Axis.Y; }
    public List<FrameGeometry.Cell> interior() { return horizontal() ? PoolGeometry.interior() : FrameGeometry.interior(); }
    public List<FrameGeometry.Cell> border() { return horizontal() ? PoolGeometry.border() : FrameGeometry.border(); }
    public String key() { return worldId + ":" + x + ":" + y + ":" + z + ":" + axis; }
    public Block block(int u, int v) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) throw new IllegalStateException("Mundo do portal não está carregado.");
        return world.getBlockAt(x + (axis == Axis.X || horizontal() ? u : 0), y + (horizontal() ? 0 : v), z + (horizontal() ? v : axis == Axis.Z ? u : 0));
    }
    public boolean valid(boolean requireActive) {
        if (Bukkit.getWorld(worldId) == null) return false;
        if (horizontal()) return PoolGeometry.valid(
            c -> block(c.u(), c.v()).getType() == frameMaterial,
            c -> PoolGeometry.flower(block(c.u(), c.v()).getRelative(0, 1, 0).getType().name()),
            c -> sourceWater(block(c.u(), c.v())),
            c -> LoadedWorlds.safeFloor(block(c.u(), c.v()).getRelative(0, -1, 0).getType()));
        if (!FrameGeometry.validBorder(c -> block(c.u(), c.v()).getType() == frameMaterial)) return false;
        for (var c : interior()) {
            Block b = block(c.u(), c.v());
            if (b.getType() == Material.NETHER_PORTAL) {
                if (!(b.getBlockData() instanceof Orientable o) || o.getAxis() != axis) return false;
            } else if (requireActive || !b.getType().isAir()) return false;
        }
        return true;
    }
    private static boolean sourceWater(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled water && water.getLevel() == 0;
    }
    public boolean contains(Block b, boolean includeBorder) {
        if (!b.getWorld().getUID().equals(worldId)) return false;
        if (horizontal()) {
            int u = b.getX() - x, v = b.getZ() - z, height = b.getY() - y;
            boolean inside = u >= 0 && u < 2 && v >= 0 && v < 2;
            if (!includeBorder) return height == 0 && inside;
            return u >= -1 && u <= 2 && v >= -1 && v <= 2
                && (inside ? height >= -1 && height <= 0 : height >= 0 && height <= 1);
        }
        int u = axis == Axis.X ? b.getX() - x : b.getZ() - z;
        int fixed = axis == Axis.X ? b.getZ() - z : b.getX() - x;
        int v = b.getY() - y;
        return fixed == 0 && (includeBorder ? u >= -1 && u <= 2 && v >= -1 && v <= 3 : u >= 0 && u < 2 && v >= 0 && v < 3);
    }
    public static PortalFrame detect(Block hit, String typeId, Material frameMaterial, PortalTypeSpec.Shape shape) {
        if (shape == PortalTypeSpec.Shape.HORIZONTAL_POOL) {
            for (int height = 0; height <= 1; height++) for (int u = -1; u <= 2; u++) for (int v = -1; v <= 2; v++) {
                PortalFrame frame = new PortalFrame(hit.getWorld().getUID(), hit.getX() - u, hit.getY() - height,
                    hit.getZ() - v, Axis.Y, typeId, frameMaterial);
                if (frame.y() > hit.getWorld().getMinHeight() && frame.y() + 2 < hit.getWorld().getMaxHeight()
                    && frame.valid(false)) return frame;
            }
            return null;
        }
        for (Axis axis : new Axis[]{Axis.X, Axis.Z})
            for (int u = -1; u <= 2; u++) for (int v = -1; v <= 3; v++) {
                PortalFrame frame = new PortalFrame(hit.getWorld().getUID(), hit.getX() - (axis == Axis.X ? u : 0),
                    hit.getY() - v, hit.getZ() - (axis == Axis.Z ? u : 0), axis, typeId, frameMaterial);
                if (frame.y() > hit.getWorld().getMinHeight() && frame.y() + 3 < hit.getWorld().getMaxHeight() && frame.valid(false)) return frame;
            }
        return null;
    }
}
