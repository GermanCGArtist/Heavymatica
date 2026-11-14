package dev.heavymatica.render;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Draws the boxes of visible regions (cutaway ON or WIREFRAME) the way Manhattan draws its selection. */
public final class WireframeRenderer {
    private static final double MAX_DIST = 160.0;

    private record Entry(AABB aabb, GizmoStyle style, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private static volatile boolean stale = true;
    private static Entry[] entries = new Entry[0];

    private WireframeRenderer() {}

    static void init() {
        PlacementStore.addListener(() -> stale = true);
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> render());
    }

    /** Argb for a region: its own color, or the role default when unset. Always opaque. */
    static int colorOf(HeavymaticRegion r) {
        int c = r.color() != 0 ? r.color() : r.role().defaultColor();
        return c | 0xFF000000;
    }

    private static void rebuild() {
        stale = false;
        List<Entry> list = new ArrayList<>();
        for (HeavymaticPlacement p : PlacementStore.all()) {
            if (!p.enabled() || p.cutaway() == CutawayMode.OFF) continue;
            for (HeavymaticRegion r : p.spec().regions()) {
                if (!p.isRegionVisible(r.name())) continue;
                GizmoStyle style = GizmoStyle.stroke(colorOf(r), 2.0f);
                for (Box3i b : p.worldBoxes(r.name())) {
                    list.add(new Entry(new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1).inflate(0.002),
                            style, b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1));
                }
            }
        }
        entries = list.toArray(new Entry[0]);
    }

    private static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null || mc.player == null) return;
        if (stale) rebuild();
        Entry[] es = entries;
        if (es.length == 0) return;
        Vec3 pos = mc.player.position();
        double px = pos.x, py = pos.y, pz = pos.z, lim = MAX_DIST * MAX_DIST;
        try (var ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (Entry e : es) {
                double dx = px < e.minX ? e.minX - px : Math.max(0, px - e.maxX);
                double dy = py < e.minY ? e.minY - py : Math.max(0, py - e.maxY);
                double dz = pz < e.minZ ? e.minZ - pz : Math.max(0, pz - e.maxZ);
                if (dx * dx + dy * dy + dz * dz > lim) continue;
                Gizmos.cuboid(e.aabb, e.style);
            }
        }
    }
}
