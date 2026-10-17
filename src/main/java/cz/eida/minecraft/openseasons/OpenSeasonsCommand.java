/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.calendar.MonthDefinition;
import cz.eida.minecraft.openseasons.config.ConfigException;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.config.RealCalendarSettings;
import cz.eida.minecraft.openseasons.entity.MobsDebug;
import cz.eida.minecraft.openseasons.region.RegionCommand;
import cz.eida.minecraft.openseasons.region.RegionService;
import cz.eida.minecraft.openseasons.events.EventService;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /openseasons reload | info [world] | set | setdate | … | debug <phenomenon> …} for administrators
 * (permission {@code mc215os.admin}). {@code debug} triggers phenomena manually, so they can be tried without
 * waiting for chance; only with {@code debug-enabled: true}.
 */
final class OpenSeasonsCommand implements TabExecutor {

    private final OpenSeasonsPlugin plugin;

    OpenSeasonsCommand(OpenSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(plugin, sender);
            case "info" -> info(sender, args.length > 1 ? args[1] : null);
            case "debug" -> {
                if (debugEnabled()) debug(sender, label, args);
                else sender.sendMessage(Component.text("Debug commands are off: set debug-enabled: true in the OpenSeasons "
                        + "config.yml and run /" + label + " reload.", NamedTextColor.RED));
            }
            case "set", "setdate", "nextseason", "disable", "pausetime" -> new SeasonCommands(plugin).admin(sender, label, args);
            case "colors" -> new ClientColorsCommand(plugin).run(sender, label, args);
            case "restore" -> plugin.flora().command().restore(sender, label, Arrays.copyOfRange(args, 1, args.length));
            case "region" -> regionCommand().run(sender, label, Arrays.copyOfRange(args, 1, args.length));
            case "help" -> help(sender, label);
            default -> usage(sender, label);
        }
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " reload | info [world] | set | setdate | nextseason | disable | pausetime"
                + " | colors [<season> <%>] | restore <world> [radius] | region [world x z] | help"
                + (debugEnabled() ? " | debug <phenomenon> [player]" : ""), NamedTextColor.GRAY));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("OpenSeasons", NamedTextColor.GOLD));
        List<String> lines = new java.util.ArrayList<>(new SeasonCommands(plugin).adminHelp(label));
        lines.add("/" + label + " info [world] – configuration sources, calendar, clock and regions of a world");
        lines.add("/" + label + " colors [<season> <%> [fog] | off] [world] – preview of landscape colours");
        lines.add("/" + label + " restore <world> [radius] [confirm] – restores blocks changed by seasonal plants");
        lines.add("/" + label + " region [world x z] – season settings of regions");
        lines.add("/" + label + " reload – reloads the configuration");
        if (debugEnabled()) lines.add("/" + label + " debug <phenomenon> [player] – triggers a phenomenon now");
        lines.forEach(l -> sender.sendMessage(Component.text(l, NamedTextColor.GRAY)));
    }

    private RegionCommand regionCommand() {
        return new RegionCommand(plugin.regions(), plugin.seasons(), plugin::config, plugin.getServer());
    }

    /** {@code debug} only with {@code debug-enabled: true} in config.yml */
    private boolean debugEnabled() {
        return plugin.config().plugin().debugEnabled();
    }

    /** reloads the configuration and reports the result */
    static void reload(OpenSeasonsPlugin plugin, CommandSender sender) {
        try {
            plugin.reload();
        } catch (ConfigException e) {
            sender.sendMessage(Component.text("The configuration cannot be loaded, the previous one applies: " + e.getMessage(),
                    NamedTextColor.RED));
            return;
        }
        int warnings = plugin.config().warnings().size();
        sender.sendMessage(Component.text("Configuration loaded" + (warnings > 0 ? ", warnings: " + warnings
                + " (see the console)" : "") + ".", warnings > 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    }

    /** phenomena that can be triggered manually: {@code /openseasons debug <phenomenon> …} */
    static final List<String> DEBUG_PHENOMENA =
            List.of("ufo", "fireflies", "falling-leaves", "small-falling-leaves", "shooting-star", "night-sparks", "aurora", "mobs", "flora");

    /** all phenomena including calendar events ({@link EventService#DEBUG_PHENOMENA}) */
    static List<String> allDebugPhenomena() {
        return java.util.stream.Stream.concat(DEBUG_PHENOMENA.stream(), EventService.DEBUG_PHENOMENA.stream()).toList();
    }

    private void debug(CommandSender sender, String label, String[] args) {
        String phenomenon = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (phenomenon) {
            case "ufo" -> ufo(sender, Arrays.copyOfRange(args, 2, args.length));
            case "mobs" -> new MobsDebug(plugin.getServer(), plugin.seasonalMobs())
                    .run(sender, label, Arrays.copyOfRange(args, 2, args.length));
            case "flora" -> plugin.flora().command().debug(sender, label, Arrays.copyOfRange(args, 2, args.length));
            case "event", "present", "egg", "fireworks", "christmas-tree", "christmas-sparkles", "killer-bunny",
                 "halloween-mob", "bats" -> plugin.events().debug(sender, label, Arrays.copyOfRange(args, 1, args.length));
            case "fireflies", "falling-leaves", "small-falling-leaves", "shooting-star", "night-sparks", "aurora" -> effect(sender, phenomenon,
                    args.length > 2 ? args[2] : null);
            default -> sender.sendMessage(Component.text("/" + label + " debug <" + String.join("|", allDebugPhenomena())
                    + "> [player]", NamedTextColor.GRAY));
        }
    }

    /** season particles right at the player, regardless of the season and time of day */
    private void effect(CommandSender sender, String phenomenon, String playerName) {
        Player target = playerName != null ? plugin.getServer().getPlayerExact(playerName)
                : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Component.text(playerName != null ? "Player " + playerName + " is not online."
                    : "Enter a player: debug " + phenomenon + " <player>", NamedTextColor.RED));
            return;
        }
        String what = switch (phenomenon) {
            case "fireflies" -> "fireflies (" + plugin.effects().fireflies(target) + ")";
            case "falling-leaves" -> "big leaves (" + plugin.effects().bigLeaves(target) + ")";
            case "small-falling-leaves" -> "small leaves (" + plugin.effects().smallLeaves(target) + ")";
            case "night-sparks" -> {
                plugin.effects().nightSparks(target);
                yield "winter sparkling for 15 s";
            }
            case "aurora" -> {
                plugin.aurora().debug(target);
                yield "aurora in the north for a minute";
            }
            default -> {
                plugin.effects().shootingStar(target);
                yield "shooting star";
            }
        };
        sender.sendMessage(Component.text("At " + target.getName() + ": " + what + ".", NamedTextColor.GREEN));
        if (!plugin.particleViewers().enabled(target)) particlesOffNote(sender, target);
    }

    /**
     * UFO fly-over now: {@code debug ufo [player] [formation]}. Without a player above the one who entered
     * the command, from the console above the spawn of the main world.
     */
    private void ufo(CommandSender sender, String[] args) {
        boolean formation = args.length > 0 && args[args.length - 1].equalsIgnoreCase("formation");
        boolean named = (formation ? args.length - 1 : args.length) >= 1;
        Location anchor;
        Player viewer;
        String where;
        if (named) {
            Player target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player " + args[0] + " is not online.", NamedTextColor.RED));
                return;
            }
            anchor = target.getLocation();
            viewer = target;
            where = target.getName();
        } else if (sender instanceof Player p) {
            anchor = p.getLocation();
            viewer = p;
            where = p.getName();
        } else {
            anchor = plugin.getServer().getWorlds().getFirst().getSpawnLocation();
            viewer = null;
            where = "the spawn";
        }
        if (plugin.sky().spawnAt(anchor, viewer, formation)) {
            sender.sendMessage(Component.text("UFO flies above " + where
                    + (formation ? " (formation)" : "") + ".", NamedTextColor.GREEN));
            if (viewer != null && !plugin.particleViewers().enabled(viewer)) particlesOffNote(sender, viewer);
        } else {
            sender.sendMessage(Component.text("The place above the player is not loaded, try again.",
                    NamedTextColor.YELLOW));
        }
    }

    /** the phenomenon was created, but a player with particles turned off does not see it */
    private static void particlesOffNote(CommandSender sender, Player target) {
        sender.sendMessage(Component.text(target.getName() + " has particles turned off (/toggleseasonparticles) and does not see it.",
                NamedTextColor.YELLOW));
    }

    private void info(CommandSender sender, String worldArg) {
        LoadedConfig c = plugin.config();
        sender.sendMessage(Component.text("OpenSeasons " + plugin.getPluginMeta().getVersion(), NamedTextColor.GOLD));
        c.sources().forEach((role, path) ->
                sender.sendMessage(Component.text("  " + role + " ← " + path, NamedTextColor.GRAY)));

        String world = worldArg != null ? worldArg
                : sender instanceof Player p ? p.getWorld().getName() : null;
        if (world == null) {
            sender.sendMessage(Component.text("Worlds with seasons: " + String.join(", ", plugin.seasons().worlds()),
                    NamedTextColor.GRAY));
            return;
        }
        Optional<WorldSeasonState> state = plugin.seasons().state(world);
        if (state.isEmpty()) {
            sender.sendMessage(Component.text("World " + world + " has no seasons.", NamedTextColor.RED));
            return;
        }
        WorldSeasonState s = state.get();
        GameCalendar cal = plugin.seasons().calendar();
        sender.sendMessage(Component.text(world + ": " + c.lang().seasonDisplayName(s.season())
                + " (" + s.season() + (s.enabled() ? "" : ", disabled") + ")", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  " + cal.weekdayName(s.date()) + " " + s.date().day() + " "
                + cal.monthName(s.date()) + " " + s.date().year() + " (" + c.calendar().formatDate(s.date()) + ")",
                NamedTextColor.GRAY));
        GameCalendar.SeasonChange next = cal.nextSeasonChange(s.date(), s.season());
        sender.sendMessage(Component.text("  next season " + c.lang().seasonDisplayName(next.season())
                + " (" + next.season() + ") in " + next.daysUntil()
                + " days, season length " + cal.seasonLength(s.season()) + " days", NamedTextColor.GRAY));
        StringBuilder ranges = new StringBuilder("  seasons by the calendar (day/month):");
        for (Season season : Season.values()) {
            GameCalendar.SeasonRange r = cal.range(season);
            ranges.append(' ').append(c.lang().seasonDisplayName(season)).append(' ')
                    .append(r.from().day()).append('/').append(r.from().month()).append(" – ")
                    .append(r.to().day()).append('/').append(r.to().month())
                    .append(season == Season.FALL ? "" : ",");
        }
        sender.sendMessage(Component.text(ranges.toString(), NamedTextColor.GRAY));
        String offset = s.dayOffset().isEmpty() ? "not bound to the clock yet"
                : s.dayOffset().getAsLong() == 0 ? "by the age of the world"
                : "shifted by " + s.dayOffset().getAsLong() + " days against the age of the world";
        sender.sendMessage(Component.text("  date " + offset, NamedTextColor.GRAY));
        MonthDefinition month = cal.month(cal.normalize(s.date()).month());
        String paused = plugin.clock().paused(world) ? ", stopped (/openseasons pausetime)" : "";
        sender.sendMessage(Component.text((plugin.clock().drives(world)
                ? "  time by the calendar: day " + month.dayLengthMinutes() + " min, night " + month.nightLengthMinutes() + " min"
                : "  time driven by vanilla (advance_time)") + paused, NamedTextColor.GRAY));
        realInfo(sender, world);
        regionCommand().info(sender, world);
    }

    /** real calendar: today's real season, the season of the whole world and the active ranges */
    private void realInfo(CommandSender sender, String world) {
        RegionService regions = plugin.regions();
        RealCalendarSettings rc = regions.realCalendar();
        java.time.LocalDate today = regions.today();
        RegionService.Local real = regions.realLocal();
        sender.sendMessage(Component.text(String.format(Locale.ROOT, "  real date %s: %s %.0f %% (next in %d days)",
                today, plugin.config().lang().seasonDisplayName(real.season()), real.progress(),
                rc.calendar().daysUntilNext(today)), NamedTextColor.GRAY));
        World w = plugin.getServer().getWorld(world);
        if (w != null) {
            regions.worldLocal(w).ifPresent(l -> sender.sendMessage(Component.text("  season of the whole world: "
                    + plugin.config().lang().seasonDisplayName(l.season()) + " – " + l.describe()
                    + " (source " + rc.worldSource(world).name().toLowerCase(Locale.ROOT) + ")", NamedTextColor.GRAY)));
        }
        List<String> active = rc.active(today).stream().map(p -> p.id() + " " + p.range()
                + (p.appliesToWorld(world) ? " (whole world)" : "")).toList();
        sender.sendMessage(Component.text("  real date ranges today: " + (active.isEmpty() ? "none"
                : String.join(", ", active)), NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new java.util.ArrayList<>(List.of("reload", "info", "help", "colors", "restore", "region"));
            subs.addAll(SeasonCommands.ADMIN_SUBCOMMANDS);
            if (debugEnabled()) subs.add("debug");
            return subs;
        }
        if (SeasonCommands.ADMIN_SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return new SeasonCommands(plugin).completeAdmin(args);
        }
        if (args[0].equalsIgnoreCase("region")) return regionCommand().complete(Arrays.copyOfRange(args, 1, args.length));
        if (args[0].equalsIgnoreCase("restore")) {
            return plugin.flora().command().completeRestore(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args[0].equalsIgnoreCase("colors")) {
            return ClientColorsCommand.complete(args, plugin.getServer().getWorlds().stream().map(World::getName).toList());
        }
        if (args[0].equalsIgnoreCase("debug")) {
            if (!debugEnabled()) return List.of();
            if (args.length == 2) return allDebugPhenomena();
            if (EventService.DEBUG_PHENOMENA.contains(args[1].toLowerCase(Locale.ROOT))) return plugin.events().complete(args);
            if (args[1].equalsIgnoreCase("mobs")) {
                return new MobsDebug(plugin.getServer(), plugin.seasonalMobs()).complete(Arrays.copyOfRange(args, 2, args.length));
            }
            if (args[1].equalsIgnoreCase("flora")) {
                return plugin.flora().command().completeDebug(Arrays.copyOfRange(args, 2, args.length));
            }
            if (args.length == 3 && !args[1].equalsIgnoreCase("ufo")) {
                return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("ufo")) {
                List<String> out = new java.util.ArrayList<>(plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName).toList());
                out.add("formation");
                return out;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("ufo")) return List.of("formation");
            return List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) return List.copyOf(plugin.seasons().worlds());
        return List.of();
    }
}
