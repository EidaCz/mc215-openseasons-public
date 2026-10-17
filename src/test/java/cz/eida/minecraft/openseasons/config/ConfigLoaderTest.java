/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path plugins;

    private Path own() {
        return plugins.resolve("OpenSeasons");
    }

    private void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void freshInstallCreatesOwnFiles() throws ConfigException {
        LoadedConfig c = new ConfigLoader(own()).load();
        assertTrue(Files.exists(own().resolve("config.yml")));
        assertTrue(Files.exists(own().resolve("calendar.yml")));
        assertTrue(Files.exists(own().resolve("lang.yml")));
        assertTrue(Files.exists(own().resolve("names.yml")));
        assertEquals("January", c.calendar().calendar().month(1).name());
        assertEquals("December", c.calendar().calendar().month(12).name());
        assertFalse(c.plugin().debugEnabled(), "debug commands are off by default");
        assertTrue(c.warnings().isEmpty(), c.warnings()::toString);
        assertEquals("Summer", c.lang().seasonDisplayName(Season.SUMMER));
        assertEquals(12, c.calendar().calendar().months().size());
    }

    @Test
    void ownFilesAndNamesOverlay() throws ConfigException, IOException {
        write(own().resolve("config.yml"), """
                config-version: 1
                debug-enabled: true
                modify-blocks: false
                flower-chance-in-spring: 3.5%
                entities:
                  winter:
                    spawn-wolves: true
                """);
        write(own().resolve("calendar.yml"), """
                config-version: 1
                week-days: [ Mon, Tue ]
                winter-start: December 4
                spring-start: March 4
                summer-start: June 4
                fall-start: September 4
                months:
                  January: { days: 31, day-length-in-min: 7, night-length-in-min: 13 }
                  February: { days: 28, day-length-in-min: 8, night-length-in-min: 12 }
                  March: { days: 31, day-length-in-min: 9, night-length-in-min: 11 }
                  April: { days: 30, day-length-in-min: 10, night-length-in-min: 10 }
                  May: { days: 31, day-length-in-min: 11, night-length-in-min: 9 }
                  June: { days: 30, day-length-in-min: 12, night-length-in-min: 8 }
                  July: { days: 31, day-length-in-min: 13, night-length-in-min: 7 }
                  August: { days: 31, day-length-in-min: 12, night-length-in-min: 8 }
                  September: { days: 30, day-length-in-min: 11, night-length-in-min: 9 }
                  October: { days: 31, day-length-in-min: 10, night-length-in-min: 10 }
                  November: { days: 30, day-length-in-min: 9, night-length-in-min: 11 }
                  December: { days: 31, day-length-in-min: 8, night-length-in-min: 12 }
                """);
        write(own().resolve("lang.yml"), """
                config-version: 1
                SUMMER-DISPLAY-NAME: High summer
                SEASON-TO-FALL:
                  - first
                  - second
                """);
        write(own().resolve("names.yml"), """
                config-version: 1
                months:
                  july: Julius
                seasons:
                  WINTER: Frost
                """);

        LoadedConfig c = new ConfigLoader(own()).load();
        assertTrue(c.warnings().isEmpty(), c.warnings()::toString);
        assertEquals(own().resolve("config.yml"), c.sources().get("config"));
        assertEquals(own().resolve("names.yml"), c.sources().get("names"));
        assertTrue(c.plugin().debugEnabled());
        assertFalse(c.main().modifyBlocks());
        assertEquals(3.5, c.main().flora().flowerChanceInSpring());
        assertTrue(c.main().entities().winter().wolves());
        // the days Mon/Tue are not in the overlay and stay; July is shown by the overlay
        assertEquals(List.of("Mon", "Tue"), c.calendar().calendar().weekdays());
        assertEquals("Julius", c.calendar().calendar().month(7).name());
        assertEquals("June", c.calendar().calendar().month(6).name());
        assertEquals("High summer", c.lang().seasonDisplayName(Season.SUMMER));
        assertEquals("Frost", c.lang().seasonDisplayName(Season.WINTER));
        assertEquals("first\nsecond", c.lang().seasonStartMessage(Season.FALL));
    }

    @Test
    void typoInOwnConfigIsReported() throws ConfigException, IOException {
        write(own().resolve("config.yml"), "config-version: 1\nmodify-blokcs: false\n");
        LoadedConfig c = new ConfigLoader(own()).load();
        assertEquals(1, c.warnings().size(), c.warnings()::toString);
        assertTrue(c.warnings().getFirst().contains("modify-blokcs"));
    }

    @Test
    void newerConfigVersionIsFatal() throws IOException {
        write(own().resolve("config.yml"), "config-version: 99\n");
        assertThrows(ConfigException.class, () -> new ConfigLoader(own()).load());
    }

    @Test
    void migratorRunsStepsInOrder() throws ConfigException {
        Consumer<YamlConfiguration> renameAB = y -> {
            y.set("b", y.get("a"));
            y.set("a", null);
        };
        Consumer<YamlConfiguration> renameBC = y -> {
            y.set("c", y.get("b"));
            y.set("b", null);
        };
        ConfigMigrator m = new ConfigMigrator("x.yml", 3, List.of(
                new ConfigMigrator.Migration(2, "b → c", renameBC),
                new ConfigMigrator.Migration(1, "a → b", renameAB)));
        YamlConfiguration yaml = YamlFiles.parse("config-version: 1\na: 5\n", "x.yml");
        assertTrue(m.migrate(yaml));
        assertEquals(3, yaml.getInt("config-version"));
        assertEquals(5, yaml.getInt("c"));
        assertFalse(m.migrate(yaml));

        ConfigMigrator gap = new ConfigMigrator("y.yml", 2, List.of());
        assertThrows(ConfigException.class, () -> gap.migrate(YamlFiles.parse("config-version: 1\n", "y.yml")));
    }
}
