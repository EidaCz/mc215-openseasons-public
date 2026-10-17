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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalRestorerTest {

    @TempDir
    Path dir;

    /** world as a map of positions; chunks outside {@code loaded} are not loaded */
    static final class FakeBlocks implements BlockAccess {
        final Map<Long, String> blocks = new HashMap<>();
        final Set<Long> unloadedChunks = new HashSet<>();
        int writes;

        void put(int x, int y, int z, String data) {
            blocks.put(BlockKeys.block(x, y, z), data);
        }

        String at(int x, int y, int z) {
            return blocks.getOrDefault(BlockKeys.block(x, y, z), "minecraft:air");
        }

        @Override
        public Optional<String> material(int x, int y, int z) {
            if (unloadedChunks.contains(BlockKeys.chunkOfBlock(x, z))) return Optional.empty();
            return Optional.of(PlacementJournal.material(at(x, y, z)));
        }

        @Override
        public boolean set(int x, int y, int z, String blockData) {
            writes++;
            put(x, y, z, blockData);
            return true;
        }
    }

    @Test
    void restoresOnlyWhatIsStillOurs() {
        PlacementJournal j = new PlacementJournal(dir);
        FakeBlocks world = new FakeBlocks();
        // our poppy still stands
        j.record("w", 1, 64, 1, "flowers", "minecraft:poppy", "minecraft:air");
        world.put(1, 64, 1, "minecraft:poppy");
        // the bush ripened meanwhile: still ours
        j.record("w", 2, 64, 2, "berry-bushes", "minecraft:sweet_berry_bush[age=1]", "minecraft:air");
        world.put(2, 64, 2, "minecraft:sweet_berry_bush[age=3]");
        // leaf litter over grass: the grass returns
        j.record("w", 3, 64, 3, "leaf-litter", "minecraft:leaf_litter[segment_amount=2]", "minecraft:short_grass");
        world.put(3, 64, 3, "minecraft:leaf_litter[segment_amount=2]");
        // a player picked the flower and placed stone there: do not touch
        j.record("w", 4, 64, 4, "flowers", "minecraft:dandelion", "minecraft:air");
        world.put(4, 64, 4, "minecraft:stone");
        // a player plant never changes
        j.recordPlayer("w", 5, 64, 5, "minecraft:poppy");
        world.put(5, 64, 5, "minecraft:poppy");

        JournalRestorer.Result r = JournalRestorer.restore(j, "w", j.chunk("w", 0, 0), world, e -> true, 100);
        assertEquals(new JournalRestorer.Result(3, 1, 0, 0), r);
        assertEquals("minecraft:air", world.at(1, 64, 1));
        assertEquals("minecraft:air", world.at(2, 64, 2));
        assertEquals("minecraft:short_grass", world.at(3, 64, 3));
        assertEquals("minecraft:stone", world.at(4, 64, 4));
        assertEquals("minecraft:poppy", world.at(5, 64, 5));
        assertEquals(3, world.writes);
        assertEquals(1, j.size("w"), "only the player plant is left");
        assertTrue(j.get("w", 5, 64, 5).orElseThrow().byPlayer());
    }

    @Test
    void unloadedChunksWait() {
        PlacementJournal j = new PlacementJournal(dir);
        FakeBlocks world = new FakeBlocks();
        j.record("w", 100, 64, 100, "flowers", "minecraft:poppy", "minecraft:air");
        world.put(100, 64, 100, "minecraft:poppy");
        world.unloadedChunks.add(BlockKeys.chunkOfBlock(100, 100));
        JournalRestorer.Result r = JournalRestorer.restore(j, "w", j.placedWithin("w", 0, 0, -1), world, e -> true, 100);
        assertEquals(new JournalRestorer.Result(0, 0, 1, 0), r);
        assertEquals(1, j.size("w"));
        assertEquals(0, world.writes, "an unloaded chunk is neither read nor changed");
    }

    @Test
    void onlyDueEntriesAndBudget() {
        PlacementJournal j = new PlacementJournal(dir);
        FakeBlocks world = new FakeBlocks();
        for (int i = 0; i < 10; i++) {
            j.record("w", i, 64, 0, i < 6 ? "flowers" : "grass", i < 6 ? "minecraft:poppy" : "minecraft:short_grass",
                    "minecraft:air");
            world.put(i, 64, 0, i < 6 ? "minecraft:poppy" : "minecraft:short_grass");
        }
        JournalRestorer.Result r = JournalRestorer.restore(j, "w", j.chunk("w", 0, 0), world,
                e -> e.kind().equals("flowers"), 4);
        assertEquals(4, r.restored());
        assertEquals(6, j.size("w"));
        assertEquals(4, j.count("w", 0, 0, "grass"));
        for (int i = 6; i < 10; i++) assertEquals("minecraft:short_grass", world.at(i, 64, 0), "the grass was not due");
    }
}
