/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.region.RegionService;
import org.bukkit.Location;

/**
 * Where events may act: the only place for the rules by region (WorldGuard flags through
 * {@link RegionService}). Manual triggering ({@code /openseasons debug}) ignores them. Main thread only.
 */
public interface EventPlaces {

    /** everywhere (without WorldGuard) */
    EventPlaces EVERYWHERE = new EventPlaces() {
        @Override
        public boolean effectsAllowed(Location at) {
            return true;
        }

        @Override
        public boolean mobChangesAllowed(Location at) {
            return true;
        }
    };

    /**
     * Event phenomena and decorations: sparks, flames, decorated spruces, presents, eggs, firework boxes
     * (flag {@code season-effects}).
     */
    boolean effectsAllowed(Location at);

    /** changes of natural spawns and added monsters and bats (flag {@code season-mob-spawning}) */
    boolean mobChangesAllowed(Location at);

    /**
     * Is an event from a real-date period of a region running at the place (flag {@code real-periods})? Periods of the whole
     * world are handled by the scheduler; nowhere without regions.
     */
    default boolean regionEvent(Location at, String eventId) {
        return false;
    }

    /** rules by region; everywhere without the region service */
    static EventPlaces of(RegionService regions) {
        if (regions == null) return EVERYWHERE;
        return new EventPlaces() {
            @Override
            public boolean effectsAllowed(Location at) {
                return regions.effectsAllowed(at);
            }

            @Override
            public boolean mobChangesAllowed(Location at) {
                return regions.mobSpawningAllowed(at);
            }

            @Override
            public boolean regionEvent(Location at, String eventId) {
                return regions.regionEventAt(at, eventId);
            }
        };
    }
}
