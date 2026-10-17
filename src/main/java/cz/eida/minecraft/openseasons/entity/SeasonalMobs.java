/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.entity;

import cz.eida.minecraft.openseasons.api.event.SeasonChangeEvent;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.effects.EffectRules;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Seasonal animals and monsters:
 *
 * <ul>
 *   <li>in a chunk a player "sees" for the first time in this season (2–8 chunks away), with the chance
 *       {@code spawn-chance-animals} a group of a kind the season adds and that fits the place appears
 *       (climate, surface, light); at most one group per chunk, in the surrounding 3 × 3 chunks one seasonal
 *       creature per chunk on average and within the vanilla category limit near the player ({@link MobRules});</li>
 *   <li>natural spawn: in summer zombie → husk, in winter skeleton → stray, in fall a pumpkin on the head
 *       (no drop);</li>
 *   <li>seasonal creatures carry their season in the PDC ({@code openseasons:season_mob}) and the tag {@link #TAG};
 *       when the season ends they despawn (right after the change, when a chunk loads and by a check every 30 s), unless
 *       they are named, leashed, tamed, riding something or carrying someone, and bees with a hive;</li>
 *   <li>seasonal snow golems: water resistance, pumpkin, attacking monsters; snow placement by all snow golems.</li>
 * </ul>
 *
 * The season at a place comes from {@link RegionService} ({@code permanent-season} of a region, otherwise the season of the world),
 * the flag {@code season-mob-spawning deny} turns off seasonal spawns, replacements and pumpkins. Spawns go through
 * {@code SpawnReason.NATURAL}, so area protections (WorldGuard {@code mob-spawning}) stop them the same way
 * as vanilla spawns. Main thread only.
 */
public final class SeasonalMobs implements Listener {

    /** tag of seasonal creatures for manual cleanup: {@code /kill @e[tag=mc215os_season_mob]} */
    public static final String TAG = "mc215os_season_mob";

    static final int SPAWN_PERIOD = 40;
    static final int SWEEP_PERIOD = 600;
    /** how many newly seen chunks one player processes per cycle (spreads the rush after joining) */
    static final int MAX_NEW_CHUNKS = 24;
    /** the vanilla category limit applies within 128 blocks of the player */
    static final double CAP_RADIUS = 128;
    /** vanilla spawn at least 24 blocks from the player */
    static final double MIN_PLAYER_DISTANCE = 24;
    static final int VISITED_LIMIT = 200_000;

    private final Plugin plugin;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    private final RegionService regions;
    private final NamespacedKey seasonKey;
    private final NamespacedKey pumpkinKey;
    private final NamespacedKey replacedKey;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final List<int[]> ring = new ArrayList<>(MobRules.ring());
    private final Map<String, Visited> visited = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean replacing;
    /** during a manual test of a natural spawn: capture the replacement */
    private boolean capture;
    private Entity lastReplacement;
    private final Totals totals = new Totals();

    /** chunks players have already "seen" in the current season of the world */
    private static final class Visited {
        Season season;
        final Set<Long> chunks = new HashSet<>();
    }

    /** counts since the plugin start */
    public static final class Totals {
        int groups;
        int mobs;
        int replaced;
        int pumpkins;
        int despawned;

        public int groups() {
            return groups;
        }

        public int mobs() {
            return mobs;
        }

        public int replaced() {
            return replaced;
        }

        public int pumpkins() {
            return pumpkins;
        }

        public int despawned() {
            return despawned;
        }
    }

    /** result of one pass through the chunks around a player */
    public static final class Cycle {
        int chunks;
        int rolled;
        int groups;
        int mobs;
        int noRoom;
        int capped;
        int noHabitat;
        int blocked;
        int region;

        @Override
        public String toString() {
            return "chunks " + chunks + ", successful rolls " + rolled + ", groups " + groups + " (" + mobs
                    + " creatures); no room (density) " + noRoom + ", over the limit " + capped + ", unsuitable place "
                    + noHabitat + ", stopped by another plugin " + blocked + ", region without seasonal spawns " + region;
        }
    }

    public SeasonalMobs(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons, RegionService regions) {
        this.plugin = plugin;
        this.config = config;
        this.seasons = seasons;
        this.regions = regions;
        this.seasonKey = new NamespacedKey(plugin, "season_mob");
        this.pumpkinKey = new NamespacedKey(plugin, "season_pumpkin");
        this.replacedKey = new NamespacedKey(plugin, "season_replaced");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnTick, SPAWN_PERIOD, SPAWN_PERIOD));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweepAll, 100L, SWEEP_PERIOD));
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        visited.clear();
    }

    public Totals totals() {
        return totals;
    }

    // --- tags ---

    /** season the seasonal creature belongs to; empty for others */
    public Optional<Season> seasonOf(Entity entity) {
        String value = entity.getPersistentDataContainer().get(seasonKey, PersistentDataType.STRING);
        return value == null ? Optional.empty() : Season.parse(value);
    }

    public boolean hasPumpkin(Entity entity) {
        return entity.getPersistentDataContainer().has(pumpkinKey, PersistentDataType.BYTE);
    }

    public boolean isReplacement(Entity entity) {
        return entity.getPersistentDataContainer().has(replacedKey, PersistentDataType.STRING);
    }

    private void mark(Entity entity, Season season) {
        entity.getPersistentDataContainer().set(seasonKey, PersistentDataType.STRING, season.name());
        entity.addScoreboardTag(TAG);
    }

    /** season of the whole world (period, real season, game calendar), only when seasons are enabled */
    Optional<Season> activeSeason(World world) {
        return regions.worldLocal(world).map(RegionService.Local::season);
    }

    /** season at a place: {@code permanent-season} of a region, otherwise the season of the world; empty without seasons */
    Optional<Season> seasonAt(Location location) {
        return regions.seasonAt(location);
    }

    /** settings {@code entities.*} */
    public MainConfig.Entities entitiesConfig() {
        return config.get().main().entities();
    }

    // --- seasonal spawns around players ---

    private void spawnTick() {
        MainConfig.Entities entities = entitiesConfig();
        if (!entities.spawnEntities()) return;
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            Optional<Season> season = activeSeason(world);
            if (season.isEmpty()) continue;
            Map<Season, List<SeasonalMob>> species = speciesFor(world, entities);
            Set<Long> seen = visited(world, season.get());
            for (Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
                scan(world, player.getLocation(), species, seen, entities.spawnChanceAnimals(), MAX_NEW_CHUNKS,
                        new Cycle());
            }
        }
    }

    /**
     * Manual pass through the chunks around a place: every loaded chunk in the radius gets a roll at 100 %, even if it was
     * already seen in this season; density, limits and place apply as otherwise.
     */
    public Cycle debugCycle(Location anchor) {
        World world = anchor.getWorld();
        Cycle cycle = new Cycle();
        if (activeSeason(world).isEmpty()) return cycle;
        scan(world, anchor, speciesFor(world, entitiesConfig()), null, 100.0, Integer.MAX_VALUE, cycle);
        return cycle;
    }

    /** kinds of each season the world allows (the season of a chunk may come from a region) */
    private static Map<Season, List<SeasonalMob>> speciesFor(World world, MainConfig.Entities entities) {
        Map<Season, List<SeasonalMob>> out = new EnumMap<>(Season.class);
        for (Season s : Season.values()) out.put(s, allowedIn(world, MobRules.enabled(s, entities)));
        return out;
    }

    /** kinds the world allows: gamerule {@code spawn_mobs}, monsters only outside peaceful difficulty */
    private static List<SeasonalMob> allowedIn(World world, List<SeasonalMob> species) {
        if (Boolean.FALSE.equals(world.getGameRuleValue(GameRules.SPAWN_MOBS))) return List.of();
        boolean monsters = world.getAllowMonsters() && world.getDifficulty() != Difficulty.PEACEFUL
                && !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.SPAWN_MONSTERS));
        boolean animals = world.getAllowAnimals();
        return species.stream().filter(m -> m.category() == SpawnCategory.MONSTER ? monsters : animals).toList();
    }

    private Set<Long> visited(World world, Season season) {
        Visited v = visited.computeIfAbsent(world.getName(), k -> new Visited());
        if (v.season != season || v.chunks.size() > VISITED_LIMIT) {
            v.season = season;
            v.chunks.clear();
        }
        return v.chunks;
    }

    private void scan(World world, Location anchor, Map<Season, List<SeasonalMob>> species, Set<Long> seen,
                      double chance, int maxNew, Cycle cycle) {
        if (species.values().stream().allMatch(List::isEmpty)) return;
        Collections.shuffle(ring, random);
        int cx0 = anchor.getBlockX() >> 4;
        int cz0 = anchor.getBlockZ() >> 4;
        Caps caps = new Caps(anchor);
        int fresh = 0;
        for (int[] d : ring) {
            if (fresh >= maxNew) break;
            int cx = cx0 + d[0];
            int cz = cz0 + d[1];
            long key = MobRules.chunkKey(cx, cz);
            if (seen != null && seen.contains(key)) continue;
            if (!world.isChunkLoaded(cx, cz)) continue;
            Chunk chunk = world.getChunkAt(cx, cz);
            if (chunk.getLoadLevel() != Chunk.LoadLevel.ENTITY_TICKING) continue;
            if (seen != null) seen.add(key);
            fresh++;
            cycle.chunks++;
            if (!MobRules.roll(random, chance)) continue;
            cycle.rolled++;
            Optional<Season> season = regions.seasonAt(world, cx, cz);
            if (season.isEmpty() || species.get(season.get()).isEmpty()) continue;
            if (!regions.mobSpawningAllowed(world, cx, cz)) {
                cycle.region++;
                continue;
            }
            spawnInChunk(world, chunk, species.get(season.get()), season.get(), caps, cycle);
        }
    }

    private void spawnInChunk(World world, Chunk chunk, List<SeasonalMob> species, Season season, Caps caps,
                              Cycle cycle) {
        if (!MobRules.roomForGroup(seasonalIn(chunk), seasonalAround(world, chunk))) {
            cycle.noRoom++;
            return;
        }
        boolean night = EffectRules.night(world.getTime());
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Map<SeasonalMob.Placement, Optional<Place>> places = new EnumMap<>(SeasonalMob.Placement.class);
            List<Place> fitting = new ArrayList<>();
            List<SeasonalMob> fittingMobs = new ArrayList<>();
            for (SeasonalMob mob : species) {
                Optional<Place> place = places.computeIfAbsent(mob.placement(), p -> place(world, x, z, p, night));
                if (place.isPresent() && MobRules.fits(mob, place.get().spot(mob))) {
                    fitting.add(place.get());
                    fittingMobs.add(mob);
                }
            }
            if (fitting.isEmpty()) continue;
            int pick = random.nextInt(fitting.size());
            SeasonalMob mob = fittingMobs.get(pick);
            Location at = fitting.get(pick).feet();
            if (!farFromPlayers(at)) continue;
            int count = MobRules.allowedByCap(MobRules.groupSize(mob, random), caps.count(mob), caps.limit(mob));
            if (count == 0) {
                cycle.capped++;
                return;
            }
            int made = spawnGroup(mob, at, count, season);
            if (made == 0) {
                cycle.blocked++;
                return;
            }
            caps.add(mob, made);
            cycle.groups++;
            cycle.mobs += made;
            return;
        }
        cycle.noHabitat++;
    }

    private boolean farFromPlayers(Location at) {
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE) return false;
        }
        return true;
    }

    private int seasonalIn(Chunk chunk) {
        int n = 0;
        for (Entity e : chunk.getEntities()) {
            if (seasonOf(e).isPresent()) n++;
        }
        return n;
    }

    private int seasonalAround(World world, Chunk center) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                if (world.isChunkLoaded(cx, cz)) n += seasonalIn(world.getChunkAt(cx, cz));
            }
        }
        return n;
    }

    /** category limits around the player, counted at most once per pass */
    private final class Caps {
        private final Location anchor;
        private final Map<SpawnCategory, Integer> counts = new EnumMap<>(SpawnCategory.class);
        private int golems = -1;

        Caps(Location anchor) {
            this.anchor = anchor;
        }

        int count(SeasonalMob mob) {
            if (mob == SeasonalMob.SNOW_GOLEM) {
                if (golems < 0) {
                    golems = nearby(anchor, e -> e.getType() == EntityType.SNOW_GOLEM && seasonOf(e).isPresent());
                }
                return golems;
            }
            return counts.computeIfAbsent(mob.category(), cat -> nearby(anchor, e -> e.getSpawnCategory() == cat
                    && MobRules.countsTowardCap(cat, seasonOf(e).isPresent(),
                    e instanceof LivingEntity le && le.getRemoveWhenFarAway())));
        }

        int limit(SeasonalMob mob) {
            if (mob == SeasonalMob.SNOW_GOLEM) return SeasonalMob.SNOW_GOLEM_CAP;
            return anchor.getWorld().getSpawnLimit(mob.category());
        }

        void add(SeasonalMob mob, int n) {
            if (mob == SeasonalMob.SNOW_GOLEM) golems = count(mob) + n;
            else counts.merge(mob.category(), n, Integer::sum);
        }
    }

    private static int nearby(Location anchor, java.util.function.Predicate<Entity> filter) {
        World w = anchor.getWorld();
        double height = w.getMaxHeight() - w.getMinHeight();
        return w.getNearbyEntities(anchor, CAP_RADIUS, height, CAP_RADIUS, filter).size();
    }

    /** place in the column x, z for the given placement */
    private record Place(Block ground, Location feet, boolean free, boolean night) {
        MobRules.Spot spot(SeasonalMob mob) {
            Block at = feet.getBlock();
            boolean water = mob == SeasonalMob.FROG && waterNear(ground);
            return new MobRules.Spot(ground.getType(), free, at.getTemperature(), at.getHumidity(),
                    at.getLightFromSky(), at.getLightFromBlocks(), night, water);
        }
    }

    private Optional<Place> place(World world, int x, int z, SeasonalMob.Placement placement, boolean night) {
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return switch (placement) {
            case GROUND -> {
                Block feet = ground.getRelative(BlockFace.UP);
                yield Optional.of(new Place(ground, feet.getLocation().add(0.5, 0, 0.5),
                        free(feet) && free(feet.getRelative(BlockFace.UP)), night));
            }
            case AIR -> {
                Block feet = ground.getRelative(BlockFace.UP, 2 + random.nextInt(3));
                boolean free = free(ground.getRelative(BlockFace.UP)) && free(feet) && free(feet.getRelative(BlockFace.UP));
                yield Optional.of(new Place(ground, feet.getLocation().add(0.5, 0.2, 0.5), free, night));
            }
            case CAVE -> cave(world, x, z, ground.getY(), night);
        };
    }

    /** air above solid ground deep below the surface */
    private Optional<Place> cave(World world, int x, int z, int surface, boolean night) {
        int low = world.getMinHeight() + 4;
        int high = surface - 8;
        if (high <= low) return Optional.empty();
        int y = low + random.nextInt(high - low);
        for (int i = 0; i < 24 && y > low; i++, y--) {
            Block feet = world.getBlockAt(x, y, z);
            Block below = feet.getRelative(BlockFace.DOWN);
            if (free(feet) && free(feet.getRelative(BlockFace.UP)) && !free(below) && !below.isLiquid()) {
                return Optional.of(new Place(below, feet.getLocation().add(0.5, 0, 0.5), true, night));
            }
        }
        return Optional.empty();
    }

    private static boolean free(Block block) {
        return block.isPassable() && !block.isLiquid();
    }

    private static boolean waterNear(Block ground) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    if (ground.getRelative(dx, dy, dz).getType() == Material.WATER) return true;
                }
            }
        }
        return false;
    }

    /** a group around a place; returns how many creatures really appeared (another plugin may cancel the spawn) */
    private int spawnGroup(SeasonalMob mob, Location base, int count, Season season) {
        int made = 0;
        for (int i = 0; i < count; i++) {
            Location at = i == 0 ? base : near(mob, base);
            Entity e = base.getWorld().spawnEntity(at, mob.type(), CreatureSpawnEvent.SpawnReason.NATURAL,
                    prepare(mob, season));
            if (e.isValid()) made++;
        }
        if (made > 0) {
            totals.groups++;
            totals.mobs += made;
        }
        return made;
    }

    /** place for the next group member within 2 blocks of the first, otherwise the same place */
    private Location near(SeasonalMob mob, Location base) {
        if (mob.placement() == SeasonalMob.Placement.CAVE) return base;
        World w = base.getWorld();
        int x = base.getBlockX() + random.nextInt(5) - 2;
        int z = base.getBlockZ() + random.nextInt(5) - 2;
        if (mob.placement() == SeasonalMob.Placement.AIR) {
            Block b = w.getBlockAt(x, base.getBlockY(), z);
            return free(b) ? b.getLocation().add(0.5, 0.2, 0.5) : base;
        }
        Block ground = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block feet = ground.getRelative(BlockFace.UP);
        if (Math.abs(feet.getY() - base.getBlockY()) > 2 || !free(feet) || !free(feet.getRelative(BlockFace.UP))
                || ground.isLiquid()) {
            return base;
        }
        return feet.getLocation().add(0.5, 0, 0.5);
    }

    private Consumer<Entity> prepare(SeasonalMob mob, Season season) {
        boolean pumpkinOnSnowman = config.get().main().snowman().wearPumpkin();
        return e -> {
            mark(e, season);
            if (mob.baby() && e instanceof Ageable a) a.setBaby();
            if (e instanceof Fox fox) fox.setFoxType(mob == SeasonalMob.SNOW_FOX ? Fox.Type.SNOW : Fox.Type.RED);
            if (e instanceof Snowman snowman) snowman.setDerp(!pumpkinOnSnowman);
            if (e instanceof Bat bat) bat.setAwake(true);
            // animals and snow golems stay until the end of the season; bats and spiders despawn like vanilla
            if (e instanceof LivingEntity le && mob.category() != SpawnCategory.MONSTER
                    && mob.category() != SpawnCategory.AMBIENT) {
                le.setRemoveWhenFarAway(false);
            }
        };
    }

    // --- manual triggering (/openseasons debug mobs) ---

    /** result of a manual spawn */
    public record DebugSpawn(SeasonalMob mob, int count, Location at, boolean habitat) {
    }

    /**
     * A group of a kind near a place (6–16 blocks, loads chunks), without chance, density and limits. Looks for a place the kind fits;
     * when there is none and {@code anywhere}, puts it on any free place ({@code habitat = false}). The creatures belong to
     * {@code season} (the current season of the world, so that the check does not remove them right away).
     */
    public Optional<DebugSpawn> debugSpawn(Location anchor, SeasonalMob mob, Season season, boolean anywhere) {
        World world = anchor.getWorld();
        boolean night = EffectRules.night(world.getTime());
        Location fallback = null;
        for (int i = 0; i < 48; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 6 + random.nextDouble() * 10;
            int x = (int) Math.floor(anchor.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(anchor.getZ() + Math.sin(angle) * dist);
            world.getChunkAt(x >> 4, z >> 4);
            Optional<Place> place = place(world, x, z, mob.placement(), night);
            if (place.isEmpty() || !place.get().free()) continue;
            if (MobRules.fits(mob, place.get().spot(mob))) {
                return Optional.of(new DebugSpawn(mob, spawnGroup(mob, place.get().feet(), MobRules.groupSize(mob, random),
                        season), place.get().feet(), true));
            }
            if (fallback == null && !place.get().ground().isLiquid()) fallback = place.get().feet();
        }
        if (fallback == null || !anywhere) return Optional.empty();
        return Optional.of(new DebugSpawn(mob, spawnGroup(mob, fallback, MobRules.groupSize(mob, random), season),
                fallback, false));
    }

    /**
     * A natural spawn for testing: the creature goes through the same rules as a vanilla spawn (replacement, pumpkin).
     * Returns the creature that really is in the world (the replacement), or empty when someone cancelled the spawn.
     */
    public Optional<Entity> debugNatural(Location at, EntityType type, boolean forcePumpkin) {
        lastReplacement = null;
        capture = true;
        Entity e;
        try {
            e = at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.NATURAL);
        } finally {
            capture = false;
        }
        Entity result = e.isValid() ? e : lastReplacement;
        lastReplacement = null;
        if (result == null || !result.isValid()) return Optional.empty();
        if (forcePumpkin && result instanceof Mob mob && !hasPumpkin(mob)) putPumpkin(mob);
        return Optional.of(result);
    }

    /** seasonal creatures in loaded chunks of the world: season → kind → count */
    public Map<Season, Map<EntityType, Integer>> counts(World world) {
        Map<Season, Map<EntityType, Integer>> out = new EnumMap<>(Season.class);
        for (LivingEntity e : world.getLivingEntities()) {
            seasonOf(e).ifPresent(s -> out.computeIfAbsent(s, k -> new TreeMap<>()).merge(e.getType(), 1, Integer::sum));
        }
        return out;
    }

    /** creatures with a pumpkin and replacements (husk, stray) in loaded chunks */
    public int[] decorated(World world) {
        int pumpkins = 0;
        int replaced = 0;
        for (LivingEntity e : world.getLivingEntities()) {
            if (hasPumpkin(e)) pumpkins++;
            if (isReplacement(e)) replaced++;
        }
        return new int[]{pumpkins, replaced};
    }

    public int visitedChunks(World world) {
        Visited v = visited.get(world.getName());
        return v == null ? 0 : v.chunks.size();
    }

    // --- despawning after the end of a season ---

    private void sweepAll() {
        for (World world : plugin.getServer().getWorlds()) sweep(world, false);
    }

    /**
     * Removes seasonal creatures whose season in the world has ended ({@code all}: all seasonal creatures without
     * protection, even with {@code despawn-season-animals} disabled). Returns the count.
     */
    public int sweep(World world, boolean all) {
        if (activeSeason(world).isEmpty() && !all) return 0;
        boolean enabled = entitiesConfig().despawnSeasonAnimals();
        int removed = 0;
        for (LivingEntity e : world.getLivingEntities()) {
            if (shouldRemove(e, enabled, all)) {
                remove(e);
                removed++;
            }
        }
        return removed;
    }

    /** the season is compared with the season at the creature's place (a region with {@code permanent-season} keeps it) */
    private boolean shouldRemove(Entity e, boolean enabled, boolean all) {
        Optional<Season> tagged = seasonOf(e);
        if (tagged.isEmpty() || !e.isValid()) return false;
        MobRules.Keep keep = keep(e);
        if (all) return !keep.any();
        Optional<Season> current = seasonAt(e.getLocation());
        return current.isPresent() && MobRules.despawn(tagged.get(), current.get(), keep, enabled);
    }

    private static MobRules.Keep keep(Entity e) {
        boolean named = e.customName() != null;
        boolean leashed = e instanceof LivingEntity le && le.isLeashed();
        boolean tamed = e instanceof Tameable t && t.isTamed();
        boolean riding = e.isInsideVehicle() || !e.getPassengers().isEmpty();
        boolean hive = e instanceof Bee bee && bee.getHive() != null;
        return new MobRules.Keep(named, leashed, tamed, riding, hive);
    }

    private void remove(Entity e) {
        Location at = e.getLocation().add(0, 0.5, 0);
        e.getWorld().spawnParticle(Particle.POOF, at, 6, 0.3, 0.3, 0.3, 0.02);
        e.remove();
        totals.despawned++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSeasonChange(SeasonChangeEvent event) {
        World world = event.getWorld();
        // another listener may still cancel the change, so only in the next tick by the real state
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int removed = sweep(world, false);
            if (removed > 0) {
                plugin.getLogger().info("World " + world.getName() + ": after the end of the season " + removed
                        + " seasonal animals and monsters despawned.");
            }
        });
    }

    /** seasonal creatures from saved chunks whose season has ended in the meantime */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> tagged = new ArrayList<>();
        for (Entity e : event.getEntities()) {
            if (seasonOf(e).isPresent()) tagged.add(e);
        }
        if (tagged.isEmpty()) return;
        World world = event.getWorld();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (activeSeason(world).isEmpty()) return;
            boolean enabled = entitiesConfig().despawnSeasonAnimals();
            int removed = 0;
            for (Entity e : tagged) {
                if (shouldRemove(e, enabled, false)) {
                    remove(e);
                    removed++;
                }
            }
            if (removed > 0) {
                plugin.getLogger().info("World " + world.getName() + ": in loaded chunk " + event.getChunk().getX()
                        + ", " + event.getChunk().getZ() + " " + removed + " seasonal animals and monsters of a past season despawned.");
            }
        });
    }

    // --- natural spawn: replacements and pumpkins ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity entity = event.getEntity();
        if (seasonOf(entity).isPresent()) return;
        Optional<Season> season = seasonAt(entity.getLocation());
        if (season.isEmpty() || !regions.mobSpawningAllowed(entity.getLocation())) return;
        MainConfig.Entities entities = entitiesConfig();
        Optional<EntityType> to = MobRules.replacement(entity.getType(), season.get(), entities);
        if (to.isPresent() && !replacing && !entity.isInsideVehicle() && entity.getPassengers().isEmpty()) {
            event.setCancelled(true);
            replace(entity, to.get(), season.get());
            return;
        }
        if (entity instanceof Mob mob) {
            EntityEquipment equipment = mob.getEquipment();
            boolean empty = equipment.getHelmet().isEmpty();
            if (MobRules.pumpkinCandidate(mob.getType(), season.get(), entities, empty)
                    && MobRules.roll(random, entities.fall().pumpkinChance())) {
                putPumpkin(mob);
            }
        }
    }

    private void replace(LivingEntity original, EntityType to, Season season) {
        boolean baby = original instanceof Ageable a && !a.isAdult();
        replacing = true;
        try {
            Entity replacement = original.getWorld().spawnEntity(original.getLocation(), to,
                    CreatureSpawnEvent.SpawnReason.NATURAL, e -> {
                        e.getPersistentDataContainer().set(replacedKey, PersistentDataType.STRING, season.name());
                        if (e instanceof Ageable a) {
                            if (baby) a.setBaby();
                            else a.setAdult();
                        }
                    });
            if (replacement.isValid()) totals.replaced++;
            if (capture) lastReplacement = replacement;
        } finally {
            replacing = false;
        }
    }

    /** pumpkin or jack o'lantern on the head, does not drop ({@code openseasons:season_pumpkin}) */
    public void putPumpkin(Mob mob) {
        EntityEquipment equipment = mob.getEquipment();
        Material head = random.nextInt(4) == 0 ? Material.JACK_O_LANTERN : Material.CARVED_PUMPKIN;
        equipment.setHelmet(new ItemStack(head));
        equipment.setHelmetDropChance(0f);
        mob.getPersistentDataContainer().set(pumpkinKey, PersistentDataType.BYTE, (byte) 1);
        totals.pumpkins++;
    }

    // --- snow golems ---

    /** a seasonal snow golem is not hurt in water or rain ({@code snowman-immune-to-water}) */
    @EventHandler(ignoreCancelled = true)
    public void onSnowmanDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Snowman snowman && event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                && config.get().main().snowman().immuneToWater() && seasonOf(snowman).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** a seasonal snow golem without {@code snowman-attack-mobs} does not target monsters nor throw snowballs */
    @EventHandler(ignoreCancelled = true)
    public void onSnowmanTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Snowman snowman && !config.get().main().snowman().attackMobs()
                && seasonOf(snowman).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSnowball(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Snowman snowman && !config.get().main().snowman().attackMobs()
                && seasonOf(snowman).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** {@code disable-snow-placement-all-snowman}: no snow golem (not even one built by a player) places snow */
    @EventHandler(ignoreCancelled = true)
    public void onSnowTrail(EntityBlockFormEvent event) {
        if (event.getEntity() instanceof Snowman && config.get().main().snowman().disableSnowPlacement()) {
            event.setCancelled(true);
        }
    }
}
