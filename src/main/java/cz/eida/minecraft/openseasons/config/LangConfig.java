/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Messages ({@code lang.yml}). Values are raw: colours {@code &a} and {@code &#RRGGBB}, variables
 * {@code $name$}.
 */
public record LangConfig(Map<String, String> messages) {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("SEASON-TO-WINTER", "&b&lWinter begins!");
        d.put("SEASON-TO-SPRING", "&b&lSpring begins!");
        d.put("SEASON-TO-SUMMER", "&b&lSummer begins!");
        d.put("SEASON-TO-FALL", "&b&lFall begins!");
        d.put("NO-PERMISSION-MESSAGE", "&cYou do not have permission to do that.");
        d.put("WINTER-DISPLAY-NAME", "Winter");
        d.put("SPRING-DISPLAY-NAME", "Spring");
        d.put("SUMMER-DISPLAY-NAME", "Summer");
        d.put("FALL-DISPLAY-NAME", "Fall");
        d.put("SEASONCOMMAND-DISABLED", "&cSeasons are disabled in this world.");
        d.put("SEASONCOMMAND-CURRENTDATE",
                "&aToday is &2$current_weekday$, $current_month$ $current_day$, year $current_year$&a.");
        d.put("SEASONSCOMMAND-CURRENTSEASON", "&aSeason: &2$current_season$");
        d.put("SEASONSCOMMAND-DAYSUNTILNEXTSEASON",
                "&aDays until the next season (&2$next_season$&a): &7$days_until_next_season$");
        d.put("SEASONSCOMMAND-DAYSUNTILNEXTEVENT",
                "&aDays until the next event (&2$next_event$&a): &7$days_until_next_event$");
        d.put("SEASONCOMMAND-TIME", "&aTime: &2$time$");
        d.put("SEASONCOMMAND-EVENTS", "&aRunning events: &2$events$");
        d.put("TOGGLESEASONSCOMMAND-MESSAGE", "&aSeason colours are &2$status$&a for you.");
        d.put("TOGGLEPARTICLESCOMMAND-MESSAGE", "&aSeason particles are &2$status$&a for you.");
        d.put(ENABLED, "enabled");
        d.put(DISABLED, "disabled");
        DEFAULTS = Collections.unmodifiableMap(d);
    }

    public LangConfig {
        messages = Collections.unmodifiableMap(new LinkedHashMap<>(messages));
    }

    public String get(String key) {
        return messages.getOrDefault(key, DEFAULTS.getOrDefault(key, key));
    }

    public String seasonDisplayName(Season season) {
        return get(season.name() + "-DISPLAY-NAME");
    }

    /** may span several lines (a list in YAML) */
    public String seasonStartMessage(Season season) {
        return get("SEASON-TO-" + season.name());
    }

    static LangConfig parse(ConfigReader r) {
        Map<String, String> messages = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            messages.put(e.getKey(), r.text(e.getKey(), e.getValue()));
        }
        return new LangConfig(messages);
    }
}
