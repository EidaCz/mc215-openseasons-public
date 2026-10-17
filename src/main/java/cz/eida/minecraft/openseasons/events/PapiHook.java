/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** PlaceholderAPI in event lines; call only while the PlaceholderAPI plugin runs. */
final class PapiHook {

    private PapiHook() {
    }

    /** @param player {@code null} for console commands */
    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
