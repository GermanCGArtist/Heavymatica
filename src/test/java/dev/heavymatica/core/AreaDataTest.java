package dev.heavymatica.core;

import dev.heavymatica.core.AreaData.Mode;
import dev.heavymatica.core.AreaData.Plan;
import dev.heavymatica.core.AreaData.Pose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaDataTest {
    @Test
    void jsonRoundTrip() {
        AreaData a = new AreaData("trench", Mode.PROTECT, "p", List.of(Box3i.of(0, 0, 0, 3, 2, 1), Box3i.of(5, 0, 0, 6, 0, 0)));
        assertEquals(a, AreaData.fromJson(a.toJson()));
    }

    @Test
    void unionCoversAllBoxes() {
        assertEquals(Box3i.of(0, 0, 0, 6, 2, 1), AreaData.union(List.of(Box3i.of(0, 0, 0, 3, 2, 1), Box3i.of(5, 0, 0, 6, 0, 0))));
    }

    @Test
    void identityAndMove() {
        Box3i b = Box3i.of(0, 0, 0, 3, 2, 1);
        assertEquals(Box3i.of(10, 64, -5, 13, 66, -4), AreaData.toWorld(b, new Pose(10, 64, -5, 0, 0)));
    }

    @Test
    void rotationMirrorFollowSchemarrayConvention() {
        Box3i b = Box3i.of(1, 0, 2, 4, 1, 3);
        // CW90: (x,z) -> (-z,x)
        assertEquals(Box3i.of(-3, 0, 1, -2, 1, 4), AreaData.toWorld(b, new Pose(0, 0, 0, 1, 0)));
        // CW180: (-x,-z)
        assertEquals(Box3i.of(-4, 0, -3, -1, 1, -2), AreaData.toWorld(b, new Pose(0, 0, 0, 2, 0)));
        // CCW90: (z,-x)
        assertEquals(Box3i.of(2, 0, -4, 3, 1, -1), AreaData.toWorld(b, new Pose(0, 0, 0, 3, 0)));
        // front/back flips X, left/right flips Z (before turning and moving)
        assertEquals(Box3i.of(-4, 0, 2, -1, 1, 3), AreaData.toWorld(b, new Pose(0, 0, 0, 0, 1)));
        assertEquals(Box3i.of(1, 0, -3, 4, 1, -2), AreaData.toWorld(b, new Pose(0, 0, 0, 0, 2)));
        assertEquals(Box3i.of(-3, 0, -4, -2, 1, -1), AreaData.toWorld(b, new Pose(0, 0, 0, 1, 1)));
    }

    @Test
    void dedupeByRoleAndBoxSet() {
        Box3i a = Box3i.of(0, 0, 0, 1, 1, 1), b = Box3i.of(5, 5, 5, 6, 6, 6);
        List<Plan> already = List.of(new Plan("x", RegionRole.DIG, List.of(a, b)));
        List<Plan> cand = List.of(
                new Plan("same_other_order", RegionRole.DIG, List.of(b, a)),
                new Plan("other_role", RegionRole.PROTECT, List.of(a, b)),
                new Plan("other_role_again", RegionRole.PROTECT, List.of(a, b)),
                new Plan("new", RegionRole.DIG, List.of(a)));
        assertEquals(List.of("other_role", "new"), AreaData.dropDuplicates(already, cand).stream().map(Plan::name).toList());
    }

    @Test
    void safeName() {
        assertEquals("my_area_1", AreaData.safeName(" my area 1 "));
        assertEquals("area", AreaData.safeName(""));
    }
}
