/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkQueueTest {

    @Test
    void drainsGraduallyWithoutDuplicates() {
        ChunkQueue q = new ChunkQueue(100);
        for (int i = 0; i < 5; i++) q.add("w", i, 0);
        assertFalse(q.add("w", 2, 0), "no duplicates");
        assertEquals(List.of(new ChunkQueue.Ref("w", 0, 0), new ChunkQueue.Ref("w", 1, 0)), q.poll(2, r -> true));
        assertEquals(3, q.size());
        assertEquals(3, q.poll(10, r -> true).size());
        assertEquals(0, q.size());
    }

    @Test
    void unloadedChunksAreDroppedNotLoaded() {
        ChunkQueue q = new ChunkQueue(100);
        q.add("w", 0, 0);
        q.add("w", 1, 0);
        q.add("w", 2, 0);
        List<ChunkQueue.Ref> out = q.poll(2, r -> r.chunkX() != 1);
        assertEquals(List.of(new ChunkQueue.Ref("w", 0, 0), new ChunkQueue.Ref("w", 2, 0)), out);
        assertEquals(0, q.size(), "an unloaded one is dropped, the load event adds it");
    }

    @Test
    void capacity() {
        ChunkQueue q = new ChunkQueue(2);
        assertTrue(q.add("w", 0, 0));
        assertTrue(q.add("w", 1, 0));
        assertFalse(q.add("w", 2, 0));
    }

    @Test
    void tpsGate() {
        assertTrue(ChunkQueue.tpsAllows(true, 19.5, 20.0));
        assertTrue(ChunkQueue.tpsAllows(true, 19.5, 19.5));
        assertFalse(ChunkQueue.tpsAllows(true, 19.5, 19.4), "below the threshold the world does not change");
        assertTrue(ChunkQueue.tpsAllows(false, 19.5, 10.0), "always without prioritise-tps");
    }
}
