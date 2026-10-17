/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Queue of chunks to clean up (no duplicates, in insertion order). A chunk that is not loaded at the moment
 * is skipped and dropped: after loading, the load event adds it again.
 */
public final class ChunkQueue {

    public record Ref(String world, int chunkX, int chunkZ) {
    }

    private final Set<Ref> queue = new LinkedHashSet<>();
    private final int capacity;

    /** @param capacity most waiting chunks; further ones are not added (they come up at the next check) */
    public ChunkQueue(int capacity) {
        this.capacity = capacity;
    }

    public boolean add(String world, int chunkX, int chunkZ) {
        if (queue.size() >= capacity) return false;
        return queue.add(new Ref(world, chunkX, chunkZ));
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    /** removes at most {@code max} loaded chunks; drops unloaded ones on the way */
    public List<Ref> poll(int max, Predicate<Ref> loaded) {
        List<Ref> out = new ArrayList<>();
        Iterator<Ref> it = queue.iterator();
        while (it.hasNext() && out.size() < max) {
            Ref r = it.next();
            it.remove();
            if (loaded.test(r)) out.add(r);
        }
        return out;
    }

    /**
     * Performance gate ({@code performance.prioritise-tps}, {@code min-tps}): with a lower TPS the world
     * does not change at all.
     */
    public static boolean tpsAllows(boolean prioritiseTps, double minTps, double tps) {
        return !prioritiseTps || tps >= minTps;
    }
}
