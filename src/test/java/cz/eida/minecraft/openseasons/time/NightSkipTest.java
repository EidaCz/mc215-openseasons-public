/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.time;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule of a slept night as in vanilla (players_sleeping_percentage). */
class NightSkipTest {

    private static final NightSkip.Sleeper DEEP = new NightSkip.Sleeper(false, true, true, false);
    private static final NightSkip.Sleeper DOZING = new NightSkip.Sleeper(false, true, false, false);
    private static final NightSkip.Sleeper AWAKE = new NightSkip.Sleeper(false, false, false, false);
    private static final NightSkip.Sleeper SPECTATOR = new NightSkip.Sleeper(true, false, false, false);
    private static final NightSkip.Sleeper IGNORED = new NightSkip.Sleeper(false, false, false, true);

    @Test
    void sleepersNeeded() {
        assertEquals(1, NightSkip.sleepersNeeded(0, 100));
        assertEquals(1, NightSkip.sleepersNeeded(5, 0));
        assertEquals(2, NightSkip.sleepersNeeded(3, 50));
        assertEquals(3, NightSkip.sleepersNeeded(3, 100));
        assertEquals(2, NightSkip.sleepersNeeded(1, 101));
    }

    @Test
    void everyoneHasToSleepByDefault() {
        assertTrue(NightSkip.nightPasses(List.of(DEEP), 100));
        assertFalse(NightSkip.nightPasses(List.of(DEEP, AWAKE), 100));
        assertTrue(NightSkip.nightPasses(List.of(DEEP, DEEP), 100));
    }

    @Test
    void percentageLowersTheCount() {
        assertTrue(NightSkip.nightPasses(List.of(DEEP, AWAKE), 50));
        assertFalse(NightSkip.nightPasses(List.of(DEEP, AWAKE, AWAKE), 50));
        assertTrue(NightSkip.nightPasses(List.of(DEEP, AWAKE, AWAKE), 0));
    }

    @Test
    void justLyingDownIsNotEnough() {
        assertFalse(NightSkip.nightPasses(List.of(DOZING), 100));
        assertFalse(NightSkip.nightPasses(List.of(DEEP, DOZING), 100));
        assertTrue(NightSkip.nightPasses(List.of(DEEP, DOZING), 50));
    }

    @Test
    void spectatorsDoNotCountAndIgnoredPlayersCountAsAsleep() {
        assertTrue(NightSkip.nightPasses(List.of(DEEP, SPECTATOR), 100));
        assertTrue(NightSkip.nightPasses(List.of(DEEP, IGNORED), 100));
        assertFalse(NightSkip.nightPasses(List.of(IGNORED, IGNORED), 100));
    }

    @Test
    void noPlayersNoSkip() {
        assertFalse(NightSkip.nightPasses(List.of(), 100));
        assertFalse(NightSkip.nightPasses(List.of(DEEP), 101));
    }
}
