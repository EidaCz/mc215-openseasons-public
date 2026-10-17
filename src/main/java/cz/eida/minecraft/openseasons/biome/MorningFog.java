/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.biome;

import cz.eida.minecraft.openseasons.config.ClientColorSettings;
import cz.eida.minecraft.openseasons.season.Season;

/**
 * Morning fog in late fall. A foggy day is rolled per
 * world and clock day (the same for all players, also after a restart). The fog lies in lowlands: biome cells below
 * its top get the foggy variant, so there is a view from a hill and fog in the valley.
 *
 * <p>Course (world time): 4:00 half height, 5:00–8:00 full, 8:00 half, 9:30 low veil of
 * 4 blocks, 10:30 gone. Heights go in steps of 4 blocks (height of a biome cell), so there are five changes per morning.
 */
public final class MorningFog {

    /** no fog */
    public static final int NONE = Integer.MIN_VALUE;
    private static final int CELL = 4;

    private MorningFog() {
    }

    /**
     * Top of the fog above sea level in blocks, or {@link #NONE}.
     *
     * @param progress   elapsed part of the season in percent
     * @param timeOfDay  world day time 0–23999 (0 = 6:00)
     */
    public static int layerTop(ClientColorSettings.MorningFog cfg, String world, long clockDay, Season season,
                               double progress, long timeOfDay) {
        if (!cfg.enabled() || season != Season.FALL || progress < cfg.fromProgress()) return NONE;
        if (!foggyDay(world, clockDay, cfg.chance())) return NONE;
        int full = quantize(cfg.height());
        int half = Math.max(CELL, quantize(cfg.height() / 2));
        long t = Math.floorMod(timeOfDay, 24000L);
        if (t >= 22000 && t < 23000) return half;
        if (t >= 23000 || t < 2000) return full;
        if (t < 3500) return half;
        if (t < 4500) return CELL;
        return NONE;
    }

    /** foggy morning: rolled by world and day */
    public static boolean foggyDay(String world, long clockDay, double chance) {
        if (chance <= 0) return false;
        long h = world.hashCode() * 0x9E3779B97F4A7C15L ^ clockDay * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        double roll = Math.floorMod(h, 10_000L) / 100.0;
        return roll < chance;
    }

    private static int quantize(int blocks) {
        return Math.max(CELL, blocks / CELL * CELL);
    }
}
