package dev.heavymatica.place;

import dev.heavymatica.HeavymaticaMod;
import dev.heavymatica.core.HeavymaticJson;
import dev.heavymatica.core.HeavymaticSpec;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Presets in config/heavymatica/presets/*.heavymatic. */
public final class PresetLibrary {
    private static final String EXT = ".heavymatic";

    private PresetLibrary() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("heavymatica").resolve("presets");
    }

    /** Preset names (file names without extension), sorted; unreadable files are logged and skipped. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        Path dir = folder();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(EXT)).forEach(p -> {
                String file = p.getFileName().toString();
                String name = file.substring(0, file.length() - EXT.length());
                try {
                    HeavymaticJson.read(Files.readString(p, StandardCharsets.UTF_8));
                    out.add(name);
                } catch (Exception e) {
                    HeavymaticaMod.LOGGER.warn("Skipping unreadable preset {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            HeavymaticaMod.LOGGER.warn("Cannot list presets: {}", e.toString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static Optional<HeavymaticSpec> load(String name) {
        Path p = fileFor(name);
        if (p == null || !Files.isRegularFile(p)) return Optional.empty();
        try {
            return Optional.of(HeavymaticJson.read(Files.readString(p, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            HeavymaticaMod.LOGGER.warn("Cannot read preset {}: {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    /** Saves under a file name sanitized from spec.name(); overwrites. */
    public static void save(HeavymaticSpec spec) {
        try {
            Files.createDirectories(folder());
            Files.writeString(folder().resolve(sanitize(spec.name()) + EXT), HeavymaticJson.write(spec), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HeavymaticaMod.LOGGER.error("Cannot save preset {}", spec.name(), e);
        }
    }

    public static boolean delete(String name) {
        Path p = fileFor(name);
        if (p == null) return false;
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            HeavymaticaMod.LOGGER.warn("Cannot delete preset {}: {}", name, e.toString());
            return false;
        }
    }

    private static Path fileFor(String name) {
        if (name == null || name.isBlank()) return null;
        return folder().resolve(sanitize(name) + EXT);
    }

    static String sanitize(String name) {
        String s = name.trim().replaceAll("[^A-Za-z0-9._ -]", "_");
        if (s.isEmpty() || s.chars().allMatch(c -> c == '.')) s = "preset";
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
