/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.placeholder;

import cz.eida.minecraft.openseasons.season.Season;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Placeholders in PlaceholderAPI. {@link SeasonExpansion} extends a PlaceholderAPI class, so without it the class
 * must not even be loaded through a method reference; the main class therefore only touches this class and creates it
 * only while PlaceholderAPI is running.
 */
public final class PlaceholderHook {

    private final List<SeasonExpansion> expansions = new ArrayList<>();

    /**
     * Registers {@code %mc215os_*%}.
     *
     * @return accepted identifiers in the form {@code %id_*%}
     */
    public List<String> register(Plugin plugin, PlaceholderResolver resolver,
                                 Function<Player, Optional<Season>> localSeason) {
        SeasonExpansion own = new SeasonExpansion(plugin, resolver, SeasonExpansion.OWN);
        own.setLocalSeason(localSeason);
        expansions.add(own);
        List<String> ids = new ArrayList<>();
        for (SeasonExpansion e : expansions) {
            if (e.register()) ids.add("%" + e.getIdentifier() + "_*%");
            else plugin.getLogger().warning("PlaceholderAPI did not accept %" + e.getIdentifier() + "_*%");
        }
        return ids;
    }

    public void unregister() {
        expansions.forEach(SeasonExpansion::unregister);
        expansions.clear();
    }
}
