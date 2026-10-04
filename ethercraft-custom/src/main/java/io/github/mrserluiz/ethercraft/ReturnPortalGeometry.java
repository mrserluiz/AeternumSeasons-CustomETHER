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
    // Arrival inside the portal is safe; extra open land on BOTH sides is not required.
    public static List<Cell> clearance() { return List.of(); }
}
