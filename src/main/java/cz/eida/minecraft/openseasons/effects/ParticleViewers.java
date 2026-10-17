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
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who sees season particles ({@code /toggleseasonparticles}): players
 * with particles turned off do not get them, the others keep them. Particles are sent to individual players
 * in range as if from the world (32 blocks, 512 with {@code force}), never to the whole world. Temporary displays of
 * phenomena (big leaves, UFO) are hidden and shown only to players with particles turned on; toggling hides
 * or shows them immediately. No phenomena are created around a player with particles turned off (including
 * fireflies and the aurora).
 */
public final class ParticleViewers {

    /** range of a particle from the world (vanilla), with {@code force} {@link #FORCED_RANGE} */
    static final double RANGE = 32;
    static final double FORCED_RANGE = 512;

    private final Plugin plugin;
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();
    /** live phenomenon displays (main thread only); invalid ones are dropped along the way */
    private final Set<Entity> displays = new HashSet<>();

    public ParticleViewers(Plugin plugin) {
        this.plugin = plugin;
    }

    /** players with particles turned off (loaded at start) */
    public void setDisabled(Set<UUID> players) {
        disabled.clear();
        disabled.addAll(players);
    }

    public Set<UUID> disabled() {
        return Set.copyOf(disabled);
    }

    public boolean enabled(Player player) {
        return !disabled.contains(player.getUniqueId());
    }

    /**
     * Personal turning particles off and on; displays currently flying are hidden or shown to the player immediately.
     *
     * @return particles are newly turned on
     */
    public boolean toggle(Player player) {
        boolean enabled = disabled.remove(player.getUniqueId());
        if (!enabled) disabled.add(player.getUniqueId());
        prune();
        for (Entity e : displays) {
            if (enabled) player.showEntity(plugin, e);
            else player.hideEntity(plugin, e);
        }
        return enabled;
    }

    /** players of the world with particles turned on: phenomena are created around them */
    public List<Player> players(World world) {
        List<Player> out = new ArrayList<>();
        for (Player p : world.getPlayers()) if (enabled(p)) out.add(p);
        return out;
    }

    /** particles to the players in range with particles turned on, like {@code World.spawnParticle} */
    public <T> void spawn(World world, Particle particle, double x, double y, double z, int count,
                          double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        double range = force ? FORCED_RANGE : RANGE;
        for (Player p : world.getPlayers()) {
            if (!enabled(p)) continue;
            Location at = p.getLocation();
            if (inRange(at.getX() - x, at.getY() - y, at.getZ() - z, range)) {
                p.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, force);
            }
        }
    }

    static boolean inRange(double dx, double dy, double dz, double range) {
        return dx * dx + dy * dy + dz * dz < range * range;
    }

    /** show the phenomenon display to nobody; call when creating it, before it gets into the world */
    public static void hide(Entity display) {
        display.setVisibleByDefault(false);
    }

    /** shows the phenomenon display to players with particles turned on (also in another world, should they come) */
    public void reveal(Entity display) {
        prune();
        displays.add(display);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (enabled(p)) p.showEntity(plugin, display);
        }
    }

    /** after a player joins: displays currently flying (visibility is lost on login) */
    public void join(Player player) {
        if (!enabled(player)) return;
        prune();
        for (Entity e : displays) player.showEntity(plugin, e);
    }

    int displayCount() {
        prune();
        return displays.size();
    }

    private void prune() {
        displays.removeIf(e -> !e.isValid());
    }
}
