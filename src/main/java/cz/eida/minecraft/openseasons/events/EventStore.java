/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Running events in the worlds ({@code data/events.yml}, written by the plugin). After start it tells
 * which event already began and is only resumed (without start commands), and which one ended while
 * the server was not running (the stop commands run).
 */
public final class EventStore {

    private final Path file;

    public EventStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** empty when the file does not exist yet (first start with events) */
    public Optional<Map<String, List<String>>> load() throws ConfigException {
        if (!Files.isRegularFile(file)) return Optional.empty();
        YamlConfiguration yaml = YamlFiles.load(file);
        Map<String, List<String>> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("running");
        if (section != null) {
            for (String world : section.getKeys(false)) out.put(world, List.copyOf(section.getStringList(world)));
        }
        return Optional.of(out);
    }

    public void save(Map<String, List<String>> running) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("OpenSeasons – running events in the worlds (written by the plugin)"));
        yaml.createSection("running");
        running.forEach((world, ids) -> yaml.set("running." + world, List.copyOf(ids)));
        YamlFiles.saveAtomically(yaml, file);
    }
}
