/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Mechanics of a built-in event (Christmas, New Year, Halloween, Easter) in the worlds where the event
 * runs. The scheduler turns it on and off ({@link #begin}, {@link #end}); the entities it creates are cleaned up when
 * the event ends. Runs only on the main thread.
 *
 * <p>In a world it runs either entirely ({@link Scope#WORLD}: game date or a real-date period of the whole world),
 * or only in regions with a period whose events contain it ({@link Scope#REGIONS}, flag
 * {@code real-periods}). Phenomena and monster changes therefore ask about the place ({@link #effectsAt}, {@link #mobChangesAt}).
 */
abstract class BuiltinEvent implements Listener {

    /** where in the world the event runs */
    enum Scope {
        /** the whole world */
        WORLD,
        /** only regions with a real-date period that contains the event */
        REGIONS
    }

    protected final Plugin plugin;
    protected final Supplier<EventSettings> settings;
    protected final ParticleViewers viewers;
    /** where phenomena and monster changes may act (regions) */
    protected final EventPlaces places;
    protected final RandomGenerator random = RandomGenerator.getDefault();
    private final Map<String, Scope> worlds = new HashMap<>();

    BuiltinEvent(Plugin plugin, Supplier<EventSettings> settings, ParticleViewers viewers, EventPlaces places) {
        this.plugin = plugin;
        this.settings = settings;
        this.viewers = viewers;
        this.places = places;
    }

    abstract SeasonEvent.Type type();

    /** the event began in the world or was resumed after a restart (the whole world) */
    final void begin(World world) {
        begin(world, Scope.WORLD);
    }

    /** the event runs in the world in the given scope; changing the scope does not restart the mechanics */
    final void begin(World world, Scope scope) {
        if (worlds.put(world.getName(), scope) == null) started(world);
    }

    /** the event ended in the world: the mechanics stop and the event entities vanish */
    final void end(World world) {
        boolean was = worlds.remove(world.getName()) != null;
        stopped(world);
        if (was) EventEntities.removeTagged(world, type());
    }

    /** plugin shutdown: entities vanish, but the event keeps "running" (it is resumed after start) */
    final void shutdown() {
        for (String name : Set.copyOf(worlds.keySet())) {
            World w = plugin.getServer().getWorld(name);
            if (w != null) {
                stopped(w);
                EventEntities.removeTagged(w, type());
            }
        }
        worlds.clear();
    }

    final boolean running(World world) {
        return worlds.containsKey(world.getName());
    }

    final Optional<Scope> scope(World world) {
        return Optional.ofNullable(worlds.get(world.getName()));
    }

    final Set<String> worlds() {
        return Set.copyOf(worlds.keySet());
    }

    /** whether the event runs at the place: in the whole world, or in a region with a period whose events contain it */
    final boolean runningAt(Location at) {
        Scope s = worlds.get(at.getWorld().getName());
        return s == Scope.WORLD || s == Scope.REGIONS && places.regionEvent(at, type().id());
    }

    /** event phenomena and decorations may appear at the place (it runs there and the region does not forbid {@code season-effects}) */
    protected final boolean effectsAt(Location at) {
        return runningAt(at) && places.effectsAllowed(at);
    }

    /** monster changes and added creatures at the place (it runs there and the region does not forbid {@code season-mob-spawning}) */
    protected final boolean mobChangesAt(Location at) {
        return runningAt(at) && places.mobChangesAllowed(at);
    }

    protected void started(World world) {
    }

    protected void stopped(World world) {
    }

    /** every server tick */
    void tick(long tick) {
    }
}
