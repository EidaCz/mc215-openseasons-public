/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.MonthDay;
import cz.eida.minecraft.openseasons.season.Season;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamesOverlayTest {

    private final List<String> warnings = new ArrayList<>();

    private NamesOverlay parse(String yaml) throws ConfigException {
        ConfigReader r = new ConfigReader(YamlFiles.parse(yaml, "names.yml"), "names.yml", warnings);
        NamesOverlay o = NamesOverlay.parse(r);
        r.reportUnknown(Set.of(), null);
        return o;
    }

    private CalendarSettings calendar(String yaml) throws ConfigException {
        return CalendarSettings.parse(new ConfigReader(YamlFiles.parse(yaml, "calendar.yml"), "calendar.yml", warnings));
    }

    @Test
    void bundledOverlayIsEmpty() throws ConfigException {
        ConfigReader r = new ConfigReader(YamlFiles.loadResource("names.yml"), "names.yml", warnings);
        r.integer("config-version", 1);
        NamesOverlay names = NamesOverlay.parse(r);
        r.reportUnknown(Set.of(), null);
        assertTrue(warnings.isEmpty(), warnings::toString);
        CalendarSettings plain = CalendarSettings.parse(
                new ConfigReader(YamlFiles.loadResource("calendar.yml"), "calendar.yml", warnings));
        assertEquals(plain, names.applyTo(plain));
    }

    @Test
    void overlayRenamesForDisplayOnly() throws ConfigException {
        NamesOverlay names = parse("""
                months:
                  july: Midsummer
                seasons:
                  SUMMER: High summer
                  FALL: Autumn
                """);
        CalendarSettings plain = CalendarSettings.parse(
                new ConfigReader(YamlFiles.loadResource("calendar.yml"), "calendar.yml", warnings));
        CalendarSettings shown = names.applyTo(plain);
        CalendarDate date = new CalendarDate(8, 7, 453);
        assertEquals("July", plain.calendar().monthName(date));
        assertEquals("Midsummer", shown.calendar().monthName(date));
        // the structure of the calendar does not change
        assertEquals(plain.calendar().daysInYear(), shown.calendar().daysInYear());
        assertEquals(plain.calendar().weekdayIndex(date), shown.calendar().weekdayIndex(date));
        assertEquals(new MonthDay(6, 4), shown.calendar().start(Season.SUMMER));
        assertEquals(Season.SUMMER, shown.calendar().seasonAt(date));

        LangConfig lang = names.applyTo(new LangConfig(Map.of("SUMMER-DISPLAY-NAME", "&e&lSummer")));
        assertEquals("High summer", lang.seasonDisplayName(Season.SUMMER));
        assertEquals("Autumn", lang.seasonDisplayName(Season.FALL));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void matchesByNameAndLeavesCustomCalendarAlone() throws ConfigException {
        NamesOverlay names = parse("""
                months:
                  january: First month
                  Frostmonth: Frost
                weekdays:
                  MONDAY: Moonday
                """);
        CalendarSettings custom = names.applyTo(calendar("""
                week-days: [ Monday, Morning ]
                winter-start: Frostmonth 1
                spring-start: Budmonth 1
                summer-start: Budmonth 10
                fall-start: Budmonth 15
                months:
                  Frostmonth: { days: 20, day-length-in-min: 6, night-length-in-min: 14 }
                  Budmonth: { days: 20, day-length-in-min: 10, night-length-in-min: 10 }
                """));
        assertEquals("Frost", custom.calendar().month(1).name());
        assertEquals("Budmonth", custom.calendar().month(2).name());
        assertEquals(List.of("Moonday", "Morning"), custom.calendar().weekdays());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void emptyOverlayChangesNothingAndBadKeysWarn() throws ConfigException {
        NamesOverlay none = parse("seasons:\n  AUTUMN: Autumn\n  NOPE: x\n  WINTER: ''\n");
        assertEquals(Map.of(Season.FALL, "Autumn"), none.seasons());
        assertEquals(2, warnings.size(), warnings::toString);
        CalendarSettings plain = calendar("{}");
        assertEquals(plain, NamesOverlay.NONE.applyTo(plain));
        LangConfig lang = new LangConfig(Map.of());
        assertEquals(lang, NamesOverlay.NONE.applyTo(lang));
        assertEquals(new YamlConfiguration().getKeys(false), Set.of());
    }
}
