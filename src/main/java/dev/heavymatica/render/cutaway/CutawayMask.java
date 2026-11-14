package dev.heavymatica.render.cutaway;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The terrain cutaway mask. Only the chunk-section MESHER consults it (see CutawayRegionMixin); the world data
 * (ClientLevel / Level#getBlockState) is never touched.
 */
public final class CutawayMask {
    private static volatile CutawayIndex current = CutawayIndex.EMPTY;
    private static boolean pending;
    private static boolean noteShown;
    private static boolean noteWanted;
    private static Object lastLevel;
    private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

    private CutawayMask() {}

    /** Called from the mesher threads. */
    public static boolean cut(int x, int y, int z) {
        return current.cut(x, y, z);
    }

    public static void init() {
        PlacementStore.addListener(() -> pending = true);
        ClientTickEvents.END_CLIENT_TICK.register(CutawayMask::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null) pending = true;   // new world: remesh whatever the mask now covers
        }
        if (noteWanted && !noteShown && mc.player != null) {
            noteShown = true;
            mc.gui.getChat().addClientSystemMessage(
                    Component.literal("Heavymatica: terrain cutaway is off with Sodium; wireframes still work."));
        }
        if (!pending) return;
        pending = false;
        rebuild(mc);
    }

    private static void rebuild(Minecraft mc) {
        List<Box3i> cuts = new ArrayList<>();
        for (HeavymaticPlacement p : PlacementStore.all()) {
            if (!p.enabled() || p.cutaway() != CutawayMode.ON) continue;
            for (HeavymaticRegion r : p.spec().regions()) {
                if (!r.role().cutsTerrain() || !p.isRegionVisible(r.name())) continue;
                cuts.addAll(p.worldBoxes(r.name()));
            }
        }
        if (SODIUM) {
            if (!cuts.isEmpty()) noteWanted = true;
            return;   // the mixin is not applied with Sodium: nothing to remesh
        }
        CutawayIndex old = current;
        CutawayIndex next;
        try {
            next = CutawayIndex.of(cuts);
        } catch (IllegalArgumentException e) {
            HeavymaticaMod.LOGGER.warn("Cutaway skipped: {}", e.getMessage());
            next = CutawayIndex.EMPTY;
        }
        current = next;   // publish before marking dirty so re-meshing sees the new mask
        if (mc.levelRenderer == null || mc.level == null) return;
        long[] dirty = CutawayIndex.withNeighbors(CutawayIndex.dirtyUnion(old, next));
        for (long k : dirty) {
            mc.levelRenderer.setSectionDirty(CutawayIndex.keyX(k), CutawayIndex.keyY(k), CutawayIndex.keyZ(k));
        }
    }
}
