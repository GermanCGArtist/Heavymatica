package dev.heavymatica.place;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.heavymatica.core.ArrayConfig;
import dev.heavymatica.core.HeavymaticJson;
import dev.heavymatica.core.HeavymaticSpec;
import dev.heavymatica.core.Mirror;
import dev.heavymatica.core.Rotation;
import dev.heavymatica.core.Transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JSON (de)serialization of placements. No Minecraft imports. */
public final class PlacementJson {
    private static final int VERSION = 1;

    private PlacementJson() {
    }

    public static String write(List<HeavymaticPlacement> placements) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray arr = new JsonArray();
        for (HeavymaticPlacement p : placements) {
            JsonObject o = new JsonObject();
            o.addProperty("id", p.id().toString());
            o.addProperty("presetName", p.presetName());
            o.add("spec", JsonParser.parseString(HeavymaticJson.write(p.spec())));
            Transform t = p.transform();
            JsonObject to = new JsonObject();
            JsonArray origin = new JsonArray();
            origin.add(t.originX()); origin.add(t.originY()); origin.add(t.originZ());
            to.add("origin", origin);
            to.addProperty("rotation", t.rotation().name());
            to.addProperty("mirror", t.mirror().name());
            ArrayConfig a = t.array();
            JsonObject ao = new JsonObject();
            ao.addProperty("copiesX", a.copiesX()); ao.addProperty("copiesY", a.copiesY()); ao.addProperty("copiesZ", a.copiesZ());
            ao.addProperty("gapX", a.gapX()); ao.addProperty("gapY", a.gapY()); ao.addProperty("gapZ", a.gapZ());
            to.add("array", ao);
            o.add("transform", to);
            o.addProperty("enabled", p.enabled());
            o.addProperty("cutaway", p.cutaway().name());
            JsonObject rv = new JsonObject();
            for (Map.Entry<String, Boolean> e : p.regionVisible().entrySet()) rv.addProperty(e.getKey(), e.getValue());
            o.add("regionVisible", rv);
            o.addProperty("manhattanName", p.manhattanName());
            o.addProperty("lastSentHash", p.lastSentHash());
            o.addProperty("outOfSync", p.outOfSync());
            arr.add(o);
        }
        root.add("placements", arr);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /** @throws RuntimeException on unreadable content */
    public static List<HeavymaticPlacement> read(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = root.get("version").getAsInt();
        if (version != VERSION) throw new IllegalArgumentException("unsupported placements version " + version);
        List<HeavymaticPlacement> out = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("placements")) {
            JsonObject o = el.getAsJsonObject();
            HeavymaticSpec spec = HeavymaticJson.read(o.get("spec").toString());
            JsonObject to = o.getAsJsonObject("transform");
            JsonArray origin = to.getAsJsonArray("origin");
            JsonObject ao = to.getAsJsonObject("array");
            ArrayConfig a = new ArrayConfig(ao.get("copiesX").getAsInt(), ao.get("copiesY").getAsInt(), ao.get("copiesZ").getAsInt(),
                    ao.get("gapX").getAsInt(), ao.get("gapY").getAsInt(), ao.get("gapZ").getAsInt());
            Transform t = new Transform(origin.get(0).getAsInt(), origin.get(1).getAsInt(), origin.get(2).getAsInt(),
                    Rotation.valueOf(to.get("rotation").getAsString()), Mirror.valueOf(to.get("mirror").getAsString()), a);
            Map<String, Boolean> rv = new LinkedHashMap<>();
            JsonObject rvo = o.getAsJsonObject("regionVisible");
            if (rvo != null) for (Map.Entry<String, JsonElement> e : rvo.entrySet()) rv.put(e.getKey(), e.getValue().getAsBoolean());
            out.add(HeavymaticPlacement.restore(
                    UUID.fromString(o.get("id").getAsString()),
                    o.get("presetName").getAsString(),
                    spec, t,
                    o.get("enabled").getAsBoolean(),
                    CutawayMode.valueOf(o.get("cutaway").getAsString()),
                    rv,
                    o.has("manhattanName") ? o.get("manhattanName").getAsString() : null,
                    o.get("lastSentHash").getAsLong(),
                    o.get("outOfSync").getAsBoolean()));
        }
        return out;
    }
}
