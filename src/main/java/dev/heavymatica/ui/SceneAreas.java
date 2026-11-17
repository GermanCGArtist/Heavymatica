package dev.heavymatica.ui;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.AreaData;

import java.util.List;

/**
 * Guard for the Vitruvius API v2 (scene objects). The v2 classes are only touched through {@link AreaObjects},
 * and only when this says v2 is there, so older Vitruvius versions (1.5 / 1.6) keep the old behaviour.
 */
public final class SceneAreas {
    private static Boolean available;

    private SceneAreas() {}

    /** Vitruvius has SceneObjectType and VitruviusApi.registerObjectType / objectsOfType. */
    public static boolean available() {
        if (available == null) {
            try {
                Class<?> type = Class.forName("dev.vitruvius.api.SceneObjectType");
                Class<?> api = Class.forName("dev.vitruvius.api.VitruviusApi");
                api.getMethod("registerObjectType", type);
                api.getMethod("objectsOfType", String.class);
                available = true;
            } catch (ReflectiveOperationException | LinkageError e) {
                available = false;
            }
        }
        return available;
    }

    public static void init() {
        if (!available()) {
            HeavymaticaMod.LOGGER.info("Vitruvius has no object API (v2); dig / protect areas stay list entries");
            return;
        }
        try {
            dev.vitruvius.api.VitruviusApi.registerObjectType(new AreaObjects());
        } catch (RuntimeException | LinkageError e) {
            HeavymaticaMod.LOGGER.warn("Could not register the dig / protect area object type", e);
            available = false;
        }
    }

    /** Plans of all visible scene areas; empty without API v2 or an editor session. Client thread. */
    public static List<AreaData.Plan> plans() {
        if (!available()) return List.of();
        try {
            return AreaObjects.plans();
        } catch (RuntimeException | LinkageError e) {
            HeavymaticaMod.LOGGER.warn("Reading scene areas failed", e);
            return List.of();
        }
    }
}
