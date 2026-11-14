package dev.heavymatica.manhattan;

import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.place.HeavymaticPlacement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Out-of-sync bookkeeping when Manhattan reports a changed area. No Fabric or Minecraft imports (testable). */
final class SyncState {
    private SyncState() {}

    /**
     * A saved Manhattan area changed. Marks every sent placement that has a region of that name out of sync unless
     * the new owner is HEAVYMATICA (our own put). Owner null = deleted. Returns the placements marked.
     */
    static List<HeavymaticPlacement> onAreaChanged(Collection<HeavymaticPlacement> placements, String areaName, String newOwner) {
        List<HeavymaticPlacement> marked = new ArrayList<>();
        if ("HEAVYMATICA".equals(newOwner) || areaName == null) return marked;
        for (HeavymaticPlacement p : placements) {
            if (p.lastSentHash() == 0) continue;
            for (HeavymaticRegion r : p.spec().regions()) {
                if (areaName.equals(p.manhattanAreaName(r))) {
                    p.markOutOfSync();
                    marked.add(p);
                    break;
                }
            }
        }
        return marked;
    }
}
