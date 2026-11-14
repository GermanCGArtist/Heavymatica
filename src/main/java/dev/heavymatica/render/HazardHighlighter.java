package dev.heavymatica.render;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.place.CutawayMode;
import dev.heavymatica.place.HeavymaticPlacement;
import dev.heavymatica.place.PlacementStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Water and lava cells inside visible DIG regions of enabled placements whose cutaway is not ON. A budgeted scan on the client tick (reads
 * loaded chunks only, never changes anything); the nearest 2000 cells are drawn as small cubes.
 */
public final class HazardHighlighter {
    private static final int BUDGET_PER_TICK = 20_000;
    private static final int RESCAN_TICKS = 200;       // 10 s
    private static final int MAX_DRAWN = 2000;
    private static final int MAX_STORED = 200_000;     // per placement and kind
    private static final int REPICK_TICKS = 10;

    private static final GizmoStyle WATER = GizmoStyle.stroke(0xFF2F6BFF, 2.0f);
    private static final GizmoStyle LAVA = GizmoStyle.stroke(0xFFFF2A00, 2.0f);

    /** Scan state of one placement. */
    private static final class Scan {
        final UUID id;
        long fingerprint;
        Box3i[] boxes = new Box3i[0];
        int boxIdx;
        long cell;                  // linear index inside boxes[boxIdx]
        boolean running;
        int ticksSinceDone;
        long[] water = new long[64], lava = new long[64];
        int waterN, lavaN;
        long[] shownWater = new long[0], shownLava = new long[0];   // last finished result

        Scan(UUID id) { this.id = id; }
    }

    private static final Map<UUID, Scan> SCANS = new HashMap<>();
    private static boolean changed = true;
    private static boolean resultsChanged;
    private static int repickTicks;
    private static Object lastLevel;

    // what is drawn (replaced as a whole)
    private static volatile AABB[] drawBoxes = new AABB[0];
    private static volatile boolean[] drawIsLava = new boolean[0];

    private HazardHighlighter() {}

    static void init() {
        PlacementStore.addListener(() -> changed = true);
        ClientTickEvents.END_CLIENT_TICK.register(HazardHighlighter::tick);
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> render());
    }

    // ---- tick: bookkeeping + scan ----

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            lastLevel = level;
            SCANS.clear();
            changed = true;
            drawBoxes = new AABB[0];
            drawIsLava = new boolean[0];
        }
        if (level == null) return;
        if (changed) { changed = false; syncScans(); }
        if (SCANS.isEmpty()) {
            if (drawBoxes.length != 0) { drawBoxes = new AABB[0]; drawIsLava = new boolean[0]; }
            return;
        }

        int budget = BUDGET_PER_TICK;
        for (Scan s : SCANS.values()) {
            if (!s.running) {
                if (++s.ticksSinceDone >= RESCAN_TICKS) startScan(s);
                continue;
            }
            budget = scan(level, s, budget);
            if (budget <= 0) break;
        }
        if (resultsChanged || ++repickTicks >= REPICK_TICKS) {
            repickTicks = 0;
            resultsChanged = false;
            pick(mc);
        }
    }

    private static void syncScans() {
        Map<UUID, Scan> next = new HashMap<>();
        for (HeavymaticPlacement p : PlacementStore.all()) {
            // With the cutaway ON the mesher shows the real water and lava (see CutawayRegionMixin): no cubes needed.
            if (!p.enabled() || p.cutaway() == CutawayMode.ON) continue;
            List<Box3i> digs = new ArrayList<>();
            long fp = 0xcbf29ce484222325L;
            for (HeavymaticRegion r : p.spec().regions()) {
                if (r.role() != RegionRole.DIG || !p.isRegionVisible(r.name())) continue;
                for (Box3i b : p.worldBoxes(r.name())) {
                    digs.add(b);
                    fp = (fp ^ b.hashCode()) * 0x100000001b3L;
                }
            }
            if (digs.isEmpty()) continue;
            Scan s = SCANS.get(p.id());
            if (s == null) {
                s = new Scan(p.id());
                s.fingerprint = fp;
                s.boxes = digs.toArray(new Box3i[0]);
                startScan(s);
            } else if (s.fingerprint != fp) {
                s.fingerprint = fp;
                s.boxes = digs.toArray(new Box3i[0]);
                startScan(s);
            }
            next.put(p.id(), s);
        }
        boolean removed = next.size() != SCANS.size();
        SCANS.clear();
        SCANS.putAll(next);
        if (removed) resultsChanged = true;
    }

    private static void startScan(Scan s) {
        s.running = true;
        s.boxIdx = 0;
        s.cell = 0;
        s.waterN = 0;
        s.lavaN = 0;
        s.ticksSinceDone = 0;
    }

    private static int scan(ClientLevel level, Scan s, int budget) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int cachedCx = Integer.MIN_VALUE, cachedCz = Integer.MIN_VALUE;
        LevelChunk chunk = null;
        int minY = level.getMinY(), maxY = level.getMaxY();   // maxY exclusive in 26.x? clamp below defensively
        while (budget > 0 && s.boxIdx < s.boxes.length) {
            Box3i b = s.boxes[s.boxIdx];
            long sx = b.sizeX(), sz = b.sizeZ();
            long total = b.volume();
            while (budget > 0 && s.cell < total) {
                long c = s.cell++;
                budget--;
                int y = b.minY() + (int) (c / (sx * sz));
                long rem = c % (sx * sz);
                int z = b.minZ() + (int) (rem / sx);
                int x = b.minX() + (int) (rem % sx);
                if (y < minY || y >= maxY) continue;
                int cx = x >> 4, cz = z >> 4;
                if (cx != cachedCx || cz != cachedCz) {
                    cachedCx = cx; cachedCz = cz;
                    chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                }
                if (chunk == null) continue;
                mp.set(x, y, z);
                FluidState fs = chunk.getFluidState(mp);
                if (fs.isEmpty()) continue;
                if (fs.is(FluidTags.WATER)) {
                    if (s.waterN < MAX_STORED) {
                        if (s.waterN == s.water.length) s.water = Arrays.copyOf(s.water, s.waterN * 2);
                        s.water[s.waterN++] = mp.asLong();
                    }
                } else if (fs.is(FluidTags.LAVA)) {
                    if (s.lavaN < MAX_STORED) {
                        if (s.lavaN == s.lava.length) s.lava = Arrays.copyOf(s.lava, s.lavaN * 2);
                        s.lava[s.lavaN++] = mp.asLong();
                    }
                }
            }
            if (s.cell >= total) { s.boxIdx++; s.cell = 0; }
        }
        if (s.boxIdx >= s.boxes.length) {
            s.running = false;
            s.shownWater = Arrays.copyOf(s.water, s.waterN);
            s.shownLava = Arrays.copyOf(s.lava, s.lavaN);
            resultsChanged = true;
        }
        return budget;
    }

    // ---- choose the nearest cells to draw ----

    private static void pick(Minecraft mc) {
        if (mc.player == null) return;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        int total = 0;
        for (Scan s : SCANS.values()) total += s.shownWater.length + s.shownLava.length;
        if (total == 0) {
            drawBoxes = new AABB[0];
            drawIsLava = new boolean[0];
            return;
        }
        // (distance, kind, pos) for all; keep the nearest MAX_DRAWN
        long[] pos = new long[total];
        double[] dist = new double[total];
        boolean[] lava = new boolean[total];
        int n = 0;
        for (Scan s : SCANS.values()) {
            for (int pass = 0; pass < 2; pass++) {
                long[] arr = pass == 0 ? s.shownWater : s.shownLava;
                for (long l : arr) {
                    double dx = BlockPos.getX(l) + 0.5 - px, dy = BlockPos.getY(l) + 0.5 - py, dz = BlockPos.getZ(l) + 0.5 - pz;
                    pos[n] = l; dist[n] = dx * dx + dy * dy + dz * dz; lava[n] = pass == 1; n++;
                }
            }
        }
        int keep = Math.min(n, MAX_DRAWN);
        int[] order;
        if (n <= MAX_DRAWN) {
            order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
        } else {
            // quickselect-free approach: sort a copy of the distances to get the cutoff, then take everything <= it
            double[] copy = Arrays.copyOf(dist, n);
            Arrays.sort(copy);
            double cutoff = copy[keep - 1];
            order = new int[keep];
            int k = 0;
            for (int i = 0; i < n && k < keep; i++) if (dist[i] <= cutoff) order[k++] = i;
            order = Arrays.copyOf(order, k);
        }
        AABB[] boxes = new AABB[order.length];
        boolean[] isLava = new boolean[order.length];
        for (int i = 0; i < order.length; i++) {
            long l = pos[order[i]];
            int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
            boxes[i] = new AABB(x + 0.3, y + 0.3, z + 0.3, x + 0.7, y + 0.7, z + 0.7);
            isLava[i] = lava[order[i]];
        }
        drawIsLava = isLava;
        drawBoxes = boxes;
    }

    // ---- draw ----

    private static void render() {
        AABB[] boxes = drawBoxes;
        boolean[] isLava = drawIsLava;
        Minecraft mc = Minecraft.getInstance();
        if (boxes.length == 0 || mc.levelRenderer == null) return;
        int n = Math.min(boxes.length, isLava.length);
        try (var ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < n; i++) Gizmos.cuboid(boxes[i], isLava[i] ? LAVA : WATER);
        }
    }
}
