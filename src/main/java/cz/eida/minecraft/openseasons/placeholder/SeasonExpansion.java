/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.placeholder;

import cz.eida.minecraft.openseasons.season.Season;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.function.Function;

/**
 * PlaceholderAPI expansion with the namespace {@code mc215os}.
 */
public final class SeasonExpansion extends PlaceholderExpansion {

    public static final String OWN = "mc215os";

    private final Plugin plugin;
    private final PlaceholderResolver resolver;
    private final String identifier;
    private volatile Function<Player, Optional<Season>> localSeason;

    public SeasonExpansion(Plugin plugin, PlaceholderResolver resolver, String identifier) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /** season at the player's location for {@link PlaceholderResolver#LOCAL_KEYS}; without it the season of their world */
    public void setLocalSeason(Function<Player, Optional<Season>> localSeason) {
        this.localSeason = localSeason;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Player online = player == null ? null : player.getPlayer();
        String world = online == null ? null : online.getWorld().getName();
        if (PlaceholderResolver.LOCAL_KEYS.contains(params)) {
            if (online == null) return "";
            Function<Player, Optional<Season>> local = localSeason;
            return resolver.local(params, local != null ? local.apply(online)
                    : Optional.ofNullable(resolver.value("season_id", world)).flatMap(Season::parse));
        }
        return resolver.resolveOwn(params, world);
    }
}
