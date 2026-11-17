package dev.heavymatica.ui;

import dev.heavymatica.core.RegionRole;
import dev.heavymatica.place.PresetLibrary;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Small dialog that asks for a preset name and a role before a new heavymatic is saved. Warns above 64 boxes, refuses
 * above 4096 (Manhattan's limit per area) and when the name would overwrite an existing preset.
 */
public final class NameRoleDialog extends GuiBase {
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, ORANGE = 0xFFFFAA00, RED = 0xFFFF5555;
    static final int MAX_BOXES = 4096, WARN_BOXES = 64;

    private final String heading;
    private final int boxCount;
    private final BiConsumer<String, RegionRole> onOk;
    private final boolean closeAllAfterOk;
    private String name;
    private RegionRole role = RegionRole.DIG;
    private final java.util.Set<String> existing = new java.util.HashSet<>();   // lower case preset names

    /**
     * @param closeAllAfterOk true = after OK no screen is shown (the Shape Generator behind it would only clear its
     *                        preview anyway); Cancel always returns to the parent
     */
    public NameRoleDialog(Screen parent, String heading, String defaultName, int boxCount, boolean closeAllAfterOk,
                          BiConsumer<String, RegionRole> onOk) {
        setParent(parent);
        setTitle(heading);
        this.heading = heading;
        this.name = defaultName;
        this.boxCount = boxCount;
        this.closeAllAfterOk = closeAllAfterOk;
        this.onOk = onOk;
        for (String n : PresetLibrary.names()) existing.add(n.toLowerCase(Locale.ROOT));
    }

    @Override
    public void initGui() {
        super.initGui();
        var font = Minecraft.getInstance().font;
        int x = 20, y = 40;
        addLabel(x, y + 6, 40, 10, WHITE, "Name");
        GuiTextFieldGeneric field = new GuiTextFieldGeneric(x + 44, y + 2, 180, 16, font);
        field.setValueWrapper(name);
        field.setMaxLengthWrapper(64);
        addTextField(field, f -> {
            name = f.getValueWrapper();
            return false;
        }, TextFieldType.STRING);
        y += 24;
        addLabel(x, y + 6, 40, 10, WHITE, "Role");
        addButton(new ButtonGeneric(x + 44, y, 110, 20, role.name(), "DIG, CLEAR, FLOOD, FILL, PROTECT, STAIRS (the stair shaft of a dig: at least 2 x 2, inside the dig region) or CUSTOM."),
                (b, mouse) -> {
                    RegionRole[] all = RegionRole.values();
                    role = all[(role.ordinal() + 1) % all.length];
                    b.setDisplayString(role.name());
                });
        y += 48;
        boolean tooMany = boxCount > MAX_BOXES;
        ButtonGeneric ok = new ButtonGeneric(x, y, 60, 20, "Save");
        ok.setEnabled(!tooMany);
        addButton(ok, (b, mouse) -> {
            String n = name == null ? "" : name.trim();
            if (n.isEmpty() || tooMany) return;
            if (closeAllAfterOk) Minecraft.getInstance().setScreen(null);
            else closeGui(true);
            onOk.accept(n, role);
        });
        addButton(new ButtonGeneric(x + 64, y, 60, 20, "Cancel"), (b, mouse) -> closeGui(true));
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        int x = 20, y = 90;
        String text = boxCount + (boxCount == 1 ? " box" : " boxes");
        int color = WHITE;
        if (boxCount > MAX_BOXES) {
            text += ": too many, Manhattan accepts at most " + MAX_BOXES + " per area. Not saved.";
            color = RED;
        } else if (boxCount > WARN_BOXES) {
            text += ": many boxes, Manhattan will ask before it takes this.";
            color = ORANGE;
        }
        drawString(ctx, text, x, y, color);
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) drawString(ctx, "Enter a name.", x, y + 12, ORANGE);
        else if (existing.contains(n)) drawString(ctx, "A preset with this name exists and will be replaced.", x, y + 12, ORANGE);
    }
}
