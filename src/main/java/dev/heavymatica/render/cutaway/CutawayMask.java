package dev.heavymatica.render.cutaway;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * The terrain cutaway mask. Only the chunk-section MESHER consults it (CutawayRegionMixin, or SodiumCutawayMixin with Sodium); the world data
 * (ClientLevel / Level#getBlockState) is never touched.
 */
public final class CutawayMask {
    private static volatile CutawayIndex current = CutawayIndex.EMPTY;
    private static boolean pending;
    private static Object lastLevel;
    private static boolean editorObjects;               // Vitruvius 2.0 editor objects are available
    private static List<Box3i> lastEditorCuts = List.of();
    private static int tickCount;

    private CutawayMask() {}

    /** Called from the mesher threads. */
    public static boolean cut(int x, int y, int z) {
        return current.cut(x, y, z);
    }

    /** What the mesher sees at a cut position: the block's fluid alone (air, water or lava). Mesher threads. */
    public static BlockState fluidOnly(BlockState real) {
        if (real == null) return Blocks.AIR.defaultBlockState();
        FluidState f = real.getFluidState();
        return f.isEmpty() ? Blocks.AIR.defaultBlockState() : f.createLegacyBlock();
    }

    public static void init() {
        PlacementStore.addListener(() -> pending = true);
        ClientTickEvents.END_CLIENT_TICK.register(CutawayMask::tick);
    }

    /** Called once when the editor object type is registered: the mask then also follows the editor's heavymatics. */
    public static void enableEditorObjects() {
        editorObjects = true;
        pending = true;
    }

    /** The cut boxes of the editor scene; empty on any failure (the editor objects are optional). */
    private static List<Box3i> editorCuts() {
        if (!editorObjects) return List.of();
        try {
            return dev.heavymatica.editor.EditorObjects.cutBoxes();
        } catch (Throwable t) {
            editorObjects = false;
            HeavymaticaMod.LOGGER.warn("Editor heavymatics left out of the cutaway: {}", t.toString());
            return List.of();
        }
    }

    private static void tick(Minecraft mc) {
        // the editor changes its objects without telling us: look every half second and rebuild when the boxes differ
        if (editorObjects && ++tickCount % 10 == 0 && !pending && !editorCuts().equals(lastEditorCuts)) pending = true;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null) pending = true;   // new world: remesh whatever the mask now covers
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
        List<Box3i> editor = editorCuts();
        cuts.addAll(editor);
        lastEditorCuts = editor;
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
