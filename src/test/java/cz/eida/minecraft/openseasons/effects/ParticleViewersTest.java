/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleViewersTest {

    /** player: how many particles they got and which displays they see (shown by the plugin) */
    private static final class FakePlayer {
        final UUID id = UUID.randomUUID();
        final double x;
        int particles;
        final Set<Entity> shown = new HashSet<>();
        Player proxy;

        FakePlayer(double x) {
            this.x = x;
        }
    }

    private static final class FakeDisplay {
        boolean valid = true;
        Boolean visibleByDefault;
        Entity proxy;
    }

    private final List<FakePlayer> online = new ArrayList<>();

    @Test
    void particlesOnlyToEnabledPlayersInRange() {
        FakePlayer near = player(0);
        FakePlayer off = player(5);
        FakePlayer far = player(100);
        World world = world();
        ParticleViewers viewers = new ParticleViewers(plugin());
        viewers.setDisabled(Set.of(off.id));

        viewers.spawn(world, Particle.END_ROD, 1, 64, 1, 1, 0, 0, 0, 0, null, false);
        assertEquals(1, near.particles);
        assertEquals(0, off.particles, "particles off");
        assertEquals(0, far.particles, "beyond 32 blocks");
        // with force the range is 512
        viewers.spawn(world, Particle.END_ROD, 1, 64, 1, 1, 0, 0, 0, 0, null, true);
        assertEquals(2, near.particles);
        assertEquals(1, far.particles);
        assertEquals(0, off.particles);

        assertEquals(List.of(near.proxy, far.proxy), viewers.players(world));
        assertTrue(ParticleViewers.inRange(31, 0, 0, ParticleViewers.RANGE));
        assertFalse(ParticleViewers.inRange(32, 0, 0, ParticleViewers.RANGE));
    }

    @Test
    void displaysFollowToggleAndJoin() {
        FakePlayer on = player(0);
        FakePlayer off = player(0);
        ParticleViewers viewers = new ParticleViewers(plugin());
        viewers.setDisabled(Set.of(off.id));

        FakeDisplay leaf = display();
        ParticleViewers.hide(leaf.proxy);
        assertEquals(Boolean.FALSE, leaf.visibleByDefault);
        viewers.reveal(leaf.proxy);
        assertEquals(Set.of(leaf.proxy), on.shown);
        assertTrue(off.shown.isEmpty());

        // turning off hides at once, turning on shows what is flying right now
        assertFalse(viewers.toggle(on.proxy));
        assertTrue(on.shown.isEmpty());
        assertTrue(viewers.toggle(off.proxy));
        assertEquals(Set.of(leaf.proxy), off.shown);
        assertEquals(Set.of(on.id), viewers.disabled());

        // after joining (a new player instance) show only with particles on
        off.shown.clear();
        viewers.join(off.proxy);
        viewers.join(on.proxy);
        assertEquals(Set.of(leaf.proxy), off.shown);
        assertTrue(on.shown.isEmpty());

        // a vanished display is forgotten
        leaf.valid = false;
        assertEquals(0, viewers.displayCount());
    }

    private FakePlayer player(double x) {
        FakePlayer p = new FakePlayer(x);
        p.proxy = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> p.id;
                    case "getLocation" -> new Location(null, p.x, 64, 0);
                    case "spawnParticle" -> {
                        p.particles++;
                        yield null;
                    }
                    case "showEntity" -> {
                        p.shown.add((Entity) args[1]);
                        yield null;
                    }
                    case "hideEntity" -> {
                        p.shown.remove((Entity) args[1]);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        online.add(p);
        return p;
    }

    private World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayers" -> online.stream().map(p -> p.proxy).toList();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static FakeDisplay display() {
        FakeDisplay d = new FakeDisplay();
        d.proxy = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> d.valid;
                    case "setVisibleByDefault" -> {
                        d.visibleByDefault = (Boolean) args[0];
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return d;
    }

    private Plugin plugin() {
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOnlinePlayers" -> online.stream().map(p -> p.proxy).toList();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServer" -> server;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
