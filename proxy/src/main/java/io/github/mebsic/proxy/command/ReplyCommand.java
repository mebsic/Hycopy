package io.github.mebsic.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.mebsic.core.util.ChatEmoteUtil;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.proxy.service.BlockService;
import io.github.mebsic.proxy.service.ChatRestrictionService;
import io.github.mebsic.proxy.service.FriendService;
import io.github.mebsic.proxy.service.PrivateMessageReplyService;
import io.github.mebsic.proxy.service.RankResolver;
import io.github.mebsic.proxy.util.Components;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.UUID;

public class ReplyCommand implements SimpleCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ProxyServer proxy;
    private final FriendService friends;
    private final BlockService blocks;
    private final ChatRestrictionService chatRestrictions;
    private final RankResolver rankResolver;
    private final PrivateMessageReplyService replies;

    public ReplyCommand(ProxyServer proxy,
                        FriendService friends,
                        BlockService blocks,
                        ChatRestrictionService chatRestrictions,
                        RankResolver rankResolver,
                        PrivateMessageReplyService replies) {
        this.proxy = proxy;
        this.friends = friends;
        this.blocks = blocks;
        this.chatRestrictions = chatRestrictions;
        this.rankResolver = rankResolver;
        this.replies = replies;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text(CommonMessages.ONLY_PLAYERS_COMMAND, NamedTextColor.RED));
            return;
        }
        Player sender = (Player) invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sender.sendMessage(Component.text("Invalid usage! Use: /reply <message>", NamedTextColor.RED));
            return;
        }
        if (chatRestrictions != null && chatRestrictions.isMuted(sender.getUniqueId())) {
            sendMuteMessage(sender);
            return;
        }
        UUID targetId = replies == null ? null : replies.getReplyTarget(sender.getUniqueId());
        if (targetId == null) {
            sender.sendMessage(Component.text("Nobody has messaged you in the past 5 minutes!", NamedTextColor.RED));
            return;
        }
        if (!friends.areFriends(sender.getUniqueId(), targetId)) {
            sender.sendMessage(Component.text("You can only message players on your friends list!", NamedTextColor.RED));
            return;
        }
        if (blocks != null && blocks.isEitherBlocked(sender.getUniqueId(), targetId)) {
            sender.sendMessage(Component.text("You cannot message this player!", NamedTextColor.RED));
            return;
        }
        Optional<Player> target = proxy.getPlayer(targetId);
        if (!target.isPresent()) {
            sender.sendMessage(Component.text("That player is offline!", NamedTextColor.RED));
            return;
        }
        String message = renderMessage(sender.getUniqueId(), joinArgs(args), "§7");
        Player recipient = target.get();
        friends.rememberName(sender.getUniqueId(), sender.getUsername());
        friends.rememberName(recipient.getUniqueId(), recipient.getUsername());
        String senderDisplay = formatRankedName(sender.getUniqueId(), sender.getUsername());
        String recipientDisplay = formatRankedName(recipient.getUniqueId(), recipient.getUsername());
        sender.sendMessage(Components.friendPrivateMessage(true, recipientDisplay, message));
        recipient.sendMessage(Components.friendPrivateMessage(false, senderDisplay, message));
        if (replies != null) {
            replies.rememberConversation(sender.getUniqueId(), recipient.getUniqueId());
        }
    }

    private String renderMessage(UUID senderId, String message, String restoreColor) {
        if (senderId == null || rankResolver == null) {
            return message == null ? "" : message;
        }
        boolean canUseMvpPlusPlusEmotes = rankResolver.hasAtLeast(senderId, "MVP_PLUS_PLUS");
        boolean canUseRankGiftingEmotes = rankResolver.hasGiftedRank(senderId);
        if (!canUseMvpPlusPlusEmotes && !canUseRankGiftingEmotes) {
            return message == null ? "" : message;
        }
        return ChatEmoteUtil.replaceEmotes(
                message,
                canUseMvpPlusPlusEmotes,
                canUseRankGiftingEmotes,
                restoreColor
        );
    }

    private String formatRankedName(UUID uuid, String fallbackName) {
        if (rankResolver == null) {
            return fallbackName == null ? "" : fallbackName;
        }
        return rankResolver.formatNameWithRank(uuid, fallbackName);
    }

    private String joinArgs(String[] args) {
        StringBuilder builder = new StringBuilder();
        for (String arg : args) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(arg);
        }
        return builder.toString();
    }

    private void sendMuteMessage(Player player) {
        if (player == null) {
            return;
        }
        String message = chatRestrictions == null ? null : chatRestrictions.formatActiveMuteMessage(player.getUniqueId());
        if (message == null || message.trim().isEmpty()) {
            player.sendMessage(Component.text("You are currently muted!", NamedTextColor.RED));
            return;
        }
        player.sendMessage(LEGACY.deserialize(message));
    }
}
