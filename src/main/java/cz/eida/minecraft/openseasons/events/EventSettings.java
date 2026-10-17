/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loaded events: definitions in file order (built-in, then custom dated, weekly, daily)
 * and the settings of the mechanics of built-in events.
 *
 * @param sources  where each file came from ({@code events}, {@code custom-events})
 * @param warnings non-fatal problems (unknown keys, invalid data, unknown items)
 */
public record EventSettings(List<SeasonEvent> events, Christmas christmas, NewYear newYear, Halloween halloween,
                            Easter easter, Map<String, Path> sources, List<String> warnings) {

    public static final EventSettings NONE = new EventSettings(List.of(), Christmas.DEFAULT, NewYear.DEFAULT,
            Halloween.DEFAULT, Easter.DEFAULT, Map.of(), List.of());

    public EventSettings {
        events = List.copyOf(events);
        sources = Map.copyOf(sources);
        warnings = List.copyOf(warnings);
    }

    public Optional<SeasonEvent> event(String id) {
        return events.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** chance of a phenomenon 0–1 (in the file {@code 0.07} or {@code 7%}) */
    public record Chance(boolean enabled, double chance) {
        public static final Chance OFF = new Chance(false, 0);
    }

    /**
     * @param presentSpacing smallest distance of the places where presents appeared, in chunks
     */
    public record Christmas(boolean nightParticles, boolean trees, boolean presents, int presentSpacing,
                            LootTable presentLoot) {
        public static final Christmas DEFAULT = new Christmas(true, true, true, 10, LootTable.EMPTY);
    }

    /** @param minDistance smallest distance of firework boxes in blocks */
    public record NewYear(boolean fireworkBoxes, int minDistance) {
        public static final NewYear DEFAULT = new NewYear(true, 34);
    }

    /**
     * Halloween mechanics.
     *
     * @param witchEffects effect names ({@code BLINDNESS}, also old ones like {@code CONFUSION})
     * @param pumpkinHeads carved pumpkin or jack o'lantern on the head of monsters with an empty head
     * @param moreBats     bats around players at night (chance per check once every 10 s)
     * @param moreSpiders  an extra spider next to a naturally spawned monster
     */
    public record Halloween(boolean mobParticles, boolean witchPotions, List<String> witchEffects,
                            Chance duplicating, Chance invisible, Chance fast, Chance flameSkeletons,
                            Chance armored, Chance zombiePie, Chance vindicatorsAtNight,
                            Chance pumpkinHeads, Chance moreBats, Chance moreSpiders) {
        public static final List<String> DEFAULT_WITCH_EFFECTS =
                List.of("BLINDNESS", "NAUSEA", "WITHER", "MINING_FATIGUE", "SLOW_FALLING", "LEVITATION");
        public static final Halloween DEFAULT = new Halloween(true, true, DEFAULT_WITCH_EFFECTS,
                new Chance(true, 0.07), new Chance(true, 0.07), new Chance(true, 0.07), new Chance(true, 0.3),
                new Chance(true, 0.25), new Chance(true, 0.3), new Chance(true, 0.05),
                new Chance(true, 0.35), new Chance(true, 0.5), new Chance(true, 0.15));

        public Halloween {
            witchEffects = List.copyOf(witchEffects);
        }
    }

    /**
     * @param killerBunnyRate chance that a killer bunny appears instead of a night zombie, skeleton, creeper or spider
     * @param eggSpacing      smallest distance of the places where eggs appeared, in chunks
     */
    public record Easter(boolean killerBunnies, double killerBunnyRate, boolean eggs, int eggSpacing, LootTable eggLoot) {
        public static final Easter DEFAULT = new Easter(true, 0.1, true, 4, LootTable.EMPTY);
    }
}
