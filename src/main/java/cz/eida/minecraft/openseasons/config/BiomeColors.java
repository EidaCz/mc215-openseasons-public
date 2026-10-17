/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import java.util.List;
import java.util.OptionalInt;

/**
 * Colours of a biome group in one season ({@code colors.<season>} in a biome file). An empty value
 * ({@code NONE}) leaves the biome colour unchanged.
 *
 * @param foliage leaves ({@code treecolor}): none, one, or five colours for patches across a forest
 */
public record BiomeColors(OptionalInt sky, OptionalInt water, OptionalInt waterFog, OptionalInt grass,
                          List<Integer> foliage, OptionalInt fog) {

    public static final BiomeColors NONE = new BiomeColors(OptionalInt.empty(), OptionalInt.empty(),
            OptionalInt.empty(), OptionalInt.empty(), List.of(), OptionalInt.empty());

    /** number of leaf colours for patches */
    public static final int FOLIAGE_VARIANTS = 5;

    public BiomeColors {
        foliage = List.copyOf(foliage);
        if (foliage.size() != 0 && foliage.size() != 1 && foliage.size() != FOLIAGE_VARIANTS) {
            throw new IllegalArgumentException("leaves have 0, 1 or " + FOLIAGE_VARIANTS + " colours, not " + foliage.size());
        }
    }

    public boolean isEmpty() {
        return sky.isEmpty() && water.isEmpty() && waterFog.isEmpty() && grass.isEmpty() && foliage.isEmpty()
                && fog.isEmpty();
    }
}
