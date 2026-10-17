/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonSource;
import org.bukkit.World;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Settings from WorldGuard flags. It queries the whole chunk column (a cuboid across
 * the whole world height): every region the chunk intersects applies. Priority, parents and the global region
 * {@code __global__} are resolved by WorldGuard itself (the value of the region with the highest priority; for states
 * deny wins). The query is made as a non-member of the region.
 *
 * <p>The class references WorldGuard and WorldEdit and is only loaded with them.
 */
final class WorldGuardSource implements RegionSource {

    /** id of the temporary cuboid for the query; WorldGuard does not store it anywhere */
    private static final String COLUMN_ID = "__mc215os_column__";

    private final RegionContainer container;
    private final SeasonFlags flags;

    WorldGuardSource(SeasonFlags flags) {
        this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        this.flags = flags;
    }

    @Override
    public RegionSettings query(World world, int chunkX, int chunkZ) {
        RegionManager regions = container.get(BukkitAdapter.adapt(world));
        if (regions == null) return RegionSettings.NONE;
        return query(regions, flags, chunkX, chunkZ, world.getMinHeight(), world.getMaxHeight() - 1);
    }

    @Override
    public long fingerprint(World world) {
        RegionManager regions = container.get(BukkitAdapter.adapt(world));
        return regions == null ? 0 : fingerprint(regions.getRegions(), flags);
    }

    @Override
    public String describe() {
        return "WorldGuard, flags " + String.join(", ", flags.available());
    }

    /** settings of a chunk column from the given regions */
    static RegionSettings query(RegionManager regions, SeasonFlags flags, int chunkX, int chunkZ, int minY, int maxY) {
        int x = chunkX << 4;
        int z = chunkZ << 4;
        ProtectedCuboidRegion column = new ProtectedCuboidRegion(COLUMN_ID, true,
                BlockVector3.at(x, minY, z), BlockVector3.at(x + 15, maxY, z + 15));
        return resolve(regions.getApplicableRegions(column), flags);
    }

    /** flag values from a set of regions by the WorldGuard rules */
    static RegionSettings resolve(ApplicableRegionSet set, SeasonFlags flags) {
        Optional<Season> season = Optional.ofNullable(value(set, flags.permanentSeason))
                .map(v -> v instanceof Enum<?> e ? e.name() : v.toString())
                .flatMap(Season::parse);
        Optional<SeasonSource> source = Optional.ofNullable(value(set, flags.seasonSource))
                .map(v -> v instanceof Enum<?> e ? e.name() : v.toString())
                .flatMap(SeasonSource::parse);
        return new RegionSettings(season, allowed(set, flags.effects), allowed(set, flags.blockChanges), allowed(set, flags.mobSpawning),
                source, periods(set, flags.realPeriods));
    }

    /**
     * Fingerprint: id, priority, parent, dimensions and values of our flags of all regions. The order of the regions
     * does not matter.
     */
    static long fingerprint(Map<String, ProtectedRegion> regions, SeasonFlags flags) {
        long sum = regions.size();
        for (ProtectedRegion r : regions.values()) {
            int h = Objects.hash(r.getId(), r.getPriority(), r.getParent() == null ? null : r.getParent().getId(),
                    r.getMinimumPoint(), r.getMaximumPoint(), r.getPoints(),
                    flag(r, flags.permanentSeason),
                    flag(r, flags.effects), flag(r, flags.blockChanges), flag(r, flags.mobSpawning),
                    flag(r, flags.seasonSource), flag(r, flags.realPeriods));
            sum += mix(h);
        }
        return sum;
    }

    private static Object flag(ProtectedRegion region, Flag<?> flag) {
        return flag == null ? null : region.getFlag(flag);
    }

    private static <V> V value(ApplicableRegionSet set, Flag<V> flag) {
        return flag == null ? null : set.queryValue(null, flag);
    }

    /** ids of the periods from {@code real-periods} of the region with the highest priority (lower case) */
    private static Set<String> periods(ApplicableRegionSet set, Flag<?> flag) {
        Object v = value(set, flag);
        if (!(v instanceof Collection<?> c)) return Set.of();
        Set<String> out = new TreeSet<>();
        for (Object o : c) if (o != null && !o.toString().isBlank()) out.add(o.toString().trim().toLowerCase(Locale.ROOT));
        return out;
    }

    /** without the flag or without a value allowed, only deny forbids */
    private static boolean allowed(ApplicableRegionSet set, StateFlag flag) {
        return flag == null || set.queryState(null, flag) != StateFlag.State.DENY;
    }

    /** bit mixing (SplitMix64), so that the sum of fingerprints does not cancel similar values */
    private static long mix(long v) {
        long z = v + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
