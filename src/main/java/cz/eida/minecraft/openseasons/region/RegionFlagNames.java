/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import java.util.List;

/**
 * Names of the WorldGuard flags used by OpenSeasons:
 * season flags and the flags of the real-world calendar.
 */
public final class RegionFlagNames {

    /** enum {@code SUMMER, WINTER, FALL, SPRING} */
    public static final String PERMANENT_SEASON = "permanent-season";
    /** state, default allow */
    public static final String SEASON_EFFECTS = "season-effects";
    /** state, default allow */
    public static final String SEASON_BLOCK_CHANGES = "season-block-changes";
    /** state, default allow */
    public static final String SEASON_MOB_SPAWNING = "season-mob-spawning";

    public static final List<String> ALL = List.of(PERMANENT_SEASON, SEASON_EFFECTS, SEASON_BLOCK_CHANGES, SEASON_MOB_SPAWNING);

    /** enum {@code GAME, REAL}: season source of the region */
    public static final String SEASON_SOURCE = "season-source";
    /** list of period ids from {@code real-calendar.yml} that apply in the region */
    public static final String REAL_PERIODS = "real-periods";

    private RegionFlagNames() {
    }
}
