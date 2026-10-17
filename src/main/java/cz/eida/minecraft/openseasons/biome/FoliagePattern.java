/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.biome;

/**
 * Placement of five leaf colours across the landscape. On the client, biomes come in cells of 4 × 4 blocks; each
 * area of 32 × 32 blocks has a dominant colour and about a quarter of the 8 × 8 block patches get another one. The pattern
 * depends only on coordinates, so it is the same for all players, after a restart and across chunk borders.
 * The client blends the colours between cells itself (the "biome blend" setting).
 */
public final class FoliagePattern {

    private FoliagePattern() {
    }

    /**
     * @param qx coordinate of the biome cell (block x / 4)
     * @param qz coordinate of the biome cell (block z / 4)
     * @return variant 0 to {@code variants - 1}
     */
    public static int variant(int qx, int qz, int variants) {
        long patch = hash(Math.floorDiv(qx, 2), Math.floorDiv(qz, 2), 0x5DEECE66DL);
        if ((patch & 3) == 0) return (int) Math.floorMod(patch >>> 2, (long) variants);
        return (int) Math.floorMod(hash(Math.floorDiv(qx, 8), Math.floorDiv(qz, 8), 0x2545F4914F6CDD1DL), (long) variants);
    }

    private static long hash(int x, int z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 32);
    }
}
