/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.flora;

import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.ConfigMigrator;
import cz.eida.minecraft.openseasons.config.ConfigReader;
import cz.eida.minecraft.openseasons.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Details of seasonal plants from {@code flora.yml}. The main switches of the basic plants stay
 * in {@code config.yml} ({@code modify-blocks}, {@code flower-chance-in-spring} …); here are
 * their chances and limits,
 * and the extra plants.
 *
 * @param chunkRadius     chunk radius around the player in which plants appear
 * @param samplesPerChunk how many places in a chunk are tried per visit
 * @param removalsPerVisit most plants removed per chunk visit
 */
public record FloraSettings(int chunkRadius, int samplesPerChunk, int removalsPerVisit,
                            Map<PlantKind, Kind> kinds) {

    public static final String FILE = "flora.yml";

    /**
     * @param enabled     only for extra plants; the basic ones are enabled by {@code config.yml}
     * @param chance      chance of a plant at a tried place per visit, percent (for spring
     *                    flowers {@code flower-chance-in-spring} applies)
     * @param maxPerChunk most plants of this kind from OpenSeasons in a chunk (for bushes
     *                    {@code max-berrybushes-per-chunk} applies to all bushes in the chunk)
     */
    public record Kind(boolean enabled, double chance, int maxPerChunk) {
    }

    /** default values, the same as in the default {@code flora.yml} */
    public static final Map<PlantKind, Kind> DEFAULT_KINDS = defaults();

    public static final FloraSettings DEFAULTS = new FloraSettings(8, 6, 64, DEFAULT_KINDS);

    public FloraSettings {
        kinds = Map.copyOf(kinds);
    }

    public Kind kind(PlantKind k) {
        return kinds.getOrDefault(k, DEFAULT_KINDS.get(k));
    }

    /** loads {@code flora.yml} from the plugin folder (creates a missing one from the default) */
    public static FloraSettings load(Path dataFolder, List<String> warnings) throws ConfigException {
        Path file = dataFolder.resolve(FILE);
        YamlFiles.copyResourceIfMissing(FILE, file);
        YamlConfiguration yaml = YamlFiles.load(file);
        ConfigMigrator migrator = ConfigMigrator.forFile(file.toString());
        if (migrator.migrate(yaml)) {
            try {
                YamlFiles.saveAtomically(yaml, file);
            } catch (IOException e) {
                warnings.add("Cannot save migrated " + file + ": " + e.getMessage());
            }
        }
        ConfigReader r = new ConfigReader(yaml, file.toString(), warnings);
        r.integer(ConfigMigrator.VERSION_KEY, 1);
        FloraSettings s = parse(r);
        r.reportUnknown(Set.of(), null);
        return s;
    }

    static FloraSettings parse(ConfigReader r) {
        Map<PlantKind, Kind> kinds = new EnumMap<>(PlantKind.class);
        for (PlantKind k : PlantKind.values()) {
            Kind def = DEFAULT_KINDS.get(k);
            String p = k.id() + ".";
            boolean enabled = k.extra() ? r.bool(p + "enabled", def.enabled()) : true;
            double chance = k == PlantKind.FLOWERS ? def.chance() : r.percent(p + "chance", def.chance());
            int max = k == PlantKind.BERRY_BUSHES ? def.maxPerChunk()
                    : Math.clamp(r.integer(p + "max-per-chunk", def.maxPerChunk()), 0, 256);
            kinds.put(k, new Kind(enabled, chance, max));
        }
        return new FloraSettings(
                Math.clamp(r.integer("chunk-radius", 8), 1, 16),
                Math.clamp(r.integer("samples-per-chunk", 6), 1, 64),
                Math.clamp(r.integer("removals-per-visit", 64), 1, 1024),
                kinds);
    }

    private static Map<PlantKind, Kind> defaults() {
        Map<PlantKind, Kind> m = new EnumMap<>(PlantKind.class);
        m.put(PlantKind.FLOWERS, new Kind(true, 0.0, 12));
        m.put(PlantKind.GRASS, new Kind(true, 10.0, 24));
        m.put(PlantKind.MUSHROOMS, new Kind(true, 4.0, 6));
        m.put(PlantKind.BERRY_BUSHES, new Kind(true, 1.0, 0));
        m.put(PlantKind.LEAF_LITTER, new Kind(true, 30.0, 40));
        m.put(PlantKind.FIREFLY_BUSH, new Kind(true, 1.5, 2));
        m.put(PlantKind.WILDFLOWERS, new Kind(true, 2.0, 6));
        m.put(PlantKind.PINK_PETALS, new Kind(true, 4.0, 6));
        m.put(PlantKind.DRY_GRASS, new Kind(true, 4.0, 8));
        m.put(PlantKind.BUSH, new Kind(true, 0.5, 2));
        m.put(PlantKind.CACTUS_FLOWER, new Kind(true, 15.0, 3));
        m.put(PlantKind.PUMPKINS, new Kind(true, 0.3, 1));
        return Map.copyOf(m);
    }
}
