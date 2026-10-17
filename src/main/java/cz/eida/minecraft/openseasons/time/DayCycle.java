/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.time;

import cz.eida.minecraft.openseasons.calendar.MonthDefinition;

/**
 * Length of day and night by month ({@code months.<month>.day-length-in-min} and
 * {@code night-length-in-min}). The day is ticks 0–12000 of the day time, the night 12000–24000; a month with a day
 * of {@code d} and a night of {@code n} minutes passes them in {@code d} and {@code n} real minutes at
 * 20 TPS (vanilla 10 + 10). Without Bukkit, tested without a server.
 */
public final class DayCycle {

    public static final long TICKS_PER_DAY = 24000;
    /** start of the night in the day time (sunset) */
    public static final long NIGHT_START = 12000;
    /** server ticks per real minute at 20 TPS */
    public static final double SERVER_TICKS_PER_MINUTE = 1200;

    private DayCycle() {
    }

    /** game ticks per server tick during the day (January 7 min → 1.43, July 13 min → 0.77) */
    public static double dayRate(MonthDefinition month) {
        return NIGHT_START / (month.dayLengthMinutes() * SERVER_TICKS_PER_MINUTE);
    }

    /** game ticks per server tick during the night */
    public static double nightRate(MonthDefinition month) {
        return (TICKS_PER_DAY - NIGHT_START) / (month.nightLengthMinutes() * SERVER_TICKS_PER_MINUTE);
    }

    /**
     * By how many game ticks the time moves in {@code serverTicks} server ticks from the day time
     * {@code timeOfDay} (including a fraction). Across sunset and sunrise each part is counted at its own speed.
     */
    public static double advance(double timeOfDay, double serverTicks, MonthDefinition month) {
        double t = timeOfDay % TICKS_PER_DAY;
        if (t < 0) t += TICKS_PER_DAY;
        double left = serverTicks;
        double moved = 0;
        while (left > 0) {
            boolean day = t < NIGHT_START;
            double rate = day ? dayRate(month) : nightRate(month);
            double end = day ? NIGHT_START : TICKS_PER_DAY;
            double untilEnd = (end - t) / rate;
            if (left < untilEnd) return moved + left * rate;
            moved += end - t;
            left -= untilEnd;
            t = day ? NIGHT_START : 0;
        }
        return moved;
    }

    /** next morning (day time 0), where vanilla moves the clock after a night slept through or a storm */
    public static long nextMorning(long fullTime) {
        return (Math.floorDiv(fullTime, TICKS_PER_DAY) + 1) * TICKS_PER_DAY;
    }

    /**
     * The world clock moves only in whole ticks; fractions from a slow day or a fast night are accumulated
     * for the next server tick. One instance per world.
     */
    public static final class Accumulator {

        private double pending;

        /**
         * One server tick.
         *
         * @return by how many whole ticks to move the clock, 0 = no change
         */
        public long step(long fullTime, MonthDefinition month) {
            pending += advance(Math.floorMod(fullTime, TICKS_PER_DAY) + pending, 1, month);
            long whole = (long) pending;
            pending -= whole;
            return whole;
        }

        /** after a time jump (night skipped) drop the fraction */
        public void reset() {
            pending = 0;
        }

        /** accumulated fraction of a tick, 0–1 */
        public double pending() {
            return pending;
        }
    }
}
