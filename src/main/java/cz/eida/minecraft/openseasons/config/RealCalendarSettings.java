/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.calendar.RealCalendar;
import cz.eida.minecraft.openseasons.calendar.RealPeriod;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * {@code real-calendar.yml}: real-world seasons, the season source of worlds and periods by the real date.
 *
 * @param worlds  season source of a world; a missing world = game calendar
 * @param periods enabled periods in file order (the first active one wins)
 */
public record RealCalendarSettings(RealCalendar calendar, Map<String, SeasonSource> worlds, List<RealPeriod> periods) {

    public static final String FILE = "real-calendar.yml";

    /** without a file: astronomical seasons, game calendar everywhere, no periods */
    public static final RealCalendarSettings NONE = new RealCalendarSettings(RealCalendar.ASTRONOMICAL, Map.of(), List.of());

    public RealCalendarSettings {
        Map<String, SeasonSource> w = new LinkedHashMap<>();
        worlds.forEach((k, v) -> w.put(k.toLowerCase(Locale.ROOT), v));
        worlds = Map.copyOf(w);
        periods = List.copyOf(periods);
    }

    /** season source of a world */
    public SeasonSource worldSource(String world) {
        return worlds.getOrDefault(world.toLowerCase(Locale.ROOT), SeasonSource.GAME);
    }

    public Optional<RealPeriod> period(String id) {
        return periods.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst();
    }

    /** periods active on the given day */
    public List<RealPeriod> active(LocalDate date) {
        return periods.stream().filter(p -> p.activeOn(date)).toList();
    }

    /** first active period of the world with a season */
    public Optional<RealPeriod> worldSeasonPeriod(String world, LocalDate date) {
        return periods.stream().filter(p -> p.season().isPresent() && p.appliesToWorld(world) && p.activeOn(date))
                .findFirst();
    }

    /** events of the active periods of the whole world */
    public Set<String> worldEvents(String world, LocalDate date) {
        Set<String> out = new LinkedHashSet<>();
        for (RealPeriod p : periods) {
            if (p.appliesToWorld(world) && p.activeOn(date)) p.events().forEach(e -> out.add(e.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /** events of all active periods (regions may use them via {@code real-periods}) */
    public Set<String> regionEvents(LocalDate date) {
        Set<String> out = new LinkedHashSet<>();
        for (RealPeriod p : periods) {
            if (p.activeOn(date)) p.events().forEach(e -> out.add(e.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /** loads the file from the plugin folder (creates a missing one from the default) */
    public static RealCalendarSettings load(Path dataFolder, List<String> warnings) throws ConfigException {
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
        RealCalendarSettings s = parse(r, yaml);
        r.reportUnknown(Set.of(), null);
        return s;
    }

    static RealCalendarSettings parse(ConfigReader r, ConfigurationSection root) {
        ZoneId zone = ZoneId.systemDefault();
        String tz = r.string("timezone", "system").trim();
        if (!tz.equalsIgnoreCase("system")) {
            try {
                zone = ZoneId.of(tz);
            } catch (DateTimeException e) {
                r.warn("timezone", "unknown time zone '" + tz + "', using the server time zone");
            }
        }

        Map<Season, MonthDay> starts = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            String key = "seasons." + s.name().toLowerCase(Locale.ROOT);
            MonthDay def = RealCalendar.ASTRONOMICAL_STARTS.get(s);
            starts.put(s, monthDay(r, key, r.string(key, null), def));
        }
        RealCalendar calendar;
        try {
            calendar = new RealCalendar(starts, zone);
        } catch (IllegalArgumentException e) {
            r.warn("seasons", e.getMessage() + ", using astronomical seasons");
            calendar = new RealCalendar(RealCalendar.ASTRONOMICAL_STARTS, zone);
        }

        Map<String, SeasonSource> worlds = new LinkedHashMap<>();
        for (String world : r.keys("worlds")) {
            String v = r.string("worlds." + world, "game");
            Optional<SeasonSource> src = SeasonSource.parse(v);
            if (src.isEmpty()) {
                r.warn("worlds." + world, "'" + v + "' is neither game nor real, using game");
            }
            worlds.put(world, src.orElse(SeasonSource.GAME));
        }

        List<RealPeriod> periods = new ArrayList<>();
        for (String id : r.keys("periods")) {
            String p = "periods." + id + ".";
            boolean enabled = r.bool(p + "enabled", true);
            String fromText = r.string(p + "from", null);
            String toText = r.string(p + "to", null);
            if (fromText == null || toText == null) {
                r.warn("periods." + id, "missing from or to, period skipped");
                continue;
            }
            MonthDay from = monthDay(r, p + "from", fromText, null);
            MonthDay to = monthDay(r, p + "to", toText, null);
            Optional<Season> season = r.optionalSeason(p + "season");
            OptionalDouble progress = root.contains(p + "season-progress")
                    ? OptionalDouble.of(r.percent(p + "season-progress", 50.0)) : OptionalDouble.empty();
            List<String> events = r.list(p + "events", List.of());
            List<String> worldsOfPeriod = r.list(p + "worlds", List.of());
            if (from == null || to == null || !enabled) continue;
            periods.add(new RealPeriod(id, from, to, season, progress, events, worldsOfPeriod));
        }
        return new RealCalendarSettings(calendar, worlds, periods);
    }

    /** {@code dd/mm} or {@code dd.mm.}; an invalid value = {@code def} with a warning */
    static MonthDay monthDay(ConfigReader r, String path, String text, MonthDay def) {
        if (text == null) return def;
        String[] parts = text.trim().replace('.', '/').split("/");
        try {
            if (parts.length >= 2) return MonthDay.of(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[0].trim()));
        } catch (NumberFormatException | DateTimeException e) {
            // falls through to the warning
        }
        r.warn(path, "date '" + text + "' must be dd/mm" + (def == null ? "" : ", using " + def.getDayOfMonth() + "/" + def.getMonthValue()));
        return def;
    }
}
