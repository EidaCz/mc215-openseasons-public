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
import cz.eida.minecraft.openseasons.calendar.GameCalendarTestAccess;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Planning and transitions of events without a server: day by day like day changes in a world. */
class EventStateTest {

    private final GameCalendar cal = GameCalendarTestAccess.standard();

    private static SeasonEvent dated(String id, String from, String to, boolean display, Set<String> disabledWorlds) {
        return new SeasonEvent(id, SeasonEvent.Type.ofBuiltin(id), true, "&c" + id, display, disabledWorlds,
                new EventSchedule.Dated(EventDate.parse(from, false), EventDate.parse(to, false)),
                List.of("start " + id), List.of("stop " + id));
    }

    private final EventSettings settings = new EventSettings(List.of(
            dated("christmas", "25/12", "28/12", true, Set.of()),
            dated("newyear", "1/1", "1/1", true, Set.of("doom")),
            dated("halloween", "31/10", "2/11", true, Set.of()),
            dated("easter", "20/4", "24/4", true, Set.of()),
            dated("nye", "31/12", "31/12", false, Set.of()),
            new SeasonEvent("sunday", SeasonEvent.Type.CUSTOM, true, "&aSunday", true, Set.of(),
                    new EventSchedule.Weekly(6), List.of(), List.of()),
            new SeasonEvent("midnight", SeasonEvent.Type.CUSTOM, true, "ERROR", false, Set.of(),
                    new EventSchedule.Daily(), List.of("&3Midnight"), List.of()),
            new SeasonEvent("disabled", SeasonEvent.Type.CUSTOM, false, "x", true, Set.of(),
                    new EventSchedule.Weekly(0), List.of(), List.of())),
            EventSettings.Christmas.DEFAULT, EventSettings.NewYear.DEFAULT, EventSettings.Halloween.DEFAULT,
            EventSettings.Easter.DEFAULT, Map.of(), List.of());

    /** walks the days like the planner and records starts and ends */
    private List<String> walk(EventState state, String world, CalendarDate from, int days) {
        List<String> log = new ArrayList<>();
        for (int i = 0; i <= days; i++) {
            CalendarDate d = cal.plusDays(from, i);
            if (i > 0) state.newDay(world);
            EventState.Change c = state.diff(world, state.target(world, EventPlanner.due(settings, cal, world, d)));
            for (String id : c.stopped()) {
                state.markStopped(world, id);
                log.add(d.day() + "/" + d.month() + " -" + id);
            }
            for (String id : c.started()) {
                state.markStarted(world, id);
                log.add(d.day() + "/" + d.month() + " +" + id);
            }
        }
        return log;
    }

    @Test
    void startsAndStopsOnDayEdges() {
        EventState state = new EventState();
        // 7/7/449 is a Monday, so 28/12 is a Sunday; ending goes from the newest event
        List<String> log = walk(state, "world", new CalendarDate(23, 12, 449), 10);
        assertEquals(List.of("25/12 +christmas", "28/12 +sunday", "29/12 -sunday", "29/12 -christmas",
                "31/12 +nye", "1/1 -nye", "1/1 +newyear", "2/1 -newyear"), log);
        assertTrue(state.running("world").isEmpty());
    }

    @Test
    void disabledWorldsAndDisabledEventsNeverRun() {
        EventState state = new EventState();
        List<String> log = walk(state, "doom", new CalendarDate(31, 12, 129), 2);
        assertTrue(log.stream().noneMatch(l -> l.contains("newyear")), log.toString());
        assertTrue(log.stream().noneMatch(l -> l.contains("disabled")), log.toString());
    }

    @Test
    void restartResumesRunningEventsWithoutStartingThemAgain() {
        EventState before = new EventState();
        walk(before, "world", new CalendarDate(24, 12, 449), 1);
        assertEquals(Set.of("christmas"), before.running("world"));

        // restart on 26/12: saved state, Christmas keeps running – no new start or end
        EventState after = new EventState();
        after.load(before.snapshot());
        EventState.Change c = after.diff("world", after.target("world",
                EventPlanner.due(settings, cal, "world", new CalendarDate(26, 12, 449))));
        assertTrue(c.isEmpty(), c.toString());

        // the server was down over the end of Christmas: after the start on 30/12 only the end
        EventState late = new EventState();
        late.load(before.snapshot());
        EventState.Change end = late.diff("world", late.target("world",
                EventPlanner.due(settings, cal, "world", new CalendarDate(30, 12, 449))));
        assertEquals(List.of("christmas"), end.stopped());
        assertTrue(end.started().isEmpty());
    }

    @Test
    void manualStartAndStopLastUntilNextDay() {
        EventState state = new EventState();
        CalendarDate july = new CalendarDate(10, 7, 449);
        state.override("world", "christmas", true);
        EventState.Change on = state.diff("world", state.target("world", EventPlanner.due(settings, cal, "world", july)));
        assertEquals(List.of("christmas"), on.started());
        state.markStarted("world", "christmas");
        // the next day the calendar decides again
        state.newDay("world");
        EventState.Change off = state.diff("world", state.target("world",
                EventPlanner.due(settings, cal, "world", cal.plusDays(july, 1))));
        assertEquals(List.of("christmas"), off.stopped());

        // manual end of the running Christmas
        EventState xmas = new EventState();
        xmas.markStarted("world", "christmas");
        xmas.override("world", "christmas", false);
        assertEquals(List.of("christmas"), xmas.diff("world", xmas.target("world",
                EventPlanner.due(settings, cal, "world", new CalendarDate(26, 12, 449)))).stopped());
    }

    @Test
    void cancelledStartWaitsForNextDay() {
        EventState state = new EventState();
        CalendarDate d = new CalendarDate(25, 12, 449);
        state.suppress("world", "christmas");
        assertTrue(state.diff("world", state.target("world", EventPlanner.due(settings, cal, "world", d))).isEmpty());
        state.newDay("world");
        assertEquals(List.of("christmas"), state.diff("world", state.target("world",
                EventPlanner.due(settings, cal, "world", cal.plusDays(d, 1)))).started());
    }

    @Test
    void nextEventSkipsHiddenAndDailyAndGroupsSameDay() {
        Optional<EventPlanner.Next> next = EventPlanner.next(settings, cal, "world", new CalendarDate(22, 7, 449));
        assertTrue(next.isPresent());
        // the nearest is the weekly Sunday (27/7)
        assertEquals(List.of("sunday"), next.get().events().stream().map(SeasonEvent::id).toList());
        assertEquals(5, next.get().daysUntil());

        EventSettings builtinOnly = new EventSettings(settings.events().subList(0, 5), settings.christmas(),
                settings.newYear(), settings.halloween(), settings.easter(), Map.of(), List.of());
        EventPlanner.Next halloween = EventPlanner.next(builtinOnly, cal, "world", new CalendarDate(22, 7, 449)).orElseThrow();
        assertEquals("halloween", halloween.events().getFirst().id());
        assertEquals(101, halloween.daysUntil());
        // nye is not displayed: on 30/12 the next one is New Year in 2 days
        EventPlanner.Next ny = EventPlanner.next(builtinOnly, cal, "world", new CalendarDate(30, 12, 449)).orElseThrow();
        assertEquals("newyear", ny.events().getFirst().id());
        assertEquals(2, ny.daysUntil());
        // New Year does not run in doom: the next one is Easter
        assertEquals("easter", EventPlanner.next(builtinOnly, cal, "doom", new CalendarDate(30, 12, 129))
                .orElseThrow().events().getFirst().id());

        EventSettings twins = new EventSettings(List.of(dated("a", "5/5", "6/5", true, Set.of()),
                dated("b", "5/5", "5/5", true, Set.of())), settings.christmas(), settings.newYear(),
                settings.halloween(), settings.easter(), Map.of(), List.of());
        EventPlanner.Next both = EventPlanner.next(twins, cal, "world", new CalendarDate(1, 5, 1)).orElseThrow();
        assertEquals(List.of("a", "b"), both.events().stream().map(SeasonEvent::id).toList());
        assertEquals(4, both.daysUntil());
    }

    @Test
    void dailyEventsListedSeparately() {
        assertEquals(List.of("midnight"), EventPlanner.daily(settings, "world").stream().map(SeasonEvent::id).toList());
        assertTrue(EventPlanner.due(settings, cal, "world", new CalendarDate(1, 3, 1)).stream().noneMatch("midnight"::equals));
    }
}
