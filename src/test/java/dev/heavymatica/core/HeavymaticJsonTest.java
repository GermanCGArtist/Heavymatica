package dev.heavymatica.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeavymaticJsonTest {
    private static HeavymaticSpec threeRegionSpec() {
        return new HeavymaticSpec("canal_segment", "Julian", "test preset", List.of(
                new HeavymaticRegion("trench", RegionRole.DIG, RegionRole.DIG.defaultColor(),
                        List.of(Box3i.of(0, 0, 0, 20, 14, 20), Box3i.of(5, 15, 5, 6, 16, 6))),
                new HeavymaticRegion("flood", RegionRole.FLOOD, 0xFF00AAFF,
                        List.of(Box3i.of(0, 0, 21, 20, 3, 30))),
                new HeavymaticRegion("guard", RegionRole.PROTECT, 0x12345678,
                        List.of(Box3i.of(-4, -1, -4, -1, 2, -1)))));
    }

    private static final String EXAMPLE = """
            { "version": 1, "name": "canal_segment", "author": "", "description": "",
              "regions": [ { "name": "trench", "role": "DIG", "color": -23296, "boxes": [[0,0,0,20,14,20]] } ] }
            """;

    @Test
    void roundTripGivesEqualSpec() {
        HeavymaticSpec spec = threeRegionSpec();
        String json = HeavymaticJson.write(spec);
        assertEquals(spec, HeavymaticJson.read(json));
    }

    @Test
    void writeUsesVersionOneAndRoleNames() {
        String json = HeavymaticJson.write(threeRegionSpec());
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"version\": 1"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"role\": \"DIG\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"role\": \"PROTECT\""));
    }

    @Test
    void exampleFromArchitectureParses() {
        HeavymaticSpec spec = HeavymaticJson.read(EXAMPLE);
        assertEquals("canal_segment", spec.name());
        assertEquals("", spec.author());
        assertEquals("", spec.description());
        assertEquals(1, spec.regions().size());
        HeavymaticRegion trench = spec.regions().get(0);
        assertEquals("trench", trench.name());
        assertEquals(RegionRole.DIG, trench.role());
        assertEquals(-23296, trench.color());
        assertEquals(List.of(Box3i.of(0, 0, 0, 20, 14, 20)), trench.boxes());
    }

    @Test
    void unknownFieldsAreIgnored() {
        String json = """
                { "version": 1, "name": "p", "extra": {"x": 1},
                  "regions": [ { "name": "r", "role": "FILL", "color": 1, "boxes": [], "note": "hi" } ] }
                """;
        HeavymaticSpec spec = HeavymaticJson.read(json);
        assertEquals(RegionRole.FILL, spec.regions().get(0).role());
    }

    @Test
    void boxesInAnyCornerOrderAreNormalized() {
        String json = """
                { "version": 1, "name": "p",
                  "regions": [ { "name": "r", "role": "CLEAR", "color": 1, "boxes": [[20,14,20,0,0,0]] } ] }
                """;
        assertEquals(List.of(Box3i.of(0, 0, 0, 20, 14, 20)), HeavymaticJson.read(json).regions().get(0).boxes());
    }

    @Test
    void badVersionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> HeavymaticJson.read("{ \"version\": 2, \"name\": \"p\", \"regions\": [] }"));
    }

    @Test
    void missingVersionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> HeavymaticJson.read("{ \"name\": \"p\", \"regions\": [] }"));
    }

    @Test
    void unknownRoleThrows() {
        String json = """
                { "version": 1, "name": "p",
                  "regions": [ { "name": "r", "role": "BURN", "color": 1, "boxes": [] } ] }
                """;
        assertThrows(IllegalArgumentException.class, () -> HeavymaticJson.read(json));
    }

    @Test
    void fiveNumberBoxThrows() {
        String json = """
                { "version": 1, "name": "p",
                  "regions": [ { "name": "r", "role": "DIG", "color": 1, "boxes": [[0,0,0,20]] } ] }
                """;
        assertThrows(IllegalArgumentException.class, () -> HeavymaticJson.read(json));
    }
}
