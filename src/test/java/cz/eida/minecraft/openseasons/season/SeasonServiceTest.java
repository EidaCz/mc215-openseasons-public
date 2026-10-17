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
import cz.eida.minecraft.openseasons.calendar.GameCalendarTestAccess;
import cz.eida.minecraft.openseasons.calendar.MonthDay;
import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonServiceTest {

    /** world clocks (minecraft:time query time) */
    private static final long WORLD_TICKS = 3_964_135_921L;
    private static final long DOOM_TICKS = 1_158_548_260L;

    private final List<String> events = new ArrayList<>();
    private boolean cancel;

    private final SeasonService.Listener listener = new SeasonService.Listener() {
        @Override
        public boolean beforeSeasonChange(String world, Season from, Season to, SeasonService.Cause cause) {
            return !cancel;
        }

        @Override
        public void seasonChanged(String world, Season from, Season to, SeasonService.Cause cause) {
            events.add(world + ":" + from + "->" + to + ":" + cause);
        }

        @Override
        public void dayChanged(String world, CalendarDate from, CalendarDate to) {
            events.add(world + ":" + to);
        }
    };

    private SeasonService service(boolean cycling) {
        GameCalendar cal = GameCalendarTestAccess.standard();
        return new SeasonService(cal, cycling, listener);
    }

    private static CalendarDate date(SeasonService s, String world) {
        return s.state(world).orElseThrow().date();
    }

    private static Season season(SeasonService s, String world) {
        return s.state(world).orElseThrow().season();
    }

    @Test
    void clockDayChangesAtMidnight() {
        assertEquals(0, SeasonService.clockDay(1000));
        assertEquals(0, SeasonService.clockDay(17999));
        assertEquals(1, SeasonService.clockDay(18000));
        assertEquals(1, SeasonService.clockDay(24000));
        assertEquals(2, SeasonService.clockDay(42000));
        assertEquals(165_172, SeasonService.clockDay(WORLD_TICKS));
        // doom was past midnight at the query (time of day 20 260): already the next day
        assertEquals(48_273, SeasonService.clockDay(DOOM_TICKS));
    }

    @Test
    void exactStateFollowsWorldAge() {
        SeasonService s = service(true);
        WorldSeasonState world = s.exactState(SeasonService.clockDay(WORLD_TICKS));
        assertEquals(new CalendarDate(12, 7, 453), world.date());
        assertEquals(Season.SUMMER, world.season());
        assertEquals(OptionalLong.of(0), world.dayOffset());
    }

    @Test
    void stateWithZeroOffsetJumpsToWorldAge() {
        // a saved date with offset 0 → the first synchronisation jumps to the age of the world
        SeasonService s = service(true);
        s.register("doom", new WorldSeasonState(true, Season.FALL, new CalendarDate(29, 10, 129), OptionalLong.of(0)));
        s.syncClock("doom", SeasonService.clockDay(DOOM_TICKS));
        assertEquals(new CalendarDate(4, 4, 133), date(s, "doom"));
        assertEquals(Season.SPRING, season(s, "doom"));
        assertEquals(List.of("doom:4/4/133", "doom:FALL->SPRING:CALENDAR"), events);
    }

    @Test
    void seasonFollowsCalendarRanges() {
        SeasonService s = service(true);
        // summer already on 2/6, but by the calendar it is spring until 3/6;
        // OpenSeasons aligns the season with the calendar when binding to the clock
        s.register("world", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(2, 6, 449)));
        s.syncClock("world", 100);
        assertEquals(List.of("world:SUMMER->SPRING:CALENDAR"), events);
        s.syncClock("world", 101);
        assertEquals(Season.SPRING, season(s, "world"));
        s.syncClock("world", 102);
        assertEquals(new CalendarDate(4, 6, 449), date(s, "world"));
        assertEquals(Season.SUMMER, season(s, "world"));

        // a manually set season lasts until the next day change
        events.clear();
        assertTrue(s.setSeason("world", Season.WINTER, SeasonService.Cause.COMMAND));
        s.syncClock("world", 102);
        assertEquals(Season.WINTER, season(s, "world"));
        s.syncClock("world", 103);
        assertEquals(Season.SUMMER, season(s, "world"));
        assertEquals(List.of("world:SUMMER->WINTER:COMMAND", "world:5/6/449", "world:WINTER->SUMMER:CALENDAR"),
                events);

        s.register("w2", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(2, 9, 1)));
        events.clear();
        s.syncClock("w2", 0);
        s.syncClock("w2", 3);
        assertEquals(Season.FALL, season(s, "w2"));
        assertEquals(List.of("w2:3/9/1", "w2:4/9/1", "w2:SUMMER->FALL:CALENDAR", "w2:5/9/1"), events);
    }

    @Test
    void largeJumpAlignsSeasonAndClockResetKeepsDate() {
        SeasonService s = service(true);
        s.register("w", new WorldSeasonState(true, Season.WINTER, new CalendarDate(1, 1, 1)));
        s.syncClock("w", 0);
        s.syncClock("w", 200);
        assertEquals(new CalendarDate(20, 7, 1), date(s, "w"));
        assertEquals(Season.SUMMER, season(s, "w"));
        assertEquals(List.of("w:20/7/1", "w:WINTER->SUMMER:CALENDAR"), events);

        // the clock moved back (vanilla /time set): the date stands, the offset grows
        events.clear();
        s.syncClock("w", 5);
        assertEquals(new CalendarDate(20, 7, 1), date(s, "w"));
        assertEquals(OptionalLong.of(195), s.state("w").orElseThrow().dayOffset());
        s.syncClock("w", 6);
        assertEquals(new CalendarDate(21, 7, 1), date(s, "w"));
        assertEquals(List.of("w:21/7/1"), events);
    }

    @Test
    void noCyclingAndCancelledChangeKeepSeason() {
        SeasonService s = service(false);
        s.register("w", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(3, 9, 1)));
        s.syncClock("w", 0);
        s.syncClock("w", 1);
        assertEquals(Season.SUMMER, season(s, "w"));

        SeasonService c = service(true);
        c.register("w", new WorldSeasonState(true, Season.SUMMER, new CalendarDate(3, 9, 1)));
        cancel = true;
        c.syncClock("w", 0);
        c.syncClock("w", 1);
        assertEquals(Season.SUMMER, season(c, "w"));
        assertFalse(c.setSeason("w", Season.WINTER, SeasonService.Cause.COMMAND));
    }

    @Test
    void disabledWorldTracksClockSilently() {
        SeasonService s = service(true);
        s.register("w", new WorldSeasonState(false, Season.WINTER, new CalendarDate(1, 1, 1)));
        s.syncClock("w", 0);
        s.syncClock("w", 200);
        assertEquals(new CalendarDate(20, 7, 1), date(s, "w"));
        assertEquals(Season.WINTER, season(s, "w"));
        assertTrue(events.isEmpty(), events::toString);

        // after turning on the season aligns with the calendar
        assertTrue(s.setEnabled("w", true));
        assertEquals(Season.SUMMER, season(s, "w"));
        s.syncClock("w", 205);
        assertEquals(new CalendarDate(25, 7, 1), date(s, "w"));
    }

    @Test
    void setDateKeepsRunningWithClock() {
        SeasonService s = service(true);
        s.register("w", new WorldSeasonState(true, Season.WINTER, new CalendarDate(1, 1, 1)));
        s.syncClock("w", 1000);
        s.setDate("w", new CalendarDate(10, 7, 5), SeasonService.Cause.COMMAND);
        assertEquals(Season.SUMMER, season(s, "w"));
        s.syncClock("w", 1001);
        assertEquals(new CalendarDate(11, 7, 5), date(s, "w"));

        // a world without a loaded clock: the date applies, the offset is computed on load
        s.register("u", new WorldSeasonState(true, Season.WINTER, new CalendarDate(1, 1, 1)));
        s.setDate("u", new CalendarDate(1, 3, 2), SeasonService.Cause.API);
        assertEquals(OptionalLong.empty(), s.state("u").orElseThrow().dayOffset());
        s.syncClock("u", 50);
        assertEquals(new CalendarDate(1, 3, 2), date(s, "u"));
        assertEquals(OptionalLong.of(s.calendar().dayNumber(new CalendarDate(1, 3, 2)) - 50),
                s.state("u").orElseThrow().dayOffset());
    }

    @Test
    void reconfigureKeepsDayNumber() {
        SeasonService s = service(true);
        s.register("w", s.exactState(400));
        s.syncClock("w", 400);
        assertEquals(new CalendarDate(5, 2, 2), date(s, "w"));

        // a year of 100 days: the same day of world age is 1/1 of year 5
        GameCalendar shortYear = new GameCalendar(List.of(new MonthDefinition("Single", 100, 10, 10)),
                List.of("Day"), Map.of(Season.WINTER, new MonthDay(1, 1), Season.SPRING, new MonthDay(1, 26),
                Season.SUMMER, new MonthDay(1, 51), Season.FALL, new MonthDay(1, 76)));
        s.reconfigure(shortYear, true);
        assertEquals(new CalendarDate(1, 1, 5), date(s, "w"));

        s.resetClocks();
        assertEquals(OptionalLong.empty(), s.state("w").orElseThrow().dayOffset());
    }

    @Test
    void newWorldState() {
        SeasonService s = service(true);
        WorldSeasonState first = s.newWorldState(Optional.empty(), Optional.empty());
        assertEquals(new CalendarDate(1, 1, 1), first.date());
        assertEquals(Season.WINTER, first.season());
        WorldSeasonState fall = s.newWorldState(Optional.of(Season.FALL), Optional.empty());
        assertEquals(new CalendarDate(4, 9, 1), fall.date());
        WorldSeasonState synced = s.newWorldState(Optional.of(Season.FALL),
                Optional.of(new WorldSeasonState(true, Season.SUMMER, new CalendarDate(7, 7, 449))));
        assertEquals(new CalendarDate(7, 7, 449), synced.date());
        assertEquals(Season.SUMMER, synced.season());
        assertEquals(OptionalLong.empty(), synced.dayOffset());
    }

    @Test
    void dirtyFlag() {
        SeasonService s = service(true);
        assertFalse(s.consumeDirty());
        s.register("w", new WorldSeasonState(true, Season.WINTER, new CalendarDate(1, 1, 1)));
        assertTrue(s.consumeDirty());
        assertFalse(s.consumeDirty());
        s.syncClock("w", 10);
        assertTrue(s.consumeDirty(), "binding to the clock is a change");
        s.syncClock("w", 10);
        assertFalse(s.consumeDirty());
    }
}
