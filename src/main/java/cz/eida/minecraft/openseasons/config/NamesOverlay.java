/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.season.Season;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Display names ({@code names.yml}): an overlay over the names from {@code calendar.yml} and
 * {@code lang.yml}. The calendar itself does not change, only what players and other plugins see
 * (placeholders, commands, API). Months and weekdays are matched by their calendar name case-insensitively,
 * so the overlay does not touch a custom calendar with other months. It takes precedence over the season
 * names in {@code lang.yml}.
 *
 * @param months   month name in the calendar (lower case) → display name
 * @param weekdays weekday name in the calendar (lower case) → display name
 * @param seasons  season → display name
 */
public record NamesOverlay(Map<String, String> months, Map<String, String> weekdays, Map<Season, String> seasons) {

    public static final NamesOverlay NONE = new NamesOverlay(Map.of(), Map.of(), Map.of());

    public NamesOverlay {
        months = Map.copyOf(months);
        weekdays = Map.copyOf(weekdays);
        seasons = Map.copyOf(seasons);
    }

    /** calendar with the display names of months and days; the structure (day counts, season starts) stays */
    public CalendarSettings applyTo(CalendarSettings settings) {
        if (months.isEmpty() && weekdays.isEmpty()) return settings;
        GameCalendar renamed = settings.calendar().withNames(
                name -> months.getOrDefault(name.toLowerCase(Locale.ROOT), name),
                name -> weekdays.getOrDefault(name.toLowerCase(Locale.ROOT), name));
        return settings.withCalendar(renamed);
    }

    /** messages with the season names from the overlay */
    public LangConfig applyTo(LangConfig lang) {
        if (seasons.isEmpty()) return lang;
        Map<String, String> messages = new LinkedHashMap<>(lang.messages());
        seasons.forEach((season, name) -> messages.put(season.name() + "-DISPLAY-NAME", name));
        return new LangConfig(messages);
    }

    static NamesOverlay parse(ConfigReader r) {
        return new NamesOverlay(names(r, "months"), names(r, "weekdays"), seasonNames(r));
    }

    private static Map<String, String> names(ConfigReader r, String section) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : r.keys(section)) {
            String value = r.string(section + "." + key, "").trim();
            if (value.isEmpty()) {
                r.warn(section + "." + key, "empty name, keeping the calendar name");
                continue;
            }
            out.put(key.toLowerCase(Locale.ROOT), value);
        }
        return out;
    }

    private static Map<Season, String> seasonNames(ConfigReader r) {
        Map<Season, String> out = new EnumMap<>(Season.class);
        for (String key : r.keys("seasons")) {
            String path = "seasons." + key;
            var season = Season.parse(key);
            String value = r.string(path, "").trim();
            if (season.isEmpty()) {
                r.warn(path, "unknown season (WINTER, SPRING, SUMMER, FALL)");
            } else if (value.isEmpty()) {
                r.warn(path, "empty name, keeping the name from lang.yml");
            } else {
                out.put(season.get(), value);
            }
        }
        return out;
    }
}
