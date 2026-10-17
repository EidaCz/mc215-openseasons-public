/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of region settings by chunk column, per world name. It is filled and recomputed only
 * on the main thread; it can be read ({@link #peek}) from anywhere. For a chunk missing in the cache, another
 * thread only registers ({@link #drainPending} computes it). A change of the regions is recognised by the fingerprint
 * ({@link #revalidate}).
 */
final class RegionCache {

    interface Query {
        RegionSettings query(String world, int chunkX, int chunkZ);
    }

    interface Fingerprint {
        long of(String world);
    }

    private final Query query;
    private final Fingerprint fingerprint;
    private final Map<String, Map<Long, RegionSettings>> worlds = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> pending = new ConcurrentHashMap<>();
    /** fingerprint of the regions the values of the world were computed with (main thread) */
    private final Map<String, Long> fingerprints = new HashMap<>();

    RegionCache(Query query, Fingerprint fingerprint) {
        this.query = query;
        this.fingerprint = fingerprint;
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    static int chunkX(long key) {
        return (int) (key >> 32);
    }

    static int chunkZ(long key) {
        return (int) key;
    }

    /** main thread: from the cache, otherwise query and store */
    RegionSettings get(String world, int chunkX, int chunkZ) {
        return chunks(world).computeIfAbsent(key(chunkX, chunkZ), k -> query.query(world, chunkX, chunkZ));
    }

    /** any thread: value from the cache, or {@code null} and the chunk is computed on the main thread */
    RegionSettings peek(String world, int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        Map<Long, RegionSettings> chunks = worlds.get(world);
        RegionSettings cached = chunks == null ? null : chunks.get(key);
        if (cached == null) pending.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet()).add(key);
        return cached;
    }

    /**
     * Main thread: computes the chunks other threads asked about.
     *
     * @return chunks with other than default settings (they got the default before and need to be refreshed)
     */
    Map<String, List<Long>> drainPending() {
        Map<String, List<Long>> notable = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, Set<Long>>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Set<Long>> e = it.next();
            it.remove();
            String world = e.getKey();
            for (long key : e.getValue()) {
                Map<Long, RegionSettings> chunks = chunks(world);
                if (chunks.containsKey(key)) continue;
                RegionSettings s = query.query(world, chunkX(key), chunkZ(key));
                chunks.put(key, s);
                if (!s.isNone()) notable.computeIfAbsent(world, w -> new ArrayList<>()).add(key);
            }
        }
        return notable;
    }

    /**
     * Main thread: when the fingerprint of the regions of a world changed, recomputes its stored chunks.
     *
     * @return chunks whose settings changed
     */
    List<Long> revalidate(String world) {
        Map<Long, RegionSettings> chunks = worlds.get(world);
        if (chunks == null) return List.of();
        long now = fingerprint.of(world);
        Long known = fingerprints.put(world, now);
        if (known != null && known == now) return List.of();
        List<Long> changed = new ArrayList<>();
        for (Map.Entry<Long, RegionSettings> e : chunks.entrySet()) {
            long key = e.getKey();
            RegionSettings s = query.query(world, chunkX(key), chunkZ(key));
            if (!s.equals(e.getValue())) {
                e.setValue(s);
                changed.add(key);
            }
        }
        return changed;
    }

    /** worlds with stored values */
    Set<String> worlds() {
        return Set.copyOf(worlds.keySet());
    }

    void evict(String world, int chunkX, int chunkZ) {
        Map<Long, RegionSettings> chunks = worlds.get(world);
        if (chunks != null) chunks.remove(key(chunkX, chunkZ));
    }

    void evictWorld(String world) {
        worlds.remove(world);
        pending.remove(world);
        fingerprints.remove(world);
    }

    void clear() {
        worlds.clear();
        pending.clear();
        fingerprints.clear();
    }

    int size() {
        return worlds.values().stream().mapToInt(Map::size).sum();
    }

    /** map of a world; a new world remembers the fingerprint its values are computed with right away */
    private Map<Long, RegionSettings> chunks(String world) {
        Map<Long, RegionSettings> chunks = worlds.get(world);
        if (chunks != null) return chunks;
        fingerprints.put(world, fingerprint.of(world));
        return worlds.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
    }
}
