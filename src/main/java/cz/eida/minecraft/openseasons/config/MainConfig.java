/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SubSeason;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Main season settings ({@code config.yml}). */
public record MainConfig(
        boolean modifyBlocks,
        boolean modifyDayNightLength,
        boolean modifyBiomeOnClient,
        Timing timing,
        Cycling cycling,
        Snowman snowman,
        Flora flora,
        Entities entities,
        SubSeasons subSeasons,
        Particles particles,
        String biomeDisplayName,
        NewWorlds newWorlds,
        BlockReplacements blockReplacements) {

    public record Timing(int delayPerOperation, int unloadedChunksCheckDelay, boolean prioritiseTps, double minTps) {
    }

    public record Cycling(boolean automatic, boolean sendMessage) {
    }

    public record Snowman(boolean disableSnowPlacement, boolean wearPumpkin, boolean attackMobs,
                          boolean immuneToWater) {
    }

    public record Flora(boolean keepPlayerPlacedPlants, double flowerChanceInSpring, int maxBerryBushesPerChunk,
                        boolean disableBerryDamageForEntities, boolean generateLeafLitter,
                        boolean destroyPlantsForLeafLitter, List<String> spawnLeafLitterUnder,
                        boolean regrowGrassEverywhere, boolean spawnMushroomsInFall) {
    }

    public record Entities(boolean spawnEntities, double spawnChanceAnimals, boolean despawnSeasonAnimals,
                           SpringEntities spring, SummerEntities summer, FallEntities fall,
                           WinterEntities winter) {
    }

    public record SpringEntities(boolean extraAnimals, boolean extraBees, boolean extraRabbits) {
    }

    public record SummerEntities(boolean ocelots, boolean pandas, boolean parrots, boolean zombiesToHusks) {
    }

    public record FallEntities(boolean bats, boolean frogs, boolean caveSpiders, boolean foxes, boolean mooshrooms,
                               boolean extraSpiders, boolean pumpkins, double pumpkinChance) {
    }

    public record WinterEntities(boolean wolves, boolean foxes, boolean polarBears, boolean snowman,
                                 boolean skeletonsToStrays) {
    }

    /** @param foliageAdjustments foliage tinting during the transition from a season to the next one */
    public record SubSeasons(boolean enabled, Map<Season, ColorAdjustment> foliageAdjustments) {
    }

    public record ColorAdjustment(int rgb, double weight) {
    }

    public record Particles(Chance fallingLeaves, Chance smallFallingLeaves, ShootingStars shootingStars,
                            NightSparks nightSparks, Chance fireflies, Ufo ufo, Aurora aurora, String fallingLeafItem, int fallingLeafCustomModelData) {
    }

    /**
     * Aurora on the northern sky on clear nights.
     *
     * @param chance chance for each night and world, percent
     */
    public record Aurora(boolean enabled, double chance, Set<Season> seasons) {
    }

    /**
     * Occasional fly-over of triangular craft.
     *
     * @param chance          chance of a fly-over per check (once a minute in a world with players), percent
     * @param relativeHeight  flight height above the surface, at most 65 % of the player's view distance
     *                        (otherwise it would vanish in the fog)
     * @param formationChance share of fly-overs in a formation of three craft, percent
     */
    public record Ufo(boolean enabled, double chance, Set<Season> seasons, int relativeHeight,
                      double formationChance) {
    }

    public record Chance(boolean enabled, double chance, Set<Season> seasons) {
    }

    public record ShootingStars(boolean enabled, int relativeHeight, double chance, Set<Season> seasons) {
    }

    public record NightSparks(boolean enabled, int particleCount, int relativeHeight, double eventChance,
                              Set<Season> seasons) {
    }

    public record NewWorlds(boolean syncWithOtherWorlds, Optional<Season> defaultSeason) {
    }

    public record BlockReplacements(boolean enabled, List<BlockReplacement> replacements) {
    }

    public record BlockReplacement(String id, boolean enabled, String toReplace, String replacement,
                                   Set<Season> seasons, Set<SubSeason> subSeasons) {
    }

    /** keys the parser reads from the root of the file; others are reported as unknown */
    static MainConfig parse(ConfigReader r) {
        Timing timing = new Timing(
                r.integer("delay-per-operation", 4),
                r.integer("unloaded-chunks-check-delay", 80),
                r.bool("performance.prioritise-tps", true),
                r.number("performance.min-tps", 19.5));

        Cycling cycling = new Cycling(
                r.bool("automatic-season-cycling", true),
                r.bool("send-message-on-season-cycle", false));

        Snowman snowman = new Snowman(
                r.bool("disable-snow-placement-all-snowman", false),
                r.bool("snowman-wear-pumpkin", false),
                r.bool("snowman-attack-mobs", true),
                r.bool("snowman-immune-to-water", true));

        Flora flora = new Flora(
                r.bool("keep-player-placed-plants", true),
                r.percent("flower-chance-in-spring", 2.0),
                r.integer("max-berrybushes-per-chunk", 10),
                r.bool("disable-berry-damage-for-entities", true),
                r.bool("generate-leaf-litter", true),
                r.bool("destroy-plants-for-leaf-litter", true),
                r.list("spawn-leaf-litter-under", List.of("OAK_LEAVES", "BIRCH_LEAVES", "DARK_OAK_LEAVES")),
                r.bool("regrow-grass-everywhere", true),
                r.bool("spawn-mushrooms-in-fall", true));

        Entities entities = new Entities(
                r.bool("entities.spawn-entities", true),
                r.percent("entities.spawn-chance-animals", 10.0),
                r.bool("entities.despawn-season-animals", true),
                new SpringEntities(
                        r.bool("entities.spring.spawn-extra-animals", true),
                        r.bool("entities.spring.spawn-extra-bees", true),
                        r.bool("entities.spring.spawn-extra-rabbits", true)),
                new SummerEntities(
                        r.bool("entities.summer.spawn-ocelots", false),
                        r.bool("entities.summer.spawn-pandas", false),
                        r.bool("entities.summer.spawn-parrots", true),
                        r.bool("entities.summer.replace-zombies-with-husks", true)),
                new FallEntities(
                        r.bool("entities.fall.spawn-bats", true),
                        r.bool("entities.fall.spawn-frogs", false),
                        r.bool("entities.fall.spawn-cave-spiders", false),
                        r.bool("entities.fall.spawn-foxes", false),
                        r.bool("entities.fall.spawn-mooshrooms", true),
                        r.bool("entities.fall.spawn-extra-spiders", true),
                        r.bool("entities.fall.give-entities-pumpkin", true),
                        r.percent("entities.fall.entities-pumpkin-chance", 22.0)),
                new WinterEntities(
                        r.bool("entities.winter.spawn-wolves", false),
                        r.bool("entities.winter.spawn-foxes", false),
                        r.bool("entities.winter.spawn-polarbears", false),
                        r.bool("entities.winter.spawn-snowman", true),
                        r.bool("entities.winter.replace-skeletons-with-strays", true)));

        Map<Season, ColorAdjustment> adjustments = new EnumMap<>(Season.class);
        for (Season s : Season.values()) {
            String path = "sub-seasons.foliage-color-adjustments." + s.name() + "-" + s.next().name();
            String def = s == Season.SPRING ? "#32CD32,WEIGHT:0.3" : "NONE";
            parseAdjustment(r, path, r.string(path, def)).ifPresent(a -> adjustments.put(s, a));
        }
        SubSeasons subSeasons = new SubSeasons(r.bool("sub-seasons.enabled", true), Map.copyOf(adjustments));

        Particles particles = new Particles(
                chance(r, "particles.FALLING_LEAVES", "spawn-chance", 4.0, EnumSet.of(Season.SUMMER)),
                chance(r, "particles.SMALL_FALLING_LEAVES", "spawn-chance", 25.0, EnumSet.of(Season.FALL)),
                new ShootingStars(
                        r.bool("particles.SHOOTING_STARS.enabled", true),
                        r.integer("particles.SHOOTING_STARS.relative-height", 150),
                        r.percent("particles.SHOOTING_STARS.chance", 1.0),
                        r.seasons("particles.SHOOTING_STARS.seasons", Set.of(Season.SUMMER))),
                new NightSparks(
                        r.bool("particles.NIGHT_SPARKS.enabled", true),
                        r.integer("particles.NIGHT_SPARKS.particle-count", 200),
                        r.integer("particles.NIGHT_SPARKS.relative-height", 40),
                        r.percent("particles.NIGHT_SPARKS.event-chance", 33.0),
                        r.seasons("particles.NIGHT_SPARKS.seasons", Set.of(Season.WINTER))),
                chance(r, "particles.FIREFLIES", "spawn-chance", 5.0, EnumSet.of(Season.SPRING, Season.SUMMER)),
                new Ufo(
                        r.bool("particles.UFO.enabled", true),
                        r.percent("particles.UFO.chance", 1.0),
                        r.seasons("particles.UFO.seasons", Set.of(Season.values())),
                        r.integer("particles.UFO.relative-height", 120),
                        r.percent("particles.UFO.formation-chance", 15.0)),
                new Aurora(
                        r.bool("particles.AURORA.enabled", true),
                        r.percent("particles.AURORA.chance", 20.0),
                        r.seasons("particles.AURORA.seasons", Set.of(Season.WINTER))),
                r.string("falling-leaf-item", "KELP"),
                r.integer("falling-leaf-custommodeldata", 0));

        List<BlockReplacement> replacements = new ArrayList<>();
        String base = "block-replacements.replacements";
        if (r.has(base)) {
            for (String id : r.keys(base)) {
                String p = base + "." + id + ".";
                replacements.add(new BlockReplacement(id,
                        r.bool(p + "enabled", true),
                        r.string(p + "to-replace", ""),
                        r.string(p + "replacement", ""),
                        r.seasons(p + "seasons", Set.of()),
                        r.subSeasons(p + "sub-seasons", Set.of(SubSeason.values()))));
            }
        } else {
            replacements.add(new BlockReplacement("birch-trees", true, "BIRCH_LEAVES", "ACACIA_LEAVES",
                    Set.of(Season.WINTER, Season.FALL), Set.of(SubSeason.values())));
            replacements.add(new BlockReplacement("pink-trees", true, "OAK_LEAVES", "CHERRY_LEAVES",
                    Set.of(Season.SPRING), Set.of(SubSeason.MIDDLE)));
        }

        return new MainConfig(
                r.bool("modify-blocks", true),
                r.bool("modify-daynight-length", true),
                r.bool("modify-biome-on-client", true),
                timing, cycling, snowman, flora, entities, subSeasons, particles,
                r.string("biome-display-name", "openseasons"),
                new NewWorlds(
                        r.bool("on-new-world-creation.sync-with-other-worlds", false),
                        r.optionalSeason("on-new-world-creation.default-season")),
                new BlockReplacements(r.bool("block-replacements.enabled", true), List.copyOf(replacements)));
    }

    private static Chance chance(ConfigReader r, String path, String chanceKey, double def, Set<Season> seasons) {
        return new Chance(
                r.bool(path + ".enabled", true),
                r.percent(path + "." + chanceKey, def),
                r.seasons(path + ".seasons", Set.copyOf(seasons)));
    }

    /** {@code #32CD32,WEIGHT:0.3} or {@code NONE} */
    private static Optional<ColorAdjustment> parseAdjustment(ConfigReader r, String path, String text) {
        if (text.isBlank() || text.trim().equalsIgnoreCase("none")) return Optional.empty();
        String[] parts = text.split(",");
        OptionalInt color = r.parseColor(path, parts[0]);
        if (color.isEmpty()) return Optional.empty();
        double weight = 0.5;
        if (parts.length > 1) {
            String w = parts[1].trim();
            if (w.toUpperCase(Locale.ROOT).startsWith("WEIGHT:")) w = w.substring("WEIGHT:".length());
            try {
                weight = Double.parseDouble(w.trim());
            } catch (NumberFormatException e) {
                r.warn(path, "invalid weight '" + parts[1].trim() + "', using 0.5");
            }
        }
        return Optional.of(new ColorAdjustment(color.getAsInt(), Math.clamp(weight, 0.0, 1.0)));
    }
}
