/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SubSeason;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Typed reading of one YAML file with default values in code.
 *
 * <p>Accepts lenient formats: percentages as text ({@code 5.0%}), lists as a YAML list or as
 * comma-separated text ({@code OAK_LEAVES, BIRCH_LEAVES}) and {@code NONE}/{@code none} as an empty
 * value. An invalid value does not stop loading: the default is used and a warning is added. Remembers
 * the keys it read, so it can report unknown ones (typos).
 */
public final class ConfigReader {

    private final ConfigurationSection root;
    private final String source;
    private final List<String> warnings;
    private final Set<String> read = new HashSet<>();

    public ConfigReader(ConfigurationSection root, String source, List<String> warnings) {
        this.root = root;
        this.source = source;
        this.warnings = warnings;
    }

    public String source() {
        return source;
    }

    public boolean has(String path) {
        return root.contains(path);
    }

    public boolean bool(String path, boolean def) {
        Object v = value(path);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("false")) return Boolean.parseBoolean(s);
        return invalid(path, v, "true/false", def);
    }

    public int integer(String path, int def) {
        Object v = value(path);
        if (v == null) return def;
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return n.intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return invalid(path, v, "integer", def);
        }
    }

    public long longValue(String path, long def) {
        Object v = value(path);
        if (v == null) return def;
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) return n.longValue();
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return invalid(path, v, "integer", def);
        }
    }

    public double number(String path, double def) {
        Object v = value(path);
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return invalid(path, v, "number", def);
        }
    }

    /** percentage 0–100; accepts {@code 5.0%}, {@code 5%} and {@code 5} */
    public double percent(String path, double def) {
        Object v = value(path);
        if (v == null) return def;
        if (v instanceof Number n) return checkPercent(path, n.doubleValue(), def);
        String s = v.toString().trim();
        if (s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
        try {
            return checkPercent(path, Double.parseDouble(s), def);
        } catch (NumberFormatException e) {
            return invalid(path, v, "a percentage, e.g. 5.0%", def);
        }
    }

    public String string(String path, String def) {
        Object v = value(path);
        return v == null ? def : v.toString();
    }

    /** text; a YAML list is joined line by line (multi-line messages) */
    public String text(String path, String def) {
        Object v = value(path);
        if (v == null) return def;
        if (v instanceof Collection<?> c) {
            return String.join("\n", c.stream().map(String::valueOf).toList());
        }
        return v.toString();
    }

    /** list from a YAML list or comma-separated text; skips {@code none} and empty items */
    public List<String> list(String path, List<String> def) {
        Object v = value(path);
        if (v == null) return def;
        Collection<?> items = v instanceof Collection<?> c ? c : List.of(v.toString().split(","));
        List<String> out = new ArrayList<>();
        for (Object item : items) {
            if (item == null) continue;
            String s = item.toString().trim();
            if (s.isEmpty() || s.equalsIgnoreCase("none")) continue;
            out.add(s);
        }
        return List.copyOf(out);
    }

    public Set<Season> seasons(String path, Set<Season> def) {
        if (value(path) == null) return def;
        EnumSet<Season> out = EnumSet.noneOf(Season.class);
        for (String s : list(path, List.of())) {
            Optional<Season> season = Season.parse(s);
            if (season.isPresent()) out.add(season.get());
            else warn(path, "unknown season '" + s + "' (winter, spring, summer, fall)");
        }
        return Set.copyOf(out);
    }

    public Set<SubSeason> subSeasons(String path, Set<SubSeason> def) {
        if (value(path) == null) return def;
        EnumSet<SubSeason> out = EnumSet.noneOf(SubSeason.class);
        for (String s : list(path, List.of())) {
            try {
                out.add(SubSeason.ofIndex(Integer.parseInt(s)));
            } catch (IllegalArgumentException e) {
                warn(path, "invalid phase '" + s + "' (0–4)");
            }
        }
        return Set.copyOf(out);
    }

    /** season, or empty for {@code NONE} */
    public Optional<Season> optionalSeason(String path) {
        Object v = value(path);
        if (v == null || v.toString().trim().equalsIgnoreCase("none")) return Optional.empty();
        Optional<Season> season = Season.parse(v.toString());
        if (season.isEmpty()) warn(path, "unknown season '" + v + "', using NONE");
        return season;
    }

    /** colour {@code #RRGGBB} or {@code RRGGBB}; {@code NONE} is empty */
    public OptionalInt color(String path) {
        Object v = value(path);
        if (v == null) return OptionalInt.empty();
        return parseColor(path, v.toString());
    }

    OptionalInt parseColor(String path, String text) {
        String s = text.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) return OptionalInt.empty();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) {
            try {
                return OptionalInt.of(Integer.parseInt(s, 16));
            } catch (NumberFormatException ignored) {
                // falls through to the warning
            }
        }
        warn(path, "invalid colour '" + text + "', expected #RRGGBB or NONE");
        return OptionalInt.empty();
    }

    /** child keys of a section in file order; a missing section is empty */
    public List<String> keys(String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            if (root.contains(path)) warn(path, "a section is expected");
            return List.of();
        }
        read.add(path);
        return List.copyOf(section.getKeys(false));
    }

    public void warn(String path, String message) {
        warnings.add(source + ": " + path + ": " + message);
    }

    /**
     * Reports the keys the parser did not read.
     *
     * @param ignored     keys (also whole sections) that are skipped silently, e.g. obsolete keys
     * @param ignoredNote what to say about skipped keys present in the file; {@code null} = say nothing
     */
    public void reportUnknown(Set<String> ignored, String ignoredNote) {
        for (String key : root.getKeys(true)) {
            if (root.isConfigurationSection(key) || read.contains(key)) continue;
            String ignoredBy = ignored.stream()
                    .filter(i -> key.equals(i) || key.startsWith(i + "."))
                    .findFirst().orElse(null);
            if (ignoredBy != null) {
                if (ignoredNote != null && key.equals(ignoredBy)) warn(key, ignoredNote);
                continue;
            }
            warn(key, "unknown key, ignored");
        }
    }

    private Object value(String path) {
        read.add(path);
        Object v = root.get(path);
        if (v instanceof ConfigurationSection) {
            warn(path, "a value is expected, not a section");
            return null;
        }
        return v;
    }

    private double checkPercent(String path, double value, double def) {
        if (value < 0 || value > 100) return invalid(path, value, "percent 0–100", def);
        return value;
    }

    private <T> T invalid(String path, Object value, String expected, T def) {
        warn(path, "value '" + value + "' is not " + expected + ", using " + def);
        return def;
    }
}
