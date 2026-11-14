package dev.heavymatica.export;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.schemarray.api.FolderActionContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Export" on a folder row: scans the folder's heavymatics one after another with {@link TerrainScanner}, then copies
 * and saves one combined text (see {@link FolderExportText}). Client thread only.
 */
public final class FolderExport {
    private static Job running;

    private FolderExport() {}

    /** The folder action handler. */
    public static void run(FolderActionContext ctx) {
        if (running != null) {
            chat("A folder export is already running (" + running.folderName + ").");
            return;
        }
        Set<String> ids = new HashSet<>(ctx.entryIds());
        List<HeavymaticPlacement> placements = new ArrayList<>();
        for (HeavymaticPlacement p : PlacementStore.all()) if (ids.contains(p.entryId())) placements.add(p);
        if (placements.isEmpty()) {
            chat("No heavymatics in folder " + ctx.folderName() + ".");
            return;
        }
        List<String> order = ctx.entryIds();   // keep the folder's list order
        placements.sort(Comparator.comparingInt(p -> order.indexOf(p.entryId())));
        chat("Scanning folder " + ctx.folderName() + " (" + placements.size() + " heavymatics)...");
        running = new Job(ctx.folderName(), placements);
        running.startNext();
    }

    public static boolean isRunning() {
        return running != null;
    }

    /** Called every client tick: restarts the current scan if a single export of the same placement replaced it. */
    static void tick() {
        Job j = running;
        if (j == null || j.current == null || TerrainScanner.isScanning(j.current)) return;
        if (Minecraft.getInstance().level == null) return;   // the scanner drops everything without a world
        // Lost its scan (a single export replaced it, or the callback failed): retry a few times, then skip it.
        if (++j.restarts <= 3) {
            j.startCurrent();
        } else {
            HeavymaticaMod.LOGGER.warn("folder export: skipping {} after repeated scan failures", j.current.presetName());
            j.next++;
            j.startNext();
        }
    }

    /** Drops a running folder export silently (disconnect). */
    static void cancel() {
        running = null;
    }

    private static final class Job {
        final String folderName;
        final List<HeavymaticPlacement> placements;
        final List<FolderExportText.Block> blocks = new ArrayList<>();
        int next;
        int restarts;
        HeavymaticPlacement current;

        Job(String folderName, List<HeavymaticPlacement> placements) {
            this.folderName = folderName;
            this.placements = placements;
        }

        void startNext() {
            restarts = 0;
            if (next >= placements.size()) {
                current = null;
                finish(this);
                return;
            }
            current = placements.get(next);
            startCurrent();
        }

        void startCurrent() {
            HeavymaticPlacement p = current;
            TerrainScanner.start(p, scans -> {
                if (running != this || current != p) return;
                List<String> areas = new ArrayList<>();
                p.spec().regions().forEach(r -> areas.add(p.manhattanAreaName(r)));
                blocks.add(new FolderExportText.Block(p.presetName(), areas, LlmExportText.build(p, scans)));
                next++;
                startNext();
            });
        }
    }

    private static void finish(Job j) {
        running = null;
        String text = FolderExportText.combine(j.folderName, j.blocks);
        int lines = LlmExportText.lineCount(text);
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(text);
        Path file = FabricLoader.getInstance().getConfigDir().resolve("heavymatica").resolve("exports")
                .resolve(LlmExportText.sanitize(j.folderName) + ".txt");
        String head = "Copied folder " + j.folderName + ": " + j.blocks.size() + " heavymatics (" + lines + " lines)";
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            chat(head + ", also saved to " + file);
        } catch (IOException e) {
            HeavymaticaMod.LOGGER.warn("could not save folder export", e);
            chat(head + "; saving to file failed: " + e.getMessage());
        }
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }
}
