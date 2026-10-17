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

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Rules of seasonal plants without a server: when each plant appears and disappears and which place it belongs to.
 * The place is described by {@link Spot}, the surroundings (trees nearby) by {@link Surroundings}. A plant only belongs
 * to a natural place: dirt or grass on the surface with a natural base, free space above it, no artificial
 * light (torches near buildings) and no player plant.
 */
public final class FloraRules {

    /** most artificial light at the place; player buildings tend to be lit */
    static final int MAX_BLOCK_LIGHT = 2;
    /** leaf litter: at most this many blocks between the leaves and the ground */
    static final int LITTER_MAX_DROP = 30;
    /** mushrooms: leaves at most this many blocks above the ground, otherwise there is no tree shade */
    static final int SHADE_MAX_HEIGHT = 16;

    static final Set<Material> MEADOW = EnumSet.of(Material.GRASS_BLOCK);
    static final Set<Material> SOIL = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL,
            Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MOSS_BLOCK);
    static final Set<Material> MUSHROOM_SOIL = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL,
            Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MYCELIUM);
    /** mushrooms grow here even in light */
    static final Set<Material> MUSHROOM_ANY_LIGHT = EnumSet.of(Material.PODZOL, Material.MYCELIUM);
    static final Set<Material> BERRY_SOIL = EnumSet.of(Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT);
    static final Set<Material> HOT_GROUND = EnumSet.of(Material.SAND, Material.RED_SAND, Material.COARSE_DIRT,
            Material.GRASS_BLOCK);
    static final Set<Material> PETAL_SOIL = EnumSet.of(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL,
            Material.COARSE_DIRT, Material.MOSS_BLOCK);

    /**
     * Natural base below the ground: grass on a plank roof or sand on a building wall does not count.
     * A flat world (grass, dirt, bedrock) passes.
     */
    static final Set<Material> NATURAL_BASE = EnumSet.of(Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT,
            Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM, Material.MOSS_BLOCK, Material.MUD,
            Material.CLAY, Material.GRAVEL, Material.SAND, Material.RED_SAND, Material.SANDSTONE,
            Material.RED_SANDSTONE, Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.TUFF, Material.CALCITE, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.BROWN_TERRACOTTA,
            Material.RED_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.SNOW_BLOCK, Material.PACKED_ICE,
            Material.BEDROCK, Material.CACTUS);

    /** small plants that leaf litter may cover ({@code destroy-plants-for-leaf-litter}) */
    static final Set<Material> LITTER_COVERS = EnumSet.of(Material.SHORT_GRASS, Material.FERN, Material.BUSH,
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET,
            Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY);

    /** plants a player plants which have to be protected with {@code keep-player-placed-plants} */
    static final Set<Material> PROTECTED_PLAYER_PLANTS = EnumSet.copyOf(LITTER_COVERS);

    static final Set<Material> SPRUCE = EnumSet.of(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, Material.SPRUCE_WOOD);
    static final Set<Material> CHERRY = EnumSet.of(Material.CHERRY_LOG, Material.CHERRY_LEAVES, Material.CHERRY_WOOD);
    static final Set<Material> BIRCH = EnumSet.of(Material.BIRCH_LOG, Material.BIRCH_WOOD);

    /** spring flowers with weights */
    private static final List<Material> SPRING_FLOWERS = List.of(
            Material.DANDELION, Material.DANDELION, Material.DANDELION, Material.DANDELION,
            Material.POPPY, Material.POPPY, Material.POPPY, Material.POPPY,
            Material.AZURE_BLUET, Material.AZURE_BLUET, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.OXEYE_DAISY, Material.OXEYE_DAISY,
            Material.CORNFLOWER, Material.CORNFLOWER, Material.CORNFLOWER,
            Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
            Material.ALLIUM, Material.LILY_OF_THE_VALLEY);

    private FloraRules() {
    }

    /**
     * Place for a plant: {@code ground} is the highest solid block of the column without leaves, the plant would go on
     * the block above it ({@code above}).
     *
     * @param base        block below the ground
     * @param skyLight    sky light at the plant's place (without the time of day)
     * @param blockLight  block light (torches, lava) at the plant's place
     * @param canopy      the highest block of the column including leaves when it is higher than the ground; otherwise {@code null}
     * @param canopyHeight how far {@code canopy} is above the ground
     * @param playerPlant a plant planted by a player is at the place
     */
    public record Spot(Material ground, Material base, Material above, int skyLight, int blockLight,
                       double temperature, double humidity, Material canopy, int canopyHeight,
                       boolean playerPlant) {
    }

    /** surroundings of the place; the query is expensive (hundreds of blocks), so only after the dice roll */
    @FunctionalInterface
    public interface Surroundings {
        /** whether one of the materials is within 4 blocks of the place (horizontally) */
        boolean near(Set<Material> materials);

        Surroundings NOTHING = materials -> false;
    }

    /**
     * What to place.
     *
     * @param amount number of pieces (litter, wildflowers 1–4), 0 = default
     * @param age    age of the bush, −1 = default
     */
    public record Plant(Material material, int amount, int age) {
        static Plant of(Material m) {
            return new Plant(m, 0, -1);
        }
    }

    // --- when ---

    /** the kind is enabled (basic kinds via config.yml, extra ones via flora.yml) */
    public static boolean enabled(PlantKind kind, MainConfig.Flora flora, FloraSettings settings) {
        boolean on = switch (kind) {
            case FLOWERS -> flora.flowerChanceInSpring() > 0;
            case GRASS -> flora.regrowGrassEverywhere();
            case MUSHROOMS -> flora.spawnMushroomsInFall();
            case BERRY_BUSHES -> true;
            case LEAF_LITTER -> flora.generateLeafLitter() && !flora.spawnLeafLitterUnder().isEmpty();
            default -> settings.kind(kind).enabled();
        };
        return on && (kind == PlantKind.FLOWERS || settings.kind(kind).chance() > 0)
                && maxPerChunk(kind, flora, settings) > 0;
    }

    /**
     * Chance of a plant at a tried place in percent. Spring flowers by
     * {@code flower-chance-in-spring}, a quarter at the start of spring and half in early spring.
     */
    public static double chance(PlantKind kind, MainConfig.Flora flora, FloraSettings settings, SubSeason phase) {
        if (kind != PlantKind.FLOWERS) return settings.kind(kind).chance();
        double base = flora.flowerChanceInSpring();
        return switch (phase) {
            case START -> base / 4;
            case EARLY -> base / 2;
            default -> base;
        };
    }

    /** most plants of a kind from OpenSeasons in a chunk; for bushes all bushes in the chunk */
    public static int maxPerChunk(PlantKind kind, MainConfig.Flora flora, FloraSettings settings) {
        return kind == PlantKind.BERRY_BUSHES ? flora.maxBerryBushesPerChunk() : settings.kind(kind).maxPerChunk();
    }

    /**
     * Should OpenSeasons remove a block from the journal now? Yes when it waits for restoring, or when it is a seasonal
     * plant and the world is in a season in which it no longer lives. Player plants and unknown kinds (e.g. from a newer
     * version) never; without a season in the world nothing changes.
     */
    public static boolean due(PlacementJournal.Entry entry, Optional<Season> season) {
        if (entry.byPlayer()) return false;
        if (PlacementJournal.RESTORE.equals(entry.kind())) return true;
        if (season.isEmpty()) return false;
        return PlantKind.byId(entry.kind()).map(k -> !k.aliveIn(season.get())).orElse(false);
    }

    // --- where and what ---

    /** natural place: no artificial light, a natural base and no player plant */
    static boolean natural(Spot s) {
        return s.blockLight() <= MAX_BLOCK_LIGHT && !s.playerPlant() && s.base() != null
                && NATURAL_BASE.contains(s.base());
    }

    /**
     * Plant for a place, or empty when the kind does not belong there.
     *
     * @param litterLeaves leaves under which leaf litter falls ({@code spawn-leaf-litter-under})
     * @param destroyPlants leaf litter may cover small plants ({@code destroy-plants-for-leaf-litter})
     */
    public static Optional<Plant> choose(PlantKind kind, Spot s, Surroundings around, RandomGenerator random,
                                         Set<Material> litterLeaves, boolean destroyPlants) {
        if (!natural(s)) return Optional.empty();
        boolean free = s.above() == Material.AIR;
        return switch (kind) {
            case FLOWERS -> free && MEADOW.contains(s.ground()) && s.skyLight() >= 13
                    && between(s.temperature(), 0.3, 1.0)
                    ? Optional.of(Plant.of(SPRING_FLOWERS.get(random.nextInt(SPRING_FLOWERS.size()))))
                    : Optional.empty();
            case GRASS -> free && MEADOW.contains(s.ground()) && s.skyLight() >= 9
                    ? Optional.of(Plant.of(s.temperature() < 0.45 && random.nextBoolean() ? Material.FERN
                    : Material.SHORT_GRASS))
                    : Optional.empty();
            case MUSHROOMS -> free && MUSHROOM_SOIL.contains(s.ground())
                    && (MUSHROOM_ANY_LIGHT.contains(s.ground())
                    || s.skyLight() <= 12 && leavesOverhead(s))
                    ? Optional.of(Plant.of(random.nextInt(100) < 35 ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM))
                    : Optional.empty();
            case BERRY_BUSHES -> free && BERRY_SOIL.contains(s.ground()) && s.skyLight() >= 9
                    && s.temperature() <= 0.8
                    ? Optional.of(new Plant(Material.SWEET_BERRY_BUSH, 0, 1 + random.nextInt(2)))
                    : Optional.empty();
            case LEAF_LITTER -> litter(s, random, litterLeaves, destroyPlants);
            case FIREFLY_BUSH -> free && SOIL.contains(s.ground()) && around.near(SPRUCE)
                    ? Optional.of(Plant.of(Material.FIREFLY_BUSH))
                    : Optional.empty();
            case WILDFLOWERS -> free && MEADOW.contains(s.ground()) && s.skyLight() >= 13
                    && between(s.temperature(), 0.4, 0.95) && (s.humidity() >= 0.6 || around.near(BIRCH))
                    ? Optional.of(new Plant(Material.WILDFLOWERS, 1 + random.nextInt(4), -1))
                    : Optional.empty();
            case PINK_PETALS -> free && PETAL_SOIL.contains(s.ground()) && around.near(CHERRY)
                    ? Optional.of(new Plant(Material.PINK_PETALS, 1 + random.nextInt(4), -1))
                    : Optional.empty();
            case DRY_GRASS -> free && HOT_GROUND.contains(s.ground()) && s.skyLight() >= 13 && s.temperature() >= 1.0
                    ? Optional.of(Plant.of(random.nextInt(100) < 30 ? Material.TALL_DRY_GRASS : Material.SHORT_DRY_GRASS))
                    : Optional.empty();
            case BUSH -> free && MEADOW.contains(s.ground()) && s.skyLight() >= 11
                    && between(s.temperature(), 0.5, 0.95)
                    ? Optional.of(Plant.of(Material.BUSH))
                    : Optional.empty();
            case CACTUS_FLOWER -> free && s.ground() == Material.CACTUS && s.temperature() >= 1.0
                    ? Optional.of(Plant.of(Material.CACTUS_FLOWER))
                    : Optional.empty();
            case PUMPKINS -> free && MEADOW.contains(s.ground()) && s.skyLight() >= 13
                    && between(s.temperature(), 0.5, 0.95)
                    ? Optional.of(Plant.of(Material.PUMPKIN))
                    : Optional.empty();
        };
    }

    /** leaf litter: under the configured leaves, on dirt, on a free place or over small plants */
    private static Optional<Plant> litter(Spot s, RandomGenerator random, Set<Material> leaves, boolean destroyPlants) {
        if (s.canopy() == null || !leaves.contains(s.canopy()) || s.canopyHeight() > LITTER_MAX_DROP) {
            return Optional.empty();
        }
        if (!SOIL.contains(s.ground())) return Optional.empty();
        boolean free = s.above() == Material.AIR || destroyPlants && LITTER_COVERS.contains(s.above());
        if (!free) return Optional.empty();
        return Optional.of(new Plant(Material.LEAF_LITTER, litterAmount(random), -1));
    }

    /** pieces of leaf litter: 1 in half the cases, 2 in a quarter, 3 and 4 in an eighth */
    static int litterAmount(RandomGenerator random) {
        int roll = random.nextInt(8);
        if (roll < 4) return 1;
        if (roll < 6) return 2;
        return roll == 6 ? 3 : 4;
    }

    private static boolean leavesOverhead(Spot s) {
        return s.canopy() != null && s.canopy().name().endsWith("_LEAVES") && s.canopyHeight() <= SHADE_MAX_HEIGHT;
    }

    private static boolean between(double v, double min, double max) {
        return v >= min && v <= max;
    }
}
