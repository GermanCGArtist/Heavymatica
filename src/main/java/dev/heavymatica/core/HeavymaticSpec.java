package dev.heavymatica.core;

import java.util.List;

/** A preset: a named set of regions. Boxes are local to the preset origin. */
public record HeavymaticSpec(String name, String author, String description, List<HeavymaticRegion> regions) {
    public HeavymaticSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("preset name must not be blank");
        }
        author = author == null ? "" : author;
        description = description == null ? "" : description;
        regions = List.copyOf(regions);
    }

    /** The smallest box holding every box of every region. */
    public Box3i bounds() {
        Box3i result = null;
        for (HeavymaticRegion region : regions) {
            for (Box3i box : region.boxes()) {
                result = result == null ? box : result.union(box);
            }
        }
        if (result == null) {
            throw new IllegalStateException("preset '" + name + "' has no boxes");
        }
        return result;
    }

    /** Total number of boxes over all regions. */
    public int boxCount() {
        int count = 0;
        for (HeavymaticRegion region : regions) {
            count += region.boxes().size();
        }
        return count;
    }
}
