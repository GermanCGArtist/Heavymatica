package dev.heavymatica.core;

/**
 * Array repeat: copies per axis and the gap between copies (gaps may be negative to overlap copies,
 * but the resulting step, bounds size + gap, must stay at least 1 block; checked by {@link Transform}).
 */
public record ArrayConfig(int copiesX, int copiesY, int copiesZ, int gapX, int gapY, int gapZ) {
    public static final ArrayConfig NONE = new ArrayConfig(1, 1, 1, 0, 0, 0);

    public ArrayConfig {
        if (copiesX < 1 || copiesY < 1 || copiesZ < 1) {
            throw new IllegalArgumentException("copies must be >= 1: " + copiesX + "," + copiesY + "," + copiesZ);
        }
    }

    public int copyCount() {
        return copiesX * copiesY * copiesZ;
    }
}
