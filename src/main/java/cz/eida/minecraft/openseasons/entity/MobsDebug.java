/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.entity;

import cz.eida.minecraft.openseasons.season.Season;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /openseasons debug mobs …}: seasonal animals and monsters for testing (every phenomenon can be triggered manually).
 *
 * <ul>
 *   <li>{@code <season|kind> [player | world x z]}: a group at a player or place (from the console without a target at
 *       the spawn of the main world),</li>
 *   <li>{@code zombie|skeleton|pumpkin [player | world x z]}: a natural spawn through the replacement and pumpkin rules,</li>
 *   <li>{@code cycle [player | world x z]}: one pass through the chunks around a player with a 100 % chance,</li>
 *   <li>{@code counts [world]}: seasonal creatures in loaded chunks,</li>
 *   <li>{@code sweep [all] [world]}: the end-of-season check right now (with {@code all} every seasonal creature
 *       without protection).</li>
 * </ul>
 */
public final class MobsDebug {

    static final List<String> ACTIONS = List.of("counts", "sweep", "cycle", "zombie", "skeleton", "pumpkin");
    static final List<String> SEASONS = List.of("winter", "spring", "summer", "fall");

    private static final Map<SeasonalMob, String> NAMES = new EnumMap<>(SeasonalMob.class);

    static {
        NAMES.put(SeasonalMob.COW, "cow");
        NAMES.put(SeasonalMob.SHEEP, "sheep");
        NAMES.put(SeasonalMob.CHICKEN, "chicken");
        NAMES.put(SeasonalMob.PIG, "pig");
        NAMES.put(SeasonalMob.BEE, "bee");
        NAMES.put(SeasonalMob.RABBIT, "rabbit");
        NAMES.put(SeasonalMob.OCELOT, "ocelot");
        NAMES.put(SeasonalMob.PANDA, "panda");
        NAMES.put(SeasonalMob.PARROT, "parrot");
        NAMES.put(SeasonalMob.BAT, "bat");
        NAMES.put(SeasonalMob.SPIDER, "spider");
        NAMES.put(SeasonalMob.CAVE_SPIDER, "cave spider");
        NAMES.put(SeasonalMob.RED_FOX, "red fox");
        NAMES.put(SeasonalMob.FROG, "frog");
        NAMES.put(SeasonalMob.MOOSHROOM, "mooshroom");
        NAMES.put(SeasonalMob.WOLF, "wolf");
        NAMES.put(SeasonalMob.SNOW_FOX, "snow fox");
        NAMES.put(SeasonalMob.POLAR_BEAR, "polar bear");
        NAMES.put(SeasonalMob.SNOW_GOLEM, "snow golem");
    }

    private final Server server;
    private final SeasonalMobs mobs;

    public MobsDebug(Server server, SeasonalMobs mobs) {
        this.server = server;
        this.mobs = mobs;
    }

    /** @param args arguments after {@code mobs} */
    public void run(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return;
        }
        String what = plain(args[0]);
        String next = args.length > 1 ? args[1] : null;
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (what) {
            case "counts" -> counts(sender, next);
            case "sweep" -> {
                boolean all = next != null && plain(next).equals("all");
                sweep(sender, all ? (args.length > 2 ? args[2] : null) : next, all);
            }
            case "cycle" -> target(sender, rest).ifPresent(t -> cycle(sender, t));
            case "zombie", "skeleton", "pumpkin" -> target(sender, rest).ifPresent(t -> natural(sender, t, what));
            default -> {
                Optional<Season> season = Season.parse(what);
                Optional<SeasonalMob> mob = SeasonalMob.byId(what);
                if (season.isEmpty() && mob.isEmpty()) {
                    error(sender, "Unknown season or kind " + args[0] + ".");
                    usage(sender, label);
                    return;
                }
                target(sender, rest).ifPresent(t -> season.ifPresentOrElse(s -> spawnSeason(sender, t, s),
                        () -> spawnMob(sender, t, mob.get(), List.of(mob.get()))));
            }
        }
    }

    public List<String> complete(String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(SEASONS);
            for (SeasonalMob m : SeasonalMob.values()) out.add(m.id());
            out.addAll(ACTIONS);
            return out;
        }
        if (args.length == 2) {
            String what = plain(args[0]);
            if (what.equals("counts")) return worlds();
            if (what.equals("sweep")) {
                List<String> out = new ArrayList<>(worlds());
                out.add("all");
                return out;
            }
            List<String> out = new ArrayList<>(server.getOnlinePlayers().stream().map(Player::getName).toList());
            out.addAll(worlds());
            return out;
        }
        if (args.length == 3 && plain(args[0]).equals("sweep")) return worlds();
        return List.of();
    }

    private List<String> worlds() {
        return server.getWorlds().stream().map(World::getName).toList();
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " debug mobs <winter|spring|summer|fall|kind> [player | world x z] | "
                + "zombie|skeleton|pumpkin [player | world x z] | cycle [player | world x z] | counts [world] | sweep [all] [world]",
                NamedTextColor.GRAY));
        StringBuilder ids = new StringBuilder("  kinds:");
        for (SeasonalMob m : SeasonalMob.values()) ids.append(' ').append(m.id());
        sender.sendMessage(Component.text(ids.toString(), NamedTextColor.GRAY));
    }

    /** place and name of the target: a player, the sender, from the console the spawn of the main world */
    private record Target(Location at, String name) {
    }

    private Optional<Target> target(CommandSender sender, String[] rest) {
        if (rest.length >= 3) {
            World world = server.getWorld(rest[0]);
            if (world == null) {
                error(sender, "World " + rest[0] + " does not exist.");
                return Optional.empty();
            }
            try {
                int x = Integer.parseInt(rest[1]);
                int z = Integer.parseInt(rest[2]);
                Location at = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
                return Optional.of(new Target(at, world.getName() + " " + x + " " + z));
            } catch (NumberFormatException e) {
                error(sender, "Coordinates must be integers: " + rest[1] + " " + rest[2] + ".");
                return Optional.empty();
            }
        }
        String playerName = rest.length > 0 ? rest[0] : null;
        if (playerName != null) {
            Player p = server.getPlayerExact(playerName);
            if (p == null) {
                error(sender, "Player " + playerName + " is not online.");
                return Optional.empty();
            }
            return Optional.of(new Target(p.getLocation(), p.getName()));
        }
        if (sender instanceof Player p) return Optional.of(new Target(p.getLocation(), p.getName()));
        World main = server.getWorlds().getFirst();
        return Optional.of(new Target(main.getSpawnLocation(), "the spawn of world " + main.getName()));
    }

    /** a group of a kind the season adds (enabled and fitting the place, otherwise any kind of the season) */
    private void spawnSeason(CommandSender sender, Target t, Season season) {
        List<SeasonalMob> enabled = new ArrayList<>(MobRules.enabled(season, mobs.entitiesConfig()));
        List<SeasonalMob> candidates = enabled.isEmpty() ? new ArrayList<>(MobRules.all(season)) : enabled;
        if (enabled.isEmpty()) {
            note(sender, "No seasonal creatures are enabled in " + seasonName(season) + ", trying all kinds.");
        }
        Collections.shuffle(candidates);
        spawnMob(sender, t, candidates.getFirst(), candidates);
    }

    /** the first kind that finds its habitat at the target; otherwise the first kind anywhere */
    private void spawnMob(CommandSender sender, Target t, SeasonalMob first, List<SeasonalMob> candidates) {
        Season tag = tagSeason(t.at(), first.season());
        Optional<SeasonalMobs.DebugSpawn> done = Optional.empty();
        for (SeasonalMob m : candidates) {
            done = mobs.debugSpawn(t.at(), m, tag, false);
            if (done.isPresent()) break;
        }
        if (done.isEmpty()) done = mobs.debugSpawn(t.at(), first, tag, true);
        if (done.isEmpty()) {
            error(sender, "There is no free space at " + t.name() + ".");
            return;
        }
        SeasonalMobs.DebugSpawn d = done.get();
        if (d.count() == 0) {
            error(sender, NAMES.get(d.mob()) + ": another plugin cancelled the spawn (area protection?).");
            return;
        }
        ok(sender, "At " + t.name() + ": " + NAMES.get(d.mob()) + " × " + d.count() + " at " + d.at().getBlockX() + " "
                + d.at().getBlockY() + " " + d.at().getBlockZ() + ", belongs to " + seasonName(tag)
                + (d.habitat() ? "" : " (outside its habitat, for testing only)") + ".");
    }

    /** the seasonal creature belongs to the current season at the place, otherwise the end-of-season check would remove it */
    private Season tagSeason(Location at, Season fallback) {
        return mobs.seasonAt(at).orElse(fallback);
    }

    private void natural(CommandSender sender, Target t, String what) {
        EntityType type = what.equals("skeleton") ? EntityType.SKELETON : EntityType.ZOMBIE;
        Location at = t.at().clone().add(3, 0, 0);
        at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
        Optional<Entity> made = mobs.debugNatural(at, type, what.equals("pumpkin"));
        if (made.isEmpty()) {
            error(sender, "Another plugin or a world rule cancelled the spawn.");
            return;
        }
        Entity e = made.get();
        String result = e.getType() == type ? e.getType().name().toLowerCase(Locale.ROOT)
                : type.name().toLowerCase(Locale.ROOT) + " → " + e.getType().name().toLowerCase(Locale.ROOT);
        boolean pumpkin = e instanceof Mob mob && mobs.hasPumpkin(mob);
        ok(sender, "Natural spawn at " + t.name() + ": " + result + (pumpkin ? ", with a pumpkin on its head" : ", without a pumpkin")
                + " (" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ").");
    }

    private void cycle(CommandSender sender, Target t) {
        World world = t.at().getWorld();
        if (mobs.activeSeason(world).isEmpty()) {
            error(sender, "World " + world.getName() + " has no enabled seasons.");
            return;
        }
        ok(sender, "Pass around " + t.name() + ": " + mobs.debugCycle(t.at()) + ".");
    }

    private void counts(CommandSender sender, String worldName) {
        List<World> worlds = worldName != null ? Optional.ofNullable(server.getWorld(worldName)).map(List::of).orElse(List.of())
                : sender instanceof Player p ? List.of(p.getWorld()) : server.getWorlds();
        if (worlds.isEmpty()) {
            error(sender, "World " + worldName + " does not exist.");
            return;
        }
        for (World w : worlds) {
            Map<Season, Map<EntityType, Integer>> counts = mobs.counts(w);
            int total = counts.values().stream().flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum();
            StringBuilder sb = new StringBuilder(w.getName() + " (" + mobs.activeSeason(w).map(MobsDebug::seasonName)
                    .orElse("no season") + "): seasonal creatures " + total);
            counts.forEach((season, byType) -> {
                sb.append("; ").append(seasonName(season)).append(':');
                byType.forEach((type, n) -> sb.append(' ').append(type.name().toLowerCase(Locale.ROOT)).append(' ').append(n));
            });
            int[] decorated = mobs.decorated(w);
            sb.append("; with a pumpkin ").append(decorated[0]).append(", replacements (husk, stray) ").append(decorated[1])
                    .append(", chunks seen this season ").append(mobs.visitedChunks(w));
            ok(sender, sb.toString());
        }
        SeasonalMobs.Totals totals = mobs.totals();
        note(sender, "Since start: groups " + totals.groups() + " (" + totals.mobs() + " creatures), replacements "
                + totals.replaced() + ", pumpkins " + totals.pumpkins() + ", despawned " + totals.despawned() + ".");
    }

    private void sweep(CommandSender sender, String worldName, boolean all) {
        List<World> worlds = worldName != null ? Optional.ofNullable(server.getWorld(worldName)).map(List::of).orElse(List.of())
                : sender instanceof Player p ? List.of(p.getWorld()) : server.getWorlds();
        if (worlds.isEmpty()) {
            error(sender, "World " + worldName + " does not exist.");
            return;
        }
        for (World w : worlds) {
            int removed = mobs.sweep(w, all);
            ok(sender, w.getName() + ": " + (all ? "removed " + removed + " seasonal creatures (all without protection)"
                    : removed + " seasonal creatures whose season ended despawned") + ".");
        }
        if (!all && !mobs.entitiesConfig().despawnSeasonAnimals()) {
            note(sender, "despawn-season-animals is disabled, nothing despawns after the end of a season.");
        }
    }

    static String seasonName(Season season) {
        return season.key();
    }

    static String plain(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private static void note(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.YELLOW));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
