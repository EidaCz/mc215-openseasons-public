/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeFilesTest {

    @TempDir
    Path dir;

    @Test
    void defaultsAreCopiedOnceAndParseWithoutWarnings() throws Exception {
        Path biomes = dir.resolve("biomes");
        BiomeFiles.copyDefaultsIfMissing(biomes);
        List<String> warnings = new ArrayList<>();
        BiomeConfig config = BiomeFiles.load(biomes, warnings);

        assertEquals(List.of(), warnings);
        assertEquals(56, config.groups().size());
        BiomeGroup forest = config.groupOf("minecraft:forest").orElseThrow();
        assertEquals("FOREST", forest.name());
        assertEquals(List.of(0xE8962A, 0xE8962A, 0xAEA42A, 0x6BA941, 0x60A17B), forest.colors(Season.FALL).foliage());
        assertEquals(OptionalInt.of(0xB8D0E1), forest.colors(Season.WINTER).grass());
        assertEquals(OptionalInt.empty(), forest.colors(Season.SUMMER).grass());
        assertEquals("TERRALITH_CAVES", config.groupOf("terralith:cave/deep_caves").orElseThrow().name());
        // a custom biome without a file gets includes: ALL, vanilla does not
        assertEquals("CUSTOM_ALL", config.groupOf("other:biome").orElseThrow().name());
        assertTrue(config.groupOf("minecraft:deep_dark").isEmpty());

        // a deleted file does not come back, the folder exists already
        Files.delete(biomes.resolve("FOREST.yml"));
        BiomeFiles.copyDefaultsIfMissing(biomes);
        assertFalse(Files.exists(biomes.resolve("FOREST.yml")));
    }

    @Test
    void fileWithoutIncludesUsesVanillaGroupAndShortColors() throws Exception {
        Path biomes = Files.createDirectories(dir.resolve("custom"));
        Files.writeString(biomes.resolve("PLAINS.yml"), """
                show-snow-instead-of-rain:
                  winter: false
                colors:
                  summer:
                    waterfogcolor: 20827
                    treecolor: 'E8962A, AEA42A'
                  winter:
                    grasscolor: '#b8d0e1'
                    skycolor: NONE
                """);
        Files.writeString(biomes.resolve("CUSTOM.yml"), "includes: ALL\n");
        Files.writeString(biomes.resolve("MINE.yml"), "includes: Other:Biome, PLAINS\n");
        List<String> warnings = new ArrayList<>();
        BiomeConfig config = BiomeFiles.load(biomes, warnings);

        BiomeGroup plains = config.groupOf("minecraft:sunflower_plains").orElseThrow();
        assertEquals("PLAINS", plains.name());
        assertFalse(plains.snows(Season.WINTER));
        assertEquals(OptionalInt.of(0x020827), plains.colors(Season.SUMMER).waterFog());
        assertEquals(List.of(0xE8962A), plains.colors(Season.SUMMER).foliage());
        assertEquals(OptionalInt.of(0xB8D0E1), plains.colors(Season.WINTER).grass());
        assertTrue(plains.colors(Season.FALL).isEmpty());
        // a file naming the biome wins over ALL; the first file in alphabetical order over the next one
        assertEquals("MINE", config.groupOf("other:biome").orElseThrow().name());
        assertEquals("MINE", config.groupOf("minecraft:plains").orElseThrow().name());
        assertEquals("CUSTOM", config.groupOf("other:next").orElseThrow().name());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("treecolor"));
    }

    @Test
    void autumnLeavesAndMorningFogSettings() {
        List<String> warnings = new ArrayList<>();
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        assertEquals(ClientColorSettings.DEFAULTS, ClientColorSettings.parse(new ConfigReader(yaml, "x", warnings)));
        yaml.set("client-colors.autumn-leaves", "NONE");
        yaml.set("client-colors.morning-fog.chance", "10%");
        yaml.set("client-colors.morning-fog.height", 100);
        ClientColorSettings s = ClientColorSettings.parse(new ConfigReader(yaml, "x", warnings));
        assertEquals(List.of(), s.autumnLeaves());
        assertEquals(10.0, s.morningFog().chance());
        assertEquals(64, s.morningFog().height());
        yaml.set("client-colors.autumn-leaves", "111111, 222222");
        assertEquals(ClientColorSettings.DEFAULT_AUTUMN_LEAVES,
                ClientColorSettings.parse(new ConfigReader(yaml, "x", warnings)).autumnLeaves());
        assertEquals(List.of(), warnings.subList(0, 0));
        assertTrue(warnings.getLast().contains("autumn-leaves"));
    }

    @Test
    void unknownGroupWithoutIncludesWarns() throws IOException, ConfigException {
        Path biomes = Files.createDirectories(dir.resolve("x"));
        Files.writeString(biomes.resolve("UNKNOWN.yml"), "show-snow-instead-of-rain:\n  winter: true\nnew-key: 1\n");
        List<String> warnings = new ArrayList<>();
        BiomeConfig config = BiomeFiles.load(biomes, warnings);
        assertEquals(Set.of(), config.groups().getFirst().biomes());
        assertEquals(2, warnings.size(), warnings.toString());
    }
}
