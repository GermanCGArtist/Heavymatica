package dev.heavymatica.export;

import dev.heavymatica.place.HeavymaticPlacement;
import dev.vitruvius.api.SceneObjectView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Export of several heavymatics at once from the Vitruvius editor (a multi-selection, a folder, a project, an
 * assembly, or one object with an array). Every placed copy becomes its own block of the combined text, so an
 * arrayed tunnel of 5 segments exports 5 sets of Manhattan areas: the base copy under the heavymatic's own Manhattan
 * name, the array copies as {@code <name>_2}, {@code <name>_3}, ... in array order. Client thread only.
 */
public final class GroupExport {
    private GroupExport() {}

    /**
     * Turns editor views into the placements to scan: the base copy is the heavymatic itself, every other copy a
     * stand-alone snapshot at that copy's pose (never added to the store, so it is not drawn or saved).
     *
     * @param placementOf  the heavymatic behind a view (empty when it is not linked)
     * @param copyPose     the snapshot for a copy: (heavymatic, view) to a placement with the given Manhattan name
     */
    public static List<HeavymaticPlacement> placements(List<SceneObjectView> views,
                                                       Function<SceneObjectView, java.util.Optional<HeavymaticPlacement>> placementOf,
                                                       CopyFactory copyPose) {
        List<HeavymaticPlacement> out = new ArrayList<>();
        Set<UUID> seenBase = new LinkedHashSet<>();
        Map<UUID, Integer> copyNumber = new HashMap<>();
        Set<String> taken = new LinkedHashSet<>();
        // the base copies first keep their own names, so the array copies are numbered around them
        for (SceneObjectView v : views) {
            var p = placementOf.apply(v);
            if (p.isPresent() && "base".equals(v.cell()) && seenBase.add(p.get().id())) taken.add(p.get().manhattanName());
        }
        Set<UUID> placedBase = new LinkedHashSet<>();
        for (SceneObjectView v : views) {
            var found = placementOf.apply(v);
            if (found.isEmpty()) continue;
            HeavymaticPlacement p = found.get();
            if ("base".equals(v.cell()) && placedBase.add(p.id())) {
                out.add(p);
                continue;
            }
            String name;
            do {                                                      // first copy is _2, like Realise array names them
                name = p.manhattanName() + "_" + (copyNumber.merge(p.id(), 1, Integer::sum) + 1);
            } while (!taken.add(name));
            out.add(copyPose.apply(p, v, name));
        }
        return out;
    }

    @FunctionalInterface
    public interface CopyFactory {
        HeavymaticPlacement apply(HeavymaticPlacement source, SceneObjectView view, String manhattanName);
    }

    /** A name for the export file and header: the heavymatic's own name when all copies are of one, else "selection-<time>". */
    public static String label(List<HeavymaticPlacement> sources) {
        Set<String> names = new LinkedHashSet<>();
        for (HeavymaticPlacement p : sources) names.add(p.presetName());
        if (names.size() == 1) return names.iterator().next() + " (" + sources.size() + " copies)";
        return "selection-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
}
