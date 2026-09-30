package io.github.mrserluiz.ethercraft;

import java.util.ArrayList;
import java.util.List;

/** Fixed 2x3 interior, full 4x5 frame including corners. Independent of Bukkit. */
public final class FrameGeometry {
    public record Cell(int u, int v) {}
    private FrameGeometry() {}
    public static List<Cell> interior() {
        List<Cell> cells = new ArrayList<>();
        for (int u = 0; u < 2; u++) for (int v = 0; v < 3; v++) cells.add(new Cell(u, v));
        return List.copyOf(cells);
    }
    public static List<Cell> border() {
        List<Cell> cells = new ArrayList<>();
        for (int u = -1; u <= 2; u++) for (int v = -1; v <= 3; v++)
            if (u == -1 || u == 2 || v == -1 || v == 3) cells.add(new Cell(u, v));
        return List.copyOf(cells);
    }
}
