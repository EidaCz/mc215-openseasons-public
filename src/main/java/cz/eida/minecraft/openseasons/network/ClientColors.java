/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.event.simple.PacketConfigReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketConfigSendEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector2i;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientSelectKnownPacks;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkBiomes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import cz.eida.minecraft.openseasons.biome.MorningFog;
import cz.eida.minecraft.openseasons.biome.SeasonTints;
import cz.eida.minecraft.openseasons.config.ClientColorSettings;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.region.RegionSettings;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Landscape colours by season on the client: when a player joins, seasonal variants are added to the
 * biome registry ({@link BiomeTable}), in chunk packets the biomes are
 * rewritten to the variant by the look of the world ({@link ChunkBiomes}) and when the look changes (phase,
 * season, morning fog) the chunks around players are sent again. Only through PacketEvents and the public
 * Paper API, without NMS.
 *
 * <p>The look of the world is computed every second on the main thread; packets only read the finished map.
 *
 * <p>Regions ({@link RegionService}, chunk column): with {@code season-effects deny} the client sees the chunk
 * as the server has it, with {@code permanent-season} in the colours of that season (half of the season, no fog), with a
 * real-date period or {@code season-source REAL} by the real calendar. The look of the whole world takes
 * the periods and the real season of the world into account ({@code real-calendar.yml}). Packets take the settings only from the cache; a chunk not yet in it is sent again once computed.
 */
public final class ClientColors {

    /** chunks per tick when refreshing */
    private static final int REFRESH_PER_TICK = 48;
    /** refresh radius on a fog change, chunks (fog is only visible around the player) */
    private static final int MIST_RADIUS = 8;
    private static final String BIOME_REGISTRY = "minecraft:worldgen/biome";
    /** highest approximate size of the added variants, bytes (the registry packet has a limit of 8 MiB) */
    private static final long MAX_BYTES = 6L << 20;

    /** look of a world: look number {@link BiomeTable#look} (season and step) and top of the fog (absolute y) */
    record WorldLook(int look, int mistTop) {
    }

    /**
     * Settings: colours (biome variants apply to new connections) and block replacements.
     *
     * @param enabled {@code modify-biome-on-client}
     */
    private record Settings(boolean enabled, LoadedConfig config, SeasonTints tints, boolean[] mistLooks,
                            BlockSwaps swaps) {
        /** the look of worlds is needed when colours or block replacements are running */
        boolean active() {
            return enabled || swaps != BlockSwaps.NONE;
        }
    }

    private record CachedTable(Settings settings, List<RegistryElement> elements, BiomeTable table) {
    }

    private record ChunkRef(String world, int x, int z) {
    }

    /** preview of the look of a world (command), only on clients, nothing is saved */
    private record Preview(Season season, double progress, boolean mist) {
    }

    private final JavaPlugin plugin;
    private final Logger log;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    private final RegionService regions;
    private final Map<UUID, BiomeTable> users = new ConcurrentHashMap<>();
    private final Map<String, WorldLook> looks = new ConcurrentHashMap<>();
    /** look by the game calendar (for regions with {@code season-source GAME} in a world with the real season) */
    private final Map<String, WorldLook> gameLooks = new ConcurrentHashMap<>();
    private final Set<ChunkRef> queue = new LinkedHashSet<>();
    private final Map<String, Preview> previews = new ConcurrentHashMap<>();
    /** players with colours turned off ({@code /toggleseasoncolors}): they see the world as the server has it */
    private final Set<UUID> colorsOff = ConcurrentHashMap.newKeySet();
    private final AtomicLong rewritten = new AtomicLong();
    private final AtomicLong rewriteNanos = new AtomicLong();
    private volatile Settings settings;
    private volatile CachedTable cache;
    private volatile boolean strippedPacksReported;
    private PacketListenerCommon listener;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public ClientColors(JavaPlugin plugin, Supplier<LoadedConfig> config, SeasonService seasons, RegionService regions) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.config = config;
        this.seasons = seasons;
        this.regions = regions;
    }

    public void start() {
        settings = settings(config.get());
        listener = PacketEvents.getAPI().getEventManager().registerListener(new Packets());
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateLooks, 20L, 20L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::drainQueue, 1L, 1L));
        regions.addChangeListener(this::regionsChanged);
        // a new real day: another real season or periods in regions and worlds
        regions.addRealDateListener(() -> {
            updateLooks();
            for (World w : plugin.getServer().getWorlds()) {
                if (looks.containsKey(w.getName())) refresh(w, Integer.MAX_VALUE);
            }
        });
        log.info("Landscape colours on the client: " + (settings.enabled() ? "enabled" : "disabled (modify-biome-on-client)")
                + ", block replacements " + (settings.swaps() == BlockSwaps.NONE ? "disabled" : "enabled")
                + "; biome groups " + settings.config().biomes().groups().size() + " from "
                + settings.config().biomes().folder().map(Object::toString).orElse("-"));
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        listener = null;
        users.clear();
        looks.clear();
        gameLooks.clear();
        queue.clear();
    }

    /** new configuration: variants apply to new connections, the look of worlds right away */
    public void reload() {
        settings = settings(config.get());
        cache = null;
        // block replacements and the look may have changed: send the chunks to players again
        looks.clear();
        updateLooks();
        plugin.getServer().getWorlds().forEach(w -> refresh(w, Integer.MAX_VALUE));
    }

    /**
     * Preview: the world is shown to clients as in the given season and its part (percent), with or without fog.
     * Lasts until {@link #clearPreview} or a restart.
     */
    public void preview(String world, Season season, double progressPercent, boolean mist) {
        previews.put(world, new Preview(season, Math.clamp(progressPercent, 0.0, 100.0), mist));
        updateLooks();
    }

    public boolean clearPreview(String world) {
        boolean had = previews.remove(world) != null;
        if (had) updateLooks();
        return had;
    }

    /** players with colours turned off (loaded at start) */
    public void setColorsDisabled(Set<UUID> players) {
        colorsOff.clear();
        colorsOff.addAll(players);
    }

    public Set<UUID> colorsDisabled() {
        return Set.copyOf(colorsOff);
    }

    public boolean colorsEnabled(UUID player) {
        return !colorsOff.contains(player);
    }

    /**
     * Personal turning landscape colours and block replacements off and on ({@code /toggleseasoncolors}):
     * a player with colours turned off gets biomes and blocks as the server has them. The chunks around them are sent again.
     *
     * @return colours are newly turned on
     */
    public boolean toggle(Player player) {
        boolean enabled = colorsOff.remove(player.getUniqueId());
        if (!enabled) colorsOff.add(player.getUniqueId());
        if (looks.containsKey(player.getWorld().getName())) refresh(player, Integer.MAX_VALUE);
        return enabled;
    }

    /** state for {@code /openseasons colors}: season, step, fog */
    public String describe(World world) {
        WorldLook look = looks.get(world.getName());
        if (look == null) return "colours are not shown (world without seasons, Nether/End or disabled)";
        Season season = Season.values()[look.look() / SeasonTints.STEPS];
        int step = look.look() % SeasonTints.STEPS;
        int from = step * (100 / SeasonTints.STEPS);
        return season + " " + from + "–" + (from + 100 / SeasonTints.STEPS) + " %"
                + (look.mistTop() == MorningFog.NONE ? "" : ", fog up to y " + look.mistTop())
                + (previews.containsKey(world.getName()) ? " (preview)" : "");
    }

    /** for listing: players with variants, rewritten chunks, average µs */
    public String stats() {
        long n = rewritten.get();
        CachedTable c = cache;
        return "players " + users.size() + ", variants " + (c == null ? 0 : c.table().added().size())
                + ", rewritten chunks " + n + (n == 0 ? "" : " (" + rewriteNanos.get() / n / 1000 + " µs)")
                + ", queued " + queue.size() + ", turned off by players " + colorsOff.size();
    }

    private Settings settings(LoadedConfig c) {
        SeasonTints tints = new SeasonTints(c.main().subSeasons().enabled(), c.main().subSeasons().foliageAdjustments(),
                c.clientColors().autumnLeaves(), c.clientColors().springBlossom() / 100.0);
        boolean[] mist = new boolean[BiomeTable.LOOKS];
        ClientColorSettings.MorningFog fog = c.clientColors().morningFog();
        if (fog.enabled() && fog.chance() > 0) {
            for (int p = 0; p < 100; p++) {
                if (p + 1 > fog.fromProgress()) mist[BiomeTable.look(Season.FALL, tints.step(p))] = true;
            }
        }
        BlockSwaps swaps = BlockSwaps.NONE;
        try {
            swaps = BlockSwaps.build(c.main().blockReplacements(), c.clientColors().disabledBlockReplacements(),
                    PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), log::warning);
        } catch (RuntimeException e) {
            log.warning("Block replacements cannot be prepared: " + e);
        }
        return new Settings(c.main().modifyBiomeOnClient(), c, tints, mist, swaps);
    }

    /** table for a client registry; the same registry and settings share one */
    private BiomeTable tableFor(List<RegistryElement> elements, Settings s) {
        CachedTable c = cache;
        if (c != null && c.settings() == s && c.elements().equals(elements)) return c.table();
        long t0 = System.nanoTime();
        String ns = s.config().main().biomeDisplayName();
        BiomeTable table = BiomeTable.build(elements, s.config().biomes(), s.tints(), ns, s.mistLooks());
        // the client accepts a packet up to 8 MiB: with a big registry without fog, then without smooth transitions
        if (table.approxBytes() > MAX_BYTES) {
            log.warning("Landscape colours: " + table.added().size() + " variants are too many, morning fog is left out.");
            table = BiomeTable.build(elements, s.config().biomes(), s.tints(), ns, new boolean[BiomeTable.LOOKS]);
        }
        if (table.approxBytes() > MAX_BYTES) {
            log.warning("Landscape colours: still too many variants, transitions only by season (like sub-seasons: false).");
            SeasonTints coarse = new SeasonTints(false, s.config().main().subSeasons().foliageAdjustments(),
                    s.config().clientColors().autumnLeaves());
            table = BiomeTable.build(elements, s.config().biomes(), coarse, ns, new boolean[BiomeTable.LOOKS]);
        }
        cache = new CachedTable(s, List.copyOf(elements), table);
        log.info("Landscape colours: " + table.added().size() + " seasonal variants for " + elements.size() + " biomes, about "
                + table.approxBytes() / 1024 + " KiB (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
        return table;
    }

    // --- look of worlds (main thread) ---

    private void updateLooks() {
        Settings s = settings;
        Set<String> present = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            Optional<WorldLook> next = s.active() ? lookOf(world, s, false) : Optional.empty();
            Optional<WorldLook> nextGame = s.active() ? lookOf(world, s, true) : Optional.empty();
            String name = world.getName();
            WorldLook prev;
            WorldLook prevGame;
            if (next.isPresent()) {
                present.add(name);
                prev = looks.put(name, next.get());
                prevGame = gameLooks.put(name, nextGame.orElse(next.get()));
            } else {
                prev = looks.remove(name);
                prevGame = gameLooks.remove(name);
            }
            WorldLook now = next.orElse(null);
            WorldLook nowGame = next.isPresent() ? gameLooks.get(name) : null;
            boolean gameChanged = !java.util.Objects.equals(prevGame, nowGame) && regions.active();
            if (java.util.Objects.equals(prev, now) && !gameChanged) continue;
            boolean onlyMist = !gameChanged && prev != null && now != null && prev.look() == now.look()
                    && now.mistTop() != MorningFog.NONE;
            refresh(world, onlyMist ? MIST_RADIUS : Integer.MAX_VALUE);
        }
        looks.keySet().retainAll(present);
        gameLooks.keySet().retainAll(present);
    }

    /**
     * Look of a world: the season of the whole world ({@link RegionService#worldLocal}: period, real season, game
     * calendar), with {@code gameCalendar} always by the game calendar. A preview takes precedence in both.
     */
    private Optional<WorldLook> lookOf(World world, Settings s, boolean gameCalendar) {
        if (world.getEnvironment() != World.Environment.NORMAL) return Optional.empty();
        Optional<WorldSeasonState> state = seasons.state(world.getName()).filter(WorldSeasonState::enabled);
        Preview preview = previews.get(world.getName());
        if (state.isEmpty() && preview == null) return Optional.empty();
        long time = world.getTime();
        double fraction = Math.floorMod(time + 6000, 24000L) / 24000.0;
        Season season;
        double progress;
        if (preview != null) {
            season = preview.season();
            progress = preview.progress();
        } else {
            RegionService.Local local = gameCalendar ? null : regions.worldLocal(world).orElse(null);
            if (local == null || local.origin() == RegionService.Origin.GAME) {
                season = state.get().season();
                progress = seasons.calendar().seasonProgress(state.get().date(), season, fraction);
            } else {
                season = local.season();
                progress = local.progress();
            }
        }
        ClientColorSettings.MorningFog fog = s.config().clientColors().morningFog();
        int top = preview != null && preview.mist() ? fog.height()
                : MorningFog.layerTop(fog, world.getName(), SeasonService.clockDay(world.getFullTime()), season, progress, time);
        return Optional.of(new WorldLook(BiomeTable.look(season, s.tints().step(progress)),
                top == MorningFog.NONE ? MorningFog.NONE : world.getSeaLevel() + top));
    }

    /** send the chunks around the players of a world again, nearest first */
    private void refresh(World world, int maxRadius) {
        for (Player p : world.getPlayers()) refresh(p, maxRadius);
    }

    private void refresh(Player p, int maxRadius) {
        int radius = Math.min(maxRadius, p.getViewDistance() + 1);
        int cx = p.getLocation().getBlockX() >> 4;
        int cz = p.getLocation().getBlockZ() >> 4;
        String world = p.getWorld().getName();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) queue.add(new ChunkRef(world, cx + dx, cz + dz));
                }
            }
        }
    }

    /** changed regions: send the affected chunks again (only in a world where colours are shown) */
    private void regionsChanged(World world, List<RegionService.ChunkPos> chunks) {
        if (!looks.containsKey(world.getName())) return;
        for (RegionService.ChunkPos c : chunks) queue.add(new ChunkRef(world.getName(), c.x(), c.z()));
    }

    /**
     * Look of a chunk by region: {@code null} with {@code season-effects deny}; override by the region (period
     * from {@code real-periods}, {@code permanent-season} at half of the season, {@code season-source REAL}) without
     * fog; {@code season-source GAME} the look by the game calendar; otherwise the look of the world. A chunk not
     * yet in the cache gets the look of the world for now.
     */
    private WorldLook regional(Player player, WorldLook world, int chunkX, int chunkZ) {
        if (!regions.active()) return world;
        RegionSettings r = regions.cached(player.getWorld(), chunkX, chunkZ);
        if (r == null) return world;
        if (!r.effects()) return null;
        Optional<RegionService.Local> local = regions.regionOverride(r);
        if (local.isPresent()) {
            return new WorldLook(BiomeTable.look(local.get().season(), settings.tints().step(local.get().progress())),
                    MorningFog.NONE);
        }
        if (regions.forcesGame(r)) return gameLooks.getOrDefault(player.getWorld().getName(), world);
        return world;
    }

    private void drainQueue() {
        Iterator<ChunkRef> it = queue.iterator();
        for (int i = 0; i < REFRESH_PER_TICK && it.hasNext(); i++) {
            ChunkRef c = it.next();
            it.remove();
            World w = plugin.getServer().getWorld(c.world());
            if (w != null && w.isChunkLoaded(c.x(), c.z())) w.refreshChunk(c.x(), c.z());
        }
    }

    // --- packets (network threads) ---

    private final class Packets extends SimplePacketListenerAbstract {

        Packets() {
            super(PacketListenerPriority.NORMAL);
        }

        /** a client that knows the vanilla pack would get the biomes without data; variants need data */
        @Override
        public void onPacketConfigReceive(PacketConfigReceiveEvent event) {
            if (event.getPacketType() != PacketType.Configuration.Client.SELECT_KNOWN_PACKS || !settings.enabled()) return;
            WrapperConfigClientSelectKnownPacks w = new WrapperConfigClientSelectKnownPacks(event);
            if (w.getKnownPacks().isEmpty()) return;
            if (!strippedPacksReported) {
                strippedPacksReported = true;
                log.info("Landscape colours: clients get registries with data (known packs " + w.getKnownPacks() + " are not used).");
            }
            w.setKnownPacks(List.of());
            event.markForReEncode(true);
        }

        @Override
        public void onPacketConfigSend(PacketConfigSendEvent event) {
            if (event.getPacketType() != PacketType.Configuration.Server.REGISTRY_DATA) return;
            WrapperConfigServerRegistryData reg = new WrapperConfigServerRegistryData(event);
            if (reg.getRegistryKey() == null || !BIOME_REGISTRY.equals(reg.getRegistryKey().toString())) return;
            UUID uuid = event.getUser().getUUID();
            Settings s = settings;
            if (!s.enabled() || uuid == null) {
                if (uuid != null) users.remove(uuid);
                return;
            }
            List<RegistryElement> elements = reg.getElements();
            BiomeTable table;
            try {
                table = tableFor(elements, s);
            } catch (RuntimeException e) {
                log.warning("Landscape colours: biome variants cannot be built: " + e);
                users.remove(uuid);
                return;
            }
            if (table.added().isEmpty()) {
                users.remove(uuid);
                return;
            }
            List<RegistryElement> merged = new ArrayList<>(elements.size() + table.added().size());
            merged.addAll(elements);
            merged.addAll(table.added());
            reg.setElements(merged);
            event.markForReEncode(true);
            users.put(uuid, table);
        }

        @Override
        public void onPacketPlaySend(PacketPlaySendEvent event) {
            PacketType.Play.Server type = event.getPacketType();
            if (type == PacketType.Play.Server.CHUNK_DATA) {
                chunkData(event);
            } else if (type == PacketType.Play.Server.CHUNK_BIOMES) {
                chunkBiomes(event);
            } else if (type == PacketType.Play.Server.BLOCK_CHANGE) {
                blockChange(event);
            } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                multiBlockChange(event);
            }
        }

        private void chunkData(PacketPlaySendEvent event) {
            WorldLook look = lookOf(event);
            if (look == null) return;
            long t0 = System.nanoTime();
            WrapperPlayServerChunkData w = null;
            Column column = null;
            if (regions.active()) {
                w = new WrapperPlayServerChunkData(event);
                column = w.getColumn();
                look = regional(event.getPlayer(), look, column.getX(), column.getZ());
                if (look == null) return;
            }
            User user = event.getUser();
            BiomeTable table = user.getUUID() == null ? null : users.get(user.getUUID());
            boolean biomes = table != null && table.changes(look.look());
            Map<Integer, Integer> swaps = settings.swaps().forLook(look.look());
            if (!biomes && swaps == null) return;
            if (w == null) {
                w = new WrapperPlayServerChunkData(event);
                column = w.getColumn();
            }
            boolean changed = biomes && ChunkBiomes.rewrite(column, new ChunkBiomes.Context(table, look.look(),
                    look.mistTop(), user.getMinWorldHeight(), user.getTotalWorldHeight()));
            if (swaps != null) changed |= BlockSwaps.apply(column, swaps);
            if (changed) {
                w.setColumn(column);
                event.markForReEncode(true);
            }
            rewritten.incrementAndGet();
            rewriteNanos.addAndGet(System.nanoTime() - t0);
        }

        private void blockChange(PacketPlaySendEvent event) {
            if (settings.swaps() == BlockSwaps.NONE) return;
            WorldLook look = lookOf(event);
            if (look == null) return;
            WrapperPlayServerBlockChange w = new WrapperPlayServerBlockChange(event);
            Map<Integer, Integer> swaps = swaps(event, look, w.getBlockPosition().getX() >> 4, w.getBlockPosition().getZ() >> 4);
            if (swaps == null) return;
            Integer r = swaps.get(w.getBlockId());
            if (r == null) return;
            w.setBlockID(r);
            event.markForReEncode(true);
        }

        private void multiBlockChange(PacketPlaySendEvent event) {
            if (settings.swaps() == BlockSwaps.NONE) return;
            WorldLook look = lookOf(event);
            if (look == null) return;
            WrapperPlayServerMultiBlockChange w = new WrapperPlayServerMultiBlockChange(event);
            Map<Integer, Integer> swaps = swaps(event, look, w.getChunkPosition().getX(), w.getChunkPosition().getZ());
            if (swaps == null) return;
            boolean changed = false;
            for (WrapperPlayServerMultiBlockChange.EncodedBlock b : w.getBlocks()) {
                Integer r = swaps.get(b.getBlockId());
                if (r != null) {
                    b.setBlockId(r);
                    changed = true;
                }
            }
            if (changed) event.markForReEncode(true);
        }

        /** block replacements for the look of a chunk (by region) */
        private Map<Integer, Integer> swaps(PacketPlaySendEvent event, WorldLook world, int chunkX, int chunkZ) {
            WorldLook look = regional(event.getPlayer(), world, chunkX, chunkZ);
            return look == null ? null : settings.swaps().forLook(look.look());
        }

        /** biomes changed on the server (e.g. /fillbiome): instead whole chunks, which are rewritten */
        private void chunkBiomes(PacketPlaySendEvent event) {
            User user = event.getUser();
            BiomeTable table = user.getUUID() == null ? null : users.get(user.getUUID());
            WorldLook look = table == null ? null : lookOf(event);
            if (look == null || !table.changes(look.look())) return;
            Player player = event.getPlayer();
            WrapperPlayServerChunkBiomes w = new WrapperPlayServerChunkBiomes(event);
            List<Vector2i> chunks = List.copyOf(w.getChunks().keySet());
            event.setCancelled(true);
            String world = player.getWorld().getName();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Vector2i c : chunks) queue.add(new ChunkRef(world, c.getX(), c.getZ()));
            });
        }

        /** look of the player's world; {@code null} without a look or with colours turned off */
        private WorldLook lookOf(PacketPlaySendEvent event) {
            Player player = event.getPlayer();
            if (player == null || colorsOff.contains(player.getUniqueId())) return null;
            return looks.get(player.getWorld().getName());
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) {
            UUID uuid = event.getUser().getUUID();
            if (uuid != null) users.remove(uuid);
        }
    }
}
