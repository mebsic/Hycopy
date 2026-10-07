package io.github.mebsic.core.command;

import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.util.NetworkConstants;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class HelpCommand implements CommandExecutor {
    private static final String CLICK_TO_SELECT = "Click to select!";
    private static final String GO_BACK = "Go back";

    private static final String FORUMS_PATH = "/forums/5/";
    private static final String MINIGAMES_PATH = "/forums/#games.67";
    private static final String BUG_REPORT_PATH = "/bug-reports/create";
    private static final String REPORT_INFO_PATH = "/hc/en-us/articles/360019646359-How-To-Report-Rule-Breakers";
    private static final String SUPPORT_PATH = "/hc/en-us";
    private static final String ALLOWED_MODS_PATH = "/hc/en-us/articles/6472550754962";
    private static final String RULES_PATH = "/rules";
    private static final String GENERAL_GAMEPLAY_PATH = "/hc/en-us/categories/360003005440-Hycopy-Guides";
    private static final String RANK_INFO_PATH = "/hc/en-us/articles/360019646559-Hycopy-Ranks-and-How-to-Obtain-Them";
    private static final String CREATOR_PROGRAM_PATH = "/hc/en-us/categories/360003024319-Creators";
    private static final String DISCORD_LINK_PATH = "/hc/en-us/articles/360019646539-How-to-join-the-Hycopy-Discord";
    private static final String FORUM_LINK_PATH = "/hc/en-us/articles/360019647059-Linking-Your-Minecraft-Account-to-Copy-net";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            sendMainMenu(player);
            return true;
        }
        String page = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
        if ("report".equals(page) || "rulebreaker".equals(page) || "rules".equals(page)) {
            sendReportRuleBreakerMenu(player);
            return true;
        }
        if ("general".equals(page) || "gameplay".equals(page) || "server".equals(page)) {
            sendGeneralGameplayMenu(player);
            return true;
        }
        if ("linking".equals(page) || "link".equals(page) || "account".equals(page)) {
            sendLinkingAccountMenu(player);
            return true;
        }
        sendMainMenu(player);
        return true;
    }

    private void sendMainMenu(Player player) {
        sendHeader(player);
        player.sendMessage(ChatColor.YELLOW + "Click to select a help option...");
        player.sendMessage(" ");
        sendOption(player, "Hycopy Minigames", ClickEvent.Action.OPEN_URL, websiteUrl(MINIGAMES_PATH));
        sendOption(player, "Found a Server Bug/Issue", ClickEvent.Action.OPEN_URL, websiteUrl(BUG_REPORT_PATH));
        sendOption(player, "Report a Rule Breaker", ClickEvent.Action.RUN_COMMAND, "/help report");
        sendOption(player, "Store", ClickEvent.Action.OPEN_URL, NetworkConstants.storeUrl());
        sendOption(player, "Support", ClickEvent.Action.OPEN_URL, websiteUrl(SUPPORT_PATH));
        sendOption(player, "Allowed Modifications", ClickEvent.Action.OPEN_URL, websiteUrl(ALLOWED_MODS_PATH));
        sendOption(player, "Hycopy Rules & Policies", ClickEvent.Action.OPEN_URL, websiteUrl(RULES_PATH));
        sendOption(player, "General Gameplay/Server", ClickEvent.Action.RUN_COMMAND, "/help general");
        sendFooter(player);
    }

    private void sendReportRuleBreakerMenu(Player player) {
        sendHeader(player);
        sendBackLine(player, "Report a Rule Breaker", "/help");
        player.sendMessage(" ");
        sendOption(player, "Report a player", ClickEvent.Action.SUGGEST_COMMAND, "/report <name>");
        sendOption(player, "Further information here", ClickEvent.Action.OPEN_URL, websiteUrl(REPORT_INFO_PATH));
        sendFooter(player);
    }

    private void sendGeneralGameplayMenu(Player player) {
        sendHeader(player);
        sendBackLine(player, "General Gameplay/Server", "/help");
        player.sendMessage(" ");
        sendOption(player, "General Gameplay", ClickEvent.Action.OPEN_URL, websiteUrl(GENERAL_GAMEPLAY_PATH));
        sendOption(player, "Rank Information", ClickEvent.Action.OPEN_URL, websiteUrl(RANK_INFO_PATH));
        sendOption(player, "Creator Program", ClickEvent.Action.OPEN_URL, websiteUrl(CREATOR_PROGRAM_PATH));
        sendOption(player, "Linking your Minecraft account", ClickEvent.Action.RUN_COMMAND, "/help linking");
        sendFooter(player);
    }

    private void sendLinkingAccountMenu(Player player) {
        sendHeader(player);
        sendBackLine(player, "Linking your Minecraft account", "/help general");
        player.sendMessage(" ");
        sendOption(player, "Link and join our Discord", ClickEvent.Action.OPEN_URL, websiteUrl(DISCORD_LINK_PATH));
        sendOption(player, "Link your account to the forums", ClickEvent.Action.OPEN_URL, websiteUrl(FORUM_LINK_PATH));
        sendFooter(player);
    }

    private void sendHeader(Player player) {
        player.sendMessage(" ");
        player.sendMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + "HYCOPY NETWORK");
    }

    private void sendFooter(Player player) {
        player.sendMessage(" ");
        TextComponent line = new TextComponent(ChatColor.YELLOW + "Need more help? Visit ");
        TextComponent forums = new TextComponent(ChatColor.AQUA + "our forums");
        forums.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, websiteUrl(FORUMS_PATH)));
        forums.setHoverEvent(buildHover(CLICK_TO_SELECT));
        line.addExtra(forums);
        line.addExtra(new TextComponent(ChatColor.YELLOW + "."));
        player.spigot().sendMessage(line);
        player.sendMessage(" ");
    }

    private void sendBackLine(Player player, String title, String command) {
        TextComponent line = new TextComponent("");
        line.addExtra(new TextComponent(ChatColor.AQUA + "« "));
        line.addExtra(new TextComponent(ChatColor.YELLOW + title));
        line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        line.setHoverEvent(buildHover(GO_BACK));
        player.spigot().sendMessage(line);
    }

    private void sendOption(Player player, String label, ClickEvent.Action action, String value) {
        TextComponent line = new TextComponent("");
        line.addExtra(new TextComponent("  " + ChatColor.RED + "* "));
        TextComponent option = new TextComponent(ChatColor.AQUA + label);
        option.setClickEvent(new ClickEvent(action, value));
        option.setHoverEvent(buildHover(CLICK_TO_SELECT));
        line.addExtra(option);
        player.spigot().sendMessage(line);
    }

    private String websiteUrl(String path) {
        return "https://" + NetworkConstants.domain() + path;
    }

    private HoverEvent buildHover(String text) {
        BaseComponent[] hover = new BaseComponent[] {new TextComponent(ChatColor.LIGHT_PURPLE + text)};
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
    }
}
