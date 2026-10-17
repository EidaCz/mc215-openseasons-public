/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Landscape colour settings ({@code client-colors:} in {@code config.yml}). Complements
 * {@code modify-biome-on-client} and the biome files.
 *
 * @param autumnLeaves five colours replacing the five-colour autumn leaves of all groups; empty =
 *                     as in the biome files
 * @param springBlossom share of the spring leaf colour (blossom) in blooming patches in mid spring, percent
 * @param disabledBlockReplacements replacements from {@code block-replacements} that are not used (names)
 */
public record ClientColorSettings(List<Integer> autumnLeaves, double springBlossom, MorningFog morningFog,
                                  Set<String> disabledBlockReplacements) {

    /** default autumn leaves: brown, yellow, red, orange and still green */
    public static final List<Integer> DEFAULT_AUTUMN_LEAVES = List.of(0x8B5A2B, 0xD9A21B, 0xB83A1E, 0xE8962A, 0x6BA941);

    /** pink oaks (cherry leaves) in spring: instead only a hint of blossom in the leaf colour */
    public static final Set<String> DEFAULT_DISABLED_REPLACEMENTS = Set.of("pink-trees");

    public static final ClientColorSettings DEFAULTS = new ClientColorSettings(DEFAULT_AUTUMN_LEAVES, 85.0,
            new MorningFog(true, 35.0, 50.0, 16), DEFAULT_DISABLED_REPLACEMENTS);

    public ClientColorSettings {
        autumnLeaves = List.copyOf(autumnLeaves);
        disabledBlockReplacements = Set.copyOf(disabledBlockReplacements);
    }

    /**
     * Occasional morning fog in late fall: lies in lowlands below {@code height} blocks above sea
     * level, rises in the morning and clears before noon.
     *
     * @param chance       chance of a foggy morning, percent (once a day, the same for all players of a world)
     * @param fromProgress from which part of fall, percent
     * @param height       highest top of the fog above sea level, blocks
     */
    public record MorningFog(boolean enabled, double chance, double fromProgress, int height) {
    }

    static ClientColorSettings parse(ConfigReader r) {
        List<Integer> leaves = DEFAULT_AUTUMN_LEAVES;
        String path = "client-colors.autumn-leaves";
        if (r.has(path)) {
            List<Integer> parsed = new ArrayList<>();
            for (String part : r.list(path, List.of())) {
                OptionalInt c = BiomeGroup.parseColor(r, path, part);
                if (c.isPresent()) parsed.add(c.getAsInt());
            }
            if (parsed.isEmpty()) {
                leaves = List.of();
            } else if (parsed.size() == BiomeColors.FOLIAGE_VARIANTS) {
                leaves = parsed;
            } else {
                r.warn(path, "expected " + BiomeColors.FOLIAGE_VARIANTS + " colours or NONE, using the default");
            }
        }
        String f = "client-colors.morning-fog.";
        MorningFog fog = new MorningFog(
                r.bool(f + "enabled", true),
                r.percent(f + "chance", 35.0),
                r.percent(f + "from-season-progress", 50.0),
                Math.clamp(r.integer(f + "height", 16), 4, 64));
        Set<String> disabled = Set.copyOf(r.list("client-colors.disabled-block-replacements",
                List.copyOf(DEFAULT_DISABLED_REPLACEMENTS)));
        return new ClientColorSettings(leaves, r.percent("client-colors.spring-blossom", 85.0), fog, disabled);
    }
}
