/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reading and writing YAML: always UTF-8, a leading BOM is skipped, writes are atomic. */
public final class YamlFiles {

    private YamlFiles() {
    }

    public static YamlConfiguration load(Path file) throws ConfigException {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
        } catch (IOException e) {
            throw new ConfigException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** default file bundled in the jar */
    public static YamlConfiguration loadResource(String name) throws ConfigException {
        try (InputStream in = resource(name)) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "jar:" + name);
        } catch (IOException e) {
            throw new ConfigException("cannot read default " + name + ": " + e.getMessage(), e);
        }
    }

    /** copies the default file from the jar when the target is missing; returns true when it created it */
    public static boolean copyResourceIfMissing(String name, Path target) throws ConfigException {
        if (Files.exists(target)) return false;
        try (InputStream in = resource(name)) {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.copy(in, target);
            return true;
        } catch (IOException e) {
            throw new ConfigException("cannot create " + target + ": " + e.getMessage(), e);
        }
    }

    public static void saveAtomically(YamlConfiguration yaml, Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static YamlConfiguration parse(String text, String source) throws ConfigException {
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new ConfigException("invalid YAML in " + source + ": " + e.getMessage(), e);
        }
        return yaml;
    }

    private static InputStream resource(String name) throws IOException {
        InputStream in = YamlFiles.class.getClassLoader().getResourceAsStream(name);
        if (in == null) throw new IOException("missing in the jar: " + name);
        return in;
    }
}
