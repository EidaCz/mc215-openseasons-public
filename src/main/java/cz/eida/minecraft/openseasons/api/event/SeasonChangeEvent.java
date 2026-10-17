/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.api.event;

import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** The season of a world changes. Cancelling it prevents the change. */
public final class SeasonChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final Season oldSeason;
    private final Season newSeason;
    private final SeasonService.Cause cause;
    private boolean cancelled;

    public SeasonChangeEvent(World world, Season oldSeason, Season newSeason, SeasonService.Cause cause) {
        this.world = world;
        this.oldSeason = oldSeason;
        this.newSeason = newSeason;
        this.cause = cause;
    }

    public World getWorld() {
        return world;
    }

    public Season getOldSeason() {
        return oldSeason;
    }

    public Season getNewSeason() {
        return newSeason;
    }

    public SeasonService.Cause getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
