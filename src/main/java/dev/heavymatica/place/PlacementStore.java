package dev.heavymatica.place;

import dev.heavymatica.HeavymaticaMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The placements of the current world; saved to config/heavymatica/placements/&lt;WorldKey&gt;.json. Client thread only. */
public final class PlacementStore {
    private static final int SAVE_DELAY_TICKS = 20;

    private static final List<HeavymaticPlacement> PLACEMENTS = new ArrayList<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static String worldKey;
    private static boolean dirty;
    private static int ticksSinceChange;

    private PlacementStore() {
    }

    public static void init() {
        HeavymaticPlacement.changeHook = p -> changed();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(PlacementStore::onJoin));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (dirty && ++ticksSinceChange >= SAVE_DELAY_TICKS) saveNow();
        });
    }

    /** Whether the placements of a world are loaded. */
    public static boolean ready() {
        return worldKey != null;
    }

    private static HeavymaticPlacement preview;

    /** An unsaved placement that is only drawn (the live preview of Vitruvius's Shape generator); null removes it. */
    public static void setPreview(HeavymaticPlacement p) {
        if (preview == null && p == null) return;
        preview = p;
        fire();      // redraw; no save
    }

    /** Everything that is drawn: the placements and the preview. */
    public static List<HeavymaticPlacement> renderables() {
        if (preview == null) return all();
        List<HeavymaticPlacement> out = new ArrayList<>(PLACEMENTS);
        out.add(preview);
        return out;
    }

    public static List<HeavymaticPlacement> all() {
        return List.copyOf(PLACEMENTS);
    }

    public static Optional<HeavymaticPlacement> byId(UUID id) {
        for (HeavymaticPlacement p : PLACEMENTS) if (p.id().equals(id)) return Optional.of(p);
        return Optional.empty();
    }

    public static Optional<HeavymaticPlacement> byEntryId(String entryId) {
        for (HeavymaticPlacement p : PLACEMENTS) if (p.entryId().equals(entryId)) return Optional.of(p);
        return Optional.empty();
    }

    public static void add(HeavymaticPlacement p) {
        PLACEMENTS.add(p);
        changed();
    }

    public static void remove(HeavymaticPlacement p) {
        if (PLACEMENTS.remove(p)) changed();
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void changed() {
        if (worldKey != null) {
            dirty = true;
            ticksSinceChange = 0;
        }
        fire();
    }

    private static void fire() {
        for (Runnable r : List.copyOf(LISTENERS)) {
            try {
                r.run();
            } catch (RuntimeException e) {
                HeavymaticaMod.LOGGER.warn("Placement listener failed", e);
            }
        }
    }

    private static Path fileFor(String key) {
        return FabricLoader.getInstance().getConfigDir().resolve("heavymatica").resolve("placements").resolve(key + ".json");
    }

    private static void onJoin() {
        if (worldKey != null) onLeave();
        String key = WorldKey.current();
        PLACEMENTS.clear();
        dirty = false;
        worldKey = key;
        if (key != null) {
            Path f = fileFor(key);
            if (Files.isRegularFile(f)) {
                try {
                    PLACEMENTS.addAll(PlacementJson.read(Files.readString(f, StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    HeavymaticaMod.LOGGER.error("Unreadable placements file {}, starting empty", f, e);
                    PLACEMENTS.clear();
                    try {
                        Files.move(f, f.resolveSibling(f.getFileName() + ".broken-" + System.currentTimeMillis()),
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException io) {
                        HeavymaticaMod.LOGGER.warn("Could not rename broken file", io);
                    }
                }
            }
        }
        fire();
    }

    private static void onLeave() {
        if (dirty) saveNow();
        PLACEMENTS.clear();
        preview = null;
        worldKey = null;
        dirty = false;
        fire();
    }

    private static void saveNow() {
        dirty = false;
        if (worldKey == null) return;
        Path f = fileFor(worldKey);
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, PlacementJson.write(PLACEMENTS), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            HeavymaticaMod.LOGGER.error("Cannot save placements to {}", f, e);
        }
    }
}
