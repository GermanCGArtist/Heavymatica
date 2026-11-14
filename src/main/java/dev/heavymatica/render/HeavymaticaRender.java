package dev.heavymatica.render;

import dev.heavymatica.render.cutaway.CutawayMask;

/** Registers wireframes, hazard highlights and the cutaway. */
public final class HeavymaticaRender {
    private HeavymaticaRender() {}

    public static void init() {
        WireframeRenderer.init();
        HazardHighlighter.init();
        CutawayMask.init();   // also shows the one-time "off with Sodium" note
    }
}
