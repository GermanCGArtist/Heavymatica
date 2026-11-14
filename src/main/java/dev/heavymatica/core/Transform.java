package dev.heavymatica.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Local boxes to world boxes. Order: mirror, rotate about the local origin (0,0,0), translate to origin, array.
 * <p>
 * Mirror is about the bounding box of the whole input: FRONT_BACK maps x to (minX+maxX)-x, LEFT_RIGHT maps z to
 * (minZ+maxZ)-z, so a mirrored shape keeps occupying the same local bounding box. Rotation then uses
 * CLOCKWISE_90: (x,z) -> (-z,x); CLOCKWISE_180: (x,z) -> (-x,-z); COUNTERCLOCKWISE_90: (x,z) -> (z,-x).
 * Array step per axis = size of the transformed shape's bounding box on that axis + gap; copy (0,0,0) is the original.
 * Copy order: index = (ix * copiesY + iy) * copiesZ + iz.
 */
public record Transform(int originX, int originY, int originZ, Rotation rotation, Mirror mirror, ArrayConfig array) {
    public Transform {
        if (rotation == null) rotation = Rotation.NONE;
        if (mirror == null) mirror = Mirror.NONE;
        if (array == null) array = ArrayConfig.NONE;
    }

    public static Transform identity() {
        return new Transform(0, 0, 0, Rotation.NONE, Mirror.NONE, ArrayConfig.NONE);
    }

    /** World boxes of all array copies, normalized. */
    public List<Box3i> apply(List<Box3i> local) {
        List<Box3i> out = new ArrayList<>();
        for (List<Box3i> copy : applyPerCopy(local)) out.addAll(copy);
        return out;
    }

    public List<List<Box3i>> applyPerCopy(List<Box3i> local) {
        List<List<Box3i>> result = new ArrayList<>(array.copyCount());
        if (local.isEmpty()) {
            for (int i = 0; i < array.copyCount(); i++) result.add(new ArrayList<>());
            return result;
        }
        int bx0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz1 = Integer.MIN_VALUE;
        for (Box3i b : local) {
            bx0 = Math.min(bx0, b.minX()); bx1 = Math.max(bx1, b.maxX());
            bz0 = Math.min(bz0, b.minZ()); bz1 = Math.max(bz1, b.maxZ());
        }
        List<Box3i> base = new ArrayList<>(local.size());
        for (Box3i b : local) {
            int x1 = b.minX(), x2 = b.maxX(), z1 = b.minZ(), z2 = b.maxZ();
            if (mirror == Mirror.FRONT_BACK) { x1 = bx0 + bx1 - b.maxX(); x2 = bx0 + bx1 - b.minX(); }
            if (mirror == Mirror.LEFT_RIGHT) { z1 = bz0 + bz1 - b.maxZ(); z2 = bz0 + bz1 - b.minZ(); }
            int ax, az, cx, cz;
            switch (rotation) {
                case CLOCKWISE_90 -> { ax = -z1; az = x1; cx = -z2; cz = x2; }
                case CLOCKWISE_180 -> { ax = -x1; az = -z1; cx = -x2; cz = -z2; }
                case COUNTERCLOCKWISE_90 -> { ax = z1; az = -x1; cx = z2; cz = -x2; }
                default -> { ax = x1; az = z1; cx = x2; cz = z2; }
            }
            base.add(Box3i.of(ax + originX, b.minY() + originY, az + originZ, cx + originX, b.maxY() + originY, cz + originZ));
        }
        Box3i bounds = base.get(0);
        for (Box3i b : base) bounds = bounds.union(b);
        int stepX = bounds.sizeX() + array.gapX();
        int stepY = bounds.sizeY() + array.gapY();
        int stepZ = bounds.sizeZ() + array.gapZ();
        if ((array.copiesX() > 1 && stepX < 1) || (array.copiesY() > 1 && stepY < 1) || (array.copiesZ() > 1 && stepZ < 1)) {
            throw new IllegalArgumentException("array step must be >= 1 (steps " + stepX + "," + stepY + "," + stepZ + ")");
        }
        for (int ix = 0; ix < array.copiesX(); ix++)
            for (int iy = 0; iy < array.copiesY(); iy++)
                for (int iz = 0; iz < array.copiesZ(); iz++) {
                    List<Box3i> copy = new ArrayList<>(base.size());
                    for (Box3i b : base) copy.add(b.offset(ix * stepX, iy * stepY, iz * stepZ));
                    result.add(copy);
                }
        return result;
    }
}
