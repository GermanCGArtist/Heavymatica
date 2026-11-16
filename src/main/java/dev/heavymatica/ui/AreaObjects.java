package dev.heavymatica.ui;

import com.google.gson.JsonObject;
import dev.heavymatica.core.AreaData;
import dev.heavymatica.core.AreaData.Mode;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.manhattan.SendFlow;
import dev.heavymatica.place.PresetLibrary;
import dev.schemarray.api.OverlayDrawer;
import dev.schemarray.api.PropertyUi;
import dev.schemarray.api.SceneObjectType;
import dev.schemarray.api.SceneObjectView;
import dev.schemarray.api.SchematicArrayApi;
import dev.schemarray.editor.core.Box;
import dev.schemarray.editor.core.Transform;
import dev.schemarray.editor.core.Vec3i;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "heavymatica:area": a dig / protect area as a Litematica Array 2.0 scene object (API v2). Only loaded when
 * {@link SceneAreas#available()}; never reference this class from code that runs with older Litematica Array.
 */
public final class AreaObjects implements SceneObjectType {
    public static final String ID = "heavymatica:area";
    private static final int DIG_RGB = 0xf2b35e, PROTECT_RGB = 0x5eaaf2;
    private static final int MAX_LISTED = 12;

    private List<String> presetNames = List.of();
    private long presetsReadAt;

    @Override public String id() { return ID; }

    @Override public String displayName() { return "Dig / protect area"; }

    @Override public JsonObject create() { return AreaData.defaults().toJson(); }

    @Override
    public Box bounds(JsonObject data) {
        return toBox(AreaData.fromJson(data).union());
    }

    private static Box toBox(Box3i b) {
        return new Box(new Vec3i(b.minX(), b.minY(), b.minZ()), new Vec3i(b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1));
    }

    static AreaData.Pose pose(Transform t) {
        int mirror = switch (t.mirror()) {
            case FRONT_BACK -> 1;
            case LEFT_RIGHT -> 2;
            default -> 0;
        };
        return new AreaData.Pose(t.pos().x(), t.pos().y(), t.pos().z(), t.rot().steps(), mirror);
    }

    @Override
    public void renderOutline(JsonObject data, Transform world, OverlayDrawer d, boolean selected) {
        AreaData a = AreaData.fromJson(data);
        boolean dig = a.mode() == Mode.DIG;
        int rgb = dig ? DIG_RGB : PROTECT_RGB;   // like WireframeRenderer: dig amber (dashed), protect blue
        float alpha = selected ? 1f : 0.8f;
        for (Box3i b : a.toWorld(pose(world))) {
            Box box = toBox(b);
            d.box(box, rgb, alpha, dig);
            d.fill(box, rgb, 0.05f);
        }
    }

    @Override
    public void drawProperties(JsonObject data, PropertyUi ui) {
        AreaData a = AreaData.fromJson(data);
        ui.section("Dig / protect area");
        String name = ui.textField("Name", a.name());
        int mode = ui.combo("Mode", a.mode().ordinal(), "Dig", "Protect");

        String[] options = presetOptions();
        int pick = ui.combo("Load preset", 0, options);
        AreaData next = new AreaData(name.isBlank() ? a.name() : name, Mode.values()[Math.max(0, Math.min(1, mode))],
                a.preset(), a.boxes());
        if (pick > 0) {
            AreaData loaded = fromPreset(options[pick], next);
            if (loaded != null) next = loaded;
        }
        if (ui.changed()) {
            JsonObject j = next.toJson();
            for (String k : j.keySet()) data.add(k, j.get(k));
        }

        if (!next.preset().isEmpty()) ui.note("Loaded from preset: " + next.preset());
        ui.text(next.boxes().size() + " box(es)");
        int shown = 0;
        for (Box3i b : next.boxes()) {
            if (shown++ >= MAX_LISTED) {
                ui.note("... and " + (next.boxes().size() - MAX_LISTED) + " more");
                break;
            }
            ui.text(b.sizeX() + " x " + b.sizeY() + " x " + b.sizeZ() + "  at " + b.minX() + ", " + b.minY() + ", " + b.minZ());
        }
        if (ui.button("Open in Heavymatica")) {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new GuiPresetBrowser(null)));
        }
        if (ManhattanBridge.available() && ui.button("Send to Manhattan")) {
            Minecraft.getInstance().execute(SendFlow::sendSceneAreas);
        }
    }

    /** The "pick a preset" entry followed by the preset names (re-read from disk at most every 5 s). */
    private String[] presetOptions() {
        long now = System.currentTimeMillis();
        if (now - presetsReadAt > 5000) {
            presetsReadAt = now;
            presetNames = PresetLibrary.names();
        }
        String[] o = new String[presetNames.size() + 1];
        o[0] = "(pick a preset)";
        for (int i = 0; i < presetNames.size(); i++) o[i + 1] = presetNames.get(i);
        return o;
    }

    /** Boxes of the preset's regions that share the first region's role; mode follows that role. */
    private static AreaData fromPreset(String presetName, AreaData base) {
        HeavymaticSpec spec = PresetLibrary.load(presetName).orElse(null);
        if (spec == null || spec.regions().isEmpty()) return null;
        HeavymaticRegion first = spec.regions().get(0);
        List<Box3i> boxes = new ArrayList<>();
        for (HeavymaticRegion r : spec.regions()) if (r.role() == first.role()) boxes.addAll(r.boxes());
        if (boxes.isEmpty()) return null;
        Box3i u = AreaData.union(boxes);
        List<Box3i> shifted = new ArrayList<>();
        for (Box3i b : boxes) shifted.add(b.offset(-u.minX(), -u.minY(), -u.minZ()));   // corner at the object's origin
        String n = base.name().equals("area") ? presetName : base.name();
        return new AreaData(n, first.role() == RegionRole.PROTECT ? Mode.PROTECT : Mode.DIG, presetName, shifted);
    }

    /** Every visible scene area, one plan per object (all array copies together), world boxes. */
    static List<AreaData.Plan> plans() {
        Map<UUID, List<SceneObjectView>> byId = new LinkedHashMap<>();
        for (SceneObjectView v : SchematicArrayApi.objectsOfType(ID)) {
            if (!v.hidden()) byId.computeIfAbsent(v.id(), k -> new ArrayList<>()).add(v);
        }
        List<AreaData.Plan> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (List<SceneObjectView> copies : byId.values()) {
            AreaData a = AreaData.fromJson(copies.get(0).data());
            List<Box3i> boxes = new ArrayList<>();
            for (SceneObjectView v : copies) boxes.addAll(a.toWorld(pose(v.world())));
            String nodeName = copies.get(0).name();
            String base = AreaData.safeName(nodeName == null || nodeName.isBlank() ? a.name() : nodeName);
            String n = base;
            for (int i = 2; !names.add(n); i++) n = base + "_" + i;
            out.add(new AreaData.Plan(n, a.mode().role(), boxes));
        }
        return out;
    }
}
