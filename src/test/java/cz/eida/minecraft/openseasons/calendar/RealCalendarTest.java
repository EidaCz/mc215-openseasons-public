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

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealCalendarTest {

    private final RealCalendar astro = RealCalendar.ASTRONOMICAL;

    /** astronomical seasons */
    @Test
    void astronomicalSeasons() {
        assertEquals(Season.FALL, astro.seasonAt(LocalDate.of(2026, 10, 17)));
        assertEquals(Season.WINTER, astro.seasonAt(LocalDate.of(2026, 3, 19)));
        assertEquals(Season.SPRING, astro.seasonAt(LocalDate.of(2026, 3, 20)));
        assertEquals(Season.SUMMER, astro.seasonAt(LocalDate.of(2026, 6, 21)));
        assertEquals(Season.FALL, astro.seasonAt(LocalDate.of(2026, 12, 20)));
        assertEquals(Season.WINTER, astro.seasonAt(LocalDate.of(2026, 12, 21)));
        // across the end of a year
        assertEquals(Season.WINTER, astro.seasonAt(LocalDate.of(2027, 1, 1)));
        assertEquals(Season.WINTER, astro.seasonAt(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void progressAndDaysUntilNext() {
        // 17/10: 24 days of 89 (23/9 – 20/12)
        assertEquals(100.0 * 24 / 89, astro.progress(LocalDate.of(2026, 10, 17)), 1e-9);
        assertEquals(65, astro.daysUntilNext(LocalDate.of(2026, 10, 17)));
        assertEquals(0.0, astro.progress(LocalDate.of(2026, 9, 23)), 1e-9);
        // winter 21/12/2026 – 19/3/2027 (89 days), 1/1 is the 11th day
        assertEquals(100.0 * 11 / 89, astro.progress(LocalDate.of(2027, 1, 1)), 1e-9);
    }

    @Test
    void meteorologicalStarts() {
        RealCalendar meteo = new RealCalendar(Map.of(Season.SPRING, MonthDay.of(3, 1), Season.SUMMER, MonthDay.of(6, 1),
                Season.FALL, MonthDay.of(9, 1), Season.WINTER, MonthDay.of(12, 1)), ZoneId.of("Europe/Prague"));
        assertEquals(Season.WINTER, meteo.seasonAt(LocalDate.of(2028, 2, 29)));
        assertEquals(Season.SPRING, meteo.seasonAt(LocalDate.of(2028, 3, 1)));
        assertEquals(ZoneId.of("Europe/Prague"), meteo.zone());
    }

    @Test
    void invalidStarts() {
        assertThrows(IllegalArgumentException.class, () -> new RealCalendar(Map.of(Season.SPRING, MonthDay.of(3, 1)),
                ZoneId.systemDefault()));
        assertThrows(IllegalArgumentException.class, () -> new RealCalendar(Map.of(Season.SPRING, MonthDay.of(3, 1),
                Season.SUMMER, MonthDay.of(3, 1), Season.FALL, MonthDay.of(9, 1), Season.WINTER, MonthDay.of(12, 1)),
                ZoneId.systemDefault()));
    }

    @Test
    void periods() {
        RealPeriod halloween = new RealPeriod("halloween", MonthDay.of(10, 25), MonthDay.of(11, 4), Optional.of(Season.FALL),
                OptionalDouble.of(70), List.of("halloween"), List.of("world"));
        assertFalse(halloween.activeOn(LocalDate.of(2026, 10, 24)));
        assertTrue(halloween.activeOn(LocalDate.of(2026, 10, 25)));
        assertTrue(halloween.activeOn(LocalDate.of(2026, 11, 4)));
        assertFalse(halloween.activeOn(LocalDate.of(2026, 11, 5)));
        assertEquals(70.0, halloween.progressOn(LocalDate.of(2026, 10, 30), astro));
        assertTrue(halloween.appliesToWorld("World"));
        assertEquals("25/10 – 04/11", halloween.range());

        // across the end of a year, without a given progress
        RealPeriod holidays = new RealPeriod("holidays", MonthDay.of(12, 20), MonthDay.of(1, 6), Optional.of(Season.WINTER),
                OptionalDouble.empty(), List.of(), List.of());
        assertTrue(holidays.activeOn(LocalDate.of(2026, 12, 31)));
        assertTrue(holidays.activeOn(LocalDate.of(2027, 1, 6)));
        assertFalse(holidays.activeOn(LocalDate.of(2027, 1, 7)));
        assertFalse(holidays.activeOn(LocalDate.of(2026, 12, 19)));
        // 1/1 is real winter: real progress; 20/12 is still autumn: half
        assertEquals(astro.progress(LocalDate.of(2027, 1, 1)), holidays.progressOn(LocalDate.of(2027, 1, 1), astro), 1e-9);
        assertEquals(50.0, holidays.progressOn(LocalDate.of(2026, 12, 20), astro));
    }
}
