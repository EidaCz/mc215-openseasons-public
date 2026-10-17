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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFilesTest {

    @TempDir
    Path dir;

    private static final java.util.function.Predicate<String> ANY_ITEM = s -> !s.isBlank();

    private EventSettings load(Path dataFolder) throws ConfigException {
        LoadedConfig config = new ConfigLoader(dataFolder).load();
        return EventFiles.load(dataFolder, config.calendar(), ANY_ITEM);
    }

    @Test
    void bundledDefaults() throws ConfigException {
        Path own = dir.resolve("OpenSeasons");
        EventSettings s = load(own);
        assertTrue(Files.isRegularFile(own.resolve(EventFiles.EVENTS)));
        assertTrue(Files.isRegularFile(own.resolve(EventFiles.CUSTOM)));
        assertEquals(List.of(), s.warnings());

        assertEquals(List.of("christmas", "newyear", "halloween", "easter", "spooky-october", "quiet-sunday",
                "morning-news"), s.events().stream().map(SeasonEvent::id).toList());
        SeasonEvent xmas = s.event("christmas").orElseThrow();
        assertEquals("&cChristmas", xmas.name());
        assertTrue(xmas.enabled() && xmas.display());
        assertEquals(new EventSchedule.Dated(new EventDate(25, 12, 0), new EventDate(28, 12, 0)), xmas.schedule());
        assertEquals(List.of("&cChristmas &ahas begun!", "&aPresents appear in villages and around them, just unwrap them."),
                xmas.startCommands());
        assertEquals(List.of(), xmas.stopCommands());
        assertEquals(new EventSchedule.Dated(new EventDate(1, 1, 0), new EventDate(1, 1, 0)),
                s.event("newyear").orElseThrow().schedule());
        assertEquals(new EventSchedule.Dated(new EventDate(31, 10, 0), new EventDate(2, 11, 0)),
                s.event("halloween").orElseThrow().schedule());
        assertEquals(new EventSchedule.Dated(new EventDate(20, 4, 0), new EventDate(24, 4, 0)),
                s.event("easter").orElseThrow().schedule());

        assertEquals(new EventSettings.Christmas(true, true, true, 10, s.christmas().presentLoot()), s.christmas());
        assertEquals(32, s.christmas().presentLoot().entries().size());
        assertEquals(new EventSettings.NewYear(true, 34), s.newYear());
        assertEquals(EventSettings.Halloween.DEFAULT, s.halloween());
        assertEquals(0.1, s.easter().killerBunnyRate());
        assertEquals(4, s.easter().eggSpacing());
        assertEquals(28, s.easter().eggLoot().entries().size());
        assertEquals(new LootTable.Entry(3, 6, "COOKIE"), s.easter().eggLoot().entries().getFirst());

        // the custom event examples are turned off
        assertTrue(s.events().stream().filter(e -> e.type() == SeasonEvent.Type.CUSTOM).noneMatch(SeasonEvent::enabled));
        SeasonEvent sunday = s.event("quiet-sunday").orElseThrow();
        assertEquals(new EventSchedule.Weekly(6), sunday.schedule());
        assertTrue(s.event("morning-news").orElseThrow().daily());
    }

    @Test
    void badValuesWarnAndFallBack() throws IOException, ConfigException {
        Path own = dir.resolve("OpenSeasons");
        load(own);
        Path file = own.resolve(EventFiles.EVENTS);
        String text = Files.readString(file)
                .replace("date: 25/12", "date: 25.12")
                .replace("spawn-chance: 0.3\n", "spawn-chance: 3\n")
                .replace("- 1:DIAMOND\n", "- 1:DIAMONDS\n");
        Files.writeString(file, text + "\nfoo: 1\n");
        Path customFile = own.resolve(EventFiles.CUSTOM);
        Files.writeString(customFile, Files.readString(customFile).replace("day: Sunday", "day: Holiday"));
        LoadedConfig config = new ConfigLoader(own).load();
        EventSettings s = EventFiles.load(own, config.calendar(), EventService::knownItem);
        List<String> w = s.warnings();
        assertTrue(w.stream().anyMatch(x -> x.contains("event-start.date") && x.contains("25.12")), w.toString());
        assertTrue(w.stream().anyMatch(x -> x.contains("DIAMONDS")), w.toString());
        assertTrue(w.stream().anyMatch(x -> x.contains("spawn-chance") && x.contains("is not between 0 and 1")), w.toString());
        assertTrue(w.stream().anyMatch(x -> x.contains("foo") && x.contains("unknown key")), w.toString());
        assertTrue(w.stream().anyMatch(x -> x.contains("Holiday")), w.toString());
        // a wrong date: the default 25/12
        assertTrue(s.event("christmas").orElseThrow().schedule().activeOn(new CalendarDate(25, 12, 1),
                config.calendar().calendar()));
        assertTrue(s.event("quiet-sunday").isEmpty());
        assertEquals(31, s.christmas().presentLoot().entries().size());
    }

    @Test
    void weekdayNamesNumbersAndEnglish() throws ConfigException {
        var cal = new ConfigLoader(dir.resolve("OpenSeasons")).load().calendar().calendar();
        assertEquals(6, EventFiles.weekday("sunday", cal).getAsInt());
        assertEquals(0, EventFiles.weekday("Monday", cal).getAsInt());
        assertEquals(2, EventFiles.weekday("3", cal).getAsInt());
        assertTrue(EventFiles.weekday("8", cal).isEmpty());
        assertTrue(EventFiles.weekday("Mondey", cal).isEmpty());
    }
}
