/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Result of one configuration load.
 *
 * @param sources      where each part came from: {@code config}, {@code calendar}, {@code lang}, {@code names},
 *                     {@code biomes}
 * @param biomes       biome files (landscape colours and biome behaviour per season)
 * @param clientColors client-side landscape colour settings ({@code client-colors:} in config.yml)
 * @param warnings     non-fatal problems (unknown keys, invalid values replaced by defaults)
 */
public record LoadedConfig(
        PluginSettings plugin,
        MainConfig main,
        CalendarSettings calendar,
        LangConfig lang,
        BiomeConfig biomes,
        ClientColorSettings clientColors,
        Map<String, Path> sources,
        List<String> warnings) {
}
