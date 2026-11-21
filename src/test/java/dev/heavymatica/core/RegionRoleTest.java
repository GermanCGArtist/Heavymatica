package dev.heavymatica.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionRoleTest {
    @Test
    void defaultColorsAreArgb() {
        assertEquals(0xFFFF8C00, RegionRole.DIG.defaultColor());
        assertEquals(0xFFFFD700, RegionRole.CLEAR.defaultColor());
        assertEquals(0xFF1E90FF, RegionRole.FLOOD.defaultColor());
        assertEquals(0xFF8B5A2B, RegionRole.FILL.defaultColor());
        assertEquals(0xFF32CD32, RegionRole.PROTECT.defaultColor());
        assertEquals(0xFF00CED1, RegionRole.STAIRS.defaultColor());
        assertEquals(0xFFFFFFFF, RegionRole.CUSTOM.defaultColor());
    }

    @Test
    void onlyDigAndClearCutTerrain() {
        assertTrue(RegionRole.DIG.cutsTerrain());
        assertTrue(RegionRole.CLEAR.cutsTerrain());
        assertFalse(RegionRole.FLOOD.cutsTerrain());
        assertFalse(RegionRole.FILL.cutsTerrain());
        assertFalse(RegionRole.PROTECT.cutsTerrain());
        assertFalse(RegionRole.STAIRS.cutsTerrain());
        assertFalse(RegionRole.CUSTOM.cutsTerrain());
    }
}
