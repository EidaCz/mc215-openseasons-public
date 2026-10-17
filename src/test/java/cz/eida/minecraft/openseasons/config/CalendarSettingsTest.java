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
import cz.eida.minecraft.openseasons.season.Season;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarSettingsTest {

    private final List<String> warnings = new ArrayList<>();

    private CalendarSettings parse(String yaml) throws ConfigException {
        ConfigReader r = new ConfigReader(YamlFiles.parse(yaml, "calendar.yml"), "calendar.yml", warnings);
        CalendarSettings s = CalendarSettings.parse(r);
        r.reportUnknown(Set.of(), null);
        return s;
    }

    @Test
    void bundledCalendar() throws ConfigException {
        YamlConfiguration bundled = YamlFiles.loadResource("calendar.yml");
        ConfigReader r = new ConfigReader(bundled, "calendar.yml", warnings);
        r.integer("config-version", 1);
        CalendarSettings s = CalendarSettings.parse(r);
        r.reportUnknown(Set.of(), null);
        assertTrue(warnings.isEmpty(), warnings::toString);
        GameCalendar cal = s.calendar();
        assertEquals(365, cal.daysInYear());
        assertEquals(CalendarSettings.DEFAULT_MONTHS, cal.months());
        assertEquals(CalendarSettings.DEFAULT_WEEKDAYS, cal.weekdays());
        assertEquals(new MonthDay(12, 4), cal.start(Season.WINTER));
        assertEquals("September", cal.monthName(new CalendarDate(1, 9, 1)));
    }

    @Test
    void fullCalendarFile() throws ConfigException {
        // every key of calendar.yml
        CalendarSettings s = parse("""
                enabled: true
                american-date-format: false
                calendar-is-real-life-days: false
                timezone: 'system'
                time:
                  format: '$hours$:$minutes$'
                  12-hour-clock:
                    enabled: false
                    add-am-pm: false
                week-days: [ Mon, Tue, Wed, Thu, Fri, Sat, Sun ]
                worlds-without-events: [ none ]
                winter-start: December 4
                spring-start: March 4
                summer-start: June 4
                fall-start: September 4
                months:
                  January: { days: 31, day-length-in-min: 7, night-length-in-min: 13 }
                  February: { days: 28, day-length-in-min: 8, night-length-in-min: 12 }
                  March: { days: 31, day-length-in-min: 9, night-length-in-min: 11 }
                  April: { days: 30, day-length-in-min: 10, night-length-in-min: 10 }
                  May: { days: 31, day-length-in-min: 11, night-length-in-min: 9 }
                  June: { days: 30, day-length-in-min: 12, night-length-in-min: 8 }
                  July: { days: 31, day-length-in-min: 13, night-length-in-min: 7 }
                  August: { days: 31, day-length-in-min: 12, night-length-in-min: 8 }
                  September: { days: 30, day-length-in-min: 11, night-length-in-min: 9 }
                  October: { days: 31, day-length-in-min: 10, night-length-in-min: 10 }
                  November: { days: 30, day-length-in-min: 9, night-length-in-min: 11 }
                  December: { days: 31, day-length-in-min: 8, night-length-in-min: 12 }
                """);
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertEquals("July", s.calendar().monthName(new CalendarDate(7, 7, 449)));
        assertEquals("Mon", s.calendar().weekdayName(new CalendarDate(7, 7, 449)));
        assertEquals(1.3, s.calendar().month(7).dayLengthMultiplier(), 1e-9);
        assertEquals("7/7/449", s.formatDate(new CalendarDate(7, 7, 449)));
    }

    @Test
    void customCalendar() throws ConfigException {
        CalendarSettings s = parse("""
                american-date-format: true
                week-days: [ Morning, Noon, Evening ]
                winter-start: Frostmonth 1
                spring-start: 2 1
                summer-start: Great Summer 5
                fall-start: Great Summer 20
                months:
                  Frostmonth: { days: 20, day-length-in-min: 6, night-length-in-min: 14 }
                  Budmonth: { days: 20, day-length-in-min: 10, night-length-in-min: 10 }
                  Great Summer: { days: 40, day-length-in-min: 14, night-length-in-min: 6 }
                """);
        assertTrue(warnings.isEmpty(), warnings::toString);
        GameCalendar cal = s.calendar();
        assertEquals(80, cal.daysInYear());
        assertEquals(3, cal.weekdays().size());
        assertEquals(new MonthDay(3, 5), cal.start(Season.SUMMER));
        assertEquals(new MonthDay(2, 1), cal.start(Season.SPRING));
        assertEquals("2/7/3", s.formatDate(new CalendarDate(7, 2, 3)));
    }

    @Test
    void brokenCalendarIsFatal() {
        assertThrows(ConfigException.class, () -> parse("""
                winter-start: Smarch 4
                """));
        assertThrows(ConfigException.class, () -> parse("""
                months:
                  Single: { days: 30 }
                """));
        assertThrows(ConfigException.class, () -> parse("""
                week-days: [ none ]
                """));
    }
}
