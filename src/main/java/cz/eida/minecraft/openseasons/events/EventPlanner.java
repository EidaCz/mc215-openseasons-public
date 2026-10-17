/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Which events should run in a world on a given date and which one begins next. Without Bukkit, purely
 * from the settings and the calendar.
 */
public final class EventPlanner {

    /** the nearest start: events beginning on that day (in file order), and in how many days */
    public record Next(List<SeasonEvent> events, int daysUntil) {
    }

    private EventPlanner() {
    }

    /** ids of the events that should run in the world (without daily ones) */
    public static Set<String> due(EventSettings settings, GameCalendar calendar, String world, CalendarDate date) {
        Set<String> out = new LinkedHashSet<>();
        for (SeasonEvent e : settings.events()) {
            if (!e.daily() && e.allowedIn(world) && e.schedule().activeOn(date, calendar)) out.add(e.id());
        }
        return out;
    }

    /** daily events that run in the world on a day change */
    public static List<SeasonEvent> daily(EventSettings settings, String world) {
        List<SeasonEvent> out = new ArrayList<>();
        for (SeasonEvent e : settings.events()) {
            if (e.daily() && e.allowedIn(world)) out.add(e);
        }
        return out;
    }

    /**
     * The nearest displayed event that begins after today (at most a year ahead). Several events
     * beginning on the same day are all returned.
     */
    public static Optional<Next> next(EventSettings settings, GameCalendar calendar, String world, CalendarDate date) {
        int best = Integer.MAX_VALUE;
        List<SeasonEvent> events = new ArrayList<>();
        for (SeasonEvent e : settings.events()) {
            if (!e.display() || e.daily() || !e.allowedIn(world)) continue;
            OptionalInt d = e.schedule().daysUntilStart(date, calendar, calendar.daysInYear());
            if (d.isEmpty() || d.getAsInt() > best) continue;
            if (d.getAsInt() < best) {
                best = d.getAsInt();
                events.clear();
            }
            events.add(e);
        }
        return events.isEmpty() ? Optional.empty() : Optional.of(new Next(List.copyOf(events), best));
    }
}
