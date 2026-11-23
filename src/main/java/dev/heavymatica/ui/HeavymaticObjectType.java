package dev.heavymatica.ui;

import com.google.gson.JsonObject;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.manhattan.SendFlow;
import dev.heavymatica.export.LlmExport;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.vitruvius.api.OverlayDrawer;
import dev.vitruvius.api.PropertyUi;
import dev.vitruvius.api.SceneObjectType;
import dev.vitruvius.api.SceneObjectView;
import dev.vitruvius.api.VitruviusApi;
import dev.heavymatica.export.FolderExport;
import dev.heavymatica.export.GroupExport;
import dev.vitruvius.editor.core.Box;
import dev.vitruvius.editor.core.Transform;
import dev.vitruvius.editor.core.Vec3i;

import java.util.Optional;
import java.util.UUID;

/**
 * A heavymatic as an object of Vitruvius's editor: a stand-in for one {@link HeavymaticPlacement} (the object's data
 * says which, by id) that can be selected, moved, turned, hidden and put into projects like anything else. The pose
 * and the hidden state are kept in step with the placement by {@link ProxySync}; the wireframes and the terrain
 * cutaway are still drawn by Heavymatica itself from the placement.
 */
public final class HeavymaticObjectType implements SceneObjectType {
    /** The id of the object type. Older versions made objects with a snapshot of the preset under this id too. */
    public static final String ID = "heavymatica:heavymatic";
    /** The id of 0.3.0, kept so that projects saved with it still load. */
    public static final String LEGACY_REF = "heavymatica:ref";
    public static final String KEY = "placementId";

    private static UUID confirmDelete;

    private final String typeId;

    public HeavymaticObjectType(String typeId) {
        this.typeId = typeId;
    }

    @Override
    public String id() {
        return typeId;
    }

    @Override
    public String displayName() {
        return "Heavymatic";
    }

    /** A new, unlinked object (Add > Heavymatic): ProxySync gives it a 4 x 4 x 4 DIG placement. */
    @Override
    public JsonObject create() {
        JsonObject o = new JsonObject();
        o.addProperty(KEY, "");
        return o;
    }

    static Optional<HeavymaticPlacement> placementOf(JsonObject data) {
        try {
            String id = data.has(KEY) ? data.get(KEY).getAsString() : "";
            return id.isEmpty() ? Optional.empty() : PlacementStore.byId(UUID.fromString(id));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The size of the placement's preset (its boxes start at 0,0,0), or 4 x 4 x 4 for an unlinked object. */
    static Vec3i sizeOf(JsonObject data) {
        var p = placementOf(data);
        if (p.isEmpty()) {
            HeavymaticSpec snap = ProxySync.snapshotOf(data);       // an object from an older version, not linked yet
            if (snap == null) return new Vec3i(4, 4, 4);
            Box3i sb = snap.bounds();
            return new Vec3i(Math.max(1, sb.sizeX()), Math.max(1, sb.sizeY()), Math.max(1, sb.sizeZ()));
        }
        Box3i b = p.get().spec().bounds();
        return new Vec3i(Math.max(1, b.maxX() + 1), Math.max(1, b.maxY() + 1), Math.max(1, b.maxZ() + 1));
    }

    /** The user deleted the object (Delete key, outliner): the heavymatic goes with it, but is kept for Undo. */
    @Override
    public void onRemoved(JsonObject data) {
        placementOf(data).ifPresent(ProxySync::userDeleted);
    }

    @Override
    public Box bounds(JsonObject data) {
        return Box.ofSize(sizeOf(data));
    }

    @Override
    public void renderOutline(JsonObject data, Transform world, OverlayDrawer d, boolean selected) {
        // The base placement draws its own wireframes via WireframeRenderer; array copies have no PlacementStore entry
        // so we draw every visible region box at the copy's world transform.
        var found = placementOf(data);
        if (found.isPresent()) {
            HeavymaticPlacement p = found.get();
            if (p.enabled() && !ProxySync.nodePose(p).equals(world)) {
                dev.heavymatica.core.Transform ct = ProxySync.copyTransform(p, world);
                boolean dashed = p.cutaway() == CutawayMode.OFF;
                for (dev.heavymatica.core.HeavymaticRegion r : p.spec().regions()) {
                    if (!p.isRegionVisible(r.name())) continue;
                    int rgb = r.color() & 0xFFFFFF;
                    for (Box3i b : ct.apply(r.boxes())) {
                        Box wb = new Box(new Vec3i(b.minX(), b.minY(), b.minZ()), new Vec3i(b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1));
                        d.box(wb, rgb, selected ? 1f : 0.85f, dashed);
                        if (p.cutaway() != CutawayMode.ON) d.fill(wb, rgb, 0.06f);
                    }
                }
            }
        }
        if (selected) d.box(bounds(data).transformed(world), 0xFFFFFF, 1f, false);
    }

    /** The visible placed copies of a heavymatic in the editor (base and array copies, also those of arrayed assemblies). */
    private static java.util.List<SceneObjectView> copiesOf(HeavymaticPlacement p) {
        java.util.List<SceneObjectView> out = new java.util.ArrayList<>();
        for (String type : new String[] {ID, LEGACY_REF}) {
            for (SceneObjectView v : VitruviusApi.objectsOfType(type)) {
                if (!v.hidden() && placementOf(v.data()).map(q -> q.id().equals(p.id())).orElse(false)) out.add(v);
            }
        }
        return out;
    }

    /**
     * Exports editor copies of heavymatics as one Manhattan / LLM text: each copy is its own set of areas (array
     * copies named {@code <name>_2, _3, ...}). Used by the right-click menu entry and the Properties button.
     */
    public static void exportViews(java.util.List<SceneObjectView> views) {
        java.util.List<HeavymaticPlacement> list = GroupExport.placements(views, v -> placementOf(v.data()),
                (src, v, name) -> HeavymaticPlacement.restore(UUID.randomUUID(), src.presetName(), src.spec(),
                        ProxySync.copyTransform(src, v.world()), true, src.cutaway(), src.regionVisible(), name, 0L, false));
        FolderExport.runGroup(GroupExport.label(list), list);
    }

    @Override
    public void drawProperties(JsonObject data, PropertyUi ui) {
        var found = placementOf(data);
        ui.section("Heavymatic");
        if (found.isEmpty()) {
            ui.note("Not linked to a heavymatic yet.");
            return;
        }
        HeavymaticPlacement p = found.get();
        ui.text(p.presetName());
        ui.note(p.spec().regions().size() + " region(s), " + p.spec().boxCount() + " boxes");
        if (ui.button("Slice: " + p.cutaway().name())) {
            CutawayMode[] all = CutawayMode.values();
            p.setCutaway(all[(p.cutaway().ordinal() + 1) % all.length]);
        }
        if (ui.button(p.enabled() ? "Shown in the world" : "Hidden in the world")) p.setEnabled(!p.enabled());
        if (ManhattanBridge.available() && ui.button("Send to Manhattan")) SendFlow.send(p);
        int copies = copiesOf(p).size();
        if (copies > 1) {
            if (ui.button("Export all " + copies + " array copies (Manhattan / LLM text)")) exportViews(copiesOf(p));
            if (ui.button("Export this copy only")) LlmExport.start(p);
        } else if (ui.button("Export (Manhattan / LLM text)")) {
            LlmExport.start(p);
        }
        if (p.id().equals(confirmDelete)) {
            ui.note("Delete this heavymatic for good? Click again to confirm.");
            if (ui.button("Yes, delete it")) {
                confirmDelete = null;
                PlacementStore.remove(p);
            }
            if (ui.button("Keep it")) confirmDelete = null;
        } else if (ui.button("Delete heavymatic")) {
            confirmDelete = p.id();
        }
        ui.note("The areas already sent to Manhattan stay there.");
    }
}
