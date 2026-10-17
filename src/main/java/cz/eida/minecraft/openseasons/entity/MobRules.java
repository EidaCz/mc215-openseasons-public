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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Rules of seasonal animals and monsters without a server: which kinds a season adds, replacements on a natural
 * spawn (zombie → husk in summer, skeleton → stray in winter), who gets a pumpkin on its head, capacity
 * and when a seasonal creature despawns.
 *
 * <p>A switch set to {@code false} only turns off the seasonal addition; vanilla spawns stay (there is no
 * blocking of vanilla spawns, only lists of what appears in addition).
 */
public final class MobRules {

    /** in a chunk that already has a seasonal creature no further group appears (one group per chunk) */
    static final int MAX_IN_CHUNK = 0;
    /** in the surrounding 3 × 3 chunks at most one seasonal creature per chunk on average */
    static final int MAX_IN_AREA = 9;
    /** chunk ring around the player where seasonal creatures appear: 2–8 chunks (32–128 blocks) */
    static final int RING_MIN = 2;
    static final int RING_MAX = 8;

    /** humanoids on which a pumpkin on the head is visible */
    static final Set<EntityType> PUMPKIN_WEARERS = Set.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.ZOMBIE_VILLAGER, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED,
            EntityType.PARCHED);

    private MobRules() {
    }

    /** kinds a season adds by {@code entities.*}; empty with {@code spawn-entities: false} */
    public static List<SeasonalMob> enabled(Season season, MainConfig.Entities entities) {
        if (!entities.spawnEntities()) return List.of();
        List<SeasonalMob> out = new ArrayList<>();
        for (SeasonalMob m : SeasonalMob.values()) {
            if (m.season() == season && m.enabled(entities)) out.add(m);
        }
        return List.copyOf(out);
    }

    /** all kinds of a season regardless of the switches (manual triggering) */
    public static List<SeasonalMob> all(Season season) {
        List<SeasonalMob> out = new ArrayList<>();
        for (SeasonalMob m : SeasonalMob.values()) {
            if (m.season() == season) out.add(m);
        }
        return List.copyOf(out);
    }

    /**
     * Replacement of a naturally spawned creature: in summer zombie → husk ({@code replace-zombies-with-husks}),
     * in winter skeleton → stray ({@code replace-skeletons-with-strays}). Applies to all natural
     * spawns in a world with seasons.
     */
    public static Optional<EntityType> replacement(EntityType type, Season season, MainConfig.Entities entities) {
        if (season == Season.SUMMER && type == EntityType.ZOMBIE && entities.summer().zombiesToHusks()) {
            return Optional.of(EntityType.HUSK);
        }
        if (season == Season.WINTER && type == EntityType.SKELETON && entities.winter().skeletonsToStrays()) {
            return Optional.of(EntityType.STRAY);
        }
        return Optional.empty();
    }

    /** in fall a naturally spawned humanoid with a free head gets a pumpkin with the chance {@code entities-pumpkin-chance} */
    public static boolean pumpkinCandidate(EntityType type, Season season, MainConfig.Entities entities,
                                           boolean helmetEmpty) {
        return season == Season.FALL && entities.fall().pumpkins() && helmetEmpty && PUMPKIN_WEARERS.contains(type);
    }

    /** dice roll: true with a probability of {@code percent} % */
    public static boolean roll(RandomGenerator random, double percent) {
        return random.nextDouble() * 100.0 < percent;
    }

    /** group size by kind (bees, rabbits, parrots, wolves 2–6; foxes, bears, snow golems 1) */
    public static int groupSize(SeasonalMob mob, RandomGenerator random) {
        return mob.minGroup() + random.nextInt(mob.maxGroup() - mob.minGroup() + 1);
    }

    /**
     * Who counts towards the category limit near the player. Seasonal creatures always. Others as in vanilla, only when
     * they can despawn, and only for monsters, bats and other categories where the vanilla limit really fluctuates.
     * Animals of the world do not count towards the limit of seasonal animals: in vanilla they do not despawn, so the vanilla
     * animal limit (10) is permanently full in an inhabited area and seasonal animals would never appear
     * (counting only the density would not prevent that). So there are at most as many seasonal animals near a player as the limit.
     */
    public static boolean countsTowardCap(SpawnCategory category, boolean seasonal, boolean canDespawn) {
        if (seasonal) return true;
        return category != SpawnCategory.ANIMAL && canDespawn;
    }

    /** how many creatures of a group may appear given the category limit near the player; 0 = none */
    public static int allowedByCap(int wanted, int counted, int limit) {
        if (limit < 0) return wanted;
        return Math.max(0, Math.min(wanted, limit - counted));
    }

    /** one group per chunk and at most one seasonal creature per chunk on average in the surrounding 3 × 3 chunks */
    public static boolean roomForGroup(int seasonalInChunk, int seasonalInArea) {
        return seasonalInChunk <= MAX_IN_CHUNK && seasonalInArea < MAX_IN_AREA;
    }

    /** circumstances that keep a seasonal creature from despawning */
    public record Keep(boolean named, boolean leashed, boolean tamed, boolean riding, boolean beeWithHive) {
        public static final Keep NONE = new Keep(false, false, false, false, false);

        boolean any() {
            return named || leashed || tamed || riding || beeWithHive;
        }
    }

    /**
     * A seasonal creature despawns when its season has ended (the world has another season) and {@code despawn-season-animals}
     * is enabled. Named ones stay, as do leashed, tamed ones, ones riding something or carrying a rider,
     * and a bee that found a hive.
     */
    public static boolean despawn(Season taggedSeason, Season current, Keep keep, boolean despawnEnabled) {
        return despawnEnabled && taggedSeason != current && !keep.any();
    }

    /** place for a seasonal creature: surface, free space above it, climate and light */
    public record Spot(Material ground, boolean free, double temperature, double humidity,
                       int skyLight, int blockLight, boolean night, boolean waterNearby) {
    }

    /** the kind fits the place: surface, climate, light (frogs need water nearby) */
    public static boolean fits(SeasonalMob mob, Spot spot) {
        if (!spot.free() || !mob.surfaces().contains(spot.ground())) return false;
        if (!mob.climate().fits(spot.temperature(), spot.humidity())) return false;
        if (mob == SeasonalMob.FROG && !spot.waterNearby()) return false;
        return switch (mob.light()) {
            case ANY -> true;
            // vanilla animals: brightness of the place above 8 (regardless of the time of day)
            case DAYLIGHT -> spot.skyLight() > 8 || spot.blockLight() > 8;
            case NIGHT -> spot.night();
            // spiders like vanilla monsters: at night and without block light
            case DARK_NIGHT -> spot.night() && spot.blockLight() == 0;
            case DARK -> spot.skyLight() == 0 && spot.blockLight() == 0;
        };
    }

    /** chunks in the ring {@link #RING_MIN}–{@link #RING_MAX} around the player as offsets {dx, dz} */
    public static List<int[]> ring() {
        List<int[]> out = new ArrayList<>();
        for (int dx = -RING_MAX; dx <= RING_MAX; dx++) {
            for (int dz = -RING_MAX; dz <= RING_MAX; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) >= RING_MIN) out.add(new int[]{dx, dz});
            }
        }
        return List.copyOf(out);
    }

    /** chunk key like {@code Chunk#getChunkKey} */
    public static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }
}
