/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.api.event;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A calendar event ended in a world (after its last day, manually, or after a configuration change). */
public final class CalendarEventEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final String eventId;
    private final String eventName;

    public CalendarEventEndEvent(World world, String eventId, String eventName) {
        this.world = world;
        this.eventId = eventId;
        this.eventName = eventName;
    }

    public World getWorld() {
        return world;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
