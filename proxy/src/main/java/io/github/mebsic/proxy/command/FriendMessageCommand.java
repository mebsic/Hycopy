package io.github.mebsic.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.util.ChatEmoteUtil;
import io.github.mebsic.core.util.MojangApi;
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

public class FriendMessageCommand implements SimpleCommand {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final ProxyServer proxy;
    private final FriendService friends;
    private final BlockService blocks;
    private final ChatRestrictionService chatRestrictions;
    private final RankResolver rankResolver;
    private final PrivateMessageReplyService replies;

    public FriendMessageCommand(ProxyServer proxy, FriendService friends, BlockService blocks, RankResolver rankResolver) {
        this(proxy, friends, blocks, null, rankResolver, null);
    }

    public FriendMessageCommand(ProxyServer proxy,
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
        if (args.length < 2) {
            sendInvalidUsage(sender);
            return;
        }
        if (chatRestrictions != null && chatRestrictions.isMuted(sender.getUniqueId())) {
            sendMuteMessage(sender);
            return;
        }
        String targetInput = args[0];
        UUID targetId = resolveUuid(targetInput);
        if (targetId == null) {
            sender.sendMessage(Component.text("No player found with name " + targetInput + "!", NamedTextColor.RED));
            return;
        }
        if (targetId.equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text("You cannot message yourself!", NamedTextColor.RED));
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
        if (rankResolver != null && rankResolver.isAppearOffline(targetId)) {
            sender.sendMessage(Component.text("That player is offline!", NamedTextColor.RED));
            return;
        }
        Optional<Player> target = proxy.getPlayer(targetId);
        if (!target.isPresent()) {
            sender.sendMessage(Component.text("That player is offline!", NamedTextColor.RED));
            return;
        }
        String message = renderMessage(sender.getUniqueId(), joinArgs(args, 1), "§7");
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

    private void sendInvalidUsage(Player sender) {
        sender.sendMessage(Component.text(
                "Invalid usage! Use: /msg <player> <message>",
                NamedTextColor.RED));
    }

    private UUID resolveUuid(String name) {
        if (name == null) {
            return null;
        }
        UUID online = proxy.getPlayer(name).map(Player::getUniqueId).orElse(null);
        if (online != null) {
            proxy.getPlayer(online).ifPresent(player -> friends.rememberName(online, player.getUsername()));
            return online;
        }
        UUID cached = friends.resolveByName(name);
        if (cached != null) {
            return cached;
        }
        UUID mojang = MojangApi.lookupUuid(name);
        if (mojang != null) {
            friends.rememberName(mojang, name);
        }
        return mojang;
    }

    private String formatRankedName(UUID uuid, String fallbackName) {
        if (rankResolver == null) {
            return fallbackName == null ? "" : fallbackName;
        }
        return rankResolver.formatNameWithRank(uuid, fallbackName);
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

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
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
