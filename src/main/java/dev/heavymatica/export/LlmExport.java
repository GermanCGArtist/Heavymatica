package dev.heavymatica.export;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.place.HeavymaticPlacement;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * "Export for Manhattan / LLM": starts a background terrain scan, then copies the text to the clipboard and saves it
 * to config/heavymatica/exports/. Client thread only.
 */
public final class LlmExport {
    private LlmExport() {}

    /** Starts the export; progress and the result are reported in chat. */
    public static void start(HeavymaticPlacement placement) {
        chat("Scanning " + placement.presetName() + "...");
        TerrainScanner.start(placement, scans -> finish(placement, scans));
    }

    /** 0..1 while an export of this placement is scanning, -1 otherwise (for a progress label on the button). */
    public static float progress(HeavymaticPlacement placement) {
        return TerrainScanner.progress(placement);
    }

    /** Registers the tick that runs the scans. Called once at start. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> TerrainScanner.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TerrainScanner.cancelAll());
    }

    private static void finish(HeavymaticPlacement p, List<RegionScan> scans) {
        String text = LlmExportText.build(p, scans);
        int lines = LlmExportText.lineCount(text);
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(text);
        Path file = FabricLoader.getInstance().getConfigDir().resolve("heavymatica").resolve("exports")
                .resolve(LlmExportText.sanitize(p.presetName()) + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            chat("Copied " + p.presetName() + " for Manhattan / LLM (" + lines + " lines), also saved to " + file);
        } catch (IOException e) {
            HeavymaticaMod.LOGGER.warn("could not save export", e);
            chat("Copied " + p.presetName() + " for Manhattan / LLM (" + lines + " lines); saving to file failed: " + e.getMessage());
        }
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }
}
