package dev.heavymatica.core;

/** What a region is for. Names match Manhattan's ManhattanAreas.Role. */
public enum RegionRole {
    DIG(0xFFFF8C00),
    CLEAR(0xFFFFD700),
    FLOOD(0xFF1E90FF),
    FILL(0xFF8B5A2B),
    PROTECT(0xFF32CD32),
    CUSTOM(0xFFFFFFFF);

    private final int defaultColor;

    RegionRole(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    /** ARGB row and wireframe color for this role. */
    public int defaultColor() {
        return defaultColor;
    }

    /** True for the roles that remove terrain (DIG and CLEAR). */
    public boolean cutsTerrain() {
        return this == DIG || this == CLEAR;
    }
}
