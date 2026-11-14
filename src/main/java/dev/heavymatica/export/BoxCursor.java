package dev.heavymatica.export;

import dev.heavymatica.core.Box3i;

import java.util.List;

/**
 * Resumable iteration over every cell of a list of boxes (x fastest, then z, then y, box after box). Visits each cell
 * of each box exactly once; cells covered by two overlapping boxes are visited once per box (not deduplicated).
 * Pure, no Minecraft imports.
 */
public final class BoxCursor {
    @FunctionalInterface
    public interface CellVisitor {
        void visit(int x, int y, int z);
    }

    private final List<Box3i> boxes;
    private int box;
    private int x, y, z;
    private boolean started;

    public BoxCursor(List<Box3i> boxes) {
        this.boxes = List.copyOf(boxes);
    }

    /** The box being visited (valid inside the visitor). */
    public Box3i current() {
        return boxes.get(box);
    }

    public boolean done() {
        return box >= boxes.size();
    }

    /** Visits up to {@code max} cells, returns how many were visited (less than max only when finished). */
    public int advance(int max, CellVisitor v) {
        int n = 0;
        while (n < max && box < boxes.size()) {
            Box3i b = boxes.get(box);
            if (!started) {
                x = b.minX(); y = b.minY(); z = b.minZ();
                started = true;
            }
            while (n < max) {
                v.visit(x, y, z);
                n++;
                if (++x > b.maxX()) {
                    x = b.minX();
                    if (++z > b.maxZ()) {
                        z = b.minZ();
                        if (++y > b.maxY()) {
                            box++;
                            started = false;
                            break;
                        }
                    }
                }
            }
        }
        return n;
    }

    /**
     * The 1-block shell around a box without the face above it, as up to 5 disjoint boxes (the edges and corners
     * below / beside are included).
     */
    public static List<Box3i> shellWithoutTop(Box3i b) {
        return List.of(
                new Box3i(b.minX() - 1, b.minY() - 1, b.minZ() - 1, b.minX() - 1, b.maxY(), b.maxZ() + 1),
                new Box3i(b.maxX() + 1, b.minY() - 1, b.minZ() - 1, b.maxX() + 1, b.maxY(), b.maxZ() + 1),
                new Box3i(b.minX(), b.minY() - 1, b.minZ() - 1, b.maxX(), b.minY() - 1, b.maxZ() + 1),
                new Box3i(b.minX(), b.minY(), b.minZ() - 1, b.maxX(), b.maxY(), b.minZ() - 1),
                new Box3i(b.minX(), b.minY(), b.maxZ() + 1, b.maxX(), b.maxY(), b.maxZ() + 1));
    }

    /** The layer directly above a box (same footprint). */
    public static Box3i above(Box3i b) {
        return new Box3i(b.minX(), b.maxY() + 1, b.minZ(), b.maxX(), b.maxY() + 1, b.maxZ());
    }

    /** True when the cell is on a face of the box. */
    public static boolean onBoundary(Box3i b, int x, int y, int z) {
        return x == b.minX() || x == b.maxX() || y == b.minY() || y == b.maxY() || z == b.minZ() || z == b.maxZ();
    }
}
