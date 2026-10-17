/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.network;

import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.HeightmapType;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import cz.eida.minecraft.openseasons.biome.FoliagePattern;
import cz.eida.minecraft.openseasons.biome.MorningFog;

import java.util.Arrays;
import java.util.Map;

/**
 * Rewriting biomes in a chunk packet: every cell of 4 × 4 × 4 blocks gets a biome variant by the look
 * of the world, leaf patches and fog. The section palette is then built like the server does: a single value, up to eight
 * values a list (1–3 bits), otherwise the global palette with a width by the size of the client registry.
 */
final class ChunkBiomes {

    private static final int CELLS = 64;

    private ChunkBiomes() {
    }

    /**
     * What to rewrite and how.
     *
     * @param mistTop top of the fog (absolute y), or {@link MorningFog#NONE}
     */
    record Context(BiomeTable table, int look, int mistTop, int minY, int worldHeight) {
    }

    /** @return true when something changed (the packet has to be encoded again) */
    static boolean rewrite(Column column, Context ctx) {
        BiomeTable t = ctx.table();
        int globalBits = ceilLog2(t.total());
        int[] surface = ctx.mistTop() == MorningFog.NONE ? null : surface(column, ctx.worldHeight(), ctx.minY());
        int[] cells = new int[CELLS];
        boolean changed = false;
        BaseChunk[] sections = column.getChunks();
        for (int s = 0; s < sections.length; s++) {
            if (!(sections[s] instanceof Chunk_v1_18 section)) continue;
            DataPalette biomes = section.getBiomeData();
            if (biomes == null) continue;
            int sectionY = ctx.minY() + s * 16;
            boolean any = false;
            for (int y = 0; y < 4; y++) {
                int cellY = sectionY + y * 4;
                for (int z = 0; z < 4; z++) {
                    for (int x = 0; x < 4; x++) {
                        int source = biomes.get(x, y, z);
                        int variant = t.varies(source, ctx.look())
                                ? FoliagePattern.variant(column.getX() * 4 + x, column.getZ() * 4 + z, BiomeTable.VARIANTS)
                                : 0;
                        boolean mist = surface != null && cellY < ctx.mistTop() && cellY + 4 > surface[z * 4 + x] - 2;
                        int id = t.map(source, ctx.look(), variant, mist);
                        cells[index(x, y, z)] = id;
                        if (id != source) any = true;
                    }
                }
            }
            if (any) {
                encode(biomes, cells, globalBits);
                changed = true;
            }
        }
        return changed;
    }

    /** the lowest surface (first free block above the ground without leaves, water counts, absolute y) in each cell
     * of 4 × 4 columns; index z × 4 + x */
    static int[] surface(Column column, int worldHeight, int minY) {
        int[] out = new int[16];
        Arrays.fill(out, Integer.MIN_VALUE);
        Map<HeightmapType, long[]> maps = column.getHeightmaps();
        long[] data = maps == null ? null : maps.get(HeightmapType.MOTION_BLOCKING_NO_LEAVES);
        if (data == null && maps != null) data = maps.get(HeightmapType.MOTION_BLOCKING);
        if (data == null) return out;
        int bits = ceilLog2(worldHeight + 1);
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                int i = bz * 16 + bx;
                if (i / perLong >= data.length) return out;
                int h = (int) ((data[i / perLong] >>> ((i % perLong) * bits)) & mask) + minY;
                int cell = (bz >> 2) * 4 + (bx >> 2);
                out[cell] = out[cell] == Integer.MIN_VALUE ? h : Math.min(out[cell], h);
            }
        }
        return out;
    }

    static int index(int x, int y, int z) {
        return (y << 2 | z) << 2 | x;
    }

    /** new section palette from 64 values */
    static void encode(DataPalette palette, int[] cells, int globalBits) {
        int[] distinct = new int[9];
        int count = 0;
        for (int c : cells) {
            boolean seen = false;
            for (int i = 0; i < count; i++) {
                if (distinct[i] == c) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                if (count == 9) break;
                distinct[count++] = c;
            }
        }
        if (count == 1) {
            palette.palette = new SingletonPalette(distinct[0]);
            palette.storage = null;
            return;
        }
        if (count <= 8) {
            int bits = count <= 2 ? 1 : count <= 4 ? 2 : 3;
            ListPalette list = new ListPalette(bits);
            BitStorage storage = new BitStorage(bits, CELLS);
            for (int i = 0; i < CELLS; i++) storage.set(i, list.stateToId(cells[i]));
            palette.palette = list;
            palette.storage = storage;
            return;
        }
        BitStorage storage = new BitStorage(globalBits, CELLS);
        for (int i = 0; i < CELLS; i++) storage.set(i, cells[i]);
        palette.palette = GlobalPalette.INSTANCE;
        palette.storage = storage;
    }

    static int ceilLog2(int n) {
        return n <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1);
    }
}
