/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SubSeason;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainConfigTest {

    @Test
    void bundledConfigEqualsCodeDefaults() throws ConfigException {
        List<String> warnings = new ArrayList<>();
        YamlConfiguration bundled = YamlFiles.loadResource("config.yml");
        ConfigReader reader = new ConfigReader(bundled, "config.yml", warnings);
        reader.integer("config-version", 1);
        PluginSettings.parse(reader);
        assertEquals(ClientColorSettings.DEFAULTS, ClientColorSettings.parse(reader));
        MainConfig fromFile = MainConfig.parse(reader);
        reader.reportUnknown(Set.of(), null);

        MainConfig defaults = MainConfig.parse(new ConfigReader(new YamlConfiguration(), "empty", warnings));
        assertEquals(defaults, fromFile);
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void codeDefaults() throws ConfigException {
        MainConfig c = MainConfig.parse(new ConfigReader(new YamlConfiguration(), "empty", new ArrayList<>()));
        assertEquals(2.0, c.flora().flowerChanceInSpring());
        assertEquals(10, c.flora().maxBerryBushesPerChunk());
        assertFalse(c.cycling().sendMessage());
        assertTrue(c.snowman().attackMobs());
        assertEquals(1.0, c.particles().shootingStars().chance());
        assertEquals(5.0, c.particles().fireflies().chance());
        assertFalse(c.entities().summer().ocelots());
        assertTrue(c.entities().spring().extraBees());
        assertFalse(c.entities().fall().foxes());
        assertEquals(22.0, c.entities().fall().pumpkinChance());
        assertFalse(c.entities().winter().wolves());
        assertEquals("openseasons", c.biomeDisplayName());
        assertTrue(c.particles().ufo().enabled());
        assertEquals(1.0, c.particles().ufo().chance());
        assertEquals(Set.of(Season.values()), c.particles().ufo().seasons());
        assertEquals(120, c.particles().ufo().relativeHeight());
        assertEquals(15.0, c.particles().ufo().formationChance());
        assertTrue(c.particles().aurora().enabled());
        assertEquals(20.0, c.particles().aurora().chance());
        assertEquals(Set.of(Season.WINTER), c.particles().aurora().seasons());
        assertEquals(0x32CD32, c.subSeasons().foliageAdjustments().get(Season.SPRING).rgb());
        assertEquals(0.3, c.subSeasons().foliageAdjustments().get(Season.SPRING).weight());
        assertEquals(Set.of(Season.SPRING, Season.SUMMER), c.particles().fireflies().seasons());
        assertEquals(Optional.empty(), c.newWorlds().defaultSeason());
        assertEquals(2, c.blockReplacements().replacements().size());
    }

    @Test
    void customFileReportsUnknownKeys() throws ConfigException {
        List<String> warnings = new ArrayList<>();
        YamlConfiguration yaml = YamlFiles.parse("""
                on-new-world-creation:
                  default-season: SUMMER
                flower-chance-in-spring: 3.5%
                max-berrybushes-per-chunk: 15
                spawn-leaf-litter-under: OAK_LEAVES, BIRCH_LEAVES
                entities:
                  spring:
                    spawn-extra-bees: true
                    spawn-extra-rabbits: false
                  winter:
                    spawn-wolves: false
                block-replacements:
                  enabled: true
                  replacements:
                    pink-trees:
                      enabled: true
                      to-replace: OAK_LEAVES
                      replacement: CHERRY_LEAVES
                      seasons: SPRING
                      sub-seasons: 2
                unknown-option: 1
                """, "config.yml");
        ConfigReader reader = new ConfigReader(yaml, "config.yml", warnings);
        MainConfig c = MainConfig.parse(reader);
        reader.reportUnknown(Set.of(), null);

        assertEquals(3.5, c.flora().flowerChanceInSpring());
        assertEquals(15, c.flora().maxBerryBushesPerChunk());
        assertEquals(List.of("OAK_LEAVES", "BIRCH_LEAVES"), c.flora().spawnLeafLitterUnder());
        assertFalse(c.entities().spring().extraRabbits());
        assertFalse(c.entities().winter().wolves());
        assertEquals(Optional.of(Season.SUMMER), c.newWorlds().defaultSeason());
        assertEquals(1, c.blockReplacements().replacements().size());
        assertEquals(Set.of(SubSeason.MIDDLE), c.blockReplacements().replacements().getFirst().subSeasons());
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(warnings.getFirst().contains("unknown-option"));
    }
}
