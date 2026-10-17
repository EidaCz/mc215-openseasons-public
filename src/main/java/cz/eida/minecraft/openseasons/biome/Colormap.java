/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.biome;

/**
 * Approximate grass, foliage and dry foliage colour from the biome temperature and downfall, as the client takes it
 * from the colour map when the biome has no colour of its own. The server has no map; the estimate is linear between the points
 * of a 5 × 5 grid in the triangle of the map (15 points per map, at most 3 per channel off the map; the points
 * come from the client jar 26.2). Used for blending colours during a season transition
 * (e.g. from summer to autumn leaves) and for fixed colours of snowy variants, where the
 * client would take another colour from the map at a lower temperature.
 */
public final class Colormap {

    /** grid divisions per axis */
    private static final int N = 4;

    // points by dryness rows j = 0, 1/4, …, 1 (1 − downfall × temperature), within a row coldness i = 0 … j (1 − temperature)
    private static final int[] GRASS = {0x47CD33, 0x65C83C, 0x67C450, 0x82C245, 0x7FBF57, 0x7CBD6B, 0xA0BD4D,
            0x97BB5E, 0x8EB971, 0x84B883, 0xBFB755, 0xB0B665, 0xA0B676, 0x90B586, 0x80B497};
    private static final int[] FOLIAGE = {0x1ABF00, 0x3FB90B, 0x41B524, 0x64B216, 0x60AE2D, 0x5BAB46, 0x89AB21,
            0x7DA936, 0x71A74D, 0x65A563, 0xAEA42A, 0x9BA33E, 0x87A353, 0x73A267, 0x60A17B};
    private static final int[] DRY_FOLIAGE = {0xA35F46, 0xA36646, 0xA36846, 0xA37146, 0xA36F46, 0xA37146, 0xA37B46,
            0xA37746, 0xA17448, 0x9B754E, 0xA38046, 0xA27A47, 0x9E784B, 0x977952, 0x8F7A5A};

    private Colormap() {
    }

    public static int grass(double temperature, double downfall) {
        return sample(GRASS, temperature, downfall);
    }

    public static int foliage(double temperature, double downfall) {
        return sample(FOLIAGE, temperature, downfall);
    }

    /** dry foliage ({@code dry_foliage_color}, e.g. leaf litter) */
    public static int dryFoliage(double temperature, double downfall) {
        return sample(DRY_FOLIAGE, temperature, downfall);
    }

    private static int sample(int[] points, double temperature, double downfall) {
        double t = Math.clamp(temperature, 0.0, 1.0);
        double d = Math.clamp(downfall, 0.0, 1.0) * t;
        double gi = (1 - t) * N;
        double gj = Math.max(1 - d, 1 - t) * N;
        int a = Math.min((int) gi, N - 1);
        int b = Math.min((int) gj, N - 1);
        double fu = gi - a;
        double fv = gj - b;
        // grid cell split by its diagonal; above the diagonal of the map triangle there are no points
        if (fv >= fu || a + 1 > b) {
            return mix(point(points, a, b), 1 - fv, point(points, a, b + 1), fv - fu, point(points, a + 1, b + 1), fu);
        }
        return mix(point(points, a, b), 1 - fu, point(points, a + 1, b), fu - fv, point(points, a + 1, b + 1), fv);
    }

    private static int point(int[] points, int i, int j) {
        return points[j * (j + 1) / 2 + i];
    }

    private static int mix(int c0, double w0, int c1, double w1, int c2, double w2) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double v = (c0 >> shift & 0xFF) * w0 + (c1 >> shift & 0xFF) * w1 + (c2 >> shift & 0xFF) * w2;
            rgb |= (int) Math.clamp(Math.round(v), 0, 255) << shift;
        }
        return rgb;
    }
}
