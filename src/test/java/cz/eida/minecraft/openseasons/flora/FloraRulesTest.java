/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.flora;

import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SubSeason;
import cz.eida.minecraft.openseasons.world.PlacementJournal;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloraRulesTest {

    /** default values of config.yml */
    static final MainConfig.Flora SERVER = new MainConfig.Flora(true, 2.0, 10, true, true, true,
            List.of("OAK_LEAVES", "BIRCH_LEAVES", "DARK_OAK_LEAVES"), true, true);
    static final Set<Material> LITTER_LEAVES = EnumSet.of(Material.OAK_LEAVES, Material.BIRCH_LEAVES,
            Material.DARK_OAK_LEAVES);

    final RandomGenerator random = new SplittableRandom(7);

    /** open meadow in a temperate biome */
    static FloraRules.Spot meadow() {
        return new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 15, 0, 0.8, 0.4, null, 0, false);
    }

    /** forest: ground under oak leaves 6 blocks above it, shade */
    static FloraRules.Spot underOak() {
        return new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 11, 0, 0.7, 0.8,
                Material.OAK_LEAVES, 6, false);
    }

    Optional<FloraRules.Plant> choose(PlantKind kind, FloraRules.Spot s) {
        return FloraRules.choose(kind, s, FloraRules.Surroundings.NOTHING, random, LITTER_LEAVES, true);
    }

    @Test
    void seasonsOfKinds() {
        assertEquals(Set.of(Season.SPRING), PlantKind.FLOWERS.spawnSeasons());
        assertTrue(PlantKind.MUSHROOMS.spawnsIn(Season.FALL));
        assertFalse(PlantKind.MUSHROOMS.spawnsIn(Season.SUMMER));
        assertTrue(PlantKind.LEAF_LITTER.aliveIn(Season.FALL));
        assertFalse(PlantKind.LEAF_LITTER.aliveIn(Season.WINTER));
        assertTrue(PlantKind.BERRY_BUSHES.aliveIn(Season.FALL), "bushes last until winter");
        assertTrue(PlantKind.FIREFLY_BUSH.spawnsIn(Season.SUMMER));
        for (PlantKind k : PlantKind.values()) {
            assertTrue(k.aliveSeasons().containsAll(k.spawnSeasons()), k + ": lives at least in the seasons it appears in");
            assertFalse(k.aliveIn(Season.WINTER), k + ": none of these lives in winter");
            assertEquals(Optional.of(k), PlantKind.byId(k.id()));
        }
        assertEquals(Optional.of(PlantKind.LEAF_LITTER), PlantKind.byId("LEAF_LITTER"));
    }

    @Test
    void dueWhenSeasonEnds() {
        PlacementJournal.Entry flower = new PlacementJournal.Entry(0, 64, 0, "flowers", "minecraft:poppy", "minecraft:air");
        assertFalse(FloraRules.due(flower, Optional.of(Season.SPRING)));
        assertTrue(FloraRules.due(flower, Optional.of(Season.SUMMER)));
        assertFalse(FloraRules.due(flower, Optional.empty()), "without a season (modify-blocks, disabled world) nothing changes");

        PlacementJournal.Entry player = new PlacementJournal.Entry(0, 64, 0, PlacementJournal.PLAYER, "minecraft:poppy", "");
        assertFalse(FloraRules.due(player, Optional.of(Season.WINTER)), "player plants never");

        PlacementJournal.Entry restore = new PlacementJournal.Entry(0, 64, 0, PlacementJournal.RESTORE, "minecraft:poppy", "minecraft:air");
        assertTrue(FloraRules.due(restore, Optional.empty()), "a pending restore always");

        PlacementJournal.Entry future = new PlacementJournal.Entry(0, 64, 0, "snow", "minecraft:snow", "minecraft:air");
        assertFalse(FloraRules.due(future, Optional.of(Season.SUMMER)), "leave an unknown kind (other version) alone");
    }

    @Test
    void flowersOnOpenMeadowWithRampInEarlySpring() {
        FloraRules.Plant p = choose(PlantKind.FLOWERS, meadow()).orElseThrow();
        assertTrue(FloraRules.LITTER_COVERS.contains(p.material()), "one of the small spring flowers: " + p);
        assertTrue(choose(PlantKind.FLOWERS, underOak()).isEmpty(), "not in the shade of a forest");

        FloraSettings s = FloraSettings.DEFAULTS;
        assertEquals(0.5, FloraRules.chance(PlantKind.FLOWERS, SERVER, s, SubSeason.START));
        assertEquals(1.0, FloraRules.chance(PlantKind.FLOWERS, SERVER, s, SubSeason.EARLY));
        assertEquals(2.0, FloraRules.chance(PlantKind.FLOWERS, SERVER, s, SubSeason.MIDDLE));
        assertEquals(10.0, FloraRules.chance(PlantKind.GRASS, SERVER, s, SubSeason.START));
    }

    @Test
    void onlyNaturalSpots() {
        FloraRules.Spot lit = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 15, 12, 0.8, 0.4, null, 0, false);
        assertTrue(choose(PlantKind.FLOWERS, lit).isEmpty(), "torches near a building");
        FloraRules.Spot roof = new FloraRules.Spot(Material.GRASS_BLOCK, Material.OAK_PLANKS, Material.AIR, 15, 0, 0.8, 0.4, null, 0, false);
        assertTrue(choose(PlantKind.FLOWERS, roof).isEmpty(), "grass on a plank roof");
        FloraRules.Spot occupied = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.SHORT_GRASS, 15, 0, 0.8, 0.4, null, 0, false);
        assertTrue(choose(PlantKind.FLOWERS, occupied).isEmpty(), "only on a free spot");
        FloraRules.Spot stone = new FloraRules.Spot(Material.STONE, Material.STONE, Material.AIR, 15, 0, 0.8, 0.4, null, 0, false);
        for (PlantKind k : PlantKind.values()) assertTrue(choose(k, stone).isEmpty(), k + " on stone");
        FloraRules.Spot snowy = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 15, 0, 0.0, 0.5, null, 0, false);
        assertTrue(choose(PlantKind.FLOWERS, snowy).isEmpty(), "not in a snowy biome");
    }

    @Test
    void mushroomsNeedShadeUnderTrees() {
        assertTrue(choose(PlantKind.MUSHROOMS, underOak()).isPresent());
        assertTrue(choose(PlantKind.MUSHROOMS, meadow()).isEmpty(), "not on a sunny meadow");
        FloraRules.Spot podzol = new FloraRules.Spot(Material.PODZOL, Material.DIRT, Material.AIR, 15, 0, 0.3, 0.8, null, 0, false);
        assertTrue(choose(PlantKind.MUSHROOMS, podzol).isPresent(), "on podzol they grow in the light too");
        FloraRules.Spot highCanopy = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 11, 0, 0.7, 0.8,
                Material.OAK_LEAVES, 40, false);
        assertTrue(choose(PlantKind.MUSHROOMS, highCanopy).isEmpty(), "leaves high above are no tree shade");
        Material m = choose(PlantKind.MUSHROOMS, underOak()).orElseThrow().material();
        assertTrue(m == Material.BROWN_MUSHROOM || m == Material.RED_MUSHROOM);
    }

    @Test
    void leafLitterUnderConfiguredLeaves() {
        FloraRules.Plant p = choose(PlantKind.LEAF_LITTER, underOak()).orElseThrow();
        assertEquals(Material.LEAF_LITTER, p.material());
        assertTrue(p.amount() >= 1 && p.amount() <= 4);
        FloraRules.Spot spruce = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 11, 0, 0.3, 0.8,
                Material.SPRUCE_LEAVES, 6, false);
        assertTrue(choose(PlantKind.LEAF_LITTER, spruce).isEmpty(), "spruce is not in spawn-leaf-litter-under");
        assertTrue(choose(PlantKind.LEAF_LITTER, meadow()).isEmpty());

        FloraRules.Spot grassy = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.SHORT_GRASS, 11, 0, 0.7, 0.8,
                Material.OAK_LEAVES, 6, false);
        assertTrue(FloraRules.choose(PlantKind.LEAF_LITTER, grassy, FloraRules.Surroundings.NOTHING, random, LITTER_LEAVES, true)
                .isPresent(), "destroy-plants-for-leaf-litter: over grass");
        assertTrue(FloraRules.choose(PlantKind.LEAF_LITTER, grassy, FloraRules.Surroundings.NOTHING, random, LITTER_LEAVES, false)
                .isEmpty(), "without destroy-plants only on a free spot");
        FloraRules.Spot playerFlower = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.POPPY, 11, 0, 0.7, 0.8,
                Material.OAK_LEAVES, 6, true);
        assertTrue(choose(PlantKind.LEAF_LITTER, playerFlower).isEmpty(), "never a player flower");
        FloraRules.Spot tooHigh = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 11, 0, 0.7, 0.8,
                Material.OAK_LEAVES, 31, false);
        assertTrue(choose(PlantKind.LEAF_LITTER, tooHigh).isEmpty(), "at most 30 blocks below the leaves");

        int[] counts = new int[5];
        for (int i = 0; i < 8000; i++) counts[FloraRules.litterAmount(random)]++;
        assertTrue(counts[1] > 3600 && counts[1] < 4400, "half with one piece: " + counts[1]);
        assertTrue(counts[4] > 800 && counts[4] < 1200, "an eighth with four: " + counts[4]);
    }

    @Test
    void berryBushesAndCaps() {
        FloraRules.Plant p = choose(PlantKind.BERRY_BUSHES, meadow()).orElseThrow();
        assertEquals(Material.SWEET_BERRY_BUSH, p.material());
        assertTrue(p.age() == 1 || p.age() == 2, "a young bush, ripens by itself");
        FloraRules.Spot hot = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 15, 0, 2.0, 0.0, null, 0, false);
        assertTrue(choose(PlantKind.BERRY_BUSHES, hot).isEmpty(), "not in a savanna");

        FloraSettings s = FloraSettings.DEFAULTS;
        assertEquals(10, FloraRules.maxPerChunk(PlantKind.BERRY_BUSHES, SERVER, s), "max-berrybushes-per-chunk");
        assertEquals(12, FloraRules.maxPerChunk(PlantKind.FLOWERS, SERVER, s));
        MainConfig.Flora noBerries = new MainConfig.Flora(true, 2.0, 0, true, true, true, List.of("OAK_LEAVES"), true, true);
        assertFalse(FloraRules.enabled(PlantKind.BERRY_BUSHES, noBerries, s), "limit 0 turns the bushes off");
    }

    @Test
    void switchesFromConfigKeys() {
        FloraSettings s = FloraSettings.DEFAULTS;
        for (PlantKind k : PlantKind.values()) assertTrue(FloraRules.enabled(k, SERVER, s), k + " on by default");
        MainConfig.Flora off = new MainConfig.Flora(true, 0.0, 10, true, false, true, List.of("OAK_LEAVES"), false, false);
        assertFalse(FloraRules.enabled(PlantKind.FLOWERS, off, s), "flower-chance-in-spring: 0%");
        assertFalse(FloraRules.enabled(PlantKind.GRASS, off, s), "regrow-grass-everywhere: false");
        assertFalse(FloraRules.enabled(PlantKind.MUSHROOMS, off, s), "spawn-mushrooms-in-fall: false");
        assertFalse(FloraRules.enabled(PlantKind.LEAF_LITTER, off, s), "generate-leaf-litter: false");
        MainConfig.Flora noLeaves = new MainConfig.Flora(true, 2.0, 10, true, true, true, List.of(), true, true);
        assertFalse(FloraRules.enabled(PlantKind.LEAF_LITTER, noLeaves, s), "empty spawn-leaf-litter-under");
    }

    @Test
    void extras() {
        FloraRules.Surroundings spruce = m -> m.contains(Material.SPRUCE_LOG);
        FloraRules.Spot taiga = new FloraRules.Spot(Material.PODZOL, Material.DIRT, Material.AIR, 12, 0, 0.25, 0.8, null, 0, false);
        assertTrue(FloraRules.choose(PlantKind.FIREFLY_BUSH, taiga, spruce, random, LITTER_LEAVES, true).isPresent(),
                "near spruces");
        assertTrue(choose(PlantKind.FIREFLY_BUSH, taiga).isEmpty(), "not without spruces");

        FloraRules.Spot humidMeadow = new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 15, 0, 0.5, 0.8, null, 0, false);
        FloraRules.Plant wild = choose(PlantKind.WILDFLOWERS, humidMeadow).orElseThrow();
        assertEquals(Material.WILDFLOWERS, wild.material());
        assertTrue(wild.amount() >= 1 && wild.amount() <= 4);
        assertTrue(choose(PlantKind.WILDFLOWERS, meadow()).isEmpty(), "not on dry plains");
        assertTrue(FloraRules.choose(PlantKind.WILDFLOWERS, meadow(), m -> m.contains(Material.BIRCH_LOG), random,
                LITTER_LEAVES, true).isPresent(), "near birches yes");

        assertTrue(FloraRules.choose(PlantKind.PINK_PETALS, meadow(), m -> m.contains(Material.CHERRY_LEAVES), random,
                LITTER_LEAVES, true).isPresent());
        assertTrue(choose(PlantKind.PINK_PETALS, meadow()).isEmpty(), "only under cherry trees");

        FloraRules.Spot desert = new FloraRules.Spot(Material.SAND, Material.SAND, Material.AIR, 15, 0, 2.0, 0.0, null, 0, false);
        Material dry = choose(PlantKind.DRY_GRASS, desert).orElseThrow().material();
        assertTrue(dry == Material.SHORT_DRY_GRASS || dry == Material.TALL_DRY_GRASS);
        assertTrue(choose(PlantKind.DRY_GRASS, meadow()).isEmpty(), "not in a temperate biome");

        FloraRules.Spot cactus = new FloraRules.Spot(Material.CACTUS, Material.CACTUS, Material.AIR, 15, 0, 2.0, 0.0, null, 0, false);
        assertEquals(Material.CACTUS_FLOWER, choose(PlantKind.CACTUS_FLOWER, cactus).orElseThrow().material());
        assertTrue(choose(PlantKind.CACTUS_FLOWER, desert).isEmpty(), "only on a cactus");

        assertEquals(Material.BUSH, choose(PlantKind.BUSH, meadow()).orElseThrow().material());
    }

    /** pumpkins in autumn: temperate meadows, they vanish with winter */
    @Test
    void pumpkinsOnTemperateMeadowsInFall() {
        assertEquals(Material.PUMPKIN, choose(PlantKind.PUMPKINS, meadow()).orElseThrow().material());
        // neither in the shade of a forest nor in snow
        assertTrue(choose(PlantKind.PUMPKINS, underOak()).isEmpty());
        assertTrue(choose(PlantKind.PUMPKINS, new FloraRules.Spot(Material.GRASS_BLOCK, Material.DIRT, Material.AIR, 15, 0,
                0.0, 0.5, null, 0, false)).isEmpty());
        assertTrue(PlantKind.PUMPKINS.spawnsIn(cz.eida.minecraft.openseasons.season.Season.FALL));
        assertTrue(PlantKind.PUMPKINS.extra());
        assertFalse(PlantKind.PUMPKINS.aliveIn(cz.eida.minecraft.openseasons.season.Season.WINTER));
    }
}
