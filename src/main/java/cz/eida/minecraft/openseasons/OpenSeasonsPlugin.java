/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.api.OpenSeasonsApi;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.ConfigLoader;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.config.RealCalendarSettings;
import cz.eida.minecraft.openseasons.events.EventPlaces;
import cz.eida.minecraft.openseasons.events.EventService;
import cz.eida.minecraft.openseasons.flora.FloraService;
import cz.eida.minecraft.openseasons.persistence.PlayerSettingsStore;
import cz.eida.minecraft.openseasons.persistence.StateStore;
import cz.eida.minecraft.openseasons.placeholder.PlaceholderResolver;
import cz.eida.minecraft.openseasons.placeholder.PlaceholderHook;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.SeasonSource;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import cz.eida.minecraft.openseasons.effects.AuroraEffect;
import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import cz.eida.minecraft.openseasons.effects.SeasonalEffects;
import cz.eida.minecraft.openseasons.entity.SeasonalMobs;
import cz.eida.minecraft.openseasons.network.ClientColors;
import cz.eida.minecraft.openseasons.sky.SkyPhenomena;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OpenSeasons: calendar and seasons of worlds, configuration and placeholders {@code %mc215os_*%}.
 * The public API is {@link OpenSeasonsApi}.
 */
public final class OpenSeasonsPlugin extends JavaPlugin implements Listener {

    private static final String STATE_FILE = "data/worlds.yml";
    private static final String PLAYERS_FILE = "data/players.yml";

    private volatile LoadedConfig config;
    private SeasonService seasons;
    private StateStore store;
    private PlayerSettingsStore playerSettings;
    /** colours turned off at start; without packetevents they are saved unchanged */
    private Set<UUID> colorsDisabledAtStart = Set.of();
    private SeasonApi api;
    private PlaceholderResolver resolver;
    /** only when PlaceholderAPI runs; its classes are not touched from here (without PAPI the start would fail) */
    private PlaceholderHook placeholders;
    private final Set<String> unmanagedReported = new HashSet<>();
    private CalendarClock clock;
    private SkyPhenomena sky;
    private SeasonalEffects effects;
    private AuroraEffect aurora;
    /** seasonal animals and monsters */
    private SeasonalMobs seasonalMobs;
    /** who sees season particles ({@code /toggleseasonparticles}) */
    private ParticleViewers particleViewers;
    /** landscape colours on the client; only with the packetevents plugin */
    private ClientColors clientColors;
    /** seasonal plants in the world and the journal of block changes */
    private FloraService flora;
    /** calendar events: Christmas, New Year, Halloween, Easter and custom ones */
    private EventService events;
    /** on the very first start all loaded worlds get seasons, not only by on-new-world-creation */
    private boolean firstRun;
    /** settings by WorldGuard regions; global without it */
    private RegionService regions;
    /** WorldGuard flags are registered before WorldGuard loads the regions */
    @Override
    public void onLoad() {
        RegionService.registerFlags(this);
    }

    @Override
    public void onEnable() {
        try {
            config = new ConfigLoader(dataFolder()).load();
        } catch (ConfigException e) {
            getLogger().severe("The configuration cannot be loaded, OpenSeasons is disabling itself: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        report(config);

        seasons = new SeasonService(config.calendar().calendar(), config.main().cycling().automatic(),
                new BukkitSeasonEvents(this));
        regions = RegionService.create(this, seasons);
        regions.setRealCalendar(loadRealCalendar());
        regions.start();
        store = new StateStore(dataFolder().resolve(STATE_FILE));
        List<String> warnings = new ArrayList<>();
        StateStore.Snapshot snapshot;
        try {
            snapshot = store.load(warnings);
        } catch (ConfigException e) {
            getLogger().severe("The world state cannot be loaded, OpenSeasons is disabling itself: " + e.getMessage());
            store = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        StateStore.Clock kind = clockKind(config);
        if (snapshot.clock() != kind && !snapshot.worlds().isEmpty()) {
            warnings.add("The date is now counted in " + (kind == StateStore.Clock.GAME ? "game" : "real")
                    + " days; the world dates stay and are bound to the clocks again.");
            seasons.load(snapshot.worlds().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> e.getValue().withDayOffset(OptionalLong.empty()))));
        } else {
            seasons.load(snapshot.worlds());
        }
        warnings.forEach(getLogger()::warning);

        api = new SeasonApi(this::seasons, this::config, this::setSeasonsEnabled, getServer()::isPrimaryThread);
        api.setRegions(regions);
        clock = new CalendarClock(getServer(), getLogger(), this::monthOf, CalendarClock.Rules.BUKKIT);
        clock.restorePaused(snapshot.timePaused());
        firstRun = snapshot.worlds().isEmpty();
        getServer().getWorlds().forEach(this::registerWorld);
        firstRun = false;

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, clock::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, this::saveIfDirty, 1200L, 1200L);

        resolver = new PlaceholderResolver(seasons, () -> config, this::timeOfDay);
        registerPlaceholders();

        PlayerSettingsStore.Settings playerOff = loadPlayerSettings();
        colorsDisabledAtStart = playerOff.colorsDisabled();
        particleViewers = new ParticleViewers(this);
        particleViewers.setDisabled(playerOff.particlesDisabled());
        sky = new SkyPhenomena(this, () -> config, seasons, particleViewers, regions);
        sky.start();
        effects = new SeasonalEffects(this, () -> config, seasons, particleViewers, regions);
        effects.start();
        aurora = new AuroraEffect(this, () -> config, seasons, particleViewers, regions);
        aurora.start();
        seasonalMobs = new SeasonalMobs(this, () -> config, seasons, regions);
        seasonalMobs.start();
        if (getServer().getPluginManager().isPluginEnabled("packetevents")) {
            clientColors = new ClientColors(this, () -> config, seasons, regions);
            clientColors.setColorsDisabled(playerOff.colorsDisabled());
            clientColors.start();
        } else {
            getLogger().info("Landscape colours on the client are off: the packetevents plugin is missing.");
        }
        flora = new FloraService(this, () -> config, seasons, regions);
        flora.start();
        events = new EventService(this, () -> config, seasons, particleViewers, dataFolder(), EventPlaces.of(regions),
                new EventService.RealEvents() {
                    @Override
                    public Set<String> world(String world) {
                        return regions.realCalendar().worldEvents(world, regions.today());
                    }

                    @Override
                    public Set<String> regions() {
                        return regions.realCalendar().regionEvents(regions.today());
                    }
                });
        events.start();
        resolver.setEvents(events);
        api.setEvents(events);

        getServer().getServicesManager().register(OpenSeasonsApi.class, api, this, ServicePriority.Normal);

        bind("openseasons", new OpenSeasonsCommand(this));
        bind("season", new SeasonCommands(this));
        bind("toggleseasoncolors", new ToggleSeasonColorsCommand(this));
        bind("toggleseasonparticles", new ToggleSeasonParticlesCommand(this));
    }

    @Override
    public void onDisable() {
        if (regions != null) regions.stop();
        if (sky != null) sky.stop();
        if (effects != null) effects.stop();
        if (aurora != null) aurora.stop();
        if (seasonalMobs != null) seasonalMobs.stop();
        if (clientColors != null) clientColors.stop();
        if (placeholders != null) placeholders.unregister();
        placeholders = null;
        if (flora != null) flora.stop();
        if (events != null) events.stop();
        if (clock != null) clock.releaseAll();
        if (seasons != null && store != null) save();
    }

    /** settings by regions (WorldGuard); everything global without it */
    RegionService regions() {
        return regions;
    }

    SkyPhenomena sky() {
        return sky;
    }

    SeasonalEffects effects() {
        return effects;
    }

    /** landscape colours on the client, {@code null} without packetevents */
    ClientColors clientColors() {
        return clientColors;
    }

    AuroraEffect aurora() {
        return aurora;
    }

    SeasonalMobs seasonalMobs() {
        return seasonalMobs;
    }

    /** calendar events */
    EventService events() {
        return events;
    }

    ParticleViewers particleViewers() {
        return particleViewers;
    }

    LoadedConfig config() {
        return config;
    }

    SeasonService seasons() {
        return seasons;
    }

    PlaceholderResolver resolver() {
        return resolver;
    }

    CalendarClock clock() {
        return clock;
    }

    /** seasonal plants, {@code null} before the start */
    FloraService flora() {
        return flora;
    }

    /** reloads the configuration; the world state stays */
    void reload() throws ConfigException {
        LoadedConfig loaded = new ConfigLoader(dataFolder()).load();
        LoadedConfig previous = config;
        config = loaded;
        report(loaded);
        if (clockKind(previous) != clockKind(loaded)) seasons.resetClocks();
        seasons.reconfigure(loaded.calendar().calendar(), loaded.main().cycling().automatic());
        registerPlaceholders();
        regions.setRealCalendar(loadRealCalendar());
        getServer().getWorlds().forEach(this::registerWorld);
        if (clientColors != null) clientColors.reload();
        if (flora != null) flora.reload();
        if (events != null) events.reload();
    }

    /**
     * Turns seasons in a world on or off (API and commands). A normal world without seasons gets them
     * on turning on, with the date by its age.
     */
    boolean setSeasonsEnabled(World world, boolean enabled) {
        String name = world.getName();
        if (seasons.state(name).isEmpty()) {
            if (!enabled || world.getEnvironment() != World.Environment.NORMAL) return false;
            WorldSeasonState state = seasons.register(name, exactInitialState(world));
            getLogger().info("World " + name + " got seasons: " + state.season() + ", " + state.date());
            syncWorld(world);
            updateClock(world);
            return true;
        }
        boolean changed = seasons.setEnabled(name, enabled);
        if (changed) updateClock(world);
        return changed;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        registerWorld(event.getWorld());
    }

    /** show the displays of phenomena in flight to a newly joined player too */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (particleViewers != null) particleViewers.join(event.getPlayer());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        seasons.forgetClock(event.getWorld().getName());
        clock.release(event.getWorld());
    }

    private Path dataFolder() {
        return getDataFolder().toPath();
    }

    /**
     * A world with a known state only binds its date to the clock. A new normal world gets seasons by
     * {@code on-new-world-creation}; with {@code default-season: NONE} it stays without seasons (except
     * on the very first start). The Nether and the End have no seasons.
     */
    private void registerWorld(World world) {
        String name = world.getName();
        Optional<WorldSeasonState> known = seasons.state(name);
        if (known.isPresent()) {
            syncWorld(world);
            seasons.state(name).map(WorldSeasonState::date).filter(d -> !d.equals(known.get().date()))
                    .ifPresent(d -> getLogger().info("World " + name + ": date by the world clock "
                            + known.get().date() + " → " + d));
            updateClock(world);
            return;
        }
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        MainConfig.NewWorlds rules = config.main().newWorlds();
        Optional<WorldSeasonState> sync = rules.syncWithOtherWorlds() ? syncSource() : Optional.empty();
        if (!firstRun && sync.isEmpty() && rules.defaultSeason().isEmpty()) {
            if (unmanagedReported.add(name)) {
                getLogger().info("World " + name + " has no seasons (on-new-world-creation.default-season: NONE).");
            }
            return;
        }
        WorldSeasonState initial = sync.isEmpty() && rules.defaultSeason().isEmpty()
                ? exactInitialState(world) : seasons.newWorldState(rules.defaultSeason(), sync);
        WorldSeasonState state = seasons.register(name, initial);
        getLogger().info("World " + name + " got seasons: " + state.season() + ", " + state.date());
        syncWorld(world);
        updateClock(world);
    }

    /** date by the age of the world; with real days the 1st day of year 1 */
    private WorldSeasonState exactInitialState(World world) {
        if (config.calendar().realLifeDays()) return seasons.newWorldState(Optional.empty(), Optional.empty());
        return seasons.exactState(SeasonService.clockDay(world.getFullTime()));
    }

    /** binds the date to the world clock (game days), or to real days */
    private void syncWorld(World world) {
        LoadedConfig c = config;
        if (!c.calendar().enabled()) return;
        seasons.syncClock(world.getName(), c.calendar().realLifeDays() ? today(c)
                : SeasonService.clockDay(world.getFullTime()));
    }

    private static long today(LoadedConfig c) {
        return LocalDate.now(c.calendar().timezone()).toEpochDay();
    }

    private static StateStore.Clock clockKind(LoadedConfig c) {
        return c.calendar().realLifeDays() ? StateStore.Clock.REAL_DAYS : StateStore.Clock.GAME;
    }

    /** takes over or releases the world clock, see {@link #drivesTime} */
    private void updateClock(World world) {
        clock.update(world, drivesTime(world));
    }

    /**
     * OpenSeasons drives the time of a world (day and night length by the month, {@link CalendarClock})
     * when {@code modify-daynight-length} and the calendar are on and the world has seasons turned on.
     * OpenSeasons then keeps {@code advance_time} off and moves the time itself; other worlds stay vanilla.
     */
    private boolean drivesTime(World world) {
        LoadedConfig c = config;
        return c.main().modifyDayNightLength() && c.calendar().enabled()
                && world.getEnvironment() == World.Environment.NORMAL
                && seasons.state(world.getName()).filter(WorldSeasonState::enabled).isPresent();
    }

    /** month of the current date of a world (day and night length) */
    private Optional<MonthDefinition> monthOf(String world) {
        GameCalendar calendar = seasons.calendar();
        return seasons.state(world).map(s -> calendar.month(calendar.normalize(s.date()).month()));
    }

    /** state of the main world, otherwise of any enabled one */
    private Optional<WorldSeasonState> syncSource() {
        List<World> worlds = getServer().getWorlds();
        if (!worlds.isEmpty()) {
            Optional<WorldSeasonState> main = seasons.state(worlds.getFirst().getName()).filter(WorldSeasonState::enabled);
            if (main.isPresent()) return main;
        }
        return seasons.snapshot().values().stream().filter(WorldSeasonState::enabled).findFirst();
    }

    /** every second: the date by the world clock or by real days; who drives the time */
    private void tick() {
        LoadedConfig c = config;
        if (c.calendar().enabled() && c.calendar().realLifeDays()) {
            long today = today(c);
            seasons.worlds().forEach(w -> seasons.syncClock(w, today));
        } else if (c.calendar().enabled()) {
            for (World world : getServer().getWorlds()) {
                seasons.syncClock(world.getName(), SeasonService.clockDay(world.getFullTime()));
            }
        }
        getServer().getWorlds().forEach(this::updateClock);
    }

    private void saveIfDirty() {
        if (seasons.consumeDirty() | clock.consumePauseChanges()) save();
    }

    /** players with colours and particles turned off, from {@code data/players.yml} */
    private PlayerSettingsStore.Settings loadPlayerSettings() {
        playerSettings = new PlayerSettingsStore(dataFolder().resolve(PLAYERS_FILE));
        List<String> warnings = new ArrayList<>();
        PlayerSettingsStore.Settings off = PlayerSettingsStore.Settings.EMPTY;
        try {
            if (playerSettings.exists()) off = playerSettings.load(warnings);
        } catch (ConfigException e) {
            warnings.add("The personal settings of players cannot be loaded (" + playerSettings.file() + "): " + e.getMessage());
        }
        warnings.forEach(getLogger()::warning);
        return off;
    }

    /** {@code real-calendar.yml}; on an error astronomical seasons, the game calendar everywhere, no ranges */
    private RealCalendarSettings loadRealCalendar() {
        List<String> warnings = new ArrayList<>();
        try {
            RealCalendarSettings s = RealCalendarSettings.load(dataFolder(), warnings);
            warnings.forEach(getLogger()::warning);
            LocalDate today = s.calendar().today();
            getLogger().info("Real calendar: " + today + " " + s.calendar().seasonAt(today) + String.format(" %.0f %%",
                    s.calendar().progress(today)) + ", worlds with the real season " + s.worlds().entrySet().stream()
                    .filter(e -> e.getValue() == SeasonSource.REAL).map(Map.Entry::getKey).toList()
                    + ", ranges " + s.periods().stream().map(p -> p.id() + " " + p.range()).toList());
            return s;
        } catch (ConfigException e) {
            warnings.forEach(getLogger()::warning);
            getLogger().severe("The real calendar cannot be loaded, only the game one applies: " + e.getMessage());
            return RealCalendarSettings.NONE;
        }
    }

    /** saves turned off colours and particles ({@code /toggleseasoncolors}, {@code /toggleseasonparticles}) */
    void savePlayerSettings() {
        if (playerSettings == null || particleViewers == null) return;
        Set<UUID> colors = clientColors != null ? clientColors.colorsDisabled() : colorsDisabledAtStart;
        try {
            playerSettings.save(new PlayerSettingsStore.Settings(colors, particleViewers.disabled()));
        } catch (IOException e) {
            getLogger().warning("The personal settings of players cannot be saved to " + playerSettings.file() + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            store.save(new StateStore.Snapshot(seasons.snapshot(), clockKind(config), clock.pausedWorlds()));
        } catch (IOException e) {
            getLogger().warning("The world state cannot be saved to " + store.file() + ": " + e.getMessage());
            seasons.markDirty();
        }
    }

    private OptionalLong timeOfDay(String world) {
        World w = getServer().getWorld(world);
        return w == null ? OptionalLong.empty() : OptionalLong.of(w.getTime());
    }

    private void registerPlaceholders() {
        if (placeholders != null) placeholders.unregister();
        placeholders = null;
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;

        placeholders = new PlaceholderHook();
        List<String> ids = placeholders.register(this, resolver, p -> regions.seasonAt(p.getLocation()));
        if (!ids.isEmpty()) getLogger().info("Placeholders: " + String.join(", ", ids));
    }

    private void bind(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void report(LoadedConfig c) {
        c.sources().forEach((role, path) -> getLogger().info("  " + role + " ← " + path));
        c.warnings().forEach(getLogger()::warning);
    }
}
