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

import java.util.OptionalInt;

/**
 * When an event runs by the game calendar. A dated event lasts from the start day
 * to the end day inclusive (New Year {@code 1/1} – {@code 1/1} is one day); a day the month
 * does not have is moved to its last day. A weekly event runs the whole given weekday, a daily one only runs
 * commands on a day change.
 */
public sealed interface EventSchedule {

    /** whether the event runs on the given day */
    boolean activeOn(CalendarDate date, GameCalendar calendar);

    /**
     * In how many days (1 to {@code horizon}) the event begins: the first day it runs and the day before not.
     * A running event counts only from its next start.
     */
    default OptionalInt daysUntilStart(CalendarDate date, GameCalendar calendar, int horizon) {
        boolean before = activeOn(date, calendar);
        for (int d = 1; d <= horizon; d++) {
            boolean now = activeOn(calendar.plusDays(date, d), calendar);
            if (now && !before) return OptionalInt.of(d);
            before = now;
        }
        return OptionalInt.empty();
    }

    /**
     * From a date to a date inclusive. The repetition is given by the start: only a day = every month, day and month = every
     * year (also across the year end, e.g. 20/12 – 5/1), with a year once. An end without a year of a one-time
     * event is in the same year, or in the next one when it would be before the start.
     */
    record Dated(EventDate start, EventDate stop) implements EventSchedule {

        @Override
        public boolean activeOn(CalendarDate date, GameCalendar calendar) {
            CalendarDate d = calendar.normalize(date);
            return switch (start.repeat()) {
                case MONTHLY -> {
                    int length = calendar.month(d.month()).days();
                    yield within(d.day(), Math.min(start.day(), length), Math.min(stop.day(), length));
                }
                case YEARLY -> {
                    int from = dayOfYear(calendar, start.month(), start.day());
                    int to = dayOfYear(calendar, stop.month() == 0 ? start.month() : stop.month(), stop.day());
                    yield from > 0 && to > 0 && within(calendar.dayOfYear(d), from, to);
                }
                case ONCE -> {
                    if (start.month() > calendar.months().size()) yield false;
                    long from = calendar.absoluteDay(new CalendarDate(start.day(), start.month(), start.year()));
                    int stopMonth = stop.month() == 0 ? start.month() : stop.month();
                    if (stopMonth > calendar.months().size()) yield false;
                    long to;
                    if (stop.year() != 0) {
                        to = calendar.absoluteDay(new CalendarDate(stop.day(), stopMonth, stop.year()));
                    } else {
                        to = calendar.absoluteDay(new CalendarDate(stop.day(), stopMonth, start.year()));
                        if (to < from) to += calendar.daysInYear();
                    }
                    long now = calendar.absoluteDay(d);
                    yield now >= from && now <= to;
                }
            };
        }

        /** day of the year; 0 when the month is not in the calendar */
        private static int dayOfYear(GameCalendar calendar, int month, int day) {
            if (month < 1 || month > calendar.months().size()) return 0;
            return calendar.dayOfYear(new CalendarDate(Math.min(day, calendar.month(month).days()), month, 1));
        }

        /** from–to inclusive, wrapping around the end of the cycle */
        private static boolean within(int value, int from, int to) {
            return from <= to ? value >= from && value <= to : value >= from || value <= to;
        }
    }

    /** the whole weekday (index in {@code week-days}, from 0) */
    record Weekly(int weekday) implements EventSchedule {

        @Override
        public boolean activeOn(CalendarDate date, GameCalendar calendar) {
            return calendar.weekdayIndex(date) == weekday;
        }
    }

    /** only commands on every day change; never "runs" */
    record Daily() implements EventSchedule {

        @Override
        public boolean activeOn(CalendarDate date, GameCalendar calendar) {
            return false;
        }

        @Override
        public OptionalInt daysUntilStart(CalendarDate date, GameCalendar calendar, int horizon) {
            return OptionalInt.empty();
        }
    }
}
