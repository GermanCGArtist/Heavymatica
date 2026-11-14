package dev.heavymatica.place;

import dev.heavymatica.core.ArrayConfig;
import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.Mirror;
import dev.heavymatica.core.Rotation;
import dev.heavymatica.core.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** A preset placed in the world. Mutable, client thread only. No Minecraft imports (testable). */
public final class HeavymaticPlacement {
    /** Set by PlacementStore; called after every change. */
    static volatile Consumer<HeavymaticPlacement> changeHook = p -> { };

    private final UUID id;
    private String presetName;
    private HeavymaticSpec spec;
    private Transform transform;
    private boolean enabled = true;
    private CutawayMode cutaway = CutawayMode.ON;
    private final Map<String, Boolean> regionVisible = new LinkedHashMap<>();
    private String manhattanName;
    private long lastSentHash;
    private boolean outOfSync;

    private Map<String, List<Box3i>> cache;

    public HeavymaticPlacement(UUID id, String presetName, HeavymaticSpec spec, Transform transform) {
        this.id = id;
        this.presetName = presetName;
        this.spec = spec;
        this.transform = transform;
        this.manhattanName = presetName;
    }

    /**
     * New placement from a spec. Enforces the normalized-preset rule: if the spec's bounds do not start at (0,0,0),
     * the boxes are shifted and the shift is added to the origin, so world boxes stay the same.
     */
    public static HeavymaticPlacement create(HeavymaticSpec spec, String presetName, int originX, int originY, int originZ) {
        Box3i b = spec.bounds();
        int sx = b.minX(), sy = b.minY(), sz = b.minZ();
        HeavymaticSpec use = spec;
        if (sx != 0 || sy != 0 || sz != 0) {
            List<HeavymaticRegion> regions = new ArrayList<>();
            for (HeavymaticRegion r : spec.regions()) {
                List<Box3i> boxes = new ArrayList<>();
                for (Box3i box : r.boxes()) boxes.add(box.offset(-sx, -sy, -sz));
                regions.add(new HeavymaticRegion(r.name(), r.role(), r.color(), boxes));
            }
            use = new HeavymaticSpec(spec.name(), spec.author(), spec.description(), regions);
        }
        Transform t = new Transform(originX + sx, originY + sy, originZ + sz, Rotation.NONE, Mirror.NONE, ArrayConfig.NONE);
        HeavymaticPlacement p = new HeavymaticPlacement(UUID.randomUUID(), presetName, use, t);
        for (HeavymaticRegion r : use.regions()) p.regionVisible.put(r.name(), true);
        return p;
    }

    /** Restores stored state without notifying anyone (used by PlacementJson). */
    public static HeavymaticPlacement restore(UUID id, String presetName, HeavymaticSpec spec, Transform transform,
                                              boolean enabled, CutawayMode cutaway, Map<String, Boolean> regionVisible,
                                              String manhattanName, long lastSentHash, boolean outOfSync) {
        HeavymaticPlacement p = new HeavymaticPlacement(id, presetName, spec, transform);
        p.enabled = enabled;
        p.cutaway = cutaway == null ? CutawayMode.ON : cutaway;
        if (regionVisible != null) p.regionVisible.putAll(regionVisible);
        if (manhattanName != null && !manhattanName.isBlank()) p.manhattanName = manhattanName;
        p.lastSentHash = lastSentHash;
        p.outOfSync = outOfSync;
        return p;
    }

    // ---- getters ----
    public UUID id() { return id; }
    public String entryId() { return "heavymatica:" + id; }
    public String presetName() { return presetName; }
    public HeavymaticSpec spec() { return spec; }
    public Transform transform() { return transform; }
    public boolean enabled() { return enabled; }
    public CutawayMode cutaway() { return cutaway; }
    public Map<String, Boolean> regionVisible() { return Collections.unmodifiableMap(regionVisible); }
    public String manhattanName() { return manhattanName; }
    public long lastSentHash() { return lastSentHash; }
    public boolean outOfSync() { return outOfSync; }

    public boolean isRegionVisible(String regionName) {
        return regionVisible.getOrDefault(regionName, true);
    }

    // ---- world boxes ----
    private Map<String, List<Box3i>> byRegion() {
        if (cache == null) {
            Map<String, List<Box3i>> m = new LinkedHashMap<>();
            for (HeavymaticRegion r : spec.regions()) {
                m.put(r.name(), List.copyOf(transform.apply(r.boxes())));
            }
            cache = m;
        }
        return cache;
    }

    /** World boxes of all array copies of one region (empty if unknown). */
    public List<Box3i> worldBoxes(String regionName) {
        return byRegion().getOrDefault(regionName, List.of());
    }

    public Map<String, List<Box3i>> worldBoxesByRegion() {
        return Collections.unmodifiableMap(byRegion());
    }

    public List<Box3i> allWorldBoxes() {
        List<Box3i> all = new ArrayList<>();
        for (List<Box3i> l : byRegion().values()) all.addAll(l);
        return all;
    }

    /** Bounds of all world boxes (all copies). */
    public Box3i worldBounds() {
        Box3i result = null;
        for (List<Box3i> l : byRegion().values()) {
            for (Box3i b : l) result = result == null ? b : result.union(b);
        }
        if (result == null) throw new IllegalStateException("placement has no boxes");
        return result;
    }

    // ---- setters ----
    public void setTransform(Transform t) { this.transform = t; markChanged(); }
    public void setOrigin(int x, int y, int z) {
        setTransform(new Transform(x, y, z, transform.rotation(), transform.mirror(), transform.array()));
    }
    public void nudge(int dx, int dy, int dz) {
        setOrigin(transform.originX() + dx, transform.originY() + dy, transform.originZ() + dz);
    }
    public void rotateClockwise() {
        Rotation next = switch (transform.rotation()) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
        setRotation(next);
    }
    public void setRotation(Rotation r) {
        setTransform(new Transform(transform.originX(), transform.originY(), transform.originZ(), r, transform.mirror(), transform.array()));
    }
    public void setMirror(Mirror m) {
        setTransform(new Transform(transform.originX(), transform.originY(), transform.originZ(), transform.rotation(), m, transform.array()));
    }
    public void setArray(ArrayConfig a) {
        setTransform(new Transform(transform.originX(), transform.originY(), transform.originZ(), transform.rotation(), transform.mirror(), a));
    }
    public void setEnabled(boolean v) { this.enabled = v; markChanged(); }
    public void setCutaway(CutawayMode m) { this.cutaway = m; markChanged(); }
    public void setRegionVisible(String regionName, boolean v) { regionVisible.put(regionName, v); markChanged(); }
    public void setManhattanName(String n) { this.manhattanName = n; markChanged(); }

    private void markChanged() {
        cache = null;
        outOfSync = lastSentHash != 0 && sendHash() != lastSentHash;
        changeHook.accept(this);
    }

    public void markSent() {
        lastSentHash = sendHash();
        outOfSync = false;
        changeHook.accept(this);
    }

    /** Manhattan reported that a script overwrote or deleted one of our areas. */
    public void markOutOfSync() {
        outOfSync = true;
        changeHook.accept(this);
    }

    // ---- Manhattan ----
    public String manhattanAreaName(HeavymaticRegion r) {
        return spec.regions().size() == 1 ? manhattanName : manhattanName + "_" + r.name();
    }

    /** Stable 64-bit FNV-1a over, per region in order: Manhattan area name, role name, world boxes of all copies. */
    public long sendHash() {
        long h = 0xcbf29ce484222325L;
        for (HeavymaticRegion r : spec.regions()) {
            h = str(h, manhattanAreaName(r));
            h = str(h, r.role().name());
            List<Box3i> boxes = worldBoxes(r.name());
            h = num(h, boxes.size());
            for (Box3i b : boxes) {
                h = num(h, b.minX()); h = num(h, b.minY()); h = num(h, b.minZ());
                h = num(h, b.maxX()); h = num(h, b.maxY()); h = num(h, b.maxZ());
            }
        }
        return h;
    }

    private static long num(long h, int v) {
        for (int i = 0; i < 4; i++) {
            h ^= (v >>> (i * 8)) & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static long str(long h, String s) {
        h = num(h, s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            h ^= c & 0xff; h *= 0x100000001b3L;
            h ^= (c >>> 8) & 0xff; h *= 0x100000001b3L;
        }
        return h;
    }
}
