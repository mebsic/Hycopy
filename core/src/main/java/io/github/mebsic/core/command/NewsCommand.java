package io.github.mebsic.core.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.service.NewsEditService;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NewsCommand implements CommandExecutor {
    private final CorePlugin plugin;
    private final NewsEditService newsEditService;

    public NewsCommand(CorePlugin plugin, NewsEditService newsEditService) {
        this.plugin = plugin;
        this.newsEditService = newsEditService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        Player player = (Player) sender;
        if (args.length > 0) {
            player.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            player.sendMessage(ChatColor.RED + "/news");
            return true;
        }
        if (plugin.getServerType() == null || !plugin.getServerType().isHub()) {
            player.sendMessage(ChatColor.RED + "That command is only available in a lobby!");
            return true;
        }

        Profile profile = plugin.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return true;
        }
        Rank rank = profile.getRank() == null ? Rank.DEFAULT : profile.getRank();
        if (!rank.isAtLeast(Rank.STAFF)) {
            player.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
            return true;
        }

        if (newsEditService == null || !newsEditService.isAvailable()) {
            player.sendMessage(ChatColor.RED + "News storage is unavailable right now!");
            return true;
        }
        newsEditService.openMainMenu(player);
        return true;
    }
}
