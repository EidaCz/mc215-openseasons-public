/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.config.CalendarSettings;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.ConfigMigrator;
import cz.eida.minecraft.openseasons.config.ConfigReader;
import cz.eida.minecraft.openseasons.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Loads events: {@code events/events.yml} (built-in) and {@code events/custom-events.yml} (custom
 * dated, weekly and daily). Missing files are created from the defaults in the jar.
 */
public final class EventFiles {

    public static final String EVENTS = "events/events.yml";
    public static final String CUSTOM = "events/custom-events.yml";

    /** English weekday names for weekly events in a calendar with a seven-day week (week from Monday) */
    static final List<String> ENGLISH_WEEKDAYS =
            List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    /** built-in event: default name, dates and messages */
    record Builtin(SeasonEvent.Type type, String name, String start, String stop, List<String> startLines) {
    }

    static final List<Builtin> BUILTINS = List.of(
            new Builtin(SeasonEvent.Type.CHRISTMAS, "&cChristmas", "25/12", "28/12", List.of(
                    "&cChristmas &ahas begun!",
                    "&aPresents appear in villages and around them, just unwrap them.")),
            new Builtin(SeasonEvent.Type.NEWYEAR, "&cNew Year", "1/1", "1/1", List.of(
                    "&cHappy New Year!",
                    "&aThe villagers celebrate, fireworks fly over the landscape at night.")),
            new Builtin(SeasonEvent.Type.HALLOWEEN, "&6Halloween", "31/10", "2/11", List.of(
                    "&6Halloween &ahas begun!",
                    "&aStrange monsters roam outside at night, be careful.")),
            new Builtin(SeasonEvent.Type.EASTER, "&dEaster", "20/4", "24/4", List.of(
                    "&dEaster &ahas begun!",
                    "&aEggs are hidden across the landscape. Watch out for killer bunnies at night.")));

    private EventFiles() {
    }

    /**
     * @param dataFolder plugin folder ({@code plugins/OpenSeasons})
     * @param calendar   calendar with the display names (weekdays, date format)
     * @param knownItem  the loot material exists and is an item
     */
    public static EventSettings load(Path dataFolder, CalendarSettings calendar, Predicate<String> knownItem)
            throws ConfigException {
        List<String> warnings = new ArrayList<>();
        Map<String, Path> sources = new LinkedHashMap<>();

        Source events = source(dataFolder, EVENTS);
        sources.put("events", events.path());
        Source custom = source(dataFolder, CUSTOM);
        sources.put("custom-events", custom.path());

        List<SeasonEvent> list = new ArrayList<>();
        ConfigReader r = new ConfigReader(events.yaml(), events.path().toString(), warnings);
        r.integer(ConfigMigrator.VERSION_KEY, 1);
        boolean american = r.has("format") ? r.integer("format", 0) == 1 : calendar.americanDateFormat();
        GameCalendar cal = calendar.calendar();

        for (Builtin b : BUILTINS) list.add(builtin(r, b, american, cal));
        EventSettings.Christmas christmas = christmas(r, knownItem);
        EventSettings.NewYear newYear = new EventSettings.NewYear(
                r.bool("dated-events.newyear.spawn-firework-boxes", true),
                Math.max(1, r.integer("dated-events.newyear.min-distance-between-firework", 34)));
        EventSettings.Halloween halloween = halloween(r);
        EventSettings.Easter easter = easter(r, knownItem);
        for (String key : r.keys("dated-events")) {
            if (SeasonEvent.Type.ofBuiltin(key) == SeasonEvent.Type.CUSTOM) {
                r.warn("dated-events." + key, "unknown built-in event (christmas, newyear, halloween, easter);"
                        + " custom ones belong to custom-events.yml");
            }
        }
        r.reportUnknown(Set.of(), null);

        ConfigReader c = new ConfigReader(custom.yaml(), custom.path().toString(), warnings);
        c.integer(ConfigMigrator.VERSION_KEY, 1);
        Set<String> ids = new HashSet<>();
        list.forEach(e -> ids.add(e.id()));
        for (SeasonEvent e : customEvents(c, calendar.americanDateFormat(), cal)) {
            if (!ids.add(e.id())) {
                c.warn("custom-events", "event " + e.id() + " is defined more than once, the first one applies");
                continue;
            }
            list.add(e);
        }
        c.reportUnknown(Set.of(), null);

        return new EventSettings(list, christmas, newYear, halloween, easter, sources, warnings);
    }

    /** file and its content */
    private record Source(Path path, YamlConfiguration yaml) {
    }

    private static Source source(Path dataFolder, String name) throws ConfigException {
        Path own = dataFolder.resolve(name);
        YamlFiles.copyResourceIfMissing(name, own);
        YamlConfiguration yaml = YamlFiles.load(own);
        ConfigMigrator migrator = ConfigMigrator.forFile(own.toString());
        if (migrator.migrate(yaml)) {
            try {
                YamlFiles.saveAtomically(yaml, own);
            } catch (IOException e) {
                throw new ConfigException("cannot save migrated " + own + ": " + e.getMessage(), e);
            }
        }
        return new Source(own, yaml);
    }

    private static SeasonEvent builtin(ConfigReader r, Builtin b, boolean american, GameCalendar cal) {
        String p = "dated-events." + b.type().id() + ".";
        EventSchedule schedule = dated(r, p, american, cal, b.start(), b.stop());
        return new SeasonEvent(b.type().id(), b.type(),
                r.bool(p + "enabled", true),
                r.string(p + "name", b.name()),
                r.bool(p + "display-event", true),
                Set.copyOf(r.list(p + "disabled-worlds", List.of())),
                schedule,
                r.has(p + "commands.start") ? r.list(p + "commands.start", List.of()) : b.startLines(),
                r.list(p + "commands.stop", List.of()));
    }

    /** {@code times.event-start.date} and {@code times.event-stop.date}; an invalid date = the default */
    private static EventSchedule.Dated dated(ConfigReader r, String p, boolean american, GameCalendar cal,
                                             String defStart, String defStop) {
        EventDate start = date(r, p + "times.event-start.date", american, cal, defStart);
        EventDate stop = date(r, p + "times.event-stop.date", american, cal, defStop == null ? defStart : defStop);
        if (stop.repeat().ordinal() < start.repeat().ordinal() && stop.repeat() == EventDate.Repeat.MONTHLY) {
            r.warn(p + "times.event-stop.date", "the end has only a day, the start also a month; the end is taken in the same month");
        }
        return new EventSchedule.Dated(start, stop);
    }

    private static EventDate date(ConfigReader r, String path, boolean american, GameCalendar cal, String def) {
        String text = r.string(path, def);
        if (text == null) {
            r.warn(path, "missing date");
            text = "1";
        }
        try {
            EventDate d = EventDate.parse(text, american);
            if (d.month() > cal.months().size()) {
                r.warn(path, "month " + d.month() + " is not in the calendar (it has " + cal.months().size() + "), the event will not happen");
            }
            return d;
        } catch (IllegalArgumentException e) {
            r.warn(path, e.getMessage() + (def != null ? ", using " + def : ""));
            return def == null ? new EventDate(1, 0, 0) : EventDate.parse(def, false);
        }
    }

    private static EventSettings.Christmas christmas(ConfigReader r, Predicate<String> knownItem) {
        String p = "dated-events.christmas.";
        return new EventSettings.Christmas(
                r.bool(p + "night-particles", true),
                r.bool(p + "christmas-trees.enabled", true),
                r.bool(p + "presents.enabled", true),
                Math.max(0, r.integer(p + "presents.chunk-distance-between-spawns", 10)),
                loot(r, p + "presents.loot", DEFAULT_PRESENT_LOOT, knownItem));
    }

    private static EventSettings.Easter easter(ConfigReader r, Predicate<String> knownItem) {
        String p = "dated-events.easter.";
        return new EventSettings.Easter(
                r.bool(p + "spawn-killer-bunnies", true),
                fraction(r, p + "killer-bunny-spawn-rate", 0.1),
                r.bool(p + "easter-eggs.enabled", true),
                Math.max(0, r.integer(p + "easter-eggs.chunk-distance-between-spawns", 4)),
                loot(r, p + "easter-eggs.loot", DEFAULT_EGG_LOOT, knownItem));
    }

    private static EventSettings.Halloween halloween(ConfigReader r) {
        String p = "dated-events.halloween.";
        EventSettings.Halloween d = EventSettings.Halloween.DEFAULT;
        return new EventSettings.Halloween(
                r.bool(p + "play-particles-around-halloween-mobs", true),
                r.bool(p + "witch-extra-potions.enabled", true),
                r.list(p + "witch-extra-potions.effects", EventSettings.Halloween.DEFAULT_WITCH_EFFECTS),
                chance(r, p + "duplicating-mobs", d.duplicating()),
                chance(r, p + "invisible-mobs", d.invisible()),
                chance(r, p + "fast-mobs", d.fast()),
                chance(r, p + "flame-skeletons", d.flameSkeletons()),
                chance(r, p + "armored-mobs", d.armored()),
                chance(r, p + "zombies-hold-pie", d.zombiePie()),
                chance(r, p + "vindicators-at-night", d.vindicatorsAtNight()),
                chance(r, p + "pumpkin-heads", d.pumpkinHeads()),
                chance(r, p + "more-bats", d.moreBats()),
                chance(r, p + "more-spiders", d.moreSpiders()));
    }

    private static EventSettings.Chance chance(ConfigReader r, String section, EventSettings.Chance def) {
        return new EventSettings.Chance(r.bool(section + ".enabled", def.enabled()),
                fraction(r, section + ".spawn-chance", def.chance()));
    }

    /** chance 0–1 ({@code 0.07}) or percent ({@code 7%}) */
    static double fraction(ConfigReader r, String path, double def) {
        if (!r.has(path)) return def;
        String text = r.string(path, "").trim();
        if (text.endsWith("%")) return r.percent(path, def * 100) / 100.0;
        double v = r.number(path, def);
        if (v < 0 || v > 1) {
            r.warn(path, "chance " + v + " is not between 0 and 1 (e.g. 0.07 or 7%), using " + def);
            return def;
        }
        return v;
    }

    private static LootTable loot(ConfigReader r, String path, List<String> def, Predicate<String> knownItem) {
        List<String> lines = r.list(path, def);
        return LootTable.parse(lines, knownItem, (line, problem) -> r.warn(path, "'" + line + "': " + problem));
    }

    private static List<SeasonEvent> customEvents(ConfigReader c, boolean american, GameCalendar cal) {
        List<SeasonEvent> out = new ArrayList<>();
        String root = "custom-events.";
        for (String id : c.keys(root + "dated-events")) {
            String p = root + "dated-events." + id + ".";
            out.add(new SeasonEvent(id, SeasonEvent.Type.CUSTOM,
                    c.bool(p + "enabled", false),
                    c.string(p + "name", id),
                    c.bool(p + "display-event", true),
                    Set.copyOf(c.list(p + "disabled-worlds", List.of())),
                    dated(c, p, american, cal, null, null),
                    c.list(p + "commands.start", List.of()),
                    c.list(p + "commands.stop", List.of())));
        }
        for (String id : c.keys(root + "weekly-events")) {
            String p = root + "weekly-events." + id + ".";
            String day = c.string(p + "day", "");
            OptionalInt index = weekday(day, cal);
            if (index.isEmpty()) {
                c.warn(p + "day", "day '" + day + "' is not in the calendar week (" + String.join(", ", cal.weekdays())
                        + "), event skipped");
                continue;
            }
            out.add(new SeasonEvent(id, SeasonEvent.Type.CUSTOM,
                    c.bool(p + "enabled", false),
                    c.string(p + "name", id),
                    c.bool(p + "display-event", true),
                    Set.copyOf(c.list(p + "disabled-worlds", List.of())),
                    new EventSchedule.Weekly(index.getAsInt()),
                    c.list(p + "commands.start", List.of()),
                    c.list(p + "commands.stop", List.of())));
        }
        for (String id : c.keys(root + "daily-events")) {
            String p = root + "daily-events." + id + ".";
            out.add(new SeasonEvent(id, SeasonEvent.Type.CUSTOM,
                    c.bool(p + "enabled", false),
                    c.string(p + "name", id),
                    c.bool(p + "display-event", false),
                    Set.copyOf(c.list(p + "disabled-worlds", List.of())),
                    new EventSchedule.Daily(),
                    c.list(p + "commands", List.of()),
                    List.of()));
        }
        return out;
    }

    /**
     * Weekday: name from the calendar (also the display one), English name in a seven-day week, or position
     * from 1. An unknown name skips the event with a warning.
     */
    static OptionalInt weekday(String text, GameCalendar cal) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return OptionalInt.empty();
        List<String> days = cal.weekdays();
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).toLowerCase(Locale.ROOT).equals(t)) return OptionalInt.of(i);
        }
        if (days.size() == 7 && ENGLISH_WEEKDAYS.contains(t)) return OptionalInt.of(ENGLISH_WEEKDAYS.indexOf(t));
        try {
            int n = Integer.parseInt(t);
            if (n >= 1 && n <= days.size()) return OptionalInt.of(n - 1);
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return OptionalInt.empty();
    }

    /** default loot of presents */
    static final List<String> DEFAULT_PRESENT_LOOT = List.of(
            "2-8:EXPERIENCE_BOTTLE", "6-20:FIREWORK_ROCKET", "1-12:FIRE_CHARGE", "2-16:POTATO", "2-8:CARROT",
            "1-16:COAL", "1-16:SNOWBALL", "2-12:SWEET_BERRIES", "6-16:COOKIE", "1-2:ENDER_PEARL",
            "4-12:BAKED_POTATO", "2-8:BOOK", "4-12:BREAD", "1:DIAMOND", "1:GOLD_INGOT", "2-8:COOKED_RABBIT",
            "2-8:COOKED_MUTTON", "2-8:COOKED_BEEF", "2-8:COOKED_CHICKEN", "2-8:COOKED_PORKCHOP",
            "2-8:COOKED_SALMON", "1-3:GOLDEN_APPLE", "2-8:GOLDEN_CARROT", "1:LEATHER_HELMET",
            "2-16:SPECTRAL_ARROW", "1:CLOCK", "1-3:MAGMA_CREAM", "1-3:NETHER_WART", "1-3:BLAZE_ROD",
            "1-6:GOLD_NUGGET", "2-8:COOKED_COD", "1-4:SPRUCE_SAPLING");

    /** default loot of eggs */
    static final List<String> DEFAULT_EGG_LOOT = List.of(
            "3-6:COOKIE", "2-16:CARROT", "1:RABBIT_FOOT", "1:CAKE", "1-20:LEATHER", "2-24:GOLDEN_CARROT",
            "2-48:POTATO", "2-48:WHEAT", "2-16:GOLDEN_APPLE", "2-48:BEETROOT", "1:SADDLE", "1:NAME_TAG",
            "2-10:EGG", "2-10:MELON_SEEDS", "2-10:PUMPKIN_SEEDS", "2-16:BLUE_DYE", "2-16:RED_DYE",
            "2-16:YELLOW_DYE", "2-32:BAMBOO", "2-32:OAK_LOG", "1:HONEY_BOTTLE", "1-8:HONEYCOMB", "1-8:CACTUS",
            "1-12:SLIME_BALL", "1-5:HAY_BLOCK", "1:MUSHROOM_STEW", "1-12:GOLD_INGOT", "1-6:DIAMOND");
}
