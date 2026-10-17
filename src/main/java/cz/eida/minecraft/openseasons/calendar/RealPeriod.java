/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

import cz.eida.minecraft.openseasons.season.Season;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A range of the real date (e.g. Halloween October 25 – November 4) in which a world or region gets
 * a season and events regardless of the game date. The end is inclusive; a range may span the year end.
 *
 * @param season   season in the range; empty = the season does not change, only events
 * @param progress season progress in percent; empty = as the real season if it is the same, otherwise half
 * @param events   event ids from {@code events.yml} (built-in and custom)
 * @param worlds   whole worlds where the range applies; regions via the WorldGuard flag {@code real-periods}
 */
public record RealPeriod(String id, MonthDay from, MonthDay to, Optional<Season> season, OptionalDouble progress,
                         List<String> events, List<String> worlds) {

    public RealPeriod {
        events = List.copyOf(events);
        worlds = List.copyOf(worlds);
    }

    public boolean activeOn(LocalDate date) {
        MonthDay d = MonthDay.from(date);
        if (!from.isAfter(to)) return !d.isBefore(from) && !d.isAfter(to);
        return !d.isBefore(from) || !d.isAfter(to);
    }

    public boolean appliesToWorld(String world) {
        return worlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    /** season progress of the range on {@code date}: as given, otherwise real (same season), otherwise 50 % */
    public double progressOn(LocalDate date, RealCalendar real) {
        if (progress.isPresent()) return progress.getAsDouble();
        if (season.isPresent() && season.get() == real.seasonAt(date)) return real.progress(date);
        return 50.0;
    }

    /** {@code 25/10 – 04/11} */
    public String range() {
        return String.format("%02d/%02d – %02d/%02d", from.getDayOfMonth(), from.getMonthValue(), to.getDayOfMonth(),
                to.getMonthValue());
    }
}
