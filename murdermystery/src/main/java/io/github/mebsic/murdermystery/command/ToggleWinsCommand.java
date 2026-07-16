package io.github.mebsic.murdermystery.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.murdermystery.stats.MurderMysteryStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleWinsCommand implements CommandExecutor {
    private final CorePlugin corePlugin;

    public ToggleWinsCommand(CorePlugin corePlugin) {
        this.corePlugin = corePlugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        Player player = (Player) sender;
        if (corePlugin == null) {
            return true;
        }
        if (args.length > 0) {
            player.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            player.sendMessage(ChatColor.RED + "/togglewins");
            return true;
        }
        ServerType serverType = corePlugin.getServerType();
        if (serverType == null || !serverType.isHub()) {
            player.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        Profile profile = corePlugin.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return true;
        }

        boolean nextEnabled = !MurderMysteryStats.areWinsInChatEnabled(profile);
        boolean changed = MurderMysteryStats.setWinsInChatEnabled(profile, nextEnabled);
        if (changed) {
            corePlugin.saveProfile(profile);
        }

        if (nextEnabled) {
            player.sendMessage(ChatColor.GREEN + "Enabled wins in chat!");
        } else {
            player.sendMessage(ChatColor.RED + "Disabled wins in chat!");
        }
        return true;
    }
}
