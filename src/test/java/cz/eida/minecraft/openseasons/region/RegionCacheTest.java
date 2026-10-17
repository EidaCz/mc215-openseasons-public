/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCacheTest {

    private static final RegionSettings WINTER = new RegionSettings(Optional.of(Season.WINTER), true, true, true);
    private static final RegionSettings NO_EFFECTS = new RegionSettings(Optional.empty(), false, true, true);

    /** "regions": chunk → settings, otherwise NONE */
    private final Map<Long, RegionSettings> regions = new HashMap<>();
    private long fingerprint = 1;
    private int queries;
    private final RegionCache cache = new RegionCache((world, x, z) -> {
        queries++;
        return regions.getOrDefault(RegionCache.key(x, z), RegionSettings.NONE);
    }, world -> fingerprint);

    @Test
    void keyRoundTripsNegativeChunks() {
        long key = RegionCache.key(-694, 12345);
        assertEquals(-694, RegionCache.chunkX(key));
        assertEquals(12345, RegionCache.chunkZ(key));
        assertEquals(-1, RegionCache.chunkZ(RegionCache.key(7, -1)));
    }

    @Test
    void getQueriesOnceAndCaches() {
        regions.put(RegionCache.key(1, 2), WINTER);
        assertSame(WINTER, cache.get("world", 1, 2));
        assertSame(WINTER, cache.get("world", 1, 2));
        assertSame(RegionSettings.NONE, cache.get("world", 0, 0));
        assertEquals(2, queries);
        assertEquals(2, cache.size());
    }

    @Test
    void peekMissIsFilledLaterAndOnlyNonDefaultChunksAreReported() {
        regions.put(RegionCache.key(5, 5), WINTER);
        assertNull(cache.peek("world", 5, 5));
        assertNull(cache.peek("world", 6, 6));
        assertNull(cache.peek("world", 6, 6));
        assertEquals(0, queries);

        Map<String, List<Long>> notable = cache.drainPending();
        assertEquals(Map.of("world", List.of(RegionCache.key(5, 5))), notable);
        assertEquals(2, queries);
        assertSame(WINTER, cache.peek("world", 5, 5));
        assertSame(RegionSettings.NONE, cache.peek("world", 6, 6));
        assertTrue(cache.drainPending().isEmpty());
    }

    @Test
    void revalidateRecomputesOnlyWhenRegionsChanged() {
        cache.get("world", 0, 0);
        cache.get("world", 1, 0);
        cache.get("world", 2, 0);
        queries = 0;

        assertEquals(List.of(), cache.revalidate("world"));
        assertEquals(0, queries);

        // /rg flag … season-effects deny in chunk 1,0
        regions.put(RegionCache.key(1, 0), NO_EFFECTS);
        fingerprint = 2;
        assertEquals(List.of(RegionCache.key(1, 0)), cache.revalidate("world"));
        assertEquals(3, queries);
        assertSame(NO_EFFECTS, cache.get("world", 1, 0));

        queries = 0;
        assertEquals(List.of(), cache.revalidate("world"));
        assertEquals(0, queries);
    }

    @Test
    void changeBetweenFillAndFirstCheckIsNotLost() {
        cache.get("world", 0, 0);
        regions.put(RegionCache.key(0, 0), WINTER);
        fingerprint = 7;
        assertEquals(List.of(RegionCache.key(0, 0)), cache.revalidate("world"));
    }

    @Test
    void evictionForgetsChunksAndWorlds() {
        cache.get("world", 0, 0);
        cache.get("doom", 0, 0);
        cache.evict("world", 0, 0);
        assertNull(cache.peek("world", 0, 0));
        cache.evictWorld("doom");
        assertEquals(java.util.Set.of("world"), cache.worlds());
        assertEquals(List.of(), cache.revalidate("doom"));
    }
}
