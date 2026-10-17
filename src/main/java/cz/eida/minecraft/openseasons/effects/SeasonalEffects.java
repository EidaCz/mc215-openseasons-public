/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import cz.eida.minecraft.openseasons.sky.SkyPhenomena;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Season particles by {@code particles.*}: fireflies, small and big falling leaves
 * and shooting stars. Everything only around players, without writing to the world; big leaves are temporary displays
 * (not saved, tag {@link #TAG}). Particles and displays are sent only to players with particles
 * turned on ({@link ParticleViewers}). The season is taken at the player's location ({@link RegionService#effectSeasonAt}):
 * in a region with {@code permanent-season} by that one, with {@code season-effects deny} no phenomena.
 */
public final class SeasonalEffects {

    public static final String TAG = "mc215os_leaf";

    static final int FIREFLY_PERIOD = 20;
    static final int SPARK_PERIOD = 2;
    static final int SPARK_RADIUS = 14;
    /** how long it sparkles after {@code /openseasons debug night-sparks} */
    static final int DEBUG_SPARK_TICKS = 300;
    static final int LEAF_PERIOD = 4;
    static final int STAR_PERIOD = 20;
    static final int LEAF_SAMPLES = 16;
    static final int LEAF_RADIUS = 12;
    static final int MAX_LEAVES_PER_PLAYER = 4;
    static final int MAX_LEAVES = 40;

    private static final Set<Material> NATURE = Set.of(Material.GRASS_BLOCK, Material.SHORT_GRASS,
            Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN, Material.MOSS_BLOCK, Material.MOSS_CARPET,
            Material.FIREFLY_BUSH, Material.SWEET_BERRY_BUSH, Material.LILY_PAD);

    private final Plugin plugin;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    private final ParticleViewers viewers;
    private final RegionService regions;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final List<ActiveStar> stars = new ArrayList<>();
    private final List<ActiveLeaf> leaves = new ArrayList<>();
    /** world → night for which sparkling was decided, and the result */
    private final Map<String, Long> sparkNightDecided = new HashMap<>();
    private final Map<String, Boolean> sparkNight = new HashMap<>();
    /** player → tick until which it sparkles around them for testing */
    private final Map<UUID, Integer> debugSparks = new HashMap<>();
    private double windX = 0.02;
    private double windZ = 0.01;
    private BukkitTask task;
    private int ticks;

    private static final class ActiveStar {
        final World world;
        final ShootingStar star;
        int tick;

        ActiveStar(World world, ShootingStar star) {
            this.world = world;
            this.star = star;
        }
    }

    private static final class ActiveLeaf {
        final World world;
        final LeafFall fall;
        final ItemDisplay entity;
        final UUID owner;
        int tick;

        ActiveLeaf(World world, LeafFall fall, ItemDisplay entity, UUID owner) {
            this.world = world;
            this.fall = fall;
            this.entity = entity;
            this.owner = owner;
        }
    }

    public SeasonalEffects(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons,
                           ParticleViewers viewers, RegionService regions) {
        this.plugin = plugin;
        this.config = config;
        this.seasons = seasons;
        this.viewers = viewers;
        this.regions = regions;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** stops and removes all big leaves */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        leaves.forEach(l -> l.entity.remove());
        leaves.clear();
        stars.clear();
    }

    // --- manual triggering (/openseasons debug) ---

    /** a swarm of fireflies around the player, also during the day and away from grass */
    public int fireflies(Player player) {
        return swarm(player, true);
    }

    /** a dense gust of small autumn leaves from the leaves around the player, without leaves above them */
    public int smallLeaves(Player player) {
        int spawned = 0;
        for (Block b : leafBlocks(player, 400)) {
            if (spawned >= 40) break;
            smallLeaf(b, true);
            smallLeaf(b, true);
            spawned += 2;
        }
        if (spawned == 0) {
            for (int i = 0; i < 20; i++) smallLeafAt(player.getWorld(), above(player, 5, 9), true);
            spawned = 20;
        }
        return spawned;
    }

    /** winter sparkling around the player for {@link #DEBUG_SPARK_TICKS} ticks, also during the day */
    public void nightSparks(Player player) {
        debugSparks.put(player.getUniqueId(), ticks + DEBUG_SPARK_TICKS);
        sparks(player, config.get().main().particles().nightSparks());
    }

    /** three big leaves from the leaves around the player, without leaves above them */
    public int bigLeaves(Player player) {
        List<Block> found = leafBlocks(player, 80);
        for (int i = 0; i < 3; i++) {
            if (!found.isEmpty()) {
                Block b = found.get(random.nextInt(found.size()));
                bigLeaf(player.getWorld(), b.getX() + 0.5, b.getY() - 0.3, b.getZ() + 0.5, player.getUniqueId());
            } else {
                double[] p = above(player, 6, 10);
                bigLeaf(player.getWorld(), p[0], p[1], p[2], player.getUniqueId());
            }
        }
        return 3;
    }

    /** a shooting star above the player, also during the day */
    public void shootingStar(Player player) {
        star(player);
    }

    // --- running ---

    private void tick() {
        ticks++;
        if (ticks % 200 == 0) {
            windX = Math.clamp(windX + random.nextDouble(-0.01, 0.01), -0.04, 0.04);
            windZ = Math.clamp(windZ + random.nextDouble(-0.01, 0.01), -0.04, 0.04);
        }
        MainConfig.Particles p = config.get().main().particles();
        for (World world : plugin.getServer().getWorlds()) {
            // phenomena are created only around players with particles turned on
            List<Player> players = viewers.players(world);
            if (players.isEmpty() && debugSparks.isEmpty()) continue;
            Optional<WorldSeasonState> state = seasons.state(world.getName());
            boolean night = EffectRules.night(world.getTime());

            // fireflies do not fly in the rain
            if (ticks % FIREFLY_PERIOD == 0 && night && !world.hasStorm() && p.fireflies().enabled()) {
                for (Player player : players) {
                    if (EffectRules.activeAt(true, p.fireflies().seasons(), regions.effectSeasonAt(player.getLocation()))
                            && EffectRules.roll(random, p.fireflies().chance())) swarm(player, false);
                }
            }
            if (ticks % LEAF_PERIOD == 0) {
                // small leaves: in the configured seasons (by default fall) fully and coloured, otherwise rarely and green
                MainConfig.Chance smallCfg = p.smallFallingLeaves();
                for (Player player : players) {
                    Optional<Season> local = regions.effectSeasonAt(player.getLocation());
                    boolean autumn = EffectRules.activeAt(smallCfg.enabled(), smallCfg.seasons(), local);
                    double small = autumn ? smallCfg.chance()
                            : smallCfg.enabled() && local.isPresent()
                            ? smallCfg.chance() * EffectRules.offSeasonLeafFactor(local.get()) : 0;
                    boolean big = EffectRules.activeAt(p.fallingLeaves().enabled(), p.fallingLeaves().seasons(), local);
                    if (small > 0 || big) sampleLeaves(player, small, autumn, big ? p.fallingLeaves().chance() : 0);
                }
            }
            MainConfig.NightSparks ns = p.nightSparks();
            // sparkling is decided per world, it applies only where the player's location has a sparkling season (also from a region)
            boolean sparkSeason = ns.enabled() && state.isPresent() && state.get().enabled();
            if (night && sparkSeason) {
                long nightId = EffectRules.nightIndex(world.getFullTime());
                if (!Long.valueOf(nightId).equals(sparkNightDecided.get(world.getName()))) {
                    // sparkling is decided once per night and world
                    sparkNightDecided.put(world.getName(), nightId);
                    sparkNight.put(world.getName(), EffectRules.roll(random, ns.eventChance()));
                }
            }
            if (ticks % SPARK_PERIOD == 0) {
                boolean sparking = night && sparkSeason && !world.hasStorm()
                        && sparkNight.getOrDefault(world.getName(), false);
                for (Player player : world.getPlayers()) {
                    Integer until = debugSparks.get(player.getUniqueId());
                    if (until != null && until < ticks) {
                        debugSparks.remove(player.getUniqueId());
                        until = null;
                    }
                    boolean here = sparking && EffectRules.activeAt(true, ns.seasons(), regions.effectSeasonAt(player.getLocation()));
                    if ((here || until != null) && viewers.enabled(player)) sparks(player, ns);
                }
            }
            MainConfig.ShootingStars s = p.shootingStars();
            if (ticks % STAR_PERIOD == 0 && !players.isEmpty() && night && !world.hasStorm() && s.enabled()
                    && EffectRules.roll(random, s.chance())) {
                List<Player> under = players.stream()
                        .filter(pl -> EffectRules.activeAt(true, s.seasons(), regions.effectSeasonAt(pl.getLocation()))).toList();
                if (!under.isEmpty()) star(under.get(random.nextInt(under.size())));
            }
        }
        advanceStars();
        advanceLeaves();
    }

    /**
     * A tiny swarm: 4–7 fireflies close together (within a block) above grass or flowers 4–14 blocks
     * from the player, low above the ground.
     */
    private int swarm(Player player, boolean anywhere) {
        World world = player.getWorld();
        Location at = player.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            double distance = random.nextDouble(4, 14);
            int x = (int) Math.floor(at.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(at.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block top = world.getHighestBlockAt(x, z);
            if (!anywhere && (!nature(top.getType()) || Math.abs(top.getY() - at.getY()) > 8)) continue;
            double cx = x + 0.5;
            double cy = (anywhere ? Math.max(top.getY(), at.getY() - 1) : top.getY()) + 1 + random.nextDouble(0.3, 1.2);
            double cz = z + 0.5;
            int count = random.nextInt(4, 8);
            for (int i = 0; i < count; i++) {
                viewers.spawn(world, Particle.FIREFLY, cx + random.nextDouble(-0.6, 0.6), cy + random.nextDouble(-0.4, 0.4),
                        cz + random.nextDouble(-0.6, 0.6), 1, 0, 0, 0, 0, null, false);
            }
            return count;
        }
        return 0;
    }

    /**
     * Winter sparkling: white sparks ({@code END_ROD}) in the air around the player. {@code particle-count}
     * is the number of sparks per second (default 200),
     * {@code relative-height} the height of the layer above the player; more sparks are lower near the player.
     */
    private void sparks(Player player, MainConfig.NightSparks ns) {
        World world = player.getWorld();
        Location at = player.getLocation();
        int count = Math.max(1, ns.particleCount() * SPARK_PERIOD / 20);
        int height = Math.max(4, ns.relativeHeight());
        for (int i = 0; i < count; i++) {
            double r = random.nextDouble();
            viewers.spawn(world, Particle.END_ROD, at.getX() + random.nextDouble(-SPARK_RADIUS, SPARK_RADIUS),
                    at.getY() - 1 + (height + 1) * r * r, at.getZ() + random.nextDouble(-SPARK_RADIUS, SPARK_RADIUS),
                    1, 0, 0, 0, 0.005, null, false);
        }
    }

    private static boolean nature(Material m) {
        return NATURE.contains(m) || Tag.FLOWERS.isTagged(m) || Tag.LEAVES.isTagged(m);
    }

    /** a few random places around the player; from leaves with free space below a leaf falls by chance */
    private void sampleLeaves(Player player, double smallChance, boolean autumn, double bigChance) {
        for (Block b : leafBlocks(player, LEAF_SAMPLES)) {
            if (smallChance > 0 && EffectRules.roll(random, smallChance)) smallLeaf(b, autumn);
            if (bigChance > 0 && EffectRules.roll(random, bigChance) && leaves.size() < MAX_LEAVES
                    && leaves.stream().filter(l -> l.owner.equals(player.getUniqueId())).count() < MAX_LEAVES_PER_PLAYER) {
                bigLeaf(player.getWorld(), b.getX() + 0.5, b.getY() - 0.3, b.getZ() + 0.5, player.getUniqueId());
            }
        }
    }

    /** leaf blocks with free space below them among {@code samples} random places around the player */
    private List<Block> leafBlocks(Player player, int samples) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        List<Block> found = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            int x = eye.getBlockX() + random.nextInt(-LEAF_RADIUS, LEAF_RADIUS + 1);
            int y = eye.getBlockY() + random.nextInt(-2, 15);
            int z = eye.getBlockZ() + random.nextInt(-LEAF_RADIUS, LEAF_RADIUS + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = world.getBlockAt(x, y, z);
            if (Tag.LEAVES.isTagged(b.getType()) && b.getRelative(BlockFace.DOWN).isPassable()) found.add(b);
        }
        return found;
    }

    private void smallLeaf(Block leaf, boolean autumn) {
        smallLeafAt(leaf.getWorld(), new double[]{leaf.getX() + random.nextDouble(), leaf.getY() - 0.05,
                leaf.getZ() + random.nextDouble()}, autumn);
    }

    private void smallLeafAt(World world, double[] p, boolean autumn) {
        viewers.spawn(world, Particle.TINTED_LEAVES, p[0], p[1], p[2], 1, 0, 0, 0, 0,
                Color.fromRGB(EffectRules.leafColor(random, autumn)), false);
    }

    private double[] above(Player player, double min, double max) {
        Location at = player.getLocation();
        return new double[]{at.getX() + random.nextDouble(-4, 4), at.getY() + random.nextDouble(min, max),
                at.getZ() + random.nextDouble(-4, 4)};
    }

    private void bigLeaf(World world, double x, double y, double z, UUID owner) {
        LeafFall fall = LeafFall.start(random, x, y, z, windX, windZ);
        ItemStack item = leafItem();
        ItemDisplay display = world.spawn(new Location(world, x, y, z, fall.yaw(0), fall.pitch(0)), ItemDisplay.class, d -> {
            ParticleViewers.hide(d);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(item);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setViewRange(1.5f);
            d.setShadowRadius(0f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f),
                    new Quaternionf()));
        });
        viewers.reveal(display);
        leaves.add(new ActiveLeaf(world, fall, display, owner));
    }

    /** leaf item from {@code falling-leaf-item} and {@code falling-leaf-custommodeldata} */
    @SuppressWarnings("deprecation")
    private ItemStack leafItem() {
        MainConfig.Particles p = config.get().main().particles();
        Material material = Material.matchMaterial(p.fallingLeafItem());
        ItemStack item = new ItemStack(material != null && material.isItem() ? material : Material.KELP);
        if (p.fallingLeafCustomModelData() > 0) {
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(p.fallingLeafCustomModelData());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void star(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        MainConfig.ShootingStars cfg = config.get().main().particles().shootingStars();
        double base = Math.max(at.getY(), world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ()));
        double sight = SkyPhenomena.sight(world.getViewDistance(), player.getClientViewDistance());
        double height = SkyPhenomena.flightHeight(cfg.relativeHeight(), sight);
        double maxDistance = 0.55 * SkyPhenomena.flightRadius(height, sight);
        stars.add(new ActiveStar(world, ShootingStar.plan(random, at.getX(), base, at.getZ(), height, maxDistance)));
    }

    /** a trail of glowing points that fade out by themselves, and sparks in the head */
    private void advanceStars() {
        Iterator<ActiveStar> it = stars.iterator();
        while (it.hasNext()) {
            ActiveStar s = it.next();
            for (double[] p : s.star.trail(s.tick)) {
                viewers.spawn(s.world, Particle.END_ROD, p[0], p[1], p[2], 1, 0, 0, 0, 0, null, true);
            }
            double[] head = s.star.head(s.tick);
            viewers.spawn(s.world, Particle.FIREWORK, head[0], head[1], head[2], 2, 0.1, 0.1, 0.1, 0.01, null, true);
            if (++s.tick >= s.star.duration()) it.remove();
        }
    }

    private void advanceLeaves() {
        Iterator<ActiveLeaf> it = leaves.iterator();
        while (it.hasNext()) {
            ActiveLeaf l = it.next();
            l.tick++;
            if (l.tick % 2 != 0) continue;
            double[] p = l.fall.position(l.tick);
            boolean gone = !l.entity.isValid() || l.tick >= LeafFall.MAX_TICKS
                    || !l.world.isChunkLoaded((int) Math.floor(p[0]) >> 4, (int) Math.floor(p[2]) >> 4)
                    || !l.world.getBlockAt((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])).isPassable();
            if (gone) {
                l.entity.remove();
                it.remove();
            } else {
                l.entity.teleport(new Location(l.world, p[0], p[1], p[2], l.fall.yaw(l.tick), l.fall.pitch(l.tick)));
            }
        }
    }
}
