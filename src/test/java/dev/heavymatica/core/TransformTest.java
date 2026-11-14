package dev.heavymatica.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransformTest {
    // asymmetric: a 3x2x1 slab and a 1x4x2 pillar, local coords
    private static final List<Box3i> SHAPE = List.of(new Box3i(0, 0, 0, 2, 1, 0), new Box3i(5, 0, 1, 5, 3, 2));

    private static Transform t(Rotation r, Mirror m) {
        return new Transform(0, 0, 0, r, m, ArrayConfig.NONE);
    }

    private static long vol(List<Box3i> l) {
        return l.stream().mapToLong(Box3i::volume).sum();
    }

    private static Set<String> cells(List<Box3i> l) {
        Set<String> s = new HashSet<>();
        for (Box3i b : l)
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int y = b.minY(); y <= b.maxY(); y++)
                    for (int z = b.minZ(); z <= b.maxZ(); z++) s.add(x + "," + y + "," + z);
        return s;
    }

    @Test
    void identity() {
        assertEquals(SHAPE, t(Rotation.NONE, Mirror.NONE).apply(SHAPE));
    }

    @Test
    void rotationsHandChecked() {
        assertEquals(List.of(new Box3i(0, 0, 0, 0, 1, 2), new Box3i(-2, 0, 5, -1, 3, 5)),
                t(Rotation.CLOCKWISE_90, Mirror.NONE).apply(SHAPE));
        assertEquals(List.of(new Box3i(-2, 0, 0, 0, 1, 0), new Box3i(-5, 0, -2, -5, 3, -1)),
                t(Rotation.CLOCKWISE_180, Mirror.NONE).apply(SHAPE));
        assertEquals(List.of(new Box3i(0, 0, -2, 0, 1, 0), new Box3i(1, 0, -5, 2, 3, -5)),
                t(Rotation.COUNTERCLOCKWISE_90, Mirror.NONE).apply(SHAPE));
    }

    @Test
    void mirrorsHandChecked() {
        // bounds x 0..5, z 0..2
        assertEquals(List.of(new Box3i(3, 0, 0, 5, 1, 0), new Box3i(0, 0, 1, 0, 3, 2)),
                t(Rotation.NONE, Mirror.FRONT_BACK).apply(SHAPE));
        assertEquals(List.of(new Box3i(0, 0, 2, 2, 1, 2), new Box3i(5, 0, 0, 5, 3, 1)),
                t(Rotation.NONE, Mirror.LEFT_RIGHT).apply(SHAPE));
    }

    @Test
    void volumeAndNormalizedPreserved() {
        for (Rotation r : Rotation.values())
            for (Mirror m : Mirror.values()) {
                List<Box3i> out = t(r, m).apply(SHAPE);
                assertEquals(vol(SHAPE), vol(out));
                assertEquals(SHAPE.size(), out.size());
                for (Box3i b : out) assertTrue(b.minX() <= b.maxX() && b.minY() <= b.maxY() && b.minZ() <= b.maxZ());
            }
    }

    @Test
    void fourQuarterTurnsIsIdentity() {
        List<Box3i> cur = SHAPE;
        for (int i = 0; i < 4; i++) cur = t(Rotation.CLOCKWISE_90, Mirror.NONE).apply(cur);
        assertEquals(SHAPE, cur);
        List<Box3i> back = t(Rotation.COUNTERCLOCKWISE_90, Mirror.NONE).apply(t(Rotation.CLOCKWISE_90, Mirror.NONE).apply(SHAPE));
        assertEquals(SHAPE, back);
    }

    @Test
    void mirrorTwiceIsIdentity() {
        for (Mirror m : new Mirror[]{Mirror.FRONT_BACK, Mirror.LEFT_RIGHT}) {
            Transform tr = t(Rotation.NONE, m);
            assertEquals(cells(SHAPE), cells(tr.apply(tr.apply(SHAPE))));
        }
    }

    @Test
    void originTranslates() {
        List<Box3i> out = new Transform(10, 20, 30, Rotation.NONE, Mirror.NONE, ArrayConfig.NONE).apply(SHAPE);
        assertEquals(new Box3i(10, 20, 30, 12, 21, 30), out.get(0));
    }

    @Test
    void arrayOffsetsWithGap() {
        // bounds 6 x 4 x 3
        Transform tr = new Transform(0, 0, 0, Rotation.NONE, Mirror.NONE, new ArrayConfig(3, 1, 2, 2, 0, -1));
        List<List<Box3i>> per = tr.applyPerCopy(SHAPE);
        assertEquals(6, per.size());
        assertEquals(SHAPE, per.get(0));
        // stepX = 8, stepZ = 2; index = (ix * 1 + iy) * 2 + iz
        assertEquals(SHAPE.get(0).offset(0, 0, 2), per.get(1).get(0));
        assertEquals(SHAPE.get(0).offset(8, 0, 0), per.get(2).get(0));
        assertEquals(SHAPE.get(1).offset(16, 0, 2), per.get(5).get(1));
        assertEquals(12, tr.apply(SHAPE).size());
    }

    @Test
    void arrayStepUsesRotatedBounds() {
        // rotated 90: x -2..0 (3 wide), z 0..5 (6 long); gapX 1 -> stepX 4, stepZ 6
        Transform tr = new Transform(0, 0, 0, Rotation.CLOCKWISE_90, Mirror.NONE, new ArrayConfig(2, 1, 2, 1, 0, 0));
        List<List<Box3i>> per = tr.applyPerCopy(SHAPE);
        Box3i first = t(Rotation.CLOCKWISE_90, Mirror.NONE).apply(SHAPE).get(0);
        assertEquals(first.offset(0, 0, 6), per.get(1).get(0));
        assertEquals(first.offset(4, 0, 0), per.get(2).get(0));
    }

    @Test
    void perCopyCountMatches() {
        ArrayConfig a = new ArrayConfig(2, 3, 4, 0, 0, 0);
        assertEquals(24, a.copyCount());
        assertEquals(24, new Transform(0, 0, 0, Rotation.NONE, Mirror.NONE, a).applyPerCopy(SHAPE).size());
        assertEquals(1, new Transform(0, 0, 0, Rotation.NONE, Mirror.NONE, ArrayConfig.NONE).applyPerCopy(SHAPE).size());
    }

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new ArrayConfig(0, 1, 1, 0, 0, 0));
        Transform bad = new Transform(0, 0, 0, Rotation.NONE, Mirror.NONE, new ArrayConfig(2, 1, 1, -6, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> bad.apply(SHAPE));
        assertDoesNotThrow(() -> new Transform(0, 0, 0, Rotation.NONE, Mirror.NONE, new ArrayConfig(1, 1, 1, -99, 0, 0)).apply(SHAPE));
    }
}
