package dev.heavymatica.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure logic of the "heavymatica:area" scene object (Litematica Array API v2): the data JSON, the union of the boxes,
 * the world transform of boxes and de-duplication. No Minecraft and no schemarray types, so it is unit tested.
 * <p>
 * Data JSON: {"name": "area", "mode": "dig"|"protect", "preset": "", "boxes": [[x1,y1,z1,x2,y2,z2], ...]}
 * with inclusive block corners (the .heavymatic box format), local to the object's origin.
 */
public record AreaData(String name, Mode mode, String preset, List<Box3i> boxes) {
    public enum Mode {
        DIG, PROTECT;

        public RegionRole role() {
            return this == DIG ? RegionRole.DIG : RegionRole.PROTECT;
        }

        public static Mode parse(String s) {
            return s != null && s.toLowerCase(Locale.ROOT).startsWith("p") ? PROTECT : DIG;
        }
    }

    /** The Mirror (0 none, 1 front/back = flips X, 2 left/right = flips Z) and turn steps of a world transform. */
    public record Pose(int x, int y, int z, int rotSteps, int mirror) {}

    public AreaData {
        if (name == null) name = "area";
        if (mode == null) mode = Mode.DIG;
        if (preset == null) preset = "";
        boxes = List.copyOf(boxes);
    }

    public static AreaData defaults() {
        return new AreaData("area", Mode.DIG, "", List.of(Box3i.of(0, 0, 0, 15, 7, 15)));
    }

    public static AreaData fromJson(JsonObject o) {
        List<Box3i> boxes = new ArrayList<>();
        JsonElement be = o == null ? null : o.get("boxes");
        if (be != null && be.isJsonArray()) {
            for (JsonElement e : be.getAsJsonArray()) {
                if (!e.isJsonArray() || e.getAsJsonArray().size() != 6) continue;
                JsonArray a = e.getAsJsonArray();
                boxes.add(Box3i.of(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                        a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt()));
            }
        }
        String name = str(o, "name", "area");
        return new AreaData(name, Mode.parse(str(o, "mode", "dig")), str(o, "preset", ""), boxes);
    }

    private static String str(JsonObject o, String key, String def) {
        if (o == null) return def;
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("mode", mode.name().toLowerCase(Locale.ROOT));
        o.addProperty("preset", preset);
        JsonArray arr = new JsonArray();
        for (Box3i b : boxes) {
            JsonArray a = new JsonArray();
            for (int v : b.toArray()) a.add(v);
            arr.add(a);
        }
        o.add("boxes", arr);
        return o;
    }

    /** Smallest box around all boxes (inclusive), or a single block at the origin when there are none. */
    public Box3i union() {
        return union(boxes);
    }

    public static Box3i union(List<Box3i> boxes) {
        if (boxes.isEmpty()) return Box3i.of(0, 0, 0, 0, 0, 0);
        Box3i u = boxes.get(0);
        for (Box3i b : boxes) u = u.union(b);
        return u;
    }

    /** One local box to the world: mirror, then turn clockwise, then move (schemarray's Transform.apply on both corners). */
    public static Box3i toWorld(Box3i b, Pose p) {
        int[] c1 = point(b.minX(), b.minY(), b.minZ(), p);
        int[] c2 = point(b.maxX(), b.maxY(), b.maxZ(), p);
        return Box3i.of(c1[0], c1[1], c1[2], c2[0], c2[1], c2[2]);
    }

    private static int[] point(int x, int y, int z, Pose p) {
        if (p.mirror() == 2) z = -z;
        else if (p.mirror() == 1) x = -x;
        int rx, rz;
        switch (Math.floorMod(p.rotSteps(), 4)) {
            case 1 -> { rx = -z; rz = x; }
            case 2 -> { rx = -x; rz = -z; }
            case 3 -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return new int[]{rx + p.x(), y + p.y(), rz + p.z()};
    }

    public List<Box3i> toWorld(Pose p) {
        List<Box3i> out = new ArrayList<>(boxes.size());
        for (Box3i b : boxes) out.add(toWorld(b, p));
        return out;
    }

    /** A Manhattan-safe area name. */
    public static String safeName(String s) {
        String r = s == null ? "" : s.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return r.isEmpty() ? "area" : r;
    }

    /** An area to send: a Manhattan name, a role and world boxes. */
    public record Plan(String name, RegionRole role, List<Box3i> boxes) {}

    /** Same role and the same set of boxes (order and duplicate boxes do not matter). */
    static String key(Plan p) {
        return p.role().name() + ":" + p.boxes().stream().sorted(ORDER).distinct().map(b -> java.util.Arrays.toString(b.toArray())).toList();
    }

    private static final Comparator<Box3i> ORDER = Comparator.comparingInt(Box3i::minX).thenComparingInt(Box3i::minY)
            .thenComparingInt(Box3i::minZ).thenComparingInt(Box3i::maxX).thenComparingInt(Box3i::maxY).thenComparingInt(Box3i::maxZ);

    /** Drops plans of {@code candidates} that equal (role + boxes) a plan in {@code already} or an earlier candidate. */
    public static List<Plan> dropDuplicates(List<Plan> already, List<Plan> candidates) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Plan p : already) seen.add(key(p));
        List<Plan> out = new ArrayList<>();
        for (Plan p : candidates) if (seen.add(key(p))) out.add(p);
        return out;
    }
}
