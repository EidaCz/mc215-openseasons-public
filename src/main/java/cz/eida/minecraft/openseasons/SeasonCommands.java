/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.calendar.CalendarDate;
import cz.eida.minecraft.openseasons.calendar.GameCalendar;
import cz.eida.minecraft.openseasons.config.LangConfig;
import cz.eida.minecraft.openseasons.config.LoadedConfig;
import cz.eida.minecraft.openseasons.events.EventService;
import cz.eida.minecraft.openseasons.events.SeasonEvent;
import cz.eida.minecraft.openseasons.season.Season;
import cz.eida.minecraft.openseasons.season.SeasonService;
import cz.eida.minecraft.openseasons.season.WorldSeasonState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /season [world]}: season, date, time and calendar events of a world (permission
 * {@code mc215os.getinfo}). Also the season administration under {@code /openseasons}: set, setdate,
 * nextseason, disable and pausetime (permission {@code mc215os.admin}); OP may do everything.
 *
 * <p>{@code set} and {@code nextseason} do not change the date (the year stays by the age of the world),
 * {@code setdate} sets the date, which then runs on with the world clock.
 */
final class SeasonCommands implements TabExecutor {

    /** season administration under {@code /openseasons} */
    static final List<String> ADMIN_SUBCOMMANDS = List.of("set", "setdate", "nextseason", "disable", "pausetime");

    private final OpenSeasonsPlugin plugin;

    SeasonCommands(OpenSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mc215os.getinfo")) return noPermission(sender);
        world(sender, arg(args, 0), "/" + label + " <world>").ifPresent(w -> info(sender, w));
        return true;
    }

    /** {@code /openseasons <set|setdate|nextseason|disable|pausetime> …} */
    void admin(CommandSender sender, String label, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> set(sender, label, args);
            case "setdate" -> setDate(sender, label, args);
            case "nextseason" -> world(sender, arg(args, 1), "/" + label + " nextseason <world>")
                    .ifPresent(w -> nextSeason(sender, w));
            case "disable" -> world(sender, arg(args, 1), "/" + label + " disable <world>")
                    .ifPresent(w -> disable(sender, label, w));
            case "pausetime" -> world(sender, arg(args, 1), "/" + label + " pausetime <world>")
                    .ifPresent(w -> pauseTime(sender, label, w));
            default -> usage(sender, "/" + label + " <" + String.join("|", ADMIN_SUBCOMMANDS) + "> …");
        }
    }

    private void info(CommandSender sender, World world) {
        LoadedConfig c = plugin.config();
        LangConfig lang = c.lang();
        Optional<WorldSeasonState> state = plugin.seasons().state(world.getName());
        if (state.isEmpty() || !state.get().enabled()) {
            lang(sender, lang.get("SEASONCOMMAND-DISABLED"));
            return;
        }
        WorldSeasonState s = state.get();
        GameCalendar cal = plugin.seasons().calendar();
        lang(sender, lang.get("SEASONSCOMMAND-CURRENTSEASON")
                .replace("$current_season$", lang.seasonDisplayName(s.season())));
        if (!c.calendar().enabled()) return;

        CalendarDate d = s.date();
        lang(sender, lang.get("SEASONCOMMAND-CURRENTDATE")
                .replace("$current_weekday$", cal.weekdayName(d))
                .replace("$current_month$", cal.monthName(d))
                .replace("$current_day$", Integer.toString(d.day()))
                .replace("$current_year$", Integer.toString(d.year())));
        if (plugin.seasons().automaticCycling()) {
            GameCalendar.SeasonChange next = cal.nextSeasonChange(d, s.season());
            lang(sender, lang.get("SEASONSCOMMAND-DAYSUNTILNEXTSEASON")
                    .replace("$next_season$", lang.seasonDisplayName(next.season()))
                    .replace("$days_until_next_season$", Integer.toString(next.daysUntil())));
        }
        lang(sender, lang.get("SEASONCOMMAND-TIME")
                .replace("$time$", c.calendar().timeFormat().format(world.getTime())));
        EventService events = plugin.events();
        if (events == null) return;
        List<String> active = events.activeNames(world.getName());
        if (!active.isEmpty()) {
            lang(sender, lang.get("SEASONCOMMAND-EVENTS").replace("$events$", String.join(", ", active)));
        }
        events.next(world.getName()).ifPresent(next -> lang(sender, lang.get("SEASONSCOMMAND-DAYSUNTILNEXTEVENT")
                .replace("$next_event$", String.join(", ", next.events().stream().map(SeasonEvent::name).toList()))
                .replace("$days_until_next_event$", Integer.toString(next.daysUntil()))));
    }

    /** {@code set <season> [world]} */
    private void set(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, "/" + label + " set <winter|spring|summer|fall> [world]");
            return;
        }
        Optional<Season> season = seasonOf(args[1]);
        if (season.isEmpty()) {
            error(sender, "Unknown season " + args[1] + ", use winter, spring, summer or fall.");
            return;
        }
        Optional<World> found = world(sender, arg(args, 2), "/" + label + " set " + args[1] + " <world>");
        if (found.isEmpty()) return;
        World w = found.get();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            error(sender, "There are no seasons in the Nether and the End.");
            return;
        }
        String name = w.getName();
        boolean wasEnabled = plugin.seasons().state(name).map(WorldSeasonState::enabled).orElse(false);
        if (!wasEnabled) plugin.setSeasonsEnabled(w, true);
        Season current = plugin.seasons().state(name).map(WorldSeasonState::season).orElse(null);
        if (current != season.get() && !plugin.seasons().setSeason(name, season.get(), SeasonService.Cause.COMMAND)) {
            error(sender, "Another plugin cancelled the season change in world " + name + ".");
            return;
        }
        if (wasEnabled && current == season.get()) {
            ok(sender, "World " + name + " already has the season " + seasonName(season.get()) + ".");
            return;
        }
        ok(sender, "World " + name + ": season " + seasonName(season.get()) + (wasEnabled ? "" : ", seasons turned on")
                + untilNext(name) + ".");
    }

    private void setDate(CommandSender sender, String label, String[] args) {
        boolean american = plugin.config().calendar().americanDateFormat();
        String format = american ? "mm/dd/yyyy" : "dd/mm/yyyy";
        if (args.length < 2) {
            usage(sender, "/" + label + " setdate <" + format + "> [world]");
            return;
        }
        GameCalendar cal = plugin.seasons().calendar();
        CalendarDate date;
        try {
            date = parseDate(args[1], american, cal);
        } catch (IllegalArgumentException e) {
            error(sender, e.getMessage());
            return;
        }
        Optional<World> found = world(sender, arg(args, 2), "/" + label + " setdate " + args[1] + " <world>");
        if (found.isEmpty()) return;
        String name = found.get().getName();
        if (plugin.seasons().state(name).isEmpty()) {
            error(sender, "World " + name + " has no seasons (/" + label + " set <season> " + name + " turns them on).");
            return;
        }
        plugin.seasons().setDate(name, date, SeasonService.Cause.COMMAND);
        WorldSeasonState s = plugin.seasons().state(name).orElseThrow();
        ok(sender, "World " + name + ": " + cal.weekdayName(s.date()) + " " + s.date().day() + " "
                + cal.monthName(s.date()) + " " + s.date().year() + ", season " + seasonName(s.season())
                + ". The date runs on with the world clock.");
    }

    private void nextSeason(CommandSender sender, World w) {
        String name = w.getName();
        Optional<WorldSeasonState> state = plugin.seasons().state(name).filter(WorldSeasonState::enabled);
        if (state.isEmpty()) {
            lang(sender, plugin.config().lang().get("SEASONCOMMAND-DISABLED"));
            return;
        }
        Season next = state.get().season().next();
        if (!plugin.seasons().setSeason(name, next, SeasonService.Cause.COMMAND)) {
            error(sender, "Another plugin cancelled the season change in world " + name + ".");
            return;
        }
        ok(sender, "World " + name + ": season " + seasonName(next) + untilNext(name) + ".");
    }

    private void disable(CommandSender sender, String label, World w) {
        String name = w.getName();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            error(sender, "There are no seasons in the Nether and the End.");
            return;
        }
        Optional<WorldSeasonState> state = plugin.seasons().state(name);
        if (state.isEmpty()) {
            error(sender, "World " + name + " has no seasons.");
        } else if (!state.get().enabled()) {
            error(sender, "World " + name + " already has seasons turned off.");
        } else {
            plugin.setSeasonsEnabled(w, false);
            ok(sender, "World " + name + ": seasons turned off. /" + label + " set <season> " + name + " turns them on.");
        }
    }

    /**
     * Stops the time in a world, or lets it run again. Where OpenSeasons drives the time, it stops the
     * calendar clock (sleeping then does not skip the night); elsewhere it toggles the vanilla
     * {@code advance_time}. Only normal worlds, the time does not run in the Nether and the End.
     */
    private void pauseTime(CommandSender sender, String label, World w) {
        String name = w.getName();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            error(sender, "The time does not run in the Nether and the End, it can only be stopped in a normal world.");
            return;
        }
        CalendarClock clock = plugin.clock();
        boolean paused = clock.togglePause(w);
        String again = " /" + label + " pausetime " + name + " again.";
        if (clock.drives(name)) {
            ok(sender, paused
                    ? "World " + name + ": time stopped, sleeping does not skip the night. To let it run:" + again
                    : "World " + name + ": the time runs by the calendar again. To stop it:" + again);
        } else {
            ok(sender, paused
                    ? "World " + name + ": time stopped (advance_time false). To let it run:" + again
                    : "World " + name + ": the time runs again (advance_time true). To stop it:" + again);
        }
    }

    /** help lines of the season administration for {@code /openseasons} */
    List<String> adminHelp(String label) {
        String format = plugin.config().calendar().americanDateFormat() ? "mm/dd/yyyy" : "dd/mm/yyyy";
        return List.of(
                "/season [world] – season, date and time",
                "/" + label + " set <winter|spring|summer|fall> [world] – season without changing the date, valid until"
                        + " the next season start; turns a disabled world on",
                "/" + label + " setdate <" + format + "> [world] – date, runs on with the world clock",
                "/" + label + " nextseason [world] – next season",
                "/" + label + " disable [world] – turns seasons off",
                "/" + label + " pausetime [world] – stops the time, or lets it run again");
    }

    /**
     * Date from a command: day/month/year, with {@code american-date-format} month/day/year. The month is
     * its order in the calendar, the year at least 1.
     *
     * @throws IllegalArgumentException with a text for the administrator
     */
    static CalendarDate parseDate(String text, boolean american, GameCalendar cal) {
        String format = american ? "mm/dd/yyyy" : "dd/mm/yyyy";
        String[] parts = text.split("/");
        if (parts.length != 3) throw new IllegalArgumentException("Enter the date as " + format + ".");
        int[] n = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                n[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Enter the date as " + format + " (numbers only).");
            }
        }
        int day = american ? n[1] : n[0];
        int month = american ? n[0] : n[1];
        int year = n[2];
        if (month < 1 || month > cal.months().size()) {
            throw new IllegalArgumentException("Month " + month + " does not exist, the calendar has " + cal.months().size() + ".");
        }
        int days = cal.month(month).days();
        if (day < 1 || day > days) {
            throw new IllegalArgumentException(cal.month(month).name() + " has " + days + " days, not " + day + ".");
        }
        if (year < 1) throw new IllegalArgumentException("The year must be at least 1.");
        return new CalendarDate(day, month, year);
    }

    /** how long a manually set season lasts: with automatic cycling until the next day change */
    private String untilNext(String world) {
        if (!plugin.seasons().automaticCycling()) return "";
        return plugin.seasons().state(world).map(s -> {
            GameCalendar cal = plugin.seasons().calendar();
            Season byCalendar = cal.seasonAt(s.date());
            if (byCalendar != s.season()) {
                return " (until the next day change, then by the calendar again: " + seasonName(byCalendar) + ")";
            }
            GameCalendar.SeasonChange next = cal.nextSeasonChange(s.date(), s.season());
            return " (by the calendar, next season " + seasonName(next.season()) + " in " + next.daysUntil()
                    + " days)";
        }).orElse("");
    }

    /** season by its English key or display name (ignoring diacritics and case) */
    Optional<Season> seasonOf(String text) {
        Optional<Season> byKey = Season.parse(text);
        if (byKey.isPresent()) return byKey;
        String wanted = plain(text);
        for (Season s : Season.values()) {
            if (plain(seasonName(s)).equals(wanted)) return Optional.of(s);
        }
        return Optional.empty();
    }

    static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }

    /** name from lang.yml without colours */
    private String seasonName(Season season) {
        return PlainTextComponentSerializer.plainText()
                .serialize(BukkitSeasonEvents.LEGACY.deserialize(plugin.config().lang().seasonDisplayName(season)));
    }

    /** world from the argument, otherwise the player's world; from the console the world is required */
    private Optional<World> world(CommandSender sender, String arg, String usage) {
        if (arg != null) {
            World w = Bukkit.getWorld(arg);
            if (w == null) error(sender, "World " + arg + " does not exist.");
            return Optional.ofNullable(w);
        }
        if (sender instanceof Player p) return Optional.of(p.getWorld());
        usage(sender, usage);
        return Optional.empty();
    }

    private static String arg(String[] args, int i) {
        return args.length > i ? args[i] : null;
    }

    private boolean noPermission(CommandSender sender) {
        lang(sender, plugin.config().lang().get("NO-PERMISSION-MESSAGE"));
        return true;
    }

    private static void lang(CommandSender sender, String text) {
        sender.sendMessage(BukkitSeasonEvents.LEGACY.deserialize(text));
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    private static void usage(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("mc215os.getinfo")) return List.of();
        return args.length == 1 ? matching(args[0], worldNames()) : List.of();
    }

    /** completion of the season administration under {@code /openseasons} */
    List<String> completeAdmin(String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "set" -> matching(args[1], Arrays.stream(Season.values()).map(Season::key).toList());
                case "setdate" -> List.of(plugin.config().calendar().americanDateFormat() ? "mm/dd/yyyy" : "dd/mm/yyyy");
                case "nextseason", "disable", "pausetime" -> matching(args[1], worldNames());
                default -> List.of();
            };
        }
        if (args.length == 3 && (sub.equals("set") || sub.equals("setdate"))) return matching(args[2], worldNames());
        return List.of();
    }

    private static List<String> worldNames() {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .map(World::getName)
                .toList();
    }

    private static List<String> matching(String token, List<String> options) {
        return StringUtil.copyPartialMatches(token, options, new ArrayList<>());
    }
}
