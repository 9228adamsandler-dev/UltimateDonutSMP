package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.SpawnHologramManager;
import com.bx.ultimateDonutSmp.managers.SpawnHologramManager.HologramType;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SetHologramCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "ultimatedonutsmp.command.sethologram";
    private static final String SETUP_PERMISSION = "ultimatedonutsmp.admin.setup";

    private final UltimateDonutSmp plugin;

    public SetHologramCommand(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set hologram locations.");
            return true;
        }

        if (!PermissionUtils.has(player, PERMISSION) && !PermissionUtils.has(player, SETUP_PERMISSION)) {
            player.sendMessage(ColorUtils.toComponent("&cYou do not have permission to set holograms."));
            return true;
        }

        SpawnHologramManager manager = plugin.getSpawnHologramManager();
        if (manager == null) {
            player.sendMessage(ColorUtils.toComponent("&cHologram system is not available."));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " <welcome|store|duels|baltop|clear> [type]"));
            player.sendMessage(ColorUtils.toComponent("&7Stand where you want the top of the hologram and run the command."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("clear") || sub.equals("remove")) {
            if (args.length < 2) {
                player.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " clear <welcome|store|duels|baltop>"));
                return true;
            }
            HologramType type = HologramType.fromString(args[1]);
            if (type == null) {
                player.sendMessage(ColorUtils.toComponent("&cUnknown type. Use: welcome, store, duels, baltop"));
                return true;
            }
            manager.clearLocation(type);
            player.sendMessage(ColorUtils.toComponent("&aCleared &f" + type.name().toLowerCase(Locale.ROOT) + " &ahologram."));
            return true;
        }

        HologramType type = HologramType.fromString(sub);
        if (type == null) {
            player.sendMessage(ColorUtils.toComponent("&cUnknown type. Use: welcome, store, duels, baltop"));
            return true;
        }

        // Place slightly above the player's eye for readability
        var location = player.getLocation().clone().add(0, 1.6, 0);
        if (manager.setLocation(type, location)) {
            player.sendMessage(ColorUtils.toComponent("&aSet &f" + type.name().toLowerCase(Locale.ROOT)
                    + " &ahologram at your location."));
        } else {
            player.sendMessage(ColorUtils.toComponent("&cFailed to set hologram."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("welcome", "store", "duels", "baltop", "clear"));
            String partial = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            List<String> options = Arrays.asList("welcome", "store", "duels", "baltop");
            String partial = args[1].toLowerCase(Locale.ROOT);
            return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        return List.of();
    }
}
