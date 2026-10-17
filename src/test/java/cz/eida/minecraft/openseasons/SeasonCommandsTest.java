/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.calendar.GameCalendarTestAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonCommandsTest {

    private final GameCalendar cal = GameCalendarTestAccess.standard();

    @Test
    void seasonNamesIgnoreDiacriticsAndCase() {
        assertEquals("ete", SeasonCommands.plain(" Été "));
        assertEquals("fall", SeasonCommands.plain("FALL"));
    }

    @Test
    void setdateParsesBothFormats() {
        assertEquals(new CalendarDate(12, 7, 453), SeasonCommands.parseDate("12/7/453", false, cal));
        assertEquals(new CalendarDate(12, 7, 453), SeasonCommands.parseDate("07/12/453", true, cal));
    }

    @Test
    void setdateRejectsDatesOutsideCalendar() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SeasonCommands.parseDate("30/2/5", false, cal))
                .getMessage().contains("28"));
        assertThrows(IllegalArgumentException.class, () -> SeasonCommands.parseDate("1/13/5", false, cal));
        assertThrows(IllegalArgumentException.class, () -> SeasonCommands.parseDate("1/1/0", false, cal));
        assertThrows(IllegalArgumentException.class, () -> SeasonCommands.parseDate("1.1.5", false, cal));
        assertThrows(IllegalArgumentException.class, () -> SeasonCommands.parseDate("a/1/5", false, cal));
    }

    @Test
    void adminSubcommands() {
        assertEquals(java.util.List.of("set", "setdate", "nextseason", "disable", "pausetime"),
                SeasonCommands.ADMIN_SUBCOMMANDS);
    }
}
