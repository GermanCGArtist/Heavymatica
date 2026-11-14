package dev.heavymatica.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VoxelToBoxDecomposerTest {
    interface Cell { boolean at(int x, int y, int z); }

    private static VoxelGrid grid(int sx, int sy, int sz, Cell c) {
        return new VoxelGrid() {
            public int sizeX() { return sx; }
            public int sizeY() { return sy; }
            public int sizeZ() { return sz; }
            public boolean filled(int x, int y, int z) { return c.at(x, y, z); }
        };
    }

    private static List<Box3i> checkExact(VoxelGrid g) {
        List<Box3i> boxes = VoxelToBoxDecomposer.decompose(g);
        int[][][] count = new int[g.sizeX()][g.sizeY()][g.sizeZ()];
        for (Box3i b : boxes)
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int y = b.minY(); y <= b.maxY(); y++)
                    for (int z = b.minZ(); z <= b.maxZ(); z++) count[x][y][z]++;
        for (int x = 0; x < g.sizeX(); x++)
            for (int y = 0; y < g.sizeY(); y++)
                for (int z = 0; z < g.sizeZ(); z++)
                    assertEquals(g.filled(x, y, z) ? 1 : 0, count[x][y][z], "cell " + x + "," + y + "," + z);
        for (int i = 0; i < boxes.size(); i++)
            for (int j = i + 1; j < boxes.size(); j++) assertFalse(boxes.get(i).intersects(boxes.get(j)));
        return boxes;
    }

    @Test
    void fullBoxIsOneBox() {
        assertEquals(List.of(new Box3i(0, 0, 0, 6, 4, 8)), checkExact(grid(7, 5, 9, (x, y, z) -> true)));
    }

    @Test
    void emptyIsZero() {
        assertEquals(0, VoxelToBoxDecomposer.decompose(grid(6, 6, 6, (x, y, z) -> false)).size());
        assertEquals(0, VoxelToBoxDecomposer.decompose(grid(0, 0, 0, (x, y, z) -> true)).size());
    }

    @Test
    void hollowCube() {
        int n = 10;
        checkExact(grid(n, n, n, (x, y, z) -> x == 0 || y == 0 || z == 0 || x == n - 1 || y == n - 1 || z == n - 1));
    }

    @Test
    void sphere() {
        int r = 8, n = 2 * r + 1;
        List<Box3i> b = checkExact(grid(n, n, n, (x, y, z) -> (x - r) * (x - r) + (y - r) * (y - r) + (z - r) * (z - r) <= r * r));
        assertFalse(b.isEmpty());
    }

    @Test
    void lShapeIsTwoBoxes() {
        assertEquals(2, checkExact(grid(10, 3, 10, (x, y, z) -> z < 5 || x < 5)).size());
    }

    @Test
    void random() {
        Random rnd = new Random(42);
        boolean[][][] f = new boolean[20][20][20];
        for (boolean[][] a : f) for (boolean[] c : a) for (int i = 0; i < c.length; i++) c[i] = rnd.nextInt(100) < 60;
        checkExact(grid(20, 20, 20, (x, y, z) -> f[x][y][z]));
    }

    @Test
    void bigGridsAreFast() {
        long t0 = System.nanoTime();
        List<Box3i> full = VoxelToBoxDecomposer.decompose(grid(256, 256, 256, (x, y, z) -> true));
        long t1 = System.nanoTime();
        assertEquals(1, full.size());
        int r = 64, n = 2 * r + 1;
        long t2 = System.nanoTime();
        List<Box3i> s = VoxelToBoxDecomposer.decompose(grid(n, n, n, (x, y, z) -> (x - r) * (x - r) + (y - r) * (y - r) + (z - r) * (z - r) <= r * r));
        long t3 = System.nanoTime();
        System.out.println("decompose 256^3 full: " + (t1 - t0) / 1_000_000 + " ms; r=64 sphere: " + (t3 - t2) / 1_000_000 + " ms, " + s.size() + " boxes");
        assertTrue((t1 - t0) / 1_000_000 < 5000);
        assertTrue((t3 - t2) / 1_000_000 < 5000);
    }
}
