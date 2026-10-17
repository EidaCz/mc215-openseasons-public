/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * New Year: at night firework boxes appear around players
 * (displays, the world does not change), launch rockets for a while and vanish again. Most right after midnight, when
 * the event begins. The boxes keep the spacing {@code min-distance-between-firework}. The rockets are
 * real fireworks, but they hurt nobody; a player with particles turned off ({@code /toggleseasonparticles})
 * sees neither boxes nor rockets and none appear around them.
 */
final class NewYearEvent extends BuiltinEvent {

    /** night by the time of day */
    static final long NIGHT_FROM = 13000;
    static final long NIGHT_TO = 23000;
    /** the first hours after midnight (18,000 = 0:00) celebrate the most */
    static final long MIDNIGHT_FROM = 18000;
    static final long MIDNIGHT_TO = 20500;
    static final int CHECK_PERIOD = 40;
    /** a box launches for 45 s */
    static final int BOX_LIFE = 900;
    static final int BOXES_NEAR = 2;
    static final int BOXES_NEAR_MIDNIGHT = 4;
    static final double NEAR = 64;

    private static final Color[] COLORS = {Color.RED, Color.fromRGB(0xFFD700), Color.fromRGB(0x3FA9F5), Color.LIME,
            Color.fromRGB(0xFF66CC), Color.WHITE, Color.ORANGE, Color.fromRGB(0x9B59FF)};
    private static final FireworkEffect.Type[] SHAPES = {FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.STAR, FireworkEffect.Type.BURST, FireworkEffect.Type.BALL_LARGE};

    private final List<Box> boxes = new ArrayList<>();

    private static final class Box {
        final Location at;
        final List<Display> parts;
        int life;
        int nextLaunch;

        Box(Location at, List<Display> parts, int life) {
            this.at = at;
            this.parts = parts;
            this.life = life;
        }

        boolean valid() {
            return parts.getFirst().isValid();
        }

        void remove() {
            parts.forEach(Entity::remove);
        }
    }

    NewYearEvent(Plugin plugin, Supplier<EventSettings> settings, ParticleViewers viewers, EventPlaces places) {
        super(plugin, settings, viewers, places);
    }

    @Override
    SeasonEvent.Type type() {
        return SeasonEvent.Type.NEWYEAR;
    }

    @Override
    protected void stopped(World world) {
        Iterator<Box> it = boxes.iterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (b.at.getWorld().equals(world)) {
                b.remove();
                it.remove();
            }
        }
    }

    static boolean night(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= NIGHT_FROM && t < NIGHT_TO;
    }

    static boolean midnight(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= MIDNIGHT_FROM && t < MIDNIGHT_TO;
    }

    @Override
    void tick(long tick) {
        Iterator<Box> it = boxes.iterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (!b.valid() || --b.life <= 0) {
                if (b.valid()) puff(b.at);
                b.remove();
                it.remove();
                continue;
            }
            if (--b.nextLaunch <= 0) {
                launch(b.at);
                boolean party = midnight(b.at.getWorld().getTime());
                b.nextLaunch = party ? 6 + random.nextInt(20) : 12 + random.nextInt(34);
            }
        }
        if (tick % CHECK_PERIOD != 0 || !settings.get().newYear().fireworkBoxes()) return;
        for (String name : worlds()) {
            World world = plugin.getServer().getWorld(name);
            if (world == null || !night(world.getTime())) continue;
            int limit = midnight(world.getTime()) ? BOXES_NEAR_MIDNIGHT : BOXES_NEAR;
            for (Player p : viewers.players(world)) {
                long near = boxes.stream().filter(b -> b.at.getWorld().equals(world)
                        && b.at.distanceSquared(p.getLocation()) < NEAR * NEAR).count();
                if (near >= limit) continue;
                boxSpot(world, p.getLocation()).ifPresent(at -> spawnBox(at, BOX_LIFE));
            }
        }
    }

    /** near a villager within 48 blocks, otherwise 12–36 blocks from the player; far enough from other boxes */
    private Optional<Location> boxSpot(World world, Location near) {
        int minDistance = settings.get().newYear().minDistance();
        for (int attempt = 0; attempt < 4; attempt++) {
            Optional<Location> spot;
            List<Villager> villagers = attempt < 2
                    ? new ArrayList<>(world.getNearbyEntitiesByType(Villager.class, near, 48)) : List.of();
            if (!villagers.isEmpty()) {
                Location v = villagers.get(random.nextInt(villagers.size())).getLocation();
                double[] xz = EventEntities.around(random, v.getX(), v.getZ(), 3, 9);
                spot = EventEntities.ground(world, xz[0], v.getY(), xz[1]);
            } else {
                double[] xz = EventEntities.around(random, near.getX(), near.getZ(), 12, 36);
                spot = EventEntities.ground(world, xz[0], near.getY(), xz[1]);
            }
            if (spot.isEmpty() || !effectsAt(spot.get())) continue;
            Location at = spot.get();
            boolean free = boxes.stream().noneMatch(b -> b.at.getWorld().equals(world)
                    && b.at.distanceSquared(at) < (double) minDistance * minDistance);
            if (free) return spot;
        }
        return Optional.empty();
    }

    /** manual triggering: a box ahead of the place, launches right away (also during the day), 30 s */
    Optional<Location> debugBox(Location near) {
        Optional<Location> g = EventEntities.groundAhead(near, 5);
        g.ifPresent(at -> spawnBox(at, 600));
        return g;
    }

    private void spawnBox(Location at, int life) {
        World world = at.getWorld();
        Location base = at.clone();
        base.setYaw(random.nextFloat() * 360f);
        List<Display> parts = new ArrayList<>();
        parts.add(part(world, base, Material.RED_CONCRETE, -0.3f, 0f, -0.3f, 0.6f, 0.42f, 0.6f));
        parts.add(part(world, base, Material.YELLOW_CONCRETE, -0.31f, 0.18f, -0.31f, 0.62f, 0.06f, 0.62f));
        Material[] tubes = {Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.WHITE_CONCRETE};
        float[][] holes = {{-0.18f, -0.18f}, {0.06f, -0.18f}, {-0.18f, 0.06f}, {0.06f, 0.06f}};
        for (int i = 0; i < holes.length; i++) {
            parts.add(part(world, base, tubes[i], holes[i][0], 0.42f, holes[i][1], 0.12f, 0.12f, 0.12f));
        }
        Box box = new Box(at.clone(), List.copyOf(parts), life);
        box.nextLaunch = 10 + random.nextInt(20);
        boxes.add(box);
    }

    private BlockDisplay part(World world, Location at, Material block, float x, float y, float z,
                              float sx, float sy, float sz) {
        BlockDisplay d = world.spawn(at, BlockDisplay.class, e -> {
            EventEntities.display(e, type(), 2f);
            ParticleViewers.hide(e);
            e.setBlock(block.createBlockData());
            e.setBrightness(new Display.Brightness(12, 15));
            e.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz),
                    new Quaternionf()));
        });
        viewers.reveal(d);
        return d;
    }

    /** rocket from a box: random colours and shape, slightly tilted */
    private void launch(Location at) {
        World world = at.getWorld();
        Location from = at.clone().add(random.nextDouble() * 0.3 - 0.15, 0.6, random.nextDouble() * 0.3 - 0.15);
        Firework fw = world.spawn(from, Firework.class, f -> {
            EventEntities.mark(f, type());
            ParticleViewers.hide(f);
            FireworkMeta meta = f.getFireworkMeta();
            meta.clearEffects();
            int effects = 1 + (random.nextInt(4) == 0 ? 1 : 0);
            for (int i = 0; i < effects; i++) {
                FireworkEffect.Builder b = FireworkEffect.builder()
                        .with(SHAPES[random.nextInt(SHAPES.length)])
                        .withColor(COLORS[random.nextInt(COLORS.length)], COLORS[random.nextInt(COLORS.length)])
                        .flicker(random.nextBoolean())
                        .trail(random.nextInt(3) != 0);
                if (random.nextBoolean()) b.withFade(COLORS[random.nextInt(COLORS.length)]);
                meta.addEffect(b.build());
            }
            meta.setPower(1 + random.nextInt(2));
            f.setFireworkMeta(meta);
            f.setVelocity(new Vector(random.nextDouble() * 0.08 - 0.04, 0.05, random.nextDouble() * 0.08 - 0.04));
        });
        viewers.reveal(fw);
        for (Player p : viewers.players(world)) {
            if (p.getLocation().distanceSquared(at) < 48 * 48) {
                p.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.9f + random.nextFloat() * 0.3f);
            }
        }
        viewers.spawn(world, Particle.SMOKE, from.getX(), from.getY(), from.getZ(), 3, 0.05, 0.05, 0.05, 0.01, null, false);
    }

    private void puff(Location at) {
        viewers.spawn(at.getWorld(), Particle.CLOUD, at.getX(), at.getY() + 0.3, at.getZ(), 6, 0.2, 0.15, 0.2, 0.01,
                null, false);
    }

    int boxCount(World world) {
        return (int) boxes.stream().filter(b -> b.at.getWorld().equals(world)).count();
    }

    /** event rockets hurt nobody */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && fw.getScoreboardTags().contains(EventEntities.TAG)) {
            event.setCancelled(true);
        }
    }
}
