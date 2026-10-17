/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.biome;

import cz.eida.minecraft.openseasons.config.BiomeColors;
import cz.eida.minecraft.openseasons.config.BiomeGroup;
import cz.eida.minecraft.openseasons.config.ClientColorSettings;
import cz.eida.minecraft.openseasons.config.MainConfig.ColorAdjustment;
import cz.eida.minecraft.openseasons.season.Season;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static cz.eida.minecraft.openseasons.biome.BiomeTint.KEEP;

/**
 * Colours of a biome group by season and its elapsed part. A season has {@link #STEPS} steps of 10 %.
 *
 * <p>Sky, water, fog and grass: the season colours from the file; at the start they blend with the previous
 * season (45 % at the very start, gone by 20 % of the season), at the end with the next one (from 80 % up to 45 %
 * at the end), so the landscape changes smoothly. Leaves have their own course:
 * <ul>
 *   <li>fall: from summer leaves gradually to autumn colours – only a hint at the start (15 %),
 *       the colours are dull, 75 % in the middle, full brown, yellow and red from 70 % of fall; grass yellows the same way,</li>
 *   <li>spring: from frosty winter to a bright fresh green; between 25 and 65 % of spring some patches blossom in saturated
 *       pink (the spring leaf colour from the file with a saturation of at least 55 %, share {@code spring-blossom}) – at
 *       the edges of the window one of five patches, two in mid spring; from 65 % the green darkens to summer. The leaf texture
 *       is grey and the colour multiplies it, so pale colours and blending green with pink look greyish.</li>
 *   <li>winter: from autumn colours to winter colours, a hint of green at the end.</li>
 * </ul>
 * A colour the file does not change ({@code NONE}) is replaced by the biome colour when blending ({@link Known}; grass
 * and foliage approximately from the colour map). {@code foliage-color-adjustments} tints the leaves during the transition
 * in proportion to the share of the neighbouring season. Without {@code sub-seasons} the whole season has the file colours.
 *
 * <p>Leaves with five colours give five variants (patches, {@link FoliagePattern}); with one colour one.
 */
public final class SeasonTints {

    /** colour steps in a season (by 10 %) */
    public static final int STEPS = 10;

    private static final double EDGE_WEIGHT = 0.45;
    private static final double EDGE = 0.2;
    private static final double AUTUMN_START = 0.15;
    private static final double AUTUMN_RATE = 1.2;
    private static final double BLOSSOM_PEAK = 0.45;
    private static final double BLOSSOM_HALF_WIDTH = 0.2;
    /** fresh spring green: summer leaves blended with a bright green */
    private static final int FRESH_GREEN = 0x92D050;
    private static final double FRESH_WEIGHT = 0.6;
    /** from this part of spring the green turns into summer green */
    private static final double SUMMER_GREEN_FROM = 0.65;

    /** biome colours as the client has them without changes; {@code -1} = unknown (default dimension) */
    public record Known(int sky, int water, int waterFog, int grass, int foliage, int fog) {
        public static final Known NONE = new Known(KEEP, KEEP, KEEP, KEEP, KEEP, KEEP);
    }

    private final boolean gradual;
    private final Map<Season, ColorAdjustment> adjustments;
    private final List<Integer> autumnLeaves;
    private final double blossom;

    /**
     * @param gradual      smooth transitions ({@code sub-seasons.enabled}); otherwise the season colours from the file
     * @param adjustments  leaf tinting during the transition from a season (key) to the next one
     * @param autumnLeaves five colours instead of the five-colour autumn leaves; empty = from the file
     * @param blossom      share of the spring leaf colour in mid spring, 0–1
     */
    public SeasonTints(boolean gradual, Map<Season, ColorAdjustment> adjustments, List<Integer> autumnLeaves,
                       double blossom) {
        this.gradual = gradual;
        this.adjustments = Map.copyOf(adjustments);
        this.autumnLeaves = List.copyOf(autumnLeaves);
        this.blossom = Math.clamp(blossom, 0.0, 1.0);
    }

    public SeasonTints(boolean gradual, Map<Season, ColorAdjustment> adjustments, List<Integer> autumnLeaves) {
        this(gradual, adjustments, autumnLeaves, ClientColorSettings.DEFAULTS.springBlossom() / 100.0);
    }

    /** colour step for the elapsed part of the season in percent; without transitions always the middle one */
    public int step(double progressPercent) {
        if (!gradual) return STEPS / 2;
        return Math.clamp((int) Math.floor(progressPercent / (100.0 / STEPS)), 0, STEPS - 1);
    }

    /** middle of a step as a part of the season 0–1 */
    static double center(int step) {
        return (step + 0.5) / STEPS;
    }

    /** colour variants (1 or 5) for a group in a season and step */
    public BiomeTint[] tints(BiomeGroup group, Season season, int step, Known known) {
        double c = center(step);
        double towardsPrevious = gradual ? EDGE_WEIGHT * Math.max(0, 1 - c / EDGE) : 0;
        double towardsNext = gradual ? EDGE_WEIGHT * Math.max(0, (c - (1 - EDGE)) / EDGE) : 0;
        Season prev = previous(season);
        Season next = season.next();
        BiomeColors own = colors(group, season);
        BiomeColors before = colors(group, prev);
        BiomeColors after = colors(group, next);

        int sky = edges(own.sky(), before.sky(), after.sky(), known.sky(), towardsPrevious, towardsNext);
        int water = edges(own.water(), before.water(), after.water(), known.water(), towardsPrevious, towardsNext);
        int waterFog = edges(own.waterFog(), before.waterFog(), after.waterFog(), known.waterFog(), towardsPrevious, towardsNext);
        int fog = edges(own.fog(), before.fog(), after.fog(), known.fog(), towardsPrevious, towardsNext);
        int grass;
        if (gradual && season == Season.FALL && own.grass().isPresent()) {
            int summer = colors(group, Season.SUMMER).grass().orElse(known.grass());
            grass = summer == KEEP ? own.grass().getAsInt() : BiomeTint.mix(summer, own.grass().getAsInt(), autumn(c));
        } else if (gradual && season == Season.WINTER && towardsPrevious > 0 && before.grass().isPresent()) {
            // from yellow autumn grass to winter grass
            grass = channel(own.grass(), before.grass(), known.grass(), towardsPrevious);
        } else {
            grass = edges(own.grass(), before.grass(), after.grass(), known.grass(), towardsPrevious, towardsNext);
        }

        boolean blooming = gradual && season == Season.SPRING && bloom(c) > 0 && !own.foliage().isEmpty();
        int variants = blooming || Math.max(Math.max(own.foliage().size(), before.foliage().size()), after.foliage().size())
                == BiomeColors.FOLIAGE_VARIANTS ? BiomeColors.FOLIAGE_VARIANTS : 1;
        BiomeTint[] out = new BiomeTint[variants];
        for (int v = 0; v < variants; v++) {
            int foliage = gradual ? foliage(group, season, c, v, known, towardsPrevious, towardsNext)
                    : pick(own.foliage(), v).orElse(KEEP);
            out[v] = new BiomeTint(sky, water, waterFog, grass, foliage, fog);
        }
        return out;
    }

    private int foliage(BiomeGroup group, Season season, double c, int v, Known known,
                        double towardsPrevious, double towardsNext) {
        OptionalInt own = pick(colors(group, season).foliage(), v);
        int summer = pick(colors(group, Season.SUMMER).foliage(), v).orElse(known.foliage());
        int result = switch (season) {
            case FALL -> {
                if (own.isEmpty()) yield edgesFoliage(group, season, v, known, towardsPrevious, towardsNext);
                yield summer == KEEP ? own.getAsInt() : BiomeTint.mix(summer, own.getAsInt(), autumn(c));
            }
            case SPRING -> {
                if (summer == KEEP) yield own.orElse(KEEP);
                int leaf = BiomeTint.mix(summer, FRESH_GREEN, FRESH_WEIGHT);
                if (c > SUMMER_GREEN_FROM) leaf = BiomeTint.mix(leaf, summer, (c - SUMMER_GREEN_FROM) / (1 - SUMMER_GREEN_FROM));
                OptionalInt winter = pick(colors(group, Season.WINTER).foliage(), v);
                if (towardsPrevious > 0 && winter.isPresent()) leaf = BiomeTint.mix(leaf, winter.getAsInt(), towardsPrevious);
                // one (at the edges of the window) or two of five patches blossom, the others stay green
                double b = bloom(c);
                if (own.isPresent() && (v == 0 || (v == 3 && b >= 0.75)) && b > 0) {
                    leaf = BiomeTint.mix(leaf, vivid(own.getAsInt()), blossom);
                }
                yield leaf;
            }
            case SUMMER -> own.orElse(KEEP);
            case WINTER -> {
                int leaf = own.orElse(known.foliage());
                OptionalInt fall = pick(colors(group, Season.FALL).foliage(), v);
                if (towardsPrevious > 0 && fall.isPresent() && leaf != KEEP) {
                    leaf = BiomeTint.mix(leaf, fall.getAsInt(), towardsPrevious);
                } else if (towardsNext > 0 && summer != KEEP && leaf != KEEP) {
                    leaf = BiomeTint.mix(leaf, summer, towardsNext);
                } else if (own.isEmpty()) {
                    leaf = KEEP;
                }
                yield leaf;
            }
        };
        return adjust(result, season, known, towardsPrevious, towardsNext);
    }

    /** leaves like the other channels (fall without autumn colours) */
    private int edgesFoliage(BiomeGroup group, Season season, int v, Known known, double towardsPrevious, double towardsNext) {
        return edges(pick(colors(group, season).foliage(), v), pick(colors(group, previous(season)).foliage(), v),
                pick(colors(group, season.next()).foliage(), v), known.foliage(), towardsPrevious, towardsNext);
    }

    /** leaf tinting during the transition ({@code foliage-color-adjustments}), in proportion to the neighbour's share */
    private int adjust(int foliage, Season season, Known known, double towardsPrevious, double towardsNext) {
        ColorAdjustment a = towardsPrevious > 0 ? adjustments.get(previous(season))
                : towardsNext > 0 ? adjustments.get(season) : null;
        if (a == null) return foliage;
        int base = foliage != KEEP ? foliage : known.foliage();
        if (base == KEEP) return foliage;
        double share = Math.max(towardsPrevious, towardsNext) / EDGE_WEIGHT;
        return BiomeTint.mix(base, a.rgb(), a.weight() * share);
    }

    /** how much spring blossoms (0–1) by the elapsed part of spring; determines the number of blooming patches */
    private double bloom(double c) {
        return blossom <= 0 ? 0 : Math.max(0, 1 - Math.abs(c - BLOSSOM_PEAK) / BLOSSOM_HALF_WIDTH);
    }

    /** blossom colour on the grey leaf texture: saturation at least 55 %, brightness at least 90 % */
    static int vivid(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
        return java.awt.Color.HSBtoRGB(hsb[0], Math.max(hsb[1], 0.55f), Math.max(hsb[2], 0.9f)) & 0xFFFFFF;
    }

    /** share of autumn colours in leaves and grass by the elapsed part of fall */
    static double autumn(double c) {
        return Math.clamp(AUTUMN_START + AUTUMN_RATE * c, 0.0, 1.0);
    }

    /** channel blended with the previous or next season */
    private static int edges(OptionalInt own, OptionalInt before, OptionalInt after, int known,
                             double towardsPrevious, double towardsNext) {
        if (towardsPrevious > 0) return channel(own, before, known, towardsPrevious);
        if (towardsNext > 0) return channel(own, after, known, towardsNext);
        return own.orElse(KEEP);
    }

    /** group colours in a season, with the custom autumn leaves */
    private BiomeColors colors(BiomeGroup group, Season season) {
        BiomeColors c = group.colors(season);
        if (season == Season.FALL && !autumnLeaves.isEmpty() && c.foliage().size() == BiomeColors.FOLIAGE_VARIANTS) {
            return new BiomeColors(c.sky(), c.water(), c.waterFog(), c.grass(), autumnLeaves, c.fog());
        }
        return c;
    }

    private static OptionalInt pick(List<Integer> foliage, int variant) {
        return foliage.isEmpty() ? OptionalInt.empty() : OptionalInt.of(foliage.get(variant % foliage.size()));
    }

    /** channel colour: own, blended with the neighbouring season, or unchanged */
    static int channel(OptionalInt own, OptionalInt other, int known, double weight) {
        if (weight == 0 || (own.isEmpty() && other.isEmpty())) return own.orElse(KEEP);
        int a = own.isPresent() ? own.getAsInt() : known;
        int b = other.isPresent() ? other.getAsInt() : known;
        if (a == KEEP || b == KEEP) return own.orElse(KEEP);
        return BiomeTint.mix(a, b, weight);
    }

    static Season previous(Season s) {
        Season[] all = Season.values();
        return all[(s.ordinal() + all.length - 1) % all.length];
    }
}
