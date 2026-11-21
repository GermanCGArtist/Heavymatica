package dev.heavymatica.manhattan;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.place.PlacementStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * Manhattan's area API (dev.baritonequeue.api.ManhattanAreas), reached by reflection only, so Heavymatica works
 * without Manhattan. Client thread only.
 */
public final class ManhattanBridge {
    private static final String API_CLASS = "dev.baritonequeue.api.ManhattanAreas";
    private static final int MAX_LISTENER_ATTEMPTS = 200;

    private static boolean checked;
    private static ManhattanApi api;
    private static boolean failureLogged;
    private static boolean listenerRegistered;
    private static int listenerAttempts;

    private ManhattanBridge() {}

    public enum SendResult { OK, SCRIPT_OWNED, IN_USE, INVALID, NOT_IN_WORLD, UNAVAILABLE }

    public record NamedBoxes(String name, List<Box3i> boxes) {}

    private static ManhattanApi api() {
        if (!checked) {
            checked = true;
            if (FabricLoader.getInstance().isModLoaded("manhattan") || FabricLoader.getInstance().isModLoaded("baritonequeue")) {
                try {
                    api = ManhattanApi.tryCreate(Class.forName(API_CLASS));
                    if (api == null) {
                        HeavymaticaMod.LOGGER.info("Manhattan found, but its area API is missing or older than version 1; send disabled");
                    }
                } catch (ClassNotFoundException | LinkageError e) {
                    HeavymaticaMod.LOGGER.info("Manhattan found, but {} cannot be loaded ({}); send disabled", API_CLASS, e.toString());
                }
            }
        }
        return api;
    }

    /** Manhattan is installed and its API is version 1 or newer. */
    public static boolean available() {
        return api() != null;
    }

    private static void failed(Throwable t) {
        if (!failureLogged) {
            failureLogged = true;
            HeavymaticaMod.LOGGER.warn("Manhattan API call failed (further failures are not logged)", t);
        }
    }

    public static SendResult put(String areaName, List<Box3i> boxes, RegionRole role, boolean replaceScriptOwned) {
        ManhattanApi a = api();
        if (a == null) return SendResult.UNAVAILABLE;
        try {
            return a.put(areaName, boxes, role, replaceScriptOwned);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed(e);
            return SendResult.UNAVAILABLE;
        }
    }

    public static boolean remove(String areaName) {
        ManhattanApi a = api();
        if (a == null) return false;
        try {
            return a.remove(areaName);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed(e);
            return false;
        }
    }

    /** Manhattan's current selection (empty without Manhattan). */
    public static List<Box3i> selection() {
        ManhattanApi a = api();
        if (a == null) return List.of();
        try {
            return a.selection();
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed(e);
            return List.of();
        }
    }

    /** Every protected area Manhattan knows (empty without Manhattan). */
    public static List<NamedBoxes> protectedAreas() {
        ManhattanApi a = api();
        if (a == null) return List.of();
        try {
            return a.protectedAreas();
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed(e);
            return List.of();
        }
    }

    /** Registers the Manhattan change listener (marks our placements out of sync). Called once at start. */
    public static void init() {
        if (!available()) return;
        // Manhattan's addListener checks the client thread and needs Manhattan initialised: do it on a client tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> registerListener());
    }

    private static void registerListener() {
        if (listenerRegistered || listenerAttempts >= MAX_LISTENER_ATTEMPTS) return;
        listenerAttempts++;
        ManhattanApi a = api();
        if (a == null) return;
        try {
            a.addListener((name, owner) -> SyncState.onAreaChanged(PlacementStore.all(), name, owner));
            listenerRegistered = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (listenerAttempts >= MAX_LISTENER_ATTEMPTS) failed(e);
        }
    }
}
