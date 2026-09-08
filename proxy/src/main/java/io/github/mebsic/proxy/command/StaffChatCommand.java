package io.github.mebsic.proxy.command;

import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.proxy.service.ChatRestrictionService;
import io.github.mebsic.proxy.service.StaffChatService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class StaffChatCommand implements SimpleCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final StaffChatService staffChat;
    private final ChatRestrictionService chatRestrictions;

    public StaffChatCommand(StaffChatService staffChat) {
        this(staffChat, null);
    }

    public StaffChatCommand(StaffChatService staffChat, ChatRestrictionService chatRestrictions) {
        this.staffChat = staffChat;
        this.chatRestrictions = chatRestrictions;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text(CommonMessages.ONLY_PLAYERS_COMMAND, NamedTextColor.RED));
            return;
        }
        Player player = (Player) invocation.source();
        if (staffChat == null || !staffChat.isStaff(player.getUniqueId())) {
            player.sendMessage(Component.text(CommonMessages.NO_PERMISSION_COMMAND, NamedTextColor.RED));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            player.sendMessage(Component.text("Invalid usage! Correct usage:", NamedTextColor.RED));
            player.sendMessage(Component.text("/staffchat <message>", NamedTextColor.RED));
            return;
        }
        if (sendActiveMuteFrame(player)) {
            return;
        }
        String message = joinArgs(args);
        staffChat.broadcastChat(player, message);
    }

    private String joinArgs(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private boolean sendActiveMuteFrame(Player player) {
        if (player == null || chatRestrictions == null) {
            return false;
        }
        String message = chatRestrictions.formatActiveMuteMessage(player.getUniqueId());
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        player.sendMessage(LEGACY.deserialize(message));
        return true;
    }
}
