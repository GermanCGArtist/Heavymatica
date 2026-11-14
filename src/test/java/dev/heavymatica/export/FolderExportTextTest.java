package dev.heavymatica.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FolderExportTextTest {
    private static FolderExportText.Block block(String label, String area) {
        return new FolderExportText.Block(label, List.of(area),
                "// Heavymatic " + label + ": 1 region\narea_box(\"" + area + "\", pos(0, 0, 0), pos(1, 1, 1))\narea(\"" + area + "\")\nclear_area()\n");
    }

    @Test
    void headerAndOrderAndBlankSeparators() {
        String t = FolderExportText.combine("canals", List.of(block("a", "a"), block("b", "b")));
        String[] lines = t.split("\n", -1);
        assertEquals("// Folder canals: 2 heavymatics", lines[0]);
        assertEquals("", lines[1]);
        assertTrue(t.indexOf("Heavymatic a:") < t.indexOf("Heavymatic b:"));
        assertTrue(t.contains("clear_area()\n\n// Heavymatic b"));
        assertTrue(t.endsWith("clear_area()\n"));
        assertFalse(t.contains("Note:"));
    }

    @Test
    void singularHeader() {
        assertTrue(FolderExportText.combine("x", List.of(block("a", "a"))).startsWith("// Folder x: 1 heavymatic\n"));
    }

    @Test
    void duplicateAreaNameIsNotedAsComment() {
        String t = FolderExportText.combine("f", List.of(block("a", "same"), block("b", "same")));
        assertTrue(t.contains("// Note: Manhattan area \"same\" is defined by 2 heavymatics (a, b)"));
        assertEquals(2, t.split("area_box\\(", -1).length - 1);
    }

    @Test
    void everyNonScriptLineIsCommentOrBlank() {
        String t = FolderExportText.combine("f", List.of(block("a", "same"), block("b", "same")));
        for (String line : t.split("\n")) {
            boolean script = line.startsWith("area_box(") || line.startsWith("area(") || line.startsWith("clear_area(");
            assertTrue(script || line.isEmpty() || line.startsWith("// "), "bad line: " + line);
        }
    }

    @Test
    void emptyFolder() {
        assertEquals("// Folder f: 0 heavymatics\n", FolderExportText.combine("f", List.of()));
    }
}
