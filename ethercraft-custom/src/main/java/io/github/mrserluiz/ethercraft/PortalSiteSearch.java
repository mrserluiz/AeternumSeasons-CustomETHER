package io.github.mrserluiz.ethercraft;
import java.util.*;
/** Closest horizontal sites first; a bounded search continues beyond the initial radius. */
final class PortalSiteSearch {
    record Offset(int x, int z) { long distanceSquared() { return (long)x*x + (long)z*z; } }
    static List<Offset> offsets(int radius) {
        radius = Math.max(0, Math.min(128, radius));
        var result = new ArrayList<Offset>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            if ((long)x*x + (long)z*z <= (long)radius*radius) result.add(new Offset(x,z));
        result.sort(Comparator.comparingLong(Offset::distanceSquared).thenComparingInt(Offset::x).thenComparingInt(Offset::z));
        return result;
    }
    static int radius(int initial, int maximum) { return Math.max(0, Math.min(128, Math.max(initial, maximum))); }
}
