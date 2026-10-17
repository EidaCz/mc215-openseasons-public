/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.ConfigLoader;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.placeholder.PlaceholderResolver;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Event placeholders: {@code %mc215os_next_event_world%} = {@code &6Halloween} (raw colours),
 * {@code %mc215os_days_until_next_event_world%} = {@code 101}, nothing running = {@code -}.
 */
class EventPlaceholdersTest {

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
                "world", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(22, 7, 449)),
                "doom", new WorldSeasonState(false, Season.FALL, new CalendarDate(12, 11, 129))));
        resolver = new PlaceholderResolver(seasons, () -> config, w -> OptionalLong.of(6000));
        resolver.setEvents(new PlaceholderResolver.Events() {
            @Override
            public List<String> activeEventNames(String world) {
                return world.equals("world") ? List.of("&cChristmas", "&aSunday") : List.of();
            }

            @Override
            public Optional<PlaceholderResolver.NextEvent> nextEvent(String world) {
                return world.equals("world") ? Optional.of(new PlaceholderResolver.NextEvent(List.of("&6Halloween"), 101))
                        : Optional.empty();
            }
        });
    }

    @Test
    void ownNamespace() {
        assertEquals("&cChristmas, &aSunday", resolver.resolveOwn("active_events", "world"));
        assertEquals("&6Halloween", resolver.resolveOwn("next_event_world", null));
        assertEquals("101", resolver.resolveOwn("days_until_next_event", "world"));
        assertEquals("-", resolver.resolveOwn("active_events_doom", null));
        assertEquals("-", resolver.resolveOwn("next_event_doom", null));
        // a disabled world as with days_until_next_season
        assertEquals("DISABLED", resolver.resolveOwn("days_until_next_event_doom", null));
    }

    @Test
    void withoutEventServiceNothingRuns() {
        resolver.setEvents(null);
        assertEquals("-", resolver.resolveOwn("active_events", "world"));
        assertEquals("-", resolver.resolveOwn("next_event", "world"));
        assertEquals("-", resolver.resolveOwn("days_until_next_event", "world"));
    }
}
