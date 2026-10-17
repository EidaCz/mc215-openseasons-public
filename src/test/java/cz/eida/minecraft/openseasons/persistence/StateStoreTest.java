/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.persistence;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendarTestAccess;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    @TempDir
    Path dir;

    @Test
    void roundTrip() throws IOException, ConfigException {
        StateStore store = new StateStore(dir.resolve("data/worlds.yml"));
        Map<String, WorldSeasonState> worlds = new LinkedHashMap<>();
        worlds.put("world", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(12, 7, 453), OptionalLong.of(0)));
        worlds.put("doom", new WorldSeasonState(false, Season.FALL, new CalendarDate(27, 10, 129), OptionalLong.of(-42)));
        worlds.put("new", new WorldSeasonState(true, Season.WINTER, new CalendarDate(1, 1, 1)));
        store.save(new StateStore.Snapshot(worlds, StateStore.Clock.REAL_DAYS));

        List<String> warnings = new ArrayList<>();
        StateStore.Snapshot loaded = store.load(warnings);
        assertEquals(worlds, loaded.worlds());
        assertEquals(StateStore.Clock.REAL_DAYS, loaded.clock());
        assertTrue(warnings.isEmpty());
        try (var files = Files.list(dir.resolve("data"))) {
            assertEquals(1, files.count(), "no temporary file is left after writing");
        }
    }

    @Test
    void pausedWorldsRoundTrip() throws IOException, ConfigException {
        Path file = dir.resolve("data/worlds.yml");
        StateStore store = new StateStore(file);
        Map<String, WorldSeasonState> worlds = Map.of(
                "world", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(12, 7, 453), OptionalLong.of(0)));
        store.save(new StateStore.Snapshot(worlds, StateStore.Clock.GAME, Set.of("world", "lobby")));
        assertTrue(Files.readString(file).contains("time-paused:"));

        StateStore.Snapshot loaded = store.load(new ArrayList<>());
        assertEquals(Set.of("world", "lobby"), loaded.timePaused());
        assertEquals(worlds, loaded.worlds());
    }

    @Test
    void fileWithoutPausedWorldsHasNone() throws IOException, ConfigException {
        Path file = dir.resolve("worlds.yml");
        Files.writeString(file, """
                format: 1
                clock: game
                worlds:
                  world:
                    enabled: true
                    season: SUMMER
                    date: {day: 12, month: 7, year: 453}
                    day-offset: 0
                """);
        StateStore.Snapshot loaded = new StateStore(file).load(new ArrayList<>());
        assertTrue(loaded.timePaused().isEmpty());
        assertEquals(1, loaded.worlds().size());
    }

    @Test
    void missingFileIsEmpty() throws ConfigException {
        StateStore.Snapshot s = new StateStore(dir.resolve("none.yml")).load(new ArrayList<>());
        assertTrue(s.worlds().isEmpty());
        assertEquals(StateStore.Clock.GAME, s.clock());
    }

    @Test
    void brokenFileIsCopiedAndFatal() throws IOException {
        Path file = dir.resolve("worlds.yml");
        Files.writeString(file, "worlds: [ : :\n");
        assertThrows(ConfigException.class, () -> new StateStore(file).load(new ArrayList<>()));
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("worlds.yml.broken-")));
        }
    }
}
