/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Loot of presents and eggs: lines {@code min-max:MATERIAL} or {@code count:MATERIAL}.
 * An opened present drops one random item with a random count in the range.
 */
public record LootTable(List<Entry> entries) {

    public static final LootTable EMPTY = new LootTable(List.of());

    /** @param material material name in upper case ({@code GOLDEN_APPLE}) */
    public record Entry(int min, int max, String material) {

        public Entry {
            if (min < 1 || max < min) throw new IllegalArgumentException("count " + min + "-" + max);
        }

        public int roll(RandomGenerator random) {
            return min == max ? min : min + random.nextInt(max - min + 1);
        }
    }

    public LootTable {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Entry pick(RandomGenerator random) {
        return entries.get(random.nextInt(entries.size()));
    }

    /**
     * Loot lines. A reversed range ({@code 6-3}) is turned around, a count below 1
     * is raised to 1; an unknown material is skipped with a warning.
     *
     * @param known  the material exists and is an item
     * @param warn   (line, problem)
     */
    public static LootTable parse(List<String> lines, Predicate<String> known, BiConsumer<String, String> warn) {
        List<Entry> out = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1) {
                warn.accept(raw, "expected min-max:MATERIAL or count:MATERIAL");
                continue;
            }
            String amount = line.substring(0, colon).trim();
            String material = line.substring(colon + 1).trim().toUpperCase(Locale.ROOT);
            if (material.startsWith("MINECRAFT:")) material = material.substring("MINECRAFT:".length());
            int min;
            int max;
            try {
                int dash = amount.indexOf('-');
                if (dash < 0) {
                    min = max = Integer.parseInt(amount);
                } else {
                    min = Integer.parseInt(amount.substring(0, dash).trim());
                    max = Integer.parseInt(amount.substring(dash + 1).trim());
                }
            } catch (NumberFormatException e) {
                warn.accept(raw, "count '" + amount + "' is neither a number nor a range");
                continue;
            }
            if (min > max) {
                int t = min;
                min = max;
                max = t;
            }
            if (min < 1) {
                warn.accept(raw, "count must be at least 1");
                min = Math.max(1, min);
                max = Math.max(min, max);
            }
            if (!known.test(material)) {
                warn.accept(raw, "unknown item " + material + ", skipped");
                continue;
            }
            out.add(new Entry(min, max, material));
        }
        return new LootTable(out);
    }
}
