package dev.heavymatica.render;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.render.cutaway.CutawayIndex;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CutawayIndexTest {
    private static Set<Long> set(long[] a) { return Arrays.stream(a).boxed().collect(Collectors.toSet()); }

    @Test
    void sectionKeyMatchesSectionPosLayout() {
        // hand computed: x<<42 | z<<20 | y (masked)
        assertEquals(0L, CutawayIndex.sectionKey(0, 0, 0));
        assertEquals(1L << 42, CutawayIndex.sectionKey(1, 0, 0));
        assertEquals(1L << 20, CutawayIndex.sectionKey(0, 0, 1));
        assertEquals(1L, CutawayIndex.sectionKey(0, 1, 0));
        assertEquals((4194303L << 42) | (4194303L << 20) | 1048575L, CutawayIndex.sectionKey(-1, -1, -1));
        assertEquals((2L << 42) | (3L << 20) | 1048572L, CutawayIndex.sectionKey(2, -4, 3));
    }

    @Test
    void keyDecodeRoundTrips() {
        int[][] cases = {{0, 0, 0}, {-1, -4, 7}, {1000, 19, -2000}, {-33554, -64, 33554}};
        for (int[] c : cases) {
            long k = CutawayIndex.sectionKey(c[0], c[1], c[2]);
            assertEquals(c[0], CutawayIndex.keyX(k));
            assertEquals(c[1], CutawayIndex.keyY(k));
            assertEquals(c[2], CutawayIndex.keyZ(k));
        }
    }

    @Test
    void sectionsTouchedByBox() {
        // x 10..20 -> sections 0,1 ; y -1..0 -> -1,0 ; z 5..5 -> 0
        long[] s = CutawayIndex.sectionsOf(Box3i.of(10, -1, 5, 20, 0, 5));
        assertEquals(4, s.length);
        assertEquals(Set.of(CutawayIndex.sectionKey(0, -1, 0), CutawayIndex.sectionKey(1, -1, 0),
                CutawayIndex.sectionKey(0, 0, 0), CutawayIndex.sectionKey(1, 0, 0)), set(s));
        // exact section aligned box: 0..15 is one section
        assertEquals(1, CutawayIndex.sectionsOf(Box3i.of(0, 0, 0, 15, 15, 15)).length);
        assertEquals(2, CutawayIndex.sectionsOf(Box3i.of(0, 0, 0, 16, 0, 0)).length);
        // negative coordinates floor correctly: -16..-1 is section -1
        assertEquals(Set.of(CutawayIndex.sectionKey(-1, 0, -1)), set(CutawayIndex.sectionsOf(Box3i.of(-16, 0, -16, -1, 0, -1))));
    }

    @Test
    void cutInsideOutsideAndEdges() {
        CutawayIndex idx = CutawayIndex.of(List.of(Box3i.of(10, 0, 10, 40, 5, 20)));
        assertTrue(idx.cut(10, 0, 10));
        assertTrue(idx.cut(40, 5, 20));   // inclusive max corner, other section
        assertTrue(idx.cut(25, 3, 15));
        assertFalse(idx.cut(9, 0, 10));
        assertFalse(idx.cut(41, 5, 20));
        assertFalse(idx.cut(25, 6, 15));
        assertFalse(idx.cut(25, -1, 15));
        assertFalse(idx.cut(25, 3, 21));
        assertFalse(idx.cut(1000, 3, 15));
    }

    @Test
    void multipleBoxesAndNegatives() {
        CutawayIndex idx = CutawayIndex.of(List.of(Box3i.of(-20, -10, -20, -5, 0, -5), Box3i.of(-4, -10, -20, 3, 0, -5)));
        assertTrue(idx.cut(-5, -3, -5));
        assertTrue(idx.cut(-4, -3, -5));    // second box, same section as first
        assertTrue(idx.cut(3, 0, -20));
        assertFalse(idx.cut(4, 0, -20));
        assertFalse(idx.cut(-21, 0, -20));
    }

    @Test
    void emptyIndexCutsNothing() {
        assertTrue(CutawayIndex.EMPTY.isEmpty());
        assertFalse(CutawayIndex.EMPTY.cut(0, 0, 0));
        assertTrue(CutawayIndex.of(List.of()).isEmpty());
        assertEquals(0, CutawayIndex.EMPTY.sectionKeys().length);
    }

    @Test
    void manyBoxesAllFound() {
        java.util.ArrayList<Box3i> l = new java.util.ArrayList<>();
        for (int i = 0; i < 300; i++) l.add(Box3i.of(i * 20, 0, 0, i * 20 + 3, 3, 3));
        CutawayIndex idx = CutawayIndex.of(l);
        for (int i = 0; i < 300; i++) {
            assertTrue(idx.cut(i * 20 + 2, 1, 1));
            assertFalse(idx.cut(i * 20 + 5, 1, 1));
        }
    }

    @Test
    void dirtyUnionOfOldAndNew() {
        CutawayIndex a = CutawayIndex.of(List.of(Box3i.of(0, 0, 0, 15, 15, 15)));
        CutawayIndex b = CutawayIndex.of(List.of(Box3i.of(0, 0, 0, 15, 15, 15), Box3i.of(16, 0, 0, 16, 0, 0)));
        long[] u = CutawayIndex.dirtyUnion(a, b);
        assertEquals(Set.of(CutawayIndex.sectionKey(0, 0, 0), CutawayIndex.sectionKey(1, 0, 0)), set(u));
        assertEquals(2, u.length);   // distinct
        assertEquals(1, CutawayIndex.dirtyUnion(a, CutawayIndex.EMPTY).length);
    }

    @Test
    void neighborsExpand27() {
        long[] n = CutawayIndex.withNeighbors(new long[]{CutawayIndex.sectionKey(0, 0, 0)});
        assertEquals(27, n.length);
        assertTrue(set(n).contains(CutawayIndex.sectionKey(-1, -1, 1)));
        // two adjacent sections: 4x3x3 = 36 distinct
        assertEquals(36, CutawayIndex.withNeighbors(new long[]{CutawayIndex.sectionKey(0, 0, 0), CutawayIndex.sectionKey(1, 0, 0)}).length);
    }
}
