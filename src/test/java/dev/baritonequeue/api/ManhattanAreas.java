package dev.baritonequeue.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/** In-memory fake with the same public shape as Manhattan's real ManhattanAreas, for the reflection tests. */
public final class ManhattanAreas {
    public static final int API_VERSION = 1;
    public static final int MAX_BOXES = 4096;

    private ManhattanAreas() {}

    public enum Role { DIG, CLEAR, FLOOD, FILL, PROTECT, CUSTOM }

    public enum Owner { MANUAL, SCRIPT, HEAVYMATICA }

    public enum Result { OK, SCRIPT_OWNED, IN_USE, INVALID, NOT_IN_WORLD }

    public record AreaInfo(String name, List<int[]> boxes, Role role, Owner owner, long updated) {}

    // ---- in-memory state ----
    private static final Map<String, AreaInfo> AREAS = new LinkedHashMap<>();
    private static final Set<String> IN_USE = new java.util.HashSet<>();
    private static final List<BiConsumer<String, Owner>> LISTENERS = new ArrayList<>();
    private static List<int[]> selection = new ArrayList<>();

    // test helpers (not part of the real API)
    public static void reset() {
        AREAS.clear();
        IN_USE.clear();
        LISTENERS.clear();
        selection = new ArrayList<>();
    }

    public static void putExternal(String name, Owner owner, Role role, List<int[]> boxes) {
        AREAS.put(name, new AreaInfo(name, boxes, role, owner, 1L));
        fire(name, owner);
    }

    public static void markInUse(String name) { IN_USE.add(name); }

    public static void setSelection(List<int[]> boxes) { selection = new ArrayList<>(boxes); }

    private static void fire(String name, Owner owner) {
        for (BiConsumer<String, Owner> l : List.copyOf(LISTENERS)) l.accept(name, owner);
    }

    // ---- the API ----
    public static Result put(String name, List<int[]> boxes, Role role, boolean replaceScriptOwned) {
        if (name == null || name.isBlank() || role == null || boxes == null || boxes.isEmpty() || boxes.size() > MAX_BOXES) {
            return Result.INVALID;
        }
        List<int[]> normalized = new ArrayList<>();
        for (int[] b : boxes) {
            if (b == null || b.length != 6) return Result.INVALID;
            normalized.add(new int[]{Math.min(b[0], b[3]), Math.min(b[1], b[4]), Math.min(b[2], b[5]),
                    Math.max(b[0], b[3]), Math.max(b[1], b[4]), Math.max(b[2], b[5])});
        }
        String n = name.trim();
        if (IN_USE.contains(n)) return Result.IN_USE;
        AreaInfo old = AREAS.get(n);
        if (!replaceScriptOwned && old != null && old.owner() == Owner.SCRIPT) return Result.SCRIPT_OWNED;
        AREAS.put(n, new AreaInfo(n, normalized, role, Owner.HEAVYMATICA, 2L));
        fire(n, Owner.HEAVYMATICA);
        return Result.OK;
    }

    public static Optional<AreaInfo> get(String name) {
        return Optional.ofNullable(AREAS.get(name));
    }

    public static Set<String> names() {
        return Set.copyOf(AREAS.keySet());
    }

    public static boolean remove(String name) {
        AreaInfo a = AREAS.get(name);
        if (a == null || a.owner() != Owner.HEAVYMATICA || IN_USE.contains(name)) return false;
        AREAS.remove(name);
        fire(name, null);
        return true;
    }

    public static boolean select(String name) {
        return AREAS.containsKey(name);
    }

    public static List<int[]> selection() {
        return new ArrayList<>(selection);
    }

    public static List<AreaInfo> protectedAreas() {
        List<AreaInfo> out = new ArrayList<>();
        for (AreaInfo a : AREAS.values()) if (a.role() == Role.PROTECT) out.add(a);
        return List.copyOf(out);
    }

    public static void addListener(BiConsumer<String, Owner> listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
