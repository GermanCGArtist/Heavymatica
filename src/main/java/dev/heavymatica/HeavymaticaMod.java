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
        dev.heavymatica.place.PlacementStore.init();   // placements, store, preset library
        dev.heavymatica.manhattan.ManhattanBridge.init();   // Manhattan link (no-op without Manhattan)
        dev.heavymatica.export.LlmExport.init();            // terrain scans for the LLM export
        dev.heavymatica.render.HeavymaticaRender.init();    // wireframes, hazards, cutaway
        dev.heavymatica.ui.HeavymaticaUi.init();            // [H] list entries, shape action, keys
    }
}
