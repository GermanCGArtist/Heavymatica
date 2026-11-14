package dev.heavymatica.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combines the per-placement export texts of a folder into one. Pure (no Minecraft imports). Like the single texts it
 * stays a valid Manhattan script: the header and the notes are "// " comments, blocks are separated by a blank line.
 */
public final class FolderExportText {
    private FolderExportText() {}

    /** One placement's finished export text with its label (preset name) and the Manhattan area names it defines. */
    public record Block(String label, List<String> areaNames, String text) {}

    public static String combine(String folderName, List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Folder ").append(folderName).append(": ").append(blocks.size())
                .append(blocks.size() == 1 ? " heavymatic" : " heavymatics").append('\n');
        Map<String, List<String>> owners = new LinkedHashMap<>();
        for (Block b : blocks) {
            for (String area : b.areaNames()) {
                owners.computeIfAbsent(area, k -> new ArrayList<>()).add(b.label());
            }
        }
        for (Map.Entry<String, List<String>> e : owners.entrySet()) {
            if (e.getValue().size() > 1) {
                sb.append("// Note: Manhattan area \"").append(e.getKey()).append("\" is defined by ")
                        .append(e.getValue().size()).append(" heavymatics (")
                        .append(String.join(", ", e.getValue())).append("); the later definition wins.\n");
            }
        }
        for (Block b : blocks) {
            sb.append('\n');
            sb.append(b.text().endsWith("\n") ? b.text() : b.text() + "\n");
        }
        return sb.toString();
    }
}
