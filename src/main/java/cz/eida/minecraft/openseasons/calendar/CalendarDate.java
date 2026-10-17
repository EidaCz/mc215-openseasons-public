/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

/**
 * Game date. The month is its position in the calendar from 1 (not a name), so renaming months
 * in the configuration does not break saved dates.
 */
public record CalendarDate(int day, int month, int year) {

    public CalendarDate {
        if (day < 1) throw new IllegalArgumentException("day must be at least 1, not " + day);
        if (month < 1) throw new IllegalArgumentException("month must be at least 1, not " + month);
    }

    @Override
    public String toString() {
        return day + "/" + month + "/" + year;
    }
}
