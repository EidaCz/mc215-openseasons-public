/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.placeholder;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.ConfigLoader;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholderResolverTest {

    @TempDir
    Path dir;

    private PlaceholderResolver resolver;

    @BeforeEach
    void setUp() throws ConfigException {
        LoadedConfig config = new ConfigLoader(dir.resolve("OpenSeasons")).load();
        SeasonService seasons = new SeasonService(config.calendar().calendar(), true, new SeasonService.Listener() {
            @Override
            public boolean beforeSeasonChange(String w, Season f, Season t, SeasonService.Cause c) {
                return true;
            }

            @Override
            public void seasonChanged(String w, Season f, Season t, SeasonService.Cause c) {
            }

            @Override
            public void dayChanged(String w, CalendarDate f, CalendarDate t) {
            }
        });
        seasons.load(Map.of(
                "world", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(7, 7, 449)),
                "world_nether", new WorldSeasonState(true, Season.WINTER, new CalendarDate(1, 1, 1)),
                "doom", new WorldSeasonState(false, Season.FALL, new CalendarDate(27, 10, 129))));
        Map<String, Long> times = Map.of("world", 6000L, "world_nether", 0L);
        resolver = new PlaceholderResolver(seasons, () -> config,
                w -> times.containsKey(w) ? OptionalLong.of(times.get(w)) : OptionalLong.empty());
    }

    @Test
    void localSeasonFromRegion() {
        assertEquals("Winter", resolver.local("local_season", java.util.Optional.of(Season.WINTER)));
        assertEquals("FALL", resolver.local("local_season_id", java.util.Optional.of(Season.FALL)));
        assertEquals("DISABLED", resolver.local("local_season", java.util.Optional.empty()));
        assertNull(resolver.local("season", java.util.Optional.of(Season.FALL)));
    }

    @Test
    void ownNamespace() {
        assertEquals("Summer", resolver.resolveOwn("season", "world"));
        assertEquals("SUMMER", resolver.resolveOwn("season_id_world", null));
        assertEquals("Fall", resolver.resolveOwn("next_season_world", null));
        assertEquals("59", resolver.resolveOwn("days_until_next_season", "world"));
        assertEquals("92", resolver.resolveOwn("season_length_world", null));
        assertEquals("July", resolver.resolveOwn("month_name_world", null));
        assertEquals("Monday", resolver.resolveOwn("weekday", "world"));
        assertEquals("7/7/449", resolver.resolveOwn("date", "world"));
        assertEquals("12:00", resolver.resolveOwn("time", "world"));
        assertEquals("MIDDLE", resolver.resolveOwn("sub_season", "world"));
        assertEquals("36", resolver.resolveOwn("season_progress", "world"));
        assertEquals("1", resolver.resolveOwn("day_world_nether", null));
        assertEquals("DISABLED", resolver.resolveOwn("season_doom", null));
        assertEquals("DISABLED", resolver.resolveOwn("days_until_next_season_doom", null));
        assertEquals("disabled", resolver.resolveOwn("enabled_doom", null));
        assertEquals("", resolver.resolveOwn("season", null));
        assertNull(resolver.resolveOwn("season_eventworld", null));
        assertNull(resolver.resolveOwn("nonsense", "world"));
        assertNull(resolver.resolveOwn("time_doom", null));
    }
}
