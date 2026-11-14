package dev.heavymatica.place;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Identifies the current world for per-world storage. */
public final class WorldKey {
    private WorldKey() {
    }

    /**
     * Singleplayer: folder name of the save (integrated server's LevelResource.ROOT path, the same way Manhattan finds it),
     * prefixed "sp-". Multiplayer: server address, prefixed "mp-". Sanitized to [A-Za-z0-9._-], max 80 chars.
     * Null when not in a world.
     */
    public static String current() {
        Minecraft mc = Minecraft.getInstance();
        String raw = null;
        var sp = mc.getSingleplayerServer();
        if (sp != null) {
            Path root = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = root.getFileName();
            raw = name == null ? null : "sp-" + name;
        } else {
            ServerData sd = mc.getCurrentServer();
            if (sd != null) raw = "mp-" + sd.ip;
        }
        return sanitize(raw);
    }

    static String sanitize(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        if (s.length() > 80) s = s.substring(0, 80);
        return s.isEmpty() ? null : s;
    }
}
