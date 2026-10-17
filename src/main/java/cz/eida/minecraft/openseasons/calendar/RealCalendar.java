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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Real-world seasons by the real date (not the game calendar): season starts as day and month,
 * astronomical by default (spring March 20, summer June 21, fall September 23, winter December 21).
 * A season lasts from its start to the day before the next start, also across the year end.
 */
public final class RealCalendar {

    public static final Map<Season, MonthDay> ASTRONOMICAL_STARTS = Map.of(
            Season.SPRING, MonthDay.of(3, 20),
            Season.SUMMER, MonthDay.of(6, 21),
            Season.FALL, MonthDay.of(9, 23),
            Season.WINTER, MonthDay.of(12, 21));

    public static final RealCalendar ASTRONOMICAL = new RealCalendar(ASTRONOMICAL_STARTS, ZoneId.systemDefault());

    /** start of a season in a particular year */
    private record Start(Season season, LocalDate date) {
    }

    private final Map<Season, MonthDay> starts;
    private final ZoneId zone;

    /** @param starts starts of all four seasons (different days) */
    public RealCalendar(Map<Season, MonthDay> starts, ZoneId zone) {
        if (starts.size() != Season.values().length) throw new IllegalArgumentException("missing start of a season");
        if (starts.values().stream().distinct().count() != starts.size()) {
            throw new IllegalArgumentException("two seasons start on the same day");
        }
        this.starts = new EnumMap<>(starts);
        this.zone = zone;
    }

    public Map<Season, MonthDay> starts() {
        return Map.copyOf(starts);
    }

    public ZoneId zone() {
        return zone;
    }

    /** today's real date in the time zone of the calendar */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public Season seasonAt(LocalDate date) {
        return around(date)[0].season();
    }

    /** season progress in percent (0 on the start day, approaching 100 the day before the next start) */
    public double progress(LocalDate date) {
        Start[] s = around(date);
        long length = ChronoUnit.DAYS.between(s[0].date(), s[1].date());
        long done = ChronoUnit.DAYS.between(s[0].date(), date);
        return length <= 0 ? 0 : 100.0 * done / length;
    }

    /** days until the start of the next season */
    public long daysUntilNext(LocalDate date) {
        return ChronoUnit.DAYS.between(date, around(date)[1].date());
    }

    /** {@code [season in effect, next start]} */
    private Start[] around(LocalDate date) {
        List<Start> all = new ArrayList<>();
        for (int year = date.getYear() - 1; year <= date.getYear() + 1; year++) {
            for (Map.Entry<Season, MonthDay> e : starts.entrySet()) all.add(new Start(e.getKey(), e.getValue().atYear(year)));
        }
        all.sort(Comparator.comparing(Start::date));
        Start current = null;
        for (Start s : all) {
            if (s.date().isAfter(date)) return new Start[]{current, s};
            current = s;
        }
        throw new IllegalStateException("no season found for " + date);
    }
}
