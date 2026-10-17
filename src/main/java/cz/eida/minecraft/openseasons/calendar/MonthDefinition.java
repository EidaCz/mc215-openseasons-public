/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

/**
 * A month of the custom calendar.
 *
 * @param name               display name (key in {@code months:})
 * @param days               number of days
 * @param dayLengthMinutes   length of the day in real minutes
 * @param nightLengthMinutes length of the night in real minutes
 */
public record MonthDefinition(String name, int days, int dayLengthMinutes, int nightLengthMinutes) {

    public MonthDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a month must have a name");
        if (days < 1) throw new IllegalArgumentException("month " + name + " must have at least 1 day");
        if (dayLengthMinutes < 1 || nightLengthMinutes < 1) {
            throw new IllegalArgumentException("month " + name + ": day and night must both last at least 1 minute");
        }
    }

    /** how many times the day is longer than the average of day and night (January 7/13 min → 0.7) */
    public double dayLengthMultiplier() {
        return dayLengthMinutes / ((dayLengthMinutes + nightLengthMinutes) / 2.0);
    }
}
