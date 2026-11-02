package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.SpawnManager;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Explicitly sets the shard AFK area / AFK location.
 * Does not touch the main spawn location or spawn cuboid.
 */
public class SetAfkAreaCommand implements CommandExecutor {

    private static final String PERMISSION = "ultimatedonutsmp.command.setafkarea";
    private static final String SETUP_PERMISSION = "ultimatedonutsmp.admin.setup";

    private final UltimateDonutSmp plugin;

    public SetAfkAreaCommand(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can save AFK area locations.");
            return true;
        }

        if (!PermissionUtils.has(player, PERMISSION) && !PermissionUtils.has(player, SETUP_PERMISSION)) {
            player.sendMessage(ColorUtils.toComponent("&cYou do not have permission to set the AFK area."));
            return true;
        }

        Location location = player.getLocation();
        SpawnManager.SetupLocationResult result = plugin.getSpawnManager().setAfkLocation(location);
        if (!result.success()) {
            player.sendMessage(ColorUtils.toComponent("&cAFK area could not be saved: &f" + result.message()));
            return true;
        }

        player.sendMessage(ColorUtils.toComponent("&aAFK area (shard) location saved. &7(World: &f"
                + (location.getWorld() == null ? "unknown" : location.getWorld().getName())
                + "&7, X: &f" + String.format("%.1f", location.getX())
                + "&7, Y: &f" + String.format("%.1f", location.getY())
                + "&7, Z: &f" + String.format("%.1f", location.getZ()) + "&7)"));
        return true;
    }
}
