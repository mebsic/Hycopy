package io.github.mebsic.core.listener;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.util.RankFormatUtil;
import io.github.mebsic.game.model.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFormatListener implements Listener {
    private static final ChatColor[] MURDER_MYSTERY_CHROMA_COLORS = new ChatColor[]{
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE
    };
    private static final MurderMysteryWinPrefixTier[] MURDER_MYSTERY_WIN_PREFIX_TIERS =
            new MurderMysteryWinPrefixTier[]{
                    new MurderMysteryWinPrefixTier(0, ChatColor.DARK_GRAY, "✪", false),
                    new MurderMysteryWinPrefixTier(100, ChatColor.GRAY, "Φ", false),
                    new MurderMysteryWinPrefixTier(250, ChatColor.WHITE, "∅", false),
                    new MurderMysteryWinPrefixTier(500, ChatColor.GOLD, "∅", false),
                    new MurderMysteryWinPrefixTier(750, ChatColor.YELLOW, "Σ", false),
                    new MurderMysteryWinPrefixTier(1_000, ChatColor.GREEN, "Σ", false),
                    new MurderMysteryWinPrefixTier(1_500, ChatColor.DARK_GREEN, "Ω", false),
                    new MurderMysteryWinPrefixTier(2_000, ChatColor.AQUA, "Ω", false),
                    new MurderMysteryWinPrefixTier(2_500, ChatColor.DARK_AQUA, "α", false),
                    new MurderMysteryWinPrefixTier(3_000, ChatColor.BLACK, "α", false),
                    new MurderMysteryWinPrefixTier(4_000, ChatColor.DARK_PURPLE, "≡", false),
                    new MurderMysteryWinPrefixTier(5_000, ChatColor.BLUE, "≡", false),
                    new MurderMysteryWinPrefixTier(7_500, ChatColor.LIGHT_PURPLE, "$", false),
                    new MurderMysteryWinPrefixTier(10_000, ChatColor.DARK_RED, "π", false),
                    new MurderMysteryWinPrefixTier(15_000, ChatColor.RED, "π", false),
                    new MurderMysteryWinPrefixTier(20_000, ChatColor.RED, "ƒ", true)
            };
    private static final Sound MENTION_DING_SOUND = Sound.NOTE_PLING;
    private static final int CASE_INSENSITIVE_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern GOOD_GAME_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])good\\s*game(?![A-Za-z0-9_])", CASE_INSENSITIVE_FLAGS);
    private static final Pattern GG_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])g\\s*g(?![A-Za-z0-9_])", CASE_INSENSITIVE_FLAGS);

    private final JavaPlugin plugin;
    private final CoreApi coreApi;
    private final CorePlugin corePlugin;

    public ChatFormatListener(JavaPlugin plugin, CoreApi coreApi) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.coreApi = Objects.requireNonNull(coreApi, "coreApi");
        this.corePlugin = plugin instanceof CorePlugin ? (CorePlugin) plugin : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rawMessage = event.getMessage() == null ? "" : event.getMessage();
        Set<Player> recipients = new HashSet<>(event.getRecipients());
        UUID uuid = player.getUniqueId();
        Rank rank = coreApi.getRank(uuid);
        ChatRenderStyle style;
        if (rank == null || rank == Rank.DEFAULT) {
            style = new ChatRenderStyle("", ChatColor.GRAY, ChatColor.GRAY, ChatColor.GRAY);
        } else {
            int networkLevel = coreApi.getNetworkLevel(uuid);
            Profile profile = coreApi.getProfile(uuid);
            String plusColor = profile == null ? null : profile.getPlusColor();
            String mvpPlusPlusPrefixColor = profile == null ? null : profile.getMvpPlusPlusPrefixColor();
            ChatColor nameColor = RankFormatUtil.baseColor(rank, mvpPlusPlusPrefixColor);
            String prefix = RankFormatUtil.buildPrefix(rank, networkLevel, plusColor, mvpPlusPlusPrefixColor);
            style = new ChatRenderStyle(prefix, nameColor, ChatColor.WHITE, ChatColor.WHITE);
        }
        boolean highlightGoodGame = shouldHighlightGoodGame(rank);
        event.setCancelled(true);

        Runnable dispatch = () -> deliverChat(player, rawMessage, recipients, style, highlightGoodGame);
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, dispatch);
        } else {
            dispatch.run();
        }
    }

    private void deliverChat(Player sender,
                             String message,
                             Set<Player> recipients,
                             ChatRenderStyle style,
                             boolean highlightGoodGame) {
        if (sender == null) {
            return;
        }
        String baseMessage = highlightGoodGame ? highlightGgPhrases(message, style.messageColor) : message;
        String header = buildMurderMysteryWinsPrefix(sender.getUniqueId())
                + style.prefix + style.nameColor + sender.getName() + style.separatorColor + ": " + style.messageColor;
        String normalLine = header + baseMessage;
        UUID senderId = sender.getUniqueId();
        boolean senderSent = false;
        for (Player recipient : recipients) {
            if (recipient == null || !recipient.isOnline()) {
                continue;
            }
            UUID recipientId = recipient.getUniqueId();
            if (recipientId.equals(senderId)) {
                recipient.sendMessage(normalLine);
                senderSent = true;
                continue;
            }
            if (corePlugin != null && corePlugin.isChatBlocked(senderId, recipientId)) {
                continue;
            }
            String highlighted = highlightExactIgnMentions(baseMessage, recipient.getName(), style.messageColor);
            recipient.sendMessage(header + highlighted);
            if (!baseMessage.equals(highlighted)) {
                recipient.playSound(recipient.getLocation(), MENTION_DING_SOUND, 1.0f, 1.0f);
            }
        }
        if (!senderSent && sender.isOnline()) {
            sender.sendMessage(normalLine);
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.stripColor(normalLine));
    }

    private String buildMurderMysteryWinsPrefix(UUID uuid) {
        if (uuid == null || corePlugin == null || !isMurderMysteryServer(corePlugin.getServerType())) {
            return "";
        }
        Profile profile = coreApi.getProfile(uuid);
        if (profile == null || !profile.isMurderMysteryWinsChatEnabled()) {
            return "";
        }
        int totalWins = Math.max(0, coreApi.getCounter(uuid, MongoManager.MURDER_MYSTERY_LIFETIME_WINS_KEY));
        if (totalWins == 0) {
            return ChatColor.DARK_GRAY + "✪ ";
        }
        MurderMysteryWinPrefixTier tier = resolveMurderMysteryWinPrefixTier(totalWins);
        String prefix = "[" + formatMurderMysteryWins(totalWins) + tier.symbol + "]";
        return colorMurderMysteryWinsPrefix(tier, prefix) + " ";
    }

    private String colorMurderMysteryWinsPrefix(MurderMysteryWinPrefixTier tier, String prefix) {
        if (tier.chroma) {
            return colorChroma(prefix);
        }
        return tier.color + prefix;
    }

    private MurderMysteryWinPrefixTier resolveMurderMysteryWinPrefixTier(int wins) {
        int safeWins = Math.max(0, wins);
        MurderMysteryWinPrefixTier resolved = MURDER_MYSTERY_WIN_PREFIX_TIERS[0];
        for (MurderMysteryWinPrefixTier tier : MURDER_MYSTERY_WIN_PREFIX_TIERS) {
            if (safeWins < tier.minimumWins) {
                break;
            }
            resolved = tier;
        }
        return resolved;
    }

    private String colorChroma(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        StringBuilder colored = new StringBuilder(prefix.length() * 3);
        for (int i = 0; i < prefix.length(); i++) {
            colored.append(MURDER_MYSTERY_CHROMA_COLORS[i % MURDER_MYSTERY_CHROMA_COLORS.length])
                    .append(prefix.charAt(i));
        }
        return colored.toString();
    }

    private String formatMurderMysteryWins(int wins) {
        int safeWins = Math.max(0, wins);
        if (safeWins < 1_000) {
            return Integer.toString(safeWins);
        }
        if (safeWins < 10_000) {
            int tenths = safeWins / 100;
            int whole = tenths / 10;
            int decimal = tenths % 10;
            if (decimal == 0) {
                return whole + "k";
            }
            return whole + "." + decimal + "k";
        }
        return (safeWins / 1_000) + "k";
    }

    private boolean isMurderMysteryServer(ServerType type) {
        return type == ServerType.MURDER_MYSTERY || type == ServerType.MURDER_MYSTERY_HUB;
    }

    private String highlightExactIgnMentions(String message, String ign, ChatColor restoreColor) {
        if (message == null || message.isEmpty() || ign == null || ign.isEmpty()) {
            return message == null ? "" : message;
        }
        StringBuilder highlighted = null;
        int copyFrom = 0;
        int searchFrom = 0;
        while (searchFrom <= message.length() - ign.length()) {
            int index = message.indexOf(ign, searchFrom);
            if (index < 0) {
                break;
            }
            int end = index + ign.length();
            if (isNameBoundary(message, index - 1) && isNameBoundary(message, end)) {
                if (highlighted == null) {
                    highlighted = new StringBuilder(message.length() + 16);
                }
                highlighted.append(message, copyFrom, index)
                        .append(ChatColor.YELLOW)
                        .append(ign)
                        .append(restoreColor);
                copyFrom = end;
                searchFrom = end;
            } else {
                searchFrom = index + 1;
            }
        }
        if (highlighted == null) {
            return message;
        }
        highlighted.append(message, copyFrom, message.length());
        return highlighted.toString();
    }

    private boolean shouldHighlightGoodGame(Rank rank) {
        if (rank == null || !rank.isAtLeast(Rank.MVP_PLUS_PLUS)) {
            return false;
        }
        if (corePlugin == null) {
            return false;
        }
        ServerType type = corePlugin.getServerType();
        if (type == null || !type.isGame()) {
            return false;
        }
        return corePlugin.getCurrentGameState() == GameState.ENDING;
    }

    private String highlightGgPhrases(String message, ChatColor restoreColor) {
        String highlighted = applyPatternHighlight(message, GOOD_GAME_PATTERN, restoreColor);
        return applyPatternHighlight(highlighted, GG_PATTERN, restoreColor);
    }

    private String applyPatternHighlight(String message, Pattern pattern, ChatColor restoreColor) {
        if (message == null || message.isEmpty() || pattern == null) {
            return message == null ? "" : message;
        }
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16);
        int cursor = 0;
        do {
            int start = matcher.start();
            int end = matcher.end();
            out.append(message, cursor, start)
                    .append(ChatColor.GOLD)
                    .append(message, start, end)
                    .append(restoreColor);
            cursor = end;
        } while (matcher.find());
        out.append(message, cursor, message.length());
        return out.toString();
    }

    private boolean isNameBoundary(String value, int index) {
        if (value == null || index < 0 || index >= value.length()) {
            return true;
        }
        char c = value.charAt(index);
        return !(Character.isLetterOrDigit(c) || c == '_');
    }

    private static final class ChatRenderStyle {
        private final String prefix;
        private final ChatColor nameColor;
        private final ChatColor separatorColor;
        private final ChatColor messageColor;

        private ChatRenderStyle(String prefix, ChatColor nameColor, ChatColor separatorColor, ChatColor messageColor) {
            this.prefix = prefix == null ? "" : prefix;
            this.nameColor = nameColor == null ? ChatColor.WHITE : nameColor;
            this.separatorColor = separatorColor == null ? ChatColor.WHITE : separatorColor;
            this.messageColor = messageColor == null ? ChatColor.WHITE : messageColor;
        }
    }

    private static final class MurderMysteryWinPrefixTier {
        private final int minimumWins;
        private final ChatColor color;
        private final String symbol;
        private final boolean chroma;

        private MurderMysteryWinPrefixTier(int minimumWins, ChatColor color, String symbol, boolean chroma) {
            this.minimumWins = Math.max(0, minimumWins);
            this.color = color == null ? ChatColor.GRAY : color;
            this.symbol = symbol == null || symbol.isEmpty() ? "Φ" : symbol;
            this.chroma = chroma;
        }
    }
}
