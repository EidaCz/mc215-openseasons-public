/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.network;

import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement;
import cz.eida.minecraft.openseasons.biome.BiomeTint;
import cz.eida.minecraft.openseasons.biome.Colormap;
import cz.eida.minecraft.openseasons.biome.SeasonTints;
import cz.eida.minecraft.openseasons.config.BiomeColors;
import cz.eida.minecraft.openseasons.config.BiomeConfig;
import cz.eida.minecraft.openseasons.config.BiomeGroup;
import cz.eida.minecraft.openseasons.season.Season;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Seasonal biome variants for the client: elements appended to the end of the registry
 * {@code minecraft:worldgen/biome} in the configuration phase, and mapping of the original biome id to a variant
 * by the look of the world (season × step of 10 %), leaf and fog variants.
 *
 * <p>A variant is a copy of the data of the original biome (music, particles stay) with changed colours:
 * {@code effects} (water, grass, foliage) and the attributes {@code minecraft:visual/*} (sky, fog, underwater
 * fog). In seasons with {@code show-snow-instead-of-rain} the variant also has downfall and a low temperature,
 * so the client shows snow instead of rain. The same colours of the same biome share one variant; a look without
 * changes leads to the original biome. Biomes without data (known from the client pack) do not change.
 */
public final class BiomeTable {

    /** looks: season × colour step */
    public static final int LOOKS = Season.values().length * SeasonTints.STEPS;
    static final int VARIANTS = BiomeColors.FOLIAGE_VARIANTS;

    /** fog: a short view distance like in the Nether, but light */
    static final float MIST_FOG_START = 0.0f;
    static final float MIST_FOG_END = 40.0f;
    static final int MIST_FOG_COLOR = 0xC9CFD4;
    /** temperature of a snowy variant; the client shows snow below {@link #SNOW_BELOW} */
    static final float SNOW_TEMPERATURE = 0.0f;
    private static final double SNOW_BELOW = 0.15;

    private static final String SKY = "minecraft:visual/sky_color";
    private static final String FOG = "minecraft:visual/fog_color";
    private static final String WATER_FOG = "minecraft:visual/water_fog_color";
    private static final String FOG_START = "minecraft:visual/fog_start_distance";
    private static final String FOG_END = "minecraft:visual/fog_end_distance";

    private final int sourceCount;
    private final List<RegistryElement> added;
    /** [(source × LOOKS + look) × VARIANTS + variant] × 2 + fog → biome id on the client */
    private final int[] ids;
    /** the look changes at least one biome */
    private final boolean[] lookChanges;
    /** (source × LOOKS + look): the leaf variants differ */
    private final boolean[] varies;

    private BiomeTable(int sourceCount, List<RegistryElement> added, int[] ids, boolean[] lookChanges, boolean[] varies) {
        this.sourceCount = sourceCount;
        this.added = List.copyOf(added);
        this.ids = ids;
        this.lookChanges = lookChanges;
        this.varies = varies;
    }

    public static int look(Season season, int step) {
        return season.ordinal() * SeasonTints.STEPS + step;
    }

    /** number of biomes from the server */
    public int sourceCount() {
        return sourceCount;
    }

    /** total biomes on the client (for the width of the global palette) */
    public int total() {
        return sourceCount + added.size();
    }

    /** elements appended to the end of the registry */
    public List<RegistryElement> added() {
        return added;
    }

    public boolean changes(int look) {
        return look >= 0 && look < LOOKS && lookChanges[look];
    }

    /** the biome has more leaf variants in the look (patches) */
    public boolean varies(int source, int look) {
        return source >= 0 && source < sourceCount && varies[source * LOOKS + look];
    }

    /** biome id on the client; an unknown id stays */
    public int map(int source, int look, int variant, boolean mist) {
        if (source < 0 || source >= sourceCount) return source;
        return ids[((source * LOOKS + look) * VARIANTS + variant) * 2 + (mist ? 1 : 0)];
    }

    /**
     * @param elements  biome registry elements as the server sends them
     * @param namespace namespace of the variants (players see it in F3), {@code biome-display-name}
     * @param mistLooks looks in which there may be morning fog
     */
    public static BiomeTable build(List<RegistryElement> elements, BiomeConfig biomes, SeasonTints tints,
                                   String namespace, boolean[] mistLooks) {
        int n = elements.size();
        int[] ids = new int[n * LOOKS * VARIANTS * 2];
        boolean[] lookChanges = new boolean[LOOKS];
        boolean[] varies = new boolean[n * LOOKS];
        List<RegistryElement> added = new ArrayList<>();
        String ns = sanitize(namespace.isBlank() ? "openseasons" : namespace);

        for (int src = 0; src < n; src++) {
            RegistryElement e = elements.get(src);
            Optional<BiomeGroup> group = e.getData() instanceof NBTCompound
                    ? biomes.groupOf(e.getId().toString()).filter(BiomeGroup::changesClient)
                    : Optional.empty();
            Map<BiomeTint, Integer> made = new HashMap<>();
            // a group with snow only does not change colours (not even the spring green of leaves)
            boolean colors = group.isPresent() && group.get().changesColors();
            SeasonTints.Known known = colors ? known((NBTCompound) e.getData()) : SeasonTints.Known.NONE;
            boolean snowsAlready = group.isPresent() && snowsAlready((NBTCompound) e.getData());
            for (Season season : Season.values()) {
                // snow instead of rain only where the biome does not snow everywhere by itself
                boolean snow = group.isPresent() && group.get().snows(season) && !snowsAlready;
                for (int step = 0; step < SeasonTints.STEPS; step++) {
                    int look = look(season, step);
                    BiomeTint[] variants = colors
                            ? tints.tints(group.get(), season, step, known)
                            : new BiomeTint[]{BiomeTint.IDENTITY};
                    if (variants.length > 1) varies[src * LOOKS + look] = true;
                    for (int v = 0; v < VARIANTS; v++) {
                        BiomeTint tint = snow ? variants[v % variants.length].withSnow() : variants[v % variants.length];
                        int base = ((src * LOOKS + look) * VARIANTS + v) * 2;
                        ids[base] = idOf(tint, src, e, season, step, v, ns, made, added, n);
                        ids[base + 1] = colors && mistLooks[look]
                                ? idOf(tint.withMist(), src, e, season, step, v, ns, made, added, n)
                                : ids[base];
                        if (ids[base] != src || ids[base + 1] != src) lookChanges[look] = true;
                    }
                }
            }
        }
        return new BiomeTable(n, added, ids, lookChanges, varies);
    }

    private static int idOf(BiomeTint tint, int src, RegistryElement e, Season season, int step, int variant,
                            String ns, Map<BiomeTint, Integer> made, List<RegistryElement> added, int n) {
        if (tint.isIdentity()) return src;
        Integer existing = made.get(tint);
        if (existing != null) return existing;
        int id = n + added.size();
        String path = sanitize(e.getId().getNamespace().equals("minecraft") ? e.getId().getKey()
                : e.getId().getNamespace() + "_" + e.getId().getKey())
                + "_" + season.key() + "_" + step * (100 / SeasonTints.STEPS) + "_" + variant
                + (tint.mist() ? "_mist" : "");
        added.add(new RegistryElement(new ResourceLocation(ns, path), recolor((NBTCompound) e.getData(), tint)));
        made.put(tint, id);
        return id;
    }

    /** approximate size of the added elements in the packet (NBT), bytes; the client accepts a packet up to 8 MiB */
    public long approxBytes() {
        long total = 0;
        for (RegistryElement e : added) total += e.getId().toString().length() + 3 + approx(e.getData());
        return total;
    }

    private static long approx(NBT tag) {
        if (tag instanceof NBTCompound c) {
            long n = 1;
            for (Map.Entry<String, NBT> e : c.getTags().entrySet()) n += 3 + e.getKey().length() + approx(e.getValue());
            return n;
        }
        if (tag instanceof NBTString s) return 2 + s.getValue().length();
        if (tag instanceof com.github.retrooper.packetevents.protocol.nbt.NBTList<?> l) {
            long n = 5;
            for (NBT t : l.getTags()) n += approx(t);
            return n;
        }
        return 8;
    }

    /** copy of biome data with changed colours */
    static NBTCompound recolor(NBTCompound source, BiomeTint t) {
        NBTCompound biome = source.copy();
        NBTCompound effects = compound(biome, "effects");
        NBTCompound attributes = compound(biome, "attributes");
        put(effects, "water_color", t.water());
        put(effects, "grass_color", t.grass());
        put(effects, "foliage_color", t.foliage());
        // swamp grass has fixed colours from noise and a set colour would override it
        if (t.grass() != BiomeTint.KEEP && "swamp".equals(effects.getStringTagValueOrNull("grass_color_modifier"))) {
            effects.removeTag("grass_color_modifier");
        }
        put(attributes, SKY, t.sky());
        put(attributes, FOG, t.fog());
        put(attributes, WATER_FOG, t.waterFog());
        if (t.mist()) {
            attributes.setTag(FOG_START, new NBTFloat(MIST_FOG_START));
            attributes.setTag(FOG_END, new NBTFloat(MIST_FOG_END));
            put(attributes, FOG, t.fog() != BiomeTint.KEEP ? BiomeTint.mix(t.fog(), MIST_FOG_COLOR, 0.6) : MIST_FOG_COLOR);
        }
        if (t.snow()) snow(source, biome, effects);
        return biome;
    }

    /**
     * Snow instead of rain: the client shows snow when the biome has downfall and a temperature below 0.15
     * (downfall, temperature 0). From temperature and downfall the client also takes the colour of grass,
     * foliage and dry foliage when the biome does not specify it; these are therefore written fixed by the original
     * biome ({@link Colormap}), so they do not shift with the lower temperature. Swamp grass (colours from noise)
     * does not depend on temperature. The temperature modifier {@code frozen} (frozen ocean) would bring rain back in places, so
     * it is removed; downfall and temperature do not change colours elsewhere on the client.
     */
    private static void snow(NBTCompound source, NBTCompound biome, NBTCompound effects) {
        double temperature = number(source, "temperature", 0.5);
        double downfall = number(source, "downfall", 0.5);
        boolean swamp = "swamp".equals(effects.getStringTagValueOrNull("grass_color_modifier"));
        if (!swamp && effects.getTagOrNull("grass_color") == null) put(effects, "grass_color", Colormap.grass(temperature, downfall));
        if (effects.getTagOrNull("foliage_color") == null) put(effects, "foliage_color", Colormap.foliage(temperature, downfall));
        if (effects.getTagOrNull("dry_foliage_color") == null) {
            put(effects, "dry_foliage_color", Colormap.dryFoliage(temperature, downfall));
        }
        biome.setTag("has_precipitation", new NBTByte((byte) 1));
        biome.setTag("temperature", new NBTFloat(SNOW_TEMPERATURE));
        biome.removeTag("temperature_modifier");
    }

    /** the biome snows by itself everywhere (downfall, temperature below 0.15 without a temperature modifier): snow changes nothing */
    static boolean snowsAlready(NBTCompound biome) {
        return biome.getTagOrNull("has_precipitation") instanceof NBTNumber p && p.getAsInt() != 0
                && number(biome, "temperature", 0.5) < SNOW_BELOW
                && "none".equals(Objects.requireNonNullElse(biome.getStringTagValueOrNull("temperature_modifier"), "none"));
    }

    /**
     * Biome colours as the client has them without changes: from the biome data, grass and foliage without a set colour approximately
     * from temperature and downfall ({@link Colormap}, only for blending transitions).
     */
    static SeasonTints.Known known(NBTCompound biome) {
        NBTCompound effects = biome.getCompoundTagOrNull("effects");
        NBTCompound attributes = biome.getCompoundTagOrNull("attributes");
        double temperature = number(biome, "temperature", 0.5);
        double downfall = number(biome, "downfall", 0.5);
        int grass = color(effects, "grass_color");
        int foliage = color(effects, "foliage_color");
        boolean swamp = effects != null && "swamp".equals(effects.getStringTagValueOrNull("grass_color_modifier"));
        if (grass == BiomeTint.KEEP) grass = swamp ? SWAMP_GRASS : Colormap.grass(temperature, downfall);
        if (foliage == BiomeTint.KEEP) foliage = Colormap.foliage(temperature, downfall);
        return new SeasonTints.Known(
                color(attributes, SKY),
                color(effects, "water_color"),
                color(attributes, WATER_FOG),
                grass,
                foliage,
                color(attributes, FOG));
    }

    /** swamp grass (the darker of the two noise colours) */
    private static final int SWAMP_GRASS = 0x6A7039;

    private static double number(NBTCompound c, String key, double def) {
        return c.getTagOrNull(key) instanceof NBTNumber n ? n.getAsDouble() : def;
    }

    private static int color(NBTCompound c, String key) {
        if (c == null) return BiomeTint.KEEP;
        NBT tag = c.getTagOrNull(key);
        if (tag instanceof NBTString s && s.getValue().startsWith("#") && s.getValue().length() == 7) {
            try {
                return Integer.parseInt(s.getValue().substring(1), 16);
            } catch (NumberFormatException e) {
                return BiomeTint.KEEP;
            }
        }
        if (tag instanceof NBTNumber num) return num.getAsInt() & 0xFFFFFF;
        return BiomeTint.KEEP;
    }

    private static NBTCompound compound(NBTCompound parent, String key) {
        NBTCompound c = parent.getCompoundTagOrNull(key);
        if (c == null) {
            c = new NBTCompound();
            parent.setTag(key, c);
        }
        return c;
    }

    /** colour as {@code "#rrggbb"} (format 26.2); a number where the server sent numbers */
    private static void put(NBTCompound target, String key, int rgb) {
        if (rgb == BiomeTint.KEEP) return;
        NBT old = target.getTagOrNull(key);
        target.setTag(key, old instanceof NBTNumber ? new NBTInt(rgb) : new NBTString(String.format("#%06x", rgb)));
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_").replace('/', '_');
    }
}
