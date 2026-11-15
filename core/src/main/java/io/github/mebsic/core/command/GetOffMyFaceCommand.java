package io.github.mebsic.core.command;

import io.github.mebsic.core.listener.HubItemListener;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class GetOffMyFaceCommand implements TabExecutor {
    private final ServerType serverType;
    private final HubItemListener hubItemListener;

    public GetOffMyFaceCommand(ServerType serverType, HubItemListener hubItemListener) {
        this.serverType = serverType;
        this.hubItemListener = hubItemListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || serverType == null) {
            return true;
        }
        if (serverType.isGame()) {
            sender.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        if (!serverType.isHub()) {
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            sender.sendMessage(ChatColor.RED + "/getoffmyface");
            return true;
        }
        hubItemListener.ejectCowboyRiders((Player) sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
