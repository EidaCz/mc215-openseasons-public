/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Shared rules of the phenomena: when they run, night, dice and autumn leaf colours. */
public final class EffectRules {

    /** autumn leaves: brown, yellow and red shades */
    static final List<Integer> AUTUMN_COLORS = List.of(
            0x8B4A1F, 0x6E3B1A, 0xA0612D,   // brown
            0xD9A520, 0xE8C547,             // yellow
            0xB0261E, 0x8E1B16);            // red

    /** leaves outside fall: fresh and darker greens */
    static final List<Integer> GREEN_COLORS = List.of(0x5E8C2E, 0x4C7A25, 0x76A33A, 0x3F6B22);

    private EffectRules() {
    }

    /**
     * How many leaves fall in a season that is not among the configured small-leaf seasons: rarely in summer,
     * very rarely in spring, not at all in winter (share of {@code spawn-chance}).
     */
    public static double offSeasonLeafFactor(Season season) {
        return switch (season) {
            case SUMMER, FALL -> 0.05;
            case SPRING -> 0.01;
            case WINTER -> 0.0;
        };
    }

    /** colour of a small leaf: autumn, or green outside fall */
    public static int leafColor(RandomGenerator random, boolean autumn) {
        List<Integer> palette = autumn ? AUTUMN_COLORS : GREEN_COLORS;
        return palette.get(random.nextInt(palette.size()));
    }

    /** the phenomenon is enabled and the world has enabled seasons in one of its seasons */
    public static boolean active(boolean enabled, Set<Season> seasons, Optional<WorldSeasonState> state) {
        return enabled && state.isPresent() && state.get().enabled() && seasons.contains(state.get().season());
    }

    /**
     * The phenomenon is enabled and the location has one of its seasons ({@code RegionService#effectSeasonAt}: season
     * of the world, or the permanent season of a region; empty without seasons or with {@code season-effects deny}).
     */
    public static boolean activeAt(boolean enabled, Set<Season> seasons, Optional<Season> season) {
        return enabled && season.isPresent() && seasons.contains(season.get());
    }

    /** night by the time of day in ticks (0 = 6:00): from 13,000 to 23,000 */
    public static boolean night(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= 13000 && t < 23000;
    }

    /** night number: changes in the evening at 13,000, not at midnight, so the whole night has the same number */
    public static long nightIndex(long fullTime) {
        return Math.floorDiv(fullTime - 13000, 24000L);
    }

    /** dice roll: true with a probability of {@code percent} % */
    public static boolean roll(RandomGenerator random, double percent) {
        return random.nextDouble() * 100.0 < percent;
    }

    public static int autumnColor(RandomGenerator random) {
        return AUTUMN_COLORS.get(random.nextInt(AUTUMN_COLORS.size()));
    }
}
