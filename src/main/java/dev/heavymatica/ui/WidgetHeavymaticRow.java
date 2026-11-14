package dev.heavymatica.ui;

import dev.heavymatica.export.LlmExport;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.manhattan.SendFlow;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.schemarray.api.SchematicArrayApi;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.Minecraft;

/**
 * A heavymatic row in the placement list: tint and color bar like placement rows, a "[H]" badge where Litematica
 * draws its schematic icon, the name, a status line, and on the right the buttons Configure, Send, Export, Cutaway,
 * Enabled and Remove.
 */
public final class WidgetHeavymaticRow extends WidgetListEntryBase<HeavymaticEntry> {
    private static final int ORANGE = 0xFFFFAA00, GREEN = 0xFF55FF55, GRAY = 0xFFA0A0A0, WHITE = 0xFFFFFFFF;

    private final boolean isOdd;
    private final HeavymaticPlacement placement;
    private final ButtonGeneric exportButton;
    private final int buttonsStartX;
    private final String status;
    private final int statusColor;

    public WidgetHeavymaticRow(int x, int y, int width, int height, boolean isOdd, HeavymaticEntry entry, int listIndex) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = isOdd;
        this.placement = entry.placement();
        var font = Minecraft.getInstance().font;
        int by = y + 1;
        int bx = x + width - 2;   // right edge of the next button

        // Remove (Shift + click): only the placement; the areas stay in Manhattan
        ButtonGeneric remove = rightButton(bx, by, "Remove", font.width("Remove") + 10);
        remove.setHoverStrings("Hold Shift and click to remove this placement.", "The areas already sent stay in Manhattan.");
        addButton(remove, (b, mouse) -> {
            if (!GuiBase.isShiftDown()) return;
            PlacementStore.remove(placement);
        });
        bx = remove.getX() - 1;

        ButtonGeneric enabled = rightButton(bx, by, placement.enabled() ? "§aEnabled" : "§cDisabled",
                font.width("Disabled") + 10);
        enabled.setHoverStrings("Shows or hides this placement (wireframes and cutaway).");
        addButton(enabled, (b, mouse) -> placement.setEnabled(!placement.enabled()));
        bx = enabled.getX() - 1;

        ButtonGeneric cut = rightButton(bx, by, "Cut: " + placement.cutaway().name(), font.width("Cut: WIREFRAME") + 10);
        cut.setHoverStrings("Terrain cutaway: ON, WIREFRAME (outline only) or OFF.");
        addButton(cut, (b, mouse) -> placement.setCutaway(nextCutaway(placement.cutaway())));
        bx = cut.getX() - 1;

        exportButton = rightButton(bx, by, "Export", font.width("Export 100%") + 10);
        exportButton.setHoverStrings("Scans the terrain and copies a text for Manhattan / an LLM.");
        addButton(exportButton, (b, mouse) -> LlmExport.start(placement));
        bx = exportButton.getX() - 1;

        if (ManhattanBridge.available()) {
            ButtonGeneric send = rightButton(bx, by, "Send", font.width("Send") + 10);
            send.setHoverStrings("Sends the areas of this placement to Manhattan.");
            addButton(send, (b, mouse) -> SendFlow.send(placement));
            bx = send.getX() - 1;
        }

        ButtonGeneric config = rightButton(bx, by, "Configure", font.width("Configure") + 10);
        addButton(config, (b, mouse) -> GuiBase.openGui(new GuiHeavymaticConfig(placement, Minecraft.getInstance().screen)));
        bx = config.getX() - 1;
        this.buttonsStartX = bx;

        // status line: "2 regions, 14 boxes" and the sync state
        String counts;
        try {
            int boxes = 0;
            for (var l : placement.worldBoxesByRegion().values()) boxes += l.size();
            int regions = placement.spec().regions().size();
            counts = regions + (regions == 1 ? " region, " : " regions, ") + boxes + (boxes == 1 ? " box" : " boxes");
        } catch (RuntimeException e) {
            counts = "invalid array settings";
        }
        if (placement.outOfSync()) {
            status = counts + ", out of sync";
            statusColor = ORANGE;
        } else if (placement.lastSentHash() != 0) {
            status = counts + ", sent";
            statusColor = GREEN;
        } else {
            status = counts;
            statusColor = GRAY;
        }
    }

    /** A button whose right edge is at rightX (explicit width, so its label can change). */
    private static ButtonGeneric rightButton(int rightX, int y, String label, int width) {
        return new ButtonGeneric(rightX - width, y, width, 20, label);
    }

    static CutawayMode nextCutaway(CutawayMode m) {
        return switch (m) {
            case ON -> CutawayMode.WIREFRAME;
            case WIREFRAME -> CutawayMode.OFF;
            case OFF -> CutawayMode.ON;
        };
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        int bg = selected || isMouseOver(mouseX, mouseY) ? 0xA0707070 : isOdd ? 0xA0101010 : 0xA0303030;
        RenderUtils.drawRect(ctx, x, y, width, height, bg);
        // the row color like a placement row: light tint and a 3 px bar
        int tint = SchematicArrayApi.colorOf(entry.id());
        if (tint != 0) {
            int rgb = tint & 0xFFFFFF;
            RenderUtils.drawRect(ctx, x, y, width, height, 0x28000000 | rgb);
            RenderUtils.drawRect(ctx, x, y, 3, height, 0xFF000000 | rgb);
        }

        // live label of the export button
        float p = LlmExport.progress(placement);
        exportButton.setDisplayString(p >= 0 ? "Export " + Math.round(p * 100) + "%" : "Export");

        boolean on = placement.enabled();
        drawString(ctx, x + 6, y + 7, on ? 0xFFFFD040 : GRAY, "[H]");
        int tx = x + 26;
        int avail = buttonsStartX - tx - 4;
        drawString(ctx, tx, y + 3, on ? WHITE : GRAY, fit(entry.name(), avail));
        drawString(ctx, tx, y + 13, statusColor, fit(status, avail));
        super.render(ctx, mouseX, mouseY, selected);
    }

    /** Text shortened with "..." to fit the width in pixels. */
    private String fit(String text, int pixels) {
        if (pixels <= 0) return "";
        while (text.length() > 3 && getStringWidth(text) > pixels) text = text.substring(0, text.length() - 4) + "...";
        return text;
    }
}
