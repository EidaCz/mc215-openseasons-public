/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.function.Consumer;

/** Migrates OpenSeasons files between schema versions ({@code config-version}). */
public final class ConfigMigrator {

    public static final String VERSION_KEY = "config-version";

    /** step from version {@code from} to {@code from + 1} */
    public record Migration(int from, String description, Consumer<YamlConfiguration> apply) {
    }

    private final String file;
    private final int currentVersion;
    private final List<Migration> migrations;

    public ConfigMigrator(String file, int currentVersion, List<Migration> migrations) {
        this.file = file;
        this.currentVersion = currentVersion;
        this.migrations = List.copyOf(migrations);
    }

    /** migrators of the plugin files; so far version 1 everywhere and no steps */
    public static ConfigMigrator forFile(String file) {
        return new ConfigMigrator(file, 1, List.of());
    }

    public int currentVersion() {
        return currentVersion;
    }

    /**
     * Adds a missing version and runs the steps up to the current version.
     *
     * @return true when the content changed and the file has to be saved
     */
    public boolean migrate(YamlConfiguration yaml) throws ConfigException {
        if (!yaml.contains(VERSION_KEY)) {
            yaml.set(VERSION_KEY, currentVersion);
            return true;
        }
        if (!yaml.isInt(VERSION_KEY)) {
            throw new ConfigException(file + ": " + VERSION_KEY + " must be an integer, not '" + yaml.get(VERSION_KEY) + "'");
        }
        int version = yaml.getInt(VERSION_KEY);
        if (version > currentVersion) {
            throw new ConfigException(file + ": " + VERSION_KEY + " " + version + " is from a newer OpenSeasons version"
                    + " (this one knows at most " + currentVersion + ")");
        }
        boolean changed = false;
        while (version < currentVersion) {
            int from = version;
            Migration step = migrations.stream().filter(m -> m.from() == from).findFirst()
                    .orElseThrow(() -> new ConfigException(file + ": no migration from " + VERSION_KEY + " " + from));
            step.apply().accept(yaml);
            version++;
            yaml.set(VERSION_KEY, version);
            changed = true;
        }
        return changed;
    }
}
