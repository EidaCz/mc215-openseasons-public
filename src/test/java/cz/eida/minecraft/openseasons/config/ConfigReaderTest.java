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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigReaderTest {

    private final List<String> warnings = new ArrayList<>();

    private ConfigReader reader(String yaml) throws ConfigException {
        return new ConfigReader(YamlFiles.parse(yaml, "test.yml"), "test.yml", warnings);
    }

    @Test
    void valueFormats() throws ConfigException {
        ConfigReader r = reader("""
                a: 5.0%
                b: 4%
                c: 0.5
                list1: 'OAK_LEAVES, BIRCH_LEAVES'
                list2: [ none ]
                list3:
                  - x
                  - NONE
                seasons: WINTER, fall
                sub: 0, 2, 4
                season: NONE
                color: '#32CD32'
                bad: abc
                """);
        assertEquals(5.0, r.percent("a", 0));
        assertEquals(4.0, r.percent("b", 0));
        assertEquals(0.5, r.percent("c", 0));
        assertEquals(List.of("OAK_LEAVES", "BIRCH_LEAVES"), r.list("list1", List.of()));
        assertEquals(List.of(), r.list("list2", List.of("default")));
        assertEquals(List.of("x"), r.list("list3", List.of()));
        assertEquals(Set.of(Season.WINTER, Season.FALL), r.seasons("seasons", Set.of()));
        assertEquals(Set.of(SubSeason.START, SubSeason.MIDDLE, SubSeason.END), r.subSeasons("sub", Set.of()));
        assertEquals(Optional.empty(), r.optionalSeason("season"));
        assertEquals(OptionalInt.of(0x32CD32), r.color("color"));
        assertTrue(warnings.isEmpty(), warnings::toString);

        assertEquals(7, r.integer("bad", 7));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("bad"));
    }

    @Test
    void reportsUnknownKeysButNotIgnoredOnes() throws ConfigException {
        ConfigReader r = reader("""
                known: true
                typo-key: 1
                old:
                  nested: 2
                section:
                  known: 1
                  extra: 2
                """);
        r.bool("known", false);
        r.integer("section.known", 0);
        r.reportUnknown(Set.of("old"), null);
        assertEquals(2, warnings.size(), warnings::toString);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("typo-key")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("section.extra")));
    }

    @Test
    void multiLineTextFromList() throws ConfigException {
        ConfigReader r = reader("""
                msg:
                  - first
                  - second
                """);
        assertEquals("first\nsecond", r.text("msg", ""));
    }

    @Test
    void bomIsStripped() throws ConfigException {
        ConfigReader r = reader("﻿modify-blocks: false\n");
        assertEquals(false, r.bool("modify-blocks", true));
    }
}
