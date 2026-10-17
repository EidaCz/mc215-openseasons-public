/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Journal of the blocks OpenSeasons changed in the world (seasonal plants, later snow and ice), and of plants
 * planted by players ({@link #PLAYER}). For each block it remembers what it placed and what was there
 * before, so it can revert the change, and only while its block is still there.
 *
 * <p>Saved per world and per area of 32 × 32 chunks in {@code data/journal/<world>/r.<x>.<z>.txt}
 * (one line per block, tab-separated). The memory holds the whole journal of loaded worlds, maps by chunk
 * and position. Main thread only; writing the files ({@link #takeDirty()} and {@link #write(Map)}) may
 * run outside of it.
 */
public final class PlacementJournal {

    /** marker of a plant planted by a player ({@code keep-player-placed-plants}); OpenSeasons does not change it */
    public static final String PLAYER = "player";
    /** the block waits for restoring ({@code /openseasons restore} in an unloaded chunk) */
    public static final String RESTORE = "restore";

    static final String HEADER = "# OpenSeasons: journal of block changes v1 (x y z kind placed original)";
    private static final String NONE = "-";
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.txt");

    /**
     * One block.
     *
     * @param kind     kind of change ({@code flowers}, {@code leaf-litter} …), {@link #PLAYER} or {@link #RESTORE}
     * @param placed   data of the block OpenSeasons placed ({@code minecraft:poppy}); for a player their plant
     * @param original data of the block before the first change by OpenSeasons; empty for a player
     */
    public record Entry(int x, int y, int z, String kind, String placed, String original) {

        public boolean byPlayer() {
            return PLAYER.equals(kind);
        }

        /** material of the placed block: data without the states in square brackets */
        public String placedMaterial() {
            return material(placed);
        }

        public long key() {
            return BlockKeys.block(x, y, z);
        }

        public int chunkX() {
            return x >> 4;
        }

        public int chunkZ() {
            return z >> 4;
        }

        Entry withKind(String k) {
            return new Entry(x, y, z, k, placed, original);
        }
    }

    private static final class WorldEntries {
        final Map<Long, Map<Long, Entry>> chunks = new HashMap<>();
        final Set<Long> dirtyRegions = new HashSet<>();
        int size;
    }

    private final Path folder;
    private final Map<String, WorldEntries> worlds = new HashMap<>();

    /** @param folder root of the journal, typically {@code plugins/OpenSeasons/data/journal} */
    public PlacementJournal(Path folder) {
        this.folder = folder;
    }

    public Path folder() {
        return folder;
    }

    /** material from block data: {@code minecraft:sweet_berry_bush[age=1]} → {@code minecraft:sweet_berry_bush} */
    public static String material(String blockData) {
        int i = blockData.indexOf('[');
        return i < 0 ? blockData : blockData.substring(0, i);
    }

    // --- loading and saving ---

    public boolean loaded(String world) {
        return worlds.containsKey(world);
    }

    /** loads the journal of a world from files (once); skips damaged lines with a warning */
    public void load(String world, List<String> warnings) {
        if (worlds.containsKey(world)) return;
        WorldEntries w = new WorldEntries();
        worlds.put(world, w);
        Path dir = worldFolder(world);
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*.txt")) {
            for (Path file : files) {
                if (!REGION_FILE.matcher(file.getFileName().toString()).matches()) continue;
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (Entry e : parse(text, file.toString(), warnings)) put(w, e);
            }
        } catch (IOException e) {
            warnings.add("Journal of block changes of world " + world + " cannot be loaded (" + dir + "): " + e.getMessage());
        }
    }

    /** forgets a world (after saving, when the world is unloaded) */
    public void unload(String world) {
        worlds.remove(world);
    }

    /**
     * Content of the changed areas to write (file → text, empty text = delete the file). The changes are
     * considered saved by this; when writing fails, the area has to be marked again ({@link #markDirty}).
     */
    public Map<Path, String> takeDirty() {
        Map<Path, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, WorldEntries> we : worlds.entrySet()) {
            WorldEntries w = we.getValue();
            if (w.dirtyRegions.isEmpty()) continue;
            Map<Long, List<Entry>> byRegion = new HashMap<>();
            for (long region : w.dirtyRegions) byRegion.put(region, new ArrayList<>());
            for (Map.Entry<Long, Map<Long, Entry>> c : w.chunks.entrySet()) {
                List<Entry> list = byRegion.get(regionOfChunk(c.getKey()));
                if (list != null) list.addAll(c.getValue().values());
            }
            for (Map.Entry<Long, List<Entry>> r : byRegion.entrySet()) {
                out.put(regionFile(we.getKey(), r.getKey()), r.getValue().isEmpty() ? "" : serialize(r.getValue()));
            }
            w.dirtyRegions.clear();
        }
        return out;
    }

    /** marks the area of a file for writing again (after a write error) */
    public void markDirty(Path file) {
        Matcher m = REGION_FILE.matcher(file.getFileName().toString());
        if (!m.matches()) return;
        for (Map.Entry<String, WorldEntries> we : worlds.entrySet()) {
            if (worldFolder(we.getKey()).equals(file.getParent())) {
                we.getValue().dirtyRegions.add(BlockKeys.chunk(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
            }
        }
    }

    public boolean dirty() {
        return worlds.values().stream().anyMatch(w -> !w.dirtyRegions.isEmpty());
    }

    /**
     * Writes the files atomically (temporary file and move); an empty text deletes the file.
     *
     * @return the files that could not be written, with the error
     */
    public static Map<Path, String> write(Map<Path, String> files) {
        Map<Path, String> failed = new LinkedHashMap<>();
        for (Map.Entry<Path, String> f : files.entrySet()) {
            try {
                if (f.getValue().isEmpty()) {
                    Files.deleteIfExists(f.getKey());
                } else {
                    writeAtomically(f.getKey(), f.getValue());
                }
            } catch (IOException e) {
                failed.put(f.getKey(), e.getMessage());
            }
        }
        return failed;
    }

    // --- entries ---

    /**
     * Records a block OpenSeasons placed. When another of its blocks is already at the position (litter over grass),
     * the original state of the first change stays, so restoring returns the world to the state before OpenSeasons.
     */
    public Entry record(String world, int x, int y, int z, String kind, String placed, String original) {
        if (PLAYER.equals(kind)) throw new IllegalArgumentException("player plants via recordPlayer");
        WorldEntries w = world(world);
        Entry previous = chunkMap(w, x, z).get(BlockKeys.block(x, y, z));
        String first = previous != null && !previous.byPlayer() ? previous.original() : original;
        Entry e = new Entry(x, y, z, kind, placed, first);
        put(w, e);
        dirty(w, x, z);
        return e;
    }

    /** a plant planted by a player: OpenSeasons does not overwrite it; replaces also its earlier entry */
    public Entry recordPlayer(String world, int x, int y, int z, String placed) {
        WorldEntries w = world(world);
        Entry e = new Entry(x, y, z, PLAYER, placed, "");
        put(w, e);
        dirty(w, x, z);
        return e;
    }

    public Optional<Entry> get(String world, int x, int y, int z) {
        WorldEntries w = worlds.get(world);
        if (w == null) return Optional.empty();
        Map<Long, Entry> chunk = w.chunks.get(BlockKeys.chunkOfBlock(x, z));
        return chunk == null ? Optional.empty() : Optional.ofNullable(chunk.get(BlockKeys.block(x, y, z)));
    }

    public Optional<Entry> remove(String world, int x, int y, int z) {
        WorldEntries w = worlds.get(world);
        if (w == null) return Optional.empty();
        long chunkKey = BlockKeys.chunkOfBlock(x, z);
        Map<Long, Entry> chunk = w.chunks.get(chunkKey);
        if (chunk == null) return Optional.empty();
        Entry e = chunk.remove(BlockKeys.block(x, y, z));
        if (e == null) return Optional.empty();
        w.size--;
        if (chunk.isEmpty()) w.chunks.remove(chunkKey);
        dirty(w, x, z);
        return Optional.of(e);
    }

    /** changes the kind of an entry (restoring postponed until the chunk is loaded) */
    public void rekind(String world, Entry entry, String kind) {
        WorldEntries w = worlds.get(world);
        if (w == null) return;
        Map<Long, Entry> chunk = w.chunks.get(BlockKeys.chunkOfBlock(entry.x(), entry.z()));
        if (chunk == null || !chunk.containsKey(entry.key())) return;
        chunk.put(entry.key(), entry.withKind(kind));
        dirty(w, entry.x(), entry.z());
    }

    /** entries of a chunk (a copy, the journal may change during the walk) */
    public List<Entry> chunk(String world, int chunkX, int chunkZ) {
        WorldEntries w = worlds.get(world);
        if (w == null) return List.of();
        Map<Long, Entry> chunk = w.chunks.get(BlockKeys.chunk(chunkX, chunkZ));
        return chunk == null ? List.of() : List.copyOf(chunk.values());
    }

    public boolean hasChunk(String world, int chunkX, int chunkZ) {
        WorldEntries w = worlds.get(world);
        return w != null && w.chunks.containsKey(BlockKeys.chunk(chunkX, chunkZ));
    }

    /** keys of chunks with entries ({@link BlockKeys#chunk}) */
    public Set<Long> chunkKeys(String world) {
        WorldEntries w = worlds.get(world);
        return w == null ? Set.of() : Set.copyOf(w.chunks.keySet());
    }

    /** how many blocks of the given kind are in the chunk */
    public int count(String world, int chunkX, int chunkZ, String kind) {
        WorldEntries w = worlds.get(world);
        if (w == null) return 0;
        Map<Long, Entry> chunk = w.chunks.get(BlockKeys.chunk(chunkX, chunkZ));
        if (chunk == null) return 0;
        int n = 0;
        for (Entry e : chunk.values()) if (e.kind().equals(kind)) n++;
        return n;
    }

    public int size(String world) {
        WorldEntries w = worlds.get(world);
        return w == null ? 0 : w.size;
    }

    /** entries of the world (without player ones) within a block radius around the centre; radius < 0 = the whole world */
    public List<Entry> placedWithin(String world, int centerX, int centerZ, int radius) {
        WorldEntries w = worlds.get(world);
        if (w == null) return List.of();
        long r2 = (long) radius * radius;
        List<Entry> out = new ArrayList<>();
        for (Map<Long, Entry> chunk : w.chunks.values()) {
            for (Entry e : chunk.values()) {
                if (e.byPlayer()) continue;
                long dx = e.x() - centerX;
                long dz = e.z() - centerZ;
                if (radius < 0 || dx * dx + dz * dz <= r2) out.add(e);
            }
        }
        return out;
    }

    /** block counts by kind (without player ones), sorted by name */
    public static Map<String, Integer> countByKind(Collection<Entry> entries) {
        Map<String, Integer> out = new TreeMap<>();
        for (Entry e : entries) if (!e.byPlayer()) out.merge(e.kind(), 1, Integer::sum);
        return out;
    }

    // --- format ---

    static String serialize(Collection<Entry> entries) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        entries.stream()
                .sorted(Comparator.comparingInt(Entry::x).thenComparingInt(Entry::z).thenComparingInt(Entry::y))
                .forEach(e -> sb.append(e.x()).append('\t').append(e.y()).append('\t').append(e.z()).append('\t')
                        .append(e.kind()).append('\t').append(e.placed()).append('\t')
                        .append(e.original().isEmpty() ? NONE : e.original()).append('\n'));
        return sb.toString();
    }

    static List<Entry> parse(String text, String source, List<String> warnings) {
        List<Entry> out = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            try {
                if (f.length != 6) throw new IllegalArgumentException("expected 6 fields, got " + f.length);
                String original = f[5].equals(NONE) ? "" : f[5];
                if (f[3].isBlank() || f[4].isBlank()) throw new IllegalArgumentException("empty kind or block");
                out.add(new Entry(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        f[3], f[4], original));
            } catch (IllegalArgumentException e) {
                warnings.add(source + ":" + (i + 1) + ": journal line skipped (" + e.getMessage() + ")");
            }
        }
        return out;
    }

    // --- internal ---

    private WorldEntries world(String world) {
        return worlds.computeIfAbsent(world, k -> new WorldEntries());
    }

    private static Map<Long, Entry> chunkMap(WorldEntries w, int x, int z) {
        return w.chunks.computeIfAbsent(BlockKeys.chunkOfBlock(x, z), k -> new HashMap<>());
    }

    private static void put(WorldEntries w, Entry e) {
        if (chunkMap(w, e.x(), e.z()).put(e.key(), e) == null) w.size++;
    }

    private static void dirty(WorldEntries w, int x, int z) {
        w.dirtyRegions.add(regionOfChunk(BlockKeys.chunkOfBlock(x, z)));
    }

    private static long regionOfChunk(long chunkKey) {
        return BlockKeys.chunk(BlockKeys.chunkX(chunkKey) >> 5, BlockKeys.chunkZ(chunkKey) >> 5);
    }

    private Path worldFolder(String world) {
        return folder.resolve(world.replaceAll("[^\\p{L}\\p{N}._-]", "_"));
    }

    private Path regionFile(String world, long region) {
        return worldFolder(world).resolve("r." + BlockKeys.chunkX(region) + "." + BlockKeys.chunkZ(region) + ".txt");
    }

    private static void writeAtomically(Path file, String text) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
