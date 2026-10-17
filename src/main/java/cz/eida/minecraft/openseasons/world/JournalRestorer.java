/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Restores blocks from the journal to their original state. A block is restored only while the material
 * OpenSeasons placed is still there (a bush that has grown is still its bush). Anything else (a player picked the plant,
 * water washed it away) is left alone and the entry is dropped. Player plants are never changed.
 */
public final class JournalRestorer {

    /**
     * @param restored restored to the original state
     * @param dropped  entry dropped, something else is there now (OpenSeasons does not change the block)
     * @param unloaded the chunk is not loaded, the entry stays
     * @param failed   the original state cannot be placed (unknown data), entry dropped
     */
    public record Result(int restored, int dropped, int unloaded, int failed) {

        public static final Result NONE = new Result(0, 0, 0, 0);

        public Result plus(Result o) {
            return new Result(restored + o.restored, dropped + o.dropped, unloaded + o.unloaded, failed + o.failed);
        }

        public int processed() {
            return restored + dropped + failed;
        }
    }

    private JournalRestorer() {
    }

    /**
     * Restores the entries that satisfy {@code due}, at most {@code budget} processed blocks.
     *
     * @param budget most processed entries (restored, dropped); unloaded ones do not count
     */
    public static Result restore(PlacementJournal journal, String world, Collection<PlacementJournal.Entry> entries,
                                 BlockAccess blocks, Predicate<PlacementJournal.Entry> due, int budget) {
        int restored = 0;
        int dropped = 0;
        int unloaded = 0;
        int failed = 0;
        for (PlacementJournal.Entry e : entries) {
            if (restored + dropped + failed >= budget) break;
            if (e.byPlayer() || !due.test(e)) continue;
            Optional<String> current = blocks.material(e.x(), e.y(), e.z());
            if (current.isEmpty()) {
                unloaded++;
                continue;
            }
            // the entry may have vanished or changed in the meantime (player event during the walk)
            Optional<PlacementJournal.Entry> live = journal.get(world, e.x(), e.y(), e.z());
            if (live.isEmpty() || live.get().byPlayer()) continue;
            journal.remove(world, e.x(), e.y(), e.z());
            if (!current.get().equals(e.placedMaterial())) {
                dropped++;
            } else if (blocks.set(e.x(), e.y(), e.z(), e.original())) {
                restored++;
            } else {
                failed++;
            }
        }
        return new Result(restored, dropped, unloaded, failed);
    }
}
