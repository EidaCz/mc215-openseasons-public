/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.calendar.MonthDay;
import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import cz.eida.minecraft.openseasons.calendar.TimeFormat;
import cz.eida.minecraft.openseasons.season.Season;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Calendar settings ({@code calendar.yml}). The calendar is fully configurable: the number and names
 * of months, their lengths and the days of the week come from the file.
 */
public record CalendarSettings(
        boolean enabled,
        boolean americanDateFormat,
        boolean realLifeDays,
        ZoneId timezone,
        TimeFormat timeFormat,
        List<String> worldsWithoutEvents,
        GameCalendar calendar) {

    static final List<String> DEFAULT_WEEKDAYS =
            List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    /** name, days, minutes of day, minutes of night */
    static final List<MonthDefinition> DEFAULT_MONTHS = List.of(
            new MonthDefinition("January", 31, 7, 13),
            new MonthDefinition("February", 28, 8, 12),
            new MonthDefinition("March", 31, 9, 11),
            new MonthDefinition("April", 30, 10, 10),
            new MonthDefinition("May", 31, 11, 9),
            new MonthDefinition("June", 30, 12, 8),
            new MonthDefinition("July", 31, 13, 7),
            new MonthDefinition("August", 31, 12, 8),
            new MonthDefinition("September", 30, 11, 9),
            new MonthDefinition("October", 31, 10, 10),
            new MonthDefinition("November", 30, 9, 11),
            new MonthDefinition("December", 31, 8, 12));

    private static final Map<Season, MonthDay> DEFAULT_STARTS = Map.of(
            Season.WINTER, new MonthDay(12, 4),
            Season.SPRING, new MonthDay(3, 4),
            Season.SUMMER, new MonthDay(6, 4),
            Season.FALL, new MonthDay(9, 4));

    /** the same settings with another calendar (display names from {@code names.yml}) */
    public CalendarSettings withCalendar(GameCalendar other) {
        return new CalendarSettings(enabled, americanDateFormat, realLifeDays, timezone, timeFormat,
                worldsWithoutEvents, other);
    }

    /** date according to {@code american-date-format}: day/month/year, or month/day/year */
    public String formatDate(CalendarDate date) {
        return americanDateFormat
                ? date.month() + "/" + date.day() + "/" + date.year()
                : date.day() + "/" + date.month() + "/" + date.year();
    }

    static CalendarSettings parse(ConfigReader r) throws ConfigException {
        List<MonthDefinition> months = new ArrayList<>();
        try {
            if (r.has("months")) {
                for (String name : r.keys("months")) {
                    String p = "months." + name + ".";
                    months.add(new MonthDefinition(name,
                            r.integer(p + "days", 30),
                            r.integer(p + "day-length-in-min", 10),
                            r.integer(p + "night-length-in-min", 10)));
                }
            } else {
                months.addAll(DEFAULT_MONTHS);
            }
        } catch (IllegalArgumentException e) {
            throw new ConfigException(r.source() + ": months: " + e.getMessage(), e);
        }
        if (months.isEmpty()) throw new ConfigException(r.source() + ": months: the calendar has no month");

        List<String> weekdays = r.list("week-days", DEFAULT_WEEKDAYS);
        if (weekdays.isEmpty()) throw new ConfigException(r.source() + ": week-days: the week has no day");

        Map<Season, MonthDay> starts = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            String path = s.key() + "-start";
            starts.put(s, r.has(path)
                    ? parseStart(r, path, r.string(path, ""), months)
                    : DEFAULT_STARTS.get(s));
        }

        GameCalendar calendar;
        try {
            calendar = new GameCalendar(months, weekdays, starts);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(r.source() + ": " + e.getMessage(), e);
        }

        return new CalendarSettings(
                r.bool("enabled", true),
                r.bool("american-date-format", false),
                r.bool("calendar-is-real-life-days", false),
                parseZone(r),
                new TimeFormat(
                        r.string("time.format", TimeFormat.DEFAULT.pattern()),
                        r.bool("time.12-hour-clock.enabled", false),
                        r.bool("time.12-hour-clock.add-am-pm", false)),
                r.list("worlds-without-events", List.of()),
                calendar);
    }

    /** {@code December 4} or {@code 12 4}: month (name or number) and day */
    private static MonthDay parseStart(ConfigReader r, String path, String text, List<MonthDefinition> months)
            throws ConfigException {
        String t = text.trim();
        int split = t.lastIndexOf(' ');
        if (split <= 0) throw new ConfigException(r.source() + ": " + path + ": expected '<month> <day>', not '" + text + "'");
        String monthPart = t.substring(0, split).trim();
        int day;
        try {
            day = Integer.parseInt(t.substring(split + 1).trim().replace(".", ""));
        } catch (NumberFormatException e) {
            throw new ConfigException(r.source() + ": " + path + ": the day in '" + text + "' is not a number", e);
        }
        OptionalInt month = monthIndex(monthPart, months);
        if (month.isEmpty()) {
            throw new ConfigException(r.source() + ": " + path + ": month '" + monthPart + "' is not in the calendar");
        }
        try {
            return new MonthDay(month.getAsInt(), day);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(r.source() + ": " + path + ": " + e.getMessage(), e);
        }
    }

    private static OptionalInt monthIndex(String text, List<MonthDefinition> months) {
        for (int i = 0; i < months.size(); i++) {
            if (months.get(i).name().equalsIgnoreCase(text)) return OptionalInt.of(i + 1);
        }
        try {
            int n = Integer.parseInt(text);
            if (n >= 1 && n <= months.size()) return OptionalInt.of(n);
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return OptionalInt.empty();
    }

    private static ZoneId parseZone(ConfigReader r) {
        String zone = r.string("timezone", "system").trim();
        if (zone.equalsIgnoreCase("system")) return ZoneId.systemDefault();
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            r.warn("timezone", "unknown time zone '" + zone + "', using the system one");
            return ZoneId.systemDefault();
        }
    }
}
