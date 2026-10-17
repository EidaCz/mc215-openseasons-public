/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.network;

import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerRegistryData.RegistryElement;
import cz.eida.minecraft.openseasons.biome.Colormap;
import cz.eida.minecraft.openseasons.biome.SeasonTints;
import cz.eida.minecraft.openseasons.config.BiomeColors;
import cz.eida.minecraft.openseasons.config.BiomeConfig;
import cz.eida.minecraft.openseasons.config.BiomeGroup;
import cz.eida.minecraft.openseasons.season.Season;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeTableTest {

    static NBTCompound biome(double temperature, String water, String sky) {
        NBTCompound b = new NBTCompound();
        b.setTag("temperature", new NBTFloat((float) temperature));
        b.setTag("downfall", new NBTFloat(0.4f));
        NBTCompound effects = new NBTCompound();
        effects.setTag("water_color", new NBTString(water));
        b.setTag("effects", effects);
        NBTCompound attributes = new NBTCompound();
        attributes.setTag("minecraft:visual/sky_color", new NBTString(sky));
        b.setTag("attributes", attributes);
        return b;
    }

    static RegistryElement element(String id, NBTCompound data) {
        return new RegistryElement(new ResourceLocation(id), data);
    }

    static BiomeConfig config() {
        Map<Season, BiomeColors> plains = new EnumMap<>(Season.class);
        plains.put(Season.FALL, new BiomeColors(OptionalInt.of(0x89919A), OptionalInt.of(0x617B64), OptionalInt.empty(),
                OptionalInt.of(0xBFB755), List.of(1, 2, 3, 4, 5), OptionalInt.empty()));
        plains.put(Season.WINTER, new BiomeColors(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(0xB8D0E1), List.of(0xB8D0E1), OptionalInt.empty()));
        Map<Season, BiomeColors> swamp = new EnumMap<>(Season.class);
        swamp.put(Season.WINTER, new BiomeColors(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(0xB8D0E1), List.of(), OptionalInt.empty()));
        return new BiomeConfig(Optional.empty(), List.of(
                new BiomeGroup("PLAINS", Set.of("minecraft:plains"), false, Map.of(), plains),
                new BiomeGroup("SWAMP", Set.of("minecraft:swamp"), false, Map.of(), swamp),
                new BiomeGroup("ALL", Set.of(), true, Map.of(), plains)));
    }

    static List<RegistryElement> elements() {
        NBTCompound swamp = biome(0.8, "#617b64", "#78a7ff");
        swamp.getCompoundTagOrNull("effects").setTag("grass_color_modifier", new NBTString("swamp"));
        return List.of(
                element("minecraft:plains", biome(0.8, "#3f76e4", "#78a7ff")),
                element("minecraft:the_void", biome(0.5, "#3f76e4", "#78a7ff")),
                element("minecraft:swamp", swamp),
                element("terralith:no_data", null));
    }

    static boolean[] mistInLateFall() {
        boolean[] mist = new boolean[BiomeTable.LOOKS];
        mist[BiomeTable.look(Season.FALL, 7)] = true;
        return mist;
    }

    @Test
    void buildsVariantsPerLookFoliageAndMist() {
        SeasonTints tints = new SeasonTints(true, Map.of(), List.of());
        BiomeTable t = BiomeTable.build(elements(), config(), tints, "openseasons", mistInLateFall());
        assertEquals(4, t.sourceCount());
        assertEquals(4 + t.added().size(), t.total());

        int fall = BiomeTable.look(Season.FALL, 7);
        Set<Integer> fallIds = new HashSet<>();
        for (int v = 0; v < 5; v++) {
            int id = t.map(0, fall, v, false);
            assertTrue(id >= 4, "variant " + v);
            fallIds.add(id);
            int mist = t.map(0, fall, v, true);
            assertNotEquals(id, mist);
            NBTCompound mistData = (NBTCompound) t.added().get(mist - 4).getData();
            assertEquals(40.0f, ((NBTFloat) mistData.getCompoundTagOrNull("attributes")
                    .getTagOrNull("minecraft:visual/fog_end_distance")).getAsFloat());
        }
        assertEquals(5, fallIds.size());
        assertTrue(t.varies(0, fall));
        NBTCompound fall0 = (NBTCompound) t.added().get(t.map(0, fall, 0, false) - 4).getData();
        assertEquals("#bfb755", fall0.getCompoundTagOrNull("effects").getStringTagValueOrNull("grass_color"));
        assertEquals("#000001", fall0.getCompoundTagOrNull("effects").getStringTagValueOrNull("foliage_color"));
        assertEquals("#89919a", fall0.getCompoundTagOrNull("attributes").getStringTagValueOrNull("minecraft:visual/sky_color"));
        assertEquals("openseasons:plains_fall_70_0", t.added().get(t.map(0, fall, 0, false) - 4).getId().toString());

        // a biome without a group and without data stays, the group does not change summer
        int spring = BiomeTable.look(Season.SUMMER, 5);
        for (int look = 0; look < BiomeTable.LOOKS; look++) {
            assertEquals(1, t.map(1, look, 0, true));
            assertEquals(3, t.map(3, look, 0, true));
        }
        assertEquals(0, t.map(0, spring, 2, false));
        assertFalse(t.changes(spring));
        assertTrue(t.changes(fall));
        // no fog outside autumn
        int winter = BiomeTable.look(Season.WINTER, 5);
        assertEquals(t.map(0, winter, 0, false), t.map(0, winter, 0, true));
        // an unknown id stays
        assertEquals(99, t.map(99, fall, 0, false));
    }

    @Test
    void sameTintSharesVariantAndSwampModifierGoes() {
        BiomeTable t = BiomeTable.build(elements(), config(), new SeasonTints(false, Map.of(), List.of()),
                "", new boolean[BiomeTable.LOOKS]);
        int winterMiddle = BiomeTable.look(Season.WINTER, 5);
        // without sub-seasons all five sub-seasons of winter share one variant
        for (int step = 0; step < SeasonTints.STEPS; step++) {
            assertEquals(t.map(0, winterMiddle, 0, false), t.map(0, BiomeTable.look(Season.WINTER, step), 3, false));
        }
        NBTCompound swampWinter = (NBTCompound) t.added().get(t.map(2, winterMiddle, 0, false) - 4).getData();
        assertNull(swampWinter.getCompoundTagOrNull("effects").getTagOrNull("grass_color_modifier"));
        assertEquals("#b8d0e1", swampWinter.getCompoundTagOrNull("effects").getStringTagValueOrNull("grass_color"));
        Set<String> names = new HashSet<>();
        t.added().forEach(e -> assertTrue(names.add(e.getId().toString()), e.getId().toString()));
        assertTrue(t.added().getFirst().getId().toString().startsWith("openseasons:"));
    }

    static NBTCompound effects(RegistryElement e) {
        return ((NBTCompound) e.getData()).getCompoundTagOrNull("effects");
    }

    @Test
    void snowInsteadOfRainKeepsColors() {
        Map<Season, BiomeColors> plains = new EnumMap<>(Season.class);
        plains.put(Season.WINTER, new BiomeColors(OptionalInt.of(0xD2DEFF), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(0xB8D0E1), List.of(0xB8D0E1), OptionalInt.empty()));
        plains.put(Season.FALL, new BiomeColors(OptionalInt.of(0x89919A), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.empty(), List.of(), OptionalInt.empty()));
        Map<Season, Boolean> winter = Map.of(Season.WINTER, true);
        BiomeConfig config = new BiomeConfig(Optional.empty(), List.of(
                new BiomeGroup("PLAINS", Set.of("minecraft:plains"), false, winter, plains),
                // only snow, colours unchanged
                new BiomeGroup("CAVES", Set.of("minecraft:lush_caves", "minecraft:swamp", "minecraft:frozen_ocean",
                        "minecraft:snowy_plains"), false, winter, Map.of())));

        NBTCompound plainsData = biome(0.8, "#3f76e4", "#78a7ff");
        plainsData.setTag("has_precipitation", new NBTByte((byte) 1));
        NBTCompound lush = biome(0.5, "#3f76e4", "#78a7ff");
        lush.setTag("downfall", new NBTFloat(0.5f));
        NBTCompound swamp = biome(0.8, "#617b64", "#78a7ff");
        swamp.getCompoundTagOrNull("effects").setTag("grass_color_modifier", new NBTString("swamp"));
        swamp.getCompoundTagOrNull("effects").setTag("foliage_color", new NBTString("#6a7039"));
        NBTCompound frozenOcean = biome(0.0, "#3938c9", "#7fa1ff");
        frozenOcean.setTag("has_precipitation", new NBTByte((byte) 1));
        frozenOcean.setTag("temperature_modifier", new NBTString("frozen"));
        NBTCompound snowy = biome(0.0, "#3f76e4", "#7fa1ff");
        snowy.setTag("has_precipitation", new NBTByte((byte) 1));
        List<RegistryElement> elements = List.of(element("minecraft:plains", plainsData),
                element("minecraft:lush_caves", lush), element("minecraft:swamp", swamp),
                element("minecraft:frozen_ocean", frozenOcean), element("minecraft:snowy_plains", snowy));

        BiomeTable t = BiomeTable.build(elements, config, new SeasonTints(true, Map.of(), List.of()), "",
                new boolean[BiomeTable.LOOKS]);
        int n = elements.size();
        int winterMiddle = BiomeTable.look(Season.WINTER, 5);

        // plains in winter: snow, colours from the file, dry leaves by the original temperature
        NBTCompound plainsWinter = (NBTCompound) t.added().get(t.map(0, winterMiddle, 0, false) - n).getData();
        assertEquals(1, ((NBTByte) plainsWinter.getTagOrNull("has_precipitation")).getAsInt());
        assertEquals(0.0f, ((NBTFloat) plainsWinter.getTagOrNull("temperature")).getAsFloat());
        assertEquals(0.4f, ((NBTFloat) plainsWinter.getTagOrNull("downfall")).getAsFloat());
        NBTCompound fx = plainsWinter.getCompoundTagOrNull("effects");
        assertEquals("#b8d0e1", fx.getStringTagValueOrNull("grass_color"));
        assertEquals("#b8d0e1", fx.getStringTagValueOrNull("foliage_color"));
        assertEquals(String.format("#%06x", Colormap.dryFoliage(0.8f, 0.4f)), fx.getStringTagValueOrNull("dry_foliage_color"));
        // autumn: colours without snow, grass and leaves from the client map (not written)
        NBTCompound plainsFall = (NBTCompound) t.added().get(t.map(0, BiomeTable.look(Season.FALL, 5), 0, false) - n).getData();
        assertEquals(0.8f, ((NBTFloat) plainsFall.getTagOrNull("temperature")).getAsFloat());
        assertNull(plainsFall.getCompoundTagOrNull("effects").getTagOrNull("grass_color"));

        // a group with snow only: in winter a variant with fixed colours from the map, otherwise the original biome
        NBTCompound lushWinter = (NBTCompound) t.added().get(t.map(1, winterMiddle, 0, false) - n).getData();
        NBTCompound lfx = lushWinter.getCompoundTagOrNull("effects");
        assertEquals(String.format("#%06x", Colormap.grass(0.5f, 0.5f)), lfx.getStringTagValueOrNull("grass_color"));
        assertEquals(String.format("#%06x", Colormap.foliage(0.5f, 0.5f)), lfx.getStringTagValueOrNull("foliage_color"));
        assertEquals(1, ((NBTByte) lushWinter.getTagOrNull("has_precipitation")).getAsInt());
        assertEquals("#3f76e4", lfx.getStringTagValueOrNull("water_color"));
        assertEquals("#78a7ff", lushWinter.getCompoundTagOrNull("attributes").getStringTagValueOrNull("minecraft:visual/sky_color"));
        for (Season s : List.of(Season.SPRING, Season.SUMMER, Season.FALL)) {
            assertEquals(1, t.map(1, BiomeTable.look(s, 5), 0, false), s.toString());
        }
        assertTrue(t.changes(winterMiddle));
        assertFalse(t.changes(BiomeTable.look(Season.SUMMER, 5)));

        // swamp: grass from noise stays, leaves already given
        NBTCompound sfx = effects(t.added().get(t.map(2, winterMiddle, 0, false) - n));
        assertEquals("swamp", sfx.getStringTagValueOrNull("grass_color_modifier"));
        assertNull(sfx.getTagOrNull("grass_color"));
        assertEquals("#6a7039", sfx.getStringTagValueOrNull("foliage_color"));

        // frozen ocean: a temperature change would give rain in places
        NBTCompound ocean = (NBTCompound) t.added().get(t.map(3, winterMiddle, 0, false) - n).getData();
        assertNull(ocean.getTagOrNull("temperature_modifier"));

        // snowy plains snow by themselves: no variant
        for (int look = 0; look < BiomeTable.LOOKS; look++) assertEquals(4, t.map(4, look, 0, false));
        assertTrue(BiomeTable.snowsAlready(snowy));
        assertFalse(BiomeTable.snowsAlready(frozenOcean));
        assertFalse(BiomeTable.snowsAlready(plainsData));
    }

    @Test
    void knownColorsFromBiomeData() {
        SeasonTints.Known k = BiomeTable.known(biome(0.8, "#3f76e4", "#78a7ff"));
        assertEquals(0x3F76E4, k.water());
        assertEquals(0x78A7FF, k.sky());
        assertEquals(cz.eida.minecraft.openseasons.biome.Colormap.grass(0.8f, 0.4f), k.grass());
        assertEquals(-1, k.fog());
    }
}
