package io.github.mrserluiz.ethercraft;

import java.util.*;

/** Plane: full 4x5 frame/interior. Landing ground is preserved on either side. */
public final class ReturnPortalGeometry {
    public record Cell(int u, int v, int side) {}
    private ReturnPortalGeometry() {}
    public static List<Cell> edits() {
        List<Cell> cells = new ArrayList<>();
        for (var c : FrameGeometry.border()) cells.add(new Cell(c.u(), c.v(), 0));
        for (var c : FrameGeometry.interior()) cells.add(new Cell(c.u(), c.v(), 0));
        return List.copyOf(cells);
    }
    public static List<Cell> clearance() {
        List<Cell> cells = new ArrayList<>();
        for (int side : new int[]{-1, 1}) for (int u = 0; u < 2; u++)
            for (int v = 0; v < 3; v++) cells.add(new Cell(u, v, side));
        return List.copyOf(cells);
    }
}
