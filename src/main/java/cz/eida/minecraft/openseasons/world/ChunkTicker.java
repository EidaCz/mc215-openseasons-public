/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Slow walk through loaded chunks around players for world changes (seasonal plants, later snow and ice).
 * Every {@code period} ticks (2 × {@code delay-per-operation}) it visits one
 * random chunk in the radius of the next player in turn and cleans up a few chunks from the queue. It never loads chunks:
 * it only visits a chunk that is loaded together with all eight neighbours. It stops at low TPS.
 */
public final class ChunkTicker {

    /** what is done with chunks; called only from the main thread */
    public interface Task {

        /** world in which the task has something to do (seasons enabled, {@code modify-blocks}) */
        boolean active(World world);

        /** visit of a chunk near a player: cleanup and placing; the chunk and its neighbours are loaded */
        void visit(World world, int chunkX, int chunkZ);

        /** cleanup of a loaded chunk from the queue (nothing is placed away from players) */
        void cleanup(World world, int chunkX, int chunkZ);
    }

    /**
     * @param period         ticks between visits
     * @param radius         chunk radius around a player
     * @param cleanupPerRun  how many chunks from the queue are cleaned up per visit
     */
    public record Settings(int period, int radius, boolean prioritiseTps, double minTps, int cleanupPerRun) {
    }

    private final Plugin plugin;
    private final Supplier<Settings> settings;
    private final List<Task> tasks;
    private final ChunkQueue cleanup = new ChunkQueue(4096);
    private final RandomGenerator random = RandomGenerator.getDefault();
    private BukkitTask task;
    private int ticks;
    private int nextPlayer;
    private long visits;
    private long skippedTps;

    public ChunkTicker(Plugin plugin, Supplier<Settings> settings, List<Task> tasks) {
        this.plugin = plugin;
        this.settings = settings;
        this.tasks = List.copyOf(tasks);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        cleanup.clear();
    }

    /** chunk to clean up (a loaded chunk with journal entries); takes precedence over random visits */
    public void enqueueCleanup(World world, int chunkX, int chunkZ) {
        cleanup.add(world.getName(), chunkX, chunkZ);
    }

    public int pendingCleanup() {
        return cleanup.size();
    }

    public long visits() {
        return visits;
    }

    public long skippedForTps() {
        return skippedTps;
    }

    /** the chunk and its eight neighbours are loaded (blocks at the edges can be read without loading) */
    public static boolean loadedWithNeighbours(World world, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(chunkX + dx, chunkZ + dz)) return false;
            }
        }
        return true;
    }

    private void tick() {
        Settings s = settings.get();
        if (++ticks < Math.max(1, s.period())) return;
        ticks = 0;
        if (!ChunkQueue.tpsAllows(s.prioritiseTps(), s.minTps(), plugin.getServer().getTPS()[0])) {
            skippedTps++;
            return;
        }
        for (ChunkQueue.Ref ref : cleanup.poll(s.cleanupPerRun(), this::loaded)) {
            World world = plugin.getServer().getWorld(ref.world());
            if (world == null) continue;
            for (Task t : tasks) {
                if (t.active(world)) t.cleanup(world, ref.chunkX(), ref.chunkZ());
            }
        }
        visitNearPlayer(s);
    }

    private boolean loaded(ChunkQueue.Ref ref) {
        World world = plugin.getServer().getWorld(ref.world());
        return world != null && world.isChunkLoaded(ref.chunkX(), ref.chunkZ());
    }

    /** one random chunk in the radius of the next player (players take turns) */
    private void visitNearPlayer(Settings s) {
        List<Player> players = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            World world = p.getWorld();
            if (tasks.stream().anyMatch(t -> t.active(world))) players.add(p);
        }
        if (players.isEmpty()) return;
        Player player = players.get(Math.floorMod(nextPlayer++, players.size()));
        World world = player.getWorld();
        int r = Math.max(0, s.radius());
        int dx;
        int dz;
        do {
            dx = random.nextInt(-r, r + 1);
            dz = random.nextInt(-r, r + 1);
        } while (dx * dx + dz * dz > r * r);
        int cx = (player.getLocation().getBlockX() >> 4) + dx;
        int cz = (player.getLocation().getBlockZ() >> 4) + dz;
        if (!loadedWithNeighbours(world, cx, cz)) return;
        visits++;
        for (Task t : tasks) {
            if (t.active(world)) t.visit(world, cx, cz);
        }
    }
}
