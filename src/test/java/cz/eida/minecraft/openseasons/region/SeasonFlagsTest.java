/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonFlagsTest {

    /** like an enum of another plugin: the same names, another class */
    enum ForeignSeason { SUMMER, WINTER, FALL, SPRING }

    enum Unrelated { RED, GREEN }

    private final List<String> warnings = new ArrayList<>();
    private final Logger log = Logger.getAnonymousLogger();

    {
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    void registersAllFlags() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        SeasonFlags flags = SeasonFlags.register(registry, log);

        List<String> all = new java.util.ArrayList<>(RegionFlagNames.ALL);
        all.addAll(List.of(RegionFlagNames.SEASON_SOURCE, RegionFlagNames.REAL_PERIODS));
        assertEquals(all, flags.available());
        for (String name : RegionFlagNames.ALL) assertTrue(registry.get(name) != null, name);
        EnumFlag<?> season = assertInstanceOf(EnumFlag.class, registry.get("permanent-season"));
        assertSame(RegionSeason.class, season.getEnumClass());
        for (String state : List.of("season-effects", "season-block-changes", "season-mob-spawning")) {
            StateFlag f = assertInstanceOf(StateFlag.class, registry.get(state));
            assertSame(StateFlag.State.ALLOW, f.getDefault(), state);
        }
        // flags of the real calendar
        EnumFlag<?> source = assertInstanceOf(EnumFlag.class, registry.get("season-source"));
        assertSame(cz.eida.minecraft.openseasons.season.SeasonSource.class, source.getEnumClass());
        assertInstanceOf(com.sk89q.worldguard.protection.flags.SetFlag.class, registry.get("real-periods"));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void reusesExistingFlagsOfTheSameKind() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        // as if another plugin registered them
        Flag<?> season = new EnumFlag<>("permanent-season", ForeignSeason.class);
        StateFlag effects = new StateFlag("season-effects", true);
        registry.register(season);
        registry.register(effects);

        SeasonFlags flags = SeasonFlags.register(registry, log);

        assertSame(season, flags.permanentSeason);
        assertSame(effects, flags.effects);
        assertEquals(6, flags.available().size());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void conflictingKindIsSkippedWithWarning() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        registry.register(new StringFlag("permanent-season"));
        registry.register(new EnumFlag<>("season-effects", Unrelated.class));
        registry.register(new EnumFlag<>("season-mob-spawning", Unrelated.class));

        SeasonFlags flags = SeasonFlags.register(registry, log);

        assertNull(flags.permanentSeason);
        assertNull(flags.effects);
        assertNull(flags.mobSpawning);
        assertEquals(List.of("season-block-changes", "season-source", "real-periods"), flags.available());
        assertEquals(3, warnings.size(), warnings::toString);
    }

    @Test
    void foreignEnumWithOtherValuesIsNotASeason() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        registry.register(new EnumFlag<>("permanent-season", Unrelated.class));

        assertNull(SeasonFlags.register(registry, log).permanentSeason);
        assertEquals(1, warnings.size());
    }

    @Test
    void lockedRegistryReusesWhatExistsAndSkipsTheRest() {
        SimpleFlagRegistry registry = new SimpleFlagRegistry();
        StateFlag mobs = new StateFlag("season-mob-spawning", true);
        registry.register(mobs);
        registry.setInitialized(true);

        SeasonFlags flags = SeasonFlags.register(registry, log);

        assertSame(mobs, flags.mobSpawning);
        assertEquals(List.of("season-mob-spawning"), flags.available());
        assertEquals(5, warnings.size(), warnings::toString);
    }
}
