/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import cz.eida.minecraft.openseasons.time.DayCycle;
import cz.eida.minecraft.openseasons.time.NightSkip;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Time in worlds where OpenSeasons drives it by the calendar: day and night length by the month of the
 * current world date ({@link DayCycle}). The vanilla time advance ({@code advance_time}) is off in these
 * worlds; every tick the timer moves the clock by whole ticks (written only on a change, fractions are
 * accumulated). When OpenSeasons releases a world (seasons turned off, {@code modify-daynight-length:
 * false}, plugin disabled), it turns {@code advance_time} on again.
 *
 * <p>Sleeping: with {@code advance_time} off vanilla does not skip the night, it only wakes the players
 * and clears the weather. The plugin scheduler runs in the server tick before the world ticks, so the
 * timer moves the clock to the morning by the same rule ({@link NightSkip}) before vanilla wakes the
 * players in the same tick. That is why it runs every tick. Like vanilla, the clock stands still during
 * {@code /tick freeze} and the pause of an empty server.
 *
 * <p>Stopped time ({@code /openseasons pausetime}) is the administrator's intent for a world, whoever
 * drives the time: in a driven world the timer does not move it and sleeping does not skip the night
 * (like vanilla with {@code advance_time = false}); in a world OpenSeasons does not drive it is vanilla
 * {@code advance_time = false}. A stopped world stays stopped after being released (seasons turned off,
 * plugin disabled). The list is saved with the rest of the world state.
 *
 * <p>Main thread only.
 */
final class CalendarClock {

    /** world gamerules; tests without a server replace them ({@link GameRules} needs the server registries) */
    interface Rules {
        boolean advanceTime(World world);

        void setAdvanceTime(World world, boolean value);

        int sleepingPercentage(World world);

        Rules BUKKIT = new Rules() {
            @Override
            public boolean advanceTime(World world) {
                return Boolean.TRUE.equals(world.getGameRuleValue(GameRules.ADVANCE_TIME));
            }

            @Override
            public void setAdvanceTime(World world, boolean value) {
                world.setGameRule(GameRules.ADVANCE_TIME, value);
            }

            @Override
            public int sleepingPercentage(World world) {
                Integer percentage = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
                return percentage == null ? 100 : percentage;
            }
        };
    }

    private final Server server;
    private final Logger logger;
    private final Function<String, Optional<MonthDefinition>> monthOf;
    private final Rules rules;
    /** driven worlds and their accumulated tick fractions */
    private final Map<String, DayCycle.Accumulator> driven = new HashMap<>();
    /** worlds without a clock (setFullTime failed) */
    private final Set<String> withoutClock = new HashSet<>();
    private final Set<String> reenableReported = new HashSet<>();
    /** worlds with stopped time ({@code /openseasons pausetime}), also not driven and not loaded ones */
    private final Set<String> paused = new TreeSet<>();
    /** the list of stopped worlds changed, to be saved */
    private boolean pausesChanged;

    CalendarClock(Server server, Logger logger, Function<String, Optional<MonthDefinition>> monthOf, Rules rules) {
        this.server = server;
        this.logger = logger;
        this.monthOf = monthOf;
        this.rules = rules;
    }

    boolean drives(String world) {
        return driven.containsKey(world);
    }

    /** the world has stopped time ({@code /openseasons pausetime}) */
    boolean paused(String world) {
        return paused.contains(world);
    }

    /** stopped worlds to be saved */
    Set<String> pausedWorlds() {
        return Set.copyOf(paused);
    }

    /** stopped worlds from the saved state, before the first {@link #update} */
    void restorePaused(Collection<String> worlds) {
        paused.clear();
        paused.addAll(worlds);
    }

    /** the list of stopped worlds changed since the last query */
    boolean consumePauseChanges() {
        boolean changed = pausesChanged;
        pausesChanged = false;
        return changed;
    }

    /**
     * Stops the time in a world, or lets it run again ({@code /openseasons pausetime}). A driven world only
     * stops or starts moving ({@code advance_time} stays off); in a world that is not driven the vanilla
     * {@code advance_time} is toggled.
     *
     * @return {@code true} when the time is stopped now
     */
    boolean togglePause(World world) {
        String name = world.getName();
        boolean pause = drives(name) ? !paused.contains(name) : rules.advanceTime(world);
        if (pause ? paused.add(name) : paused.remove(name)) pausesChanged = true;
        if (!drives(name)) rules.setAdvanceTime(world, !pause);
        logger.info("World " + name + ": time " + (pause ? "stopped" : "runs again") + " (pausetime"
                + (drives(name) ? ", time driven by OpenSeasons)." : ", advance_time " + !pause + ")."));
        return pause;
    }

    /**
     * Takes over or releases the world clock. Called after a change of the state or configuration and
     * every second: a driven world where someone turned {@code advance_time} on gets it turned off again
     * (otherwise the time would run twice).
     */
    void update(World world, boolean drive) {
        String name = world.getName();
        if (!drive || withoutClock.contains(name)) {
            release(world);
            forgetResumedPause(world);
            return;
        }
        boolean fresh = driven.putIfAbsent(name, new DayCycle.Accumulator()) == null;
        boolean advancing = rules.advanceTime(world);
        if (advancing) rules.setAdvanceTime(world, false);
        if (fresh) {
            logger.info("World " + name + ": time driven by OpenSeasons by the calendar" + lengths(name)
                    + (advancing ? ", advance_time turned off" : "")
                    + (paused.contains(name) ? ", time stopped (pausetime)." : "."));
        } else if (advancing && reenableReported.add(name)) {
            logger.warning("World " + name + ": someone turned advance_time on, but OpenSeasons drives the time"
                    + " (modify-daynight-length); turned off again"
                    + (paused.contains(name) ? ". /openseasons pausetime " + name + " lets the stopped time run." : "."));
        }
    }

    /** OpenSeasons no longer drives the world: the time runs by vanilla again, a stopped world stays stopped */
    void release(World world) {
        String name = world.getName();
        if (driven.remove(name) == null) return;
        boolean stopped = paused.contains(name);
        rules.setAdvanceTime(world, !stopped);
        logger.info("World " + name + (stopped
                ? ": time driven by vanilla and stays stopped (pausetime, advance_time off)."
                : ": time runs by vanilla again (advance_time on)."));
    }

    /** someone turned {@code advance_time} on in a world that is not driven: the stop no longer applies */
    private void forgetResumedPause(World world) {
        String name = world.getName();
        if (!paused.contains(name) || !rules.advanceTime(world)) return;
        paused.remove(name);
        pausesChanged = true;
        logger.info("World " + name + ": advance_time is on, the time stop (pausetime) is cancelled.");
    }

    /** on plugin disable, so the time runs without it too */
    void releaseAll() {
        for (String name : List.copyOf(driven.keySet())) {
            World world = server.getWorld(name);
            if (world != null) release(world);
        }
        driven.clear();
    }

    /** every server tick */
    void tick() {
        if (driven.isEmpty() || server.isPaused() || !server.getServerTickManager().isRunningNormally()) return;
        for (Iterator<Map.Entry<String, DayCycle.Accumulator>> it = driven.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, DayCycle.Accumulator> e = it.next();
            if (paused.contains(e.getKey())) continue;
            World world = server.getWorld(e.getKey());
            Optional<MonthDefinition> month = monthOf.apply(e.getKey());
            if (world == null || month.isEmpty()) continue;
            try {
                advance(world, e.getValue(), month.get());
            } catch (IllegalArgumentException ex) {
                // a world without a world clock (custom dimension type): leave it alone
                it.remove();
                withoutClock.add(e.getKey());
                rules.setAdvanceTime(world, true);
                logger.warning("World " + e.getKey() + " has no clock, OpenSeasons does not drive its time: " + ex.getMessage());
            }
        }
    }

    private void advance(World world, DayCycle.Accumulator accumulator, MonthDefinition month) {
        long now = world.getFullTime();
        if (nightPasses(world)) {
            accumulator.reset();
            world.setFullTime(DayCycle.nextMorning(now));
            return;
        }
        long ticks = accumulator.step(now, month);
        if (ticks > 0) world.setFullTime(now + ticks);
    }

    private boolean nightPasses(World world) {
        List<Player> players = world.getPlayers();
        if (players.stream().noneMatch(Player::isDeeplySleeping)) return false;
        List<NightSkip.Sleeper> sleepers = new ArrayList<>(players.size());
        for (Player p : players) {
            sleepers.add(new NightSkip.Sleeper(p.getGameMode() == GameMode.SPECTATOR, p.isSleeping(),
                    p.isDeeplySleeping(), p.isSleepingIgnored()));
        }
        return NightSkip.nightPasses(sleepers, rules.sleepingPercentage(world));
    }

    private String lengths(String world) {
        return monthOf.apply(world)
                .map(m -> " (" + m.name() + ": day " + m.dayLengthMinutes() + " min, night " + m.nightLengthMinutes() + " min)")
                .orElse("");
    }
}
