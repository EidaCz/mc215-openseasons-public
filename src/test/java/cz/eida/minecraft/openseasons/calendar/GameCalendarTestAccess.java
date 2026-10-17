/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.calendar;

/** Shared test calendar for tests from other packages. */
public final class GameCalendarTestAccess {

    private GameCalendarTestAccess() {
    }

    public static GameCalendar standard() {
        return GameCalendarTest.standard();
    }
}
