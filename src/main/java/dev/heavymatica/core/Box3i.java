package dev.heavymatica.core;

/** An inclusive block box, always normalized (min <= max on every axis). */
public record Box3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box3i {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Box3i not normalized: " + minX + "," + minY + "," + minZ
                    + " > " + maxX + "," + maxY + "," + maxZ + " (use Box3i.of for any corner order)");
        }
    }

    /** A box from two corners in any order. */
    public static Box3i of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box3i(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** From Manhattan's box format {x1,y1,z1,x2,y2,z2}. */
    public static Box3i of(int[] b) {
        return of(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public Box3i offset(int dx, int dy, int dz) {
        return new Box3i(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Box3i o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    /** The smallest box holding both. */
    public Box3i union(Box3i o) {
        return new Box3i(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }

    /** Manhattan's box format: {minX,minY,minZ,maxX,maxY,maxZ}. */
    public int[] toArray() {
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }
}
