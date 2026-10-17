/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.entity;

import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.season.Season;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.SpawnCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobRulesTest {

    /** {@code entities:} as in the default config.yml */
    static final MainConfig.Entities LIVE = new MainConfig.Entities(true, 10.0, true,
            new MainConfig.SpringEntities(true, true, true),
            new MainConfig.SummerEntities(false, false, true, true),
            new MainConfig.FallEntities(true, false, false, false, true, true, true, 22.0),
            new MainConfig.WinterEntities(false, false, false, true, true));

    static final MainConfig.Entities ALL_ON = new MainConfig.Entities(true, 10.0, true,
            new MainConfig.SpringEntities(true, true, true),
            new MainConfig.SummerEntities(true, true, true, true),
            new MainConfig.FallEntities(true, true, true, true, true, true, true, 22.0),
            new MainConfig.WinterEntities(true, true, true, true, true));

    static final MainConfig.Entities ALL_OFF = new MainConfig.Entities(true, 10.0, false,
            new MainConfig.SpringEntities(false, false, false),
            new MainConfig.SummerEntities(false, false, false, false),
            new MainConfig.FallEntities(false, false, false, false, false, false, false, 22.0),
            new MainConfig.WinterEntities(false, false, false, false, false));

    @Test
    void liveServerSpawnTablePerSeason() {
        assertEquals(List.of(SeasonalMob.COW, SeasonalMob.SHEEP, SeasonalMob.CHICKEN, SeasonalMob.PIG,
                SeasonalMob.BEE, SeasonalMob.RABBIT), MobRules.enabled(Season.SPRING, LIVE));
        assertEquals(List.of(SeasonalMob.PARROT), MobRules.enabled(Season.SUMMER, LIVE));
        assertEquals(List.of(SeasonalMob.BAT, SeasonalMob.SPIDER, SeasonalMob.MOOSHROOM),
                MobRules.enabled(Season.FALL, LIVE));
        assertEquals(List.of(SeasonalMob.SNOW_GOLEM), MobRules.enabled(Season.WINTER, LIVE));
    }

    @Test
    void everyFlagGatesItsSpecies() {
        assertEquals(List.of(SeasonalMob.OCELOT, SeasonalMob.PANDA, SeasonalMob.PARROT),
                MobRules.enabled(Season.SUMMER, ALL_ON));
        assertEquals(List.of(SeasonalMob.BAT, SeasonalMob.SPIDER, SeasonalMob.CAVE_SPIDER, SeasonalMob.RED_FOX,
                SeasonalMob.FROG, SeasonalMob.MOOSHROOM), MobRules.enabled(Season.FALL, ALL_ON));
        assertEquals(List.of(SeasonalMob.WOLF, SeasonalMob.SNOW_FOX, SeasonalMob.POLAR_BEAR, SeasonalMob.SNOW_GOLEM),
                MobRules.enabled(Season.WINTER, ALL_ON));
        for (Season s : Season.values()) {
            assertEquals(List.of(), MobRules.enabled(s, ALL_OFF), s.name());
            assertEquals(MobRules.all(s), MobRules.enabled(s, ALL_ON), s.name());
        }
        MainConfig.Entities noSpawning = new MainConfig.Entities(false, 10.0, true, ALL_ON.spring(), ALL_ON.summer(),
                ALL_ON.fall(), ALL_ON.winter());
        for (Season s : Season.values()) assertEquals(List.of(), MobRules.enabled(s, noSpawning));
    }

    @Test
    void groupSizesFromInventory() {
        for (SeasonalMob m : List.of(SeasonalMob.BEE, SeasonalMob.RABBIT, SeasonalMob.PARROT, SeasonalMob.WOLF)) {
            assertEquals(2, m.minGroup(), m.id());
            assertEquals(6, m.maxGroup(), m.id());
        }
        for (SeasonalMob m : List.of(SeasonalMob.RED_FOX, SeasonalMob.SNOW_FOX, SeasonalMob.MOOSHROOM,
                SeasonalMob.OCELOT, SeasonalMob.PANDA, SeasonalMob.POLAR_BEAR, SeasonalMob.SNOW_GOLEM)) {
            assertEquals(1, m.minGroup(), m.id());
            assertEquals(1, m.maxGroup(), m.id());
        }
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        for (SeasonalMob m : SeasonalMob.values()) {
            for (int i = 0; i < 50; i++) {
                int n = MobRules.groupSize(m, random);
                assertTrue(n >= m.minGroup() && n <= m.maxGroup(), m.id() + " " + n);
            }
        }
        // spring young of farm animals
        for (SeasonalMob m : SeasonalMob.values()) {
            assertEquals(Set.of(SeasonalMob.COW, SeasonalMob.SHEEP, SeasonalMob.CHICKEN, SeasonalMob.PIG).contains(m),
                    m.baby(), m.id());
        }
    }

    @Test
    void foxVariantsBySeason() {
        assertEquals(EntityType.FOX, SeasonalMob.RED_FOX.type());
        assertEquals(EntityType.FOX, SeasonalMob.SNOW_FOX.type());
        assertEquals(Season.FALL, SeasonalMob.RED_FOX.season());
        assertEquals(Season.WINTER, SeasonalMob.SNOW_FOX.season());
    }

    @Test
    void huskAndStrayReplacement() {
        assertEquals(Optional.of(EntityType.HUSK), MobRules.replacement(EntityType.ZOMBIE, Season.SUMMER, LIVE));
        assertEquals(Optional.of(EntityType.STRAY), MobRules.replacement(EntityType.SKELETON, Season.WINTER, LIVE));
        for (Season s : List.of(Season.WINTER, Season.SPRING, Season.FALL)) {
            assertEquals(Optional.empty(), MobRules.replacement(EntityType.ZOMBIE, s, LIVE), s.name());
        }
        for (Season s : List.of(Season.SPRING, Season.SUMMER, Season.FALL)) {
            assertEquals(Optional.empty(), MobRules.replacement(EntityType.SKELETON, s, LIVE), s.name());
        }
        // a replacement is not replaced again, other types unchanged
        assertEquals(Optional.empty(), MobRules.replacement(EntityType.HUSK, Season.SUMMER, LIVE));
        assertEquals(Optional.empty(), MobRules.replacement(EntityType.STRAY, Season.WINTER, LIVE));
        assertEquals(Optional.empty(), MobRules.replacement(EntityType.ZOMBIE_VILLAGER, Season.SUMMER, LIVE));
        assertEquals(Optional.empty(), MobRules.replacement(EntityType.WITHER_SKELETON, Season.WINTER, LIVE));
        // switches turned off
        assertEquals(Optional.empty(), MobRules.replacement(EntityType.ZOMBIE, Season.SUMMER, ALL_OFF));
        assertEquals(Optional.empty(), MobRules.replacement(EntityType.SKELETON, Season.WINTER, ALL_OFF));
    }

    @Test
    void pumpkinsOnlyInFallOnEmptyHumanoidHeads() {
        assertTrue(MobRules.pumpkinCandidate(EntityType.ZOMBIE, Season.FALL, LIVE, true));
        assertTrue(MobRules.pumpkinCandidate(EntityType.SKELETON, Season.FALL, LIVE, true));
        assertFalse(MobRules.pumpkinCandidate(EntityType.ZOMBIE, Season.FALL, LIVE, false), "already has a helmet");
        assertFalse(MobRules.pumpkinCandidate(EntityType.SPIDER, Season.FALL, LIVE, true), "not visible on a spider");
        assertFalse(MobRules.pumpkinCandidate(EntityType.CREEPER, Season.FALL, LIVE, true));
        assertFalse(MobRules.pumpkinCandidate(EntityType.ZOMBIE, Season.WINTER, LIVE, true));
        assertFalse(MobRules.pumpkinCandidate(EntityType.ZOMBIE, Season.FALL, ALL_OFF, true));
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        int hits = 0;
        for (int i = 0; i < 20000; i++) if (MobRules.roll(random, LIVE.fall().pumpkinChance())) hits++;
        assertEquals(0.22, hits / 20000.0, 0.02);
    }

    @Test
    void capsAndDensity() {
        assertEquals(3, MobRules.allowedByCap(3, 0, 10));
        assertEquals(2, MobRules.allowedByCap(3, 8, 10));
        assertEquals(0, MobRules.allowedByCap(3, 10, 10));
        assertEquals(0, MobRules.allowedByCap(3, 14, 10));
        assertEquals(6, MobRules.allowedByCap(6, 50, -1), "no limit");
        assertTrue(MobRules.roomForGroup(0, 0));
        assertTrue(MobRules.roomForGroup(0, 8));
        assertFalse(MobRules.roomForGroup(1, 1), "one group per chunk");
        assertFalse(MobRules.roomForGroup(0, 9), "at most 9 in the 3 × 3 surroundings");
        // towards the limit: seasonal always, animals of the world never, monsters and bats only when they can despawn
        assertTrue(MobRules.countsTowardCap(SpawnCategory.ANIMAL, true, false));
        assertFalse(MobRules.countsTowardCap(SpawnCategory.ANIMAL, false, true));
        assertTrue(MobRules.countsTowardCap(SpawnCategory.MONSTER, false, true));
        assertFalse(MobRules.countsTowardCap(SpawnCategory.MONSTER, false, false));
        assertTrue(MobRules.countsTowardCap(SpawnCategory.AMBIENT, false, true));
    }

    @Test
    void despawnFilter() {
        MobRules.Keep none = MobRules.Keep.NONE;
        assertTrue(MobRules.despawn(Season.SPRING, Season.SUMMER, none, true));
        assertTrue(MobRules.despawn(Season.WINTER, Season.SPRING, none, true));
        assertFalse(MobRules.despawn(Season.SPRING, Season.SPRING, none, true), "the season lasts");
        assertFalse(MobRules.despawn(Season.SPRING, Season.SUMMER, none, false), "despawn-season-animals: false");
        assertFalse(MobRules.despawn(Season.SPRING, Season.SUMMER, new MobRules.Keep(true, false, false, false, false), true));
        assertFalse(MobRules.despawn(Season.SPRING, Season.SUMMER, new MobRules.Keep(false, true, false, false, false), true));
        assertFalse(MobRules.despawn(Season.WINTER, Season.SPRING, new MobRules.Keep(false, false, true, false, false), true));
        assertFalse(MobRules.despawn(Season.FALL, Season.WINTER, new MobRules.Keep(false, false, false, true, false), true));
        assertFalse(MobRules.despawn(Season.SPRING, Season.SUMMER, new MobRules.Keep(false, false, false, false, true), true));
    }

    private static MobRules.Spot spot(Material ground, double temperature, double humidity, int sky, int block,
                                      boolean night, boolean water) {
        return new MobRules.Spot(ground, true, temperature, humidity, sky, block, night, water);
    }

    @Test
    void habitat() {
        // plains: temperature 0.8, humidity 0.4
        assertTrue(MobRules.fits(SeasonalMob.COW, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        assertTrue(MobRules.fits(SeasonalMob.BEE, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.COW, spot(Material.SAND, 0.8, 0.4, 15, 0, false, false)), "ground");
        assertFalse(MobRules.fits(SeasonalMob.COW, spot(Material.GRASS_BLOCK, 0.0, 0.5, 15, 0, false, false)), "snow");
        assertFalse(MobRules.fits(SeasonalMob.COW, spot(Material.GRASS_BLOCK, 0.8, 0.4, 0, 0, false, false)), "dark");
        assertFalse(MobRules.fits(SeasonalMob.COW, new MobRules.Spot(Material.GRASS_BLOCK, false, 0.8, 0.4, 15, 0,
                false, false)), "no room");
        // parrots in warm wet forests, not on plains
        assertTrue(MobRules.fits(SeasonalMob.PARROT, spot(Material.GRASS_BLOCK, 0.95, 0.9, 15, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.PARROT, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        // winter: polar bear only in snow, snow golem not in a desert
        assertTrue(MobRules.fits(SeasonalMob.POLAR_BEAR, spot(Material.SNOW_BLOCK, 0.0, 0.5, 15, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.POLAR_BEAR, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        assertTrue(MobRules.fits(SeasonalMob.SNOW_GOLEM, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.SNOW_GOLEM, spot(Material.SAND, 2.0, 0.0, 15, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.SNOW_GOLEM, spot(Material.GRASS_BLOCK, 2.0, 0.0, 15, 0, false, false)),
                "savanna: it would melt");
        // autumn: bats at night, spiders at night without light, frogs near water
        assertTrue(MobRules.fits(SeasonalMob.BAT, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, true, false)));
        assertFalse(MobRules.fits(SeasonalMob.BAT, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        assertTrue(MobRules.fits(SeasonalMob.SPIDER, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, true, false)));
        assertFalse(MobRules.fits(SeasonalMob.SPIDER, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 9, true, false)));
        assertFalse(MobRules.fits(SeasonalMob.SPIDER, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
        assertTrue(MobRules.fits(SeasonalMob.CAVE_SPIDER, spot(Material.DEEPSLATE, 0.8, 0.4, 0, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.CAVE_SPIDER, spot(Material.DEEPSLATE, 0.8, 0.4, 0, 4, false, false)));
        assertTrue(MobRules.fits(SeasonalMob.FROG, spot(Material.MUD, 0.8, 0.9, 15, 0, false, true)));
        assertFalse(MobRules.fits(SeasonalMob.FROG, spot(Material.MUD, 0.8, 0.9, 15, 0, false, false)), "no water");
        assertTrue(MobRules.fits(SeasonalMob.MOOSHROOM, spot(Material.PODZOL, 0.7, 0.8, 12, 0, false, false)));
        assertFalse(MobRules.fits(SeasonalMob.MOOSHROOM, spot(Material.GRASS_BLOCK, 0.8, 0.4, 15, 0, false, false)));
    }

    @Test
    void ringAroundPlayer() {
        List<int[]> ring = MobRules.ring();
        assertEquals(17 * 17 - 3 * 3, ring.size());
        for (int[] d : ring) {
            int dist = Math.max(Math.abs(d[0]), Math.abs(d[1]));
            assertTrue(dist >= MobRules.RING_MIN && dist <= MobRules.RING_MAX);
        }
        assertEquals(((long) -1 & 0xFFFFFFFFL) | (2L << 32), MobRules.chunkKey(-1, 2));
        assertEquals(MobRules.chunkKey(3, -7), ((long) 3) | (((long) -7 & 0xFFFFFFFFL) << 32));
    }

    @Test
    void debugNames() {
        assertEquals(Optional.of(SeasonalMob.SNOW_GOLEM), SeasonalMob.byId("snow-golem"));
        assertEquals(Optional.of(SeasonalMob.RED_FOX), SeasonalMob.byId("RED_FOX"));
        assertEquals(Optional.empty(), SeasonalMob.byId("zombie"));
        for (String action : MobsDebug.ACTIONS) {
            assertTrue(SeasonalMob.byId(action).isEmpty() && Season.parse(action).isEmpty(), action);
        }
        for (String season : MobsDebug.SEASONS) assertTrue(Season.parse(season).isPresent(), season);
    }
}
