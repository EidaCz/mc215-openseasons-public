/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.api.event;

import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A calendar event begins in a world (Christmas, Easter, a custom event from {@code custom-events.yml}).
 * Cancelling postpones the start until the next day change: the commands do not run and the built-in mechanics do not start.
 * It is not raised when an event already running is resumed after a restart. Daily events do not raise it.
 */
public final class CalendarEventStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final String eventId;
    private final String eventName;
    private boolean cancelled;

    public CalendarEventStartEvent(World world, String eventId, String eventName) {
        this.world = world;
        this.eventId = eventId;
        this.eventName = eventName;
    }

    public World getWorld() {
        return world;
    }

    /** key of the event in the file ({@code christmas}, {@code newyear}, {@code halloween}, {@code easter} …) */
    public String getEventId() {
        return eventId;
    }

    /** display name, raw with colours {@code &} */
    public String getEventName() {
        return eventName;
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
