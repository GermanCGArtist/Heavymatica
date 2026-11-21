package dev.heavymatica.ui;

import com.google.gson.JsonObject;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.manhattan.SendFlow;
import dev.heavymatica.export.LlmExport;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.vitruvius.api.OverlayDrawer;
import dev.vitruvius.api.PropertyUi;
import dev.vitruvius.api.SceneObjectType;
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
    public static final String ID = "heavymatica:ref";
    public static final String KEY = "placementId";

    private static UUID confirmDelete;

    @Override
    public String id() {
        return ID;
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
        if (p.isEmpty()) return new Vec3i(4, 4, 4);
        Box3i b = p.get().spec().bounds();
        return new Vec3i(Math.max(1, b.maxX() + 1), Math.max(1, b.maxY() + 1), Math.max(1, b.maxZ() + 1));
    }

    @Override
    public Box bounds(JsonObject data) {
        return Box.ofSize(sizeOf(data));
    }

    @Override
    public void renderOutline(JsonObject data, Transform world, OverlayDrawer d, boolean selected) {
        // the placement draws its own wireframes; the object only shows that it is selected
        if (selected) d.box(bounds(data).transformed(world), 0xFFFFFF, 1f, false);
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
        if (ui.button("Export (Manhattan / LLM text)")) LlmExport.start(p);
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
