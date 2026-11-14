package dev.heavymatica.export;

import dev.heavymatica.place.HeavymaticPlacement;

/**
 * "Export for Manhattan / LLM": starts a background terrain scan, then copies the text to the clipboard and saves it
 * to config/heavymatica/exports/. Client thread only. STUB: signatures are final, P6b implements the bodies.
 */
public final class LlmExport {
    private LlmExport() {}

    /** Starts the export; progress and the result are reported in chat. */
    public static void start(HeavymaticPlacement placement) {}

    /** 0..1 while an export of this placement is scanning, -1 otherwise (for a progress label on the button). */
    public static float progress(HeavymaticPlacement placement) {
        return -1f;
    }

    /** Registers the tick that runs the scans. Called once at start. */
    public static void init() {}
}
