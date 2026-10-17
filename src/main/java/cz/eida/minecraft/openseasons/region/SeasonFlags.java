/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import cz.eida.minecraft.openseasons.season.SeasonSource;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The six WorldGuard season flags ({@link RegionFlagNames}) and two flags of the real-world
 * calendar ({@code season-source}, {@code real-periods}). They are registered
 * in {@code onLoad}, before WorldGuard loads the regions. When a flag of the same name already exists
 * (e.g. registered by another plugin) and is of the same kind, that one is used; otherwise the flag
 * is skipped and its setting applies only globally. A missing flag is {@code null}.
 *
 * <p>The class references WorldGuard and is only loaded with it.
 */
public final class SeasonFlags {

    private static volatile SeasonFlags registered;

    /** {@code permanent-season}: {@link EnumFlag} with the values of {@link RegionSeason} or a foreign enum with the same names */
    final Flag<?> permanentSeason;
    final StateFlag effects;
    final StateFlag blockChanges;
    final StateFlag mobSpawning;
    /** {@code season-source}: enum {@link SeasonSource} or a foreign enum with the same names */
    final Flag<?> seasonSource;
    /** {@code real-periods}: set of strings */
    final Flag<?> realPeriods;

    SeasonFlags(Flag<?> permanentSeason, StateFlag effects, StateFlag blockChanges, StateFlag mobSpawning,
                Flag<?> seasonSource, Flag<?> realPeriods) {
        this.permanentSeason = permanentSeason;
        this.effects = effects;
        this.blockChanges = blockChanges;
        this.mobSpawning = mobSpawning;
        this.seasonSource = seasonSource;
        this.realPeriods = realPeriods;
    }

    SeasonFlags(Flag<?> permanentSeason, StateFlag effects, StateFlag blockChanges, StateFlag mobSpawning) {
        this(permanentSeason, effects, blockChanges, mobSpawning, null, null);
    }

    /** flags from {@link #registerWithWorldGuard}; {@code null} when they were not registered */
    static SeasonFlags registered() {
        return registered;
    }

    /** registration in the registry of the running WorldGuard (called from {@code onLoad}) */
    static SeasonFlags registerWithWorldGuard(Logger log) {
        SeasonFlags flags = register(WorldGuard.getInstance().getFlagRegistry(), log);
        registered = flags;
        return flags;
    }

    /**
     * Registers the flags, or takes over existing ones of the same kind.
     */
    static SeasonFlags register(FlagRegistry registry, Logger log) {
        Flag<?> season = ensure(registry, new EnumFlag<>(RegionFlagNames.PERMANENT_SEASON, RegionSeason.class),
                SeasonFlags::seasonEnum, log);
        StateFlag effects = (StateFlag) ensure(registry, new StateFlag(RegionFlagNames.SEASON_EFFECTS, true),
                f -> f instanceof StateFlag, log);
        StateFlag blocks = (StateFlag) ensure(registry, new StateFlag(RegionFlagNames.SEASON_BLOCK_CHANGES, true),
                f -> f instanceof StateFlag, log);
        StateFlag mobs = (StateFlag) ensure(registry, new StateFlag(RegionFlagNames.SEASON_MOB_SPAWNING, true),
                f -> f instanceof StateFlag, log);
        Flag<?> source = ensure(registry, new EnumFlag<>(RegionFlagNames.SEASON_SOURCE, SeasonSource.class),
                SeasonFlags::sourceEnum, log);
        Flag<?> periods = ensure(registry, new SetFlag<>(RegionFlagNames.REAL_PERIODS, new StringFlag(null)),
                f -> f instanceof SetFlag<?>, log);
        return new SeasonFlags(season, effects, blocks, mobs, source, periods);
    }

    /** registered or taken-over flags, e.g. for listing */
    List<String> available() {
        return Stream.of(permanentSeason, effects, blockChanges, mobSpawning,
                        seasonSource, realPeriods)
                .filter(Objects::nonNull).map(Flag::getName).toList();
    }

    /** enum with the values SUMMER, WINTER, FALL and SPRING (own or foreign) */
    private static boolean seasonEnum(Flag<?> flag) {
        if (!(flag instanceof EnumFlag<?> e)) return false;
        Set<String> names = Arrays.stream(e.getEnumClass().getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        return Arrays.stream(RegionSeason.values()).allMatch(s -> names.contains(s.name()));
    }

    /** enum with the values GAME and REAL */
    private static boolean sourceEnum(Flag<?> flag) {
        if (!(flag instanceof EnumFlag<?> e)) return false;
        Set<String> names = Arrays.stream(e.getEnumClass().getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        return Arrays.stream(SeasonSource.values()).allMatch(s -> names.contains(s.name()));
    }

    private static Flag<?> ensure(FlagRegistry registry, Flag<?> flag, Predicate<Flag<?>> compatible, Logger log) {
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException | IllegalStateException e) {
            Flag<?> existing = registry.get(flag.getName());
            if (existing != null && compatible.test(existing)) {
                log.info("WorldGuard: flag " + flag.getName() + " already exists (" + existing.getClass().getSimpleName()
                        + "), OpenSeasons will use it.");
                return existing;
            }
            log.warning("WorldGuard: flag " + flag.getName() + " cannot be registered (" + e.getMessage() + ")"
                    + (existing != null ? ", the existing " + existing.getClass().getSimpleName() + " is of another kind" : "")
                    + "; its setting applies only globally.");
            return null;
        }
    }
}
