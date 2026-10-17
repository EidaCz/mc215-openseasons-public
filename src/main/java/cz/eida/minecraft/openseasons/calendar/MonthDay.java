/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

/** A day of the year without a year, e.g. the start of a season. The month is its position from 1. */
public record MonthDay(int month, int day) {

    public MonthDay {
        if (month < 1 || day < 1) throw new IllegalArgumentException("invalid day of the year " + month + "/" + day);
    }
}
