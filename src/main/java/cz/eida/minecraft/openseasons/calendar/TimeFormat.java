/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

/**
 * Display of the game time by the {@code time.format} template, e.g. {@code $hours$:$minutes$}.
 * Tick 0 of a day is 6:00, 6000 is noon, 18000 midnight.
 */
public record TimeFormat(String pattern, boolean twelveHourClock, boolean amPm) {

    public static final TimeFormat DEFAULT = new TimeFormat("$hours$:$minutes$", false, false);

    public String format(long timeOfDayTicks) {
        long ticks = Math.floorMod(timeOfDayTicks, 24000L);
        int minutesOfDay = (int) ((ticks * 1440 / 24000 + 360) % 1440);
        int hours = minutesOfDay / 60;
        int minutes = minutesOfDay % 60;

        String h;
        String suffix = "";
        if (twelveHourClock) {
            int h12 = hours % 12 == 0 ? 12 : hours % 12;
            h = Integer.toString(h12);
            if (amPm) suffix = hours < 12 ? " AM" : " PM";
        } else {
            h = String.format("%02d", hours);
        }
        return pattern.replace("$hours$", h).replace("$minutes$", String.format("%02d", minutes)) + suffix;
    }
}
