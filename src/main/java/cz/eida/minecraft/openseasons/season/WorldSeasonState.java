/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.season;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;

import java.util.OptionalLong;

/**
 * State of one world. With automatic cycling the season follows the ranges in the calendar: it is aligned
 * at start and on every day change. A manually set season lasts until the next day change.
 *
 * <p>The date is computed from the world clock: the day number since January 1 of year 1 is the game day of the clock plus
 * {@code dayOffset}. Offset 0 means that the year and date exactly match the age of the world; another offset
 * comes from setting the date or setting the clock back.
 *
 * @param enabled   a disabled world keeps its season and date and only silently follows the clock, nothing happens in it
 * @param dayOffset empty: the date applies as it is and the offset is computed from the clock when the world
 *                  is first loaded
 */
public record WorldSeasonState(boolean enabled, Season season, CalendarDate date, OptionalLong dayOffset) {

    public WorldSeasonState {
        if (season == null) throw new IllegalArgumentException("a world must have a season");
        if (date == null) throw new IllegalArgumentException("a world must have a date");
        if (dayOffset == null) dayOffset = OptionalLong.empty();
    }

    /** state with the given date; the offset is computed from the clock */
    public WorldSeasonState(boolean enabled, Season season, CalendarDate date) {
        this(enabled, season, date, OptionalLong.empty());
    }

    public WorldSeasonState withSeason(Season s) {
        return new WorldSeasonState(enabled, s, date, dayOffset);
    }

    public WorldSeasonState withDate(CalendarDate d) {
        return new WorldSeasonState(enabled, season, d, dayOffset);
    }

    public WorldSeasonState withEnabled(boolean e) {
        return new WorldSeasonState(e, season, date, dayOffset);
    }

    public WorldSeasonState withDayOffset(OptionalLong offset) {
        return new WorldSeasonState(enabled, season, date, offset);
    }
}
