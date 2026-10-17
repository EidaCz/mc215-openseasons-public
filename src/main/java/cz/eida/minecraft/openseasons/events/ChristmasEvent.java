/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Christmas: sparks in the sky above players at night, decorated spruces (balls,
 * lights and a star made of displays, the world does not change) and presents that appear near villagers and around
 * players. Sparks and decorations are season particles ({@code /toggleseasonparticles} turns them off),
 * presents are seen by everyone.
 */
final class ChristmasEvent extends BuiltinEvent {

    /** night window of the sparks by the time of day */
    static final long NIGHT_FROM = 13670;
    static final long NIGHT_TO = 22812;
    /** presents: an attempt at every player every 10 s */
    static final int PRESENT_PERIOD = 200;
    static final int MAX_PRESENTS_PER_WORLD = 64;
    /** trees: a check every 5 s, at most this many decorated trees around a player and in the world */
    static final int TREE_PERIOD = 100;
    static final int TREES_NEAR_PLAYER = 6;
    static final int TREES_PER_WORLD = 48;
    static final double TREE_RADIUS = 44;
    static final double TREE_KEEP = 96;
    static final int ORNAMENTS = 10;
    static final int LIGHTS = 12;

    private static final Material[] BALLS = {Material.RED_CONCRETE, Material.GOLD_BLOCK, Material.BLUE_CONCRETE,
            Material.WHITE_CONCRETE, Material.PURPLE_CONCRETE, Material.RED_CONCRETE};
    private static final int[] LIGHT_COLORS = {0xFFD54A, 0xFF4436, 0x4CD964, 0x5AC8FA, 0xFFF4E0};
    private static final int DIM = 0x5A4A2A;

    private final Pickups pickups;
    /** places (chunk x, z) where a present appeared in this run of the event; world → list */
    private final Map<String, List<int[]>> presentSpots = new HashMap<>();
    /** decorated trees: world → tree key → entities */
    private final Map<String, Map<Long, Decoration>> trees = new HashMap<>();

    private record Decoration(Location at, List<Display> parts, List<TextDisplay> lights, int[] colors) {
        boolean valid() {
            return !parts.isEmpty() && parts.getFirst().isValid();
        }

        void remove() {
            parts.forEach(Entity::remove);
        }
    }

    ChristmasEvent(Plugin plugin, Supplier<EventSettings> settings, ParticleViewers viewers, EventPlaces places,
                   Pickups pickups) {
        super(plugin, settings, viewers, places);
        this.pickups = pickups;
    }

    @Override
    SeasonEvent.Type type() {
        return SeasonEvent.Type.CHRISTMAS;
    }

    @Override
    protected void stopped(World world) {
        pickups.removeAll(world, Pickups.Kind.PRESENT);
        presentSpots.remove(world.getName());
        Map<Long, Decoration> decorated = trees.remove(world.getName());
        if (decorated != null) decorated.values().forEach(Decoration::remove);
    }

    @Override
    void tick(long tick) {
        EventSettings.Christmas c = settings.get().christmas();
        for (String name : worlds()) {
            World world = plugin.getServer().getWorld(name);
            if (world == null) continue;
            if (c.nightParticles() && tick % 2 == 0) nightSparkles(world);
            if (c.presents() && tick % PRESENT_PERIOD == 0) spawnPresents(world, c);
            if (c.trees() && tick % TREE_PERIOD == 0) decorateTrees(world);
            if (c.trees() && tick % 20 == 10) twinkle(world);
        }
    }

    // ---------------------------------------------------------------- sparks in the sky

    static boolean sparkleNight(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t > NIGHT_FROM && t < NIGHT_TO;
    }

    private void nightSparkles(World world) {
        if (!sparkleNight(world.getTime())) return;
        for (Player p : viewers.players(world)) {
            if (effectsAt(p.getLocation())) sparkles(world, p.getLocation());
        }
    }

    /** sparks high above a place: white, blue, green and red */
    private void sparkles(World world, Location at) {
        for (int i = 0; i < 3; i++) {
            double x = at.getX() + (random.nextDouble() - 0.5) * 28;
            double z = at.getZ() + (random.nextDouble() - 0.5) * 28;
            double y = at.getY() + 18 + random.nextDouble() * 10;
            Particle particle = switch (i) {
                case 0 -> Particle.FIREWORK;
                case 1 -> Particle.SCRAPE;
                default -> Particle.HAPPY_VILLAGER;
            };
            viewers.spawn(world, particle, x, y, z, 2, 1.5, 1.0, 1.5, 0.0, null, false);
        }
        double x = at.getX() + (random.nextDouble() - 0.5) * 24;
        double z = at.getZ() + (random.nextDouble() - 0.5) * 24;
        Color color = random.nextBoolean() ? Color.fromRGB(0xE0281E) : Color.fromRGB(0x2FA84F);
        viewers.spawn(world, Particle.DUST, x, at.getY() + 20 + random.nextDouble() * 6, z, 2, 1.5, 1.0, 1.5, 0.0,
                new Particle.DustOptions(color, 1.2f), false);
    }

    /** manual triggering: sparks above a place (a moving player) for {@code ticks} */
    void debugSparkles(Supplier<Location> where, int ticks) {
        int[] left = {ticks};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            Location at = where.get();
            if (at == null || left[0]-- <= 0) {
                task.cancel();
                return;
            }
            if (left[0] % 2 == 0) sparkles(at.getWorld(), at);
        }, 1L, 1L);
    }

    // ---------------------------------------------------------------- presents

    private void spawnPresents(World world, EventSettings.Christmas c) {
        if (pickups.live(world, Pickups.Kind.PRESENT).size() >= MAX_PRESENTS_PER_WORLD) return;
        for (Player p : world.getPlayers()) {
            Optional<Location> spot = presentSpot(world, p.getLocation());
            if (spot.isEmpty()) continue;
            Location at = spot.get();
            if (!effectsAt(at) || !farFromEarlier(world, at, c.presentSpacing())) continue;
            presentSpots.computeIfAbsent(world.getName(), w -> new ArrayList<>())
                    .add(new int[]{at.getBlockX() >> 4, at.getBlockZ() >> 4});
            pickups.spawn(Pickups.Kind.PRESENT, at);
        }
    }

    /** near a random villager within 48 blocks, otherwise 8–32 blocks from the player */
    private Optional<Location> presentSpot(World world, Location near) {
        List<Villager> villagers = new ArrayList<>(world.getNearbyEntitiesByType(Villager.class, near, 48));
        if (!villagers.isEmpty()) {
            Location v = villagers.get(random.nextInt(villagers.size())).getLocation();
            double[] xz = EventEntities.around(random, v.getX(), v.getZ(), 1.5, 5);
            Optional<Location> g = EventEntities.ground(world, xz[0], v.getY(), xz[1]);
            if (g.isPresent()) return g;
        }
        double[] xz = EventEntities.around(random, near.getX(), near.getZ(), 8, 32);
        return EventEntities.ground(world, xz[0], near.getY(), xz[1]);
    }

    /** chunk-distance-between-spawns: a place far enough from all earlier presents of this run */
    private boolean farFromEarlier(World world, Location at, int spacing) {
        if (spacing <= 0) return true;
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        for (int[] s : presentSpots.getOrDefault(world.getName(), List.of())) {
            if (EventEntities.chunkDistance(cx, cz, s[0], s[1]) < spacing) return false;
        }
        return true;
    }

    /** manual triggering: a present a short way ahead of the place, regardless of spacing */
    Optional<Location> debugPresent(Location near) {
        Optional<Location> g = EventEntities.groundAhead(near, 2.5);
        g.ifPresent(at -> pickups.spawn(Pickups.Kind.PRESENT, at));
        return g;
    }

    // ---------------------------------------------------------------- decorated spruces

    private void decorateTrees(World world) {
        Map<Long, Decoration> decorated = trees.computeIfAbsent(world.getName(), w -> new HashMap<>());
        List<Player> players = viewers.players(world);
        // cleanup: invalid ones (unloaded chunk) and far from all players
        Iterator<Map.Entry<Long, Decoration>> it = decorated.entrySet().iterator();
        while (it.hasNext()) {
            Decoration d = it.next().getValue();
            if (!d.valid() || players.stream().noneMatch(p -> p.getLocation().distanceSquared(d.at()) < TREE_KEEP * TREE_KEEP)) {
                d.remove();
                it.remove();
            }
        }
        TreeScan.Blocks blocks = blocks(world);
        for (Player p : players) {
            if (decorated.size() >= TREES_PER_WORLD) return;
            Location at = p.getLocation();
            long near = decorated.values().stream()
                    .filter(d -> d.at().distanceSquared(at) < TREE_RADIUS * TREE_RADIUS).count();
            for (int attempt = 0; attempt < 10 && near < TREES_NEAR_PLAYER; attempt++) {
                double[] xz = EventEntities.around(random, at.getX(), at.getZ(), 6, TREE_RADIUS);
                int x = (int) Math.floor(xz[0]);
                int z = (int) Math.floor(xz[1]);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Optional<TreeScan.Tree> tree = TreeScan.find(blocks, x, z);
                if (tree.isEmpty() || decorated.containsKey(tree.get().key())) continue;
                if (!loaded(world, tree.get())) continue;
                if (!effectsAt(new Location(world, tree.get().x(), tree.get().top(), tree.get().z()))) continue;
                decorated.put(tree.get().key(), decorate(world, blocks, tree.get()));
                near++;
            }
        }
    }

    /** manual triggering: decorates the nearest spruce within 24 blocks */
    Optional<Location> debugTree(Location at) {
        World world = at.getWorld();
        TreeScan.Blocks blocks = blocks(world);
        for (int r = 0; r <= 24; r += 2) {
            for (int i = 0; i < Math.max(1, r * 3); i++) {
                double angle = Math.PI * 2 * i / Math.max(1, r * 3);
                int x = (int) Math.floor(at.getX() + Math.cos(angle) * r);
                int z = (int) Math.floor(at.getZ() + Math.sin(angle) * r);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Optional<TreeScan.Tree> tree = TreeScan.find(blocks, x, z);
                if (tree.isEmpty() || !loaded(world, tree.get())) continue;
                Map<Long, Decoration> decorated = trees.computeIfAbsent(world.getName(), w -> new HashMap<>());
                Decoration old = decorated.remove(tree.get().key());
                if (old != null) old.remove();
                Decoration d = decorate(world, blocks, tree.get());
                decorated.put(tree.get().key(), d);
                return Optional.of(d.at());
            }
        }
        return Optional.empty();
    }

    private static boolean loaded(World world, TreeScan.Tree t) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded((t.x() + dx * TreeScan.MAX_REACH) >> 4, (t.z() + dz * TreeScan.MAX_REACH) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Decoration decorate(World world, TreeScan.Blocks blocks, TreeScan.Tree tree) {
        List<TreeScan.Spot> surface = TreeScan.surface(blocks, tree);
        List<Display> parts = new ArrayList<>();
        List<TextDisplay> lights = new ArrayList<>();
        int shift = random.nextInt(TreeScan.DIRECTIONS.length);
        for (TreeScan.Spot s : TreeScan.spiral(surface, shift, ORNAMENTS)) {
            Material ball = BALLS[random.nextInt(BALLS.length)];
            parts.add(shown(world.spawn(new Location(world, s.x(), s.y(), s.z()), BlockDisplay.class, d -> {
                decoration(d);
                d.setBlock(ball.createBlockData());
                d.setBrightness(new Display.Brightness(12, 15));
                d.setTransformation(new Transformation(new Vector3f(-0.13f, -0.13f, -0.13f), new Quaternionf(),
                        new Vector3f(0.26f, 0.26f, 0.26f), new Quaternionf()));
            })));
        }
        List<TreeScan.Spot> lightSpots = TreeScan.spiral(surface, shift + 2, LIGHTS);
        int[] colors = new int[lightSpots.size()];
        for (int i = 0; i < lightSpots.size(); i++) {
            TreeScan.Spot s = lightSpots.get(i);
            int color = LIGHT_COLORS[random.nextInt(LIGHT_COLORS.length)];
            colors[i] = color;
            TextDisplay light = shown(world.spawn(new Location(world, s.x(), s.y() + 0.25, s.z()), TextDisplay.class, d -> {
                decoration(d);
                d.text(Component.text("●", TextColor.color(color)));
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setDefaultBackground(false);
                d.setShadowed(false);
                d.setSeeThrough(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(0f, -0.1f, 0f), new Quaternionf(),
                        new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
            }));
            lights.add(light);
            parts.add(light);
        }
        Location top = new Location(world, tree.x() + 0.5, tree.top() + 1.0, tree.z() + 0.5);
        parts.add(shown(world.spawn(top, ItemDisplay.class, d -> {
            decoration(d);
            d.setItemStack(new ItemStack(Material.NETHER_STAR));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(0f, 0.15f, 0f), new Quaternionf(),
                    new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
        })));
        return new Decoration(top, List.copyOf(parts), List.copyOf(lights), colors);
    }

    /** decoration: a temporary display, hidden for now; {@link #shown} shows it */
    private void decoration(Display d) {
        EventEntities.display(d, type(), 1.5f);
        ParticleViewers.hide(d);
    }

    /** shows a decoration to players with particles turned on */
    private <T extends Display> T shown(T d) {
        viewers.reveal(d);
        return d;
    }

    /** the lights sometimes go out and light up again */
    private void twinkle(World world) {
        Map<Long, Decoration> decorated = trees.get(world.getName());
        if (decorated == null) return;
        for (Decoration d : decorated.values()) {
            for (int i = 0; i < d.lights().size(); i++) {
                if (random.nextInt(4) != 0) continue;
                TextDisplay light = d.lights().get(i);
                if (!light.isValid()) continue;
                boolean dim = random.nextInt(3) == 0;
                light.text(Component.text("●", TextColor.color(dim ? DIM : d.colors()[i])));
            }
        }
    }

    int decoratedTrees(World world) {
        return trees.getOrDefault(world.getName(), Map.of()).size();
    }

    /** reading the world for {@link TreeScan} */
    static TreeScan.Blocks blocks(World world) {
        return new TreeScan.Blocks() {
            @Override
            public int highest(int x, int z) {
                return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            }

            @Override
            public boolean leaves(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType() == Material.SPRUCE_LEAVES;
            }

            @Override
            public boolean log(int x, int y, int z) {
                Material m = world.getBlockAt(x, y, z).getType();
                return m == Material.SPRUCE_LOG || m == Material.SPRUCE_WOOD;
            }

            @Override
            public boolean open(int x, int y, int z) {
                Block b = world.getBlockAt(x, y, z);
                return b.isPassable() && !b.isLiquid();
            }
        };
    }
}
