package dev.heavymatica.manhattan;

import dev.heavymatica.place.HeavymaticPlacement;

/**
 * "Send to Manhattan" for one placement, with its confirm dialogs (many boxes, replacing a script's area) and
 * messages. Client thread only. STUB: signatures are final, P6a implements the bodies.
 */
public final class SendFlow {
    private SendFlow() {}

    /** Starts sending; may open confirm screens and returns at once. */
    public static void send(HeavymaticPlacement placement) {}

    /** Removes the placement's areas from Manhattan (only ones Heavymatica owns). */
    public static void unsend(HeavymaticPlacement placement) {}
}
