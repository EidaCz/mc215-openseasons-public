/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import cz.eida.minecraft.openseasons.season.Season;

/**
 * Values of the WorldGuard flag {@code permanent-season} (and their order in the WorldGuard
 * help).
 */
public enum RegionSeason {
    SUMMER, WINTER, FALL, SPRING;

    public Season season() {
        return Season.valueOf(name());
    }
}
