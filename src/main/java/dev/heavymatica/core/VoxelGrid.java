package dev.heavymatica.core;

/** Read-only voxel view, so the decomposer does not depend on schemarray classes. */
public interface VoxelGrid {
    int sizeX();
    int sizeY();
    int sizeZ();
    boolean filled(int x, int y, int z);
}
