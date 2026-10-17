/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCalendarTest {

    static final List<String> WEEK = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    /** the default calendar: season starts on 4/12, 4/3, 4/6 and 4/9 */
    static GameCalendar standard() {
        int[] days = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        String[] names = {"January", "February", "March", "April", "May", "June", "July", "August",
                "September", "October", "November", "December"};
        List<MonthDefinition> months = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) months.add(new MonthDefinition(names[i], days[i], 10, 10));
        return new GameCalendar(months, WEEK, Map.of(
                Season.WINTER, new MonthDay(12, 4),
                Season.SPRING, new MonthDay(3, 4),
                Season.SUMMER, new MonthDay(6, 4),
                Season.FALL, new MonthDay(9, 4)));
    }

    @Test
    void weekdaysAndSeasonStarts() {
        // 7/7/449 is a Monday, 27/10/129 a Wednesday
        GameCalendar cal = standard();
        CalendarDate world = new CalendarDate(7, 7, 449);
        assertEquals("Monday", cal.weekdayName(world));
        assertEquals("July", cal.monthName(world));
        assertEquals(59, cal.daysUntilNextSeasonStart(world));
        assertEquals(Season.FALL, cal.nextSeasonStart(world));
        assertEquals(92, cal.seasonLength(Season.SUMMER));

        CalendarDate doom = new CalendarDate(27, 10, 129);
        assertEquals("Wednesday", cal.weekdayName(doom));
        assertEquals(38, cal.daysUntilNextSeasonStart(doom));
        assertEquals(91, cal.seasonLength(Season.FALL));
    }

    @Test
    void plusDaysRollsOverMonthsAndYears() {
        GameCalendar cal = standard();
        assertEquals(new CalendarDate(1, 2, 1), cal.plusDays(new CalendarDate(31, 1, 1), 1));
        assertEquals(new CalendarDate(1, 1, 2), cal.plusDays(new CalendarDate(31, 12, 1), 1));
        assertEquals(new CalendarDate(5, 3, 11), cal.plusDays(new CalendarDate(5, 3, 10), 365));
        assertEquals(new CalendarDate(31, 12, 0), cal.plusDays(new CalendarDate(1, 1, 1), -1));
        CalendarDate d = new CalendarDate(17, 8, 449);
        assertEquals(d, cal.fromAbsoluteDay(cal.absoluteDay(d)));
    }

    @Test
    void dayNumberCountsFromFirstDayOfYearOne() {
        GameCalendar cal = standard();
        assertEquals(0, cal.dayNumber(new CalendarDate(1, 1, 1)));
        assertEquals(new CalendarDate(1, 1, 1), cal.dateOfDayNumber(0));
        assertEquals(new CalendarDate(1, 1, 2), cal.dateOfDayNumber(365));
        // world clocks (minecraft:time query time / 24 000)
        assertEquals(new CalendarDate(12, 7, 453), cal.dateOfDayNumber(165_172));
        assertEquals(new CalendarDate(3, 4, 133), cal.dateOfDayNumber(48_272));
        CalendarDate d = new CalendarDate(29, 10, 129);
        assertEquals(d, cal.dateOfDayNumber(cal.dayNumber(d)));
    }

    @Test
    void seasonByDateWrapsOverNewYear() {
        GameCalendar cal = standard();
        assertEquals(Season.SPRING, cal.seasonAt(new CalendarDate(3, 6, 449)));
        assertEquals(Season.SUMMER, cal.seasonAt(new CalendarDate(4, 6, 449)));
        assertEquals(Season.WINTER, cal.seasonAt(new CalendarDate(15, 1, 2)));
        assertEquals(Season.WINTER, cal.seasonAt(new CalendarDate(31, 12, 2)));
        assertEquals(Optional.of(Season.FALL), cal.seasonStartingOn(new CalendarDate(4, 9, 1)));
        assertTrue(cal.seasonStartingOn(new CalendarDate(5, 9, 1)).isEmpty());
        // on the day of a start the next start counts
        assertEquals(91, cal.daysUntilNextSeasonStart(new CalendarDate(4, 9, 1)));
    }

    @Test
    void seasonRangesFromCalendar() {
        GameCalendar cal = standard();
        assertEquals(new GameCalendar.SeasonRange(new MonthDay(12, 4), new MonthDay(3, 3)), cal.range(Season.WINTER));
        assertEquals(new GameCalendar.SeasonRange(new MonthDay(3, 4), new MonthDay(6, 3)), cal.range(Season.SPRING));
        assertEquals(new GameCalendar.SeasonRange(new MonthDay(6, 4), new MonthDay(9, 3)), cal.range(Season.SUMMER));
        assertEquals(new GameCalendar.SeasonRange(new MonthDay(9, 4), new MonthDay(12, 3)), cal.range(Season.FALL));
        // the last day of a range still belongs to the season, the next day does not
        assertEquals(Season.SPRING, cal.seasonAt(new CalendarDate(3, 6, 1)));
        assertEquals(Season.SUMMER, cal.seasonAt(new CalendarDate(4, 6, 1)));

        // a start on 1/1: the previous season ends on 31/12
        GameCalendar newYear = new GameCalendar(cal.months(), WEEK, Map.of(
                Season.WINTER, new MonthDay(1, 1), Season.SPRING, new MonthDay(4, 1),
                Season.SUMMER, new MonthDay(7, 1), Season.FALL, new MonthDay(10, 1)));
        assertEquals(new MonthDay(12, 31), newYear.range(Season.FALL).to());
        assertEquals(new MonthDay(3, 31), newYear.range(Season.WINTER).to());
    }

    @Test
    void nextChangeSkipsCurrentSeason() {
        GameCalendar cal = standard();
        // summer already on 3/6, the summer start is 4/6 → the next change is autumn
        assertEquals(new GameCalendar.SeasonChange(Season.FALL, 93),
                cal.nextSeasonChange(new CalendarDate(3, 6, 449), Season.SUMMER));
        assertEquals(new GameCalendar.SeasonChange(Season.FALL, 59),
                cal.nextSeasonChange(new CalendarDate(7, 7, 449), Season.SUMMER));
        // the season by the calendar and one set manually otherwise
        assertEquals(new GameCalendar.SeasonChange(Season.SUMMER, 1),
                cal.nextSeasonChange(new CalendarDate(3, 6, 449), Season.SPRING));
    }

    @Test
    void customCalendarWithOwnMonthsAndWeek() {
        GameCalendar cal = new GameCalendar(
                List.of(new MonthDefinition("Frostmonth", 20, 6, 14), new MonthDefinition("Budmonth", 25, 10, 10),
                        new MonthDefinition("Sunmonth", 25, 14, 6), new MonthDefinition("Leafmonth", 20, 10, 10),
                        new MonthDefinition("Duskmonth", 10, 8, 12)),
                List.of("Oneday", "Twoday", "Threeday", "Fourday", "Feastday"),
                Map.of(Season.WINTER, new MonthDay(5, 1), Season.SPRING, new MonthDay(2, 1),
                        Season.SUMMER, new MonthDay(3, 1), Season.FALL, new MonthDay(4, 1)));
        assertEquals(100, cal.daysInYear());
        assertEquals(new CalendarDate(1, 1, 2), cal.plusDays(new CalendarDate(10, 5, 1), 1));
        assertEquals("Duskmonth", cal.monthName(new CalendarDate(3, 5, 1)));
        assertEquals(Season.WINTER, cal.seasonAt(new CalendarDate(7, 1, 3)));
        assertEquals(30, cal.seasonLength(Season.WINTER));
        // the week has 5 days: the weekday repeats after 5 days and goes round across the end of a year too
        CalendarDate d = new CalendarDate(12, 2, 7);
        assertEquals(cal.weekdayName(d), cal.weekdayName(cal.plusDays(d, 5)));
        assertEquals((cal.weekdayIndex(d) + 1) % 5, cal.weekdayIndex(cal.plusDays(d, 1)));
        CalendarDate lastDay = new CalendarDate(10, 5, 7);
        assertEquals((cal.weekdayIndex(lastDay) + 1) % 5, cal.weekdayIndex(cal.plusDays(lastDay, 1)));
    }

    @Test
    void normalizeClampsDatesAfterCalendarShrinks() {
        GameCalendar cal = standard();
        assertEquals(new CalendarDate(28, 2, 3), cal.normalize(new CalendarDate(30, 2, 3)));
        assertEquals(new CalendarDate(31, 12, 3), cal.normalize(new CalendarDate(40, 15, 3)));
    }

    @Test
    void progressCountsFromSeasonStart() {
        GameCalendar cal = standard();
        assertEquals(0.0, cal.seasonProgress(new CalendarDate(4, 6, 1), Season.SUMMER, 0.0), 1e-9);
        assertEquals(50.0, cal.seasonProgress(new CalendarDate(20, 7, 1), Season.SUMMER, 0.0), 1e-9);
        assertEquals(100.0, cal.seasonProgress(new CalendarDate(1, 3, 1), Season.SUMMER, 0.5), 1e-9);
    }

    @Test
    void rejectsInvalidCalendars() {
        List<MonthDefinition> months = List.of(new MonthDefinition("A", 10, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new GameCalendar(months, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GameCalendar(months, WEEK, Map.of(
                Season.WINTER, new MonthDay(1, 1), Season.SPRING, new MonthDay(1, 1),
                Season.SUMMER, new MonthDay(1, 5), Season.FALL, new MonthDay(1, 8))));
        assertThrows(IllegalArgumentException.class, () -> new GameCalendar(months, WEEK, Map.of(
                Season.WINTER, new MonthDay(1, 1), Season.SPRING, new MonthDay(1, 3),
                Season.SUMMER, new MonthDay(1, 5), Season.FALL, new MonthDay(2, 1))));
        assertThrows(IllegalArgumentException.class, () -> new MonthDefinition("B", 0, 10, 10));
    }
}
