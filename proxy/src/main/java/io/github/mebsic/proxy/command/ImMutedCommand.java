package io.github.mebsic.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.util.MojangApi;
import io.github.mebsic.proxy.service.BlockService;
import io.github.mebsic.proxy.service.ChatRestrictionService;
import io.github.mebsic.proxy.service.FriendService;
import io.github.mebsic.proxy.service.PrivateMessageReplyService;
import io.github.mebsic.proxy.service.RankResolver;
import io.github.mebsic.proxy.util.Components;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImMutedCommand implements SimpleCommand {
    private static final long NOTIFY_COOLDOWN_MILLIS = 5L * 60L * 1000L;
    private static final String MUTED_MESSAGE = "Hey! I'm currently muted and am unable to message right now.";

    private final ProxyServer proxy;
    private final FriendService friends;
    private final BlockService blocks;
    private final ChatRestrictionService chatRestrictions;
    private final RankResolver rankResolver;
    private final PrivateMessageReplyService replies;
    private final Map<String, Long> notifiedPairs;

    public ImMutedCommand(ProxyServer proxy,
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
        this.notifiedPairs = new ConcurrentHashMap<String, Long>();
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(Component.text(CommonMessages.ONLY_PLAYERS_COMMAND, NamedTextColor.RED));
            return;
        }
        Player sender = (Player) invocation.source();
        String[] args = invocation.arguments();
        if (args.length != 1) {
            sender.sendMessage(Component.text("Invalid usage! Use: /immuted <player>", NamedTextColor.RED));
            return;
        }
        if (chatRestrictions == null || !chatRestrictions.isMuted(sender.getUniqueId())) {
            sender.sendMessage(Component.text("You are not muted!", NamedTextColor.RED));
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
        String notificationKey = notificationKey(sender.getUniqueId(), targetId);
        long now = System.currentTimeMillis();
        Long lastNotifiedAt = notifiedPairs.get(notificationKey);
        if (lastNotifiedAt != null && now - lastNotifiedAt.longValue() < NOTIFY_COOLDOWN_MILLIS) {
            sender.sendMessage(Component.text("You have already tried notifying this player of your mute!", NamedTextColor.RED));
            return;
        }
        Optional<Player> target = proxy.getPlayer(targetId);
        if (!target.isPresent()) {
            sender.sendMessage(Component.text("That player is offline!", NamedTextColor.RED));
            return;
        }
        Player recipient = target.get();
        friends.rememberName(sender.getUniqueId(), sender.getUsername());
        friends.rememberName(recipient.getUniqueId(), recipient.getUsername());
        String senderDisplay = formatRankedName(sender.getUniqueId(), sender.getUsername());
        String recipientDisplay = formatRankedName(recipient.getUniqueId(), recipient.getUsername());
        sender.sendMessage(Components.friendPrivateMessage(true, recipientDisplay, MUTED_MESSAGE, "§e"));
        recipient.sendMessage(Components.friendPrivateMessage(false, senderDisplay, MUTED_MESSAGE, "§e"));
        notifiedPairs.put(notificationKey, now);
        if (replies != null) {
            replies.rememberConversation(sender.getUniqueId(), recipient.getUniqueId());
        }
    }

    private String notificationKey(UUID senderId, UUID targetId) {
        return senderId.toString() + ":" + targetId.toString();
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
}
