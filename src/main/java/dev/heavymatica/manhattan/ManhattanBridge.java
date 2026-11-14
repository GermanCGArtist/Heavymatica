package dev.heavymatica.manhattan;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.RegionRole;

import java.util.List;

/**
 * Manhattan's area API (dev.baritonequeue.api.ManhattanAreas), reached by reflection only, so Heavymatica works
 * without Manhattan. Client thread only. STUB: signatures are final, P6a implements the bodies.
 */
public final class ManhattanBridge {
    private ManhattanBridge() {}

    public enum SendResult { OK, SCRIPT_OWNED, IN_USE, INVALID, NOT_IN_WORLD, UNAVAILABLE }

    public record NamedBoxes(String name, List<Box3i> boxes) {}

    /** Manhattan is installed and its API is version 1 or newer. */
    public static boolean available() {
        return false;
    }

    public static SendResult put(String areaName, List<Box3i> boxes, RegionRole role, boolean replaceScriptOwned) {
        return SendResult.UNAVAILABLE;
    }

    public static boolean remove(String areaName) {
        return false;
    }

    /** Manhattan's current selection (empty without Manhattan). */
    public static List<Box3i> selection() {
        return List.of();
    }

    /** Every protected area Manhattan knows (empty without Manhattan). */
    public static List<NamedBoxes> protectedAreas() {
        return List.of();
    }

    /** Registers the Manhattan change listener (marks our placements out of sync). Called once at start. */
    public static void init() {}
}
