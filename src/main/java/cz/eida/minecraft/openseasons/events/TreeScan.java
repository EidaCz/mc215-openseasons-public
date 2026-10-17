/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finding spruces for Christmas decorations and places for ornaments on their surface. Without Bukkit, over the interface
 * {@link Blocks}, so it can be tested on an imaginary forest. The world is only read.
 */
final class TreeScan {

    /** what is in a block; read only */
    interface Blocks {
        /** height of the highest block that stops movement (leaves too) */
        int highest(int x, int z);

        boolean leaves(int x, int y, int z);

        boolean log(int x, int y, int z);

        /** air or passable (snow, grass): an ornament may hang there */
        boolean open(int x, int y, int z);
    }

    /** spruce: trunk column, top of the crown and the lowest level of needles */
    record Tree(int x, int z, int top, int bottom) {
        long key() {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }

    /** place of an ornament in world coordinates and the level from the top (0 = top) */
    record Spot(double x, double y, double z, int level, int direction) {
    }

    /** eight directions around the trunk */
    static final int[][] DIRECTIONS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    static final int MAX_REACH = 6;
    static final int MIN_HEIGHT = 4;

    private TreeScan() {
    }

    /**
     * A spruce whose crown is at the top of column {@code x, z}: climbs the needles to the highest place of the
     * surroundings, finds the trunk below the top and measures the crown. Empty when there is no spruce or it is too small.
     */
    static Optional<Tree> find(Blocks b, int x, int z) {
        int y = b.highest(x, z);
        if (!b.leaves(x, y, z)) {
            if (!b.leaves(x, y - 1, z)) return Optional.empty();
            y--;
        }
        int cx = x;
        int cz = z;
        int cy = y;
        for (int step = 0; step < 6; step++) {
            int bx = cx;
            int bz = cz;
            int by = cy;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int h = topLeaves(b, cx + dx, cz + dz);
                    if (h > by) {
                        by = h;
                        bx = cx + dx;
                        bz = cz + dz;
                    }
                }
            }
            if (by == cy) break;
            cx = bx;
            cz = bz;
            cy = by;
        }
        // trunk below the top (with big spruces it may be one block aside)
        int trunkX = Integer.MIN_VALUE;
        int trunkZ = 0;
        int trunkY = 0;
        search:
        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (b.log(cx + dx, cy - dy, cz + dz)) {
                        trunkX = cx + dx;
                        trunkZ = cz + dz;
                        trunkY = cy - dy;
                        break search;
                    }
                }
            }
        }
        if (trunkX == Integer.MIN_VALUE) return Optional.empty();
        int bottom = cy;
        int gap = 0;
        for (int yy = trunkY; yy > trunkY - 40 && b.log(trunkX, yy, trunkZ); yy--) {
            if (leafyLevel(b, trunkX, yy, trunkZ)) {
                bottom = yy;
                gap = 0;
            } else if (++gap >= 3) {
                break;
            }
        }
        if (cy - bottom < MIN_HEIGHT) return Optional.empty();
        return Optional.of(new Tree(trunkX, trunkZ, cy, bottom));
    }

    private static int topLeaves(Blocks b, int x, int z) {
        int h = b.highest(x, z);
        if (b.leaves(x, h, z)) return h;
        if (b.leaves(x, h - 1, z)) return h - 1;
        return Integer.MIN_VALUE;
    }

    private static boolean leafyLevel(Blocks b, int x, int y, int z) {
        return b.leaves(x + 1, y, z) || b.leaves(x - 1, y, z) || b.leaves(x, y, z + 1) || b.leaves(x, y, z - 1);
    }

    /**
     * Places on the surface of the crown: on each level and direction the outer needle block with free space in front of it;
     * the ornament hangs right in front of its face.
     */
    static List<Spot> surface(Blocks b, Tree t) {
        List<Spot> out = new ArrayList<>();
        for (int y = t.top() - 1; y >= t.bottom(); y--) {
            int level = t.top() - y;
            for (int d = 0; d < DIRECTIONS.length; d++) {
                int dx = DIRECTIONS[d][0];
                int dz = DIRECTIONS[d][1];
                int reach = 0;
                for (int k = 1; k <= MAX_REACH; k++) {
                    if (b.leaves(t.x() + dx * k, y, t.z() + dz * k)) reach = k;
                    else break;
                }
                if (reach == 0 || !b.open(t.x() + dx * (reach + 1), y, t.z() + dz * (reach + 1))) continue;
                double ox = t.x() + dx * reach + 0.5 + dx * 0.56;
                double oz = t.z() + dz * reach + 0.5 + dz * 0.56;
                out.add(new Spot(ox, y + 0.35, oz, level, d));
            }
        }
        return out;
    }

    /**
     * Ornaments in a spiral: on each level two opposite directions, rotated by three eighths per level. When
     * there are more places than {@code max}, they are taken evenly over the whole height.
     *
     * @param offset offset of the spiral (different for lights than for balls)
     */
    static List<Spot> spiral(List<Spot> surface, int offset, int max) {
        List<Spot> all = new ArrayList<>();
        for (Spot s : surface) {
            int want = Math.floorMod(s.level() * 3 + offset, DIRECTIONS.length);
            if (s.direction() == want || s.direction() == (want + 4) % DIRECTIONS.length) all.add(s);
        }
        if (all.size() <= max) return all;
        List<Spot> out = new ArrayList<>();
        for (int i = 0; i < max; i++) out.add(all.get((int) ((long) i * all.size() / max)));
        return out;
    }
}
