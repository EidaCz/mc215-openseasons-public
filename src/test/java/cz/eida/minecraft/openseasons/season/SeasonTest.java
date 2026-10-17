/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.season;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonTest {

    @Test
    void cycle() {
        assertEquals(Season.SPRING, Season.WINTER.next());
        assertEquals(Season.SUMMER, Season.SPRING.next());
        assertEquals(Season.FALL, Season.SUMMER.next());
        assertEquals(Season.WINTER, Season.FALL.next());
    }

    @Test
    void parse() {
        assertEquals(Optional.of(Season.SUMMER), Season.parse("summer"));
        assertEquals(Optional.of(Season.FALL), Season.parse(" Autumn "));
        assertTrue(Season.parse("NONE").isEmpty());
        assertTrue(Season.parse("DISABLED").isEmpty());
        assertTrue(Season.parse(null).isEmpty());
    }

    @Test
    void subSeasonThresholds() {
        assertEquals(SubSeason.START, SubSeason.fromProgress(8.9));
        assertEquals(SubSeason.EARLY, SubSeason.fromProgress(9.0));
        assertEquals(SubSeason.MIDDLE, SubSeason.fromProgress(18.0));
        assertEquals(SubSeason.LATE, SubSeason.fromProgress(84.0));
        assertEquals(SubSeason.END, SubSeason.fromProgress(92.0));
        assertEquals(SubSeason.END, SubSeason.fromProgress(92.1));
        assertEquals(SubSeason.MIDDLE, SubSeason.ofIndex(2));
        assertThrows(IllegalArgumentException.class, () -> SubSeason.ofIndex(5));
    }
}
