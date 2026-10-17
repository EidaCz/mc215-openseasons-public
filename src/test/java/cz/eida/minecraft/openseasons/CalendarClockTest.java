/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import cz.eida.minecraft.openseasons.season.SeasonService;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.ServerTickManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time control without a server: world, server and players are proxies of the Bukkit interfaces, the gamerules are
 * kept by {@link FakeWorld}. The clock only moves via {@code setFullTime}, as with {@code advance_time} off.
 */
class CalendarClockTest {

    private static final MonthDefinition JANUARY = new MonthDefinition("January", 31, 7, 13);
    private static final MonthDefinition JULY = new MonthDefinition("July", 31, 13, 7);

    private final Map<String, FakeWorld> worlds = new HashMap<>();
    private MonthDefinition month = JULY;
    private boolean paused;
    private boolean frozen;
    private CalendarClock clock;

    /** world state behind the proxy */
    private static final class FakeWorld {
        final String name;
        long fullTime;
        boolean advanceTime = true;
        int sleepingPercentage = 100;
        int writes;
        boolean hasClock = true;
        final List<Player> players = new ArrayList<>();
        World proxy;

        FakeWorld(String name, long fullTime) {
            this.name = name;
            this.fullTime = fullTime;
        }
    }

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        clock = new CalendarClock(server(), logger, w -> Optional.of(month), new CalendarClock.Rules() {
            @Override
            public boolean advanceTime(World world) {
                return fake(world).advanceTime;
            }

            @Override
            public void setAdvanceTime(World world, boolean value) {
                fake(world).advanceTime = value;
            }

            @Override
            public int sleepingPercentage(World world) {
                return fake(world).sleepingPercentage;
            }
        });
    }

    @Test
    void takingOverTurnsAdvanceTimeOffAndReleasingBackOn() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        assertTrue(clock.drives("world"));
        assertFalse(w.advanceTime);

        clock.update(w.proxy, false);
        assertFalse(clock.drives("world"));
        assertTrue(w.advanceTime);
    }

    @Test
    void worldLeftFrozenByAnotherPluginIsTakenOver() {
        FakeWorld w = world("world", 0);
        w.advanceTime = false;
        clock.update(w.proxy, true);
        assertTrue(clock.drives("world"));
        assertFalse(w.advanceTime);
        tick(100);
        assertTrue(w.fullTime > 0);
    }

    @Test
    void notDrivenWorldIsLeftAlone() {
        FakeWorld w = world("lobby", 5000);
        w.advanceTime = false;
        clock.update(w.proxy, false);
        tick(100);
        assertFalse(w.advanceTime);
        assertEquals(5000, w.fullTime);
        assertEquals(0, w.writes);
    }

    @Test
    void advanceTimeTurnedOnAgainIsSwitchedOff() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        w.advanceTime = true;
        clock.update(w.proxy, true);
        assertFalse(w.advanceTime);
    }

    @Test
    void summerDayRunsSlowerThanVanilla() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        tick(13 * 1200);
        assertEquals(12000, w.fullTime, 1);
        // a long day: a write only when moving by a whole tick
        assertTrue(w.writes < 13 * 1200);
        assertEquals(12000, w.writes, 1);
        tick(7 * 1200);
        assertEquals(24000, w.fullTime, 1);
    }

    @Test
    void winterNightRunsSlower() {
        month = JANUARY;
        FakeWorld w = world("world", 12000);
        clock.update(w.proxy, true);
        tick(13 * 1200);
        assertEquals(24000, w.fullTime, 1);
    }

    @Test
    void externalTimeChangesAreKept() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        tick(10);
        w.fullTime = 18000;
        tick(1);
        assertTrue(w.fullTime >= 18000 && w.fullTime <= 18002);
    }

    @Test
    void sleepingPlayersSkipTheNight() {
        FakeWorld w = world("world", 10 * 24000L + 14000);
        w.players.add(player(true, true, false));
        clock.update(w.proxy, true);
        long dayBefore = SeasonService.clockDay(w.fullTime);
        tick(1);
        assertEquals(11 * 24000L, w.fullTime);
        assertEquals(dayBefore + 1, SeasonService.clockDay(w.fullTime));
    }

    @Test
    void notEnoughSleepersKeepsTheNight() {
        FakeWorld w = world("world", 10 * 24000L + 14000);
        w.players.add(player(true, true, false));
        w.players.add(player(false, false, false));
        clock.update(w.proxy, true);
        tick(1);
        assertTrue(w.fullTime < 10 * 24000L + 14003);

        w.sleepingPercentage = 50;
        tick(1);
        assertEquals(11 * 24000L, w.fullTime);
    }

    @Test
    void spectatorsDoNotBlockSleep() {
        FakeWorld w = world("world", 10 * 24000L + 14000);
        w.players.add(player(true, true, false));
        w.players.add(player(false, false, true));
        clock.update(w.proxy, true);
        tick(1);
        assertEquals(11 * 24000L, w.fullTime);
    }

    @Test
    void frozenOrPausedServerStopsTheClock() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        frozen = true;
        tick(100);
        assertEquals(0, w.fullTime);
        frozen = false;
        paused = true;
        tick(100);
        assertEquals(0, w.fullTime);
        paused = false;
        tick(100);
        assertTrue(w.fullTime > 0);
    }

    @Test
    void releaseAllRestoresVanillaTime() {
        FakeWorld a = world("world", 0);
        FakeWorld b = world("doom", 0);
        clock.update(a.proxy, true);
        clock.update(b.proxy, true);
        clock.releaseAll();
        assertTrue(a.advanceTime);
        assertTrue(b.advanceTime);
        assertFalse(clock.drives("world"));
        assertFalse(clock.drives("doom"));
    }

    @Test
    void worldWithoutClockIsGivenUp() {
        FakeWorld w = world("custom", 0);
        w.hasClock = false;
        clock.update(w.proxy, true);
        // the first write (summer: only the second tick) fails
        tick(2);
        assertFalse(clock.drives("custom"));
        assertTrue(w.advanceTime);
        clock.update(w.proxy, true);
        assertFalse(clock.drives("custom"));
    }

    @Test
    void pausedDrivenWorldStandsStill() {
        FakeWorld w = world("world", 3000);
        clock.update(w.proxy, true);
        assertTrue(clock.togglePause(w.proxy));
        assertTrue(clock.paused("world"));
        tick(1200);
        assertEquals(3000, w.fullTime);
        assertEquals(0, w.writes);
        // a driven world: advance_time stays off, OpenSeasons keeps the clock
        assertFalse(w.advanceTime);
        assertTrue(clock.drives("world"));

        assertFalse(clock.togglePause(w.proxy));
        assertFalse(clock.paused("world"));
        tick(100);
        assertTrue(w.fullTime > 3000);
        assertFalse(w.advanceTime);
    }

    @Test
    void sleepDoesNotSkipTheNightWhilePaused() {
        FakeWorld w = world("world", 10 * 24000L + 14000);
        w.players.add(player(true, true, false));
        clock.update(w.proxy, true);
        clock.togglePause(w.proxy);
        tick(20);
        assertEquals(10 * 24000L + 14000, w.fullTime);
    }

    @Test
    void timeSetWhilePausedStays() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        clock.togglePause(w.proxy);
        w.fullTime = 18000;
        tick(100);
        assertEquals(18000, w.fullTime);
    }

    @Test
    void pauseInNotDrivenWorldTogglesAdvanceTime() {
        FakeWorld w = world("lobby", 5000);
        clock.update(w.proxy, false);
        assertTrue(clock.togglePause(w.proxy));
        assertFalse(w.advanceTime);
        assertTrue(clock.paused("lobby"));
        clock.update(w.proxy, false);
        assertTrue(clock.paused("lobby"));

        assertFalse(clock.togglePause(w.proxy));
        assertTrue(w.advanceTime);
        assertFalse(clock.paused("lobby"));
        assertFalse(clock.drives("lobby"));
    }

    @Test
    void notDrivenWorldStoppedByOthersIsResumed() {
        // advance_time turned off from elsewhere: pausetime toggles it
        FakeWorld w = world("lobby", 5000);
        w.advanceTime = false;
        clock.update(w.proxy, false);
        assertFalse(clock.togglePause(w.proxy));
        assertTrue(w.advanceTime);
        assertFalse(clock.paused("lobby"));
    }

    @Test
    void advanceTimeTurnedOnInNotDrivenWorldEndsThePause() {
        FakeWorld w = world("lobby", 5000);
        clock.update(w.proxy, false);
        clock.togglePause(w.proxy);
        clock.consumePauseChanges();
        w.advanceTime = true;
        clock.update(w.proxy, false);
        assertFalse(clock.paused("lobby"));
        assertTrue(clock.consumePauseChanges());
    }

    @Test
    void releasingPausedWorldKeepsTimeStopped() {
        FakeWorld w = world("world", 3000);
        clock.update(w.proxy, true);
        clock.togglePause(w.proxy);
        clock.update(w.proxy, false);
        assertFalse(clock.drives("world"));
        assertFalse(w.advanceTime);
        assertTrue(clock.paused("world"));

        // a world taken over again (seasons on) stays stopped
        clock.update(w.proxy, true);
        tick(100);
        assertEquals(3000, w.fullTime);

        clock.releaseAll();
        assertFalse(w.advanceTime);
        assertTrue(clock.paused("world"));
    }

    @Test
    void advanceTimeTurnedOnInPausedDrivenWorldIsSwitchedOff() {
        FakeWorld w = world("world", 3000);
        clock.update(w.proxy, true);
        clock.togglePause(w.proxy);
        w.advanceTime = true;
        clock.update(w.proxy, true);
        assertFalse(w.advanceTime);
        assertTrue(clock.paused("world"));
    }

    @Test
    void restoredPauseHoldsAfterRestart() {
        clock.restorePaused(List.of("world", "unloaded"));
        assertFalse(clock.consumePauseChanges());
        FakeWorld w = world("world", 3000);
        w.advanceTime = false;
        clock.update(w.proxy, true);
        tick(100);
        assertEquals(3000, w.fullTime);
        assertEquals(java.util.Set.of("world", "unloaded"), clock.pausedWorlds());
    }

    @Test
    void pauseChangesAreReportedOnce() {
        FakeWorld w = world("world", 0);
        clock.update(w.proxy, true);
        assertFalse(clock.consumePauseChanges());
        clock.togglePause(w.proxy);
        assertTrue(clock.consumePauseChanges());
        assertFalse(clock.consumePauseChanges());
        clock.togglePause(w.proxy);
        assertTrue(clock.consumePauseChanges());
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) clock.tick();
    }

    private FakeWorld fake(World world) {
        return worlds.get(world.getName());
    }

    private FakeWorld world(String name, long fullTime) {
        FakeWorld w = new FakeWorld(name, fullTime);
        w.proxy = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> w.name;
                    case "getFullTime" -> w.fullTime;
                    case "setFullTime" -> {
                        if (!w.hasClock) throw new IllegalArgumentException("the world has no clock");
                        w.fullTime = (long) args[0];
                        w.writes++;
                        yield null;
                    }
                    case "getPlayers" -> List.copyOf(w.players);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "World[" + w.name + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        worlds.put(name, w);
        return w;
    }

    private static Player player(boolean inBed, boolean deeplySleeping, boolean spectator) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isSleeping" -> inBed;
                    case "isDeeplySleeping" -> deeplySleeping;
                    case "isSleepingIgnored" -> false;
                    case "getGameMode" -> spectator ? GameMode.SPECTATOR : GameMode.SURVIVAL;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Server server() {
        ServerTickManager ticks = (ServerTickManager) Proxy.newProxyInstance(ServerTickManager.class.getClassLoader(),
                new Class<?>[]{ServerTickManager.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isRunningNormally" -> !frozen;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> args[0] instanceof String name && worlds.containsKey(name)
                            ? worlds.get(name).proxy : null;
                    case "isPaused" -> paused;
                    case "getServerTickManager" -> ticks;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
