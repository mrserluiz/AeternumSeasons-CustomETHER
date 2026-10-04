package io.github.mrserluiz.ethercraft;

import java.util.*;

/** A 2x2 source-water pool with a complete 4x4 rim and single-block flowers. */
public final class PoolGeometry {
    private PoolGeometry() {}
    private static final Set<String> FLOWERS = Set.of("DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM",
        "AZURE_BLUET", "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP", "PINK_TULIP", "OXEYE_DAISY",
        "CORNFLOWER", "LILY_OF_THE_VALLEY", "TORCHFLOWER", "OPEN_EYEBLOSSOM", "CLOSED_EYEBLOSSOM");
    public static List<String> returnFlowers() {
        return List.of("DANDELION", "POPPY", "ALLIUM", "AZURE_BLUET", "CORNFLOWER", "OXEYE_DAISY");
    }
    // Bush survival requires brightness or skylight; placing with physics disabled alone is insufficient.
    public static boolean flowerLight(int sky, int brightness) { return sky >= 5 || brightness >= 8; }
    public static boolean flower(String material) { return FLOWERS.contains(material); }
    public static List<FrameGeometry.Cell> interior() {
        return List.of(new FrameGeometry.Cell(0, 0), new FrameGeometry.Cell(1, 0),
            new FrameGeometry.Cell(0, 1), new FrameGeometry.Cell(1, 1));
    }
    public static List<FrameGeometry.Cell> border() {
        List<FrameGeometry.Cell> cells = new ArrayList<>();
        for (int u = -1; u <= 2; u++) for (int v = -1; v <= 2; v++)
            if (u == -1 || u == 2 || v == -1 || v == 2) cells.add(new FrameGeometry.Cell(u, v));
        return List.copyOf(cells);
    }
    public static boolean valid(java.util.function.Predicate<FrameGeometry.Cell> rim,
                                java.util.function.Predicate<FrameGeometry.Cell> flowers,
                                java.util.function.Predicate<FrameGeometry.Cell> water,
                                java.util.function.Predicate<FrameGeometry.Cell> floor) {
        return border().stream().allMatch(c -> rim.test(c) && flowers.test(c))
            && interior().stream().allMatch(c -> water.test(c) && floor.test(c));
    }
    public record Cell(int u, int v, int height) {}
    public enum Part { FRAME, WATER, FLOWER }
    public record Placement(Cell cell, Part part) {}
    public static List<Placement> constructionPlan() {
        var plan = new ArrayList<Placement>();
        for (var c : border()) plan.add(new Placement(new Cell(c.u(), c.v(), 0), Part.FRAME));
        for (var c : interior()) plan.add(new Placement(new Cell(c.u(), c.v(), 0), Part.WATER));
        for (var c : border()) plan.add(new Placement(new Cell(c.u(), c.v(), 1), Part.FLOWER));
        return List.copyOf(plan);
    }
    public static List<Cell> edits() {
        List<Cell> cells = new ArrayList<>();
        for (var c : border()) { cells.add(new Cell(c.u(), c.v(), 0)); cells.add(new Cell(c.u(), c.v(), 1)); }
        for (var c : interior()) cells.add(new Cell(c.u(), c.v(), 0));
        return List.copyOf(cells);
    }
    public static List<Cell> clearance() {
        List<Cell> cells = new ArrayList<>();
        for (var c : border()) for (int h = 2; h <= 4; h++) cells.add(new Cell(c.u(), c.v(), h));
        for (var c : interior()) for (int h = 1; h <= 3; h++) cells.add(new Cell(c.u(), c.v(), h));
        return List.copyOf(cells);
    }
}
