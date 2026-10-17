/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.region;

import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /openseasons region [world x z]}: region settings at the player's location or at the given
 * coordinates (also from the console and RCON). Read only.
 */
public final class RegionCommand {

    private final RegionService regions;
    private final SeasonService seasons;
    private final Supplier<LoadedConfig> config;
    private final Server server;

    public RegionCommand(RegionService regions, SeasonService seasons, Supplier<LoadedConfig> config, Server server) {
        this.regions = regions;
        this.seasons = seasons;
        this.config = config;
        this.server = server;
    }

    /** @param args arguments after {@code region} */
    public void run(CommandSender sender, String label, String[] args) {
        Location at;
        if (args.length >= 3) {
            World world = server.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage(Component.text("Unknown world " + args[0] + ".", NamedTextColor.RED));
                return;
            }
            try {
                at = new Location(world, Double.parseDouble(args[1]), 64, Double.parseDouble(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("The x and z coordinates must be numbers.", NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player p) {
            at = p.getLocation();
        } else {
            sender.sendMessage(Component.text("/" + label + " region <world> <x> <z>", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Regions: " + regions.describe(), NamedTextColor.GOLD));
        place(sender, at);
    }

    /** lines for {@code /openseasons info}: source and the player's location if they are in the listed world */
    public void info(CommandSender sender, String world) {
        sender.sendMessage(Component.text("  regions: " + regions.describe(), NamedTextColor.GRAY));
        if (sender instanceof Player p && p.getWorld().getName().equals(world)) place(sender, p.getLocation());
    }

    public List<String> complete(String[] args) {
        if (args.length == 1) return server.getWorlds().stream().map(World::getName).toList();
        return List.of();
    }

    private void place(CommandSender sender, Location at) {
        World world = at.getWorld();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        RegionSettings s = regions.at(world, cx, cz);
        sender.sendMessage(Component.text("  " + world.getName() + " " + at.getBlockX() + " " + at.getBlockZ()
                + " (chunk " + cx + ", " + cz + "): " + s.describe(), s.isNone() ? NamedTextColor.GRAY : NamedTextColor.GREEN));
        Optional<RegionService.Local> local = regions.localAt(world, cx, cz);
        Optional<Season> here = local.map(RegionService.Local::season);
        Optional<Season> global = seasons.state(world.getName()).filter(WorldSeasonState::enabled).map(WorldSeasonState::season);
        sender.sendMessage(Component.text("  season here: " + name(here) + local.map(l -> " – " + l.describe()).orElse("")
                + (here.equals(global) ? "" : ", game calendar of the world " + name(global))
                + "; colours and particles " + yesNo(s.effects()) + ", block changes " + yesNo(s.blockChanges())
                + ", seasonal animals " + yesNo(s.mobSpawning()), NamedTextColor.GRAY));
    }

    private String name(Optional<Season> season) {
        return season.map(s -> config.get().lang().seasonDisplayName(s) + " (" + s + ")").orElse("none");
    }

    private static String yesNo(boolean allowed) {
        return allowed ? "yes" : "no";
    }
}
