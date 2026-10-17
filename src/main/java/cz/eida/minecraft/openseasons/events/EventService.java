/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.api.event.CalendarEventEndEvent;
import cz.eida.minecraft.openseasons.api.event.CalendarEventStartEvent;
import cz.eida.minecraft.openseasons.api.event.DayChangeEvent;
import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import cz.eida.minecraft.openseasons.placeholder.PlaceholderResolver;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Calendar events: by the game date of the world it starts and ends the events from
 * {@code events/events.yml} and {@code events/custom-events.yml}, runs their lines (messages to the players
 * of the world, console commands) and turns on the mechanics of built-in events. The date is the same as for the
 * seasons (world clock, {@link DayChangeEvent}).
 *
 * <p>An event begins and ends at a day boundary (at game midnight). Running events are saved to
 * {@code data/events.yml}: after a restart or reload a running event is only resumed (mechanics
 * yes, start commands no), an event that ended in the meantime runs its stop commands. On the very
 * first start running events are also resumed without start commands. Events run only in worlds with
 * enabled seasons, with the calendar enabled
 * and outside {@code worlds-without-events}.
 */
public final class EventService implements Listener, PlaceholderResolver.Events {

    /**
     * Events from real-date periods ({@code real-calendar.yml}) active today: of the whole world
     * (run with everything like by the game date) and of all periods (regions may have them via {@code real-periods}).
     */
    public interface RealEvents {
        RealEvents NONE = new RealEvents() {
            @Override
            public Set<String> world(String world) {
                return Set.of();
            }

            @Override
            public Set<String> regions() {
                return Set.of();
            }
        };

        /** event ids of the periods of the whole world (lower case) */
        Set<String> world(String world);

        /** event ids of all active periods (lower case) */
        Set<String> regions();
    }

    /** phenomena for {@code /openseasons debug} */
    public static final List<String> DEBUG_PHENOMENA = List.of("event", "present", "egg", "fireworks",
            "christmas-tree", "christmas-sparkles", "killer-bunny", "halloween-mob", "bats");

    static final String STORE_FILE = "data/events.yml";
    static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final Plugin plugin;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    private final ParticleViewers viewers;
    private final Path dataFolder;
    private final EventPlaces places;
    private final RealEvents realEvents;

    private volatile EventSettings settings = EventSettings.NONE;
    /** settings before the last reload: stop commands of an event that disappeared from the configuration */
    private EventSettings previous = EventSettings.NONE;
    private final EventState state = new EventState();
    private EventStore store;
    /** the very first start with events (the state file did not exist yet) */
    private boolean firstRun;
    /** worlds that went through reconciliation since start (resuming running events) */
    private final Set<String> resumed = new HashSet<>();
    private final Map<SeasonEvent.Type, BuiltinEvent> builtins = new EnumMap<>(SeasonEvent.Type.class);
    private Pickups pickups;
    private ChristmasEvent christmas;
    private NewYearEvent newYear;
    private HalloweenEvent halloween;
    private EasterEvent easter;
    private BukkitTask task;
    private long ticks;
    private final Map<String, CachedNext> nextCache = new ConcurrentHashMap<>();

    private record CachedNext(CalendarDate date, EventSettings settings, Optional<EventPlanner.Next> next) {
    }

    /** @param places where phenomena and monster changes may act, e.g. {@link EventPlaces#of} over regions */
    public EventService(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons, ParticleViewers viewers,
                        Path dataFolder, EventPlaces places) {
        this(plugin, config, seasons, viewers, dataFolder, places, RealEvents.NONE);
    }

    /** @param realEvents events from real-date periods */
    public EventService(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons, ParticleViewers viewers,
                        Path dataFolder, EventPlaces places, RealEvents realEvents) {
        this.realEvents = realEvents == null ? RealEvents.NONE : realEvents;
        this.plugin = plugin;
        this.config = config;
        this.seasons = seasons;
        this.viewers = viewers;
        this.dataFolder = dataFolder;
        this.places = places == null ? EventPlaces.EVERYWHERE : places;
    }

    // ---------------------------------------------------------------- life cycle

    public void start() {
        settings = loadSettings().orElse(EventSettings.NONE);
        store = new EventStore(dataFolder.resolve(STORE_FILE));
        try {
            Optional<Map<String, List<String>>> saved = store.load();
            firstRun = saved.isEmpty();
            saved.ifPresent(state::load);
        } catch (ConfigException e) {
            plugin.getLogger().warning("Running events cannot be loaded (" + store.file() + "), running ones are resumed"
                    + " without start commands: " + e.getMessage());
            firstRun = true;
        }
        pickups = new Pickups(viewers, () -> settings);
        christmas = new ChristmasEvent(plugin, () -> settings, viewers, places, pickups);
        newYear = new NewYearEvent(plugin, () -> settings, viewers, places);
        halloween = new HalloweenEvent(plugin, () -> settings, viewers, places);
        easter = new EasterEvent(plugin, () -> settings, viewers, places, pickups);
        Stream.of(christmas, newYear, halloween, easter).forEach(b -> builtins.put(b.type(), b));
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(this, plugin);
        pm.registerEvents(pickups, plugin);
        builtins.values().forEach(b -> pm.registerEvents(b, plugin));
        plugin.getServer().getWorlds().forEach(this::reconcile);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** plugin shutdown: entities vanish, running events stay saved and are resumed after start */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        builtins.values().forEach(BuiltinEvent::shutdown);
        if (pickups != null) pickups.removeAll();
        save();
    }

    /** after {@code /openseasons reload}: new definitions, reconciliation of all worlds */
    public void reload() {
        Optional<EventSettings> loaded = loadSettings();
        if (loaded.isEmpty()) return;
        previous = settings;
        settings = loaded.get();
        nextCache.clear();
        plugin.getServer().getWorlds().forEach(this::reconcile);
    }

    public EventSettings settings() {
        return settings;
    }

    private Optional<EventSettings> loadSettings() {
        LoadedConfig c = config.get();
        try {
            EventSettings s = EventFiles.load(dataFolder, c.calendar(), EventService::knownItem);
            s.sources().forEach((role, path) -> plugin.getLogger().info("  " + role + " ← " + path));
            s.warnings().forEach(plugin.getLogger()::warning);
            List<String> unknown = HalloweenEvent.unknownEffects(s.halloween().witchEffects());
            if (!unknown.isEmpty()) {
                plugin.getLogger().warning("halloween.witch-extra-potions.effects: unknown effects " + unknown
                        + ", skipped");
            }
            return Optional.of(s);
        } catch (ConfigException e) {
            plugin.getLogger().severe("Events cannot be loaded, " + (settings == EventSettings.NONE ? "they will not run"
                    : "the previous ones apply") + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** the material exists; whether it is an item is checked only when a present is opened (needs the server registry) */
    static boolean knownItem(String name) {
        Material m = Material.matchMaterial(name);
        return m != null && !m.isLegacy();
    }

    private void tick() {
        ticks++;
        if (ticks % 20 == 0) plugin.getServer().getWorlds().forEach(this::reconcile);
        for (BuiltinEvent b : builtins.values()) {
            try {
                b.tick(ticks);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Event " + b.type().id() + ": " + e);
            }
        }
        pickups.tick(ticks);
    }

    // ---------------------------------------------------------------- scheduling

    /** events may run in the world: calendar enabled, seasons enabled, the world is not in worlds-without-events */
    public boolean eligible(String world) {
        LoadedConfig c = config.get();
        if (!c.calendar().enabled()) return false;
        if (seasons.state(world).filter(WorldSeasonState::enabled).isEmpty()) return false;
        for (String w : c.calendar().worldsWithoutEvents()) {
            if (w.equalsIgnoreCase(world)) return false;
        }
        return true;
    }

    private Optional<CalendarDate> date(String world) {
        return seasons.state(world).map(WorldSeasonState::date);
    }

    private void reconcile(World world) {
        String name = world.getName();
        boolean fresh = resumed.add(name);
        EventSettings s = settings;
        Set<String> target;
        boolean eligible = eligible(name);
        if (eligible) {
            GameCalendar cal = seasons.calendar();
            Set<String> due = EventPlanner.due(s, cal, name, date(name).orElseThrow());
            due.addAll(known(s, realEvents.world(name), name));
            target = state.target(name, due);
            target.removeIf(id -> s.event(id).isEmpty());
        } else {
            target = Set.of();
        }
        if (fresh) {
            for (String id : state.running(name)) {
                if (target.contains(id)) builtin(id).ifPresent(b -> b.begin(world));
            }
        }
        EventState.Change change = state.diff(name, target);
        for (String id : change.stopped()) stopEvent(world, id);
        for (String id : change.started()) {
            if (fresh && firstRun) {
                state.markStarted(name, id);
                builtin(id).ifPresent(b -> b.begin(world));
                plugin.getLogger().info("World " + name + ": event " + id + " is running (first start, start commands are"
                        + " not run)");
            } else {
                startEvent(world, id);
            }
        }
        if (!change.isEmpty()) save();
        reconcileRegions(world, eligible ? known(s, realEvents.regions(), name) : Set.of());
    }

    /**
     * Built-in events from region periods: in a world where they do not run entirely, they run only as mechanics in regions
     * with {@code real-periods} (without start and stop lines, not saved).
     */
    private void reconcileRegions(World world, Set<String> regionIds) {
        for (BuiltinEvent b : builtins.values()) {
            String id = b.type().id();
            if (state.running(world.getName(), id)) {
                b.begin(world, BuiltinEvent.Scope.WORLD);
            } else if (regionIds.contains(id)) {
                if (b.scope(world).isEmpty()) plugin.getLogger().info("World " + world.getName() + ": event " + id
                        + " runs in regions with a real-date period");
                b.begin(world, BuiltinEvent.Scope.REGIONS);
            } else if (b.scope(world).filter(sc -> sc == BuiltinEvent.Scope.REGIONS).isPresent()) {
                b.end(world);
                plugin.getLogger().info("World " + world.getName() + ": event " + id + " in regions ended");
            }
        }
    }

    /** ids from real-date periods → event ids from events.yml (case-insensitive), allowed in the world */
    private static Set<String> known(EventSettings s, Set<String> ids, String world) {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String id : ids) {
            s.events().stream().filter(e -> e.id().equalsIgnoreCase(id) && e.allowedIn(world)).findFirst()
                    .ifPresent(e -> out.add(e.id()));
        }
        return out;
    }

    private void startEvent(World world, String id) {
        Optional<SeasonEvent> found = settings.event(id);
        if (found.isEmpty()) return;
        SeasonEvent e = found.get();
        CalendarEventStartEvent event = new CalendarEventStartEvent(world, id, e.name());
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            state.suppress(world.getName(), id);
            plugin.getLogger().info("World " + world.getName() + ": another plugin cancelled the start of event " + id);
            return;
        }
        state.markStarted(world.getName(), id);
        builtin(id).ifPresent(b -> b.begin(world));
        run(world, e.startCommands());
        plugin.getLogger().info("World " + world.getName() + ": event " + id + " begins");
    }

    private void stopEvent(World world, String id) {
        Optional<SeasonEvent> e = settings.event(id).or(() -> previous.event(id));
        state.markStopped(world.getName(), id);
        builtin(id).ifPresent(b -> b.end(world));
        plugin.getServer().getPluginManager().callEvent(new CalendarEventEndEvent(world, id, e.map(SeasonEvent::name).orElse(id)));
        e.ifPresent(ev -> run(world, ev.stopCommands()));
        plugin.getLogger().info("World " + world.getName() + ": event " + id + " ends");
    }

    private Optional<BuiltinEvent> builtin(String id) {
        SeasonEvent.Type type = SeasonEvent.Type.ofBuiltin(id);
        return type == SeasonEvent.Type.CUSTOM ? Optional.empty() : Optional.ofNullable(builtins.get(type));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDayChange(DayChangeEvent event) {
        World world = event.getWorld();
        String name = world.getName();
        state.newDay(name);
        if (eligible(name)) {
            for (SeasonEvent e : EventPlanner.daily(settings, name)) run(world, e.startCommands());
        }
        reconcile(world);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        builtins.values().forEach(b -> {
            if (b.running(world)) b.end(world);
        });
        resumed.remove(world.getName());
    }

    /** event entities are temporary; should an old one load anyway, it vanishes outside a running event */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> stale = new ArrayList<>();
        for (Entity e : event.getEntities()) {
            EventEntities.typeOf(e).ifPresent(type -> {
                BuiltinEvent b = builtins.get(type);
                if (b == null || !b.running(e.getWorld())) stale.add(e);
            });
        }
        if (!stale.isEmpty()) plugin.getServer().getScheduler().runTask(plugin, () -> stale.forEach(Entity::remove));
    }

    private void save() {
        if (store == null) return;
        try {
            store.save(state.snapshot());
        } catch (IOException e) {
            plugin.getLogger().warning("Running events cannot be saved to " + store.file() + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- event lines

    /** messages to the players of the world and console commands with placeholders */
    private void run(World world, List<String> lines) {
        List<EventLines.Line> parsed = EventLines.parse(lines);
        if (parsed.isEmpty()) return;
        Map<String, String> values = values(world.getName());
        boolean papi = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        for (EventLines.Line line : parsed) {
            String text = EventLines.fill(line.text(), values);
            if (line.kind() == EventLines.Kind.COMMAND) {
                String command = papi ? PapiHook.apply(null, text) : text;
                try {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Event command '/" + command + "' failed: " + e);
                }
            } else {
                for (Player p : world.getPlayers()) {
                    p.sendMessage(LEGACY.deserialize(papi ? PapiHook.apply(p, text) : text));
                }
            }
        }
    }

    /** %day%, %month%, %month_asname%, %year%, %weekday%, %season%, %world% */
    Map<String, String> values(String world) {
        Map<String, String> v = new LinkedHashMap<>();
        Optional<WorldSeasonState> s = seasons.state(world);
        GameCalendar cal = seasons.calendar();
        s.ifPresent(st -> {
            CalendarDate d = st.date();
            v.put("day", Integer.toString(d.day()));
            v.put("month", Integer.toString(d.month()));
            v.put("month_asname", cal.monthName(d));
            v.put("year", Integer.toString(d.year()));
            v.put("weekday", cal.weekdayName(d));
            v.put("season", st.enabled() ? config.get().lang().seasonDisplayName(st.season()) : "DISABLED");
        });
        v.put("world", world);
        return v;
    }

    // ---------------------------------------------------------------- queries (placeholders, /season, API)

    /** ids of the events running in the world (also hidden ones); callable from any thread */
    public List<String> activeIds(String world) {
        return List.copyOf(state.running(world));
    }

    /** display names of the running events with {@code display-event: true} */
    public List<String> activeNames(String world) {
        EventSettings s = settings;
        List<String> out = new ArrayList<>();
        for (String id : state.running(world)) s.event(id).filter(SeasonEvent::display).ifPresent(e -> out.add(e.name()));
        return out;
    }

    public Optional<String> name(String id) {
        return settings.event(id).map(SeasonEvent::name);
    }

    @Override
    public List<String> activeEventNames(String world) {
        return activeNames(world);
    }

    @Override
    public Optional<PlaceholderResolver.NextEvent> nextEvent(String world) {
        return next(world).map(n -> new PlaceholderResolver.NextEvent(n.events().stream().map(SeasonEvent::name).toList(),
                n.daysUntil()));
    }

    /** the nearest displayed event that begins in the world; empty when none or the world has no events */
    public Optional<EventPlanner.Next> next(String world) {
        if (!eligible(world)) return Optional.empty();
        Optional<CalendarDate> date = date(world);
        if (date.isEmpty()) return Optional.empty();
        EventSettings s = settings;
        CachedNext cached = nextCache.get(world);
        if (cached != null && cached.date().equals(date.get()) && cached.settings() == s) return cached.next();
        Optional<EventPlanner.Next> next = EventPlanner.next(s, seasons.calendar(), world, date.get());
        nextCache.put(world, new CachedNext(date.get(), s, next));
        return next;
    }

    // ---------------------------------------------------------------- /openseasons debug

    /**
     * {@code debug <phenomenon> …}: {@code args[0]} is a phenomenon from {@link #DEBUG_PHENOMENA}.
     * {@code event [<id> [start|stop] [world]]}, the others {@code <phenomenon> [player | <world> <x> <z>]}.
     */
    public void debug(CommandSender sender, String label, String[] args) {
        String phenomenon = args[0].toLowerCase(Locale.ROOT);
        if (phenomenon.equals("event")) {
            debugEvent(sender, label, args);
            return;
        }
        // where: a player (by default the one who entered the command), or a world and coordinates x z (surface, the chunk must be loaded)
        Player target = null;
        Location at;
        String where;
        if (args.length >= 4) {
            World w = plugin.getServer().getWorld(args[1]);
            if (w == null) {
                error(sender, "World " + args[1] + " does not exist.");
                return;
            }
            double x;
            double z;
            try {
                x = Double.parseDouble(args[2]);
                z = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                error(sender, "Enter the coordinates as numbers: debug " + phenomenon + " <world> <x> <z>");
                return;
            }
            if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
                error(sender, "The chunk at " + (int) x + " " + (int) z + " is not loaded (e.g. /forceload add " + (int) x
                        + " " + (int) z + ").");
                return;
            }
            at = new Location(w, x, w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1, z);
            where = w.getName() + " " + (int) x + " " + (int) z;
        } else {
            target = args.length > 1 ? plugin.getServer().getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target == null) {
                error(sender, args.length > 1 ? "Player " + args[1] + " is not online."
                        : "Enter a player or place: debug " + phenomenon + " <player> | <world> <x> <z>");
                return;
            }
            at = target.getLocation();
            where = target.getName();
        }
        Player follow = target;
        String done = switch (phenomenon) {
            case "present" -> christmas.debugPresent(at).map(l -> "present" + coords(l)).orElse(null);
            case "egg" -> easter.debugEgg(at).map(l -> "egg" + coords(l)).orElse(null);
            case "fireworks" -> newYear.debugBox(at).map(l -> "firework box for 30 s" + coords(l)).orElse(null);
            case "killer-bunny" -> easter.debugBunny(at).map(l -> "killer bunny" + coords(l)).orElse(null);
            case "halloween-mob" -> halloween.debugMob(at).map(l -> "Halloween zombie with a twin" + coords(l)).orElse(null);
            case "bats" -> {
                int n = halloween.spawnBats(at, 4);
                yield n > 0 ? "bats (" + n + ")" : null;
            }
            case "christmas-sparkles" -> {
                Location fixed = at.clone();
                christmas.debugSparkles(() -> follow == null ? fixed : follow.isOnline() ? follow.getLocation() : null, 300);
                yield "Christmas sparkles for 15 s";
            }
            case "christmas-tree" -> christmas.debugTree(at).map(l -> "decorated spruce" + coords(l)).orElse(null);
            default -> null;
        };
        if (done == null) {
            error(sender, phenomenon.equals("christmas-tree") ? "There is no spruce within 24 blocks of " + where + "."
                    : "There is no place on the ground at " + where + ", try elsewhere.");
            return;
        }
        ok(sender, "U " + where + ": " + done + ".");
        if (target != null && !viewers.enabled(target)
                && Set.of("christmas-tree", "christmas-sparkles", "fireworks").contains(phenomenon)) {
            sender.sendMessage(Component.text(target.getName() + " has particles turned off (/toggleseasonparticles) and will not"
                    + " see the phenomenon.", NamedTextColor.YELLOW));
        }
    }

    private static String coords(Location l) {
        return " (" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")";
    }

    private void debugEvent(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            World w = defaultWorld(sender, null);
            if (w == null) return;
            listEvents(sender, w);
            sender.sendMessage(Component.text("/" + label + " debug event <id> [start|stop] [world]", NamedTextColor.GRAY));
            return;
        }
        String id = args[1];
        Optional<SeasonEvent> e = settings.event(id);
        if (e.isEmpty()) {
            error(sender, "There is no event " + id + " (" + String.join(", ", ids()) + ").");
            return;
        }
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        boolean hasAction = action.equals("start") || action.equals("stop");
        World world = defaultWorld(sender, hasAction ? (args.length > 3 ? args[3] : null) : (args.length > 2 ? args[2] : null));
        if (world == null) return;
        String name = world.getName();
        if (!hasAction) {
            status(sender, world, e.get());
            return;
        }
        if (!eligible(name)) {
            error(sender, "Events do not run in world " + name + " (seasons or the calendar disabled, worlds-without-events).");
            return;
        }
        if (e.get().daily()) {
            if (action.equals("start")) {
                run(world, e.get().startCommands());
                ok(sender, "Daily event " + id + ": lines run in world " + name + ".");
            } else {
                error(sender, "A daily event has no end.");
            }
            return;
        }
        state.override(name, id, action.equals("start"));
        reconcile(world);
        boolean running = state.running(name, id);
        if (action.equals("start")) {
            if (running) ok(sender, "World " + name + ": event " + plain(e.get().name()) + " runs until the next day change"
                    + " (then by the calendar).");
            else error(sender, "World " + name + ": another plugin cancelled the start of event " + id + ".");
        } else {
            ok(sender, "World " + name + ": event " + plain(e.get().name()) + " stopped until the next day change.");
        }
    }

    private void listEvents(CommandSender sender, World world) {
        String name = world.getName();
        sender.sendMessage(Component.text("Events in world " + name + (eligible(name) ? "" : " (not running: the world has none)")
                + ":", NamedTextColor.GOLD));
        for (SeasonEvent e : settings.events()) {
            sender.sendMessage(Component.text("  " + e.id() + " – " + plain(e.name()) + ": " + describe(world, e),
                    NamedTextColor.GRAY));
        }
    }

    private void status(CommandSender sender, World world, SeasonEvent e) {
        sender.sendMessage(Component.text(e.id() + " – " + plain(e.name()) + " in world " + world.getName() + ": "
                + describe(world, e), NamedTextColor.GREEN));
        BuiltinEvent b = builtin(e.id()).orElse(null);
        if (b == null) return;
        String detail = switch (e.type()) {
            case CHRISTMAS -> "presents " + pickups.live(world, Pickups.Kind.PRESENT).size() + ", decorated spruces "
                    + christmas.decoratedTrees(world);
            case EASTER -> "eggs " + pickups.live(world, Pickups.Kind.EGG).size();
            case NEWYEAR -> "firework boxes " + newYear.boxCount(world);
            default -> null;
        };
        if (detail != null) sender.sendMessage(Component.text("  " + detail, NamedTextColor.GRAY));
    }

    private String describe(World world, SeasonEvent e) {
        String name = world.getName();
        if (!e.enabled()) return "disabled";
        if (!e.allowedIn(name)) return "disabled in this world (disabled-worlds)";
        if (e.daily()) return "daily (on every day change)";
        if (state.running(name, e.id())) return "running";
        World w = plugin.getServer().getWorld(name);
        if (w != null && builtin(e.id()).flatMap(b -> b.scope(w)).filter(sc -> sc == BuiltinEvent.Scope.REGIONS).isPresent()) {
            return "running only in regions with a real-date period (real-periods)";
        }
        Optional<CalendarDate> d = date(name);
        if (d.isEmpty()) return "not running";
        OptionalInt days = e.schedule().daysUntilStart(d.get(), seasons.calendar(), seasons.calendar().daysInYear());
        return days.isPresent() ? "not running, begins in " + days.getAsInt() + " days" : "not running";
    }

    private List<String> ids() {
        return settings.events().stream().map(SeasonEvent::id).toList();
    }

    /** completion for {@code /openseasons debug …}; {@code args[0]} = {@code debug} */
    public List<String> complete(String[] args) {
        if (args.length < 2) return List.of();
        if (args[1].equalsIgnoreCase("event")) {
            if (args.length == 3) return ids();
            if (args.length == 4) return List.of("start", "stop");
            if (args.length == 5) return plugin.getServer().getWorlds().stream().map(World::getName).toList();
            return List.of();
        }
        if (args.length == 3) {
            return Stream.concat(plugin.getServer().getOnlinePlayers().stream().map(Player::getName),
                    plugin.getServer().getWorlds().stream().map(World::getName)).toList();
        }
        return List.of();
    }

    private World defaultWorld(CommandSender sender, String arg) {
        if (arg != null) {
            World w = plugin.getServer().getWorld(arg);
            error(sender, "World " + arg + " does not exist.");
            return w;
        }
        if (sender instanceof Player p) return p.getWorld();
        return plugin.getServer().getWorlds().getFirst();
    }

    private static String plain(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(legacy));
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
