/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {

    @Test
    void ticksToClock() {
        assertEquals("06:00", TimeFormat.DEFAULT.format(0));
        assertEquals("12:00", TimeFormat.DEFAULT.format(6000));
        assertEquals("00:00", TimeFormat.DEFAULT.format(18000));
        // 9559 ticks = 15:33
        assertEquals("15:33", TimeFormat.DEFAULT.format(9559));
        assertEquals("06:00", TimeFormat.DEFAULT.format(24000 * 5));
    }

    @Test
    void twelveHourClock() {
        assertEquals("3:33 PM", new TimeFormat("$hours$:$minutes$", true, true).format(9559));
        assertEquals("12:00", new TimeFormat("$hours$:$minutes$", true, false).format(18000));
        assertEquals("time 6 h 00 min", new TimeFormat("time $hours$ h $minutes$ min", true, false).format(0));
    }
}
