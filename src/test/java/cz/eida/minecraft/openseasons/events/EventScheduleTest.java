/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.calendar.GameCalendarTestAccess;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventScheduleTest {

    private final GameCalendar cal = GameCalendarTestAccess.standard();

    private static EventSchedule.Dated dated(String from, String to) {
        return new EventSchedule.Dated(EventDate.parse(from, false), EventDate.parse(to, false));
    }

    private boolean on(EventSchedule s, int day, int month, int year) {
        return s.activeOn(new CalendarDate(day, month, year), cal);
    }

    @Test
    void parsesThreeForms() {
        assertEquals(new EventDate(25, 12, 0), EventDate.parse("25/12", false));
        assertEquals(EventDate.Repeat.YEARLY, EventDate.parse("25/12", false).repeat());
        assertEquals(new EventDate(1, 1, 450), EventDate.parse("1/1/450", false));
        assertEquals(EventDate.Repeat.ONCE, EventDate.parse("1/1/450", false).repeat());
        assertEquals(new EventDate(5, 0, 0), EventDate.parse(" 5 ", false));
        assertEquals(EventDate.Repeat.MONTHLY, EventDate.parse("5", false).repeat());
        // american-date-format: month before day
        assertEquals(new EventDate(25, 12, 0), EventDate.parse("12/25", true));
        assertEquals(new EventDate(4, 7, 451), EventDate.parse("7/4/451", true));
        assertEquals("12/25", new EventDate(25, 12, 0).format(true));
        assertEquals("25/12/450", new EventDate(25, 12, 450).format(false));
        assertThrows(IllegalArgumentException.class, () -> EventDate.parse("25-12", false));
        assertThrows(IllegalArgumentException.class, () -> EventDate.parse("0/12", false));
        assertThrows(IllegalArgumentException.class, () -> EventDate.parse("1/2/3/4", false));
        assertThrows(IllegalArgumentException.class, () -> EventDate.parse("", false));
    }

    @Test
    void yearlyEventsFromServerIncludeStopDay() {
        EventSchedule christmas = dated("25/12", "28/12");
        assertFalse(on(christmas, 24, 12, 449));
        assertTrue(on(christmas, 25, 12, 449));
        assertTrue(on(christmas, 28, 12, 1));
        assertFalse(on(christmas, 29, 12, 449));
        EventSchedule newYear = dated("1/1", "1/1");
        assertTrue(on(newYear, 1, 1, 450));
        assertFalse(on(newYear, 2, 1, 450));
        assertFalse(on(newYear, 31, 12, 449));
        EventSchedule halloween = dated("31/10", "2/11");
        assertTrue(on(halloween, 31, 10, 129));
        assertTrue(on(halloween, 1, 11, 129));
        assertTrue(on(halloween, 2, 11, 129));
        assertFalse(on(halloween, 3, 11, 129));
        assertFalse(on(halloween, 30, 10, 129));
    }

    @Test
    void yearlyRangeWrapsOverNewYear() {
        EventSchedule winterBreak = dated("20/12", "5/1");
        assertTrue(on(winterBreak, 20, 12, 10));
        assertTrue(on(winterBreak, 31, 12, 10));
        assertTrue(on(winterBreak, 1, 1, 11));
        assertTrue(on(winterBreak, 5, 1, 11));
        assertFalse(on(winterBreak, 6, 1, 11));
        assertFalse(on(winterBreak, 19, 12, 10));
    }

    @Test
    void dayOnlyRepeatsEveryMonthAndClampsToMonthLength() {
        EventSchedule payday = dated("5", "7");
        assertTrue(on(payday, 5, 2, 3));
        assertTrue(on(payday, 7, 11, 3));
        assertFalse(on(payday, 8, 11, 3));
        assertFalse(on(payday, 4, 1, 3));
        // day 31: the last day in February (28 days)
        EventSchedule last = dated("31", "31");
        assertTrue(on(last, 28, 2, 5));
        assertFalse(on(last, 27, 2, 5));
        assertTrue(on(last, 31, 1, 5));
        assertTrue(on(last, 30, 4, 5));
        // across the end of a month
        EventSchedule turn = dated("30", "2");
        assertTrue(on(turn, 30, 1, 5));
        assertTrue(on(turn, 1, 2, 5));
        assertTrue(on(turn, 28, 2, 5));
        assertFalse(on(turn, 3, 2, 5));
    }

    @Test
    void onceWithYearHappensOnlyThatYear() {
        EventSchedule once = new EventSchedule.Dated(EventDate.parse("30/12/450", false), EventDate.parse("2/1/451", false));
        assertTrue(on(once, 30, 12, 450));
        assertTrue(on(once, 1, 1, 451));
        assertTrue(on(once, 2, 1, 451));
        assertFalse(on(once, 3, 1, 451));
        assertFalse(on(once, 30, 12, 451));
        // an end without a year: the same year, or the next one
        EventSchedule open = new EventSchedule.Dated(EventDate.parse("30/12/450", false), EventDate.parse("2/1", false));
        assertTrue(on(open, 1, 1, 451));
        assertFalse(on(open, 1, 1, 450));
    }

    @Test
    void weeklyWholeDayAndDailyNeverActive() {
        EventSchedule sunday = new EventSchedule.Weekly(6);
        // 7/7/449 is a Monday, 13/7 a Sunday
        assertFalse(on(sunday, 7, 7, 449));
        assertTrue(on(sunday, 13, 7, 449));
        assertFalse(on(sunday, 14, 7, 449));
        assertEquals(OptionalInt.of(6), sunday.daysUntilStart(new CalendarDate(7, 7, 449), cal, 365));
        // a running weekly event: the next start in a week
        assertEquals(OptionalInt.of(7), sunday.daysUntilStart(new CalendarDate(13, 7, 449), cal, 365));
        EventSchedule daily = new EventSchedule.Daily();
        assertFalse(on(daily, 1, 1, 1));
        assertTrue(daily.daysUntilStart(new CalendarDate(1, 1, 1), cal, 365).isEmpty());
    }

    @Test
    void daysUntilStartMatchesProductionPlaceholders() {
        // 22/7/449 → Halloween in 101 days
        assertEquals(OptionalInt.of(101), dated("31/10", "2/11").daysUntilStart(new CalendarDate(22, 7, 449), cal, 365));
        // doom 12/11/129 → Christmas in 43 days
        assertEquals(OptionalInt.of(43), dated("25/12", "28/12").daysUntilStart(new CalendarDate(12, 11, 129), cal, 365));
        // during Christmas the next start counts from next year
        assertEquals(OptionalInt.of(363), dated("25/12", "28/12").daysUntilStart(new CalendarDate(27, 12, 129), cal, 365));
        // a one-off event in the past does not start any more
        assertTrue(new EventSchedule.Dated(EventDate.parse("1/1/100", false), EventDate.parse("2/1/100", false))
                .daysUntilStart(new CalendarDate(1, 6, 449), cal, 365).isEmpty());
    }
}
