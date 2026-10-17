/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.api.event.DayChangeEvent;
import cz.eida.minecraft.openseasons.api.event.SeasonChangeEvent;
import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Changes from {@link SeasonService} as Bukkit events and messages to players. */
final class BukkitSeasonEvents implements SeasonService.Listener {

    /** texts in lang.yml: {@code &a} and {@code &#RRGGBB} */
    static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final OpenSeasonsPlugin plugin;

    BukkitSeasonEvents(OpenSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean beforeSeasonChange(String world, Season from, Season to, SeasonService.Cause cause) {
        World w = Bukkit.getWorld(world);
        if (w == null) return true;
        SeasonChangeEvent event = new SeasonChangeEvent(w, from, to, cause);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public void seasonChanged(String world, Season from, Season to, SeasonService.Cause cause) {
        plugin.getLogger().info("World " + world + ": " + from + " → " + to + " (" + cause + ")");
        LoadedConfig config = plugin.config();
        if (cause != SeasonService.Cause.CALENDAR || !config.main().cycling().sendMessage()) return;
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        Component message = LEGACY.deserialize(config.lang().seasonStartMessage(to));
        w.getPlayers().forEach(p -> p.sendMessage(message));
    }

    @Override
    public void dayChanged(String world, CalendarDate from, CalendarDate to) {
        World w = Bukkit.getWorld(world);
        if (w != null) Bukkit.getPluginManager().callEvent(new DayChangeEvent(w, from, to));
    }
}
