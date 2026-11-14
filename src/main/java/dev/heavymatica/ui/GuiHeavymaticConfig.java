package dev.heavymatica.ui;

import dev.heavymatica.core.ArrayConfig;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.Mirror;
import dev.heavymatica.core.Rotation;
import dev.heavymatica.core.Transform;
import dev.heavymatica.export.LlmExport;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.manhattan.SendFlow;
import dev.heavymatica.place.HeavymaticPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The configuration screen of one placement: origin, rotation, mirror, array, Manhattan name, cutaway, regions.
 * Typing in a field applies at once; the +- buttons apply and rebuild the screen. Shift = 10 instead of 1.
 */
public final class GuiHeavymaticConfig extends GuiBase {
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, ORANGE = 0xFFFFAA00, RED = 0xFFFF5555;
    private static final int LEFT = 12, ROW = 22, AXIS_W = 98, MAX_COPIES = 100, MAX_GAP = 10000;
    private static final String[] AXIS = {"X", "Y", "Z"};

    private final HeavymaticPlacement placement;
    private ButtonGeneric exportButton;
    private int totalX, totalY;

    public GuiHeavymaticConfig(HeavymaticPlacement placement, Screen parent) {
        this.placement = placement;
        setParent(parent);
        setTitle("Heavymatic: " + placement.presetName());
    }

    @Override
    public void initGui() {
        super.initGui();
        var font = Minecraft.getInstance().font;
        Transform t = placement.transform();
        int y = 26;

        // ---- origin ----
        addLabel(LEFT, y + 6, 40, 10, WHITE, "Origin");
        int[] origin = {t.originX(), t.originY(), t.originZ()};
        for (int i = 0; i < 3; i++) {
            int axis = i;
            numberControl(LEFT + 44 + i * AXIS_W, y, AXIS[i], origin[i],
                    v -> setOrigin(axis, v), d -> setOrigin(axis, origin[axis] + d));
        }
        y += ROW + 2;

        // ---- move, rotate, mirror ----
        int x = LEFT;
        ButtonGeneric toPlayer = new ButtonGeneric(x, y, 100, 20, "Move to player", "Sets the origin to the block you stand on.");
        addButton(toPlayer, (b, mouse) -> {
            var p = Minecraft.getInstance().player;
            if (p == null) return;
            var pos = p.blockPosition();
            placement.setOrigin(pos.getX(), pos.getY(), pos.getZ());
            initGui();
        });
        x += 104;
        ButtonGeneric rotate = new ButtonGeneric(x, y, 110, 20, "Rotate: " + degrees(t.rotation()), "Cycles clockwise: 0, 90, 180, 270.");
        addButton(rotate, (b, mouse) -> {
            apply(nextRotation(placement.transform().rotation()), placement.transform().mirror(), placement.transform().array());
            initGui();
        });
        x += 114;
        ButtonGeneric mirror = new ButtonGeneric(x, y, 130, 20, "Mirror: " + t.mirror().name(), "NONE, FRONT_BACK (flips X), LEFT_RIGHT (flips Z).");
        addButton(mirror, (b, mouse) -> {
            apply(placement.transform().rotation(), nextMirror(placement.transform().mirror()), placement.transform().array());
            initGui();
        });
        y += ROW + 6;

        // ---- array ----
        ArrayConfig a = t.array();
        int[] copies = {a.copiesX(), a.copiesY(), a.copiesZ()};
        int[] gaps = {a.gapX(), a.gapY(), a.gapZ()};
        int[] size = transformedSize(t.rotation(), t.mirror());
        addLabel(LEFT, y + 6, 40, 10, WHITE, "Copies");
        for (int i = 0; i < 3; i++) {
            int axis = i;
            numberControl(LEFT + 44 + i * AXIS_W, y, AXIS[i], copies[i],
                    v -> setCopies(axis, v), d -> setCopies(axis, copies[axis] + d));
        }
        y += ROW;
        addLabel(LEFT, y + 6, 40, 10, WHITE, "Gap");
        for (int i = 0; i < 3; i++) {
            int axis = i;
            numberControl(LEFT + 44 + i * AXIS_W, y, AXIS[i], gaps[i],
                    v -> setGap(axis, v), d -> setGap(axis, gaps[axis] + d));
        }
        y += ROW;
        addLabel(LEFT, y + 2, 300, 10, GRAY, "Size " + size[0] + " x " + size[1] + " x " + size[2]
                + ". Copy step = size + gap, at least 1.");
        y += 14;

        // ---- Manhattan name, cutaway ----
        addLabel(LEFT, y + 6, 40, 10, WHITE, "Name");
        GuiTextFieldGeneric nameField = new GuiTextFieldGeneric(LEFT + 44, y + 2, 150, 16, font);
        nameField.setValueWrapper(placement.manhattanName());
        nameField.setHoverTooltip("Name of the area in Manhattan (a multi-region preset adds _<region>).");
        addTextField(nameField, f -> {
            String n = f.getValueWrapper().trim();
            if (!n.isEmpty() && !n.equals(placement.manhattanName())) placement.setManhattanName(n);
            return false;
        }, TextFieldType.STRING);
        ButtonGeneric cut = new ButtonGeneric(LEFT + 200, y, 130, 20, "Cutaway: " + placement.cutaway().name(),
                "Terrain cutaway: ON, WIREFRAME (outline only) or OFF.");
        addButton(cut, (b, mouse) -> {
            placement.setCutaway(WidgetHeavymaticRow.nextCutaway(placement.cutaway()));
            initGui();
        });
        y += ROW + 4;

        // ---- total, send, export, close ----
        totalX = LEFT;
        totalY = y + 6;
        int bx = LEFT + 260;
        if (ManhattanBridge.available()) {
            ButtonGeneric send = new ButtonGeneric(bx, y, 60, 20, "Send", "Sends the areas of this placement to Manhattan.");
            addButton(send, (b, mouse) -> SendFlow.send(placement));
            bx += 64;
        }
        exportButton = new ButtonGeneric(bx, y, 80, 20, "Export", "Scans the terrain and copies a text for Manhattan / an LLM.");
        addButton(exportButton, (b, mouse) -> LlmExport.start(placement));
        bx += 84;
        addButton(new ButtonGeneric(bx, y, 60, 20, "Close"), (b, mouse) -> GuiBase.openGui(getParent()));
        y += ROW + 4;

        // ---- regions: a right column on a wide screen, else below ----
        boolean wide = getScreenWidth() >= 640;
        int rx = wide ? LEFT + 44 + 3 * AXIS_W + 20 : LEFT;
        int ry = wide ? 26 : y + 4;
        addLabel(rx, ry + 2, 200, 10, WHITE, "Regions");
        ry += 14;
        List<HeavymaticRegion> regions = placement.spec().regions();
        int fit = Math.max(1, (getScreenHeight() - 10 - ry) / 20);
        for (int i = 0; i < regions.size(); i++) {
            HeavymaticRegion r = regions.get(i);
            if (i >= fit - 1 && regions.size() > fit) {
                addLabel(rx, ry + 6, 200, 10, GRAY, "... and " + (regions.size() - i) + " more");
                break;
            }
            int color = 0xFF000000 | (r.color() & 0xFFFFFF);
            addLabel(rx, ry + 6, 110, 10, color, ellipsize(r.name(), 110));
            addLabel(rx + 114, ry + 6, 50, 10, WHITE, r.role().name());
            addLabel(rx + 168, ry + 6, 50, 10, GRAY, r.boxes().size() + (r.boxes().size() == 1 ? " box" : " boxes"));
            boolean vis = placement.isRegionVisible(r.name());
            addButton(new ButtonGeneric(rx + 224, ry, 60, 20, vis ? "§aVisible" : "§cHidden"), (b, mouse) -> {
                placement.setRegionVisible(r.name(), !placement.isRegionVisible(r.name()));
                initGui();
            });
            ry += 20;
        }
    }

    // ---- number controls ----

    /** Label, integer field, and - / + buttons (Shift = 10). onType gets typed values, onStep the +-1/10 buttons. */
    private void numberControl(int x, int y, String label, int value, IntConsumer onType, IntConsumer onStep) {
        var font = Minecraft.getInstance().font;
        addLabel(x, y + 6, 10, 10, WHITE, label);
        GuiTextFieldInteger field = new GuiTextFieldInteger(x + 10, y + 3, 44, 14, font);
        field.setValueWrapper(String.valueOf(value));
        addTextField(field, f -> {
            String s = f.getValueWrapper().trim();
            if (s.isEmpty() || s.equals("-")) return false;
            try {
                onType.accept(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // out of int range: ignore
            }
            return false;
        }, TextFieldType.INTEGER);
        addButton(new ButtonGeneric(x + 56, y + 2, 16, 18, "-", "Shift: 10"), (b, mouse) -> {
            onStep.accept(isShiftDown() ? -10 : -1);
            initGui();
        });
        addButton(new ButtonGeneric(x + 74, y + 2, 16, 18, "+", "Shift: 10"), (b, mouse) -> {
            onStep.accept(isShiftDown() ? 10 : 1);
            initGui();
        });
    }

    private void setOrigin(int axis, int v) {
        Transform t = placement.transform();
        int[] o = {t.originX(), t.originY(), t.originZ()};
        o[axis] = v;
        placement.setOrigin(o[0], o[1], o[2]);
    }

    private void setCopies(int axis, int v) {
        ArrayConfig a = placement.transform().array();
        int[] c = {a.copiesX(), a.copiesY(), a.copiesZ()};
        c[axis] = v;
        apply(placement.transform().rotation(), placement.transform().mirror(),
                new ArrayConfig(c[0], c[1], c[2], a.gapX(), a.gapY(), a.gapZ()));
    }

    private void setGap(int axis, int v) {
        ArrayConfig a = placement.transform().array();
        int[] g = {a.gapX(), a.gapY(), a.gapZ()};
        g[axis] = v;
        apply(placement.transform().rotation(), placement.transform().mirror(),
                new ArrayConfig(a.copiesX(), a.copiesY(), a.copiesZ(), g[0], g[1], g[2]));
    }

    /**
     * Sets rotation, mirror and array in one go, with the array clamped: copies >= 1, gap >= 1 - transformed size on
     * that axis (Transform refuses a copy step below 1). Rotating swaps the X and Z size, so the gaps are clamped again.
     */
    private void apply(Rotation r, Mirror m, ArrayConfig a) {
        int[] size = transformedSize(r, m);
        int[] c = {a.copiesX(), a.copiesY(), a.copiesZ()};
        int[] g = {a.gapX(), a.gapY(), a.gapZ()};
        for (int i = 0; i < 3; i++) {
            c[i] = Math.max(1, Math.min(MAX_COPIES, c[i]));
            g[i] = Math.max(1 - size[i], Math.min(MAX_GAP, g[i]));
        }
        Transform t = placement.transform();
        placement.setTransform(new Transform(t.originX(), t.originY(), t.originZ(), r, m,
                new ArrayConfig(c[0], c[1], c[2], g[0], g[1], g[2])));
    }

    /** Size (x, y, z) of the preset after mirror and rotation, without the array. */
    private int[] transformedSize(Rotation r, Mirror m) {
        List<Box3i> all = new ArrayList<>();
        for (HeavymaticRegion region : placement.spec().regions()) all.addAll(region.boxes());
        Transform plain = new Transform(0, 0, 0, r, m, ArrayConfig.NONE);
        Box3i b = null;
        for (Box3i box : plain.apply(all)) b = b == null ? box : b.union(box);
        if (b == null) return new int[] {1, 1, 1};
        return new int[] {b.maxX() - b.minX() + 1, b.maxY() - b.minY() + 1, b.maxZ() - b.minZ() + 1};
    }

    private static Rotation nextRotation(Rotation r) {
        return switch (r) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
    }

    private static Mirror nextMirror(Mirror m) {
        return switch (m) {
            case NONE -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.NONE;
        };
    }

    private static String degrees(Rotation r) {
        return switch (r) {
            case NONE -> "0";
            case CLOCKWISE_90 -> "90";
            case CLOCKWISE_180 -> "180";
            case COUNTERCLOCKWISE_90 -> "270";
        };
    }

    private String ellipsize(String text, int width) {
        var font = Minecraft.getInstance().font;
        while (text.length() > 4 && font.width(text) > width) text = text.substring(0, text.length() - 4) + "...";
        return text;
    }

    // ---- frame ----

    @Override
    public void tick() {
        super.tick();
        if (exportButton != null) {
            float p = LlmExport.progress(placement);
            exportButton.setDisplayString(p >= 0 ? "Export " + Math.round(p * 100) + "%" : "Export");
        }
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        int total = 0;
        try {
            for (var l : placement.worldBoxesByRegion().values()) total += l.size();
        } catch (RuntimeException e) {
            drawString(ctx, "Invalid array settings", totalX, totalY, RED);
            return;
        }
        int color = total > 4096 ? RED : total > 64 ? ORANGE : WHITE;
        String text = "Total boxes: " + total;
        if (total > 4096) text += " (too many for Manhattan)";
        else if (total > 64) text += " (many)";
        drawString(ctx, text, totalX, totalY, color);
    }
}
