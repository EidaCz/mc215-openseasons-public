/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.biome;

/**
 * Resulting colours of one biome variant on the client. {@code -1} = the colour stays as the biome has it.
 *
 * @param mist morning fog (short view distance and light fog)
 * @param snow snow instead of rain ({@code show-snow-instead-of-rain})
 */
public record BiomeTint(int sky, int water, int waterFog, int grass, int foliage, int fog, boolean mist,
                        boolean snow) {

    public static final int KEEP = -1;
    public static final BiomeTint IDENTITY = new BiomeTint(KEEP, KEEP, KEEP, KEEP, KEEP, KEEP);

    /** colours without fog and snow */
    public BiomeTint(int sky, int water, int waterFog, int grass, int foliage, int fog) {
        this(sky, water, waterFog, grass, foliage, fog, false, false);
    }

    public boolean isIdentity() {
        return equals(IDENTITY);
    }

    public BiomeTint withMist() {
        return new BiomeTint(sky, water, waterFog, grass, foliage, fog, true, snow);
    }

    public BiomeTint withSnow() {
        return new BiomeTint(sky, water, waterFog, grass, foliage, fog, mist, true);
    }

    /** linear RGB blend; {@code weight} = share of colour {@code b} */
    public static int mix(int a, int b, double weight) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - weight) + ((b >> 16) & 0xFF) * weight);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - weight) + ((b >> 8) & 0xFF) * weight);
        int bl = (int) Math.round((a & 0xFF) * (1 - weight) + (b & 0xFF) * weight);
        return r << 16 | g << 8 | bl;
    }
}
