/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.flora;

import cz.eida.minecraft.openseasons.season.Season;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Kinds of seasonal plants. A plant appears in the seasons {@link #spawnSeasons()} and OpenSeasons
 * removes it again when the world enters a season outside {@link #aliveSeasons()}. The id is also the kind in the journal
 * ({@code data/journal}) and the name in {@code flora.yml} and in {@code /openseasons debug flora}.
 */
public enum PlantKind {
    /** spring flowers ({@code flower-chance-in-spring}) */
    FLOWERS("flowers", "spring flowers", false, set(Season.SPRING), set(Season.SPRING)),
    /** regrowing grass ({@code regrow-grass-everywhere}); dies off in winter */
    GRASS("grass", "regrowing grass", false, set(Season.SPRING, Season.SUMMER),
            set(Season.SPRING, Season.SUMMER, Season.FALL)),
    /** mushrooms in the shade of trees ({@code spawn-mushrooms-in-fall}) */
    MUSHROOMS("mushrooms", "mushrooms", false, set(Season.FALL), set(Season.FALL)),
    /** sweet berry bushes ({@code max-berrybushes-per-chunk}) */
    BERRY_BUSHES("berry-bushes", "sweet berry bushes", false, set(Season.SUMMER), set(Season.SUMMER, Season.FALL)),
    /** leaf litter under trees ({@code generate-leaf-litter}) */
    LEAF_LITTER("leaf-litter", "leaf litter", false, set(Season.FALL), set(Season.FALL)),
    /** extra: firefly bushes near spruces */
    FIREFLY_BUSH("firefly-bush", "firefly bushes", true, set(Season.SUMMER), set(Season.SUMMER)),
    /** extra: wildflowers on humid meadows and in birch forests */
    WILDFLOWERS("wildflowers", "wildflowers", true, set(Season.SPRING), set(Season.SPRING, Season.SUMMER)),
    /** extra: pink petals under cherry trees */
    PINK_PETALS("pink-petals", "pink petals under cherry trees", true, set(Season.SPRING), set(Season.SPRING)),
    /** extra: dry grass in hot biomes */
    DRY_GRASS("dry-grass", "dry grass", true, set(Season.SUMMER), set(Season.SUMMER, Season.FALL)),
    /** extra: bushes in temperate biomes */
    BUSH("bush", "bushes", true, set(Season.SUMMER), set(Season.SUMMER, Season.FALL)),
    /** extra: flowers on cacti */
    CACTUS_FLOWER("cactus-flower", "cactus flowers", true, set(Season.SPRING), set(Season.SPRING)),
    /** extra: pumpkins on meadows of the temperate zone */
    PUMPKINS("pumpkins", "pumpkins", true, set(Season.FALL), set(Season.FALL));

    private final String id;
    private final String displayName;
    private final boolean extra;
    private final Set<Season> spawnSeasons;
    private final Set<Season> aliveSeasons;

    PlantKind(String id, String displayName, boolean extra, Set<Season> spawnSeasons, Set<Season> aliveSeasons) {
        this.id = id;
        this.displayName = displayName;
        this.extra = extra;
        this.spawnSeasons = spawnSeasons;
        this.aliveSeasons = aliveSeasons;
    }

    public String id() {
        return id;
    }

    /** display name for players and administrators */
    public String displayName() {
        return displayName;
    }

    /** extra plant, enabled only in {@code flora.yml} */
    public boolean extra() {
        return extra;
    }

    public Set<Season> spawnSeasons() {
        return spawnSeasons;
    }

    public Set<Season> aliveSeasons() {
        return aliveSeasons;
    }

    public boolean spawnsIn(Season season) {
        return spawnSeasons.contains(season);
    }

    public boolean aliveIn(Season season) {
        return aliveSeasons.contains(season);
    }

    public static Optional<PlantKind> byId(String id) {
        if (id == null) return Optional.empty();
        String v = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (PlantKind k : values()) {
            if (k.id.equals(v)) return Optional.of(k);
        }
        return Optional.empty();
    }

    private static Set<Season> set(Season first, Season... rest) {
        return Set.copyOf(EnumSet.of(first, rest));
    }
}
