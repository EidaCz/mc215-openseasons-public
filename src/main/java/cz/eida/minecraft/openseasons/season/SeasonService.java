/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.season;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Seasons and dates of worlds. Without Bukkit: it reports events through {@link Listener}, so it can
 * be tested without a server. Written from the main thread, readable (placeholders) from anywhere.
 *
 * <p>The date follows the world clock ({@link #syncClock}), see {@link WorldSeasonState}.
 */
public final class SeasonService {

    /** a bigger clock jump is not caught up day by day, the date jumps directly */
    public static final int MAX_CATCH_UP = 30;

    public enum Cause {
        /** season start in the calendar */
        CALENDAR,
        /** administrator command */
        COMMAND,
        /** another plugin */
        API
    }

    public interface Listener {
        /** @return false cancels the change */
        boolean beforeSeasonChange(String world, Season from, Season to, Cause cause);

        void seasonChanged(String world, Season from, Season to, Cause cause);

        void dayChanged(String world, CalendarDate from, CalendarDate to);
    }

    private final Map<String, WorldSeasonState> states = new ConcurrentHashMap<>();
    /** game day of the clock of loaded worlds at the last synchronisation (runtime only) */
    private final Map<String, Long> clockDays = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final Listener listener;
    private volatile GameCalendar calendar;
    private volatile boolean automaticCycling;

    public SeasonService(GameCalendar calendar, boolean automaticCycling, Listener listener) {
        this.calendar = calendar;
        this.automaticCycling = automaticCycling;
        this.listener = listener;
    }

    public GameCalendar calendar() {
        return calendar;
    }

    public boolean automaticCycling() {
        return automaticCycling;
    }

    /**
     * Game day from {@code World#getFullTime()}. The day changes at midnight (tick 18000), so sleeping
     * through the night moves the date by exactly one day.
     */
    public static long clockDay(long fullTime) {
        return Math.floorDiv(fullTime + 6000, 24000);
    }

    /**
     * After a configuration reload: a new calendar. Worlds bound to their clock get the date of their day
     * in the new calendar, the others are moved into its bounds.
     */
    public void reconfigure(GameCalendar newCalendar, boolean newAutomaticCycling) {
        this.calendar = newCalendar;
        this.automaticCycling = newAutomaticCycling;
        states.replaceAll((w, s) -> {
            Long clock = clockDays.get(w);
            if (clock != null && s.dayOffset().isPresent()) {
                return s.withDate(newCalendar.dateOfDayNumber(clock + s.dayOffset().getAsLong()));
            }
            return normalized(s);
        });
        dirty.set(true);
        states.keySet().forEach(this::align);
    }

    public Optional<WorldSeasonState> state(String world) {
        return Optional.ofNullable(states.get(world));
    }

    public Set<String> worlds() {
        return Set.copyOf(states.keySet());
    }

    /** takes over saved states (at start) */
    public void load(Map<String, WorldSeasonState> loaded) {
        loaded.forEach((w, s) -> states.put(w, normalized(s)));
    }

    /** state for saving */
    public Map<String, WorldSeasonState> snapshot() {
        return new LinkedHashMap<>(states);
    }

    /** returns true and clears the flag when something changed since the last call */
    public boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    /** saving failed, try again */
    public void markDirty() {
        dirty.set(true);
    }

    /** creates a world that has no state yet */
    public WorldSeasonState register(String world, WorldSeasonState initial) {
        WorldSeasonState existing = states.putIfAbsent(world, normalized(initial));
        if (existing != null) return existing;
        dirty.set(true);
        return states.get(world);
    }

    /**
     * State whose date exactly matches the age of the world: the clock day since January 1 of year 1, the season by
     * the calendar, offset 0.
     */
    public WorldSeasonState exactState(long clockDay) {
        CalendarDate date = calendar.dateOfDayNumber(clockDay);
        return new WorldSeasonState(true, calendar.seasonAt(date), date, OptionalLong.of(0));
    }

    /**
     * Default state of a new world: date and season from another world, or the start of the chosen season,
     * or day 1 of year 1 with the season by the calendar. The date applies, the offset to the clock is computed.
     */
    public WorldSeasonState newWorldState(Optional<Season> defaultSeason, Optional<WorldSeasonState> syncWith) {
        if (syncWith.isPresent()) return new WorldSeasonState(true, syncWith.get().season(), syncWith.get().date());
        if (defaultSeason.isPresent()) {
            Season s = defaultSeason.get();
            return new WorldSeasonState(true, s, calendar.dateOf(calendar.start(s), 1));
        }
        CalendarDate first = new CalendarDate(1, 1, 1);
        return new WorldSeasonState(true, calendar.seasonAt(first), first);
    }

    /**
     * Aligns the date of a world with its clock: day number = game day of the clock + offset. Day by day (at most
     * {@link #MAX_CATCH_UP}) it raises day changes and season starts; a bigger jump moves the date directly
     * and with automatic cycling aligns the season with the calendar. A clock set back (smaller day than before)
     * does not move the date back, it only increases the offset. A disabled world changes its date silently. An unknown world is ignored.
     */
    public void syncClock(String world, long clockDay) {
        WorldSeasonState s = states.get(world);
        if (s == null) return;
        Long last = clockDays.put(world, clockDay);
        if (last != null && last == clockDay && s.dayOffset().isPresent()) return;

        long offset;
        if (s.dayOffset().isEmpty()) {
            offset = calendar.dayNumber(s.date()) - clockDay;
        } else if (last != null && clockDay < last) {
            offset = s.dayOffset().getAsLong() + (last - clockDay);
        } else {
            offset = s.dayOffset().getAsLong();
        }
        if (s.dayOffset().isEmpty() || s.dayOffset().getAsLong() != offset) {
            states.put(world, s.withDayOffset(OptionalLong.of(offset)));
            dirty.set(true);
        }
        moveTo(world, calendar.dateOfDayNumber(clockDay + offset));
        if (last == null) align(world);
    }

    /** the world was unloaded: on the next load the clock is taken again */
    public void forgetClock(String world) {
        clockDays.remove(world);
    }

    private void moveTo(String world, CalendarDate target) {
        WorldSeasonState s = states.get(world);
        long delta = calendar.dayNumber(target) - calendar.dayNumber(s.date());
        if (delta == 0) return;
        if (!s.enabled()) {
            states.put(world, s.withDate(target));
            dirty.set(true);
        } else if (delta > 0 && delta <= MAX_CATCH_UP) {
            for (long i = 0; i < delta; i++) nextDay(world);
        } else {
            jump(world, target, Cause.CALENDAR);
        }
    }

    /** next day; with automatic cycling the season is aligned with the range in the calendar */
    private void nextDay(String world) {
        WorldSeasonState s = states.get(world);
        CalendarDate to = calendar.plusDays(s.date(), 1);
        states.put(world, s.withDate(to));
        dirty.set(true);
        listener.dayChanged(world, s.date(), to);
        align(world);
    }

    /**
     * With automatic cycling aligns the season of an enabled world with the calendar: the season whose
     * range (from its start to the day before the next start) contains the date applies. A manually set season
     * therefore lasts until the next day change. The change can be cancelled by an event.
     */
    private void align(String world) {
        WorldSeasonState s = states.get(world);
        if (s == null || !automaticCycling || !s.enabled()) return;
        setSeason(world, calendar.seasonAt(s.date()), Cause.CALENDAR);
    }

    /** jump in the date: one day change, with automatic cycling the season is aligned with the calendar */
    private void jump(String world, CalendarDate to, Cause cause) {
        WorldSeasonState s = states.get(world);
        states.put(world, s.withDate(to));
        dirty.set(true);
        listener.dayChanged(world, s.date(), to);
        if (automaticCycling && s.enabled()) setSeason(world, calendar.seasonAt(to), cause);
    }

    /** @return false when the world does not exist, the season already applies or someone cancelled the change */
    public boolean setSeason(String world, Season season, Cause cause) {
        WorldSeasonState s = states.get(world);
        if (s == null || s.season() == season) return false;
        if (!listener.beforeSeasonChange(world, s.season(), season, cause)) return false;
        states.put(world, s.withSeason(season));
        dirty.set(true);
        listener.seasonChanged(world, s.season(), season, cause);
        return true;
    }

    /**
     * Sets the date; the offset to the clock changes so that the date keeps running from the new day. With
     * automatic cycling the season is aligned with the calendar (as on every date jump), otherwise it
     * stays.
     */
    public boolean setDate(String world, CalendarDate date, Cause cause) {
        WorldSeasonState s = states.get(world);
        if (s == null) return false;
        CalendarDate to = calendar.normalize(date);
        Long clock = clockDays.get(world);
        states.put(world, s.withDayOffset(clock == null ? OptionalLong.empty()
                : OptionalLong.of(calendar.dayNumber(to) - clock)));
        jump(world, to, cause);
        return true;
    }

    /**
     * Enables or disables seasons. After enabling, with automatic cycling the season is aligned
     * with the calendar, because only the date was running while disabled.
     */
    public boolean setEnabled(String world, boolean enabled) {
        WorldSeasonState s = states.get(world);
        if (s == null || s.enabled() == enabled) return false;
        states.put(world, s.withEnabled(enabled));
        dirty.set(true);
        if (enabled && automaticCycling) setSeason(world, calendar.seasonAt(s.date()), Cause.CALENDAR);
        return true;
    }

    /** after a change of the clock kind (game ↔ real days): offsets are invalid, dates stay and are bound again */
    public void resetClocks() {
        clockDays.clear();
        states.replaceAll((w, s) -> s.withDayOffset(OptionalLong.empty()));
        dirty.set(true);
    }

    private WorldSeasonState normalized(WorldSeasonState s) {
        CalendarDate d = calendar.normalize(s.date());
        return d.equals(s.date()) ? s : s.withDate(d);
    }
}
