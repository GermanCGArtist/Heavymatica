package dev.heavymatica.render.cutaway;

import dev.heavymatica.core.Box3i;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Immutable section index of cut boxes. No Minecraft imports; section keys use the same bit layout as
 * {@code SectionPos.asLong} (x: 22 bits at shift 42, z: 22 bits at shift 20, y: 20 bits at shift 0).
 * Safe to read from any thread (never mutated after construction).
 */
public final class CutawayIndex {
    public static final CutawayIndex EMPTY = new CutawayIndex(new long[0], new int[0][], false, 0, 0, 0, 0, 0, 0);

    private final long[] keys;        // open-addressing table, length power of two (or empty)
    private final int[][] boxes;      // parallel to keys: flat {minX,minY,minZ,maxX,maxY,maxZ,...}; null = free slot
    private final boolean nonEmpty;
    private final int bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ;   // bounds of all boxes (fast reject)
    private volatile long[] sectionKeysCache;

    private CutawayIndex(long[] keys, int[][] boxes, boolean nonEmpty, int bMinX, int bMinY, int bMinZ,
                         int bMaxX, int bMaxY, int bMaxZ) {
        this.keys = keys;
        this.boxes = boxes;
        this.nonEmpty = nonEmpty;
        this.bMinX = bMinX; this.bMinY = bMinY; this.bMinZ = bMinZ;
        this.bMaxX = bMaxX; this.bMaxY = bMaxY; this.bMaxZ = bMaxZ;
    }

    /** Same as {@code SectionPos.asLong(sx, sy, sz)}. */
    public static long sectionKey(int sx, int sy, int sz) {
        long l = 0L;
        l |= ((long) sx & 4194303L) << 42;
        l |= ((long) sy & 1048575L);
        l |= ((long) sz & 4194303L) << 20;
        return l;
    }

    public static int keyX(long k) { return (int) (k >> 42); }
    public static int keyY(long k) { return (int) (k << 44 >> 44); }
    public static int keyZ(long k) { return (int) (k << 22 >> 42); }

    /** Section keys touched by one box. */
    public static long[] sectionsOf(Box3i b) {
        int x0 = b.minX() >> 4, x1 = b.maxX() >> 4;
        int y0 = b.minY() >> 4, y1 = b.maxY() >> 4;
        int z0 = b.minZ() >> 4, z1 = b.maxZ() >> 4;
        long n = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (n > 4_000_000L) throw new IllegalArgumentException("box touches too many sections: " + n);
        long[] out = new long[(int) n];
        int i = 0;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) out[i++] = sectionKey(x, y, z);
        return out;
    }

    public static CutawayIndex of(Collection<Box3i> input) {
        if (input.isEmpty()) return EMPTY;
        int cap = 16;
        long[] tk = new long[cap];
        int[][] tv = new int[cap][];
        int[] tn = new int[cap];     // ints used per slot
        boolean[] used = new boolean[cap];
        int size = 0;
        int mnX = Integer.MAX_VALUE, mnY = Integer.MAX_VALUE, mnZ = Integer.MAX_VALUE;
        int mxX = Integer.MIN_VALUE, mxY = Integer.MIN_VALUE, mxZ = Integer.MIN_VALUE;
        for (Box3i b : input) {
            mnX = Math.min(mnX, b.minX()); mnY = Math.min(mnY, b.minY()); mnZ = Math.min(mnZ, b.minZ());
            mxX = Math.max(mxX, b.maxX()); mxY = Math.max(mxY, b.maxY()); mxZ = Math.max(mxZ, b.maxZ());
            for (long k : sectionsOf(b)) {
                if ((size + 1) * 2 > cap) {
                    int ncap = cap * 2;
                    long[] nk = new long[ncap];
                    int[][] nv = new int[ncap][];
                    int[] nn = new int[ncap];
                    boolean[] nu = new boolean[ncap];
                    for (int s = 0; s < cap; s++) {
                        if (!used[s]) continue;
                        int p = slot(tk[s], ncap);
                        while (nu[p]) p = (p + 1) & (ncap - 1);
                        nk[p] = tk[s]; nv[p] = tv[s]; nn[p] = tn[s]; nu[p] = true;
                    }
                    tk = nk; tv = nv; tn = nn; used = nu; cap = ncap;
                }
                int p = slot(k, cap);
                while (used[p] && tk[p] != k) p = (p + 1) & (cap - 1);
                if (!used[p]) { used[p] = true; tk[p] = k; tv[p] = new int[6]; tn[p] = 0; size++; }
                if (tn[p] + 6 > tv[p].length) tv[p] = Arrays.copyOf(tv[p], tv[p].length * 2);
                int[] a = tv[p];
                int o = tn[p];
                a[o] = b.minX(); a[o + 1] = b.minY(); a[o + 2] = b.minZ();
                a[o + 3] = b.maxX(); a[o + 4] = b.maxY(); a[o + 5] = b.maxZ();
                tn[p] = o + 6;
            }
        }
        int[][] vals = new int[cap][];
        for (int s = 0; s < cap; s++) if (used[s]) vals[s] = Arrays.copyOf(tv[s], tn[s]);
        return new CutawayIndex(tk, vals, true, mnX, mnY, mnZ, mxX, mxY, mxZ);
    }

    private static int slot(long k, int cap) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & (cap - 1);
    }

    public boolean isEmpty() { return !nonEmpty; }

    /** True if the block position lies inside any cut box. Allocation-free; thread-safe. */
    public boolean cut(int x, int y, int z) {
        if (!nonEmpty || x < bMinX || x > bMaxX || y < bMinY || y > bMaxY || z < bMinZ || z > bMaxZ) return false;
        long k = sectionKey(x >> 4, y >> 4, z >> 4);
        int mask = keys.length - 1;
        int p = slot(k, keys.length);
        int[] a;
        while ((a = boxes[p]) != null) {
            if (keys[p] == k) {
                for (int i = 0; i < a.length; i += 6) {
                    if (x >= a[i] && x <= a[i + 3] && y >= a[i + 1] && y <= a[i + 4] && z >= a[i + 2] && z <= a[i + 5]) return true;
                }
                return false;
            }
            p = (p + 1) & mask;
        }
        return false;
    }

    /** All section keys that hold at least one cut box (distinct, unsorted). */
    public long[] sectionKeys() {
        long[] c = sectionKeysCache;
        if (c == null) {
            int n = 0;
            for (int[] a : boxes) if (a != null) n++;
            c = new long[n];
            int i = 0;
            for (int p = 0; p < boxes.length; p++) if (boxes[p] != null) c[i++] = keys[p];
            sectionKeysCache = c;
        }
        return c;
    }

    /** Distinct sections of both snapshots (the sections whose mesh may change). */
    public static long[] dirtyUnion(CutawayIndex a, CutawayIndex b) {
        long[] x = a.sectionKeys(), y = b.sectionKeys();
        long[] r = Arrays.copyOf(x, x.length + y.length);
        System.arraycopy(y, 0, r, x.length, y.length);
        return Arrays.stream(r).distinct().toArray();
    }

    /** Distinct sections of {@code keys} plus their 26 neighbours (faces next to a hole re-mesh too). */
    public static long[] withNeighbors(long[] keys) {
        HashSet<Long> set = new HashSet<>();
        for (long k : keys) {
            int x = keyX(k), y = keyY(k), z = keyZ(k);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) set.add(sectionKey(x + dx, y + dy, z + dz));
        }
        long[] out = new long[set.size()];
        int i = 0;
        for (long k : set) out[i++] = k;
        return out;
    }
}
