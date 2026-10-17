/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.persistence;

import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Personal player settings in {@code plugins/OpenSeasons/data/players.yml}: {@code colors-disabled} are the
 * players with landscape colours turned off ({@code /toggleseasoncolors}), {@code particles-disabled} the
 * players with season particles turned off ({@code /toggleseasonparticles}). Written atomically, always both
 * settings.
 */
public final class PlayerSettingsStore {

    public static final int FORMAT = 1;
    private static final String COLORS_DISABLED = "colors-disabled";
    private static final String PARTICLES_DISABLED = "particles-disabled";

    /** players with landscape colours turned off and with season particles turned off */
    public record Settings(Set<UUID> colorsDisabled, Set<UUID> particlesDisabled) {

        public static final Settings EMPTY = new Settings(Set.of(), Set.of());

        public Settings {
            colorsDisabled = Set.copyOf(colorsDisabled);
            particlesDisabled = Set.copyOf(particlesDisabled);
        }

        public boolean isEmpty() {
            return colorsDisabled.isEmpty() && particlesDisabled.isEmpty();
        }
    }

    private final Path file;

    public PlayerSettingsStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean exists() {
        return Files.exists(file);
    }

    /** settings from the file; a missing file or key = nobody turned anything off */
    public Settings load(List<String> warnings) throws ConfigException {
        if (!Files.exists(file)) return Settings.EMPTY;
        YamlConfiguration yaml = YamlFiles.load(file);
        return new Settings(uuids(yaml.getStringList(COLORS_DISABLED), file + ": " + COLORS_DISABLED, warnings),
                uuids(yaml.getStringList(PARTICLES_DISABLED), file + ": " + PARTICLES_DISABLED, warnings));
    }

    public void save(Settings settings) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Personal player settings of OpenSeasons. Do not edit while the server is running."));
        yaml.set("format", FORMAT);
        yaml.set(COLORS_DISABLED, sorted(settings.colorsDisabled()));
        yaml.set(PARTICLES_DISABLED, sorted(settings.particlesDisabled()));
        YamlFiles.saveAtomically(yaml, file);
    }

    private static List<String> sorted(Set<UUID> players) {
        List<String> list = new ArrayList<>();
        new TreeSet<>(players).forEach(u -> list.add(u.toString()));
        return list;
    }

    private static Set<UUID> uuids(List<String> values, String where, List<String> warnings) {
        Set<UUID> out = new TreeSet<>();
        for (String v : values) {
            try {
                out.add(UUID.fromString(v.trim()));
            } catch (IllegalArgumentException e) {
                warnings.add(where + ": '" + v + "' is not a UUID, skipped");
            }
        }
        return out;
    }
}
