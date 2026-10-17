/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One event from {@code events.yml} or {@code custom-events.yml}.
 *
 * @param id             key in the file ({@code christmas}, {@code october-event-example} …)
 * @param name           display name, raw with colours {@code &}
 * @param display        shown in {@code /season} and placeholders
 * @param disabledWorlds worlds where the event does not run (lower case)
 * @param startCommands  lines at the start (daily event: on every day change); {@code /} = console command
 * @param stopCommands   lines at the end
 */
public record SeasonEvent(String id, Type type, boolean enabled, String name, boolean display,
                          Set<String> disabledWorlds, EventSchedule schedule,
                          List<String> startCommands, List<String> stopCommands) {

    /** built-in events have their own mechanics, custom ones only commands */
    public enum Type {
        CHRISTMAS("christmas"),
        NEWYEAR("newyear"),
        HALLOWEEN("halloween"),
        EASTER("easter"),
        CUSTOM(null);

        private final String id;

        Type(String id) {
            this.id = id;
        }

        /** key of the built-in event in {@code dated-events}; {@code null} for custom ones */
        public String id() {
            return id;
        }

        public static Type ofBuiltin(String id) {
            for (Type t : values()) {
                if (t.id != null && t.id.equals(id)) return t;
            }
            return CUSTOM;
        }
    }

    public SeasonEvent {
        disabledWorlds = disabledWorlds.stream().map(w -> w.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        startCommands = List.copyOf(startCommands);
        stopCommands = List.copyOf(stopCommands);
    }

    /** enabled and the world is not in {@code disabled-worlds} */
    public boolean allowedIn(String world) {
        return enabled && !disabledWorlds.contains(world.toLowerCase(Locale.ROOT));
    }

    /** daily event: only commands on a day change */
    public boolean daily() {
        return schedule instanceof EventSchedule.Daily;
    }
}
