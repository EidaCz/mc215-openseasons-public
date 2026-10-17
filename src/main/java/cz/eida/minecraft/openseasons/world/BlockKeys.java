/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

/**
 * Packed keys of block and chunk positions in a {@code long} (journal maps without an object per position).
 * Position: x and z with 26 bits each, y with 12 bits (−2048 to 2047), the same as vanilla.
 */
public final class BlockKeys {

    private BlockKeys() {
    }

    public static long block(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static int blockX(long key) {
        return (int) (key >> 38);
    }

    public static int blockY(long key) {
        return (int) (key << 52 >> 52);
    }

    public static int blockZ(long key) {
        return (int) (key << 26 >> 38);
    }

    public static long chunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }

    /** chunk the block lies in */
    public static long chunkOfBlock(int x, int z) {
        return chunk(x >> 4, z >> 4);
    }
}
