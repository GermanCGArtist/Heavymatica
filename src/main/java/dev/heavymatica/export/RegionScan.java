package dev.heavymatica.export;

import java.util.List;
import java.util.Map;

/**
 * Terrain scan result of one region (all its world boxes, all array copies). Pure data.
 *
 * @param blockCounts     block id (without "minecraft:") to cell count, air excluded
 * @param fallingEdge     falling blocks (sand, gravel, concrete powder...) on box boundary cells or directly above a box
 * @param lavaAdjacent    lava cells in the 1-block shell around the boxes
 * @param overlapsProtected names of Manhattan protected areas whose boxes intersect this region
 */
public record RegionScan(String regionName, long totalCells, long scannedCells, Map<String, Long> blockCounts,
                         long water, long lava, long air, long fallingEdge, long lavaAdjacent,
                         List<String> overlapsProtected) {
    public RegionScan {
        blockCounts = Map.copyOf(blockCounts);
        overlapsProtected = List.copyOf(overlapsProtected);
    }

    public long unscannedCells() {
        return Math.max(0, totalCells - scannedCells);
    }

    /** Percent of cells not scanned (unloaded chunks), rounded to the nearest integer. */
    public int percentUnscanned() {
        if (totalCells <= 0) return 0;
        return (int) Math.round(100.0 * unscannedCells() / totalCells);
    }
}
