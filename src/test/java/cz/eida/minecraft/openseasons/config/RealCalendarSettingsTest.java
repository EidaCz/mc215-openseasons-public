/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.calendar.RealPeriod;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealCalendarSettingsTest {

    @TempDir
    Path dir;

    /** default file: astronomical seasons, world by the game, Halloween 25/10 – 4/11 for world */
    @Test
    void defaultFile() throws ConfigException {
        List<String> warnings = new ArrayList<>();
        RealCalendarSettings s = RealCalendarSettings.load(dir, warnings);
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertTrue(Files.exists(dir.resolve(RealCalendarSettings.FILE)));
        assertEquals(MonthDay.of(9, 23), s.calendar().starts().get(Season.FALL));
        assertEquals(SeasonSource.GAME, s.worldSource("world"));
        assertEquals(SeasonSource.GAME, s.worldSource("doom"));
        RealPeriod h = s.period("halloween").orElseThrow();
        assertEquals(MonthDay.of(10, 25), h.from());
        assertEquals(MonthDay.of(11, 4), h.to());
        assertEquals(Season.FALL, h.season().orElseThrow());
        assertEquals(70.0, h.progress().getAsDouble());
        assertEquals(List.of("halloween"), h.events());
        LocalDate oct30 = LocalDate.of(2026, 10, 30);
        assertEquals(Set.of("halloween"), s.worldEvents("world", oct30));
        assertEquals(Set.of(), s.worldEvents("doom", oct30));
        assertEquals(Set.of("halloween"), s.regionEvents(oct30));
        assertEquals("halloween", s.worldSeasonPeriod("world", oct30).orElseThrow().id());
        assertTrue(s.worldSeasonPeriod("world", LocalDate.of(2026, 10, 17)).isEmpty());
    }

    @Test
    void customValuesAndWarnings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                timezone: Europe/Prague
                seasons:
                  spring: 1/3
                  summer: 1.6.
                  fall: 1/9
                  winter: 31/02
                worlds:
                  world: real
                  doom: unknown
                periods:
                  holidays:
                    from: 20/12
                    to: 06/01
                    season: WINTER
                    events: [ christmas ]
                  disabled:
                    enabled: false
                    from: 1/1
                    to: 2/1
                  no-end:
                    from: 1/1
                """);
        List<String> warnings = new ArrayList<>();
        RealCalendarSettings s = RealCalendarSettings.parse(new ConfigReader(yaml, "test", warnings), yaml);
        assertEquals(ZoneId.of("Europe/Prague"), s.calendar().zone());
        assertEquals(MonthDay.of(6, 1), s.calendar().starts().get(Season.SUMMER));
        // 31/02 does not exist: the default astronomical winter
        assertEquals(MonthDay.of(12, 21), s.calendar().starts().get(Season.WINTER));
        assertEquals(SeasonSource.REAL, s.worldSource("WORLD"));
        assertEquals(SeasonSource.GAME, s.worldSource("doom"));
        assertEquals(List.of("holidays"), s.periods().stream().map(RealPeriod::id).toList());
        assertTrue(s.period("holidays").orElseThrow().progress().isEmpty());
        assertEquals(3, warnings.size(), warnings::toString);
    }
}
