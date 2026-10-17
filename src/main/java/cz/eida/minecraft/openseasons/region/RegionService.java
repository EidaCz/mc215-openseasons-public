/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import cz.eida.minecraft.openseasons.calendar.RealPeriod;
import cz.eida.minecraft.openseasons.config.RealCalendarSettings;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.SeasonSource;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Season settings by region (WorldGuard flags): the only place
 * where all features ask about regions. A region value
 * overrides the global setting; without WorldGuard {@link RegionSettings#NONE} applies everywhere.
 *
 * <p>Settings apply to the whole chunk column. Values are kept in a cache
 * by chunk (filled when a chunk loads, dropped when it unloads); every 2 s the fingerprint of the
 * regions is compared and after a change ({@code /rg flag}, {@code /rg redefine} …) the stored chunks are recomputed
 * and the listeners ({@link #addChangeListener}) get the changed chunks.
 *
 * <p>Threads: the queries {@code at/…Allowed/seasonAt} are for the main thread. From another thread
 * (packets) they return only the cached value, otherwise {@link RegionSettings#NONE}; a missing chunk is
 * computed on the main thread and when it has other than default settings, the listeners get it as changed.
 * WorldGuard is never touched off the main thread.
 *
 * <p>The season at a location ({@link #localAt}) also takes the real-world calendar into account ({@code real-calendar.yml},
 * region before world): region period ({@code real-periods}) &gt;
 * {@code permanent-season} &gt; {@code season-source} of the region &gt; world period &gt; season source of the world &gt;
 * game calendar.
 */
public final class RegionService implements Listener {

    /** chunk column */
    public record ChunkPos(int x, int z) {
    }

    /** change of chunk settings (main thread) */
    @FunctionalInterface
    public interface ChangeListener {
        void changed(World world, List<ChunkPos> chunks);
    }

    /** how often the fingerprint of the regions is compared, ticks */
    static final long CHECK_PERIOD = 40L;
    /** progress of the permanent season of a region ({@code permanent-season}) */
    public static final double PERMANENT_PROGRESS = 50.0;

    /** where the season at a location comes from */
    public enum Origin {
        /** game calendar of the world */
        GAME,
        /** real-world season */
        REAL,
        /** period of the real date (e.g. Halloween) */
        PERIOD,
        /** {@code permanent-season} of the region */
        PERMANENT
    }

    /**
     * Season at a location.
     *
     * @param progress progress in percent; {@code NaN} for {@link Origin#GAME} (computed from the game date
     *                 and the time of day of the world)
     * @param periodId period for {@link Origin#PERIOD}, otherwise {@code null}
     */
    public record Local(Season season, double progress, Origin origin, String periodId) {

        /** short description for administrators */
        public String describe() {
            return switch (origin) {
                case GAME -> "game calendar";
                case REAL -> String.format(java.util.Locale.ROOT, "real season (%.0f %%)", progress);
                case PERIOD -> String.format(java.util.Locale.ROOT, "period %s (%.0f %%)", periodId, progress);
                case PERMANENT -> "permanent-season of the region";
            };
        }
    }

    private final Plugin plugin;
    private final Logger log;
    private final SeasonService seasons;
    private final RegionSource source;
    private final RegionCache cache;
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> realDateListeners = new CopyOnWriteArrayList<>();
    private volatile RealCalendarSettings real = RealCalendarSettings.NONE;
    private volatile LocalDate today = RealCalendarSettings.NONE.calendar().today();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean queryFailureReported;

    RegionService(Plugin plugin, SeasonService seasons, RegionSource source) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.seasons = seasons;
        this.source = source;
        this.cache = new RegionCache(this::query, this::fingerprint);
    }

    /**
     * From {@code onLoad}: registers the flags in WorldGuard before it loads the regions. Nothing without WorldGuard.
     */
    public static void registerFlags(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) return;
        try {
            SeasonFlags flags = SeasonFlags.registerWithWorldGuard(plugin.getLogger());
            plugin.getLogger().info("WorldGuard: flags " + String.join(", ", flags.available()));
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("WorldGuard: flags cannot be registered (" + e + "); only global settings apply.");
        }
    }

    /** from {@code onEnable}: with a running WorldGuard and the flags from {@link #registerFlags} by region, otherwise globally */
    public static RegionService create(Plugin plugin, SeasonService seasons) {
        RegionSource source = RegionSource.NONE;
        if (plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                SeasonFlags flags = SeasonFlags.registered();
                if (flags == null) {
                    plugin.getLogger().warning("WorldGuard is running, but the OpenSeasons flags are not registered; only global settings apply.");
                } else {
                    source = new WorldGuardSource(flags);
                }
            } catch (LinkageError | RuntimeException e) {
                plugin.getLogger().warning("WorldGuard is running, but its API cannot be used (" + e + "); only global settings apply.");
            }
        }
        return new RegionService(plugin, seasons, source);
    }

    public void start() {
        log.info("Settings by region: " + source.describe());
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshToday, 20L, 20L));
        if (!active()) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::drainPending, 1L, 1L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkRegions, CHECK_PERIOD, CHECK_PERIOD));
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        HandlerList.unregisterAll(this);
        cache.clear();
    }

    /** settings take regions into account (WorldGuard with the flags is running) */
    public boolean active() {
        return source != RegionSource.NONE;
    }

    /** source and size of the cache, for listing */
    public String describe() {
        return source.describe() + (active() ? ", " + cache.size() + " chunks cached" : "");
    }

    /** real-world calendar ({@code real-calendar.yml}); after loading and reloading */
    public void setRealCalendar(RealCalendarSettings settings) {
        real = settings;
        today = settings.calendar().today();
    }

    public RealCalendarSettings realCalendar() {
        return real;
    }

    /** today's real date (refreshed every second) */
    public LocalDate today() {
        return today;
    }

    /** listener of a change of the real date (a new day: another real season or periods) */
    public void addRealDateListener(Runnable listener) {
        realDateListeners.add(listener);
    }

    /** for tests: a fixed real date */
    void setToday(LocalDate date) {
        today = date;
    }

    void refreshToday() {
        LocalDate now = real.calendar().today();
        if (now.equals(today)) return;
        today = now;
        log.info("Real date " + now + ": " + real.calendar().seasonAt(now) + ", periods "
                + real.active(now).stream().map(RealPeriod::id).toList());
        for (Runnable r : realDateListeners) {
            try {
                r.run();
            } catch (RuntimeException e) {
                log.warning("Real date listener failed: " + e);
            }
        }
    }

    /** listener of changes of chunk settings (e.g. to send chunks to clients again) */
    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    // --- queries ---

    /** region settings in a chunk column; off the main thread only from the cache */
    public RegionSettings at(World world, int chunkX, int chunkZ) {
        if (!active()) return RegionSettings.NONE;
        if (!plugin.getServer().isPrimaryThread()) {
            RegionSettings cached = cache.peek(world.getName(), chunkX, chunkZ);
            return cached != null ? cached : RegionSettings.NONE;
        }
        return cache.get(world.getName(), chunkX, chunkZ);
    }

    public RegionSettings at(Location location) {
        return at(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public RegionSettings at(Block block) {
        return at(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * For packets (any thread): the cached value, or {@code null} when the chunk has not been
     * computed yet (it is computed and with other than default settings it comes to the change listeners).
     * Without WorldGuard always {@link RegionSettings#NONE}.
     */
    public RegionSettings cached(World world, int chunkX, int chunkZ) {
        return active() ? cache.peek(world.getName(), chunkX, chunkZ) : RegionSettings.NONE;
    }

    /**
     * Season at a location (see the order in the class description). Empty in a world without seasons or
     * with disabled ones (regions do not add seasons there).
     */
    public Optional<Season> seasonAt(World world, int chunkX, int chunkZ) {
        return localAt(world, chunkX, chunkZ).map(Local::season);
    }

    /** season at a location including its progress and origin */
    public Optional<Local> localAt(World world, int chunkX, int chunkZ) {
        Optional<WorldSeasonState> state = seasons.state(world.getName()).filter(WorldSeasonState::enabled);
        if (state.isEmpty()) return Optional.empty();
        RegionSettings r = at(world, chunkX, chunkZ);
        return Optional.of(regionOverride(r).orElseGet(() -> forcesGame(r) ? game(state.get())
                : worldLocal(world.getName(), state.get())));
    }

    public Optional<Local> localAt(Location location) {
        return localAt(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /** season of the whole world: world period, real season (worlds: real), otherwise the game calendar */
    public Optional<Local> worldLocal(World world) {
        return seasons.state(world.getName()).filter(WorldSeasonState::enabled).map(s -> worldLocal(world.getName(), s));
    }

    Local worldLocal(String world, WorldSeasonState state) {
        RealCalendarSettings rc = real;
        LocalDate d = today;
        Optional<RealPeriod> period = rc.worldSeasonPeriod(world, d);
        if (period.isPresent()) {
            RealPeriod p = period.get();
            return new Local(p.season().orElseThrow(), p.progressOn(d, rc.calendar()), Origin.PERIOD, p.id());
        }
        if (rc.worldSource(world) == SeasonSource.REAL) return real(rc, d);
        return game(state);
    }

    /**
     * Season override by the region: an active period from {@code real-periods} with a season, {@code permanent-season},
     * {@code season-source REAL}. Empty = the world applies (or the game calendar, see {@link #forcesGame}).
     * A pure function of the chunk settings, callable from any thread.
     */
    public Optional<Local> regionOverride(RegionSettings r) {
        RealCalendarSettings rc = real;
        LocalDate d = today;
        if (!r.realPeriods().isEmpty()) {
            for (RealPeriod p : rc.periods()) {
                if (p.season().isPresent() && r.realPeriods().contains(p.id().toLowerCase(java.util.Locale.ROOT))
                        && p.activeOn(d)) {
                    return Optional.of(new Local(p.season().get(), p.progressOn(d, rc.calendar()), Origin.PERIOD, p.id()));
                }
            }
        }
        if (r.permanentSeason().isPresent()) {
            return Optional.of(new Local(r.permanentSeason().get(), PERMANENT_PROGRESS, Origin.PERMANENT, null));
        }
        if (r.seasonSource().filter(s -> s == SeasonSource.REAL).isPresent()) return Optional.of(real(rc, d));
        return Optional.empty();
    }

    /** a region without another override has {@code season-source GAME}: game calendar even in a world with the real one */
    public boolean forcesGame(RegionSettings r) {
        return regionOverride(r).isEmpty() && r.seasonSource().filter(s -> s == SeasonSource.GAME).isPresent();
    }

    /** today's real season */
    public Local realLocal() {
        return real(real, today);
    }

    private static Local real(RealCalendarSettings rc, LocalDate d) {
        return new Local(rc.calendar().seasonAt(d), rc.calendar().progress(d), Origin.REAL, null);
    }

    private static Local game(WorldSeasonState state) {
        return new Local(state.season(), Double.NaN, Origin.GAME, null);
    }

    /**
     * Is an event from a region period running at the location ({@code real-periods} with an active period whose
     * {@code events} contain it)? Periods of the whole world are handled by the event scheduler.
     */
    public boolean regionEventAt(Location location, String eventId) {
        RegionSettings r = at(location);
        if (r.realPeriods().isEmpty()) return false;
        LocalDate d = today;
        for (RealPeriod p : real.periods()) {
            if (r.realPeriods().contains(p.id().toLowerCase(java.util.Locale.ROOT)) && p.activeOn(d)
                    && p.events().stream().anyMatch(e -> e.equalsIgnoreCase(eventId))) {
                return true;
            }
        }
        return false;
    }

    public Optional<Season> seasonAt(Location location) {
        return seasonAt(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public Optional<Season> seasonAt(Block block) {
        return seasonAt(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /**
     * Season for phenomena (particles, sky): like {@link #seasonAt}, but empty when the region
     * forbids {@code season-effects}.
     */
    public Optional<Season> effectSeasonAt(Location location) {
        return effectsAllowed(location) ? seasonAt(location) : Optional.empty();
    }

    /** {@code season-effects}: landscape colours, particles and sky phenomena */
    public boolean effectsAllowed(Location location) {
        return at(location).effects();
    }

    public boolean effectsAllowed(World world, int chunkX, int chunkZ) {
        return at(world, chunkX, chunkZ).effects();
    }

    /** {@code season-block-changes}: snow, ice, plants and other block changes by season */
    public boolean blockChangesAllowed(World world, int chunkX, int chunkZ) {
        return at(world, chunkX, chunkZ).blockChanges();
    }

    public boolean blockChangesAllowed(Block block) {
        return at(block).blockChanges();
    }

    /** {@code season-mob-spawning}: seasonal animals and monsters */
    public boolean mobSpawningAllowed(World world, int chunkX, int chunkZ) {
        return at(world, chunkX, chunkZ).mobSpawning();
    }

    public boolean mobSpawningAllowed(Location location) {
        return at(location).mobSpawning();
    }


    /** drops the cache and recomputes everything (e.g. after a reload) */
    public void invalidate() {
        for (String world : cache.worlds()) cache.evictWorld(world);
    }

    // --- cache (main thread) ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        // beforehand, so that the chunk packets find the value in the cache
        if (seasons.state(event.getWorld().getName()).isPresent()) {
            cache.get(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        cache.evict(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        cache.evictWorld(event.getWorld().getName());
    }

    void drainPending() {
        for (Map.Entry<String, List<Long>> e : cache.drainPending().entrySet()) notify(e.getKey(), e.getValue());
    }

    void checkRegions() {
        for (String world : cache.worlds()) {
            List<Long> changed = cache.revalidate(world);
            if (!changed.isEmpty()) {
                log.info("Regions in world " + world + " changed, " + changed.size() + " chunks have new settings.");
                notify(world, changed);
            }
        }
    }

    private void notify(String worldName, List<Long> keys) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null || keys.isEmpty()) return;
        List<ChunkPos> chunks = keys.stream().map(k -> new ChunkPos(RegionCache.chunkX(k), RegionCache.chunkZ(k))).toList();
        for (ChangeListener l : listeners) {
            try {
                l.changed(world, chunks);
            } catch (RuntimeException e) {
                log.warning("Region change listener failed: " + e);
            }
        }
    }

    private RegionSettings query(String worldName, int chunkX, int chunkZ) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return RegionSettings.NONE;
        try {
            return source.query(world, chunkX, chunkZ);
        } catch (RuntimeException e) {
            if (!queryFailureReported) {
                queryFailureReported = true;
                log.warning("Region query failed (" + e + "); where it fails, the global settings apply.");
            }
            return RegionSettings.NONE;
        }
    }

    private long fingerprint(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return 0;
        try {
            return source.fingerprint(world);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
