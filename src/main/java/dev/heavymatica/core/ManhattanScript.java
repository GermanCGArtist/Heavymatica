package dev.heavymatica.core;

import java.util.List;
import java.util.Objects;

/** Text for Manhattan scripts: area_box lines and the role call. */
public final class ManhattanScript {
    private ManhattanScript() {
    }

    /** One area_box(...) line per box, each ending with a newline. */
    public static String areaBoxLines(String areaName, List<Box3i> worldBoxes) {
        String name = quote(areaName);
        StringBuilder sb = new StringBuilder();
        for (Box3i b : worldBoxes) {
            sb.append("area_box(").append(name)
                    .append(", pos(").append(b.minX()).append(", ").append(b.minY()).append(", ").append(b.minZ())
                    .append("), pos(").append(b.maxX()).append(", ").append(b.maxY()).append(", ").append(b.maxZ())
                    .append("))\n");
        }
        return sb.toString();
    }

    /**
     * The lines that define a region's area: area_box lines, or for the stair shaft one stairs(...) line for its
     * first box (a shaft is one footprint). Define the shaft before the dig that uses it runs.
     */
    public static String areaLines(RegionRole role, String areaName, List<Box3i> worldBoxes) {
        if (role != RegionRole.STAIRS || worldBoxes.isEmpty()) return areaBoxLines(areaName, worldBoxes);
        Box3i b = worldBoxes.get(0);
        return "stairs(" + quote(areaName)
                + ", pos(" + b.minX() + ", " + b.minY() + ", " + b.minZ()
                + "), pos(" + b.maxX() + ", " + b.maxY() + ", " + b.maxZ() + "))\n";
    }

    /** The area and role call for a region, each line ending with a newline. */
    public static String roleCall(RegionRole role, String areaName) {
        Objects.requireNonNull(role, "role");
        String name = quote(areaName);
        return switch (role) {
            // protect("n") without corners protects the current selection, so select the area first
            case PROTECT -> "area(" + name + ")\nprotect(" + name + ")\n";
            case DIG -> "area(" + name + ")\nclear_area()\n";
            case CLEAR -> "area(" + name + ")\nclear_area(2)\n";
            case FLOOD -> "area(" + name + ")\nflood()\n";
            case FILL -> "area(" + name + ")\nfill_water(\"dirt\")\n";
            case STAIRS -> "";   // stairs(...) in areaLines already did it: the shaft belongs to the dig it lies in
            case CUSTOM -> "area(" + name + ")\n";
        };
    }

    /** A double-quoted string literal with backslashes and double quotes escaped. */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
