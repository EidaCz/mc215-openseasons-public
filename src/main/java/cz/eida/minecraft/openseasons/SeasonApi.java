/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.api.OpenSeasonsApi;
import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.events.EventService;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** {@link OpenSeasonsApi} on top of the plugin's {@link SeasonService}. */
final class SeasonApi implements OpenSeasonsApi {

    private final Supplier<SeasonService> seasons;
    private final Supplier<LoadedConfig> config;
    private final BiPredicate<World, Boolean> enabler;
    private final BooleanSupplier mainThread;
    private volatile EventService events;
    private volatile RegionService regions;

    /**
     * @param enabler    turning seasons in a world on and off (creates the state, takes over the clock)
     * @param mainThread we are on the main server thread
     */
    SeasonApi(Supplier<SeasonService> seasons, Supplier<LoadedConfig> config, BiPredicate<World, Boolean> enabler,
              BooleanSupplier mainThread) {
        this.seasons = seasons;
        this.config = config;
        this.enabler = enabler;
        this.mainThread = mainThread;
    }

    @Override
    public String seasonName(Season season) {
        return config.get().lang().seasonDisplayName(season);
    }

    @Override
    public GameCalendar calendar() {
        return seasons.get().calendar();
    }

    @Override
    public boolean hasSeasons(World world) {
        return state(world).isPresent();
    }

    @Override
    public boolean isEnabled(World world) {
        return state(world).map(WorldSeasonState::enabled).orElse(false);
    }

    @Override
    public Optional<Season> season(World world) {
        return state(world).filter(WorldSeasonState::enabled).map(WorldSeasonState::season);
    }

    @Override
    public Optional<Season> calendarSeason(World world) {
        GameCalendar cal = calendar();
        return state(world).map(s -> cal.seasonAt(s.date()));
    }

    @Override
    public Optional<CalendarDate> date(World world) {
        return state(world).map(WorldSeasonState::date);
    }

    @Override
    public OptionalInt year(World world) {
        return state(world).map(s -> OptionalInt.of(s.date().year())).orElse(OptionalInt.empty());
    }

    @Override
    public boolean setSeason(World world, Season season) {
        requireMainThread();
        return isEnabled(world) && seasons.get().setSeason(world.getName(), season, SeasonService.Cause.API);
    }

    @Override
    public boolean setDate(World world, CalendarDate date) {
        requireMainThread();
        return seasons.get().setDate(world.getName(), date, SeasonService.Cause.API);
    }

    @Override
    public boolean setEnabled(World world, boolean enabled) {
        requireMainThread();
        return enabler.test(world, enabled);
    }

    /** settings by regions; without them the world season and everything allowed */
    void setRegions(RegionService regions) {
        this.regions = regions;
    }

    @Override
    public Optional<Season> seasonAt(Location location) {
        RegionService r = regions;
        if (location == null || location.getWorld() == null) return Optional.empty();
        return r == null ? season(location.getWorld()) : r.seasonAt(location);
    }

    @Override
    public boolean seasonEffectsAllowed(Location location) {
        RegionService r = regions;
        return r == null || location == null || location.getWorld() == null || r.effectsAllowed(location);
    }

    @Override
    public boolean seasonBlockChangesAllowed(Location location) {
        RegionService r = regions;
        return r == null || location == null || location.getWorld() == null
                || r.blockChangesAllowed(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public boolean seasonMobSpawningAllowed(Location location) {
        RegionService r = regions;
        return r == null || location == null || location.getWorld() == null || r.mobSpawningAllowed(location);
    }

    /** calendar events; none until they start */
    void setEvents(EventService events) {
        this.events = events;
    }

    @Override
    public List<String> activeEvents(World world) {
        EventService e = events;
        return e == null || world == null ? List.of() : e.activeIds(world.getName());
    }

    @Override
    public Optional<String> eventName(String eventId) {
        EventService e = events;
        return e == null || eventId == null ? Optional.empty() : e.name(eventId);
    }

    private Optional<WorldSeasonState> state(World world) {
        return world == null ? Optional.empty() : seasons.get().state(world.getName());
    }

    private void requireMainThread() {
        if (!mainThread.getAsBoolean()) {
            throw new IllegalStateException("OpenSeasons: the season and date can only be changed from the main thread");
        }
    }
}
