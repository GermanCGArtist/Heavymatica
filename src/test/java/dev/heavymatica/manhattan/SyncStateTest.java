package dev.heavymatica.manhattan;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.place.HeavymaticPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateTest {
    private static HeavymaticPlacement multi() {
        return HeavymaticPlacement.create(new HeavymaticSpec("m", "", "", List.of(
                new HeavymaticRegion("a", RegionRole.DIG, 1, List.of(Box3i.of(0, 0, 0, 1, 1, 1))),
                new HeavymaticRegion("b", RegionRole.FILL, 2, List.of(Box3i.of(3, 0, 0, 4, 1, 1))))), "m", 0, 0, 0);
    }

    @Test
    void scriptOverwriteAndDeleteMarkSentPlacements() {
        HeavymaticPlacement p = multi();
        p.markSent();
        assertEquals(1, SyncState.onAreaChanged(List.of(p), "m_b", "SCRIPT").size());
        assertTrue(p.outOfSync());
        p.markSent();
        assertFalse(p.outOfSync());
        assertEquals(1, SyncState.onAreaChanged(List.of(p), "m_a", null).size());
        assertTrue(p.outOfSync());
    }

    @Test
    void ownPutAndUnrelatedNamesAreIgnored() {
        HeavymaticPlacement p = multi();
        p.markSent();
        assertTrue(SyncState.onAreaChanged(List.of(p), "m_a", "HEAVYMATICA").isEmpty());
        assertTrue(SyncState.onAreaChanged(List.of(p), "other", "SCRIPT").isEmpty());
        assertFalse(p.outOfSync());
    }

    @Test
    void neverSentPlacementIsIgnored() {
        HeavymaticPlacement p = multi();
        assertTrue(SyncState.onAreaChanged(List.of(p), "m_a", "MANUAL").isEmpty());
        assertFalse(p.outOfSync());
    }
}
