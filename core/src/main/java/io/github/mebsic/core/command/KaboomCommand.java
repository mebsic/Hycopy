package io.github.mebsic.core.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class KaboomCommand implements CommandExecutor, TabCompleter {
    private static final Vector LAUNCH_VELOCITY = new Vector(0.0, 2.0, 0.0);

    private final CorePlugin plugin;

    public KaboomCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!RankUtil.hasAtLeast(plugin, player, Rank.STAFF)) {
                player.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
                return true;
            }
        }

        if (args.length != 1) {
            sendUsage(sender, label);
            return true;
        }

        List<Player> targets = resolveTargets(sender, args[0]);
        if (targets == null) {
            return true;
        }

        for (Player target : targets) {
            launch(target);
        }

        sender.sendMessage(ChatColor.GREEN + CommonMessages.DONE);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player && !RankUtil.hasAtLeast(plugin, (Player) sender, Rank.STAFF)) {
            return Collections.emptyList();
        }
        if (args.length != 1) {
            return Collections.emptyList();
        }
        return matching(targetSuggestions(), args[0]);
    }

    private List<Player> resolveTargets(CommandSender sender, String rawTarget) {
        if (rawTarget == null) {
            return null;
        }
        if (rawTarget.equalsIgnoreCase("all")) {
            return new ArrayList<Player>(Bukkit.getOnlinePlayers());
        }
        Player target = Bukkit.getPlayerExact(rawTarget);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + CommonMessages.PLAYER_NOT_FOUND_COMMAND);
            return null;
        }
        List<Player> targets = new ArrayList<Player>();
        targets.add(target);
        return targets;
    }

    private void launch(Player target) {
        if (target == null) {
            return;
        }
        Location location = target.getLocation();
        World world = location.getWorld();
        if (world != null) {
            world.strikeLightningEffect(location);
        }
        target.setFallDistance(0.0F);
        target.setVelocity(LAUNCH_VELOCITY.clone());
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
        sender.sendMessage(ChatColor.RED + "/" + label + " <player/all>");
    }

    private List<String> targetSuggestions() {
        List<String> suggestions = new ArrayList<String>();
        suggestions.add("all");
        for (Player player : Bukkit.getOnlinePlayers()) {
            suggestions.add(player.getName());
        }
        return suggestions;
    }

    private List<String> matching(List<String> values, String prefix) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<String>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
