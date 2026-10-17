/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeScanTest {

    /** a made-up forest: trunk, cone of needles and surface height */
    private static final class Forest implements TreeScan.Blocks {
        final Map<Long, Character> blocks = new HashMap<>();

        static long key(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0xFFFFF) << 21 | ((long) z & 0x1FFFFF);
        }

        void put(int x, int y, int z, char c) {
            blocks.put(key(x, y, z), c);
        }

        char at(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), y <= 63 ? 'g' : ' ');
        }

        /** a spruce with its trunk at x, z from the ground 64: trunk up to top-1, needles in a cone, a tip at the top */
        void spruce(int x, int z, int top) {
            for (int y = 64; y < top; y++) put(x, y, z, 'l');
            for (int y = 67; y < top; y++) {
                int r = Math.max(1, (top - y + 1) / 2);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= r * r + 1) put(x + dx, y, z + dz, 'L');
                    }
                }
            }
            put(x, top, z, 'L');
        }

        @Override
        public int highest(int x, int z) {
            for (int y = 120; y > 63; y--) if (at(x, y, z) != ' ') return y;
            return 63;
        }

        @Override
        public boolean leaves(int x, int y, int z) {
            return at(x, y, z) == 'L';
        }

        @Override
        public boolean log(int x, int y, int z) {
            return at(x, y, z) == 'l';
        }

        @Override
        public boolean open(int x, int y, int z) {
            return at(x, y, z) == ' ';
        }
    }

    @Test
    void findsSpruceFromAnyColumnOfItsCrown() {
        Forest f = new Forest();
        f.spruce(10, 20, 76);
        Optional<TreeScan.Tree> fromSide = TreeScan.find(f, 12, 21);
        assertTrue(fromSide.isPresent());
        assertEquals(new TreeScan.Tree(10, 20, 76, 67), fromSide.get());
        assertEquals(fromSide, TreeScan.find(f, 10, 20));
        // grass next to a tree is not a tree
        assertTrue(TreeScan.find(f, 30, 30).isEmpty());
    }

    @Test
    void lowBushIsNotATree() {
        Forest f = new Forest();
        f.put(0, 64, 0, 'l');
        f.put(1, 64, 0, 'L');
        f.put(-1, 64, 0, 'L');
        f.put(0, 65, 0, 'L');
        assertTrue(TreeScan.find(f, 0, 0).isEmpty());
    }

    @Test
    void ornamentsHangOutsideTheCrownInASpiral() {
        Forest f = new Forest();
        f.spruce(0, 0, 76);
        TreeScan.Tree tree = TreeScan.find(f, 0, 0).orElseThrow();
        List<TreeScan.Spot> surface = TreeScan.surface(f, tree);
        assertFalse(surface.isEmpty());
        for (TreeScan.Spot s : surface) {
            int bx = (int) Math.floor(s.x());
            int bz = (int) Math.floor(s.z());
            int by = (int) Math.floor(s.y());
            assertTrue(f.open(bx, by, bz), "decoration in the needles: " + s);
            assertTrue(s.y() > tree.bottom() && s.y() < tree.top(), s.toString());
        }
        List<TreeScan.Spot> balls = TreeScan.spiral(surface, 0, 10);
        assertEquals(10, balls.size());
        // spread over the whole height, not just at the top
        double minY = balls.stream().mapToDouble(TreeScan.Spot::y).min().orElseThrow();
        double maxY = balls.stream().mapToDouble(TreeScan.Spot::y).max().orElseThrow();
        assertTrue(maxY - minY >= 5, "span " + (maxY - minY));
        assertTrue(TreeScan.spiral(surface, 0, 1000).size() < surface.size());
    }
}
