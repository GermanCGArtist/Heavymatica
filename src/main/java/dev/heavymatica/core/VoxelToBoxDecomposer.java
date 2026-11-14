package dev.heavymatica.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exact, non-overlapping cover of a voxel grid by boxes (greedy: grow along X, then Z, then Y). Grid-local coordinates. */
public final class VoxelToBoxDecomposer {
    private VoxelToBoxDecomposer() {}

    public static List<Box3i> decompose(VoxelGrid g) {
        int sx = g.sizeX(), sy = g.sizeY(), sz = g.sizeZ();
        List<Box3i> out = new ArrayList<>();
        if (sx <= 0 || sy <= 0 || sz <= 0) return out;
        // rem[i] = filled and not yet claimed; index = (y * sz + z) * sx + x
        boolean[] rem = new boolean[Math.multiplyExact(Math.multiplyExact(sx, sy), sz)];
        int i = 0;
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) rem[i++] = g.filled(x, y, z);
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                int row = (y * sz + z) * sx;
                for (int x = 0; x < sx; x++) {
                    if (!rem[row + x]) continue;
                    int x2 = x;
                    while (x2 + 1 < sx && rem[row + x2 + 1]) x2++;
                    int z2 = z;
                    while (z2 + 1 < sz && rowFree(rem, (y * sz + z2 + 1) * sx, x, x2)) z2++;
                    int y2 = y;
                    outer:
                    while (y2 + 1 < sy) {
                        for (int zz = z; zz <= z2; zz++) {
                            if (!rowFree(rem, ((y2 + 1) * sz + zz) * sx, x, x2)) break outer;
                        }
                        y2++;
                    }
                    for (int yy = y; yy <= y2; yy++)
                        for (int zz = z; zz <= z2; zz++) {
                            int base = (yy * sz + zz) * sx;
                            Arrays.fill(rem, base + x, base + x2 + 1, false);
                        }
                    out.add(new Box3i(x, y, z, x2, y2, z2));
                    x = x2;
                }
            }
        }
        return out;
    }

    private static boolean rowFree(boolean[] rem, int base, int x1, int x2) {
        for (int x = x1; x <= x2; x++) if (!rem[base + x]) return false;
        return true;
    }
}
