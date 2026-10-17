/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLinesTest {

    @Test
    void slashIsConsoleCommandOtherwiseMessageAndNoneSkipped() {
        List<EventLines.Line> lines = EventLines.parse(List.of(
                "&cChristmas &ahas begun!", "/difficulty hard", "NONE", "none", "", "  /  say hello ", "/", "  &a indented"));
        assertEquals(List.of(
                new EventLines.Line(EventLines.Kind.MESSAGE, "&cChristmas &ahas begun!"),
                new EventLines.Line(EventLines.Kind.COMMAND, "difficulty hard"),
                new EventLines.Line(EventLines.Kind.COMMAND, "say hello"),
                new EventLines.Line(EventLines.Kind.MESSAGE, "  &a indented")), lines);
    }

    @Test
    void fillsEventPlaceholdersAndLeavesOthersForPlaceholderApi() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("day", "25");
        v.put("month", "12");
        v.put("month_asname", "December");
        v.put("year", "449");
        v.put("weekday", "Thursday");
        v.put("season", "Winter");
        v.put("world", "world");
        assertEquals(EventLines.PLACEHOLDERS, List.copyOf(v.keySet()));
        assertEquals("It is Thursday, December 25 (12) 449, Winter in world, %player_name%",
                EventLines.fill("It is %weekday%, %month_asname% %day% (%month%) %year%, %season% in %world%, %player_name%", v));
    }

    @Test
    void lootParsesRangesSingleAmountsAndProductionQuirks() {
        List<String> problems = new ArrayList<>();
        Set<String> items = Set.of("COOKIE", "DIAMOND", "GOLDEN_APPLE", "POTATO");
        LootTable loot = LootTable.parse(List.of("6-3:COOKIE", "1:DIAMOND", "2-16:golden_apple", "minecraft:potato",
                        "2-8:NONEXISTENT", "x-2:COOKIE", "no colon", "0-2:POTATO"),
                items::contains, (line, problem) -> problems.add(line + ": " + problem));
        assertEquals(List.of(
                new LootTable.Entry(3, 6, "COOKIE"),
                new LootTable.Entry(1, 1, "DIAMOND"),
                new LootTable.Entry(2, 16, "GOLDEN_APPLE"),
                new LootTable.Entry(1, 2, "POTATO")), loot.entries());
        // minecraft:potato has no count, NONEXISTENT does not exist, x-2 is no number, no colon, 0 is raised to 1
        assertEquals(5, problems.size(), problems.toString());

        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        for (int i = 0; i < 200; i++) {
            int n = new LootTable.Entry(3, 6, "COOKIE").roll(random);
            assertTrue(n >= 3 && n <= 6, "count " + n);
        }
        assertEquals(1, new LootTable.Entry(1, 1, "DIAMOND").roll(random));
    }

    @Test
    void defaultLootHasAllProductionLines() {
        // default events.yml: 32 present items, 28 egg items
        assertEquals(32, EventFiles.DEFAULT_PRESENT_LOOT.size());
        assertEquals(28, EventFiles.DEFAULT_EGG_LOOT.size());
        LootTable presents = LootTable.parse(EventFiles.DEFAULT_PRESENT_LOOT, EventService::knownItem, (l, p) -> {
            throw new AssertionError(l + ": " + p);
        });
        assertEquals(32, presents.entries().size());
        LootTable eggs = LootTable.parse(EventFiles.DEFAULT_EGG_LOOT, EventService::knownItem, (l, p) -> {
            throw new AssertionError(l + ": " + p);
        });
        assertEquals(28, eggs.entries().size());
    }
}
