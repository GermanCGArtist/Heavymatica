package dev.heavymatica.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes the .heavymatic format (version 1). Unknown fields are ignored. */
public final class HeavymaticJson {
    private static final int VERSION = 1;

    private HeavymaticJson() {
    }

    public static String write(HeavymaticSpec s) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("name", s.name());
        root.addProperty("author", s.author());
        root.addProperty("description", s.description());

        JsonArray regions = new JsonArray();
        for (HeavymaticRegion region : s.regions()) {
            JsonObject r = new JsonObject();
            r.addProperty("name", region.name());
            r.addProperty("role", region.role().name());
            r.addProperty("color", region.color());
            JsonArray boxes = new JsonArray();
            for (Box3i box : region.boxes()) {
                JsonArray b = new JsonArray();
                for (int v : box.toArray()) {
                    b.add(v);
                }
                boxes.add(b);
            }
            r.add("boxes", boxes);
            regions.add(r);
        }
        root.add("regions", regions);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    public static HeavymaticSpec read(String json) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(".heavymatic: root must be a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException(".heavymatic: invalid JSON: " + e.getMessage(), e);
        }

        JsonElement version = root.get("version");
        if (version == null || version.isJsonNull()) {
            throw new IllegalArgumentException(".heavymatic: missing \"version\"");
        }
        if (!isNumber(version) || version.getAsInt() != VERSION) {
            throw new IllegalArgumentException(".heavymatic: unsupported \"version\" " + version + " (expected 1)");
        }

        String name = string(root, "name", null);
        String author = string(root, "author", "");
        String description = string(root, "description", "");

        List<HeavymaticRegion> regions = new ArrayList<>();
        JsonElement regionsEl = root.get("regions");
        if (regionsEl != null && !regionsEl.isJsonNull()) {
            if (!regionsEl.isJsonArray()) {
                throw new IllegalArgumentException(".heavymatic: \"regions\" must be an array");
            }
            for (JsonElement el : regionsEl.getAsJsonArray()) {
                regions.add(readRegion(el));
            }
        }
        return new HeavymaticSpec(name, author, description, regions);
    }

    private static HeavymaticRegion readRegion(JsonElement el) {
        if (!el.isJsonObject()) {
            throw new IllegalArgumentException(".heavymatic: each region must be an object");
        }
        JsonObject r = el.getAsJsonObject();
        String name = string(r, "name", null);
        RegionRole role = parseRole(string(r, "role", null));

        int color = role.defaultColor();
        JsonElement colorEl = r.get("color");
        if (colorEl != null && !colorEl.isJsonNull()) {
            if (!isNumber(colorEl)) {
                throw new IllegalArgumentException(".heavymatic: region '" + name + "' color must be a number");
            }
            color = colorEl.getAsInt();
        }

        List<Box3i> boxes = new ArrayList<>();
        JsonElement boxesEl = r.get("boxes");
        if (boxesEl != null && !boxesEl.isJsonNull()) {
            if (!boxesEl.isJsonArray()) {
                throw new IllegalArgumentException(".heavymatic: region '" + name + "' boxes must be an array");
            }
            for (JsonElement b : boxesEl.getAsJsonArray()) {
                boxes.add(readBox(name, b));
            }
        }
        return new HeavymaticRegion(name, role, color, boxes);
    }

    private static RegionRole parseRole(String roleName) {
        if (roleName == null) {
            throw new IllegalArgumentException(".heavymatic: region is missing \"role\"");
        }
        try {
            return RegionRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(".heavymatic: unknown role '" + roleName + "'", e);
        }
    }

    private static Box3i readBox(String regionName, JsonElement b) {
        if (!b.isJsonArray() || b.getAsJsonArray().size() != 6) {
            throw new IllegalArgumentException(".heavymatic: region '" + regionName
                    + "' box must be an array of 6 numbers [minX,minY,minZ,maxX,maxY,maxZ]");
        }
        int[] v = new int[6];
        int i = 0;
        for (JsonElement n : b.getAsJsonArray()) {
            if (!isNumber(n)) {
                throw new IllegalArgumentException(".heavymatic: region '" + regionName
                        + "' box contains a non-number: " + n);
            }
            v[i++] = n.getAsInt();
        }
        return Box3i.of(v);
    }

    private static boolean isNumber(JsonElement el) {
        return el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(".heavymatic: \"" + key + "\" must be a string");
        }
        return el.getAsString();
    }
}
