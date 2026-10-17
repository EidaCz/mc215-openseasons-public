/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One biome file ({@code biomes/<name>.yml}): which biomes it includes and how they look on the client
 * in the seasons.
 *
 * @param name      file name without the extension
 * @param biomes    biome keys {@code namespace:path}; empty for {@code includes: ALL}
 * @param allCustom {@code includes: ALL}: all custom biomes no other file covers
 */
public record BiomeGroup(String name, Set<String> biomes, boolean allCustom,
                         Map<Season, Boolean> snowInsteadOfRain, Map<Season, BiomeColors> colors) {

    public BiomeGroup {
        biomes = Set.copyOf(biomes);
        snowInsteadOfRain = Map.copyOf(snowInsteadOfRain);
        colors = Map.copyOf(colors);
    }

    public BiomeColors colors(Season season) {
        return colors.getOrDefault(season, BiomeColors.NONE);
    }

    /** some season changes colours */
    public boolean changesColors() {
        return colors.values().stream().anyMatch(c -> !c.isEmpty());
    }

    /** in the season it snows instead of raining on the client ({@code show-snow-instead-of-rain.<season>}) */
    public boolean snows(Season season) {
        return snowInsteadOfRain.getOrDefault(season, false);
    }

    /** the group changes biomes on the client: colours or snow instead of rain */
    public boolean changesClient() {
        return changesColors() || snowInsteadOfRain.containsValue(true);
    }

    /**
     * @param vanillaDefault biomes by file name when the file has no {@code includes}
     *                       (files of vanilla groups)
     */
    static BiomeGroup parse(String name, ConfigReader r, Set<String> vanillaDefault) {
        List<String> includes = r.list("includes", List.of());
        boolean all = includes.size() == 1 && includes.getFirst().equalsIgnoreCase("ALL");
        Set<String> biomes = new LinkedHashSet<>();
        if (!all) {
            for (String id : includes) biomes.add(normalizeKey(id));
            if (includes.isEmpty()) {
                biomes.addAll(vanillaDefault);
                if (vanillaDefault.isEmpty()) r.warn("includes", "the file names no biomes and its name is no known vanilla biome group");
            }
        }

        Map<Season, Boolean> snow = new EnumMap<>(Season.class);
        Map<Season, BiomeColors> colors = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            snow.put(s, r.bool("show-snow-instead-of-rain." + s.key(), s == Season.WINTER));
            String p = "colors." + s.key() + ".";
            colors.put(s, new BiomeColors(
                    color(r, p + "skycolor"),
                    color(r, p + "watercolor"),
                    color(r, p + "waterfogcolor"),
                    color(r, p + "grasscolor"),
                    foliage(r, p + "treecolor"),
                    color(r, p + "fogcolor")));
        }
        return new BiomeGroup(name, biomes, all, snow, colors);
    }

    /** {@code PLAINS} → {@code minecraft:plains}, {@code terralith:cave/deep_caves} unchanged */
    static String normalizeKey(String id) {
        String k = id.trim().toLowerCase(Locale.ROOT);
        return k.indexOf(':') < 0 ? "minecraft:" + k : k;
    }

    private static OptionalInt color(ConfigReader r, String path) {
        String v = r.string(path, null);
        return v == null ? OptionalInt.empty() : parseColor(r, path, v);
    }

    /** one colour, or five comma-separated colours; any other count is cut to the first */
    private static List<Integer> foliage(ConfigReader r, String path) {
        String v = r.string(path, null);
        if (v == null) return List.of();
        List<Integer> out = new ArrayList<>();
        for (String part : v.split(",")) {
            OptionalInt c = parseColor(r, path, part);
            if (c.isEmpty()) return List.of();
            out.add(c.getAsInt());
        }
        if (out.size() != 1 && out.size() != BiomeColors.FOLIAGE_VARIANTS) {
            r.warn(path, "expected one or " + BiomeColors.FOLIAGE_VARIANTS + " colours, using the first");
            return List.of(out.getFirst());
        }
        return out;
    }

    /**
     * Colour {@code RRGGBB} (with or without {@code #}). A shorter value is padded with zeros on the left:
     * YAML reads {@code 20827} as a number ({@code #020827}).
     */
    static OptionalInt parseColor(ConfigReader r, String path, String text) {
        String s = text.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) return OptionalInt.empty();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.isEmpty() && s.length() <= 6) {
            try {
                return OptionalInt.of(Integer.parseInt(s, 16));
            } catch (NumberFormatException ignored) {
                // falls through to the warning
            }
        }
        r.warn(path, "invalid colour '" + text.trim() + "', expected RRGGBB or NONE");
        return OptionalInt.empty();
    }
}
