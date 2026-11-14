package dev.heavymatica.core;

import java.util.List;
import java.util.Objects;

/** A named group of boxes with one role. Boxes are local to the preset origin. */
public record HeavymaticRegion(String name, RegionRole role, int color, List<Box3i> boxes) {
    public HeavymaticRegion {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("region name must not be blank");
        }
        Objects.requireNonNull(role, "region role must not be null");
        boxes = List.copyOf(boxes);
    }
}
