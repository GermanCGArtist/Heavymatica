package dev.heavymatica.ui;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.vitruvius.api.VitruviusApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Registers the [H] list entries, the shape action and the keys (H opens the menu, "Cycle cutaway" is unbound). */
public final class HeavymaticaUi {
    private static final double NEAR = 16.0;

    // one entry object per placement, so the list's selection keeps working across rebuilds
    private static final Map<UUID, HeavymaticEntry> ENTRIES = new HashMap<>();
    private static KeyMapping openKey, cutawayKey;

    private HeavymaticaUi() {}

    public static void init() {
        VitruviusApi.registerListEntries(HeavymaticaUi::entries);
        PlacementStore.addListener(VitruviusApi::refreshList);
        VitruviusApi.registerFolderAction("Export",
                "Scans every heavymatic in this folder and its subfolders and copies one text for Manhattan / an LLM.",
                dev.heavymatica.export.FolderExport::run);
        VitruviusApi.registerShapeAction("Save as Heavymatic",
                "Splits the shape into exact boxes and saves them as a heavymatic preset, placed where the preview is.",
                ShapeAction::run);
        VitruviusApi.registerShapeOutput(new ShapeOutputImpl());   // the editor's Shape generator window: live preview + create

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("heavymatica", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heavymatica.open", InputConstants.KEY_H, category));
        cutawayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.heavymatica.cycle_cutaway", InputConstants.UNKNOWN.getValue() /* unbound */, category));
        ClientTickEvents.END_CLIENT_TICK.register(HeavymaticaUi::tick);
    }

    private static List<HeavymaticEntry> entries() {
        List<HeavymaticEntry> out = new ArrayList<>();
        ENTRIES.keySet().retainAll(PlacementStore.all().stream().map(HeavymaticPlacement::id).toList());
        for (HeavymaticPlacement p : PlacementStore.all()) out.add(ENTRIES.computeIfAbsent(p.id(), id -> new HeavymaticEntry(p)));
        return out;
    }

    private static void tick(Minecraft mc) {
        while (openKey.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(new GuiPresetBrowser(null));
        }
        while (cutawayKey.consumeClick()) {
            if (mc.player != null) cycleCutaway(mc);
        }
    }

    /** Cycles the cutaway of the placements selected in the list, else of the nearest one within 16 blocks. */
    private static void cycleCutaway(Minecraft mc) {
        List<HeavymaticPlacement> targets = new ArrayList<>();
        Set<String> selected = VitruviusApi.selectedEntryIds();
        for (String id : selected) PlacementStore.byEntryId(id).ifPresent(targets::add);

        if (targets.isEmpty()) {
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            double best = NEAR;
            HeavymaticPlacement nearest = null;
            for (HeavymaticPlacement p : PlacementStore.all()) {
                double d;
                try {
                    d = distance(p.worldBounds(), px, py, pz);
                } catch (RuntimeException e) {
                    continue;   // no boxes or invalid array
                }
                if (d <= best) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest != null) targets.add(nearest);
        }
        if (targets.isEmpty()) {
            CreateFromSelection.say("No heavymatic selected or within " + (int) NEAR + " blocks.");
            return;
        }
        for (HeavymaticPlacement p : targets) {
            CutawayMode next = WidgetHeavymaticRow.nextCutaway(p.cutaway());
            p.setCutaway(next);
            CreateFromSelection.say(p.presetName() + ": cutaway " + next.name());
        }
    }

    /** Distance from a point to a block box (0 inside); the box covers whole blocks, so max + 1. */
    private static double distance(Box3i b, double x, double y, double z) {
        double dx = Math.max(Math.max(b.minX() - x, 0), x - (b.maxX() + 1));
        double dy = Math.max(Math.max(b.minY() - y, 0), y - (b.maxY() + 1));
        double dz = Math.max(Math.max(b.minZ() - z, 0), z - (b.maxZ() + 1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
