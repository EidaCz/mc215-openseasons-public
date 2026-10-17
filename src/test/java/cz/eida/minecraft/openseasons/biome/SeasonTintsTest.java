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
import cz.eida.minecraft.openseasons.config.MainConfig.ColorAdjustment;
import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static cz.eida.minecraft.openseasons.biome.BiomeTint.KEEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonTintsTest {

    private static final List<Integer> AUTUMN = List.of(0x8B5A2B, 0xD9A21B, 0xB83A1E, 0xE8962A, 0x6BA941);
    private static final BiomeColors FALL = new BiomeColors(OptionalInt.of(0x89919A), OptionalInt.of(0x617B64),
            OptionalInt.empty(), OptionalInt.of(0xBFB755),
            List.of(0xE8962A, 0xE8962A, 0xAEA42A, 0x6BA941, 0x60A17B), OptionalInt.empty());
    private static final BiomeColors WINTER = new BiomeColors(OptionalInt.of(0xD2DEFF), OptionalInt.of(0x216FC4),
            OptionalInt.of(0x216FC4), OptionalInt.of(0xB8D0E1), List.of(0xB8D0E1), OptionalInt.of(0xA6D1FF));
    private static final BiomeColors SPRING = new BiomeColors(OptionalInt.of(0x78A7FF), OptionalInt.empty(),
            OptionalInt.empty(), OptionalInt.empty(), List.of(0xFFB0E9), OptionalInt.empty());
    private static final BiomeColors SUMMER = new BiomeColors(OptionalInt.of(0x84BDE8), OptionalInt.empty(),
            OptionalInt.empty(), OptionalInt.empty(), List.of(), OptionalInt.empty());

    private static BiomeGroup group() {
        Map<Season, BiomeColors> c = new EnumMap<>(Season.class);
        c.put(Season.FALL, FALL);
        c.put(Season.WINTER, WINTER);
        c.put(Season.SPRING, SPRING);
        c.put(Season.SUMMER, SUMMER);
        return new BiomeGroup("TEST", Set.of("minecraft:forest"), false, Map.of(), c);
    }

    /** vanilla biome colours; grass and leaves roughly from the colour map */
    private static final SeasonTints.Known KNOWN = new SeasonTints.Known(0x79A6FF, 0x3F76E4, KEEP, 0x79C05A, 0x59AE30, KEEP);

    private static int luma(int rgb) {
        return ((rgb >> 16 & 0xFF) * 3 + (rgb >> 8 & 0xFF) * 6 + (rgb & 0xFF)) / 10;
    }

    @Test
    void stepsFollowSeasonProgress() {
        SeasonTints t = new SeasonTints(true, Map.of(), List.of());
        assertEquals(0, t.step(0));
        assertEquals(4, t.step(49.9));
        assertEquals(9, t.step(100));
        assertEquals(5, new SeasonTints(false, Map.of(), List.of()).step(3));
    }

    @Test
    void autumnLeavesTurnGraduallyFromSummerGreen() {
        SeasonTints t = new SeasonTints(true, Map.of(), AUTUMN);
        // start of autumn: only a hint (dull), half 75 %, full colours from 70 %
        BiomeTint[] start = t.tints(group(), Season.FALL, 0, KNOWN);
        assertEquals(5, start.length);
        assertEquals(BiomeTint.mix(0x59AE30, 0xB83A1E, 0.21), start[2].foliage());
        assertEquals(BiomeTint.mix(0x59AE30, 0xB83A1E, SeasonTints.autumn(0.45)), t.tints(group(), Season.FALL, 4, KNOWN)[2].foliage());
        for (int step = 7; step < 10; step++) {
            BiomeTint[] full = t.tints(group(), Season.FALL, step, KNOWN);
            assertEquals(0x8B5A2B, full[0].foliage());
            assertEquals(0xD9A21B, full[1].foliage());
            assertEquals(0xB83A1E, full[2].foliage());
        }
        // grass turns yellow the same way
        assertEquals(BiomeTint.mix(0x79C05A, 0xBFB755, 0.21), start[0].grass());
        assertEquals(0xBFB755, t.tints(group(), Season.FALL, 7, KNOWN)[0].grass());
        // sky: 45→0 % of summer at the start, autumn in the middle, towards winter at the end
        assertEquals(BiomeTint.mix(0x89919A, 0x84BDE8, 0.45 * 0.75), start[0].sky());
        assertEquals(0x89919A, t.tints(group(), Season.FALL, 5, KNOWN)[0].sky());
        assertEquals(BiomeTint.mix(0x89919A, 0xD2DEFF, 0.45 * 0.75), t.tints(group(), Season.FALL, 9, KNOWN)[0].sky());
    }

    @Test
    void springGoesFreshGreenThenBlossomThenSummerGreen() {
        SeasonTints t = new SeasonTints(true, Map.of(), List.of());
        int fresh = BiomeTint.mix(0x59AE30, 0x92D050, 0.6);
        // start: from frosty winter to bright green
        assertEquals(BiomeTint.mix(fresh, 0xB8D0E1, 0.45 * 0.75), t.tints(group(), Season.SPRING, 0, KNOWN)[0].foliage());
        assertTrue(luma(fresh) > luma(0x59AE30), "fresh green is brighter than the summer one");
        assertEquals(1, t.tints(group(), Season.SPRING, 1, KNOWN).length);
        // 45 %: two of five patches blossom in deep pink, the others stay green
        int pink = BiomeTint.mix(fresh, SeasonTints.vivid(0xFFB0E9), 0.85);
        BiomeTint[] peak = t.tints(group(), Season.SPRING, 4, KNOWN);
        assertEquals(5, peak.length);
        assertEquals(pink, peak[0].foliage());
        assertEquals(pink, peak[3].foliage());
        assertEquals(fresh, peak[1].foliage());
        float[] hsb = java.awt.Color.RGBtoHSB(pink >> 16 & 0xFF, pink >> 8 & 0xFF, pink & 0xFF, null);
        assertTrue(hsb[1] > 0.4f && (hsb[0] > 0.8f || hsb[0] < 0.05f), "deep pink, not grey: " + Integer.toHexString(pink));
        // at the edges of the window only one of five patches blossoms
        BiomeTint[] edge = t.tints(group(), Season.SPRING, 3, KNOWN);
        assertEquals(pink, edge[0].foliage());
        assertEquals(fresh, edge[3].foliage());
        assertEquals(1, t.tints(group(), Season.SPRING, 6, KNOWN).length);
        // spring-blossom changes the share of the blossom
        assertEquals(BiomeTint.mix(fresh, SeasonTints.vivid(0xFFB0E9), 0.4),
                new SeasonTints(true, Map.of(), List.of(), 0.4).tints(group(), Season.SPRING, 4, KNOWN)[0].foliage());
        // from 65 % the green darkens to the summer one
        assertEquals(BiomeTint.mix(fresh, 0x59AE30, (0.95 - 0.65) / 0.35), t.tints(group(), Season.SPRING, 9, KNOWN)[0].foliage());
        // summer: own (no) leaves
        assertEquals(KEEP, t.tints(group(), Season.SUMMER, 5, KNOWN)[0].foliage());
    }


    @Test
    void winterFadesFromAutumnColours() {
        SeasonTints t = new SeasonTints(true, Map.of(), AUTUMN);
        BiomeTint[] start = t.tints(group(), Season.WINTER, 0, KNOWN);
        assertEquals(5, start.length);
        assertEquals(BiomeTint.mix(0xB8D0E1, 0xB83A1E, 0.45 * 0.75), start[2].foliage());
        assertEquals(BiomeTint.mix(0xB8D0E1, 0xBFB755, 0.45 * 0.75), start[0].grass());
        assertEquals(0xB8D0E1, t.tints(group(), Season.WINTER, 5, KNOWN)[0].foliage());
        // a hint of green at the end of winter
        assertEquals(BiomeTint.mix(0xB8D0E1, 0x59AE30, 0.45 * 0.75), t.tints(group(), Season.WINTER, 9, KNOWN)[0].foliage());
        // autumn does not change the underwater fog: mixing with an unknown colour is skipped
        assertEquals(0x216FC4, start[0].waterFog());
    }

    @Test
    void withoutGradualWholeSeasonUsesFileColours() {
        SeasonTints t = new SeasonTints(false, Map.of(), List.of());
        assertEquals(0xD2DEFF, t.tints(group(), Season.WINTER, 0, KNOWN)[0].sky());
        assertEquals(0xFFB0E9, t.tints(group(), Season.SPRING, 0, KNOWN)[0].foliage());
        assertEquals(0xAEA42A, t.tints(group(), Season.FALL, 0, KNOWN)[2].foliage());
    }

    @Test
    void foliageAdjustmentTintsTransitions() {
        SeasonTints t = new SeasonTints(true, Map.of(Season.SPRING, new ColorAdjustment(0x32CD32, 0.3)), List.of());
        // end of spring: the green darkening to the summer one is tinted in proportion to the summer share
        int leaf = BiomeTint.mix(BiomeTint.mix(0x59AE30, 0x92D050, 0.6), 0x59AE30, (0.95 - 0.65) / 0.35);
        assertEquals(BiomeTint.mix(leaf, 0x32CD32, 0.3 * 0.75), t.tints(group(), Season.SPRING, 9, KNOWN)[0].foliage());
        assertEquals(BiomeTint.mix(0x59AE30, 0x32CD32, 0.3 * 0.75), t.tints(group(), Season.SUMMER, 0, KNOWN)[0].foliage());
        assertEquals(KEEP, t.tints(group(), Season.SUMMER, 5, KNOWN)[0].foliage());
    }

    @Test
    void earlyAutumnIsDullerThanLate() {
        SeasonTints t = new SeasonTints(true, Map.of(), AUTUMN);
        int early = t.tints(group(), Season.FALL, 1, KNOWN)[1].foliage();
        int late = t.tints(group(), Season.FALL, 8, KNOWN)[1].foliage();
        assertTrue(luma(late) > luma(early), "yellow brighter at the end");
    }

    @Test
    void colormapApproximation() {
        // real colours from the client 26.2 maps (temperature, humidity): grass, leaves, dry leaves
        assertClose(0x91BD59, Colormap.grass(0.8, 0.4));          // plains
        assertClose(0x77AB2F, Colormap.foliage(0.8, 0.4));
        assertClose(0xA37546, Colormap.dryFoliage(0.8, 0.4));
        assertClose(0x79C05A, Colormap.grass(0.7, 0.8));          // les
        assertClose(0x59AE30, Colormap.foliage(0.7, 0.8));
        assertClose(0x86B783, Colormap.grass(0.25, 0.8));         // taiga
        assertClose(0x68A464, Colormap.foliage(0.25, 0.8));
        assertClose(0x83BB6D, Colormap.grass(0.5, 0.8));          // meadow
        assertClose(0x64A948, Colormap.foliage(0.5, 0.8));
        assertClose(0x9A764F, Colormap.dryFoliage(0.25, 0.8));
        // map corners exactly
        assertEquals(0xBFB755, Colormap.grass(2.0, 0.0));
        assertEquals(0x60A17B, Colormap.foliage(0.0, 0.5));
        assertEquals(0x8F7A5A, Colormap.dryFoliage(-0.5, 0.4));
    }

    /** colour map estimate: at most 3 off per channel */
    private static void assertClose(int expected, int actual) {
        for (int shift = 16; shift >= 0; shift -= 8) {
            int d = Math.abs((expected >> shift & 0xFF) - (actual >> shift & 0xFF));
            assertTrue(d <= 3, String.format("#%06X instead of #%06X", actual, expected));
        }
    }

    @Test
    void mixIsLinearPerChannel() {
        assertEquals(0x808080, BiomeTint.mix(0x000000, 0xFFFFFF, 0.5));
        assertEquals(0x102030, BiomeTint.mix(0x102030, 0xFFFFFF, 0.0));
    }
}
