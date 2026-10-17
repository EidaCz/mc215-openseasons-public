/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.persistence;

import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.persistence.PlayerSettingsStore.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSettingsStoreTest {

    @TempDir
    Path dir;

    private static final UUID A = UUID.fromString("0f6e3b44-8d2b-4c1e-9a61-2d0c2a1b7e01");
    private static final UUID B = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000002");

    @Test
    void roundTripAndMissingFile() throws IOException, ConfigException {
        PlayerSettingsStore store = new PlayerSettingsStore(dir.resolve("data/players.yml"));
        List<String> warnings = new ArrayList<>();
        assertFalse(store.exists());
        assertEquals(Settings.EMPTY, store.load(warnings));

        store.save(new Settings(Set.of(B, A), Set.of(B)));
        assertTrue(store.exists());
        assertEquals(new Settings(Set.of(A, B), Set.of(B)), store.load(warnings));
        String text = Files.readString(store.file());
        assertTrue(text.contains("colors-disabled:") && text.contains("particles-disabled:"), text);
        // toggling particles does not erase colours and vice versa
        store.save(new Settings(Set.of(A), Set.of()));
        assertEquals(new Settings(Set.of(A), Set.of()), store.load(warnings));
        store.save(Settings.EMPTY);
        assertTrue(store.load(warnings).isEmpty());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void olderFileWithoutParticles() throws IOException, ConfigException {
        // a file from the time with /toggleseasoncolors only
        Path file = dir.resolve("players.yml");
        Files.writeString(file, "format: 1\ncolors-disabled:\n- " + A + "\n");
        assertEquals(new Settings(Set.of(A), Set.of()), new PlayerSettingsStore(file).load(new ArrayList<>()));
    }
}
