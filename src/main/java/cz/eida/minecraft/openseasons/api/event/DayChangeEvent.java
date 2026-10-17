/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.api.event;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** The next calendar day began in a world (or an administrator set the date). */
public final class DayChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final CalendarDate from;
    private final CalendarDate to;

    public DayChangeEvent(World world, CalendarDate from, CalendarDate to) {
        this.world = world;
        this.from = from;
        this.to = to;
    }

    public World getWorld() {
        return world;
    }

    public CalendarDate getFrom() {
        return from;
    }

    public CalendarDate getTo() {
        return to;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
