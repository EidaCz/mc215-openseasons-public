/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.sky;

import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Sky phenomena, so far a UFO: now and then a triangular craft or a formation of three flies over a player.
 * The hull is made of thin black plates (block displays, see {@link UfoShape}), the lights are text displays.
 * Everything is temporary (not saved with the world, not on maps) and vanishes after the fly-over.
 *
 * <p>The entities stay at one place above the player the whole time (in a loaded chunk and within entity tracking
 * range) and the craft moves across the sky only by the transformation of the displays, which the client interpolates smoothly.
 * The path may therefore start hundreds of blocks away regardless of loaded chunks. Once a minute
 * the dice are rolled in every world with players and seasons by {@code particles.UFO.chance}.
 *
 * <p>The UFO belongs to the season particles ({@code particles.UFO}): a player with particles turned off
 * ({@link ParticleViewers}) does not see it and it does not fly over them. The season is taken at the player's location
 * ({@link RegionService#effectSeasonAt}); it does not fly over a region with {@code season-effects deny}.
 */
public final class SkyPhenomena {

    /** tag of the entities, so they can be cleaned up manually: /kill @e[tag=mc215os_ufo] */
    public static final String TAG = "mc215os_ufo";

    static final int CHECK_PERIOD = 1200;
    static final double HOVER_CHANCE = 0.3;
    /** how often the transformation changes; in between the client interpolates it smoothly */
    static final int MOVE_PERIOD = 2;
    /** at most this share of the view distance the craft may fly above the surface, so it does not vanish in the fog */
    static final double MAX_HEIGHT_SHARE = 0.65;
    /** start and end of the path just inside the view distance (the craft flies out of the fog and disappears into it again) */
    static final double START_SHARE = 0.95;

    private static final Component WHITE_LIGHT = Component.text("●", TextColor.color(0xFFF6E0));
    private static final Component RED_LIGHT = Component.text("●", TextColor.color(0xFF3B2F));

    private final Plugin plugin;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    private final ParticleViewers viewers;
    private final RegionService regions;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final List<Formation> active = new ArrayList<>();
    private final List<UfoShape.PieceTransform> hullPieces =
            UfoShape.hullPieces().stream().map(UfoShape::decompose).toList();
    private BukkitTask task;
    private int ticks;

    /** one fly-over: path, place of the entities and the entities of each craft of the formation */
    private record Formation(World world, Location anchor, UfoFlight flight, List<Craft> crafts) {
    }

    /** hull (plates) and lights; the offset is the position of the craft in the formation */
    private record Craft(double[] offset, List<BlockDisplay> hull, List<TextDisplay> lights) {
    }

    public SkyPhenomena(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons, ParticleViewers viewers,
                        RegionService regions) {
        this.plugin = plugin;
        this.config = config;
        this.seasons = seasons;
        this.viewers = viewers;
        this.regions = regions;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** stops and removes all craft */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        active.forEach(this::remove);
        active.clear();
    }

    public int activeCount() {
        return active.size();
    }

    /**
     * A fly-over above a place right now (administrator command); {@code formation} = three craft.
     *
     * @param viewer the player the path is computed for (their view distance), or {@code null}
     */
    public boolean spawnAt(Location anchor, Player viewer, boolean formation) {
        MainConfig.Ufo ufo = config.get().main().particles().ufo();
        return spawn(anchor.getWorld(), anchor, viewer, ufo.relativeHeight(), formation);
    }

    /**
     * View distance in blocks: the smaller of the server view distance and the client setting. The client draws nothing farther (fog),
     * so a craft beyond this distance is not visible.
     */
    public static double sight(int serverViewDistance, int clientViewDistance) {
        int chunks = clientViewDistance > 0 ? Math.min(serverViewDistance, clientViewDistance) : serverViewDistance;
        return Math.max(2, chunks) * 16.0;
    }

    /** flight height above the surface: as configured, at most {@link #MAX_HEIGHT_SHARE} of the view distance */
    public static double flightHeight(int configured, double sight) {
        return Math.min(configured, sight * MAX_HEIGHT_SHARE);
    }

    /** horizontal distance of the path start: where the craft at the given height is just at the edge of the view distance */
    public static double flightRadius(double height, double sight) {
        return START_SHARE * Math.sqrt(sight * sight - height * height);
    }

    private void tick() {
        ticks++;
        if (ticks % CHECK_PERIOD == 0) maybeSpawn();
        Iterator<Formation> it = active.iterator();
        while (it.hasNext()) {
            Formation f = it.next();
            f.flight().step();
            if (f.flight().finished() || !move(f)) {
                remove(f);
                it.remove();
            }
        }
    }

    private void maybeSpawn() {
        MainConfig.Ufo ufo = config.get().main().particles().ufo();
        if (!ufo.enabled()) return;
        for (World world : plugin.getServer().getWorlds()) {
            Optional<WorldSeasonState> state = seasons.state(world.getName());
            if (state.isEmpty() || !state.get().enabled()) continue;
            // only above players whose location has a UFO season (also from a region)
            List<Player> players = viewers.players(world).stream()
                    .filter(p -> regions.effectSeasonAt(p.getLocation()).filter(ufo.seasons()::contains).isPresent()).toList();
            if (players.isEmpty()) continue;
            if (random.nextDouble() * 100.0 >= ufo.chance()) continue;
            Player anchor = players.get(random.nextInt(players.size()));
            boolean formation = random.nextDouble() * 100.0 < ufo.formationChance();
            spawn(world, anchor.getLocation(), anchor, ufo.relativeHeight(), formation);
        }
    }

    private boolean spawn(World world, Location anchor, Player viewer, int configuredHeight, boolean formation) {
        if (!world.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) return false;
        // height above the surface, not above a player in a cave; the whole fly-over within the player's view distance
        double base = Math.max(anchor.getY(), world.getHighestBlockYAt(anchor.getBlockX(), anchor.getBlockZ()));
        double sight = sight(world.getViewDistance(), viewer == null ? 0 : viewer.getClientViewDistance());
        double height = flightHeight(configuredHeight, sight);
        UfoFlight flight = UfoFlight.plan(random, anchor.getX(), base, anchor.getZ(), flightRadius(height, sight),
                height, HOVER_CHANCE);
        // the entities stand above the player at flight height; the craft is drawn shifted by the transformation
        Location at = new Location(world, anchor.getX(), base + height, anchor.getZ(), 0f, 0f);

        BlockData black = Material.BLACK_CONCRETE.createBlockData();
        List<Craft> crafts = new ArrayList<>();
        int count = formation ? UfoShape.FORMATION.size() : 1;
        for (int i = 0; i < count; i++) {
            double[] offset = UfoShape.FORMATION.get(i);
            List<BlockDisplay> hull = new ArrayList<>();
            for (UfoShape.PieceTransform piece : hullPieces) {
                hull.add(world.spawn(at, BlockDisplay.class, d -> {
                    configureCommon(d);
                    d.setBlock(black);
                    d.setTransformation(pieceTransformation(piece, at, flight.pose(), offset));
                }));
                viewers.reveal(hull.getLast());
            }
            List<TextDisplay> lights = new ArrayList<>();
            for (UfoShape.Light light : UfoShape.LIGHTS) {
                lights.add(world.spawn(at, TextDisplay.class, d -> {
                    configureLight(d, light);
                    d.setTransformation(lightTransformation(light, at, flight.pose(), offset));
                }));
                viewers.reveal(lights.getLast());
            }
            crafts.add(new Craft(offset, hull, lights));
        }
        active.add(new Formation(world, at, flight, crafts));
        return true;
    }

    /** moves the craft (new transformations); false = the entities vanished or their chunk was unloaded */
    private boolean move(Formation f) {
        if (!f.world().isChunkLoaded(f.anchor().getBlockX() >> 4, f.anchor().getBlockZ() >> 4)) return false;
        UfoFlight.Pose pose = f.flight().pose();
        boolean redOn = UfoShape.redLightOn(f.flight().tick());
        boolean moveNow = f.flight().tick() % MOVE_PERIOD == 0;
        for (Craft c : f.crafts()) {
            if (c.hull().isEmpty() || !c.hull().getFirst().isValid()) return false;
            if (moveNow) {
                for (int i = 0; i < c.hull().size(); i++) {
                    animate(c.hull().get(i), pieceTransformation(hullPieces.get(i), f.anchor(), pose, c.offset()));
                }
            }
            for (int i = 0; i < c.lights().size(); i++) {
                UfoShape.Light light = UfoShape.LIGHTS.get(i);
                TextDisplay d = c.lights().get(i);
                if (moveNow) animate(d, lightTransformation(light, f.anchor(), pose, c.offset()));
                if (light.blinking() && UfoShape.redLightOn(f.flight().tick() - 1) != redOn) {
                    d.text(redOn ? RED_LIGHT : Component.empty());
                }
            }
        }
        return true;
    }

    /** new transformation the client reaches smoothly in {@link #MOVE_PERIOD} ticks */
    private static void animate(Display d, Transformation t) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(MOVE_PERIOD);
        d.setTransformation(t);
    }

    /** offset of a craft point (right, up, forward) relative to the place of the entities, in world coordinates */
    private static Vector3f offsetFrom(Location at, UfoFlight.Pose pose, double right, double up, double forward) {
        double[] p = pose.local(right, up, forward);
        return new Vector3f((float) (p[0] - at.getX()), (float) (p[1] - at.getY()), (float) (p[2] - at.getZ()));
    }

    /** rotation of the craft coordinates (x = −right, z = forward) by the heading */
    private static Quaternionf heading(UfoFlight.Pose pose) {
        return new Quaternionf().rotationY((float) -pose.heading());
    }

    /** hull plate: centre of the craft + rotated corner of the plate, rotated rotation, the same scale */
    private static Transformation pieceTransformation(UfoShape.PieceTransform piece, Location at, UfoFlight.Pose pose,
                                                      double[] offset) {
        Quaternionf yaw = heading(pose);
        Vector3f corner = yaw.transform(new Vector3f((float) piece.translation()[0], (float) piece.translation()[1],
                (float) piece.translation()[2]));
        Vector3f center = offsetFrom(at, pose, offset[0], offset[1], offset[2]);
        return new Transformation(center.add(corner),
                new Quaternionf(yaw).mul(new Quaternionf().rotationY((float) piece.leftAngle())),
                new Vector3f((float) piece.scale()[0], (float) piece.scale()[1], (float) piece.scale()[2]),
                new Quaternionf().rotationY((float) piece.rightAngle()));
    }

    /** light: a dot lying flat face down, centred on the position of the light */
    private static Transformation lightTransformation(UfoShape.Light light, Location at, UfoFlight.Pose pose,
                                                      double[] offset) {
        float s = UfoShape.LIGHT_SCALE;
        Vector3f p = offsetFrom(at, pose, offset[0] + light.right(), offset[1] + light.up(),
                offset[2] + light.forward());
        // the glyph has its origin at the bottom edge; after tilting face down it lies towards +z
        p.add(0f, 0f, -0.1f * s);
        return new Transformation(p, new Quaternionf().rotationX((float) (Math.PI / 2)), new Vector3f(s, s, s),
                new Quaternionf());
    }

    private void remove(Formation f) {
        for (Craft c : f.crafts()) {
            c.hull().forEach(Entity::remove);
            c.lights().forEach(Entity::remove);
        }
    }

    private void configureCommon(Display d) {
        ParticleViewers.hide(d);
        d.setPersistent(false);
        d.addScoreboardTag(TAG);
        d.setViewRange(8f);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
    }

    /** light fully lit even at night; position and rotation are in the transformation */
    private void configureLight(TextDisplay d, UfoShape.Light light) {
        configureCommon(d);
        d.text(light.blinking() ? RED_LIGHT : WHITE_LIGHT);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setDefaultBackground(false);
        d.setShadowed(false);
        d.setSeeThrough(false);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setBillboard(Display.Billboard.FIXED);
        d.setBrightness(new Display.Brightness(15, 15));
    }
}
