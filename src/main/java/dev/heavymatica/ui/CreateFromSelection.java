package dev.heavymatica.ui;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.heavymatica.place.PresetLibrary;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Makes a new one-region heavymatic from a selection (Manhattan's or Litematica's), saves it and places it on it. */
public final class CreateFromSelection {
    private CreateFromSelection() {}

    /** Manhattan's selection boxes (empty without Manhattan). */
    public static List<Box3i> manhattanBoxes() {
        return ManhattanBridge.available() ? ManhattanBridge.selection() : List.of();
    }

    /** All boxes of Litematica's current area selection (empty if there is none). */
    public static List<Box3i> litematicaBoxes() {
        List<Box3i> out = new ArrayList<>();
        AreaSelection sel = DataManager.getSelectionManager().getCurrentSelection();
        if (sel == null) return out;
        for (Box b : sel.getAllSubRegionBoxes()) {
            BlockPos p1 = b.getPos1(), p2 = b.getPos2();
            if (p1 == null || p2 == null) continue;   // a box with one corner set is not a box yet
            out.add(Box3i.of(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ()));
        }
        return out;
    }

    /** Asks for name and role, then saves and places. The boxes are world coordinates. */
    public static void ask(Screen parent, String heading, String defaultName, List<Box3i> worldBoxes) {
        if (worldBoxes.isEmpty()) {
            say("The selection is empty.");
            return;
        }
        GuiBase.openGui(new NameRoleDialog(parent, heading, defaultName, worldBoxes.size(), false,
                (name, role) -> saveAndPlace(name, role, worldBoxes)));
    }

    /**
     * Builds the one-region spec, shifted so its bounds start at (0,0,0) (the preset convention), saves it, and places
     * it with the origin at the boxes' min corner, so it lies exactly on the original boxes.
     */
    public static void saveAndPlace(String name, RegionRole role, List<Box3i> worldBoxes) {
        if (worldBoxes.isEmpty() || worldBoxes.size() > NameRoleDialog.MAX_BOXES) {
            say("Not saved: " + worldBoxes.size() + " boxes (1 to " + NameRoleDialog.MAX_BOXES + " allowed).");
            return;
        }
        Box3i bounds = worldBoxes.get(0);
        for (Box3i b : worldBoxes) bounds = bounds.union(b);
        int ox = bounds.minX(), oy = bounds.minY(), oz = bounds.minZ();
        List<Box3i> local = new ArrayList<>();
        for (Box3i b : worldBoxes) local.add(b.offset(-ox, -oy, -oz));
        HeavymaticSpec spec = new HeavymaticSpec(name, "", "",
                List.of(new HeavymaticRegion(name, role, role.defaultColor(), local)));
        PresetLibrary.save(spec);
        PlacementStore.add(HeavymaticPlacement.create(spec, name, ox, oy, oz));
        say("Saved preset '" + name + "' (" + local.size() + " boxes, " + role.name() + ") and placed it.");
    }

    /** A chat message "[Heavymatica] ...". */
    public static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Heavymatica] " + text));
    }
}
