/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.season;

/**
 * Phase within a season by the elapsed part of the season (thresholds 9 / 18 / 84 / 92 %).
 * The phase index ({@link #ordinal()}) is used in the configuration, e.g. {@code sub-seasons: 0, 1, 2}.
 */
public enum SubSeason {
    START, EARLY, MIDDLE, LATE, END;

    private static final SubSeason[] VALUES = values();

    /** @param percent elapsed part of the season in percent (0–100) */
    public static SubSeason fromProgress(double percent) {
        if (percent < 9) return START;
        if (percent < 18) return EARLY;
        if (percent < 84) return MIDDLE;
        if (percent < 92) return LATE;
        return END;
    }

    public static SubSeason ofIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            throw new IllegalArgumentException("season phase must be 0–" + (VALUES.length - 1) + ", not " + index);
        }
        return VALUES[index];
    }
}
