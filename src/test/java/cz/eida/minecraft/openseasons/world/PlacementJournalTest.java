/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementJournalTest {

    @TempDir
    Path dir;

    @Test
    void keysRoundTrip() {
        int[][] positions = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-17, 70, 33}};
        for (int[] p : positions) {
            long key = BlockKeys.block(p[0], p[1], p[2]);
            assertEquals(p[0], BlockKeys.blockX(key));
            assertEquals(p[1], BlockKeys.blockY(key));
            assertEquals(p[2], BlockKeys.blockZ(key));
        }
        long chunk = BlockKeys.chunk(-3, 7);
        assertEquals(-3, BlockKeys.chunkX(chunk));
        assertEquals(7, BlockKeys.chunkZ(chunk));
        assertEquals(BlockKeys.chunk(-1, 2), BlockKeys.chunkOfBlock(-1, 33));
    }

    @Test
    void roundTripThroughFiles() {
        PlacementJournal j = new PlacementJournal(dir);
        j.record("world", 1, 64, 2, "flowers", "minecraft:poppy", "minecraft:air");
        j.record("world", -40, 70, 600, "leaf-litter", "minecraft:leaf_litter[facing=north,segment_amount=2]",
                "minecraft:short_grass");
        j.recordPlayer("world", 5, 64, 5, "minecraft:dandelion");
        j.record("world with space", 0, 0, 0, "grass", "minecraft:short_grass", "minecraft:air");
        Map<Path, String> files = j.takeDirty();
        assertEquals(3, files.size(), "two regions in world and one in the other world: " + files.keySet());
        assertTrue(files.containsKey(dir.resolve("world_with_space").resolve("r.0.0.txt")), files.keySet().toString());
        assertTrue(PlacementJournal.write(files).isEmpty());
        assertFalse(j.dirty());

        PlacementJournal loaded = new PlacementJournal(dir);
        List<String> warnings = new ArrayList<>();
        loaded.load("world", warnings);
        loaded.load("world with space", warnings);
        assertEquals(List.of(), warnings);
        assertEquals(3, loaded.size("world"));
        assertEquals(1, loaded.size("world with space"));
        PlacementJournal.Entry litter = loaded.get("world", -40, 70, 600).orElseThrow();
        assertEquals("leaf-litter", litter.kind());
        assertEquals("minecraft:leaf_litter", litter.placedMaterial());
        assertEquals("minecraft:short_grass", litter.original());
        assertTrue(loaded.get("world", 5, 64, 5).orElseThrow().byPlayer());
        assertEquals("", loaded.get("world", 5, 64, 5).orElseThrow().original());
    }

    @Test
    void emptyRegionFileIsDeleted() throws Exception {
        PlacementJournal j = new PlacementJournal(dir);
        j.record("world", 1, 64, 2, "flowers", "minecraft:poppy", "minecraft:air");
        PlacementJournal.write(j.takeDirty());
        Path file = dir.resolve("world").resolve("r.0.0.txt");
        assertTrue(Files.exists(file));
        j.remove("world", 1, 64, 2);
        PlacementJournal.write(j.takeDirty());
        assertFalse(Files.exists(file));
    }

    @Test
    void secondPlacementKeepsFirstOriginal() {
        PlacementJournal j = new PlacementJournal(dir);
        j.record("world", 3, 65, 3, "grass", "minecraft:short_grass", "minecraft:air");
        PlacementJournal.Entry e = j.record("world", 3, 65, 3, "leaf-litter", "minecraft:leaf_litter", "minecraft:short_grass");
        assertEquals("leaf-litter", e.kind());
        assertEquals("minecraft:air", e.original(), "restore returns the state before the first change by OpenSeasons");
        assertEquals(1, j.size("world"));
    }

    @Test
    void countsAndRadius() {
        PlacementJournal j = new PlacementJournal(dir);
        for (int i = 0; i < 5; i++) j.record("world", i, 64, 0, "flowers", "minecraft:poppy", "minecraft:air");
        j.record("world", 7, 64, 7, "grass", "minecraft:short_grass", "minecraft:air");
        j.record("world", 500, 64, 500, "flowers", "minecraft:poppy", "minecraft:air");
        j.recordPlayer("world", 8, 64, 8, "minecraft:poppy");
        assertEquals(5, j.count("world", 0, 0, "flowers"));
        assertEquals(1, j.count("world", 0, 0, "grass"));
        assertEquals(0, j.count("world", 1, 1, "flowers"));
        assertEquals(6, j.placedWithin("world", 0, 0, 20).size(), "a player plant does not count");
        assertEquals(7, j.placedWithin("world", 0, 0, -1).size());
        assertEquals(Map.of("flowers", 6, "grass", 1), PlacementJournal.countByKind(j.placedWithin("world", 0, 0, -1)));
    }

    @Test
    void brokenLinesAreSkipped() {
        List<String> warnings = new ArrayList<>();
        List<PlacementJournal.Entry> entries = PlacementJournal.parse(PlacementJournal.HEADER + "\n"
                + "1\t64\t2\tflowers\tminecraft:poppy\tminecraft:air\n"
                + "x\t64\t2\tflowers\tminecraft:poppy\tminecraft:air\n"
                + "1\t64\n", "test", warnings);
        assertEquals(1, entries.size());
        assertEquals(2, warnings.size());
    }

    @Test
    void playerPlacementReplacesOurEntry() {
        PlacementJournal j = new PlacementJournal(dir);
        j.record("world", 1, 64, 1, "flowers", "minecraft:poppy", "minecraft:air");
        j.recordPlayer("world", 1, 64, 1, "minecraft:dandelion");
        assertTrue(j.get("world", 1, 64, 1).orElseThrow().byPlayer());
        assertTrue(j.placedWithin("world", 0, 0, -1).isEmpty());
    }
}
