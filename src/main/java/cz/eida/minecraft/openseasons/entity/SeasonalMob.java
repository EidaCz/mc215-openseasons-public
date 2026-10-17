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

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Seasonal kinds of animals and monsters: in which season they appear in addition, how many
 * there are in a group, where (climate by biome temperature and downfall, surface) and which switch
 * in {@code entities.*} enables them. The kind appears
 * where it also lives in vanilla (parrots in warm humid forests, polar bears in snow, snow golems
 * outside hot biomes where they would melt).
 */
public enum SeasonalMob {

    // spring: young farm animals, bees, rabbits
    COW(Season.SPRING, EntityType.COW, SpawnCategory.ANIMAL, 1, 3, Placement.GROUND, Climate.TEMPERATE,
            Surfaces.GRASSY, Light.DAYLIGHT, true, e -> e.spring().extraAnimals()),
    SHEEP(Season.SPRING, EntityType.SHEEP, SpawnCategory.ANIMAL, 1, 3, Placement.GROUND, Climate.TEMPERATE,
            Surfaces.GRASSY, Light.DAYLIGHT, true, e -> e.spring().extraAnimals()),
    CHICKEN(Season.SPRING, EntityType.CHICKEN, SpawnCategory.ANIMAL, 1, 3, Placement.GROUND, Climate.TEMPERATE,
            Surfaces.GRASSY, Light.DAYLIGHT, true, e -> e.spring().extraAnimals()),
    PIG(Season.SPRING, EntityType.PIG, SpawnCategory.ANIMAL, 1, 3, Placement.GROUND, Climate.TEMPERATE,
            Surfaces.GRASSY, Light.DAYLIGHT, true, e -> e.spring().extraAnimals()),
    BEE(Season.SPRING, EntityType.BEE, SpawnCategory.ANIMAL, 2, 6, Placement.AIR, Climate.FLOWERY,
            Surfaces.GRASSY, Light.DAYLIGHT, false, e -> e.spring().extraBees()),
    RABBIT(Season.SPRING, EntityType.RABBIT, SpawnCategory.ANIMAL, 2, 6, Placement.GROUND, Climate.LAND,
            Surfaces.RABBIT, Light.DAYLIGHT, false, e -> e.spring().extraRabbits()),

    // summer: tropical animals
    OCELOT(Season.SUMMER, EntityType.OCELOT, SpawnCategory.ANIMAL, 1, 1, Placement.GROUND, Climate.JUNGLE,
            Surfaces.FOREST_FLOOR, Light.DAYLIGHT, false, e -> e.summer().ocelots()),
    PANDA(Season.SUMMER, EntityType.PANDA, SpawnCategory.ANIMAL, 1, 1, Placement.GROUND, Climate.JUNGLE,
            Surfaces.FOREST_FLOOR, Light.DAYLIGHT, false, e -> e.summer().pandas()),
    PARROT(Season.SUMMER, EntityType.PARROT, SpawnCategory.ANIMAL, 2, 6, Placement.GROUND, Climate.WARM_HUMID,
            Surfaces.FOREST_FLOOR, Light.DAYLIGHT, false, e -> e.summer().parrots()),

    // fall: bats and spiders at night, mooshrooms, foxes, frogs
    BAT(Season.FALL, EntityType.BAT, SpawnCategory.AMBIENT, 1, 3, Placement.AIR, Climate.LAND,
            Surfaces.NATURAL, Light.NIGHT, false, e -> e.fall().bats()),
    SPIDER(Season.FALL, EntityType.SPIDER, SpawnCategory.MONSTER, 1, 1, Placement.GROUND, Climate.LAND,
            Surfaces.NATURAL, Light.DARK_NIGHT, false, e -> e.fall().extraSpiders()),
    CAVE_SPIDER(Season.FALL, EntityType.CAVE_SPIDER, SpawnCategory.MONSTER, 1, 2, Placement.CAVE, Climate.ANY,
            Surfaces.CAVE, Light.DARK, false, e -> e.fall().caveSpiders()),
    RED_FOX(Season.FALL, EntityType.FOX, SpawnCategory.ANIMAL, 1, 1, Placement.GROUND, Climate.WOODLAND,
            Surfaces.FOREST_FLOOR, Light.ANY, false, e -> e.fall().foxes()),
    FROG(Season.FALL, EntityType.FROG, SpawnCategory.ANIMAL, 2, 3, Placement.GROUND, Climate.WETLAND,
            Surfaces.WETLAND, Light.DAYLIGHT, false, e -> e.fall().frogs()),
    MOOSHROOM(Season.FALL, EntityType.MOOSHROOM, SpawnCategory.ANIMAL, 1, 1, Placement.GROUND, Climate.FOREST,
            Surfaces.MUSHROOMY, Light.DAYLIGHT, false, e -> e.fall().mooshrooms()),

    // winter: wolves, snow foxes, polar bears, snow golems
    WOLF(Season.WINTER, EntityType.WOLF, SpawnCategory.ANIMAL, 2, 6, Placement.GROUND, Climate.WOODLAND,
            Surfaces.WINTER, Light.ANY, false, e -> e.winter().wolves()),
    SNOW_FOX(Season.WINTER, EntityType.FOX, SpawnCategory.ANIMAL, 1, 1, Placement.GROUND, Climate.WOODLAND,
            Surfaces.WINTER, Light.ANY, false, e -> e.winter().foxes()),
    POLAR_BEAR(Season.WINTER, EntityType.POLAR_BEAR, SpawnCategory.ANIMAL, 1, 1, Placement.GROUND, Climate.SNOWY,
            Surfaces.SNOWY, Light.ANY, false, e -> e.winter().polarBears()),
    SNOW_GOLEM(Season.WINTER, EntityType.SNOW_GOLEM, SpawnCategory.MISC, 1, 1, Placement.GROUND, Climate.NOT_HOT,
            Surfaces.WINTER, Light.ANY, false, e -> e.winter().snowman());

    /** snow golems have no vanilla limit (category MISC); at most this many seasonal ones per player */
    public static final int SNOW_GOLEM_CAP = 2;

    /** where the kind appears: on the ground, in the air above the ground, in a cave */
    public enum Placement { GROUND, AIR, CAVE }

    /** light of the place: animals in light as in vanilla, bats at night, spiders in darkness */
    public enum Light { ANY, DAYLIGHT, NIGHT, DARK_NIGHT, DARK }

    /**
     * Climate by biome temperature and downfall ({@code Block#getTemperature}, {@code #getHumidity}),
     * so it also works for custom biomes (e.g. Terralith).
     */
    public record Climate(double minTemperature, double maxTemperature, double minHumidity) {
        static final Climate ANY = new Climate(-10, 10, 0);
        /** anywhere on land */
        static final Climate LAND = new Climate(-10, 10, 0);
        /** meadows and forests of the temperate zone: no snow, no desert */
        static final Climate TEMPERATE = new Climate(0.2, 1.0, 0.3);
        /** flowering meadows and forests */
        static final Climate FLOWERY = new Climate(0.5, 1.0, 0.4);
        static final Climate JUNGLE = new Climate(0.9, 1.5, 0.8);
        /** warm humid forests (jungles, forests, swamps) */
        static final Climate WARM_HUMID = new Climate(0.7, 1.5, 0.5);
        /** forests, taiga, snowy taiga */
        static final Climate WOODLAND = new Climate(-1.0, 0.8, 0.3);
        static final Climate FOREST = new Climate(0.3, 1.0, 0.6);
        static final Climate WETLAND = new Climate(0.2, 1.5, 0.8);
        static final Climate SNOWY = new Climate(-10, 0.15, 0);
        /** snow golems melt in biomes with a temperature from 1.0 (desert, savanna, badlands) */
        static final Climate NOT_HOT = new Climate(-10, 0.99, 0);

        public boolean fits(double temperature, double humidity) {
            return temperature >= minTemperature && temperature <= maxTemperature && humidity >= minHumidity;
        }
    }

    /** surfaces on which the kind appears */
    static final class Surfaces {
        static final Set<Material> GRASSY = Set.of(Material.GRASS_BLOCK);
        static final Set<Material> RABBIT = Set.of(Material.GRASS_BLOCK, Material.SAND, Material.SNOW,
                Material.SNOW_BLOCK, Material.COARSE_DIRT);
        static final Set<Material> FOREST_FLOOR = Set.of(Material.GRASS_BLOCK, Material.PODZOL,
                Material.COARSE_DIRT, Material.MOSS_BLOCK);
        static final Set<Material> NATURAL = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
                Material.PODZOL, Material.STONE, Material.SAND, Material.GRAVEL, Material.MOSS_BLOCK,
                Material.MYCELIUM, Material.TERRACOTTA, Material.SNOW, Material.SNOW_BLOCK);
        static final Set<Material> CAVE = Set.of(Material.STONE, Material.DEEPSLATE, Material.TUFF,
                Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.DIRT, Material.GRAVEL,
                Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.OAK_PLANKS);
        static final Set<Material> WETLAND = Set.of(Material.GRASS_BLOCK, Material.MUD, Material.MOSS_BLOCK,
                Material.CLAY, Material.MUDDY_MANGROVE_ROOTS);
        static final Set<Material> MUSHROOMY = Set.of(Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM);
        static final Set<Material> WINTER = Set.of(Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT,
                Material.SNOW, Material.SNOW_BLOCK, Material.DIRT, Material.STONE);
        static final Set<Material> SNOWY = Set.of(Material.SNOW, Material.SNOW_BLOCK, Material.GRASS_BLOCK,
                Material.ICE, Material.PACKED_ICE, Material.STONE);

        private Surfaces() {
        }
    }

    private final Season season;
    private final EntityType type;
    private final SpawnCategory category;
    private final int minGroup;
    private final int maxGroup;
    private final Placement placement;
    private final Climate climate;
    private final Set<Material> surfaces;
    private final Light light;
    private final boolean baby;
    private final Predicate<MainConfig.Entities> enabledBy;

    SeasonalMob(Season season, EntityType type, SpawnCategory category, int minGroup, int maxGroup,
                Placement placement, Climate climate, Set<Material> surfaces, Light light, boolean baby,
                Predicate<MainConfig.Entities> enabledBy) {
        this.season = season;
        this.type = type;
        this.category = category;
        this.minGroup = minGroup;
        this.maxGroup = maxGroup;
        this.placement = placement;
        this.climate = climate;
        this.surfaces = surfaces;
        this.light = light;
        this.baby = baby;
        this.enabledBy = enabledBy;
    }

    public Season season() {
        return season;
    }

    public EntityType type() {
        return type;
    }

    public SpawnCategory category() {
        return category;
    }

    public int minGroup() {
        return minGroup;
    }

    public int maxGroup() {
        return maxGroup;
    }

    public Placement placement() {
        return placement;
    }

    public Climate climate() {
        return climate;
    }

    public Set<Material> surfaces() {
        return surfaces;
    }

    public Light light() {
        return light;
    }

    /** appears as a baby (young farm animals in spring) */
    public boolean baby() {
        return baby;
    }

    /** switch in {@code entities.*} */
    public boolean enabled(MainConfig.Entities entities) {
        return enabledBy.test(entities);
    }

    /** name for {@code /openseasons debug mobs}: {@code cow}, {@code red_fox}, {@code snow_golem} … */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SeasonalMob> byId(String id) {
        String v = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (SeasonalMob m : values()) {
            if (m.id().equals(v)) return Optional.of(m);
        }
        return Optional.empty();
    }
}
