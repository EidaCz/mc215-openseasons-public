/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.network.ClientColors;
import cz.eida.minecraft.openseasons.season.Season;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /openseasons colors}: state of landscape colours in a world and a season preview (on clients only,
 * nothing is saved), e.g. {@code /openseasons colors fall 40} or {@code colors fall 70 fog}.
 */
final class ClientColorsCommand {

    static final List<String> OPTIONS = List.of("off", "winter", "spring", "summer", "fall");
    private static final String USAGE = "colors [<season> <percent> [fog] | off] [world]";

    private final OpenSeasonsPlugin plugin;

    ClientColorsCommand(OpenSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    void run(CommandSender sender, String label, String[] args) {
        ClientColors colors = plugin.clientColors();
        if (colors == null) {
            sender.sendMessage(Component.text("Landscape colours are not running (the packetevents plugin is missing).", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            Optional<World> world = world(sender, null);
            world.ifPresent(w -> sender.sendMessage(Component.text(w.getName() + ": " + colors.describe(w), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text(colors.stats(), NamedTextColor.GRAY));
            return;
        }
        if (args[1].equalsIgnoreCase("off")) {
            Optional<World> world = world(sender, args.length > 2 ? args[2] : null);
            if (world.isEmpty()) return;
            boolean had = colors.clearPreview(world.get().getName());
            sender.sendMessage(Component.text(had ? "Preview cancelled: " + colors.describe(world.get())
                    : "World " + world.get().getName() + " had no preview.", NamedTextColor.GREEN));
            return;
        }
        Optional<Season> season = new SeasonCommands(plugin).seasonOf(args[1]);
        if (season.isEmpty() || args.length < 3) {
            sender.sendMessage(Component.text("/" + label + " " + USAGE, NamedTextColor.GRAY));
            return;
        }
        double percent;
        try {
            percent = Double.parseDouble(args[2].replace("%", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Season percent 0–100, not " + args[2], NamedTextColor.RED));
            return;
        }
        int next = 3;
        boolean mist = args.length > next && SeasonCommands.plain(args[next]).equals("fog");
        if (mist) next++;
        Optional<World> world = world(sender, args.length > next ? args[next] : null);
        if (world.isEmpty()) return;
        colors.preview(world.get().getName(), season.get(), percent, mist);
        sender.sendMessage(Component.text("Preview " + world.get().getName() + ": " + colors.describe(world.get())
                + " – cancel with /" + label + " colors off", NamedTextColor.GREEN));
    }

    private Optional<World> world(CommandSender sender, String name) {
        if (name != null) {
            World w = plugin.getServer().getWorld(name);
            if (w == null) sender.sendMessage(Component.text("Unknown world " + name, NamedTextColor.RED));
            return Optional.ofNullable(w);
        }
        if (sender instanceof Player p) return Optional.of(p.getWorld());
        List<World> worlds = plugin.getServer().getWorlds();
        if (worlds.isEmpty()) return Optional.empty();
        return Optional.of(worlds.getFirst());
    }

    static List<String> complete(String[] args, List<String> worlds) {
        if (args.length == 2) return OPTIONS;
        if (args.length == 3 && args[1].toLowerCase(Locale.ROOT).equals("off")) return worlds;
        if (args.length == 3) return List.of("10", "40", "70", "95");
        if (args.length == 4) return List.of("fog");
        return worlds;
    }
}
