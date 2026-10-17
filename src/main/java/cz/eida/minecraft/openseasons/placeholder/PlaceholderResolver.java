/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.placeholder;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.SubSeason;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Placeholder values without depending on PlaceholderAPI. A key asks either about the player's world
 * ({@code season}) or about a named world ({@code season_world_nether}).
 */
public final class PlaceholderResolver {

    /** keys of the {@code mc215os} namespace */
    public static final List<String> KEYS = List.of(
            "season", "season_id", "next_season", "next_season_id", "days_until_next_season",
            "season_length", "season_progress", "sub_season",
            "day", "month", "month_name", "year", "weekday", "date", "time", "enabled",
            "active_events", "next_event", "days_until_next_event");

    /**
     * keys by the player's location: season including the permanent season of a WorldGuard region
     * ({@code permanent-season}); empty without a player
     */
    public static final List<String> LOCAL_KEYS = List.of("local_season", "local_season_id");

    public static final String DISABLED = "DISABLED";
    /** no event */
    public static final String NO_EVENT = "-";

    /** calendar events for placeholders; callable from any thread */
    public interface Events {
        Events NONE = new Events() {
            @Override
            public List<String> activeEventNames(String world) {
                return List.of();
            }

            @Override
            public Optional<NextEvent> nextEvent(String world) {
                return Optional.empty();
            }
        };

        /** display names of the running events (raw, with colours {@code &}) */
        List<String> activeEventNames(String world);

        /** the nearest event that will begin in the world */
        Optional<NextEvent> nextEvent(String world);
    }

    /** names of the events beginning on the same day, and in how many days */
    public record NextEvent(List<String> names, int daysUntil) {
    }

    private volatile Events events = Events.NONE;

    /** world day time in ticks (0 = 6:00); empty when the world is not loaded */
    public interface WorldTime {
        OptionalLong timeOfDay(String world);
    }

    /** @param world {@code null} = the player's world */
    public record Request(String key, String world) {
    }

    private final SeasonService seasons;
    private final Supplier<LoadedConfig> config;
    private final WorldTime time;

    public PlaceholderResolver(SeasonService seasons, Supplier<LoadedConfig> config, WorldTime time) {
        this.seasons = seasons;
        this.config = config;
        this.time = time;
    }

    /** source of calendar events; without it the event placeholders return {@link #NO_EVENT} */
    public void setEvents(Events events) {
        this.events = events == null ? Events.NONE : events;
    }

    /**
     * Splits the parameters into a key and an optional world. An exact match of a key wins; otherwise the
     * longest key followed by an underscore and the name of a world with seasons is searched for.
     */
    public Optional<Request> parse(String params, Collection<String> keys) {
        if (keys.contains(params)) return Optional.of(new Request(params, null));
        return keys.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(k -> params.startsWith(k + "_"))
                .map(k -> new Request(k, params.substring(k.length() + 1)))
                .filter(r -> seasons.state(r.world()).isPresent())
                .findFirst();
    }

    /**
     * {@code %mc215os_<params>%}.
     *
     * @param playerWorld the player's world, {@code null} without a player or for an offline player
     * @return {@code null} for an unknown key or world (PlaceholderAPI then leaves the text unchanged),
     * an empty text for a player key without a player
     */
    public String resolveOwn(String params, String playerWorld) {
        Optional<Request> request = parse(params, KEYS);
        if (request.isEmpty()) return null;
        String world = request.get().world() != null ? request.get().world() : playerWorld;
        if (world == null) return "";
        return value(request.get().key(), world);
    }

    /**
     * A key from {@link #LOCAL_KEYS} for the season at the player's location.
     *
     * @return {@code null} for another key, {@link #DISABLED} without a season at the location
     */
    public String local(String key, Optional<Season> season) {
        return switch (key) {
            case "local_season" -> season.map(s -> config.get().lang().seasonDisplayName(s)).orElse(DISABLED);
            case "local_season_id" -> season.map(Season::name).orElse(DISABLED);
            default -> null;
        };
    }

    /** value of a key from {@link #KEYS} for a world; {@code null} when the world has no seasons or the key does not exist */
    public String value(String key, String world) {
        Optional<WorldSeasonState> found = seasons.state(world);
        if (found.isEmpty()) return null;
        WorldSeasonState s = found.get();
        LoadedConfig c = config.get();
        GameCalendar cal = seasons.calendar();
        CalendarDate date = s.date();

        return switch (key) {
            case "season" -> s.enabled() ? c.lang().seasonDisplayName(s.season()) : DISABLED;
            case "season_id" -> s.enabled() ? s.season().name() : DISABLED;
            case "next_season" -> c.lang().seasonDisplayName(cal.nextSeasonChange(date, s.season()).season());
            case "next_season_id" -> cal.nextSeasonChange(date, s.season()).season().name();
            case "days_until_next_season" -> s.enabled() && seasons.automaticCycling()
                    ? Integer.toString(cal.nextSeasonChange(date, s.season()).daysUntil()) : DISABLED;
            case "season_length" -> Integer.toString(cal.seasonLength(s.season()));
            case "season_progress" -> Integer.toString((int) Math.floor(progress(world, s)));
            case "sub_season" -> SubSeason.fromProgress(progress(world, s)).name();
            case "day" -> Integer.toString(date.day());
            case "month" -> Integer.toString(date.month());
            case "month_name" -> cal.monthName(date);
            case "year" -> Integer.toString(date.year());
            case "weekday" -> cal.weekdayName(date);
            case "date" -> c.calendar().formatDate(date);
            case "time" -> {
                OptionalLong t = time.timeOfDay(world);
                yield t.isPresent() ? c.calendar().timeFormat().format(t.getAsLong()) : null;
            }
            case "enabled" -> c.lang().get(s.enabled() ? "ENABLED" : "DISABLED");
            // events: names separated by commas (raw, with colours &)
            case "active_events" -> joined(events.activeEventNames(world));
            case "next_event" -> events.nextEvent(world).map(n -> joined(n.names())).orElse(NO_EVENT);
            case "days_until_next_event" -> !s.enabled() ? DISABLED
                    : events.nextEvent(world).map(n -> Integer.toString(n.daysUntil())).orElse(NO_EVENT);
            default -> null;
        };
    }

    private static String joined(List<String> names) {
        return names.isEmpty() ? NO_EVENT : String.join(", ", names);
    }

    private double progress(String world, WorldSeasonState s) {
        OptionalLong t = time.timeOfDay(world);
        double fraction = t.isPresent() ? Math.floorMod(t.getAsLong() + 6000, 24000L) / 24000.0 : 0.0;
        return seasons.calendar().seasonProgress(s.date(), s.season(), fraction);
    }
}
