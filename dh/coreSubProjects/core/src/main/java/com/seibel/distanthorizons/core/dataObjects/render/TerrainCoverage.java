package com.seibel.distanthorizons.core.dataObjects.render;

import java.util.Arrays;

/** Immutable provenance for the 64 by 64 columns used to build one terrain buffer.
 * A set bit means complete source terrain, including known air; it does not mean
 * that an object is visible. Depth testing remains necessary.
 */
public final class TerrainCoverage {
    public static final int WIDTH = 64;
    public static final TerrainCoverage UNKNOWN = new TerrainCoverage(new long[WIDTH]);
    private final long[] rows;

    private TerrainCoverage(long[] rows) { this.rows = rows; }

    /** Copies worker-owned bits before publishing them with an uploaded buffer. */
    public static TerrainCoverage copyOf(long[] rows) {
        if (rows.length != WIDTH) throw new IllegalArgumentException("Expected 64 coverage rows");
        return new TerrainCoverage(Arrays.copyOf(rows, WIDTH));
    }

    public boolean isKnown(int x, int z) {
        return x >= 0 && z >= 0 && x < WIDTH && z < WIDTH && (rows[x] & (1L << z)) != 0;
    }

    /** Inclusive bounds; out-of-section or inverted queries fail closed. */
    public boolean isKnown(int minX, int minZ, int maxX, int maxZ) {
        if (minX < 0 || minZ < 0 || maxX >= WIDTH || maxZ >= WIDTH || maxX < minX || maxZ < minZ) return false;
        long mask = (-1L >>> (63 - maxZ)) & (-1L << minZ);
        for (int x = minX; x <= maxX; x++) if ((rows[x] & mask) != mask) return false;
        return true;
    }
}
