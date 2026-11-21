package dev.heavymatica.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManhattanScriptTest {
    private static final List<Box3i> TWO_BOXES = List.of(
            Box3i.of(10, 64, -5, 12, 66, -3),
            Box3i.of(0, 0, 0, 1, 1, 1));

    @Test
    void areaBoxLinesForTwoBoxes() {
        String expected = """
                area_box("trench", pos(10, 64, -5), pos(12, 66, -3))
                area_box("trench", pos(0, 0, 0), pos(1, 1, 1))
                """;
        assertEquals(expected, ManhattanScript.areaBoxLines("trench", TWO_BOXES));
    }

    @Test
    void areaBoxLinesEmptyForNoBoxes() {
        assertEquals("", ManhattanScript.areaBoxLines("trench", List.of()));
    }

    @Test
    void roleCallDig() {
        assertEquals("area(\"a\")\nclear_area()\n", ManhattanScript.roleCall(RegionRole.DIG, "a"));
    }

    @Test
    void roleCallClear() {
        assertEquals("area(\"a\")\nclear_area(2)\n", ManhattanScript.roleCall(RegionRole.CLEAR, "a"));
    }

    @Test
    void roleCallFlood() {
        assertEquals("area(\"a\")\nflood()\n", ManhattanScript.roleCall(RegionRole.FLOOD, "a"));
    }

    @Test
    void roleCallFill() {
        assertEquals("area(\"a\")\nfill_water(\"dirt\")\n", ManhattanScript.roleCall(RegionRole.FILL, "a"));
    }

    @Test
    void roleCallProtectSelectsTheAreaFirst() {
        assertEquals("area(\"a\")\nprotect(\"a\")\n", ManhattanScript.roleCall(RegionRole.PROTECT, "a"));
    }

    @Test
    void stairsAreOneStairsLineForTheFirstBox() {
        assertEquals("stairs(\"shaft\", pos(0, 1, 2), pos(2, 9, 4))\n",
                ManhattanScript.areaLines(RegionRole.STAIRS, "shaft", List.of(Box3i.of(0, 1, 2, 2, 9, 4), Box3i.of(8, 8, 8, 9, 9, 9))));
        assertEquals("", ManhattanScript.roleCall(RegionRole.STAIRS, "shaft"));
    }

    @Test
    void otherRolesKeepTheirAreaBoxLines() {
        assertEquals(ManhattanScript.areaBoxLines("trench", TWO_BOXES), ManhattanScript.areaLines(RegionRole.DIG, "trench", TWO_BOXES));
    }

    @Test
    void roleCallCustomHasOnlyArea() {
        assertEquals("area(\"a\")\n", ManhattanScript.roleCall(RegionRole.CUSTOM, "a"));
    }

    @Test
    void nameWithQuoteAndBackslashIsEscaped() {
        String name = "say \"hi\" \\ there";
        String escaped = "\"say \\\"hi\\\" \\\\ there\"";
        assertEquals("area_box(" + escaped + ", pos(0, 0, 0), pos(0, 0, 0))\n",
                ManhattanScript.areaBoxLines(name, List.of(Box3i.of(0, 0, 0, 0, 0, 0))));
        assertEquals("area(" + escaped + ")\nprotect(" + escaped + ")\n", ManhattanScript.roleCall(RegionRole.PROTECT, name));
    }
}
