/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.effects;

import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.MainConfig;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Aurora ({@code particles.AURORA}) made of particles: on a clear night in the configured season the dice are rolled
 * once per night and world; on success a curtain of rays glows on the northern sky above every player
 * ({@link AuroraShape}). The rays are glowing {@code GLOW} particles (blue-green), with a purple fringe of
 * {@code REVERSE_PORTAL} at the top. Each player sees their own aurora (sent only to them, also at a distance); a player
 * with particles turned off ({@link ParticleViewers}) sees none. The season is taken at the player's location
 * ({@link RegionService#effectSeasonAt}); in a region with {@code season-effects deny} there is no aurora.
 */
public final class AuroraEffect {

    static final int EMIT_PERIOD = 3;
    /** at most this many particles per ray per emission */
    static final int RAY_PARTICLES = 5;
    /** how long it glows after {@code /openseasons debug aurora} */
    static final int DEBUG_TICKS = 1200;

    private final Plugin plugin;
    private final Supplier<LoadedConfig> config;
    private final SeasonService seasons;
    private final ParticleViewers viewers;
    private final RegionService regions;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final Map<String, Long> decidedNight = new HashMap<>();
    private final Map<String, Boolean> auroraNight = new HashMap<>();
    /** wave phase of the aurora in a world (new every night) */
    private final Map<String, Long> seeds = new HashMap<>();
    private final Map<UUID, Integer> debugUntil = new HashMap<>();
    private BukkitTask task;
    private int ticks;

    public AuroraEffect(Plugin plugin, Supplier<LoadedConfig> config, SeasonService seasons,
                        ParticleViewers viewers, RegionService regions) {
        this.plugin = plugin;
        this.config = config;
        this.seasons = seasons;
        this.viewers = viewers;
        this.regions = regions;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        debugUntil.clear();
    }

    /** aurora above the player for a minute, also during the day and outside the season */
    public void debug(Player player) {
        debugUntil.put(player.getUniqueId(), ticks + DEBUG_TICKS);
    }

    private void tick() {
        ticks++;
        if (ticks % EMIT_PERIOD != 0) return;
        MainConfig.Aurora cfg = config.get().main().particles().aurora();
        for (World world : plugin.getServer().getWorlds()) {
            Optional<WorldSeasonState> state = seasons.state(world.getName());
            boolean night = EffectRules.night(world.getTime());
            // dice per world and night; aurora only for players whose location has an aurora season (also from a region)
            boolean season = cfg.enabled() && state.isPresent() && state.get().enabled();
            if (night && season) {
                long nightId = EffectRules.nightIndex(world.getFullTime());
                if (!Long.valueOf(nightId).equals(decidedNight.get(world.getName()))) {
                    decidedNight.put(world.getName(), nightId);
                    auroraNight.put(world.getName(), EffectRules.roll(random, cfg.chance()));
                    seeds.put(world.getName(), random.nextLong());
                }
            }
            boolean on = night && season && !world.hasStorm() && auroraNight.getOrDefault(world.getName(), false);
            for (Player player : world.getPlayers()) {
                Integer until = debugUntil.get(player.getUniqueId());
                if (until != null && until < ticks) {
                    debugUntil.remove(player.getUniqueId());
                    until = null;
                }
                boolean here = on && EffectRules.activeAt(true, cfg.seasons(), regions.effectSeasonAt(player.getLocation()));
                if ((here || until != null) && viewers.enabled(player)) emit(player, seeds.computeIfAbsent(world.getName(), w -> random.nextLong()));
            }
        }
    }

    /** one emission of rays; each ray is one packet with particles spread along its length */
    private void emit(Player player, long seed) {
        Location at = player.getLocation();
        for (AuroraShape.Ray ray : AuroraShape.rays(ticks, seed)) {
            int count = (int) Math.round(ray.intensity() * RAY_PARTICLES);
            if (count <= 0) continue;
            double x = at.getX() + ray.x();
            double z = at.getZ() + ray.z();
            player.spawnParticle(Particle.GLOW, x, at.getY() + ray.baseY() + ray.height() / 2, z, count,
                    0.35, ray.height() / 3.5, 0.35, 0, null, true);
            if (ray.violetTop() && ray.intensity() > 0.4) {
                player.spawnParticle(Particle.REVERSE_PORTAL, x, at.getY() + ray.baseY() + ray.height() * 0.95, z, 2,
                        0.4, 1.5, 0.4, 0, null, true);
            }
        }
    }
}
