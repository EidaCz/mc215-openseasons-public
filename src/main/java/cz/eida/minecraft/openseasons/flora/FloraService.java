/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.flora;

import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.SubSeason;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import cz.eida.minecraft.openseasons.world.BlockAccess;
import cz.eida.minecraft.openseasons.world.BlockKeys;
import cz.eida.minecraft.openseasons.world.ChunkTicker;
import cz.eida.minecraft.openseasons.world.JournalRestorer;
import cz.eida.minecraft.openseasons.world.PlacementJournal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Segmentable;
import org.bukkit.block.data.type.FlowerBed;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * Seasonal plants in the world: spring flowers, regrowing grass, mushrooms,
 * sweet berry bushes, leaf litter and extra plants. Plants appear slowly
 * in loaded chunks around players ({@link ChunkTicker}) and each one goes into the journal
 * ({@link PlacementJournal}); when their season ends, OpenSeasons removes them, but only while they
 * are still there. Player plants are never changed. WorldGuard regions: with the flag
 * {@code season-block-changes: deny} nothing changes in the chunk, {@code permanent-season} determines the season
 * of the chunk; both through {@link RegionService} in one place ({@link #regionAllows}, {@link #chunkSeason}).
 */
public final class FloraService implements Listener, ChunkTicker.Task {

    static final String JOURNAL_FOLDER = "data/journal";
    /** {@code /openseasons debug flora}: chunks around the player and places in a chunk */
    static final int DEBUG_CHUNK_RADIUS = 2;
    static final int DEBUG_SAMPLES = 16;
    /** the ticker leaves plants from debug outside their season alone for this long */
    static final long DEBUG_GRACE_MS = 10 * 60_000L;
    static final int RESTORE_PER_TICK = 1000;
    static final long FLUSH_PERIOD = 1200L;
    /** chunks from the cleanup queue per visit (after a season change 10 chunks per second with delay 4) */
    static final int CLEANUP_PER_RUN = 4;
    private static final int SURROUNDING_RADIUS = 4;

    private final Plugin plugin;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    /** region flags ({@code permanent-season}, {@code season-block-changes}); {@code null} = without regions */
    private final RegionService regions;
    private final Logger log;
    private final PlacementJournal journal;
    private final ChunkTicker ticker;
    private final FloraCommand command;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private volatile FloraSettings settings = FloraSettings.DEFAULTS;
    /** world:position → until when to leave a debug plant alone */
    private final Map<String, Long> debugGrace = new HashMap<>();
    /** seasons of the worlds at the last cleanup; a change starts a cleanup of loaded chunks */
    private final Map<String, Optional<Season>> sweptSeason = new HashMap<>();
    private final Set<String> sweepNeeded = new HashSet<>();
    private final Deque<RestoreJob> restoreJobs = new ArrayDeque<>();
    private ExecutorService writer;
    private BukkitTask flushTask;
    private BukkitTask sweepTask;
    private BukkitTask restoreTask;
    private int sweepPeriod;
    private long placed;
    private long removed;

    /** restoring part of a world ({@code /openseasons restore}) in batches */
    private static final class RestoreJob {
        final String world;
        final Deque<PlacementJournal.Entry> entries;
        final CommandSender sender;
        JournalRestorer.Result result = JournalRestorer.Result.NONE;

        RestoreJob(String world, List<PlacementJournal.Entry> entries, CommandSender sender) {
            this.world = world;
            this.entries = new ArrayDeque<>(entries);
            this.sender = sender;
        }
    }

    public FloraService(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons, RegionService regions) {
        this.plugin = plugin;
        this.config = config;
        this.seasons = seasons;
        this.regions = regions;
        this.log = plugin.getLogger();
        this.journal = new PlacementJournal(dataFolder().resolve(JOURNAL_FOLDER));
        this.ticker = new ChunkTicker(plugin, this::tickerSettings, List.of(this));
        this.command = new FloraCommand(plugin.getServer(), () -> this);
    }

    public void start() {
        reloadSettings();
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OpenSeasons-journal");
            t.setDaemon(true);
            return t;
        });
        List<String> warnings = new ArrayList<>();
        plugin.getServer().getWorlds().forEach(w -> journal.load(w.getName(), warnings));
        warnings.forEach(log::warning);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // region change (permanent season, season-block-changes): clean up chunks with a journal by the new season
        if (regions != null) {
            regions.addChangeListener((world, chunks) -> chunks.stream()
                    .filter(c -> journal.hasChunk(world.getName(), c.x(), c.z()))
                    .forEach(c -> ticker.enqueueCleanup(world, c.x(), c.z())));
        }
        ticker.start();
        flushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushAsync, FLUSH_PERIOD, FLUSH_PERIOD);
        restoreTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runRestoreJobs, 1L, 1L);
        scheduleSweep();
        int total = plugin.getServer().getWorlds().stream().mapToInt(w -> journal.size(w.getName())).sum();
        log.info("Seasonal plants: " + (config.get().main().modifyBlocks() ? "enabled" : "disabled (modify-blocks: false)")
                + ", " + total + " blocks in the journal.");
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        ticker.stop();
        for (BukkitTask t : new BukkitTask[]{flushTask, sweepTask, restoreTask}) {
            if (t != null) t.cancel();
        }
        restoreJobs.clear();
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flushSync();
    }

    /** after {@code /openseasons reload}: {@code flora.yml} again and the cleanup period */
    public void reload() {
        reloadSettings();
        if (sweepPeriod != sweepPeriod()) scheduleSweep();
        sweepNeeded.addAll(seasons.worlds());
    }

    public PlacementJournal journal() {
        return journal;
    }

    public FloraSettings settings() {
        return settings;
    }

    /** {@code /openseasons restore} and {@code /openseasons debug flora} (holds pending confirmations) */
    public FloraCommand command() {
        return command;
    }

    // --- ChunkTicker.Task ---

    @Override
    public boolean active(World world) {
        return journal.size(world.getName()) > 0 || placementAllowed(world);
    }

    @Override
    public void visit(World world, int chunkX, int chunkZ) {
        cleanup(world, chunkX, chunkZ);
        Optional<Season> chunkSeason = chunkSeason(world, chunkX, chunkZ);
        Optional<WorldSeasonState> state = seasons.state(world.getName());
        if (chunkSeason.isEmpty() || state.isEmpty()) return;
        Season season = chunkSeason.get();
        // the permanent season of a region has no progress, it counts as its middle
        SubSeason phase = season != state.get().season() ? SubSeason.MIDDLE
                : SubSeason.fromProgress(seasons.calendar().seasonProgress(state.get().date(), season, 0.0));
        MainConfig.Flora flora = config.get().main().flora();
        FloraSettings s = settings;
        List<PlantKind> kinds = new ArrayList<>();
        for (PlantKind k : PlantKind.values()) {
            if (k.spawnsIn(season) && FloraRules.enabled(k, flora, s)) kinds.add(k);
        }
        if (kinds.isEmpty()) return;
        for (int i = 0; i < s.samplesPerChunk(); i++) {
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            if (tryColumn(world, x, z, shuffled(kinds), phase, false)) placed++;
        }
    }

    @Override
    public void cleanup(World world, int chunkX, int chunkZ) {
        List<PlacementJournal.Entry> entries = journal.chunk(world.getName(), chunkX, chunkZ);
        if (entries.isEmpty()) return;
        Optional<Season> season = chunkSeason(world, chunkX, chunkZ);
        long now = System.currentTimeMillis();
        // a pending restore applies right away, the grace period of debug plants only for the end of the season
        JournalRestorer.Result r = JournalRestorer.restore(journal, world.getName(), entries, BlockAccess.of(world),
                e -> FloraRules.due(e, season)
                        && (PlacementJournal.RESTORE.equals(e.kind()) || !inGrace(world.getName(), e, now)),
                settings.removalsPerVisit());
        removed += r.restored();
    }

    // --- placing ---

    /** tries to place one plant of the kinds in order on one column; true = placed */
    private boolean tryColumn(World world, int x, int z, List<PlantKind> kinds, SubSeason phase, boolean debug) {
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (groundY <= world.getMinHeight() || groundY + 1 >= world.getMaxHeight()) return false;
        Block ground = world.getBlockAt(x, groundY, z);
        Block above = ground.getRelative(BlockFace.UP);
        String w = world.getName();
        MainConfig.Flora flora = config.get().main().flora();
        FloraSettings s = settings;
        FloraRules.Spot spot = null;
        for (PlantKind kind : kinds) {
            if (!debug && !roll(FloraRules.chance(kind, flora, s, phase))) continue;
            int cx = x >> 4;
            int cz = z >> 4;
            if (kind != PlantKind.BERRY_BUSHES
                    && journal.count(w, cx, cz, kind.id()) >= FloraRules.maxPerChunk(kind, flora, s)) continue;
            if (kind == PlantKind.LEAF_LITTER && above.getType() == Material.LEAF_LITTER) {
                if (thickenLitter(w, above)) return true;
                continue;
            }
            if (spot == null) spot = spot(world, x, groundY, z, ground, above, flora);
            Optional<FloraRules.Plant> plant = FloraRules.choose(kind, spot, surroundings(world, x, groundY + 1, z),
                    random, leafMaterials(flora), flora.destroyPlantsForLeafLitter());
            if (plant.isEmpty()) continue;
            if (!regionAllows(world, cx, cz)) return false;
            BlockData data = blockData(plant.get());
            if (!data.isSupported(above)) continue;
            if (kind == PlantKind.BERRY_BUSHES
                    && berryBushes(world, cx, cz) >= FloraRules.maxPerChunk(kind, flora, s)) continue;
            String original = above.getBlockData().getAsString();
            above.setBlockData(data, false);
            PlacementJournal.Entry e = journal.record(w, x, groundY + 1, z, kind.id(), data.getAsString(), original);
            if (debug && seasons.state(w).map(st -> !kind.aliveIn(st.season())).orElse(true)) {
                debugGrace.put(graceKey(w, e), System.currentTimeMillis() + DEBUG_GRACE_MS);
            }
            return true;
        }
        return false;
    }

    private FloraRules.Spot spot(World world, int x, int groundY, int z, Block ground, Block above,
                                 MainConfig.Flora flora) {
        int canopyY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        Material canopy = canopyY > groundY ? world.getBlockAt(x, canopyY, z).getType() : null;
        boolean playerPlant = flora.keepPlayerPlacedPlants()
                && journal.get(world.getName(), x, groundY + 1, z).map(PlacementJournal.Entry::byPlayer).orElse(false);
        return new FloraRules.Spot(ground.getType(), ground.getRelative(BlockFace.DOWN).getType(), above.getType(),
                above.getLightFromSky(), above.getLightFromBlocks(), above.getTemperature(), above.getHumidity(),
                canopy, canopyY - groundY, playerPlant);
    }

    /** surroundings within 4 blocks horizontally, from the block below the place to 6 above it; only in loaded chunks */
    private static FloraRules.Surroundings surroundings(World world, int x, int y, int z) {
        return materials -> {
            for (int dx = -SURROUNDING_RADIUS; dx <= SURROUNDING_RADIUS; dx++) {
                for (int dz = -SURROUNDING_RADIUS; dz <= SURROUNDING_RADIUS; dz++) {
                    if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
                    for (int dy = -1; dy <= 6; dy++) {
                        if (materials.contains(world.getBlockAt(x + dx, y + dy, z + dz).getType())) return true;
                    }
                }
            }
            return false;
        };
    }

    /** our leaf litter at the place thickens by one piece (at most 4) */
    private boolean thickenLitter(String world, Block block) {
        Optional<PlacementJournal.Entry> e = journal.get(world, block.getX(), block.getY(), block.getZ());
        if (e.isEmpty() || !PlantKind.LEAF_LITTER.id().equals(e.get().kind())) return false;
        if (!(block.getBlockData() instanceof Segmentable litter)
                || litter.getSegmentAmount() >= litter.getMaximumSegmentAmount()) return false;
        litter.setSegmentAmount(litter.getSegmentAmount() + 1);
        block.setBlockData(litter, false);
        journal.record(world, block.getX(), block.getY(), block.getZ(), PlantKind.LEAF_LITTER.id(),
                litter.getAsString(), e.get().original());
        return true;
    }

    /** sweet berry bushes on the surface of the chunk, natural and from OpenSeasons */
    private static int berryBushes(World world, int chunkX, int chunkZ) {
        int n = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = (chunkX << 4) + dx;
                int z = (chunkZ << 4) + dz;
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                if (world.getBlockAt(x, y, z).getType() == Material.SWEET_BERRY_BUSH) n++;
            }
        }
        return n;
    }

    private BlockData blockData(FloraRules.Plant plant) {
        BlockData data = plant.material().createBlockData();
        if (plant.amount() > 0) {
            if (data instanceof Segmentable seg) {
                seg.setSegmentAmount(Math.clamp(plant.amount(), seg.getMinimumSegmentAmount(), seg.getMaximumSegmentAmount()));
            } else if (data instanceof FlowerBed bed) {
                bed.setFlowerAmount(Math.clamp(plant.amount(), bed.getMinimumFlowerAmount(), bed.getMaximumFlowerAmount()));
            }
        }
        if (plant.age() >= 0 && data instanceof Ageable age) age.setAge(Math.min(plant.age(), age.getMaximumAge()));
        if (data instanceof Directional dir) {
            List<BlockFace> faces = dir.getFaces().stream().filter(BlockFace::isCartesian)
                    .filter(f -> f.getModY() == 0).toList();
            if (!faces.isEmpty()) dir.setFacing(faces.get(random.nextInt(faces.size())));
        }
        return data;
    }

    private static Set<Material> leafMaterials(MainConfig.Flora flora) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : flora.spawnLeafLitterUnder()) {
            Material m = Material.matchMaterial(name);
            if (m != null) out.add(m);
        }
        return out;
    }

    // --- when ---

    /**
     * World where OpenSeasons places plants: {@code modify-blocks}, a normal world with enabled seasons.
     */
    private boolean placementAllowed(World world) {
        return seasonForChanges(world).isPresent();
    }

    /** season by which blocks change in the world; empty = nothing changes (only a pending restore) */
    private Optional<Season> seasonForChanges(World world) {
        if (!config.get().main().modifyBlocks() || world.getEnvironment() != World.Environment.NORMAL) {
            return Optional.empty();
        }
        return seasons.state(world.getName()).filter(WorldSeasonState::enabled).map(WorldSeasonState::season);
    }

    /**
     * Season by which blocks change in a chunk: {@code permanent-season} of a region, otherwise the season
     * of the world; empty when nothing changes in the world ({@link #seasonForChanges}) or the region forbids
     * {@code season-block-changes}.
     */
    private Optional<Season> chunkSeason(World world, int chunkX, int chunkZ) {
        Optional<Season> season = seasonForChanges(world);
        if (season.isEmpty() || regions == null) return season;
        if (!regionAllows(world, chunkX, chunkZ)) return Optional.empty();
        return regions.seasonAt(world, chunkX, chunkZ).or(() -> season);
    }

    /** blocks may change by season in the chunk: the region does not forbid {@code season-block-changes} */
    private boolean regionAllows(World world, int chunkX, int chunkZ) {
        return regions == null || regions.blockChangesAllowed(world, chunkX, chunkZ);
    }

    private boolean roll(double percent) {
        return random.nextDouble() * 100.0 < percent;
    }

    private List<PlantKind> shuffled(List<PlantKind> kinds) {
        List<PlantKind> out = new ArrayList<>(kinds);
        for (int i = out.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            PlantKind t = out.get(i);
            out.set(i, out.get(j));
            out.set(j, t);
        }
        return out;
    }

    private boolean inGrace(String world, PlacementJournal.Entry e, long now) {
        if (debugGrace.isEmpty()) return false;
        String key = graceKey(world, e);
        Long until = debugGrace.get(key);
        if (until == null) return false;
        if (until > now) return true;
        debugGrace.remove(key);
        return false;
    }

    private static String graceKey(String world, PlacementJournal.Entry e) {
        return world + ":" + BlockKeys.block(e.x(), e.y(), e.z());
    }

    // --- cleanup of loaded chunks after a season change ---

    private int sweepPeriod() {
        return Math.max(20, config.get().main().timing().unloadedChunksCheckDelay());
    }

    private void scheduleSweep() {
        if (sweepTask != null) sweepTask.cancel();
        sweepPeriod = sweepPeriod();
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, sweepPeriod, sweepPeriod);
    }

    /**
     * Every {@code unloaded-chunks-check-delay} ticks: when the season of the world changed (or a restore
     * is pending), queues loaded chunks with a block to remove for cleanup. Unloaded chunks are
     * cleaned up when they load.
     */
    private void sweep() {
        long now = System.currentTimeMillis();
        debugGrace.values().removeIf(until -> until <= now);
        for (World world : plugin.getServer().getWorlds()) {
            String w = world.getName();
            Optional<Season> season = seasonForChanges(world);
            if (!season.equals(sweptSeason.get(w))) sweepNeeded.add(w);
            sweptSeason.put(w, season);
            if (!sweepNeeded.remove(w) || journal.size(w) == 0) continue;
            int queued = 0;
            for (long key : journal.chunkKeys(w)) {
                int cx = BlockKeys.chunkX(key);
                int cz = BlockKeys.chunkZ(key);
                if (!world.isChunkLoaded(cx, cz)) continue;
                Optional<Season> chunkSeason = chunkSeason(world, cx, cz);
                boolean due = journal.chunk(w, cx, cz).stream().anyMatch(e -> FloraRules.due(e, chunkSeason));
                if (due) {
                    ticker.enqueueCleanup(world, cx, cz);
                    queued++;
                }
            }
            // the queue has a limited capacity: while something was found, again next time
            if (queued > 0) sweepNeeded.add(w);
        }
    }

    // --- saving ---

    private void flushAsync() {
        if (!journal.dirty() || writer == null || writer.isShutdown()) return;
        Map<Path, String> files = journal.takeDirty();
        writer.execute(() -> {
            Map<Path, String> failed = PlacementJournal.write(files);
            if (failed.isEmpty() || !plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> failed.forEach((file, error) -> {
                journal.markDirty(file);
                log.warning("The journal of block changes cannot be saved to " + file + ": " + error);
            }));
        });
    }

    private void flushSync() {
        if (!journal.dirty()) return;
        PlacementJournal.write(journal.takeDirty())
                .forEach((file, error) -> log.warning("The journal of block changes cannot be saved to " + file + ": " + error));
    }

    // --- events ---

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        List<String> warnings = new ArrayList<>();
        journal.load(event.getWorld().getName(), warnings);
        warnings.forEach(log::warning);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        flushSync();
        journal.unload(event.getWorld().getName());
    }

    /** chunk with journal entries: after loading clean up what withered or waits for restoring in the meantime */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        if (journal.hasChunk(world.getName(), cx, cz)) ticker.enqueueCleanup(world, cx, cz);
    }

    /** a player placed something at the place: our entry belongs to them, remember their plant */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        String w = b.getWorld().getName();
        if (!journal.loaded(w)) return;
        journal.remove(w, b.getX(), b.getY(), b.getZ());
        if (config.get().main().flora().keepPlayerPlacedPlants()
                && FloraRules.PROTECTED_PLAYER_PLANTS.contains(b.getType())) {
            journal.recordPlayer(w, b.getX(), b.getY(), b.getZ(), b.getBlockData().getAsString());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        journal.remove(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    /** our mushrooms spread by themselves: the new ones belong to them and also disappear at the end of fall */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Block source = event.getSource();
        if (source.getType() != Material.BROWN_MUSHROOM && source.getType() != Material.RED_MUSHROOM) return;
        String w = source.getWorld().getName();
        Optional<PlacementJournal.Entry> from = journal.get(w, source.getX(), source.getY(), source.getZ());
        if (from.isEmpty() || !PlantKind.MUSHROOMS.id().equals(from.get().kind())) return;
        Block target = event.getBlock();
        journal.record(w, target.getX(), target.getY(), target.getZ(), PlantKind.MUSHROOMS.id(),
                event.getNewState().getBlockData().getAsString(), target.getBlockData().getAsString());
    }

    /** {@code disable-berry-damage-for-entities}: sweet berry bushes do not hurt animals nor monsters */
    @EventHandler(ignoreCancelled = true)
    public void onBerryDamage(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof Player || !config.get().main().flora().disableBerryDamageForEntities()) return;
        Block damager = event.getDamager();
        boolean berry = damager != null ? damager.getType() == Material.SWEET_BERRY_BUSH
                : event.getDamagerBlockState() != null && event.getDamagerBlockState().getType() == Material.SWEET_BERRY_BUSH;
        if (berry) event.setCancelled(true);
    }

    // --- debug and restore ---

    /**
     * Result of a manual pass: placed by kind, removed.
     *
     * @param outOfSeason placed outside their season (the ticker leaves them for {@link #DEBUG_GRACE_MS})
     */
    public record DebugResult(Map<PlantKind, Integer> placed, int chunks, JournalRestorer.Result removed,
                              int outOfSeason) {
    }

    /**
     * {@code /openseasons debug flora <kind>}: one pass through the chunks around a place regardless of chance
     * and season (the chunk limits and place rules apply); plants go into the journal and outside their season
     * they stay for {@link #DEBUG_GRACE_MS}.
     */
    public DebugResult debugPlace(Location center, List<PlantKind> kinds) {
        World world = center.getWorld();
        Map<PlantKind, Integer> counts = new HashMap<>();
        int chunks = 0;
        int pcx = center.getBlockX() >> 4;
        int pcz = center.getBlockZ() >> 4;
        for (int dx = -DEBUG_CHUNK_RADIUS; dx <= DEBUG_CHUNK_RADIUS; dx++) {
            for (int dz = -DEBUG_CHUNK_RADIUS; dz <= DEBUG_CHUNK_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!ChunkTicker.loadedWithNeighbours(world, cx, cz)) continue;
                chunks++;
                for (int i = 0; i < DEBUG_SAMPLES; i++) {
                    int x = (cx << 4) + random.nextInt(16);
                    int z = (cz << 4) + random.nextInt(16);
                    for (PlantKind k : shuffled(kinds)) {
                        if (tryColumn(world, x, z, List.of(k), SubSeason.MIDDLE, true)) {
                            counts.merge(k, 1, Integer::sum);
                            break;
                        }
                    }
                }
            }
        }
        placed += counts.values().stream().mapToInt(Integer::intValue).sum();
        Optional<Season> season = seasons.state(world.getName()).map(WorldSeasonState::season);
        int outOfSeason = counts.entrySet().stream()
                .filter(e -> season.map(s -> !e.getKey().aliveIn(s)).orElse(true))
                .mapToInt(Map.Entry::getValue).sum();
        return new DebugResult(Map.copyOf(counts), chunks, JournalRestorer.Result.NONE, outOfSeason);
    }

    /** {@code /openseasons debug flora wither}: removes withered plants around the place right away, also from debug */
    public DebugResult debugWither(Location center) {
        World world = center.getWorld();
        String w = world.getName();
        JournalRestorer.Result total = JournalRestorer.Result.NONE;
        int chunks = 0;
        int pcx = center.getBlockX() >> 4;
        int pcz = center.getBlockZ() >> 4;
        for (int dx = -DEBUG_CHUNK_RADIUS; dx <= DEBUG_CHUNK_RADIUS; dx++) {
            for (int dz = -DEBUG_CHUNK_RADIUS; dz <= DEBUG_CHUNK_RADIUS; dz++) {
                List<PlacementJournal.Entry> entries = journal.chunk(w, pcx + dx, pcz + dz);
                if (entries.isEmpty()) continue;
                chunks++;
                entries.forEach(e -> debugGrace.remove(graceKey(w, e)));
                Optional<Season> season = chunkSeason(world, pcx + dx, pcz + dz);
                total = total.plus(JournalRestorer.restore(journal, w, entries, BlockAccess.of(world),
                        e -> FloraRules.due(e, season), Integer.MAX_VALUE));
            }
        }
        removed += total.restored();
        return new DebugResult(Map.of(), chunks, total, 0);
    }

    /** blocks from OpenSeasons in the world within a radius (radius < 0 = the whole world), without player plants */
    public List<PlacementJournal.Entry> placedWithin(World world, int centerX, int centerZ, int radius) {
        return journal.placedWithin(world.getName(), centerX, centerZ, radius);
    }

    /**
     * Returns blocks to the state before OpenSeasons: loaded chunks in batches right away, the others are marked
     * and restored when they load. The result goes to {@code sender}.
     */
    public void restore(World world, List<PlacementJournal.Entry> entries, CommandSender sender) {
        String w = world.getName();
        for (PlacementJournal.Entry e : entries) {
            journal.rekind(w, e, PlacementJournal.RESTORE);
            debugGrace.remove(graceKey(w, e));
        }
        restoreJobs.add(new RestoreJob(w, entries, sender));
        sweepNeeded.add(w);
    }

    private void runRestoreJobs() {
        RestoreJob job = restoreJobs.peek();
        if (job == null) return;
        World world = plugin.getServer().getWorld(job.world);
        if (world == null) {
            restoreJobs.poll();
            return;
        }
        List<PlacementJournal.Entry> batch = new ArrayList<>();
        while (!job.entries.isEmpty() && batch.size() < RESTORE_PER_TICK) batch.add(job.entries.poll());
        job.result = job.result.plus(JournalRestorer.restore(journal, job.world, batch, BlockAccess.of(world),
                e -> true, Integer.MAX_VALUE));
        if (!job.entries.isEmpty()) return;
        restoreJobs.poll();
        removed += job.result.restored();
        JournalRestorer.Result r = job.result;
        job.sender.sendMessage(Component.text("Restoring world " + job.world + " finished: restored "
                + r.restored() + " blocks, " + r.dropped() + " already changed by someone else (left alone)"
                + (r.failed() > 0 ? ", " + r.failed() + " could not be placed" : "")
                + (r.unloaded() > 0 ? ", " + r.unloaded() + " in unloaded chunks will be restored when they load" : "")
                + ".", NamedTextColor.GREEN));
    }

    /** state for {@code /openseasons debug flora info} */
    public List<String> status(World world) {
        List<String> out = new ArrayList<>();
        String w = world.getName();
        Optional<Season> season = seasonForChanges(world);
        out.add("Seasonal plants in world " + w + ": " + (season.isPresent() ? "change by season " + season.get()
                : "do not change (modify-blocks or disabled seasons)"));
        Map<String, Integer> byKind = PlacementJournal.countByKind(journal.placedWithin(w, 0, 0, -1));
        out.add("  in the journal: " + (byKind.isEmpty() ? "nothing" : byKind.toString()) + ", player plants "
                + (journal.size(w) - byKind.values().stream().mapToInt(Integer::intValue).sum()));
        out.add("  since start placed " + placed + ", removed " + removed + "; chunk visits " + ticker.visits()
                + ", skipped for TPS " + ticker.skippedForTps() + ", in the cleanup queue " + ticker.pendingCleanup());
        out.add("  regions: " + (regions == null || !regions.active() ? "without WorldGuard, changes everywhere"
                : "season-block-changes and permanent-season by WorldGuard"));
        return out;
    }

    // --- settings ---

    private void reloadSettings() {
        List<String> warnings = new ArrayList<>();
        try {
            settings = FloraSettings.load(dataFolder(), warnings);
        } catch (ConfigException e) {
            warnings.add(FloraSettings.FILE + " cannot be loaded, using defaults: " + e.getMessage());
        }
        warnings.forEach(log::warning);
    }

    private ChunkTicker.Settings tickerSettings() {
        MainConfig.Timing t = config.get().main().timing();
        return new ChunkTicker.Settings(2 * Math.max(1, t.delayPerOperation()), settings.chunkRadius(),
                t.prioritiseTps(), t.minTps(), CLEANUP_PER_RUN);
    }

    private Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }
}
