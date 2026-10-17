/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.config;

/**
 * Settings of OpenSeasons itself (own {@code config.yml}).
 *
 * @param debugEnabled debug commands {@code /openseasons debug …} (key {@code debug-enabled}); without it
 *                     {@code debug} is disabled
 */
public record PluginSettings(boolean debugEnabled) {

    static PluginSettings parse(ConfigReader r) {
        return new PluginSettings(r.bool("debug-enabled", false));
    }
}
