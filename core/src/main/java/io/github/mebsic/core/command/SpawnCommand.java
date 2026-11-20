package io.github.mebsic.core.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.util.HubFlightUtil;
import io.github.mebsic.game.util.LocationUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class SpawnCommand implements TabExecutor {
    private final CorePlugin plugin;

    public SpawnCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        if (!plugin.getServerType().isHub()) {
            sender.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            sender.sendMessage(ChatColor.RED + "/spawn");
            return true;
        }

        Location spawn = LocationUtil.deserialize(plugin.getConfig().getString("hub.spawn", ""));
        Player player = (Player) sender;
        if (spawn == null || !player.teleport(spawn)) {
            return true;
        }
        Profile profile = plugin.getProfile(player.getUniqueId());
        if (profile != null && profile.isFlightEnabled()
                && HubFlightUtil.applyFlight(player, profile.getRank())) {
            HubFlightUtil.applyVelocityIfFlightEnabled(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
