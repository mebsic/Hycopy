package io.github.mebsic.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatEmoteUtil {
    private static final String GRAY = "§7";
    private static final String DARK_GRAY = "§8";
    private static final String GOLD = "§6";
    private static final String YELLOW = "§e";
    private static final String GREEN = "§a";
    private static final String AQUA = "§b";
    private static final String DARK_AQUA = "§3";
    private static final String RED = "§c";
    private static final String LIGHT_PURPLE = "§d";
    private static final String DARK_PURPLE = "§5";
    private static final String BLUE = "§9";
    private static final String WHITE = "§f";
    private static final String BOLD = "§l";
    private static final String RESET = "§r";

    private static final List<ChatEmote> MVP_PLUS_PLUS_EMOTES = buildMvpPlusPlusEmotes();
    private static final List<ChatEmote> RANK_GIFTING_EMOTES = buildRankGiftingEmotes();

    private ChatEmoteUtil() {
    }

    public static List<ChatEmote> mvpPlusPlusEmotes() {
        return MVP_PLUS_PLUS_EMOTES;
    }

    public static List<ChatEmote> rankGiftingEmotes() {
        return RANK_GIFTING_EMOTES;
    }

    public static String replaceEmotes(String message,
                                       boolean canUseMvpPlusPlusEmotes,
                                       boolean canUseRankGiftingEmotes,
                                       String restoreColor) {
        if (message == null || message.isEmpty()) {
            return message == null ? "" : message;
        }
        String rendered = message;
        String restore = restoreColor == null ? "" : restoreColor;
        if (canUseMvpPlusPlusEmotes) {
            rendered = replaceEmotes(rendered, MVP_PLUS_PLUS_EMOTES, restore);
        }
        if (canUseRankGiftingEmotes) {
            rendered = replaceEmotes(rendered, RANK_GIFTING_EMOTES, restore);
        }
        return rendered;
    }

    private static String replaceEmotes(String message, List<ChatEmote> emotes, String restoreColor) {
        String rendered = message;
        for (ChatEmote emote : emotes) {
            rendered = rendered.replace(emote.getToken(), emote.getDisplay() + RESET + restoreColor);
        }
        return rendered;
    }

    private static List<ChatEmote> buildMvpPlusPlusEmotes() {
        List<ChatEmote> emotes = new ArrayList<ChatEmote>();
        emotes.add(new ChatEmote("<3", RED + "❤"));
        emotes.add(new ChatEmote(":star:", GOLD + "✮"));
        emotes.add(new ChatEmote(":yes:", GREEN + "✔"));
        emotes.add(new ChatEmote(":no:", RED + "✖"));
        emotes.add(new ChatEmote(":java:", AQUA + "☕"));
        emotes.add(new ChatEmote(":arrow:", YELLOW + "➜"));
        emotes.add(new ChatEmote(":shrug:", YELLOW + "¯\\_(ツ)_/¯"));
        emotes.add(new ChatEmote(":tableflip:", RED + "(╯°□°）╯" + WHITE + "︵ " + GRAY + "┻━┻"));
        emotes.add(new ChatEmote("o/", LIGHT_PURPLE + "(ﾟ◡ﾟ)/"));
        emotes.add(new ChatEmote(":123:", GREEN + "1" + YELLOW + "2" + RED + "3"));
        emotes.add(new ChatEmote(":totem:", AQUA + "◎_◎"));
        emotes.add(new ChatEmote(":typing:", YELLOW + "✎" + WHITE + "..."));
        emotes.add(new ChatEmote(":maths:", GREEN + "√" + YELLOW + BOLD + "(" + GREEN + "π" + YELLOW + BOLD + "+" + GREEN + BOLD + "x" + YELLOW + BOLD + ")=" + RED + BOLD + "L"));
        emotes.add(new ChatEmote(":snail:", YELLOW + "@" + GREEN + "'" + YELLOW + "-" + GREEN + "'"));
        emotes.add(new ChatEmote(":thinking:", GOLD + "(" + GREEN + "0" + GOLD + "." + RED + "o" + GOLD + "?)"));
        emotes.add(new ChatEmote(":gimme:", AQUA + "༼つ◕_◕༽つ"));
        emotes.add(new ChatEmote(":wizard:", YELLOW + "(" + AQUA + "'" + YELLOW + "-" + AQUA + "'" + YELLOW + ")⊃" + RED + "━" + LIGHT_PURPLE + "☆ﾟ.*･｡ﾟ"));
        emotes.add(new ChatEmote(":pvp:", YELLOW + "⚔"));
        emotes.add(new ChatEmote(":peace:", GREEN + "✌"));
        emotes.add(new ChatEmote(":oof:", RED + BOLD + "OOF"));
        emotes.add(new ChatEmote(":puffer:", YELLOW + BOLD + "<('O')>"));
        return Collections.unmodifiableList(emotes);
    }

    private static List<ChatEmote> buildRankGiftingEmotes() {
        List<ChatEmote> emotes = new ArrayList<ChatEmote>();
        emotes.add(new ChatEmote(":sloth:", GOLD + "( " + DARK_GRAY + "⬩" + GOLD + " ⊝ " + DARK_GRAY + "⬩" + GOLD + " )"));
        emotes.add(new ChatEmote(":dj:", BLUE + "ヽ" + DARK_PURPLE + "(" + LIGHT_PURPLE + "⌐" + RED + "■" + GOLD + "_" + YELLOW + "■" + AQUA + ")" + DARK_AQUA + "ノ" + BLUE + "♫"));
        emotes.add(new ChatEmote("^_^", GREEN + "^_^"));
        emotes.add(new ChatEmote("^-^", GREEN + "^-^"));
        emotes.add(new ChatEmote(":dab:", LIGHT_PURPLE + "<" + YELLOW + "o" + LIGHT_PURPLE + "/"));
        emotes.add(new ChatEmote(":yey:", GREEN + "ヽ (◕◡◕) ﾉ"));
        emotes.add(new ChatEmote(":snow:", AQUA + "☃"));
        emotes.add(new ChatEmote(":cute:", YELLOW + "(" + GREEN + "✿" + YELLOW + "ᴖ‿ᴖ)"));
        emotes.add(new ChatEmote(":cat:", YELLOW + "= " + AQUA + "＾● ⋏ ●＾" + YELLOW + " ="));
        emotes.add(new ChatEmote(":dog:", GOLD + "(ᵔᴥᵔ)"));
        emotes.add(new ChatEmote("h/", YELLOW + "ヽ(^◇^*)/"));
        return Collections.unmodifiableList(emotes);
    }

    public static final class ChatEmote {
        private final String token;
        private final String display;

        private ChatEmote(String token, String display) {
            this.token = token == null ? "" : token;
            this.display = display == null ? "" : display;
        }

        public String getToken() {
            return token;
        }

        public String getDisplay() {
            return display;
        }
    }
}
