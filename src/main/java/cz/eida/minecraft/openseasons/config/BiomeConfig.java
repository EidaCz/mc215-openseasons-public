/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loaded biome files. A biome belongs to the group that names it (files in alphabetical order, the
 * first one wins); a custom biome (outside {@code minecraft:}) without a group gets the group with
 * {@code includes: ALL}.
 */
public final class BiomeConfig {

    public static final BiomeConfig EMPTY = new BiomeConfig(Optional.empty(), List.of());

    private final Optional<Path> folder;
    private final List<BiomeGroup> groups;
    private final Map<String, BiomeGroup> byBiome = new HashMap<>();
    private final Optional<BiomeGroup> allCustom;

    /** @param folder folder the files were loaded from (for display) */
    public BiomeConfig(Optional<Path> folder, List<BiomeGroup> groups) {
        this.folder = folder;
        this.groups = List.copyOf(groups);
        for (BiomeGroup g : this.groups) {
            for (String b : g.biomes()) byBiome.putIfAbsent(b, g);
        }
        this.allCustom = this.groups.stream().filter(BiomeGroup::allCustom).findFirst();
    }

    public Optional<Path> folder() {
        return folder;
    }

    public List<BiomeGroup> groups() {
        return groups;
    }

    /** group of a biome {@code namespace:path} */
    public Optional<BiomeGroup> groupOf(String biomeKey) {
        BiomeGroup g = byBiome.get(biomeKey);
        if (g != null) return Optional.of(g);
        return biomeKey.startsWith("minecraft:") ? Optional.empty() : allCustom;
    }
}
