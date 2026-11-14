package dev.heavymatica.export;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.place.HeavymaticPlacement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LlmExportTextTest {
    private static HeavymaticPlacement placement() {
        HeavymaticSpec spec = new HeavymaticSpec("canal", "", "", List.of(
                new HeavymaticRegion("trench", RegionRole.DIG, 0, List.of(new Box3i(0, 0, 0, 9, 4, 9), new Box3i(10, 0, 0, 14, 4, 9))),
                new HeavymaticRegion("keep", RegionRole.PROTECT, 0, List.of(new Box3i(0, 5, 0, 3, 6, 3)))));
        return HeavymaticPlacement.create(spec, "canal", 100, 60, 200);
    }

    private static List<RegionScan> scans() {
        return List.of(
                new RegionScan("trench", 750, 600, Map.of("stone", 289L, "dirt", 123L, "gravel", 50L, "sand", 40L,
                        "granite", 30L, "clay", 5L), 34, 0, 12, 3, 0, List.of("base")),
                new RegionScan("keep", 32, 32, Map.of("grass_block", 16L), 0, 2, 14, 0, 4, List.of()));
    }

    @Test
    void structure() {
        String t = LlmExportText.build(placement(), scans());
        String[] lines = t.split("\n");
        assertEquals("// Heavymatic canal: 2 regions, 3 boxes, world bounds (100, 60, 200)-(114, 66, 209), volume 782", lines[0]);
        assertTrue(t.contains("// Region trench (role DIG, 2 boxes, Manhattan area \"canal_trench\")"));
        assertTrue(t.contains("// water: 34, lava: 0, air: 12, top blocks: stone 289, dirt 123, gravel 50, sand 40, granite 30; 20% unscanned (600 of 750 cells)"));
        assertFalse(t.contains("clay 5"));
        assertTrue(t.contains("// 3 falling blocks at the edges"));
        assertTrue(t.contains("// overlaps protected area 'base'"));
        assertTrue(t.contains("// lava next to the region: 4 cells"));
        assertTrue(t.contains("area_box(\"canal_trench\", pos(100, 60, 200), pos(109, 64, 209))"));
        assertTrue(t.contains("area_box(\"canal_trench\", pos(110, 60, 200), pos(114, 64, 209))"));
        assertTrue(t.contains("area_box(\"canal_keep\", pos(100, 65, 200), pos(103, 66, 203))"));
        assertTrue(t.contains("area(\"canal_trench\")\nclear_area()\n"));
        assertTrue(t.contains("area(\"canal_keep\")\nprotect(\"canal_keep\")\n"));
        assertTrue(t.endsWith("\n"));
        assertEquals(lines.length, LlmExportText.lineCount(t));
    }

    @Test
    void everyNonScriptLineIsAComment() {
        String t = LlmExportText.build(placement(), scans());
        for (String line : t.split("\n")) {
            boolean script = line.startsWith("area_box(") || line.startsWith("area(") || line.startsWith("clear_area(")
                    || line.startsWith("protect(");
            assertTrue(script || line.startsWith("// "), "bad line: " + line);
        }
    }

    @Test
    void missingScanIsMarked() {
        String t = LlmExportText.build(placement(), List.of());
        assertTrue(t.contains("// terrain: not scanned"));
        assertTrue(t.contains("area_box("));
    }

    @Test
    void sanitize() {
        assertEquals("my_canal_1", LlmExportText.sanitize("my canal/1"));
        assertEquals("heavymatic", LlmExportText.sanitize(" "));
    }

    @Test
    void cursorVisitsEveryCellOnceAcrossSlices() {
        List<Box3i> boxes = List.of(new Box3i(0, 0, 0, 4, 2, 3), new Box3i(10, 5, 5, 10, 5, 5), new Box3i(-3, -3, -3, -1, -2, -1));
        long expected = 0;
        for (Box3i b : boxes) expected += b.volume();
        BoxCursor c = new BoxCursor(boxes);
        Set<String> seen = new HashSet<>();
        int total = 0;
        int[] slices = {1, 7, 13, 100};
        int i = 0;
        while (!c.done()) {
            total += c.advance(slices[i++ % slices.length], (x, y, z) -> {
                assertTrue(boxes.stream().anyMatch(b -> b.contains(x, y, z)));
                assertTrue(seen.add(x + "," + y + "," + z), "visited twice");
            });
        }
        assertEquals(expected, total);
        assertEquals(expected, seen.size());
        assertEquals(0, c.advance(10, (x, y, z) -> fail()));
    }

    @Test
    void shellAndTopCoverTheOuterLayerExactly() {
        Box3i b = new Box3i(0, 0, 0, 2, 2, 2);
        Set<String> cells = new HashSet<>();
        List<Box3i> parts = new ArrayList<>(BoxCursor.shellWithoutTop(b));
        parts.add(BoxCursor.above(b));
        new BoxCursor(parts).advance(Integer.MAX_VALUE, (x, y, z) -> {
            assertFalse(b.contains(x, y, z));
            assertTrue(cells.add(x + "," + y + "," + z), "duplicate " + x + "," + y + "," + z);
        });
        // 5x5x4 below-and-beside shell of a 3x3x3 box (y -1..2) minus the box, plus the 3x3 top
        assertEquals(5 * 5 * 4 - 27 + 9, cells.size());
    }
}
