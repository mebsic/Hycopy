package io.github.mebsic.core.command;

import io.github.mebsic.core.util.ChatEmoteUtil;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EmotesCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        Player player = (Player) sender;
        player.sendMessage(ChatColor.GREEN + "Available to "
                + ChatColor.GOLD + "MVP"
                + ChatColor.RED + "++"
                + ChatColor.GREEN + ":");
        for (ChatEmoteUtil.ChatEmote emote : ChatEmoteUtil.mvpPlusPlusEmotes()) {
            sendEmoteLine(player, emote);
        }
        player.sendMessage(ChatColor.GREEN + "Available through Rank Gifting:");
        for (ChatEmoteUtil.ChatEmote emote : ChatEmoteUtil.rankGiftingEmotes()) {
            sendEmoteLine(player, emote);
        }
        return true;
    }

    private void sendEmoteLine(Player player, ChatEmoteUtil.ChatEmote emote) {
        if (player == null || emote == null) {
            return;
        }
        player.sendMessage(ChatColor.GOLD + emote.getToken()
                + ChatColor.WHITE + " - "
                + emote.getDisplay());
    }
}
