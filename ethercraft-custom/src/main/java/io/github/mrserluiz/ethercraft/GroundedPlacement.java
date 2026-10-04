package io.github.mrserluiz.ethercraft;

import java.util.*;
import java.util.function.*;

/** Ground/support checks are separate from editable blocks: never manufacture a floating floor. */
final class GroundedPlacement {
    record Cell(int u, int v, int height) {}
    static List<Cell> support(boolean horizontal) {
        var cells = new ArrayList<Cell>();
        if (horizontal) {
            for (int u = -1; u <= 2; u++) for (int v = -1; v <= 2; v++) {
                cells.add(new Cell(u, v, 0)); cells.add(new Cell(u, v, -1));
            }
        } else {
            for (int u = 0; u < 2; u++) cells.add(new Cell(u, 0, -1));
        }
        return List.copyOf(cells);
    }
    static boolean grounded(boolean horizontal, Predicate<Cell> safeSolid) { return support(horizontal).stream().allMatch(safeSolid); }
    static List<Integer> heights(int bottom, int top, int preferred, IntPredicate anchorSolid) {
        var heights = new ArrayList<Integer>();
        for (int y = bottom; y <= top; y++) if (anchorSolid.test(y)) heights.add(y);
        heights.sort(Comparator.<Integer>comparingLong(y -> Math.abs((long)y - preferred)).thenComparing(Comparator.reverseOrder()));
        return heights;
    }
}
