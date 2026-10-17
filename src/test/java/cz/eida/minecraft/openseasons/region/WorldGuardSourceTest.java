/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.PriorityRTreeIndex;
import com.sk89q.worldguard.protection.managers.storage.MemoryRegionDatabase;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Evaluation of flags by the real WorldGuard (without a server): priority, parents, chunk column. */
class WorldGuardSourceTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;

    private final SimpleFlagRegistry registry = new SimpleFlagRegistry();
    private final StringFlag greeting = new StringFlag("greeting");
    private final SeasonFlags flags;
    private final RegionManager regions;

    WorldGuardSourceTest() {
        registry.register(greeting);
        flags = SeasonFlags.register(registry, Logger.getAnonymousLogger());
        regions = new RegionManager(new MemoryRegionDatabase(), new PriorityRTreeIndex.Factory(), registry);
    }

    private RegionSettings chunk(int cx, int cz) {
        return WorldGuardSource.query(regions, flags, cx, cz, MIN_Y, MAX_Y);
    }

    private ProtectedCuboidRegion cuboid(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        ProtectedCuboidRegion r = new ProtectedCuboidRegion(id, BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
        regions.addRegion(r);
        return r;
    }

    @Test
    void noRegionMeansGlobalBehaviour() {
        assertEquals(RegionSettings.NONE, chunk(0, 0));
    }

    @Test
    void permanentSeasonAndDenyFlagsApplyToTheWholeChunkColumn() {
        // a large base region
        ProtectedCuboidRegion base = cuboid("base", 11490, -64, -11102, 11589, 319, -11003);
        base.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.SUMMER);
        base.setFlag(flags.effects, StateFlag.State.DENY);
        base.setFlag(flags.blockChanges, StateFlag.State.DENY);

        RegionSettings s = chunk(11490 >> 4, -11102 >> 4);
        assertEquals(Optional.of(Season.SUMMER), s.permanentSeason());
        assertFalse(s.effects());
        assertFalse(s.blockChanges());
        assertTrue(s.mobSpawning());
        // a chunk outside the region
        assertEquals(RegionSettings.NONE, chunk(11600 >> 4, -11102 >> 4));
    }

    @Test
    void regionHighUpStillCoversTheChunkColumn() {
        // only y 52..115 and only part of the chunk
        ProtectedCuboidRegion village = cuboid("village", -1369, 52, -637, -1279, 115, -547);
        village.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.FALL);

        assertEquals(Optional.of(Season.FALL), chunk(-1369 >> 4, -637 >> 4).permanentSeason());
        assertEquals(Optional.of(Season.FALL), chunk(-1279 >> 4, -547 >> 4).permanentSeason());
        assertEquals(RegionSettings.NONE, chunk((-1279 >> 4) + 1, -547 >> 4));
    }

    @Test
    void polygonRegionCountsWhereItIntersectsTheChunk() {
        ProtectedPolygonalRegion spawn = new ProtectedPolygonalRegion("spawn",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(40, 0), BlockVector2.at(0, 40)), MIN_Y, MAX_Y);
        spawn.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.SPRING);
        regions.addRegion(spawn);

        assertEquals(Optional.of(Season.SPRING), chunk(0, 0).permanentSeason());
        assertEquals(Optional.of(Season.SPRING), chunk(1, 1).permanentSeason());
        // chunk 2,2 (32..47) is already beyond the hypotenuse x + z = 40
        assertEquals(RegionSettings.NONE, chunk(2, 2));
    }

    @Test
    void higherPriorityWins() {
        ProtectedCuboidRegion outer = cuboid("outer", 0, MIN_Y, 0, 63, MAX_Y, 63);
        outer.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.WINTER);
        outer.setFlag(flags.blockChanges, StateFlag.State.DENY);
        ProtectedCuboidRegion inner = cuboid("inner", 16, MIN_Y, 16, 31, MAX_Y, 31);
        inner.setPriority(10);
        inner.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.SUMMER);
        inner.setFlag(flags.effects, StateFlag.State.ALLOW);
        outer.setFlag(flags.effects, StateFlag.State.DENY);

        RegionSettings in = chunk(1, 1);
        assertEquals(Optional.of(Season.SUMMER), in.permanentSeason());
        // without a value in the higher region the lower one applies
        assertFalse(in.blockChanges());
        assertTrue(in.effects());

        RegionSettings out = chunk(3, 3);
        assertEquals(Optional.of(Season.WINTER), out.permanentSeason());
        assertFalse(out.effects());
    }

    @Test
    void denyWinsOverAllowAtTheSamePriority() {
        cuboid("a", 0, MIN_Y, 0, 15, MAX_Y, 15).setFlag(flags.mobSpawning, StateFlag.State.ALLOW);
        cuboid("b", 8, MIN_Y, 8, 15, MAX_Y, 15).setFlag(flags.mobSpawning, StateFlag.State.DENY);

        assertFalse(chunk(0, 0).mobSpawning());
    }

    @Test
    void childInheritsFromParent() throws Exception {
        ProtectedCuboidRegion parent = cuboid("taiga", -2000, MIN_Y, -2000, 2000, MAX_Y, 2000);
        parent.setFlag(flags.blockChanges, StateFlag.State.DENY);
        parent.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.WINTER);
        ProtectedCuboidRegion child = new ProtectedCuboidRegion("village", BlockVector3.at(0, 0, 0), BlockVector3.at(15, 10, 15));
        child.setParent(parent);
        child.setPriority(5);
        child.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.FALL);
        regions.addRegion(child);

        RegionSettings s = chunk(0, 0);
        assertEquals(Optional.of(Season.FALL), s.permanentSeason());
        assertFalse(s.blockChanges());
    }

    @Test
    void globalRegionApplies() {
        GlobalProtectedRegion global = new GlobalProtectedRegion("__global__");
        global.setFlag(flags.effects, StateFlag.State.DENY);
        regions.addRegion(global);

        assertFalse(chunk(100, -100).effects());
    }

    @Test
    void foreignEnumFlagOfAnotherPluginIsRead() {
        SimpleFlagRegistry otherRegistry = new SimpleFlagRegistry();
        EnumFlag<SeasonFlagsTest.ForeignSeason> otherSeason = new EnumFlag<>("permanent-season", SeasonFlagsTest.ForeignSeason.class);
        otherRegistry.register(otherSeason);
        SeasonFlags reused = SeasonFlags.register(otherRegistry, Logger.getAnonymousLogger());
        RegionManager otherRegions = new RegionManager(new MemoryRegionDatabase(), new PriorityRTreeIndex.Factory(), otherRegistry);
        ProtectedCuboidRegion r = new ProtectedCuboidRegion("oasis", BlockVector3.at(0, 0, 0), BlockVector3.at(15, 10, 15));
        r.setFlag(otherSeason, SeasonFlagsTest.ForeignSeason.WINTER);
        otherRegions.addRegion(r);

        assertEquals(Optional.of(Season.WINTER),
                WorldGuardSource.query(otherRegions, reused, 0, 0, MIN_Y, MAX_Y).permanentSeason());
    }

    @Test
    void missingFlagsAreIgnored() {
        SeasonFlags none = new SeasonFlags(null, null, null, null);
        cuboid("x", 0, MIN_Y, 0, 15, MAX_Y, 15).setFlag(flags.effects, StateFlag.State.DENY);

        assertEquals(RegionSettings.NONE, WorldGuardSource.query(regions, none, 0, 0, MIN_Y, MAX_Y));
    }

    @Test
    void fingerprintFollowsRelevantChangesOnly() {
        ProtectedCuboidRegion r = cuboid("r", 0, MIN_Y, 0, 15, MAX_Y, 15);
        long before = WorldGuardSource.fingerprint(regions.getRegions(), flags);
        assertEquals(before, WorldGuardSource.fingerprint(regions.getRegions(), flags));

        r.setFlag((EnumFlag<RegionSeason>) flags.permanentSeason, RegionSeason.WINTER);
        long flagged = WorldGuardSource.fingerprint(regions.getRegions(), flags);
        assertNotEquals(before, flagged);

        r.setPriority(3);
        long prioritized = WorldGuardSource.fingerprint(regions.getRegions(), flags);
        assertNotEquals(flagged, prioritized);

        // /rg redefine: a new region of the same name with other dimensions
        ProtectedCuboidRegion bigger = new ProtectedCuboidRegion("r", BlockVector3.at(0, MIN_Y, 0), BlockVector3.at(31, MAX_Y, 15));
        bigger.copyFrom(r);
        regions.addRegion(bigger);
        r = bigger;
        long redefined = WorldGuardSource.fingerprint(regions.getRegions(), flags);
        assertNotEquals(prioritized, redefined);

        // another flag (greeting) does not change the fingerprint
        r.setFlag(greeting, "Hello");
        assertEquals(redefined, WorldGuardSource.fingerprint(regions.getRegions(), flags));

        regions.removeRegion("r");
        assertNotEquals(redefined, WorldGuardSource.fingerprint(regions.getRegions(), flags));
    }
}
