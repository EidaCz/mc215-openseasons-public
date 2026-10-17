/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.events;

import cz.eida.minecraft.openseasons.effects.EffectRules;
import cz.eida.minecraft.openseasons.effects.ParticleViewers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Easter: eggs that can be clicked appear across the landscape around players
 * (spacing {@code chunk-distance-between-spawns}), and at night instead of a zombie, skeleton, creeper
 * or spider a killer bunny sometimes appears ({@code killer-bunny-spawn-rate}).
 */
final class EasterEvent extends BuiltinEvent {

    /** monsters a killer bunny may come instead of at night */
    static final Set<EntityType> REPLACED = Set.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER);
    static final int EGG_PERIOD = 200;
    static final int MAX_EGGS_PER_WORLD = 80;

    private final Pickups pickups;
    /** places (chunk x, z) where an egg appeared in this run of the event */
    private final Map<String, List<int[]>> eggSpots = new HashMap<>();

    EasterEvent(Plugin plugin, Supplier<EventSettings> settings, ParticleViewers viewers, EventPlaces places,
                Pickups pickups) {
        super(plugin, settings, viewers, places);
        this.pickups = pickups;
    }

    @Override
    SeasonEvent.Type type() {
        return SeasonEvent.Type.EASTER;
    }

    @Override
    protected void stopped(World world) {
        pickups.removeAll(world, Pickups.Kind.EGG);
        eggSpots.remove(world.getName());
    }

    /** at {@code NORMAL}, before seasonal monsters ({@code HIGH}), which skip a cancelled spawn */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!REPLACED.contains(event.getEntityType()) || !runningAt(event.getLocation())) return;
        EventSettings.Easter e = settings.get().easter();
        World world = event.getEntity().getWorld();
        if (!e.killerBunnies() || !EffectRules.night(world.getTime()) || random.nextDouble() >= e.killerBunnyRate()) return;
        if (!mobChangesAt(event.getLocation())) return;
        event.setCancelled(true);
        killerBunny(event.getLocation());
    }

    Rabbit killerBunny(Location at) {
        return at.getWorld().spawn(at, Rabbit.class, CreatureSpawnEvent.SpawnReason.CUSTOM, false, r -> {
            EventEntities.mark(r, type());
            r.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
            r.setRemoveWhenFarAway(true);
        });
    }

    @Override
    void tick(long tick) {
        if (tick % EGG_PERIOD != 0) return;
        EventSettings.Easter e = settings.get().easter();
        if (!e.eggs()) return;
        for (String name : worlds()) {
            World world = plugin.getServer().getWorld(name);
            if (world == null || pickups.live(world, Pickups.Kind.EGG).size() >= MAX_EGGS_PER_WORLD) continue;
            for (Player p : world.getPlayers()) {
                double[] xz = EventEntities.around(random, p.getLocation().getX(), p.getLocation().getZ(), 8, 40);
                Optional<Location> spot = EventEntities.ground(world, xz[0], p.getLocation().getY(), xz[1]);
                if (spot.isEmpty() || !effectsAt(spot.get())
                        || !farFromEarlier(world, spot.get(), e.eggSpacing())) continue;
                eggSpots.computeIfAbsent(name, w -> new ArrayList<>())
                        .add(new int[]{spot.get().getBlockX() >> 4, spot.get().getBlockZ() >> 4});
                pickups.spawn(Pickups.Kind.EGG, spot.get());
            }
        }
    }

    private boolean farFromEarlier(World world, Location at, int spacing) {
        if (spacing <= 0) return true;
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        for (int[] s : eggSpots.getOrDefault(world.getName(), List.of())) {
            if (EventEntities.chunkDistance(cx, cz, s[0], s[1]) < spacing) return false;
        }
        return true;
    }

    /** manual triggering: an egg a short way ahead of the place, regardless of spacing */
    Optional<Location> debugEgg(Location near) {
        Optional<Location> g = EventEntities.groundAhead(near, 2.5);
        g.ifPresent(at -> pickups.spawn(Pickups.Kind.EGG, at));
        return g;
    }

    /** manual triggering: a killer bunny a short way ahead of the place */
    Optional<Location> debugBunny(Location near) {
        Optional<Location> g = EventEntities.groundAhead(near, 5);
        g.ifPresent(this::killerBunny);
        return g;
    }
}
