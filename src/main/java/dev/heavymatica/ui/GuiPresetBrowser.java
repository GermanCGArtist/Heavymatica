package dev.heavymatica.ui;

import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.heavymatica.place.PresetLibrary;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * The Heavymatica menu (key H): the saved presets with Place (at your block position) and Delete (asks first, Shift = at once), and the
 * buttons that make a new preset from the Manhattan or the Litematica selection.
 */
public final class GuiPresetBrowser extends GuiBase {
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, ROW = 22;

    private int page;

    public GuiPresetBrowser(Screen parent) {
        setParent(parent);
        setTitle("Heavymatica");
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 20, y = 30;

        ButtonGeneric fromMh = new ButtonGeneric(x, y, 190, 20, "New from Manhattan selection",
                "Makes a preset from the boxes selected in Manhattan.");
        // only when Manhattan is there and has a selection
        boolean mhAvailable = ManhattanBridge.available();
        boolean mhSelection = mhAvailable && !CreateFromSelection.manhattanBoxes().isEmpty();
        fromMh.setEnabled(mhSelection);
        if (!mhAvailable) fromMh.setHoverStrings("Manhattan is not installed.");
        else if (!mhSelection) fromMh.setHoverStrings("Nothing is selected in Manhattan.");
        addButton(fromMh, (b, mouse) -> CreateFromSelection.ask(this, "New heavymatic from Manhattan selection", "selection",
                CreateFromSelection.manhattanBoxes()));

        ButtonGeneric fromLm = new ButtonGeneric(x + 196, y, 190, 20, "New from Litematica selection",
                "Makes a preset from all boxes of the current Litematica area selection.");
        fromLm.setEnabled(!CreateFromSelection.litematicaBoxes().isEmpty());
        addButton(fromLm, (b, mouse) -> CreateFromSelection.ask(this, "New heavymatic from Litematica selection", "selection",
                CreateFromSelection.litematicaBoxes()));
        y += ROW + 8;

        List<String> names = PresetLibrary.names();
        int top = y + 14;
        int perPage = Math.max(1, (getScreenHeight() - top - 36) / ROW);
        int pages = Math.max(1, (names.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        addLabel(x, y, 300, 10, WHITE, names.isEmpty() ? "No presets yet." : "Presets (" + names.size() + ")");
        y = top;

        for (int i = page * perPage; i < Math.min(names.size(), (page + 1) * perPage); i++) {
            String name = names.get(i);
            HeavymaticSpec spec = PresetLibrary.load(name).orElse(null);
            String info = spec == null ? "unreadable" : spec.regions().size() + " regions, " + spec.boxCount() + " boxes";
            addLabel(x, y + 6, 200, 10, WHITE, ellipsize(name, 200));
            addLabel(x + 206, y + 6, 160, 10, GRAY, info);
            ButtonGeneric place = new ButtonGeneric(x + 372, y, 50, 20, "Place", "Places it at your block position.");
            place.setEnabled(spec != null);
            addButton(place, (b, mouse) -> place(name, spec));
            ButtonGeneric del = new ButtonGeneric(x + 426, y, 50, 20, "Delete", "Deletes the preset file (asks first; Shift + click skips the question).",
                    "Placements already in the world stay.");
            addButton(del, (b, mouse) -> ConfirmDelete.run("Delete preset",
                    "Delete preset " + name + "? Placements already in the world stay.",
                    () -> {
                        PresetLibrary.delete(name);
                        initGui();
                    }));
            y += ROW;
        }

        int by = getScreenHeight() - 28;
        if (pages > 1) {
            ButtonGeneric prev = new ButtonGeneric(x, by, 40, 20, "<");
            prev.setEnabled(page > 0);
            addButton(prev, (b, mouse) -> { page--; initGui(); });
            addLabel(x + 46, by + 6, 60, 10, GRAY, (page + 1) + " / " + pages);
            ButtonGeneric next = new ButtonGeneric(x + 100, by, 40, 20, ">");
            next.setEnabled(page < pages - 1);
            addButton(next, (b, mouse) -> { page++; initGui(); });
        }
        addButton(new ButtonGeneric(x + 160, by, 60, 20, "Close"), (b, mouse) -> closeGui(true));
    }

    private void place(String name, HeavymaticSpec spec) {
        var player = Minecraft.getInstance().player;
        if (player == null || spec == null) return;
        var pos = player.blockPosition();
        PlacementStore.add(HeavymaticPlacement.create(spec, name, pos.getX(), pos.getY(), pos.getZ()));
        CreateFromSelection.say("Placed '" + name + "' at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".");
        closeGui(true);
    }

    private String ellipsize(String text, int width) {
        var font = Minecraft.getInstance().font;
        while (text.length() > 4 && font.width(text) > width) text = text.substring(0, text.length() - 4) + "...";
        return text;
    }
}
