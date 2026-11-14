package dev.heavymatica.export;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.HeavymaticRegion;
import dev.heavymatica.manhattan.ManhattanBridge;
import dev.heavymatica.place.HeavymaticPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Background terrain scan of placements: at most {@link #BUDGET} cells per client tick in total, only loaded chunks
 * (cells in unloaded chunks count as unscanned). One scan per placement; starting again restarts it. Client thread only.
 *
 * Overlapping boxes of one region are not deduplicated (each box is scanned on its own); array copies never overlap.
 * Edge falling blocks on a face shared by two adjacent boxes of the same region are counted too (cheap, slightly
 * pessimistic).
 */
public final class TerrainScanner {
    public static final int BUDGET = 20000;

    private static final Map<UUID, Scan> SCANS = new LinkedHashMap<>();

    private TerrainScanner() {}

    /** Starts (or restarts) the scan of a placement; {@code onDone} gets one RegionScan per region, in spec order. */
    public static void start(HeavymaticPlacement p, Consumer<List<RegionScan>> onDone) {
        SCANS.put(p.id(), new Scan(p, onDone));
    }

    public static boolean isScanning(HeavymaticPlacement p) {
        return SCANS.containsKey(p.id());
    }

    /** 0..1 while scanning, -1 otherwise. */
    public static float progress(HeavymaticPlacement p) {
        Scan s = SCANS.get(p.id());
        return s == null ? -1f : s.progress();
    }

    public static void cancelAll() {
        SCANS.clear();
    }

    /** Runs one tick's worth of scanning; call on END_CLIENT_TICK. */
    public static void tick() {
        if (SCANS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            SCANS.clear();
            return;
        }
        int budget = BUDGET;
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Scan> e : new ArrayList<>(SCANS.entrySet())) {
            if (budget <= 0) break;
            Scan s = e.getValue();
            budget -= s.run(level, budget);
            if (s.finished()) finished.add(e.getKey());
        }
        for (UUID id : finished) {
            Scan s = SCANS.remove(id);
            if (s == null) continue;
            try {
                s.onDone.accept(s.result());
            } catch (RuntimeException ex) {
                dev.heavymatica.HeavymaticaMod.LOGGER.warn("export callback failed", ex);
            }
        }
    }

    // ---- one scan ----
    private static final class Scan {
        final Consumer<List<RegionScan>> onDone;
        final List<RegionState> regions = new ArrayList<>();
        final long totalInside;
        int current;

        Scan(HeavymaticPlacement p, Consumer<List<RegionScan>> onDone) {
            this.onDone = onDone;
            List<ManhattanBridge.NamedBoxes> protectedAreas;
            try {
                protectedAreas = ManhattanBridge.protectedAreas();
            } catch (RuntimeException ex) {
                protectedAreas = List.of();
            }
            long total = 0;
            for (HeavymaticRegion r : p.spec().regions()) {
                RegionState rs = new RegionState(r.name(), p.worldBoxes(r.name()), protectedAreas);
                regions.add(rs);
                total += rs.total;
            }
            this.totalInside = total;
        }

        float progress() {
            if (totalInside <= 0) return 0f;
            long done = 0;
            for (RegionState r : regions) done += r.insideVisited;
            return Math.min(0.99f, (float) done / totalInside);
        }

        boolean finished() {
            return current >= regions.size();
        }

        /** Scans up to {@code budget} cells, returns the number used. */
        int run(ClientLevel level, int budget) {
            int used = 0;
            while (used < budget && current < regions.size()) {
                RegionState r = regions.get(current);
                used += r.run(level, budget - used);
                if (r.done()) current++;
            }
            return used;
        }

        List<RegionScan> result() {
            List<RegionScan> out = new ArrayList<>();
            for (RegionState r : regions) out.add(r.toScan());
            return out;
        }
    }

    private static final class RegionState {
        final String name;
        final List<Box3i> boxes;
        final long total;
        final List<String> overlaps = new ArrayList<>();
        final BoxCursor inside;
        final BoxCursor shell;
        final BoxCursor top;
        long insideVisited, scanned, water, lava, air, falling, lavaAdjacent;
        final Map<Block, long[]> counts = new HashMap<>();
        int phase; // 0 inside, 1 shell, 2 top, 3 done

        int cacheCx = Integer.MIN_VALUE, cacheCz = Integer.MIN_VALUE;
        boolean cacheLoaded;

        RegionState(String name, List<Box3i> boxes, List<ManhattanBridge.NamedBoxes> protectedAreas) {
            this.name = name;
            this.boxes = boxes;
            long t = 0;
            List<Box3i> shells = new ArrayList<>();
            List<Box3i> tops = new ArrayList<>();
            for (Box3i b : boxes) {
                t += b.volume();
                shells.addAll(BoxCursor.shellWithoutTop(b));
                tops.add(BoxCursor.above(b));
            }
            this.total = t;
            this.inside = new BoxCursor(boxes);
            this.shell = new BoxCursor(shells);
            this.top = new BoxCursor(tops);
            for (ManhattanBridge.NamedBoxes pa : protectedAreas) {
                if (intersectsAny(pa.boxes())) overlaps.add(pa.name());
            }
        }

        private boolean intersectsAny(List<Box3i> others) {
            for (Box3i a : boxes) for (Box3i o : others) if (a.intersects(o)) return true;
            return false;
        }

        boolean done() {
            return phase >= 3;
        }

        /** Skips shell cells that lie in another box of the same region (only checked for up to 64 boxes). */
        private boolean insideRegion(int x, int y, int z) {
            if (boxes.size() > 64) return false;
            for (Box3i b : boxes) if (b.contains(x, y, z)) return true;
            return false;
        }

        private boolean loaded(ClientLevel level, int x, int y, int z) {
            if (level.isOutsideBuildHeight(y)) return false;
            int cx = x >> 4, cz = z >> 4;
            if (cx != cacheCx || cz != cacheCz) {
                cacheCx = cx;
                cacheCz = cz;
                cacheLoaded = level.hasChunk(cx, cz);
            }
            return cacheLoaded;
        }

        int run(ClientLevel level, int budget) {
            int used = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (used < budget && phase < 3) {
                int left = budget - used;
                switch (phase) {
                    case 0 -> used += inside.advance(left, (x, y, z) -> {
                        insideVisited++;
                        if (!loaded(level, x, y, z)) return;
                        scanned++;
                        BlockState st = level.getBlockState(pos.set(x, y, z));
                        classify(st);
                        if (st.getBlock() instanceof FallingBlock && BoxCursor.onBoundary(inside.current(), x, y, z)) falling++;
                    });
                    case 1 -> used += shell.advance(left, (x, y, z) -> {
                        if (!loaded(level, x, y, z) || insideRegion(x, y, z)) return;
                        if (level.getBlockState(pos.set(x, y, z)).getFluidState().is(FluidTags.LAVA)) lavaAdjacent++;
                    });
                    default -> used += top.advance(left, (x, y, z) -> {
                        if (!loaded(level, x, y, z) || insideRegion(x, y, z)) return;
                        BlockState st = level.getBlockState(pos.set(x, y, z));
                        if (st.getFluidState().is(FluidTags.LAVA)) lavaAdjacent++;
                        if (st.getBlock() instanceof FallingBlock) falling++;
                    });
                }
                if (phase == 0 && inside.done()) phase = 1;
                else if (phase == 1 && shell.done()) phase = 2;
                else if (phase == 2 && top.done()) phase = 3;
            }
            return used;
        }

        private void classify(BlockState st) {
            if (st.isAir()) {
                air++;
                return;
            }
            counts.computeIfAbsent(st.getBlock(), b -> new long[1])[0]++;
            FluidState fs = st.getFluidState();
            if (!fs.isEmpty()) {
                if (fs.is(FluidTags.WATER)) water++;
                else if (fs.is(FluidTags.LAVA)) lava++;
            }
        }

        RegionScan toScan() {
            Map<String, Long> byId = new HashMap<>();
            for (Map.Entry<Block, long[]> e : counts.entrySet()) {
                String id = BuiltInRegistries.BLOCK.getKey(e.getKey()).toString();
                if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
                byId.merge(id, e.getValue()[0], Long::sum);
            }
            return new RegionScan(name, total, scanned, byId, water, lava, air, falling, lavaAdjacent, overlaps);
        }
    }
}
