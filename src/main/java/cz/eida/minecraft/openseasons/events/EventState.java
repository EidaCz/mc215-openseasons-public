/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which events run in the worlds. Without Bukkit: the scheduler passes what should run by the calendar
 * and gets the difference (what begins, what ends). Running events are saved, so after a restart
 * a running event is only resumed and its start commands do not run again.
 *
 * <p>Manually turning on and off ({@code /openseasons debug event}) and a cancelled start (a
 * {@code CalendarEventStartEvent} listener) apply until the next day change in the world, then the calendar decides again.
 * Written from the main thread, readable from anywhere (placeholders).
 */
public final class EventState {

    /** difference to the running events; both in the order in which to apply them */
    public record Change(List<String> started, List<String> stopped) {
        public boolean isEmpty() {
            return started.isEmpty() && stopped.isEmpty();
        }
    }

    private final Map<String, Set<String>> running = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> overrides = new HashMap<>();
    private final Map<String, Set<String>> suppressed = new HashMap<>();

    /** running events of the world in the order in which they began */
    public Set<String> running(String world) {
        return running.getOrDefault(world, Set.of());
    }

    public boolean running(String world, String id) {
        return running(world).contains(id);
    }

    /** worlds where something runs */
    public Set<String> worlds() {
        return Set.copyOf(running.keySet());
    }

    /**
     * What should run in the world: by the calendar ({@code due}), adjusted by manual turning on and off
     * and without cancelled starts.
     */
    public Set<String> target(String world, Set<String> due) {
        Set<String> out = new LinkedHashSet<>(due);
        overrides.getOrDefault(world, Map.of()).forEach((id, on) -> {
            if (on) out.add(id);
            else out.remove(id);
        });
        Set<String> blocked = suppressed.getOrDefault(world, Set.of());
        out.removeIf(id -> blocked.contains(id) && !running(world, id));
        return out;
    }

    /** what begins (in the order of {@code target}) and what ends (from the newest running one) */
    public Change diff(String world, Set<String> target) {
        Set<String> now = running(world);
        List<String> started = new ArrayList<>();
        for (String id : target) if (!now.contains(id)) started.add(id);
        List<String> stopped = new ArrayList<>();
        for (String id : now) if (!target.contains(id)) stopped.add(id);
        Collections.reverse(stopped);
        return new Change(List.copyOf(started), List.copyOf(stopped));
    }

    public void markStarted(String world, String id) {
        running.compute(world, (w, set) -> {
            Set<String> next = set == null ? new LinkedHashSet<>() : new LinkedHashSet<>(set);
            next.add(id);
            return Collections.unmodifiableSet(next);
        });
    }

    public void markStopped(String world, String id) {
        running.computeIfPresent(world, (w, set) -> {
            Set<String> next = new LinkedHashSet<>(set);
            next.remove(id);
            return next.isEmpty() ? null : Collections.unmodifiableSet(next);
        });
    }

    /** manually on ({@code true}) or off until the next day change */
    public void override(String world, String id, boolean on) {
        overrides.computeIfAbsent(world, w -> new LinkedHashMap<>()).put(id, on);
        if (on) {
            Set<String> s = suppressed.get(world);
            if (s != null) s.remove(id);
        }
    }

    /** another plugin cancelled the start: not tried again until the next day change */
    public void suppress(String world, String id) {
        suppressed.computeIfAbsent(world, w -> new LinkedHashSet<>()).add(id);
        Map<String, Boolean> o = overrides.get(world);
        if (o != null && Boolean.TRUE.equals(o.get(id))) o.remove(id);
    }

    /** a new day: manual changes and cancelled starts no longer apply */
    public void newDay(String world) {
        overrides.remove(world);
        suppressed.remove(world);
    }

    public Map<String, List<String>> snapshot() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        running.forEach((w, s) -> out.put(w, List.copyOf(s)));
        return out;
    }

    public void load(Map<String, List<String>> saved) {
        running.clear();
        saved.forEach((w, ids) -> {
            if (!ids.isEmpty()) running.put(w, Collections.unmodifiableSet(new LinkedHashSet<>(ids)));
        });
    }
}
