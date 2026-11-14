package dev.heavymatica.manhattan;

import dev.baritonequeue.api.ManhattanAreas;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.manhattan.ManhattanBridge.NamedBoxes;
import dev.heavymatica.manhattan.ManhattanBridge.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManhattanApiTest {
    private ManhattanApi api;

    @BeforeEach
    void setUp() {
        ManhattanAreas.reset();
        api = ManhattanApi.tryCreate(ManhattanAreas.class);
        assertNotNull(api);
    }

    @Test
    void putRoundTripNormalizesBoxes() throws Exception {
        // corners given "backwards" by Box3i.of are normalized already; the fake normalizes again
        List<Box3i> boxes = List.of(Box3i.of(5, 1, 2, 0, 0, 0), Box3i.of(10, 10, 10, 12, 12, 12));
        assertEquals(SendResult.OK, api.put("canal", boxes, RegionRole.DIG, false));
        var info = ManhattanAreas.get("canal").orElseThrow();
        assertEquals(2, info.boxes().size());
        assertArrayEquals(new int[]{0, 0, 0, 5, 1, 2}, info.boxes().get(0));
        assertArrayEquals(new int[]{10, 10, 10, 12, 12, 12}, info.boxes().get(1));
        assertEquals(ManhattanAreas.Owner.HEAVYMATICA, info.owner());
    }

    @Test
    void rolePassedByName() throws Exception {
        for (RegionRole r : RegionRole.values()) {
            assertEquals(SendResult.OK, api.put("a_" + r.name(), List.of(Box3i.of(0, 0, 0, 1, 1, 1)), r, false));
            assertEquals(r.name(), ManhattanAreas.get("a_" + r.name()).orElseThrow().role().name());
        }
    }

    @Test
    void scriptOwnedThenReplace() throws Exception {
        ManhattanAreas.putExternal("mine", ManhattanAreas.Owner.SCRIPT, ManhattanAreas.Role.CUSTOM, List.of(new int[]{0, 0, 0, 1, 1, 1}));
        List<Box3i> b = List.of(Box3i.of(0, 0, 0, 3, 3, 3));
        assertEquals(SendResult.SCRIPT_OWNED, api.put("mine", b, RegionRole.CLEAR, false));
        assertEquals(ManhattanAreas.Owner.SCRIPT, ManhattanAreas.get("mine").orElseThrow().owner());
        assertEquals(SendResult.OK, api.put("mine", b, RegionRole.CLEAR, true));
        assertEquals(ManhattanAreas.Owner.HEAVYMATICA, ManhattanAreas.get("mine").orElseThrow().owner());
    }

    @Test
    void inUseAndInvalidMapping() throws Exception {
        ManhattanAreas.markInUse("busy");
        assertEquals(SendResult.IN_USE, api.put("busy", List.of(Box3i.of(0, 0, 0, 1, 1, 1)), RegionRole.DIG, true));
        assertEquals(SendResult.INVALID, api.put("empty", List.of(), RegionRole.DIG, false));
        assertEquals(SendResult.INVALID, api.put(" ", List.of(Box3i.of(0, 0, 0, 1, 1, 1)), RegionRole.DIG, false));
    }

    @Test
    void removeOnlyOwnAreas() throws Exception {
        api.put("ours", List.of(Box3i.of(0, 0, 0, 1, 1, 1)), RegionRole.DIG, false);
        ManhattanAreas.putExternal("theirs", ManhattanAreas.Owner.MANUAL, ManhattanAreas.Role.CUSTOM, List.of(new int[]{0, 0, 0, 1, 1, 1}));
        assertTrue(api.remove("ours"));
        assertFalse(api.remove("theirs"));
        assertFalse(api.remove("missing"));
    }

    @Test
    void selectionAndProtectedConversion() throws Exception {
        ManhattanAreas.setSelection(List.of(new int[]{1, 2, 3, 4, 5, 6}));
        assertEquals(List.of(Box3i.of(1, 2, 3, 4, 5, 6)), api.selection());
        api.put("safe", List.of(Box3i.of(0, 0, 0, 9, 9, 9)), RegionRole.PROTECT, false);
        api.put("dig", List.of(Box3i.of(0, 0, 0, 9, 9, 9)), RegionRole.DIG, false);
        List<NamedBoxes> prot = api.protectedAreas();
        assertEquals(1, prot.size());
        assertEquals("safe", prot.get(0).name());
        assertEquals(List.of(Box3i.of(0, 0, 0, 9, 9, 9)), prot.get(0).boxes());
    }

    @Test
    void boxConversionRoundTrip() {
        List<Box3i> in = List.of(Box3i.of(-3, 4, 5, 6, 7, -8));
        assertEquals(in, ManhattanApi.fromArrays(ManhattanApi.toArrays(in)));
    }

    @Test
    void listenerDeliveryWithOwnerNames() throws Exception {
        List<String> seen = new ArrayList<>();
        api.addListener((name, owner) -> seen.add(name + ":" + owner));
        api.put("x", List.of(Box3i.of(0, 0, 0, 1, 1, 1)), RegionRole.DIG, false);
        ManhattanAreas.putExternal("y", ManhattanAreas.Owner.SCRIPT, ManhattanAreas.Role.CUSTOM, List.of(new int[]{0, 0, 0, 1, 1, 1}));
        api.remove("x");
        assertEquals(List.of("x:HEAVYMATICA", "y:SCRIPT", "x:null"), seen);
    }

    @Test
    void wrongShapeIsUnavailable() {
        assertNull(ManhattanApi.tryCreate(String.class));
    }
}
