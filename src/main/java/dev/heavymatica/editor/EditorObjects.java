package dev.heavymatica.editor;

import com.google.gson.JsonObject;
import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticJson;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.PresetLibrary;
import dev.vitruvius.api.OverlayDrawer;
import dev.vitruvius.api.PropertyUi;
import dev.vitruvius.api.SceneObjectType;
import dev.vitruvius.api.SceneObjectView;
import dev.vitruvius.api.VitruviusApi;
import dev.vitruvius.editor.core.Box;
import dev.vitruvius.editor.core.Transform;
import dev.vitruvius.editor.core.Vec3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Heavymatics as objects of Litematica Array's 2.0 editor: <b>Add ▸ Heavymatic</b> (Shift+A) puts a preset into the
 * scene, where it moves, rotates, mirrors and arrays like any object. The object keeps a snapshot of the preset
 * ({@code spec}), so a project does not depend on the preset file, and a cutaway mode ({@code cutaway}).
 *
 * <p>This class touches the Litematica Array API (version 2) and must only be loaded after checking for it:
 * {@link #register()} is called inside a try/catch for that.
 */
public final class EditorObjects implements SceneObjectType {
    public static final String TYPE_ID = "heavymatica:heavymatic";
    private static final String[] MODES = {"ON", "WIREFRAME", "OFF"};
    private static final int MAX_CACHE = 64;

    /** A spec with its boxes moved so the bounds start at (0,0,0). */
    private record Local(HeavymaticSpec spec, int sx, int sy, int sz) {}

    private static final Map<String, Local> CACHE = new HashMap<>();

    public static void register() {
        VitruviusApi.registerObjectType(new EditorObjects());
    }

    // ------------------------------------------------------------------------------------------- the type

    @Override public String id() { return TYPE_ID; }

    @Override public String displayName() { return "Heavymatic"; }

    @Override
    public JsonObject create() {
        JsonObject o = new JsonObject();
        List<String> names = PresetLibrary.names();
        HeavymaticSpec spec = names.isEmpty() ? null : PresetLibrary.load(names.get(0)).orElse(null);
        if (spec == null) spec = placeholder();
        setSpec(o, names.isEmpty() ? "" : names.get(0), spec);
        o.addProperty("cutaway", CutawayMode.ON.name());
        return o;
    }

    @Override
    public Box bounds(JsonObject data) {
        Local l = local(data);
        return Box.ofSize(new Vec3i(l.sx, l.sy, l.sz));
    }

    @Override
    public void renderOutline(JsonObject data, Transform world, OverlayDrawer d, boolean selected) {
        Local l = local(data);
        CutawayMode mode = mode(data);
        for (HeavymaticRegion r : l.spec.regions()) {
            int rgb = r.color() & 0xFFFFFF;
            for (Box3i b : r.boxes()) {
                Box w = toBox(b).transformed(world);
                d.box(w, rgb, selected ? 1f : 0.85f, mode == CutawayMode.OFF);
                if (mode != CutawayMode.ON) d.fill(w, rgb, 0.06f);   // with the cutaway on, the hole itself shows the volume
            }
        }
    }

    @Override
    public void drawProperties(JsonObject data, PropertyUi ui) {
        ui.section("Heavymatic");
        List<String> names = new ArrayList<>(PresetLibrary.names());
        String current = str(data, "preset");
        if (!current.isEmpty() && !names.contains(current)) names.add(0, current + " (file missing)");
        if (names.isEmpty()) {
            ui.note("No presets yet. Save one with H in the world.");
        } else {
            int idx = Math.max(0, names.indexOf(current.isEmpty() || names.contains(current) ? current : current + " (file missing)"));
            int next = ui.combo("Preset", idx, names.toArray(new String[0]));
            if (next != idx && next >= 0 && next < names.size()) {
                PresetLibrary.load(names.get(next)).ifPresent(s -> setSpec(data, names.get(next), s));
            }
        }
        int mi = mode(data).ordinal();
        int nm = ui.combo("Cutaway", mi, MODES);
        if (nm != mi && nm >= 0 && nm < MODES.length) data.addProperty("cutaway", MODES[nm]);
        ui.note("ON cuts the terrain where this digs or clears, WIREFRAME only outlines it, OFF hides the cut.");
        Local l = local(data);
        ui.note(l.spec.regions().size() + " regions, " + l.spec.boxCount() + " boxes, " + l.sx + " x " + l.sy + " x " + l.sz);
    }

    // ------------------------------------------------------------------------------------------- cutaway source

    /**
     * World boxes (inclusive) that the terrain cutaway must cut for the heavymatics of the open editor scene: visible
     * objects with the cutaway ON, one set per array copy, only regions that cut terrain. Empty when the editor is closed.
     */
    public static List<Box3i> cutBoxes() {
        List<Box3i> out = new ArrayList<>();
        for (SceneObjectView v : VitruviusApi.objectsOfType(TYPE_ID)) {
            if (v.hidden() || mode(v.data()) != CutawayMode.ON) continue;
            Local l = local(v.data());
            for (HeavymaticRegion r : l.spec.regions()) {
                if (!r.role().cutsTerrain()) continue;
                for (Box3i b : r.boxes()) {
                    Box w = toBox(b).transformed(v.world());
                    out.add(new Box3i(w.min().x(), w.min().y(), w.min().z(),
                            w.maxExclusive().x() - 1, w.maxExclusive().y() - 1, w.maxExclusive().z() - 1));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static Box toBox(Box3i b) {
        return new Box(new Vec3i(b.minX(), b.minY(), b.minZ()), new Vec3i(b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1));
    }

    private static CutawayMode mode(JsonObject data) {
        try {
            return CutawayMode.valueOf(str(data, "cutaway"));
        } catch (IllegalArgumentException e) {
            return CutawayMode.ON;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static HeavymaticSpec placeholder() {
        return new HeavymaticSpec("heavymatic", "", "",
                List.of(new HeavymaticRegion("area", RegionRole.DIG, RegionRole.DIG.defaultColor(), List.of(Box3i.of(0, 0, 0, 7, 3, 7)))));
    }

    /** Stores the preset as a normalized snapshot (bounds start at 0,0,0). */
    private static void setSpec(JsonObject data, String preset, HeavymaticSpec spec) {
        data.addProperty("preset", preset);
        data.addProperty("spec", HeavymaticJson.write(normalize(spec)));
    }

    private static HeavymaticSpec normalize(HeavymaticSpec spec) {
        Box3i b = spec.bounds();
        if (b.minX() == 0 && b.minY() == 0 && b.minZ() == 0) return spec;
        List<HeavymaticRegion> regions = new ArrayList<>();
        for (HeavymaticRegion r : spec.regions()) {
            List<Box3i> boxes = new ArrayList<>();
            for (Box3i box : r.boxes()) boxes.add(box.offset(-b.minX(), -b.minY(), -b.minZ()));
            regions.add(new HeavymaticRegion(r.name(), r.role(), r.color(), boxes));
        }
        return new HeavymaticSpec(spec.name(), spec.author(), spec.description(), regions);
    }

    /** The snapshot of an object, parsed once per distinct text. A broken or empty one is a single DIG box, never an exception. */
    private static Local local(JsonObject data) {
        String json = str(data, "spec");
        synchronized (CACHE) {
            Local l = CACHE.get(json);
            if (l != null) return l;
            HeavymaticSpec spec;
            try {
                spec = normalize(HeavymaticJson.read(json));
                spec.bounds();   // throws without boxes
            } catch (RuntimeException e) {
                HeavymaticaMod.LOGGER.warn("Heavymatic object with an unreadable spec: {}", e.getMessage());
                spec = placeholder();
            }
            Box3i b = spec.bounds();
            l = new Local(spec, b.sizeX(), b.sizeY(), b.sizeZ());
            if (CACHE.size() >= MAX_CACHE) CACHE.clear();
            CACHE.put(json, l);
            return l;
        }
    }
}
