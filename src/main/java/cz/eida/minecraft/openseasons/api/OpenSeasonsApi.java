/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.api;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Public API of OpenSeasons for other plugins. The instance is in the Bukkit {@code ServicesManager}
 * while OpenSeasons runs: {@code OpenSeasonsApi.get().ifPresent(api -> ...)}.
 *
 * <p>With automatic cycling the season follows the ranges in the calendar: it is aligned at start and on
 * every day change; {@link #setSeason} lasts until the next day change (cancelling {@code SeasonChangeEvent}
 * keeps a forced season). The date follows the world clock, so the year matches the world's age until
 * someone sets the date differently. Reading works from any thread, changes only from the main thread.
 * Changes are reported by {@link cz.eida.minecraft.openseasons.api.event.SeasonChangeEvent}
 * (cancellable) and {@link cz.eida.minecraft.openseasons.api.event.DayChangeEvent}.
 */
public interface OpenSeasonsApi {

    /** the running OpenSeasons; empty when it is not installed or disabled */
    static Optional<OpenSeasonsApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(OpenSeasonsApi.class));
    }

    /** calendar (months, week, season starts); may differ after a configuration reload */
    GameCalendar calendar();

    /** the world has seasons, even if they are disabled */
    boolean hasSeasons(World world);

    /** seasons are enabled in the world */
    boolean isEnabled(World world);

    /** season of an enabled world; empty when the world has no seasons or they are disabled */
    Optional<Season> season(World world);

    /**
     * Display name of the season from {@code names.yml}, otherwise from {@code lang.yml} (raw, may contain
     * colours {@code &}), e.g. "Summer".
     */
    String seasonName(Season season);

    /** season that would apply by the calendar on today's date of the world (without manual changes) */
    Optional<Season> calendarSeason(World world);

    /** date of the world, also of a disabled one; empty when the world has no seasons */
    Optional<CalendarDate> date(World world);

    /** year by {@link #date}; without a set date it matches the age of the world */
    OptionalInt year(World world);

    /**
     * Sets the season of an enabled world without changing the date. With automatic cycling it lasts until the next day
     * change, then the season is aligned with the calendar.
     *
     * @return false when the world has no seasons or they are disabled, the season already applies, or a
     * {@code SeasonChangeEvent} listener cancelled the change
     */
    boolean setSeason(World world, Season season);

    /**
     * Sets the date of the world; it keeps running with its clock. With automatic cycling the season is aligned
     * with the calendar.
     *
     * @return false when the world has no seasons
     */
    boolean setDate(World world, CalendarDate date);

    /**
     * Enables or disables seasons. A normal world that does not have them yet gets them by enabling
     * (date by the world age, season by the calendar). The Nether and the End have no seasons.
     *
     * @return false when nothing changed or the world does not allow it
     */
    boolean setEnabled(World world, boolean enabled);

    /**
     * Calendar events currently running in the world (also hidden ones), as keys from the event
     * files: {@code christmas}, {@code newyear}, {@code halloween}, {@code easter} or custom.
     * Start and end are announced by {@link cz.eida.minecraft.openseasons.api.event.CalendarEventStartEvent}
     * (cancellable) and {@link cz.eida.minecraft.openseasons.api.event.CalendarEventEndEvent}.
     */
    default List<String> activeEvents(World world) {
        return List.of();
    }

    /** display name of the event (raw, with colours {@code &}), e.g. "&cChristmas"; empty for an unknown one */
    default Optional<String> eventName(String eventId) {
        return Optional.empty();
    }

    /**
     * Season at a location: in a WorldGuard region with the {@code permanent-season} flag the permanent season of the region,
     * otherwise {@link #season(World)}; also periods and real seasons from {@code real-calendar.yml}. Empty in a world
     * without enabled seasons. A region applies to the whole chunk column. Off the main thread only from the cache.
     */
    default Optional<Season> seasonAt(Location location) {
        return location == null ? Optional.empty() : season(location.getWorld());
    }

    /** whether seasons may be shown at a location (landscape colours, particles, phenomena): flag {@code season-effects} */
    default boolean seasonEffectsAllowed(Location location) {
        return true;
    }

    /** whether blocks may change by season at a location (snow, ice, plants): flag {@code season-block-changes} */
    default boolean seasonBlockChangesAllowed(Location location) {
        return true;
    }

    /** whether seasonal animals and monsters may appear at a location: flag {@code season-mob-spawning} */
    default boolean seasonMobSpawningAllowed(Location location) {
        return true;
    }
}
