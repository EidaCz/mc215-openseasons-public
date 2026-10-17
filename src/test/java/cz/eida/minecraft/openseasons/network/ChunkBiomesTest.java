/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.network;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.HeightmapType;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import cz.eida.minecraft.openseasons.biome.FoliagePattern;
import cz.eida.minecraft.openseasons.biome.MorningFog;
import cz.eida.minecraft.openseasons.biome.SeasonTints;
import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkBiomesTest {

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    private static DataPalette singleton(int id) {
        return new DataPalette(new SingletonPalette(id), null, PaletteType.BIOME);
    }

    /** lower half of the section plains (0), upper the_void (1) */
    private static DataPalette halves() {
        ListPalette list = new ListPalette(1);
        BitStorage storage = new BitStorage(1, 64);
        for (int i = 0; i < 64; i++) storage.set(i, list.stateToId(i < 32 ? 0 : 1));
        return new DataPalette(list, storage, PaletteType.BIOME);
    }

    /** surface (first free block) at the height {@code surface} above min_y in all columns */
    private static long[] heightmap(int surface) {
        int bits = 9;
        int perLong = 64 / bits;
        long[] data = new long[(256 + perLong - 1) / perLong];
        for (int i = 0; i < 256; i++) data[i / perLong] |= (long) surface << ((i % perLong) * bits);
        return data;
    }

    private static Column column(int x, int z, DataPalette... biomes) {
        BaseChunk[] sections = new BaseChunk[biomes.length];
        for (int i = 0; i < biomes.length; i++) sections[i] = new Chunk_v1_18(ClientVersion.V_26_2, 0, DataPalette.createForChunk(), biomes[i]);
        return new Column(x, z, true, sections, new TileEntity[0],
                Map.of(HeightmapType.MOTION_BLOCKING_NO_LEAVES, heightmap(20)));
    }

    private static BiomeTable table() {
        return BiomeTable.build(BiomeTableTest.elements(), BiomeTableTest.config(),
                new SeasonTints(true, Map.of(), List.of()), "openseasons", BiomeTableTest.mistInLateFall());
    }

    private static DataPalette biomes(Column c, int section) {
        return ((Chunk_v1_18) c.getChunks()[section]).getBiomeData();
    }

    @Test
    void rewritesCellsWithFoliagePatchesAndKeepsUnknownBiomes() {
        BiomeTable t = table();
        int fall = BiomeTable.look(Season.FALL, 7);
        Column c = column(3, -7, singleton(0), halves());
        assertTrue(ChunkBiomes.rewrite(c, new ChunkBiomes.Context(t, fall, MorningFog.NONE, MIN_Y, HEIGHT)));
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int v = FoliagePattern.variant(3 * 4 + x, -7 * 4 + z, 5);
                    assertEquals(t.map(0, fall, v, false), biomes(c, 0).get(x, y, z));
                    assertEquals(y < 2 ? t.map(0, fall, v, false) : 1, biomes(c, 1).get(x, y, z));
                }
            }
        }
        // the group does not change summer: nothing to rewrite
        Column spring = column(0, 0, singleton(0));
        assertFalse(ChunkBiomes.rewrite(spring, new ChunkBiomes.Context(t,
                BiomeTable.look(Season.SUMMER, 5), MorningFog.NONE, MIN_Y, HEIGHT)));
        assertInstanceOf(SingletonPalette.class, biomes(spring, 0).palette);
    }

    @Test
    void mistOnlyNearSurfaceBelowLayerTop() {
        BiomeTable t = table();
        int fall = BiomeTable.look(Season.FALL, 7);
        Column c = column(0, 0, singleton(0), singleton(0));
        // surface y = -44, fog up to y = -40
        ChunkBiomes.rewrite(c, new ChunkBiomes.Context(t, fall, -40, MIN_Y, HEIGHT));
        for (int y = 0; y < 4; y++) {
            int v = FoliagePattern.variant(1, 2, 5);
            assertEquals(t.map(0, fall, v, false), biomes(c, 0).get(1, y, 2), "underground without fog, y " + y);
            boolean mist = y < 2; // cells y -48 and -44
            assertEquals(t.map(0, fall, v, mist), biomes(c, 1).get(1, y, 2), "y " + y);
        }
        int[] surface = ChunkBiomes.surface(c, HEIGHT, MIN_Y);
        assertEquals(-44, surface[0]);
        assertEquals(-44, surface[15]);
    }

    @Test
    void encodeChoosesPaletteLikeServer() {
        DataPalette p = singleton(0);
        int[] cells = new int[64];
        ChunkBiomes.encode(p, cells, 9);
        assertInstanceOf(SingletonPalette.class, p.palette);
        assertNull(p.storage);
        for (int i = 0; i < 64; i++) cells[i] = 100 + i % 5;
        ChunkBiomes.encode(p, cells, 9);
        assertInstanceOf(ListPalette.class, p.palette);
        assertEquals(3, p.storage.getBitsPerEntry());
        for (int i = 0; i < 64; i++) cells[i] = 300 + i % 12;
        ChunkBiomes.encode(p, cells, 9);
        assertEquals(GlobalPalette.INSTANCE, p.palette);
        assertEquals(9, p.storage.getBitsPerEntry());
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) assertEquals(cells[ChunkBiomes.index(x, y, z)], p.get(x, y, z));
            }
        }
        assertEquals(9, ChunkBiomes.ceilLog2(400));
        assertEquals(7, ChunkBiomes.ceilLog2(128));
    }
}
