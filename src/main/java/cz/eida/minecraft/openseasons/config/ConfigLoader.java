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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the OpenSeasons configuration from the plugin folder: {@code config.yml} (plugin and season
 * settings), {@code calendar.yml}, {@code lang.yml}, {@code names.yml} (display-name overlay) and the
 * {@code biomes/} folder. Missing files are created from the defaults in the jar.
 */
public final class ConfigLoader {

    public static final String CONFIG = "config.yml";
    public static final String CALENDAR = "calendar.yml";
    public static final String LANG = "lang.yml";
    public static final String NAMES = "names.yml";

    private final Path dataFolder;

    /** @param dataFolder the plugin folder, typically {@code plugins/OpenSeasons} */
    public ConfigLoader(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public LoadedConfig load() throws ConfigException {
        List<String> warnings = new ArrayList<>();
        Map<String, Path> sources = new LinkedHashMap<>();

        // config.yml: plugin settings, client colours and season settings
        Path configPath = dataFolder.resolve(CONFIG);
        ConfigReader configReader = own(CONFIG, warnings);
        PluginSettings settings = PluginSettings.parse(configReader);
        ClientColorSettings clientColors = ClientColorSettings.parse(configReader);
        MainConfig main = MainConfig.parse(configReader);
        configReader.reportUnknown(Set.of(), null);
        sources.put("config", configPath);

        ConfigReader calendarReader = own(CALENDAR, warnings);
        CalendarSettings calendar = CalendarSettings.parse(calendarReader);
        calendarReader.reportUnknown(Set.of(), null);
        sources.put("calendar", dataFolder.resolve(CALENDAR));

        ConfigReader langReader = own(LANG, warnings);
        LangConfig lang = LangConfig.parse(langReader);
        langReader.reportUnknown(Set.of(), null);
        sources.put("lang", dataFolder.resolve(LANG));

        // names.yml: display names of months, weekdays and seasons
        ConfigReader namesReader = own(NAMES, warnings);
        NamesOverlay names = NamesOverlay.parse(namesReader);
        namesReader.reportUnknown(Set.of(), null);
        sources.put("names", dataFolder.resolve(NAMES));
        calendar = names.applyTo(calendar);
        lang = names.applyTo(lang);

        Path biomeFolder = dataFolder.resolve(BiomeFiles.FOLDER);
        BiomeFiles.copyDefaultsIfMissing(biomeFolder);
        BiomeConfig biomes = BiomeFiles.load(biomeFolder, warnings);
        sources.put("biomes", biomeFolder);

        return new LoadedConfig(settings, main, calendar, lang, biomes, clientColors, Map.copyOf(sources),
                List.copyOf(warnings));
    }

    private ConfigReader own(String name, List<String> warnings) throws ConfigException {
        ConfigReader reader = new ConfigReader(loadOwn(name), dataFolder.resolve(name).toString(), warnings);
        reader.integer(ConfigMigrator.VERSION_KEY, 1);
        return reader;
    }

    /** own file: creates the default, loads it and migrates it to the current schema version if needed */
    private YamlConfiguration loadOwn(String name) throws ConfigException {
        Path file = dataFolder.resolve(name);
        YamlFiles.copyResourceIfMissing(name, file);
        YamlConfiguration yaml = YamlFiles.load(file);
        ConfigMigrator migrator = ConfigMigrator.forFile(file.toString());
        int before = yaml.getInt(ConfigMigrator.VERSION_KEY, 0);
        if (migrator.migrate(yaml)) {
            try {
                if (before > 0) {
                    Files.copy(file, file.resolveSibling(name + ".v" + before + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                }
                YamlFiles.saveAtomically(yaml, file);
            } catch (IOException e) {
                throw new ConfigException("cannot save migrated " + file + ": " + e.getMessage(), e);
            }
        }
        return yaml;
    }
}
