/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendarTestAccess;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionServiceTest {

    private static final RegionSettings WINTER_NO_EFFECTS = new RegionSettings(Optional.of(Season.WINTER),
            false, true, false);
    private static final RegionSettings NO_BLOCKS = new RegionSettings(Optional.empty(), true, false, true);

    private boolean mainThread = true;
    private boolean worldGuard;
    private final World world = world("world");
    private final World nether = world("world_nether");
    private final SeasonService seasons = new SeasonService(GameCalendarTestAccess.standard(), false, new SeasonService.Listener() {
        @Override
        public boolean beforeSeasonChange(String w, Season from, Season to, SeasonService.Cause cause) {
            return true;
        }

        @Override
        public void seasonChanged(String w, Season from, Season to, SeasonService.Cause cause) {
        }

        @Override
        public void dayChanged(String w, CalendarDate from, CalendarDate to) {
        }
    });
    private final Plugin plugin = plugin();

    /** regions: chunk → settings */
    private final Map<Long, RegionSettings> regions = new HashMap<>();
    private long fingerprint = 1;
    private final RegionSource source = new RegionSource() {
        @Override
        public RegionSettings query(World w, int chunkX, int chunkZ) {
            return regions.getOrDefault(RegionCache.key(chunkX, chunkZ), RegionSettings.NONE);
        }

        @Override
        public long fingerprint(World w) {
            return fingerprint;
        }

        @Override
        public String describe() {
            return "test";
        }
    };

    RegionServiceTest() {
        seasons.register("world", new WorldSeasonState(true, Season.FALL, new CalendarDate(1, 10, 1)));
    }

    @Test
    void withoutWorldGuardEverythingIsGlobal() {
        RegionService.registerFlags(plugin); // nothing without WorldGuard
        RegionService service = RegionService.create(plugin, seasons);

        assertFalse(service.active());
        assertSame(RegionSettings.NONE, service.at(world, 0, 0));
        assertSame(RegionSettings.NONE, service.cached(world, 0, 0));
        assertEquals(Optional.of(Season.FALL), service.seasonAt(world, 0, 0));
        assertEquals(Optional.of(Season.FALL), service.effectSeasonAt(new Location(world, 5, 64, 5)));
        assertTrue(service.blockChangesAllowed(world, 0, 0));
        assertTrue(service.mobSpawningAllowed(world, 0, 0));
        assertEquals(Optional.empty(), service.seasonAt(nether, 0, 0));
    }

    @Test
    void worldGuardWithoutRegisteredFlagsFallsBackToGlobal() {
        worldGuard = true;
        RegionService service = RegionService.create(plugin, seasons);
        assertFalse(service.active());
    }

    @Test
    void regionOverridesTheWorldSeason() {
        RegionService service = new RegionService(plugin, seasons, source);
        regions.put(RegionCache.key(2, -3), WINTER_NO_EFFECTS);
        regions.put(RegionCache.key(0, 0), NO_BLOCKS);
        Location inside = new Location(world, 40, 70, -40);

        assertTrue(service.active());
        assertEquals(Optional.of(Season.WINTER), service.seasonAt(inside));
        assertEquals(Optional.of(Season.WINTER), service.seasonAt(world, 2, -3));
        assertFalse(service.effectsAllowed(inside));
        assertEquals(Optional.empty(), service.effectSeasonAt(inside));
        assertFalse(service.mobSpawningAllowed(inside));
        assertTrue(service.blockChangesAllowed(world, 2, -3));

        assertEquals(Optional.of(Season.FALL), service.seasonAt(world, 0, 0));
        assertFalse(service.blockChangesAllowed(world, 0, 0));
        assertEquals(Optional.of(Season.FALL), service.effectSeasonAt(new Location(world, 1, 64, 1)));
    }

    @Test
    void regionDoesNotAddSeasonsToWorldsWithoutThem() {
        RegionService service = new RegionService(plugin, seasons, source);
        regions.put(RegionCache.key(0, 0), WINTER_NO_EFFECTS);
        assertEquals(Optional.empty(), service.seasonAt(nether, 0, 0));

        seasons.setEnabled("world", false);
        assertEquals(Optional.empty(), service.seasonAt(world, 0, 0));
    }

    @Test
    void otherThreadsOnlyReadTheCacheAndChangesAreReported() {
        RegionService service = new RegionService(plugin, seasons, source);
        List<String> changes = new ArrayList<>();
        service.addChangeListener((w, chunks) -> changes.add(w.getName() + chunks));
        regions.put(RegionCache.key(2, -3), WINTER_NO_EFFECTS);

        mainThread = false;
        assertNull(service.cached(world, 2, -3));
        assertSame(RegionSettings.NONE, service.at(world, 2, -3));
        assertNull(service.cached(world, 0, 0));

        mainThread = true;
        service.drainPending();
        // only a chunk with other than default settings has to be sent again
        assertEquals(List.of("world[ChunkPos[x=2, z=-3]]"), changes);
        mainThread = false;
        assertSame(WINTER_NO_EFFECTS, service.cached(world, 2, -3));
        assertSame(WINTER_NO_EFFECTS, service.at(world, 2, -3));
        assertSame(RegionSettings.NONE, service.cached(world, 0, 0));
    }

    @Test
    void regionChangeIsDetectedAndReported() {
        RegionService service = new RegionService(plugin, seasons, source);
        List<String> changes = new ArrayList<>();
        service.addChangeListener((w, chunks) -> changes.add(w.getName() + chunks));
        service.at(world, 0, 0);
        service.at(world, 1, 0);

        service.checkRegions();
        assertTrue(changes.isEmpty());

        // /rg flag … permanent-season WINTER
        regions.put(RegionCache.key(1, 0), WINTER_NO_EFFECTS);
        fingerprint = 2;
        service.checkRegions();
        assertEquals(List.of("world[ChunkPos[x=1, z=0]]"), changes);
        assertEquals(Optional.of(Season.WINTER), service.seasonAt(world, 1, 0));
    }

    private Plugin plugin() {
        PluginManager pm = (PluginManager) Proxy.newProxyInstance(PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isPluginEnabled" -> worldGuard && "WorldGuard".equals(args[0]);
                    case "getPlugin" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPrimaryThread" -> mainThread;
                    case "getPluginManager" -> pm;
                    case "getWorld" -> "world".equals(args[0]) ? world : "world_nether".equals(args[0]) ? nether : null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Logger log = Logger.getAnonymousLogger();
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServer" -> server;
                    case "getLogger" -> log;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "World[" + name + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    // --- real calendar (real-calendar.yml) ---

    private static final java.time.LocalDate OCT_17 = java.time.LocalDate.of(2026, 10, 17);
    private static final java.time.LocalDate OCT_30 = java.time.LocalDate.of(2026, 10, 30);
    private static final java.time.LocalDate JAN_15 = java.time.LocalDate.of(2027, 1, 15);

    private static cz.eida.minecraft.openseasons.config.RealCalendarSettings realCalendar(
            cz.eida.minecraft.openseasons.season.SeasonSource worldSource) {
        cz.eida.minecraft.openseasons.calendar.RealPeriod halloween = new cz.eida.minecraft.openseasons.calendar.RealPeriod(
                "halloween", java.time.MonthDay.of(10, 25), java.time.MonthDay.of(11, 4), Optional.of(Season.FALL),
                java.util.OptionalDouble.of(70), List.of("halloween"), List.of("world"));
        return new cz.eida.minecraft.openseasons.config.RealCalendarSettings(
                cz.eida.minecraft.openseasons.calendar.RealCalendar.ASTRONOMICAL, Map.of("world", worldSource), List.of(halloween));
    }

    private static RegionSettings region(Optional<Season> permanent, cz.eida.minecraft.openseasons.season.SeasonSource source,
                                         java.util.Set<String> periods) {
        return new RegionSettings(permanent, true, true, true,
                Optional.ofNullable(source), periods);
    }

    @Test
    void worldFollowsRealSeasonsAndPeriods() {
        RegionService service = new RegionService(plugin, seasons, source);
        // game calendar: autumn (1/10/1); real: 15/1 winter
        service.setRealCalendar(realCalendar(cz.eida.minecraft.openseasons.season.SeasonSource.REAL));
        service.setToday(JAN_15);
        RegionService.Local l = service.localAt(world, 0, 0).orElseThrow();
        assertEquals(Season.WINTER, l.season());
        assertEquals(RegionService.Origin.REAL, l.origin());
        assertEquals(Season.WINTER, service.worldLocal(world).orElseThrow().season());
        // Halloween of the whole world takes precedence over the real season
        service.setToday(OCT_30);
        l = service.localAt(world, 0, 0).orElseThrow();
        assertEquals(RegionService.Origin.PERIOD, l.origin());
        assertEquals(70.0, l.progress());
        // world by the game
        service.setRealCalendar(realCalendar(cz.eida.minecraft.openseasons.season.SeasonSource.GAME));
        service.setToday(OCT_17);
        assertEquals(RegionService.Origin.GAME, service.localAt(world, 0, 0).orElseThrow().origin());
        assertEquals(Optional.of(Season.FALL), service.seasonAt(world, 0, 0));
        // a world without seasons gets nothing
        assertEquals(Optional.empty(), service.localAt(nether, 0, 0));
    }

    /** region before world, in a region: range > permanent-season > season-source */
    @Test
    void regionBeforeWorld() {
        worldGuard = true;
        RegionService service = new RegionService(plugin, seasons, source);
        service.setRealCalendar(realCalendar(cz.eida.minecraft.openseasons.season.SeasonSource.GAME));
        service.setToday(OCT_30);
        // a spawn with permanent spring stays spring even during Halloween of the whole world
        regions.put(RegionCache.key(1, 0), region(Optional.of(Season.SPRING), null, java.util.Set.of()));
        assertEquals(Optional.of(Season.SPRING), service.seasonAt(world, 1, 0));
        assertEquals(RegionService.Origin.PERIOD, service.localAt(world, 0, 0).orElseThrow().origin());
        // a region range overrides the permanent season
        regions.put(RegionCache.key(2, 0), region(Optional.of(Season.SPRING), null, java.util.Set.of("halloween")));
        RegionService.Local l = service.localAt(world, 2, 0).orElseThrow();
        assertEquals(Season.FALL, l.season());
        assertEquals("halloween", l.periodId());
        // season-source REAL: 15/1 winter, although the game calendar has autumn
        regions.put(RegionCache.key(3, 0), region(Optional.empty(), cz.eida.minecraft.openseasons.season.SeasonSource.REAL,
                java.util.Set.of()));
        service.setToday(JAN_15);
        assertEquals(Optional.of(Season.WINTER), service.seasonAt(world, 3, 0));
        // season-source GAME in a world with the real season: game calendar
        service.setRealCalendar(realCalendar(cz.eida.minecraft.openseasons.season.SeasonSource.REAL));
        service.setToday(JAN_15);
        regions.put(RegionCache.key(4, 0), region(Optional.empty(), cz.eida.minecraft.openseasons.season.SeasonSource.GAME,
                java.util.Set.of()));
        assertEquals(Optional.of(Season.FALL), service.seasonAt(world, 4, 0));
        assertTrue(service.forcesGame(service.at(world, 4, 0)));
        assertEquals(Optional.of(Season.WINTER), service.seasonAt(world, 0, 0));
    }

    @Test
    void regionEventsOnlyInRegionsWithThePeriod() {
        worldGuard = true;
        RegionService service = new RegionService(plugin, seasons, source);
        service.setRealCalendar(realCalendar(cz.eida.minecraft.openseasons.season.SeasonSource.GAME));
        regions.put(RegionCache.key(5, 0), region(Optional.empty(), null, java.util.Set.of("halloween")));
        Location inside = new Location(world, 5 * 16 + 3, 64, 4);
        Location outside = new Location(world, 3, 64, 4);
        service.setToday(OCT_30);
        assertTrue(service.regionEventAt(inside, "halloween"));
        assertTrue(service.regionEventAt(inside, "HALLOWEEN"));
        assertFalse(service.regionEventAt(inside, "christmas"));
        assertFalse(service.regionEventAt(outside, "halloween"));
        service.setToday(java.time.LocalDate.of(2026, 11, 5));
        assertFalse(service.regionEventAt(inside, "halloween"));
    }
}
