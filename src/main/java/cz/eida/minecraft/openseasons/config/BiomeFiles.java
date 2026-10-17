/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Biome files ({@code biomes/*.yml}) in the plugin folder; on the first start the default files are
 * copied there from the jar.
 */
public final class BiomeFiles {

    public static final String FOLDER = "biomes";
    /** list of the default files in the jar */
    static final String INDEX = FOLDER + "/files.txt";

    /**
     * Vanilla biome groups by file name, for files without {@code includes}. {@code PALE_GARDEN} is the
     * only biome of its group.
     */
    static final Map<String, Set<String>> VANILLA_GROUPS = Map.ofEntries(
            group("BADLANDS", "badlands", "wooded_badlands", "eroded_badlands"),
            group("BEACH", "beach", "stony_shore"),
            group("BIRCH_FOREST", "birch_forest", "old_growth_birch_forest"),
            group("CAVES", "dripstone_caves", "lush_caves"),
            group("DARK_FOREST", "dark_forest"),
            group("DESERT", "desert"),
            group("FLOWER_FOREST", "flower_forest", "meadow", "cherry_grove"),
            group("FOREST", "forest"),
            group("FROZEN_BIOMES", "snowy_beach", "snowy_taiga", "snowy_plains", "frozen_river", "ice_spikes",
                    "frozen_ocean", "deep_frozen_ocean"),
            group("FROZEN_MOUNTAINS", "jagged_peaks", "frozen_peaks", "snowy_slopes", "grove"),
            group("JUNGLE", "jungle", "bamboo_jungle", "sparse_jungle"),
            group("MOUNTAINS", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "stony_peaks"),
            group("MUSHROOM_FIELDS", "mushroom_fields"),
            group("OCEAN", "ocean", "cold_ocean", "deep_cold_ocean", "deep_lukewarm_ocean", "deep_ocean",
                    "lukewarm_ocean", "warm_ocean"),
            group("PALE_GARDEN", "pale_garden"),
            group("PLAINS", "plains", "sunflower_plains"),
            group("RIVER", "river"),
            group("SAVANNA", "savanna", "savanna_plateau", "windswept_savanna"),
            group("SWAMP", "swamp"),
            group("TAIGA", "taiga", "old_growth_spruce_taiga", "old_growth_pine_taiga"));

    private BiomeFiles() {
    }

    /** all {@code *.yml} in the folder in alphabetical order; a missing folder is an empty configuration */
    public static BiomeConfig load(Path folder, List<String> warnings) throws ConfigException {
        if (!Files.isDirectory(folder)) return new BiomeConfig(Optional.of(folder), List.of());
        List<Path> files;
        try (Stream<Path> s = Files.list(folder)) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ConfigException("cannot read folder " + folder + ": " + e.getMessage(), e);
        }
        List<BiomeGroup> groups = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            name = name.substring(0, name.length() - 4);
            YamlConfiguration yaml = YamlFiles.load(file);
            ConfigReader r = new ConfigReader(yaml, file.toString(), warnings);
            groups.add(BiomeGroup.parse(name, r, VANILLA_GROUPS.getOrDefault(name.toUpperCase(Locale.ROOT), Set.of())));
            r.reportUnknown(Set.of(), null);
        }
        return new BiomeConfig(Optional.of(folder), groups);
    }

    /** default files from the jar, only when the folder does not exist yet (a deleted file is not restored) */
    public static void copyDefaultsIfMissing(Path folder) throws ConfigException {
        if (Files.exists(folder)) return;
        for (String name : defaultFiles()) {
            YamlFiles.copyResourceIfMissing(FOLDER + "/" + name, folder.resolve(name));
        }
    }

    static List<String> defaultFiles() throws ConfigException {
        try (InputStream in = BiomeFiles.class.getClassLoader().getResourceAsStream(INDEX)) {
            if (in == null) throw new ConfigException("missing in the jar: " + INDEX);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new ConfigException("cannot read " + INDEX + ": " + e.getMessage(), e);
        }
    }

    private static Map.Entry<String, Set<String>> group(String name, String... biomes) {
        return Map.entry(name, Stream.of(biomes).map(b -> "minecraft:" + b).collect(Collectors.toUnmodifiableSet()));
    }
}
