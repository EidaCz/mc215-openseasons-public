/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.time;

import java.util.Collection;

/**
 * A night slept through by the same rule as vanilla (Paper 26.x): counted are the players of the world
 * outside spectator mode; sleeping is whoever lies in a bed or has
 * {@code sleepingIgnored}. The night is skipped when at least the required number sleeps, the same number sleeps deeply
 * (at least 100 ticks in bed, or ignored) and at least one really sleeps deeply. The required number
 * is {@code max(1, ⌈active × players_sleeping_percentage / 100⌉)}; above 100 % nobody skips the night.
 *
 * <p>Vanilla with {@code advance_time = false} only wakes the players and resets the weather, it does not move the time.
 */
public final class NightSkip {

    /** state of one player in the world */
    public record Sleeper(boolean spectator, boolean inBed, boolean deeplySleeping, boolean sleepingIgnored) {
    }

    private NightSkip() {
    }

    public static int sleepersNeeded(int activePlayers, int percentage) {
        return Math.max(1, (int) Math.ceil(activePlayers * percentage / 100.0f));
    }

    public static boolean nightPasses(Collection<Sleeper> players, int percentage) {
        int active = 0;
        int sleeping = 0;
        int deep = 0;
        boolean anyDeep = false;
        for (Sleeper p : players) {
            if (p.deeplySleeping() || p.sleepingIgnored()) deep++;
            if (p.deeplySleeping()) anyDeep = true;
            if (p.spectator()) continue;
            active++;
            if (p.inBed() || p.sleepingIgnored()) sleeping++;
        }
        int needed = sleepersNeeded(active, percentage);
        return anyDeep && sleeping >= needed && deep >= needed;
    }
}
