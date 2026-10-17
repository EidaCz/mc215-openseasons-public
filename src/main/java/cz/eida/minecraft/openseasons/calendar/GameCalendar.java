/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

import cz.eida.minecraft.openseasons.season.Season;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

/**
 * Custom calendar: any number of months with their own names and lengths, any week length
 * and four season starts. Nothing is fixed to 12 months or 7 days.
 *
 * <p>The weekday is computed from the day number {@code year × year length + day of year}
 * modulo the week length; index 0 is the first day of the {@code week-days} list.
 */
public final class GameCalendar {

    private final List<MonthDefinition> months;
    private final List<String> weekdays;
    private final EnumMap<Season, MonthDay> starts;
    /** number of days before the start of a month (index from 0) */
    private final int[] monthOffsets;
    private final int daysInYear;
    /** season starts sorted by day of the year */
    private final List<SeasonStart> startsInYear;

    private record SeasonStart(Season season, int dayOfYear) {
    }

    public GameCalendar(List<MonthDefinition> months, List<String> weekdays, Map<Season, MonthDay> starts) {
        if (months == null || months.isEmpty()) throw new IllegalArgumentException("the calendar must have at least one month");
        if (weekdays == null || weekdays.isEmpty()) throw new IllegalArgumentException("the week must have at least one day");
        this.months = List.copyOf(months);
        this.weekdays = List.copyOf(weekdays);

        this.monthOffsets = new int[this.months.size()];
        int total = 0;
        for (int i = 0; i < this.months.size(); i++) {
            monthOffsets[i] = total;
            total += this.months.get(i).days();
        }
        this.daysInYear = total;

        this.starts = new EnumMap<>(Season.class);
        List<SeasonStart> sorted = new ArrayList<>();
        for (Season season : Season.values()) {
            MonthDay md = starts == null ? null : starts.get(season);
            if (md == null) throw new IllegalArgumentException("missing start of season " + season.key());
            if (md.month() > this.months.size()) {
                throw new IllegalArgumentException("start of season " + season.key() + ": month " + md.month()
                        + " does not exist (the calendar has " + this.months.size() + ")");
            }
            MonthDefinition month = this.months.get(md.month() - 1);
            if (md.day() > month.days()) {
                throw new IllegalArgumentException("start of season " + season.key() + ": " + month.name()
                        + " has only " + month.days() + " days");
            }
            int doy = monthOffsets[md.month() - 1] + md.day();
            for (SeasonStart other : sorted) {
                if (other.dayOfYear() == doy) {
                    throw new IllegalArgumentException("seasons " + other.season().key() + " and " + season.key()
                        + " start on the same day");
                }
            }
            this.starts.put(season, md);
            sorted.add(new SeasonStart(season, doy));
        }
        sorted.sort(Comparator.comparingInt(SeasonStart::dayOfYear));
        this.startsInYear = Collections.unmodifiableList(sorted);
    }

    /** the same calendar with other month and day names (display names from {@code names.yml}) */
    public GameCalendar withNames(UnaryOperator<String> monthName, UnaryOperator<String> weekdayName) {
        List<MonthDefinition> renamed = months.stream()
                .map(m -> new MonthDefinition(monthName.apply(m.name()), m.days(), m.dayLengthMinutes(),
                        m.nightLengthMinutes()))
                .toList();
        return new GameCalendar(renamed, weekdays.stream().map(weekdayName).toList(), starts);
    }

    public List<MonthDefinition> months() {
        return months;
    }

    public List<String> weekdays() {
        return weekdays;
    }

    public int daysInYear() {
        return daysInYear;
    }

    public MonthDay start(Season season) {
        return starts.get(season);
    }

    /** month by its position from 1 */
    public MonthDefinition month(int month) {
        return months.get(month - 1);
    }

    /** month position (from 1) by name, case-insensitive */
    public OptionalInt monthIndex(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < months.size(); i++) {
            if (months.get(i).name().toLowerCase(Locale.ROOT).equals(wanted)) return OptionalInt.of(i + 1);
        }
        return OptionalInt.empty();
    }

    /** moves a date into the calendar bounds (after shortening a month or removing months in the configuration) */
    public CalendarDate normalize(CalendarDate date) {
        int month = Math.min(date.month(), months.size());
        int day = Math.min(date.day(), month(month).days());
        return month == date.month() && day == date.day() ? date : new CalendarDate(day, month, date.year());
    }

    /** day of the year from 1 */
    public int dayOfYear(CalendarDate date) {
        CalendarDate d = normalize(date);
        return monthOffsets[d.month() - 1] + d.day();
    }

    /** day number: year × year length + day of year */
    public long absoluteDay(CalendarDate date) {
        return (long) date.year() * daysInYear + dayOfYear(date);
    }

    public CalendarDate fromAbsoluteDay(long absoluteDay) {
        long year = Math.floorDiv(absoluteDay - 1, daysInYear);
        int doy = (int) (absoluteDay - year * daysInYear);
        int month = 0;
        while (month + 1 < months.size() && monthOffsets[month + 1] < doy) month++;
        return new CalendarDate(doy - monthOffsets[month], month + 1, Math.toIntExact(year));
    }

    public CalendarDate plusDays(CalendarDate date, long days) {
        return fromAbsoluteDay(absoluteDay(date) + days);
    }

    /**
     * Days elapsed since 1 January of year 1 (that day is 0). The count does not depend on the calendar, so
     * after changing the months the same day becomes another date.
     */
    public long dayNumber(CalendarDate date) {
        return absoluteDay(date) - daysInYear - 1;
    }

    /** inverse of {@link #dayNumber}: the date of the day with the given number since 1 January of year 1 */
    public CalendarDate dateOfDayNumber(long dayNumber) {
        return fromAbsoluteDay(dayNumber + daysInYear + 1);
    }

    /** first day of the given year in the season (default date of a new world) */
    public CalendarDate dateOf(MonthDay monthDay, int year) {
        return new CalendarDate(monthDay.day(), monthDay.month(), year);
    }

    public int weekdayIndex(CalendarDate date) {
        return (int) Math.floorMod(absoluteDay(date), (long) weekdays.size());
    }

    public String weekdayName(CalendarDate date) {
        return weekdays.get(weekdayIndex(date));
    }

    public String monthName(CalendarDate date) {
        return month(normalize(date).month()).name();
    }

    /** season by date: the last start that happened in the year (wrapping around the year end) */
    public Season seasonAt(CalendarDate date) {
        int doy = dayOfYear(date);
        SeasonStart current = startsInYear.getLast();
        for (SeasonStart s : startsInYear) {
            if (s.dayOfYear() <= doy) current = s;
        }
        return current.season();
    }

    /** season starting on the given day */
    public Optional<Season> seasonStartingOn(CalendarDate date) {
        int doy = dayOfYear(date);
        for (SeasonStart s : startsInYear) {
            if (s.dayOfYear() == doy) return Optional.of(s.season());
        }
        return Optional.empty();
    }

    /** the nearest season start after the given day (today's start does not count) */
    public Season nextSeasonStart(CalendarDate date) {
        return nearestStartAfter(dayOfYear(date)).season();
    }

    /** days until the nearest season start, 1 to the year length */
    public int daysUntilNextSeasonStart(CalendarDate date) {
        return distanceAfter(dayOfYear(date), nearestStartAfter(dayOfYear(date)).dayOfYear());
    }

    /** the nearest season change: when and to what */
    public record SeasonChange(Season season, int daysUntil) {
    }

    /**
     * The nearest start of a season other than {@code current}. A world whose season was set earlier than
     * by the calendar (e.g. summer on June 3 with the start on June 4) therefore waits for fall, not for "the next summer".
     */
    public SeasonChange nextSeasonChange(CalendarDate date, Season current) {
        int doy = dayOfYear(date);
        SeasonChange best = null;
        for (SeasonStart s : startsInYear) {
            if (s.season() == current) continue;
            int d = distanceAfter(doy, s.dayOfYear());
            if (best == null || d < best.daysUntil()) best = new SeasonChange(s.season(), d);
        }
        return best;
    }

    /** range of a season in the year: from its start to the day before the next start (wrapping around the year end) */
    public record SeasonRange(MonthDay from, MonthDay to) {
    }

    public SeasonRange range(Season season) {
        int startDoy = startDayOfYear(season);
        int lastDoy = Math.floorMod(nearestStartAfter(startDoy).dayOfYear() - 2, daysInYear) + 1;
        return new SeasonRange(starts.get(season), monthDayOf(lastDoy));
    }

    /** month and day for a day of the year from 1 */
    private MonthDay monthDayOf(int doy) {
        int month = 0;
        while (month + 1 < months.size() && monthOffsets[month + 1] < doy) month++;
        return new MonthDay(month + 1, doy - monthOffsets[month]);
    }

    /** length of a season in days: from its start to the next start */
    public int seasonLength(Season season) {
        int startDoy = startDayOfYear(season);
        return distanceAfter(startDoy, nearestStartAfter(startDoy).dayOfYear());
    }

    /** days elapsed since the last start of the given season (0 on the start day) */
    public int daysIntoSeason(CalendarDate date, Season season) {
        return Math.floorMod(dayOfYear(date) - startDayOfYear(season), daysInYear);
    }

    /**
     * Elapsed part of the season in percent, 0–100. A season set manually outside its date counts
     * from its calendar start and does not go over 100.
     *
     * @param fractionOfDay elapsed part of the day 0–1
     */
    public double seasonProgress(CalendarDate date, Season season, double fractionOfDay) {
        double elapsed = daysIntoSeason(date, season) + Math.clamp(fractionOfDay, 0.0, 1.0);
        return Math.min(100.0, elapsed / seasonLength(season) * 100.0);
    }

    private int startDayOfYear(Season season) {
        MonthDay md = starts.get(season);
        return monthOffsets[md.month() - 1] + md.day();
    }

    private SeasonStart nearestStartAfter(int doy) {
        SeasonStart best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SeasonStart s : startsInYear) {
            int d = distanceAfter(doy, s.dayOfYear());
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }

    /** distance from day {@code from} to the nearest later day {@code to}, 1 to the year length */
    private int distanceAfter(int from, int to) {
        return Math.floorMod(to - from - 1, daysInYear) + 1;
    }
}
