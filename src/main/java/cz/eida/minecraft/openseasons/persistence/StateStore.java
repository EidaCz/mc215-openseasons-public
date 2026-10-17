/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.persistence;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.YamlFiles;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/**
 * State of the worlds in {@code plugins/OpenSeasons/data/worlds.yml}. Written atomically (temporary file
 * and move) and only after a change. A damaged file is copied aside before the error.
 *
 * <p>{@code day-offset} is the offset of the date against the world clock; the date is only the last computed one.
 * {@code time-paused} are the worlds with the time paused ({@code /openseasons pausetime}); it may also contain
 * worlds without seasons.
 */
public final class StateStore {

    public static final int FORMAT = 1;

    /** what the date runs by; {@code day-offset} values only apply to the clock they were created with */
    public enum Clock {
        /** game days of the world ({@code World#getFullTime()}) */
        GAME,
        /** real days ({@code calendar-is-real-life-days}) */
        REAL_DAYS
    }

    /** @param timePaused worlds with the time paused */
    public record Snapshot(Map<String, WorldSeasonState> worlds, Clock clock, Set<String> timePaused) {

        public Snapshot {
            timePaused = timePaused == null ? Set.of() : Set.copyOf(timePaused);
        }

        public Snapshot(Map<String, WorldSeasonState> worlds, Clock clock) {
            this(worlds, clock, Set.of());
        }
    }

    private final Path file;

    public StateStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public Snapshot load(List<String> warnings) throws ConfigException {
        if (!Files.exists(file)) return new Snapshot(Map.of(), Clock.GAME);
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (ConfigException e) {
            Path copy = file.resolveSibling(file.getFileName() + ".broken-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            try {
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException io) {
                e.addSuppressed(io);
            }
            throw new ConfigException(e.getMessage() + " (copy: " + copy.getFileName() + ")", e);
        }
        if (yaml.getInt("format", FORMAT) > FORMAT) {
            throw new ConfigException(file + ": format " + yaml.getInt("format") + " is from a newer OpenSeasons version");
        }

        Map<String, WorldSeasonState> worlds = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("worlds");
        if (section != null) {
            for (String world : section.getKeys(false)) {
                ConfigurationSection w = section.getConfigurationSection(world);
                Optional<Season> season = w == null ? Optional.empty() : Season.parse(w.getString("season"));
                if (season.isEmpty()) {
                    warnings.add(file + ": worlds." + world + ": missing or unknown season, the world will be created again");
                    continue;
                }
                try {
                    CalendarDate date = new CalendarDate(w.getInt("date.day", 1), w.getInt("date.month", 1),
                            w.getInt("date.year", 1));
                    OptionalLong offset = w.isSet("day-offset") ? OptionalLong.of(w.getLong("day-offset"))
                            : OptionalLong.empty();
                    worlds.put(world, new WorldSeasonState(w.getBoolean("enabled", true), season.get(), date, offset));
                } catch (IllegalArgumentException e) {
                    warnings.add(file + ": worlds." + world + ": " + e.getMessage() + ", the world will be created again");
                }
            }
        }

        Clock clock = Clock.GAME;
        String c = yaml.getString("clock", "game");
        if (c.equalsIgnoreCase("real-days")) {
            clock = Clock.REAL_DAYS;
        } else if (!c.equalsIgnoreCase("game")) {
            warnings.add(file + ": clock: unknown value '" + c + "', using game");
        }
        Set<String> paused = new TreeSet<>();
        for (String world : yaml.getStringList("time-paused")) {
            if (!world.isBlank()) paused.add(world.trim());
        }
        return new Snapshot(worlds, clock, paused);
    }

    public void save(Snapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("World state of OpenSeasons. Do not edit while the server is running."));
        yaml.set("format", FORMAT);
        yaml.set("clock", snapshot.clock() == Clock.REAL_DAYS ? "real-days" : "game");
        snapshot.worlds().forEach((world, s) -> {
            String p = "worlds." + world + ".";
            yaml.set(p + "enabled", s.enabled());
            yaml.set(p + "season", s.season().name());
            yaml.set(p + "date.day", s.date().day());
            yaml.set(p + "date.month", s.date().month());
            yaml.set(p + "date.year", s.date().year());
            s.dayOffset().ifPresent(o -> yaml.set(p + "day-offset", o));
        });
        yaml.set("time-paused", new ArrayList<>(new TreeSet<>(snapshot.timePaused())));
        YamlFiles.saveAtomically(yaml, file);
    }
}
