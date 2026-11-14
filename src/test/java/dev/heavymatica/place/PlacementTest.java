package dev.heavymatica.place;

import dev.heavymatica.core.ArrayConfig;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.Mirror;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.core.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlacementTest {
    private static HeavymaticSpec one() {
        return new HeavymaticSpec("canal", "me", "d",
                List.of(new HeavymaticRegion("trench", RegionRole.DIG, 0xFFFF0000, List.of(Box3i.of(0, 0, 0, 4, 2, 9)))));
    }

    private static HeavymaticSpec three() {
        return new HeavymaticSpec("multi", "", "", List.of(
                new HeavymaticRegion("a", RegionRole.DIG, 1, List.of(Box3i.of(0, 0, 0, 3, 3, 3))),
                new HeavymaticRegion("b", RegionRole.PROTECT, 2, List.of(Box3i.of(5, 0, 0, 6, 1, 1), Box3i.of(7, 0, 0, 8, 1, 1))),
                new HeavymaticRegion("c", RegionRole.FILL, 3, List.of(Box3i.of(0, 4, 0, 3, 4, 3)))));
    }

    @Test
    void jsonRoundTrip() {
        HeavymaticPlacement a = HeavymaticPlacement.create(one(), "canal", 10, 64, -20);
        a.setRotation(Rotation.CLOCKWISE_90);
        a.setMirror(Mirror.LEFT_RIGHT);
        a.setArray(new ArrayConfig(3, 1, 2, 1, 0, -1));
        a.setCutaway(CutawayMode.WIREFRAME);
        a.setManhattanName("my_canal");
        a.markSent();
        a.markOutOfSync();
        HeavymaticPlacement b = HeavymaticPlacement.create(three(), "multi", 0, 0, 0);
        b.setEnabled(false);
        b.setRegionVisible("b", false);

        List<HeavymaticPlacement> back = PlacementJson.read(PlacementJson.write(List.of(a, b)));
        assertEquals(2, back.size());
        HeavymaticPlacement a2 = back.get(0), b2 = back.get(1);
        assertEquals(a.id(), a2.id());
        assertEquals(a.spec(), a2.spec());
        assertEquals(a.transform(), a2.transform());
        assertEquals(Rotation.CLOCKWISE_90, a2.transform().rotation());
        assertEquals(CutawayMode.WIREFRAME, a2.cutaway());
        assertEquals("my_canal", a2.manhattanName());
        assertEquals(a.lastSentHash(), a2.lastSentHash());
        assertNotEquals(0, a2.lastSentHash());
        assertTrue(a2.outOfSync());
        assertEquals(a.sendHash(), a2.sendHash());
        assertEquals(a.allWorldBoxes(), a2.allWorldBoxes());
        assertFalse(b2.enabled());
        assertFalse(b2.isRegionVisible("b"));
        assertTrue(b2.isRegionVisible("a"));
        assertEquals(b.spec(), b2.spec());
        assertEquals(0, b2.lastSentHash());
    }

    @Test
    void sendHashReactsToRelevantChangesOnly() {
        HeavymaticPlacement p = HeavymaticPlacement.create(three(), "multi", 0, 0, 0);
        long h = p.sendHash();
        p.setCutaway(CutawayMode.OFF);
        p.setEnabled(false);
        p.setRegionVisible("a", false);
        assertEquals(h, p.sendHash());

        p.nudge(1, 0, 0);
        long moved = p.sendHash();
        assertNotEquals(h, moved);
        p.nudge(-1, 0, 0);
        assertEquals(h, p.sendHash());

        p.setArray(new ArrayConfig(2, 1, 1, 0, 0, 0));
        assertNotEquals(h, p.sendHash());
        p.setArray(ArrayConfig.NONE);
        p.setManhattanName("other");
        assertNotEquals(h, p.sendHash());

        HeavymaticSpec s = three();
        HeavymaticSpec s2 = new HeavymaticSpec("multi", "", "", List.of(
                s.regions().get(0),
                new HeavymaticRegion("b", RegionRole.CLEAR, 2, s.regions().get(1).boxes()),
                s.regions().get(2)));
        assertNotEquals(h, HeavymaticPlacement.create(s2, "multi", 0, 0, 0).sendHash());
    }

    @Test
    void outOfSyncTracksSentState() {
        HeavymaticPlacement p = HeavymaticPlacement.create(one(), "canal", 0, 0, 0);
        p.nudge(1, 0, 0);
        assertFalse(p.outOfSync());
        p.markSent();
        p.setCutaway(CutawayMode.OFF);
        assertFalse(p.outOfSync());
        p.nudge(0, 1, 0);
        assertTrue(p.outOfSync());
        p.nudge(0, -1, 0);
        assertFalse(p.outOfSync());
    }

    @Test
    void createNormalizes() {
        HeavymaticSpec shifted = new HeavymaticSpec("s", "", "", List.of(
                new HeavymaticRegion("r", RegionRole.DIG, 1, List.of(Box3i.of(10, 5, -3, 14, 8, 0)))));
        HeavymaticPlacement p = HeavymaticPlacement.create(shifted, "s", 100, 60, 200);
        assertEquals(Box3i.of(0, 0, 0, 4, 3, 3), p.spec().bounds());
        assertEquals(110, p.transform().originX());
        assertEquals(65, p.transform().originY());
        assertEquals(197, p.transform().originZ());
        assertEquals(List.of(Box3i.of(110, 65, 197, 114, 68, 200)), p.worldBoxes("r"));
        // unshifted world boxes: local + origin
        assertEquals(Box3i.of(110, 65, 197, 114, 68, 200), p.worldBounds());
    }

    @Test
    void manhattanAreaNames() {
        HeavymaticPlacement single = HeavymaticPlacement.create(one(), "canal", 0, 0, 0);
        assertEquals("canal", single.manhattanAreaName(single.spec().regions().get(0)));
        HeavymaticPlacement multi = HeavymaticPlacement.create(three(), "multi", 0, 0, 0);
        assertEquals("multi_a", multi.manhattanAreaName(multi.spec().regions().get(0)));
        assertEquals("multi_c", multi.manhattanAreaName(multi.spec().regions().get(2)));
        assertEquals("heavymatica:" + multi.id(), multi.entryId());
    }

    @Test
    void worldBoxesCoverArrayAndInvalidateCache() {
        HeavymaticPlacement p = HeavymaticPlacement.create(one(), "canal", 0, 0, 0);
        assertEquals(1, p.worldBoxes("trench").size());
        p.setArray(new ArrayConfig(2, 1, 1, 0, 0, 0));
        assertEquals(2, p.worldBoxes("trench").size());
        assertEquals(2, p.allWorldBoxes().size());
        assertEquals(Box3i.of(0, 0, 0, 9, 2, 9), p.worldBounds());
    }
}
