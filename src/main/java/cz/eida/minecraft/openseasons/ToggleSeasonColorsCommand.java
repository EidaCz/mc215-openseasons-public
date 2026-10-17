/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons;

import cz.eida.minecraft.openseasons.config.LangConfig;
import cz.eida.minecraft.openseasons.network.ClientColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /toggleseasoncolors}: a player turns the landscape colours off or on for themselves. Permission
 * {@code mc215os.toggleseasons}; message {@code TOGGLESEASONSCOMMAND-MESSAGE} with the state
 * {@code ENABLED}/{@code DISABLED}.
 */
final class ToggleSeasonColorsCommand implements TabExecutor {

    private final OpenSeasonsPlugin plugin;

    ToggleSeasonColorsCommand(OpenSeasonsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        LangConfig lang = plugin.config().lang();
        if (!sender.isOp() && !sender.hasPermission("mc215os.toggleseasons")) {
            sender.sendMessage(BukkitSeasonEvents.LEGACY.deserialize(lang.get("NO-PERMISSION-MESSAGE")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        ClientColors colors = plugin.clientColors();
        if (colors == null) {
            sender.sendMessage(Component.text("Landscape colours are not running (the packetevents plugin is missing).", NamedTextColor.RED));
            return true;
        }
        boolean enabled = colors.toggle(player);
        plugin.savePlayerSettings();
        sender.sendMessage(BukkitSeasonEvents.LEGACY.deserialize(lang.get("TOGGLESEASONSCOMMAND-MESSAGE")
                .replace("$status$", lang.get(enabled ? "ENABLED" : "DISABLED"))));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        return List.of();
    }
}
