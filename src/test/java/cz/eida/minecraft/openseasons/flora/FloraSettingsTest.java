/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.flora;

import cz.eida.minecraft.openseasons.config.ConfigReader;
import cz.eida.minecraft.openseasons.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloraSettingsTest {

    @Test
    void bundledFileMatchesDefaults() throws Exception {
        YamlConfiguration yaml = YamlFiles.loadResource(FloraSettings.FILE);
        List<String> warnings = new ArrayList<>();
        ConfigReader r = new ConfigReader(yaml, FloraSettings.FILE, warnings);
        r.integer("config-version", 1);
        FloraSettings s = FloraSettings.parse(r);
        r.reportUnknown(Set.of(), null);
        assertEquals(List.of(), warnings);
        assertEquals(FloraSettings.DEFAULTS, s);
    }

    @Test
    void createsFileAndWarnsOnBadValues(@TempDir Path dir) throws Exception {
        List<String> warnings = new ArrayList<>();
        FloraSettings.load(dir, warnings);
        assertTrue(Files.exists(dir.resolve(FloraSettings.FILE)));
        assertEquals(List.of(), warnings);

        Files.writeString(dir.resolve(FloraSettings.FILE), """
                config-version: 1
                chunk-radius: 50
                grass:
                  chance: 150%
                firefly-bush:
                  enabled: false
                flowers:
                  chance: 5%
                """);
        FloraSettings s = FloraSettings.load(dir, warnings);
        assertEquals(16, s.chunkRadius(), "radius limited");
        assertEquals(10.0, s.kind(PlantKind.GRASS).chance(), "invalid percent → default");
        assertFalse(s.kind(PlantKind.FIREFLY_BUSH).enabled());
        assertEquals(2, warnings.size(), "percent and flowers.chance (the chance is in config.yml): " + warnings);
    }
}
