package dev.heavymatica;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HeavymaticaMod implements ClientModInitializer {
    public static final String MOD_ID = "heavymatica";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Heavymatica initialised");
        // place.init();      // placements, store, preset library
        // ui.init();         // list entries, shape action
        // render.init();     // wireframes, hazards, cutaway
        // manhattan.init();  // Manhattan bridge, sync state
        // export.init();     // terrain scanner, LLM export
    }
}
