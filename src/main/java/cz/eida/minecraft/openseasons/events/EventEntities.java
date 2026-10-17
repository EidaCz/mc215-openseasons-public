/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Shared for event entities: tags, display settings and finding a place on the ground. All event
 * entities are temporary (not saved with the world) and carry the tag {@link #TAG} and the event tag,
 * so after the end of the event and after a restart they can be found and cleaned up (manually {@code /kill @e[tag=mc215os_event]}).
 */
public final class EventEntities {

    /** shared tag of all event entities */
    public static final String TAG = "mc215os_event";
    /** event tag: {@code mc215os_event_christmas} … */
    public static final String TAG_PREFIX = "mc215os_event_";

    private EventEntities() {
    }

    public static String tag(SeasonEvent.Type type) {
        return TAG_PREFIX + type.id();
    }

    /** temporary event entity with the tags */
    public static void mark(Entity e, SeasonEvent.Type type) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(tag(type));
    }

    /** display: no shadow, with view range and fixed rotation */
    public static void display(Display d, SeasonEvent.Type type, float viewRange) {
        mark(d, type);
        d.setViewRange(viewRange);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
    }

    /** removes the event entities in the world (loaded chunks) */
    public static int removeTagged(World world, SeasonEvent.Type type) {
        String tag = tag(type);
        int n = 0;
        for (Entity e : world.getEntities()) {
            if (e.getScoreboardTags().contains(tag) && !(e instanceof Player)) {
                e.remove();
                n++;
            }
        }
        return n;
    }

    /** event the entity belongs to; empty for foreign entities */
    public static Optional<SeasonEvent.Type> typeOf(Entity e) {
        if (!e.getScoreboardTags().contains(TAG)) return Optional.empty();
        for (SeasonEvent.Type t : SeasonEvent.Type.values()) {
            if (t.id() != null && e.getScoreboardTags().contains(tag(t))) return Optional.of(t);
        }
        return Optional.empty();
    }

    /** point at a horizontal distance of {@code min}–{@code max} from the centre, in a random direction */
    public static double[] around(RandomGenerator random, double x, double z, double min, double max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = min + random.nextDouble() * (max - min);
        return new double[]{x + Math.cos(angle) * distance, z + Math.sin(angle) * distance};
    }

    /**
     * Place on the ground near the height {@code yHint}: a solid block (not a liquid, not leaves) and two free
     * passable blocks above it. Searches from {@code yHint + 4} down to {@code yHint - 8}, then the surface. The chunk must
     * be loaded; otherwise empty.
     */
    public static Optional<Location> ground(World world, double x, double yHint, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return Optional.empty();
        int top = Math.min(world.getMaxHeight() - 3, (int) Math.floor(yHint) + 4);
        int bottom = Math.max(world.getMinHeight(), (int) Math.floor(yHint) - 8);
        for (int y = top; y >= bottom; y--) {
            if (standable(world, bx, y, bz)) return Optional.of(new Location(world, bx + 0.5, y + 1, bz + 0.5));
        }
        return surface(world, x, z);
    }

    /** surface: the highest solid block without leaves, free space above it */
    public static Optional<Location> surface(World world, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return Optional.empty();
        int y = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight() || !standable(world, bx, y, bz)) return Optional.empty();
        return Optional.of(new Location(world, bx + 0.5, y + 1, bz + 0.5));
    }

    private static boolean standable(World world, int x, int y, int z) {
        Block floor = world.getBlockAt(x, y, z);
        Material m = floor.getType();
        if (!m.isSolid() || floor.isLiquid() || m.name().endsWith("_LEAVES") || m == Material.BARRIER) return false;
        return clear(world.getBlockAt(x, y + 1, z)) && clear(world.getBlockAt(x, y + 2, z));
    }

    /** air or a passable plant, not a liquid */
    private static boolean clear(Block b) {
        return b.isPassable() && !b.isLiquid() && b.getType() != Material.LAVA && b.getType() != Material.WATER;
    }

    /** place on the ground {@code distance} blocks ahead of the view (by the rotation of the place) */
    public static Optional<Location> groundAhead(Location at, double distance) {
        double yaw = Math.toRadians(at.getYaw());
        return ground(at.getWorld(), at.getX() - Math.sin(yaw) * distance, at.getY(), at.getZ() + Math.cos(yaw) * distance);
    }

    /** chunk distance of two places (Euclidean, in chunks) */
    public static double chunkDistance(int cx1, int cz1, int cx2, int cz2) {
        double dx = cx1 - cx2;
        double dz = cz1 - cz2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
