package dev.heavymatica.export;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.ManhattanScript;
import dev.heavymatica.place.HeavymaticPlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds the export text. Pure (no Minecraft imports). Every line a Manhattan script would not understand starts
 * with "// ", so the whole text can be pasted into a script as-is.
 */
public final class LlmExportText {
    public static final int TOP_BLOCKS = 5;

    private LlmExportText() {}

    /** @param scans one per region, matched by region name (a region without a scan gets "not scanned"). */
    public static String build(HeavymaticPlacement p, List<RegionScan> scans) {
        List<HeavymaticRegion> regions = p.spec().regions();
        Box3i wb = p.worldBounds();
        long volume = 0;
        int boxes = 0;
        for (HeavymaticRegion r : regions) {
            for (Box3i b : p.worldBoxes(r.name())) {
                volume += b.volume();
                boxes++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("// Heavymatic ").append(p.presetName()).append(": ")
                .append(regions.size()).append(regions.size() == 1 ? " region, " : " regions, ")
                .append(boxes).append(boxes == 1 ? " box, " : " boxes, ")
                .append("world bounds (").append(wb.minX()).append(", ").append(wb.minY()).append(", ").append(wb.minZ())
                .append(")-(").append(wb.maxX()).append(", ").append(wb.maxY()).append(", ").append(wb.maxZ())
                .append("), volume ").append(volume).append('\n');
        sb.append("// Terrain was scanned in loaded chunks only; unscanned cells are not in the counts.\n");
        sb.append("// The area_box and call lines below are a Manhattan script; everything starting with // is a comment.\n");

        List<HeavymaticRegion> ordered = new ArrayList<>(regions);
        ordered.sort(Comparator.comparingInt(r -> r.role() == dev.heavymatica.core.RegionRole.STAIRS ? 0 : 1));   // the shaft before the dig
        for (HeavymaticRegion r : ordered) {
            String area = p.manhattanAreaName(r);
            List<Box3i> wbx = p.worldBoxes(r.name());
            sb.append("// ---\n");
            sb.append("// Region ").append(r.name()).append(" (role ").append(r.role().name())
                    .append(", ").append(wbx.size()).append(wbx.size() == 1 ? " box" : " boxes")
                    .append(", Manhattan area \"").append(area).append("\")\n");
            RegionScan s = find(scans, r.name());
            if (s == null) {
                sb.append("// terrain: not scanned\n");
            } else {
                appendScan(sb, s);
            }
            sb.append(ManhattanScript.areaLines(r.role(), area, wbx));
            sb.append(ManhattanScript.roleCall(r.role(), area));
        }
        return sb.toString();
    }

    private static void appendScan(StringBuilder sb, RegionScan s) {
        sb.append("// water: ").append(s.water()).append(", lava: ").append(s.lava()).append(", air: ").append(s.air())
                .append(", top blocks: ").append(topBlocks(s.blockCounts()))
                .append("; ").append(s.percentUnscanned()).append("% unscanned (")
                .append(s.scannedCells()).append(" of ").append(s.totalCells()).append(" cells)\n");
        if (s.fallingEdge() > 0) {
            sb.append("// ").append(s.fallingEdge()).append(s.fallingEdge() == 1 ? " falling block" : " falling blocks")
                    .append(" at the edges (sand, gravel... may collapse into the area)\n");
        }
        if (s.lavaAdjacent() > 0) {
            sb.append("// lava next to the region: ").append(s.lavaAdjacent()).append(" cells (may flow in)\n");
        }
        for (String name : s.overlapsProtected()) {
            sb.append("// overlaps protected area '").append(name).append("'\n");
        }
    }

    private static String topBlocks(Map<String, Long> counts) {
        if (counts.isEmpty()) return "none";
        List<Map.Entry<String, Long>> list = new ArrayList<>(counts.entrySet());
        list.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(TOP_BLOCKS, list.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).getKey()).append(' ').append(list.get(i).getValue());
        }
        return sb.toString();
    }

    private static RegionScan find(List<RegionScan> scans, String region) {
        for (RegionScan s : scans) if (s.regionName().equals(region)) return s;
        return null;
    }

    /** A file-name-safe version of a name. */
    public static String sanitize(String name) {
        String s = name == null ? "" : name.strip().replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isBlank() ? "heavymatic" : s;
    }

    public static int lineCount(String text) {
        return text.isEmpty() ? 0 : text.split("\n", -1).length - (text.endsWith("\n") ? 1 : 0);
    }
}
