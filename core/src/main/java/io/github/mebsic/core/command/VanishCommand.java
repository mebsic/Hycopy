package io.github.mebsic.core.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {
    private final CorePlugin plugin;

    public VanishCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }

        Player player = (Player) sender;
        ServerType serverType = plugin.getServerType();
        if (serverType == null || !serverType.isHub()) {
            player.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        if (args.length > 0) {
            player.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            player.sendMessage(ChatColor.RED + "/vanish");
            return true;
        }

        Profile profile = plugin.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return true;
        }
        Rank rank = profile.getRank() == null ? Rank.DEFAULT : profile.getRank();
        if (!canUseVanish(rank)) {
            player.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
            return true;
        }
        if (!plugin.isMongoEnabled() || plugin.getProfileStore() == null) {
            player.sendMessage(ChatColor.RED + "MongoDB is not enabled!");
            return true;
        }

        boolean vanished = !profile.isVanished();
        if (!plugin.setVanished(player.getUniqueId(), vanished)) {
            player.sendMessage(ChatColor.RED + "Your vanish state could not be updated! Please try again later.");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + (vanished ? "You vanished!" : "You reappeared!"));
        return true;
    }

    private boolean canUseVanish(Rank rank) {
        return rank == Rank.YOUTUBE || rank == Rank.STAFF;
    }
}
