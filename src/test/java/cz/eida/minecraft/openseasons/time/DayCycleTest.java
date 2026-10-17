/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.time;

import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import cz.eida.minecraft.openseasons.season.SeasonService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Day and night length by the month; months as in the default calendar.yml. */
class DayCycleTest {

    private static final MonthDefinition JANUARY = new MonthDefinition("January", 31, 7, 13);
    private static final MonthDefinition APRIL = new MonthDefinition("April", 30, 10, 10);
    private static final MonthDefinition JULY = new MonthDefinition("July", 31, 13, 7);

    @Test
    void ratesFollowMinutes() {
        assertEquals(1.0, DayCycle.dayRate(APRIL));
        assertEquals(1.0, DayCycle.nightRate(APRIL));
        assertEquals(12000.0 / 8400, DayCycle.dayRate(JANUARY), 1e-12);
        assertEquals(12000.0 / 15600, DayCycle.nightRate(JANUARY), 1e-12);
        assertEquals(12000.0 / 15600, DayCycle.dayRate(JULY), 1e-12);
    }

    @Test
    void vanillaMonthMovesOneTickPerTick() {
        DayCycle.Accumulator acc = new DayCycle.Accumulator();
        long time = 3_964_135_921L;
        for (int i = 0; i < 48_000; i++) {
            assertEquals(1, acc.step(time, APRIL));
            time++;
        }
        assertEquals(0.0, acc.pending());
    }

    @Test
    void januaryDayTakesSevenMinutesAndNightThirteen() {
        Run day = run(0, JANUARY, 12000);
        assertEquals(7 * 1200, day.serverTicks, 1);
        Run whole = run(0, JANUARY, 24000);
        assertEquals(20 * 1200, whole.serverTicks, 1);
    }

    @Test
    void julyDayTakesThirteenMinutesAndNightSeven() {
        Run day = run(0, JULY, 12000);
        assertEquals(13 * 1200, day.serverTicks, 1);
        Run night = run(12000, JULY, 24000);
        assertEquals(7 * 1200, night.serverTicks, 1);
    }

    @Test
    void writesOnlyWhenTheClockMoves() {
        // a long day: 12 000 ticks in 15 600 server ticks, only 12 000 writes
        Run day = run(0, JULY, 12000);
        assertEquals(12000, day.writes, 1);
        assertTrue(day.maxStep == 1);
        // a short day: 12 000 ticks in 8 400 server ticks, by one or two
        Run januaryDay = run(0, JANUARY, 12000);
        assertEquals(januaryDay.serverTicks, januaryDay.writes);
        assertEquals(2, januaryDay.maxStep);
    }

    @Test
    void manyDaysDoNotDrift() {
        Run days = run(0, JANUARY, 24000L * 30);
        assertEquals(30 * 20 * 1200, days.serverTicks, 1);
    }

    @Test
    void advanceSplitsAtSunsetAndSunrise() {
        // January: 0.5 tick until sunset in the day (1.43/tick), the rest of the server tick at night (0.77/tick)
        double untilSunset = 0.5 / DayCycle.dayRate(JANUARY);
        double expected = 0.5 + (1 - untilSunset) * DayCycle.nightRate(JANUARY);
        assertEquals(expected, DayCycle.advance(11999.5, 1, JANUARY), 1e-9);
        // across the midnight of the day (24000 → 0) back into the day
        double untilSunrise = 0.25 / DayCycle.nightRate(JULY);
        assertEquals(0.25 + (1 - untilSunrise) * DayCycle.dayRate(JULY),
                DayCycle.advance(23999.75, 1, JULY), 1e-9);
        assertEquals(DayCycle.advance(500, 1, JULY), DayCycle.advance(24500, 1, JULY), 1e-9);
    }

    @Test
    void nextMorningIsTheNextDayStart() {
        assertEquals(24000, DayCycle.nextMorning(13000));
        assertEquals(48000, DayCycle.nextMorning(24000));
        assertEquals(165_173L * 24000, DayCycle.nextMorning(3_964_135_921L + 6000));
        assertEquals(0, DayCycle.nextMorning(-100));
    }

    @Test
    void skippedNightMovesTheDateByOneDay() {
        long evening = 10 * 24000L + 13000;
        long afterMidnight = 10 * 24000L + 20000;
        assertEquals(SeasonService.clockDay(evening) + 1, SeasonService.clockDay(DayCycle.nextMorning(evening)));
        // after midnight the date has changed, the morning is the same day
        assertEquals(SeasonService.clockDay(evening) + 1, SeasonService.clockDay(afterMidnight));
        assertEquals(SeasonService.clockDay(afterMidnight), SeasonService.clockDay(DayCycle.nextMorning(afterMidnight)));
    }

    private record Run(long serverTicks, long writes, long maxStep) {
    }

    /** server ticks until the clock gets from {@code from} to {@code to} */
    private static Run run(long from, MonthDefinition month, long to) {
        DayCycle.Accumulator acc = new DayCycle.Accumulator();
        long time = from;
        long ticks = 0;
        long writes = 0;
        long max = 0;
        while (time < to) {
            long step = acc.step(time, month);
            ticks++;
            if (step > 0) {
                writes++;
                max = Math.max(max, step);
                time += step;
            }
        }
        return new Run(ticks, writes, max);
    }
}
