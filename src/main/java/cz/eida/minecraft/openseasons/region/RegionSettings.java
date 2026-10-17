/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import cz.eida.minecraft.openseasons.season.Season;

import cz.eida.minecraft.openseasons.season.SeasonSource;

import java.util.Optional;
import java.util.Set;

/**
 * Region settings in a chunk column (WorldGuard flags).
 * Without a region or without WorldGuard {@link #NONE}: everything allowed, nothing permanent.
 *
 * @param permanentSeason      {@code permanent-season}: permanent season instead of the calendar
 * @param effects              {@code season-effects}: landscape colours, particles and sky phenomena
 * @param blockChanges         {@code season-block-changes}: snow, ice, plants and other block changes
 * @param mobSpawning          {@code season-mob-spawning}: seasonal animals and monsters
 * @param seasonSource         {@code season-source}: season by the game or the real calendar
 * @param realPeriods          {@code real-periods}: ids of real-calendar periods that apply in the region
 */
public record RegionSettings(Optional<Season> permanentSeason, boolean effects, boolean blockChanges,
                             boolean mobSpawning, Optional<SeasonSource> seasonSource, Set<String> realPeriods) {

    public static final RegionSettings NONE = new RegionSettings(Optional.empty(), true, true, true);

    public RegionSettings {
        realPeriods = Set.copyOf(realPeriods);
    }

    /** only the season flags, without the real-calendar flags */
    public RegionSettings(Optional<Season> permanentSeason, boolean effects, boolean blockChanges, boolean mobSpawning) {
        this(permanentSeason, effects, blockChanges, mobSpawning, Optional.empty(), Set.of());
    }

    /** the region changes nothing */
    public boolean isNone() {
        return equals(NONE);
    }

    /** short listing for administrators, e.g. "permanent-season WINTER, season-effects deny" */
    public String describe() {
        if (isNone()) return "no region settings";
        StringBuilder sb = new StringBuilder();
        permanentSeason.ifPresent(s -> append(sb, RegionFlagNames.PERMANENT_SEASON + " " + s));
        if (!effects) append(sb, RegionFlagNames.SEASON_EFFECTS + " deny");
        if (!blockChanges) append(sb, RegionFlagNames.SEASON_BLOCK_CHANGES + " deny");
        if (!mobSpawning) append(sb, RegionFlagNames.SEASON_MOB_SPAWNING + " deny");
        seasonSource.ifPresent(s -> append(sb, RegionFlagNames.SEASON_SOURCE + " " + s));
        if (!realPeriods.isEmpty()) append(sb, RegionFlagNames.REAL_PERIODS + " " + String.join(",", new java.util.TreeSet<>(realPeriods)));
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(part);
    }
}
