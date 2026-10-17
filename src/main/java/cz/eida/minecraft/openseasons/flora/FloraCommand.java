/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.flora;

import cz.eida.minecraft.openseasons.world.PlacementJournal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Seasonal plant commands under {@code /openseasons}:
 * <ul>
 *     <li>{@code restore <world> [radius] [confirm]} – restores the blocks OpenSeasons placed;
 *     it first shows what will change and waits for a confirmation,</li>
 *     <li>{@code debug flora <kind|all|wither|info> [player | world x z]} – a manual pass around a player or place.</li>
 * </ul>
 */
public final class FloraCommand {

    static final long CONFIRM_MS = 30_000L;
    static final List<String> CONFIRM = List.of("confirm");
    /** {@code debug flora} apart from the kinds */
    static final List<String> DEBUG_EXTRA = List.of("all", "wither", "info");

    private record Pending(String world, int radius, Location center, long until) {
    }

    private final Supplier<FloraService> flora;
    private final Server server;
    private final Map<String, Pending> pending = new HashMap<>();

    public FloraCommand(Server server, Supplier<FloraService> flora) {
        this.server = server;
        this.flora = flora;
    }

    // --- restore ---

    /** @param args arguments after {@code restore} */
    public void restore(CommandSender sender, String label, String[] args) {
        FloraService service = flora.get();
        if (service == null) {
            sender.sendMessage(Component.text("Seasonal plants are not running.", NamedTextColor.RED));
            return;
        }
        String usage = "/" + label + " restore <world> [radius] [confirm]";
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        boolean confirm = !rest.isEmpty() && CONFIRM.contains(rest.getLast().toLowerCase(Locale.ROOT));
        if (confirm) rest.removeLast();
        if (rest.isEmpty() || rest.size() > 2) {
            sender.sendMessage(Component.text(usage, NamedTextColor.GRAY));
            return;
        }
        World world = server.getWorld(rest.get(0));
        if (world == null) {
            sender.sendMessage(Component.text("World " + rest.get(0) + " does not exist.", NamedTextColor.RED));
            return;
        }
        int radius = -1;
        if (rest.size() == 2) {
            try {
                radius = Integer.parseInt(rest.get(1));
            } catch (NumberFormatException e) {
                radius = -2;
            }
            if (radius < 1) {
                sender.sendMessage(Component.text("The radius is a number of blocks (at least 1): " + usage, NamedTextColor.RED));
                return;
            }
        }
        Location center = sender instanceof Player p && p.getWorld().equals(world) ? p.getLocation()
                : world.getSpawnLocation();
        String key = sender.getName();
        if (confirm) {
            Pending p = pending.remove(key);
            if (p == null || p.until < System.currentTimeMillis() || !p.world.equals(world.getName())
                    || p.radius != radius) {
                sender.sendMessage(Component.text("First enter " + "/" + label + " restore " + world.getName()
                        + (radius > 0 ? " " + radius : "") + " and then confirm within 30 s.", NamedTextColor.RED));
                return;
            }
            List<PlacementJournal.Entry> entries = service.placedWithin(world, p.center.getBlockX(),
                    p.center.getBlockZ(), radius);
            service.restore(world, entries, sender);
            sender.sendMessage(Component.text("Restoring " + entries.size() + " blocks in world " + world.getName()
                    + " is running.", NamedTextColor.GREEN));
            return;
        }
        List<PlacementJournal.Entry> entries = service.placedWithin(world, center.getBlockX(), center.getBlockZ(), radius);
        String where = radius < 0 ? "in the whole world " + world.getName()
                : "within " + radius + " blocks of " + (sender instanceof Player p && p.getWorld().equals(world)
                ? "you" : "the spawn") + " (" + center.getBlockX() + ", " + center.getBlockZ() + ") in world " + world.getName();
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("OpenSeasons placed nothing " + where + ", there is nothing to restore.",
                    NamedTextColor.YELLOW));
            return;
        }
        pending.put(key, new Pending(world.getName(), radius, center, System.currentTimeMillis() + CONFIRM_MS));
        sender.sendMessage(Component.text("Restoring will revert " + entries.size() + " blocks of OpenSeasons " + where
                + ": " + describe(PlacementJournal.countByKind(entries)) + ".", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Blocks someone else changed in the meantime and player plants stay. "
                + "New plants keep appearing while modify-blocks is enabled.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Confirm within 30 s: /" + label + " restore " + world.getName()
                + (radius > 0 ? " " + radius : "") + " confirm", NamedTextColor.YELLOW));
    }

    // --- debug ---

    /**
     * @param args arguments after {@code debug flora}: kind and player, or world and coordinates x z (from the console
     *             without a player; the chunks must be loaded, e.g. {@code /forceload})
     */
    public void debug(CommandSender sender, String label, String[] args) {
        FloraService service = flora.get();
        String usage = "/" + label + " debug flora <" + String.join("|", debugChoices()) + "> [player | <world> <x> <z>]";
        if (service == null) {
            sender.sendMessage(Component.text("Seasonal plants are not running.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text(usage, NamedTextColor.GRAY));
            return;
        }
        String what = args[0].toLowerCase(Locale.ROOT);
        Optional<PlantKind> kind = PlantKind.byId(what);
        if (kind.isEmpty() && !DEBUG_EXTRA.contains(what)) {
            sender.sendMessage(Component.text(usage, NamedTextColor.GRAY));
            return;
        }
        Location center;
        String where;
        if (args.length >= 4) {
            World world = server.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage(Component.text("World " + args[1] + " does not exist.", NamedTextColor.RED));
                return;
            }
            try {
                center = new Location(world, Integer.parseInt(args[2]), 0, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text(usage, NamedTextColor.GRAY));
                return;
            }
            where = world.getName() + " " + args[2] + " " + args[3];
        } else {
            Player target = args.length > 1 ? server.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target == null) {
                sender.sendMessage(Component.text(args.length > 1 ? "Player " + args[1] + " is not online."
                        : "Enter a player or place: debug flora " + what + " <player | world x z>", NamedTextColor.RED));
                return;
            }
            center = target.getLocation();
            where = target.getName();
        }
        switch (what) {
            case "info" -> service.status(center.getWorld())
                    .forEach(line -> sender.sendMessage(Component.text(line, NamedTextColor.GRAY)));
            case "wither" -> {
                FloraService.DebugResult r = service.debugWither(center);
                sender.sendMessage(Component.text("At " + where + " removed " + r.removed().restored()
                        + " withered blocks (" + r.chunks() + " chunks with a journal)"
                        + (r.removed().dropped() > 0 ? ", " + r.removed().dropped() + " already changed by someone else" : "")
                        + ".", NamedTextColor.GREEN));
            }
            default -> {
                List<PlantKind> kinds = kind.map(List::of).orElse(List.of(PlantKind.values()));
                FloraService.DebugResult r = service.debugPlace(center, kinds);
                int total = r.placed().values().stream().mapToInt(Integer::intValue).sum();
                Map<String, Integer> named = new TreeMap<>();
                r.placed().forEach((k, n) -> named.put(k.id(), n));
                sender.sendMessage(Component.text("At " + where + " placed " + total + " ("
                        + (named.isEmpty() ? "no suitable place or the chunk limit is full" : describe(named)) + "), "
                        + r.chunks() + " loaded chunks.", total > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                if (r.outOfSeason() > 0) {
                    sender.sendMessage(Component.text(r.outOfSeason() + " of them are out of their season: they stay for 10 minutes, "
                            + "then the ticker removes them (or right away: debug flora wither).", NamedTextColor.GRAY));
                }
            }
        }
    }

    public static List<String> debugChoices() {
        List<String> out = new ArrayList<>();
        for (PlantKind k : PlantKind.values()) out.add(k.id());
        out.addAll(DEBUG_EXTRA);
        return out;
    }

    /** completion after {@code debug flora}: kind, player or world */
    public List<String> completeDebug(String[] args) {
        if (args.length == 1) return debugChoices();
        if (args.length == 2) {
            List<String> out = new ArrayList<>(server.getOnlinePlayers().stream().map(Player::getName).toList());
            server.getWorlds().forEach(w -> out.add(w.getName()));
            return out;
        }
        return List.of();
    }

    /** completion after {@code restore}: world, radius, confirmation */
    public List<String> completeRestore(String[] args) {
        if (args.length == 1) return server.getWorlds().stream().map(World::getName).toList();
        if (args.length == 2) return List.of("64", "128", "256", "confirm");
        if (args.length == 3) return List.of("confirm");
        return List.of();
    }

    private static String describe(Map<String, Integer> byKind) {
        List<String> parts = new ArrayList<>();
        byKind.forEach((id, n) -> parts.add((PlacementJournal.RESTORE.equals(id) ? "waiting for restore"
                : PlantKind.byId(id).map(PlantKind::displayName).orElse(id)) + " " + n));
        return String.join(", ", parts);
    }
}
