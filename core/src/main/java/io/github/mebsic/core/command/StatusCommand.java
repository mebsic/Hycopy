package io.github.mebsic.core.command;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.ProfileStatus;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StatusCommand implements TabExecutor {
    private static final String VALID_OPTIONS = "Online, Away, Busy, Appear Offline";
    private static final List<String> FIRST_ARGUMENT_COMPLETIONS = Arrays.asList(
            "online",
            "away",
            "busy",
            "offline",
            "appear"
    );
    private static final List<String> APPEAR_ARGUMENT_COMPLETIONS = Collections.singletonList("offline");

    private final CorePlugin plugin;

    public StatusCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        Player player = (Player) sender;
        ServerType serverType = plugin.getServerType();
        if (serverType == null || !serverType.isHub()) {
            player.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        if (args.length == 0) {
            sendMissingStatus(player);
            return true;
        }
        ProfileStatus status = parseStatus(args);
        if (status == null) {
            sendInvalidStatus(player);
            return true;
        }
        Profile profile = plugin.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return true;
        }
        if (!plugin.isMongoEnabled() || plugin.getProfileStore() == null) {
            player.sendMessage(ChatColor.RED + "MongoDB is not enabled!");
            return true;
        }
        if (!plugin.setStatus(player.getUniqueId(), status)) {
            player.sendMessage(ChatColor.RED + "Could not update your status! Please try again later.");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + "Your online status has been set to "
                + ChatColor.YELLOW + status.getDisplayName());
        return true;
    }

    private ProfileStatus parseStatus(String[] args) {
        if (args == null || args.length == 0 || args.length > 2) {
            return null;
        }
        String normalized = normalize(String.join(" ", args));
        if (normalized.equals("online")) {
            return ProfileStatus.ONLINE;
        }
        if (normalized.equals("away")) {
            return ProfileStatus.AWAY;
        }
        if (normalized.equals("busy")) {
            return ProfileStatus.BUSY;
        }
        if (normalized.equals("appear offline") || normalized.equals("offline")) {
            return ProfileStatus.APPEAR_OFFLINE;
        }
        return null;
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }

    private void sendMissingStatus(Player player) {
        player.sendMessage(ChatColor.RED + "Please provide a status to set!");
        player.sendMessage(ChatColor.RED + "Valid options: " + VALID_OPTIONS);
    }

    private void sendInvalidStatus(Player player) {
        player.sendMessage(ChatColor.RED + "Invalid status, valid options: " + VALID_OPTIONS);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args == null || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filterByPrefix(FIRST_ARGUMENT_COMPLETIONS, args[0]);
        }
        if (args.length == 2 && "appear".equals(normalize(args[0]))) {
            return filterByPrefix(APPEAR_ARGUMENT_COMPLETIONS, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> values, String rawPrefix) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = rawPrefix == null ? "" : rawPrefix.trim().toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            return values;
        }
        java.util.ArrayList<String> matches = new java.util.ArrayList<String>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
