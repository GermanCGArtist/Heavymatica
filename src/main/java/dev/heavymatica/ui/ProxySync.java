package dev.heavymatica.ui;

import com.google.gson.JsonObject;
import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.ArrayConfig;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.Mirror;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.core.Rotation;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import dev.vitruvius.api.SceneObjectView;
import dev.vitruvius.api.VitruviusApi;
import dev.vitruvius.editor.core.Rot;
import dev.vitruvius.editor.core.Transform;
import dev.vitruvius.editor.core.Vec3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps one editor object per heavymatic placement, and the two in step. While the editor runs:
 * a placement without an object gets one (under Loose); an object the user moved, turned or hid in the editor moves
 * the placement; a placement changed elsewhere (the configuration screen) moves its object; an object made with
 * Add > Heavymatic, or duplicated in the editor, gets a placement of its own; an object whose placement is gone is
 * removed. Whichever side changed since the last pass wins; the editor wins a tie.
 *
 * <p>The poses differ in one detail: Vitruvius mirrors a shape about its origin, Heavymatica about its own bounding
 * box. The object's position is the placement's origin plus that shift, so the blocks line up either way.
 */
public final class ProxySync {
    private ProxySync() {}

    private static final class State {
        UUID nodeId;
        Transform lastNode;        // the object's pose at the end of the last pass
        dev.heavymatica.core.Transform lastPlacement;
        boolean lastHidden;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static boolean failedOnce;
    /** Heavymatics whose object the user deleted: kept so that Undo (the object comes back) can bring them back too. */
    private static final Map<UUID, HeavymaticPlacement> REMOVED = new HashMap<>();

    static void userDeleted(HeavymaticPlacement p) {
        REMOVED.put(p.id(), p);
        STATES.remove(p.id());
        PlacementStore.remove(p);
    }

    /** Whether the editor shows this placement as an object (so the read-only list row is left out there). */
    public static boolean has(UUID placementId) {
        return STATES.containsKey(placementId);
    }

    public static void tick() {
        if (!PlacementStore.ready()) {
            STATES.clear();
            REMOVED.clear();
            return;
        }
        if (!VitruviusApi.editorReady()) return;
        try {
            sync();
        } catch (Throwable t) {
            if (!failedOnce) {
                failedOnce = true;
                HeavymaticaMod.LOGGER.warn("Heavymatic editor sync failed", t);
            }
        }
    }

    // ------------------------------------------------------------------ pose maths

    private static Rot rotOf(Rotation r) {
        return Rot.values()[r.ordinal()];
    }

    private static Rotation rotationOf(Rot r) {
        return Rotation.values()[r.ordinal()];
    }

    private static int mirrorCode(Mirror m) {      // Vitruvius: 0 none, 1 left-right, 2 front-back
        return m == Mirror.LEFT_RIGHT ? 1 : m == Mirror.FRONT_BACK ? 2 : 0;
    }

    private static Mirror mirrorOf(dev.vitruvius.editor.core.Mirror m) {
        return switch (m) {
            case LEFT_RIGHT -> Mirror.LEFT_RIGHT;
            case FRONT_BACK -> Mirror.FRONT_BACK;
            default -> Mirror.NONE;
        };
    }

    private static dev.vitruvius.editor.core.Mirror vitMirror(Mirror m) {
        return switch (m) {
            case LEFT_RIGHT -> dev.vitruvius.editor.core.Mirror.LEFT_RIGHT;
            case FRONT_BACK -> dev.vitruvius.editor.core.Mirror.FRONT_BACK;
            default -> dev.vitruvius.editor.core.Mirror.NONE;
        };
    }

    /** What Heavymatica adds before it turns a mirrored shape (the mirror keeps the box where it was). */
    private static Vec3i mirrorShift(Mirror m, Rot rot, HeavymaticPlacement p) {
        Box3i b = p.spec().bounds();
        int sx = b.maxX() + 1, sz = b.maxZ() + 1;
        Vec3i v = m == Mirror.FRONT_BACK ? new Vec3i(sx - 1, 0, 0) : m == Mirror.LEFT_RIGHT ? new Vec3i(0, 0, sz - 1) : Vec3i.ZERO;
        return new Transform(Vec3i.ZERO, rot, dev.vitruvius.editor.core.Mirror.NONE).applyVector(v);
    }

    /** The object's pose for a placement. */
    static Transform nodePose(HeavymaticPlacement p) {
        dev.heavymatica.core.Transform t = p.transform();
        Rot rot = rotOf(t.rotation());
        Vec3i shift = mirrorShift(t.mirror(), rot, p);
        return new Transform(new Vec3i(t.originX() + shift.x(), t.originY() + shift.y(), t.originZ() + shift.z()), rot, vitMirror(t.mirror()));
    }

    /** The placement's transform for an object's pose (the array stays as it is). */
    static dev.heavymatica.core.Transform placementTransform(HeavymaticPlacement p, Transform node) {
        Mirror m = mirrorOf(node.mirror());
        Vec3i shift = mirrorShift(m, node.rot(), p);
        return new dev.heavymatica.core.Transform(node.pos().x() - shift.x(), node.pos().y() - shift.y(), node.pos().z() - shift.z(),
                rotationOf(node.rot()), m, p.transform().array());
    }

    /** Transform for a single Vitruvius array copy (no Heavymatica-side array expansion). */
    static dev.heavymatica.core.Transform copyTransform(HeavymaticPlacement p, Transform node) {
        Mirror m = mirrorOf(node.mirror());
        Vec3i shift = mirrorShift(m, node.rot(), p);
        return new dev.heavymatica.core.Transform(node.pos().x() - shift.x(), node.pos().y() - shift.y(), node.pos().z() - shift.z(),
                rotationOf(node.rot()), m, ArrayConfig.NONE);
    }

    /** World cut boxes for all Vitruvius array copies of heavymatics (excluding base copies already cut via PlacementStore). */
    public static List<Box3i> arrayCutBoxes() {
        List<Box3i> out = new ArrayList<>();
        for (String type : new String[] {HeavymaticObjectType.ID, HeavymaticObjectType.LEGACY_REF}) {
            for (SceneObjectView v : VitruviusApi.objectsOfType(type)) {
                if ("base".equals(v.cell()) || v.hidden()) continue;
                var found = HeavymaticObjectType.placementOf(v.data());
                if (found.isEmpty()) continue;
                HeavymaticPlacement p = found.get();
                if (!p.enabled() || p.cutaway() != dev.heavymatica.place.CutawayMode.ON) continue;
                dev.heavymatica.core.Transform ct = copyTransform(p, v.world());
                for (HeavymaticRegion r : p.spec().regions()) {
                    if (!r.role().cutsTerrain() || !p.isRegionVisible(r.name())) continue;
                    out.addAll(ct.apply(r.boxes()));
                }
            }
        }
        return out;
    }

    private static boolean samePose(dev.heavymatica.core.Transform a, dev.heavymatica.core.Transform b) {
        return a.originX() == b.originX() && a.originY() == b.originY() && a.originZ() == b.originZ()
                && a.rotation() == b.rotation() && a.mirror() == b.mirror();
    }

    /** The preset snapshot an older version stored in the object's data ({@code spec}), or null. */
    static HeavymaticSpec snapshotOf(JsonObject data) {
        try {
            if (!data.has("spec")) return null;
            HeavymaticSpec s = dev.heavymatica.core.HeavymaticJson.read(data.get("spec").getAsString());
            s.bounds();                                                // throws without boxes
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject dataFor(UUID placementId) {
        JsonObject o = new JsonObject();
        o.addProperty(HeavymaticObjectType.KEY, placementId.toString());
        return o;
    }

    // ------------------------------------------------------------------ the pass

    private static void sync() {
        List<SceneObjectView> views = new ArrayList<>();
        for (String type : new String[] {HeavymaticObjectType.ID, HeavymaticObjectType.LEGACY_REF}) {
            for (SceneObjectView v : VitruviusApi.objectsOfType(type)) if ("base".equals(v.cell())) views.add(v);
        }

        Map<String, SceneObjectView> byPlacement = new HashMap<>();
        Set<UUID> claimed = new HashSet<>();
        List<SceneObjectView> unlinked = new ArrayList<>();
        for (SceneObjectView v : views) {
            String pid = v.data().has(HeavymaticObjectType.KEY) ? v.data().get(HeavymaticObjectType.KEY).getAsString() : "";
            UUID placementId = null;
            try {
                if (!pid.isEmpty()) placementId = UUID.fromString(pid);
            } catch (IllegalArgumentException ignored) {
                // unlinked
            }
            State known = placementId == null ? null : STATES.get(placementId);
            if (placementId == null) {
                unlinked.add(v);
            } else if (PlacementStore.byId(placementId).isEmpty() && REMOVED.containsKey(placementId)) {
                PlacementStore.add(REMOVED.remove(placementId));    // Undo brought the object back: so the heavymatic returns
                byPlacement.put(placementId.toString(), v);
                claimed.add(placementId);
            } else if (PlacementStore.byId(placementId).isEmpty()) {
                VitruviusApi.removeObject(v.id());                  // its heavymatic was deleted elsewhere
                STATES.remove(placementId);
            } else if (known != null && !known.nodeId.equals(v.id())) {
                unlinked.add(v);                                    // a copy made in the editor: it gets a placement of its own
            } else if (!claimed.add(placementId)) {
                unlinked.add(v);
            } else {
                byPlacement.put(placementId.toString(), v);
            }
        }

        // objects without a placement: Add > Heavymatic (a new 4 x 4 x 4 DIG box) or a copy of an existing one
        for (SceneObjectView v : unlinked) {
            var source = HeavymaticObjectType.placementOf(v.data());
            HeavymaticSpec spec;
            String name;
            HeavymaticSpec snap = source.isPresent() ? null : snapshotOf(v.data());
            if (source.isPresent()) {
                spec = source.get().spec();
                name = source.get().presetName();
            } else if (snap != null) {
                // an object of an older version: a snapshot of a preset, and its own cutaway mode
                spec = snap;
                String preset = v.data().has("preset") ? v.data().get("preset").getAsString() : "";
                name = preset.isEmpty() ? v.name() : preset;
            } else {
                name = v.name();
                spec = new HeavymaticSpec(name, "", "", List.of(new HeavymaticRegion(name, RegionRole.DIG, RegionRole.DIG.defaultColor(),
                        List.of(Box3i.of(0, 0, 0, 3, 3, 3)))));
            }
            HeavymaticPlacement p = HeavymaticPlacement.create(spec, name, 0, 0, 0);
            p.setTransform(placementTransform(p, v.world()));
            if (snap != null && v.data().has("cutaway")) {
                try {
                    p.setCutaway(dev.heavymatica.place.CutawayMode.valueOf(v.data().get("cutaway").getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // keeps ON
                }
            }
            PlacementStore.add(p);
            VitruviusApi.setObjectData(v.id(), dataFor(p.id()));
            State st = new State();
            st.nodeId = v.id();
            st.lastNode = v.world();
            st.lastPlacement = p.transform();
            st.lastHidden = v.hidden();
            STATES.put(p.id(), st);
            claimed.add(p.id());
        }

        for (HeavymaticPlacement p : PlacementStore.all()) {
            State st = STATES.get(p.id());
            SceneObjectView v = byPlacement.get(p.id().toString());
            if (v == null && st != null && claimed.contains(p.id())) continue;      // adopted just now
            if (v == null) {
                // no object yet (or it vanished with a closed project): make one under Loose
                Transform pose = nodePose(p);
                UUID id = VitruviusApi.addObject(HeavymaticObjectType.ID, p.presetName(), dataFor(p.id()), pose.pos().x(), pose.pos().y(), pose.pos().z());
                if (id == null) continue;
                VitruviusApi.setObjectPose(id, pose.pos().x(), pose.pos().y(), pose.pos().z(), pose.rot().steps(), mirrorCode(p.transform().mirror()));
                State ns = new State();
                ns.nodeId = id;
                ns.lastNode = pose;
                ns.lastPlacement = p.transform();
                ns.lastHidden = false;
                STATES.put(p.id(), ns);
                continue;
            }
            if (st == null || !st.nodeId.equals(v.id())) {
                st = new State();
                st.nodeId = v.id();
                st.lastNode = v.world();
                st.lastPlacement = p.transform();
                st.lastHidden = v.hidden();
                STATES.put(p.id(), st);
                // first sight of an object that was saved with a project: the placement is what was saved by Heavymatica,
                // so the object follows it
                Transform pose = nodePose(p);
                if (!pose.equals(v.world())) pushPose(p, st, pose);
                continue;
            }
            boolean nodeMoved = !v.world().equals(st.lastNode);
            boolean placementMoved = !samePose(p.transform(), st.lastPlacement);
            if (nodeMoved) {
                dev.heavymatica.core.Transform t = placementTransform(p, v.world());
                if (!samePose(t, p.transform())) p.setTransform(t);
                st.lastNode = v.world();
                st.lastPlacement = p.transform();
            } else if (placementMoved) {
                pushPose(p, st, nodePose(p));
            }
            if (v.hidden() != st.lastHidden) {
                st.lastHidden = v.hidden();
                if (p.enabled() == v.hidden()) p.setEnabled(!v.hidden());
            }
        }
        STATES.keySet().removeIf(id -> PlacementStore.byId(id).isEmpty());
    }

    private static void pushPose(HeavymaticPlacement p, State st, Transform pose) {
        if (VitruviusApi.setObjectPose(st.nodeId, pose.pos().x(), pose.pos().y(), pose.pos().z(), pose.rot().steps(), mirrorCode(p.transform().mirror()))) {
            st.lastNode = pose;
            st.lastPlacement = p.transform();
        }
    }

    /** Forget everything (leaving a world). */
    public static void reset() {
        STATES.clear();
    }
}
