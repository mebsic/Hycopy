package io.github.mebsic.murdermystery.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.book.BookPromptService;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.murdermystery.book.MurderMysteryChanceBookPrompt;
import io.github.mebsic.murdermystery.util.MurderMysteryChanceMode;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChanceCommand implements CommandExecutor {
    private final CorePlugin corePlugin;

    public ChanceCommand(CorePlugin corePlugin) {
        this.corePlugin = corePlugin;
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
            player.sendMessage(ChatColor.RED + "/chance");
            return true;
        }
        if (corePlugin == null) {
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
        if (!MurderMysteryChanceMode.canUse(profile)) {
            player.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
            return true;
        }
        BookPromptService bookPromptService = corePlugin.getBookPromptService();
        if (bookPromptService == null) {
            player.sendMessage(ChatColor.RED + "Unable to open the chance book right now!");
            return true;
        }
        boolean desiredEnabled = !profile.isMurderMysteryTenTimesModeEnabled();
        MurderMysteryChanceBookPrompt prompt = new MurderMysteryChanceBookPrompt(player.getUniqueId(), desiredEnabled);
        if (!bookPromptService.openPrompt(player, prompt)) {
            player.sendMessage(ChatColor.RED + "Unable to open the chance book right now!");
        }
        return true;
    }
}
