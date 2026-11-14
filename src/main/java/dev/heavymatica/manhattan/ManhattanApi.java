package dev.heavymatica.manhattan;

import dev.heavymatica.core.Box3i;
import dev.heavymatica.core.RegionRole;
import dev.heavymatica.manhattan.ManhattanBridge.NamedBoxes;
import dev.heavymatica.manhattan.ManhattanBridge.SendResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The reflection core: holds the cached Methods of a class shaped like dev.baritonequeue.api.ManhattanAreas.
 * No Fabric or Minecraft imports, so it is testable with a fake class. Exceptions of the target are not swallowed here.
 */
final class ManhattanApi {
    private final Method put;
    private final Method remove;
    private final Method selection;
    private final Method protectedAreas;
    private final Method addListener;
    private final Class<?> roleClass;
    private final Method infoName;
    private final Method infoBoxes;
    final int apiVersion;

    ManhattanApi(Class<?> areas) throws ReflectiveOperationException {
        apiVersion = areas.getField("API_VERSION").getInt(null);
        roleClass = nested(areas, "Role");
        Class<?> infoClass = nested(areas, "AreaInfo");
        put = areas.getMethod("put", String.class, List.class, roleClass, boolean.class);
        remove = areas.getMethod("remove", String.class);
        selection = areas.getMethod("selection");
        protectedAreas = areas.getMethod("protectedAreas");
        addListener = areas.getMethod("addListener", BiConsumer.class);
        infoName = infoClass.getMethod("name");
        infoBoxes = infoClass.getMethod("boxes");
    }

    /** Null (never throws) when the class does not have the expected shape or its API_VERSION is below 1. */
    static ManhattanApi tryCreate(Class<?> areas) {
        try {
            ManhattanApi api = new ManhattanApi(areas);
            return api.apiVersion >= 1 ? api : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Class<?> nested(Class<?> outer, String simple) throws ClassNotFoundException {
        for (Class<?> c : outer.getDeclaredClasses()) {
            if (c.getSimpleName().equals(simple)) return c;
        }
        throw new ClassNotFoundException(outer.getName() + "$" + simple);
    }

    private static Object enumByName(Class<?> enumClass, String name) {
        for (Object o : enumClass.getEnumConstants()) {
            if (((Enum<?>) o).name().equals(name)) return o;
        }
        throw new IllegalArgumentException("No constant " + name + " in " + enumClass.getName());
    }

    private static Object call(Method m, Object... args) throws ReflectiveOperationException {
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error err) throw err;
            throw e;
        }
    }

    static List<int[]> toArrays(List<Box3i> boxes) {
        List<int[]> out = new ArrayList<>(boxes.size());
        for (Box3i b : boxes) out.add(b.toArray());
        return out;
    }

    static List<Box3i> fromArrays(List<?> arrays) {
        List<Box3i> out = new ArrayList<>(arrays.size());
        for (Object o : arrays) {
            int[] a = (int[]) o;
            out.add(Box3i.of(a[0], a[1], a[2], a[3], a[4], a[5]));
        }
        return out;
    }

    SendResult put(String name, List<Box3i> boxes, RegionRole role, boolean replaceScriptOwned)
            throws ReflectiveOperationException {
        Object r = call(put, name, toArrays(boxes), enumByName(roleClass, role.name()), replaceScriptOwned);
        try {
            return SendResult.valueOf(((Enum<?>) r).name());
        } catch (IllegalArgumentException e) {
            return SendResult.INVALID;
        }
    }

    boolean remove(String name) throws ReflectiveOperationException {
        return (Boolean) call(remove, name);
    }

    List<Box3i> selection() throws ReflectiveOperationException {
        return fromArrays((List<?>) call(selection));
    }

    List<NamedBoxes> protectedAreas() throws ReflectiveOperationException {
        List<?> infos = (List<?>) call(protectedAreas);
        List<NamedBoxes> out = new ArrayList<>(infos.size());
        for (Object info : infos) {
            out.add(new NamedBoxes((String) infoName.invoke(info), fromArrays((List<?>) infoBoxes.invoke(info))));
        }
        return out;
    }

    /** The listener gets the area name and the new owner's enum name (MANUAL, SCRIPT, HEAVYMATICA), null = deleted. */
    void addListener(BiConsumer<String, String> listener) throws ReflectiveOperationException {
        BiConsumer<String, Object> adapter = (name, owner) ->
                listener.accept(name, owner == null ? null : ((Enum<?>) owner).name());
        call(addListener, adapter);
    }
}
