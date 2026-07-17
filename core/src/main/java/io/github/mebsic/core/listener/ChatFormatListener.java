package io.github.mebsic.core.listener;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.PrefixCosmeticDefinition;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.PrefixCosmeticCatalog;
import io.github.mebsic.core.util.RankFormatUtil;
import io.github.mebsic.game.model.GameState;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
        MurderMysteryWinsPrefix winsPrefix = buildMurderMysteryWinsPrefix(sender, style);
        String prefixText = winsPrefix == null ? "" : winsPrefix.visibleText;
        String header = prefixText
                + style.prefix + style.nameColor + sender.getName() + style.separatorColor + ": " + style.messageColor;
        String headerWithoutWinsPrefix = style.prefix + style.nameColor + sender.getName()
                + style.separatorColor + ": " + style.messageColor;
        String normalLine = header + baseMessage;
        UUID senderId = sender.getUniqueId();
        boolean senderSent = false;
        for (Player recipient : recipients) {
            if (recipient == null || !recipient.isOnline()) {
                continue;
            }
            UUID recipientId = recipient.getUniqueId();
            if (recipientId.equals(senderId)) {
                sendChatLine(recipient, winsPrefix, headerWithoutWinsPrefix + baseMessage);
                senderSent = true;
                continue;
            }
            if (corePlugin != null && corePlugin.isChatBlocked(senderId, recipientId)) {
                continue;
            }
            String highlighted = highlightExactIgnMentions(baseMessage, recipient.getName(), style.messageColor);
            sendChatLine(recipient, winsPrefix, headerWithoutWinsPrefix + highlighted);
            if (!baseMessage.equals(highlighted)) {
                recipient.playSound(recipient.getLocation(), MENTION_DING_SOUND, 1.0f, 1.0f);
            }
        }
        if (!senderSent && sender.isOnline()) {
            sendChatLine(sender, winsPrefix, headerWithoutWinsPrefix + baseMessage);
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.stripColor(normalLine));
    }

    private MurderMysteryWinsPrefix buildMurderMysteryWinsPrefix(Player sender, ChatRenderStyle style) {
        if (sender == null || corePlugin == null || !isMurderMysteryServer(corePlugin.getServerType())) {
            return null;
        }
        UUID uuid = sender.getUniqueId();
        Profile profile = coreApi.getProfile(uuid);
        if (profile == null) {
            return null;
        }
        int totalWins = Math.max(0, coreApi.getCounter(uuid, MongoManager.MURDER_MYSTERY_LIFETIME_WINS_KEY));
        PrefixCosmeticDefinition icon = resolveSelectedPrefixCosmetic(profile, CosmeticType.PREFIX_ICON);
        PrefixCosmeticDefinition scheme = resolveSelectedPrefixCosmetic(profile, CosmeticType.PREFIX_SCHEME);
        String symbol = icon == null || icon.getSymbol().isEmpty() ? "✪" : icon.getSymbol();
        boolean plainStarPrefix = totalWins == 0
                || PrefixCosmeticCatalog.isNoneSchemeId(scheme == null ? null : scheme.getId());
        String actualPrefix = plainStarPrefix
                ? ChatColor.GRAY + "✪"
                : colorMurderMysteryWinsPrefix(scheme, "[" + formatMurderMysteryWins(totalWins) + symbol + "]");
        String visiblePrefix = profile.isMurderMysteryWinsChatEnabled()
                ? actualPrefix
                : ChatColor.GRAY + "✪";
        String hoverText = buildMurderMysteryWinsHoverText(sender, style, totalWins);
        return new MurderMysteryWinsPrefix(visiblePrefix + " ", buildHover(hoverText));
    }

    private String buildMurderMysteryWinsHoverText(Player sender, ChatRenderStyle style, int totalWins) {
        String safeName = sender == null ? "Unknown" : sender.getName();
        ChatRenderStyle safeStyle = style == null
                ? new ChatRenderStyle("", ChatColor.GRAY, ChatColor.GRAY, ChatColor.GRAY)
                : style;
        return safeStyle.prefix + safeStyle.nameColor + safeName
                + "\n" + ChatColor.GRAY + "Classic Wins: " + ChatColor.GREEN + formatWholeNumber(totalWins);
    }

    private HoverEvent buildHover(String text) {
        BaseComponent[] hover = TextComponent.fromLegacyText(text == null ? "" : text);
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
    }

    private void sendChatLine(Player recipient, MurderMysteryWinsPrefix winsPrefix, String restOfLine) {
        if (recipient == null) {
            return;
        }
        if (winsPrefix == null) {
            recipient.sendMessage(restOfLine == null ? "" : restOfLine);
            return;
        }
        TextComponent root = new TextComponent("");
        addColoredText(root, winsPrefix.visibleText, winsPrefix.hover);
        addColoredText(root, restOfLine, null);
        recipient.spigot().sendMessage(root);
    }

    private void addColoredText(TextComponent root, String text, HoverEvent hover) {
        if (root == null || text == null || text.isEmpty()) {
            return;
        }
        BaseComponent[] converted = TextComponent.fromLegacyText(text);
        if (converted == null || converted.length == 0) {
            return;
        }
        for (BaseComponent part : converted) {
            if (part == null) {
                continue;
            }
            if (hover != null) {
                part.setHoverEvent(hover);
            }
            root.addExtra(part);
        }
    }

    private String colorMurderMysteryWinsPrefix(PrefixCosmeticDefinition scheme, String prefix) {
        if (scheme != null && scheme.isChroma()) {
            return colorChroma(prefix);
        }
        return parsePrefixColor(scheme == null ? null : scheme.getColor()) + prefix;
    }

    private PrefixCosmeticDefinition resolveSelectedPrefixCosmetic(Profile profile, CosmeticType type) {
        if (profile == null || !PrefixCosmeticCatalog.isPrefixType(type)) {
            return PrefixCosmeticCatalog.definition(type, PrefixCosmeticCatalog.defaultId(type));
        }
        String selected = PrefixCosmeticCatalog.normalizeId(profile.getSelected().get(type));
        if (PrefixCosmeticCatalog.RANDOM_ID.equals(selected)) {
            return pickRandomPrefixCosmetic(profile, type, false);
        }
        if (PrefixCosmeticCatalog.RANDOM_FAVORITE_ID.equals(selected)) {
            return pickRandomPrefixCosmetic(profile, type, true);
        }
        PrefixCosmeticDefinition definition = PrefixCosmeticCatalog.definition(type, selected);
        if (definition != null && hasUnlockedPrefixCosmetic(profile, type, definition.getId())) {
            return definition;
        }
        return PrefixCosmeticCatalog.definition(type, PrefixCosmeticCatalog.defaultId(type));
    }

    private PrefixCosmeticDefinition pickRandomPrefixCosmetic(Profile profile, CosmeticType type, boolean favoritesOnly) {
        List<PrefixCosmeticDefinition> candidates = new ArrayList<PrefixCosmeticDefinition>();
        Set<String> source = favoritesOnly ? profile.getFavorites().get(type) : profile.getUnlocked().get(type);
        if (source != null) {
            for (String id : source) {
                if (type == CosmeticType.PREFIX_SCHEME && PrefixCosmeticCatalog.isNoneSchemeId(id)) {
                    continue;
                }
                PrefixCosmeticDefinition definition = PrefixCosmeticCatalog.definition(type, id);
                if (definition != null && hasUnlockedPrefixCosmetic(profile, type, definition.getId())) {
                    candidates.add(definition);
                }
            }
        }
        if (candidates.isEmpty() && favoritesOnly) {
            return pickRandomPrefixCosmetic(profile, type, false);
        }
        if (candidates.isEmpty()) {
            return PrefixCosmeticCatalog.definition(type, PrefixCosmeticCatalog.defaultId(type));
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean hasUnlockedPrefixCosmetic(Profile profile, CosmeticType type, String id) {
        if (profile == null || !PrefixCosmeticCatalog.isPrefixType(type)) {
            return false;
        }
        String normalized = PrefixCosmeticCatalog.normalizeId(id);
        if (normalized.isEmpty()) {
            return false;
        }
        Set<String> unlocked = profile.getUnlocked().get(type);
        if (unlocked == null || unlocked.isEmpty()) {
            return false;
        }
        for (String unlockedId : unlocked) {
            if (normalized.equals(PrefixCosmeticCatalog.normalizeId(unlockedId))) {
                return true;
            }
        }
        return false;
    }

    private ChatColor parsePrefixColor(String colorName) {
        if (colorName != null && !colorName.trim().isEmpty()) {
            try {
                return ChatColor.valueOf(colorName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ChatColor.GRAY;
            }
        }
        return ChatColor.GRAY;
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

    private String formatWholeNumber(int value) {
        return String.format(Locale.US, "%,d", Math.max(0, value));
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

    private static final class MurderMysteryWinsPrefix {
        private final String visibleText;
        private final HoverEvent hover;

        private MurderMysteryWinsPrefix(String visibleText, HoverEvent hover) {
            this.visibleText = visibleText == null ? "" : visibleText;
            this.hover = hover;
        }
    }

}
