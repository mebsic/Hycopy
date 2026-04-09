package io.github.mebsic.core.command;

import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.service.ProfileCommandSyncService;
import io.github.mebsic.core.store.ProfileStore;
import io.github.mebsic.core.util.MojangApi;
import io.github.mebsic.core.util.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RankCommand implements TabExecutor {
    private static final int DEFAULT_MVP_PLUS_PLUS_DAYS = 30;
    private static final String MVP_PLUS_REQUIRED_MESSAGE = ChatColor.RED + "That player must have MVP+ rank!";
    private static final String DURATION_NOT_SUPPORTED_MESSAGE = ChatColor.RED + "That rank does not have any duration!";
    private static final List<String> TAB_RANKS = Arrays.asList(
            "DEFAULT",
            "VIP",
            "VIP+",
            "MVP",
            "MVP+",
            "MVP++",
            "YOUTUBE",
            "STAFF"
    );
    private static final List<String> TAB_MVP_PLUS_PLUS_DURATIONS = Arrays.asList(
            "30d",
            "90d",
            "180d",
            "365d"
    );

    private final CorePlugin plugin;

    public RankCommand(CorePlugin plugin) {
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
        if (args.length < 2 || args.length > 3) {
            sendInvalidUsage(sender);
            return true;
        }
        Rank rank = parseRank(args[1]);
        if (rank == null) {
            sendAvailableRanks(sender);
            return true;
        }
        Integer mvpPlusPlusDays = null;
        if (rank == Rank.MVP_PLUS_PLUS) {
            mvpPlusPlusDays = parseMvpPlusPlusDays(args.length >= 3 ? args[2] : null);
            if (mvpPlusPlusDays == null) {
                sendInvalidUsage(sender);
                return true;
            }
        } else if (args.length >= 3) {
            sender.sendMessage(DURATION_NOT_SUPPORTED_MESSAGE);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        UUID uuid = target != null ? target.getUniqueId() : MojangApi.lookupUuid(args[0]);
        String name = target != null ? target.getName() : args[0];
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + CommonMessages.PLAYER_NOT_FOUND_COMMAND);
            return true;
        }
        Rank currentRank = resolveCurrentRank(uuid, name, target);
        if (rank == Rank.MVP_PLUS_PLUS
                && currentRank != Rank.MVP_PLUS
                && currentRank != Rank.MVP_PLUS_PLUS) {
            sender.sendMessage(MVP_PLUS_REQUIRED_MESSAGE);
            return true;
        }
        boolean selfTarget = sender instanceof Player
                && uuid.equals(((Player) sender).getUniqueId());
        String targetMessage = formatRankSetMessage(rank);
        if (target != null) {
            if (rank == Rank.MVP_PLUS_PLUS) {
                plugin.setRank(uuid, rank, mvpPlusPlusDays, true);
            } else {
                plugin.setRank(uuid, rank);
            }
            target.sendMessage(targetMessage);
        } else {
            if (!plugin.isMongoEnabled() || plugin.getProfileStore() == null) {
                sender.sendMessage(ChatColor.RED + "MongoDB is not enabled!");
                return true;
            }
            Boolean hasActiveSubscription = null;
            Long subscriptionExpiresAt = null;
            if (rank == Rank.MVP_PLUS_PLUS && mvpPlusPlusDays != null && mvpPlusPlusDays > 0) {
                long now = System.currentTimeMillis();
                long baseTimestamp = now;
                long existingExpiry = 0L;
                boolean existingActive = false;
                Profile existing = plugin.getProfile(uuid);
                if (existing != null) {
                    existingActive = existing.hasActiveSubscription();
                    existingExpiry = existing.getSubscriptionExpiresAt();
                } else {
                    ProfileStore.ProfileMeta meta = plugin.getProfileStore().loadProfileMeta(uuid, name);
                    if (meta != null) {
                        existingActive = meta.hasActiveSubscription();
                        existingExpiry = meta.getSubscriptionExpiresAt();
                    }
                }
                if (existingActive && existingExpiry > now) {
                    baseTimestamp = existingExpiry;
                }
                hasActiveSubscription = true;
                subscriptionExpiresAt = plugin.calculateSubscriptionExpiryFromBase(baseTimestamp, mvpPlusPlusDays);
            } else if (rank != Rank.MVP_PLUS_PLUS) {
                hasActiveSubscription = false;
                subscriptionExpiresAt = 0L;
            }
            plugin.getProfileStore().updateRank(uuid, name, rank, hasActiveSubscription, subscriptionExpiresAt);
        }
        ProfileCommandSyncService sync = plugin.getProfileCommandSyncService();
        if (sync != null) {
            sync.dispatchRankUpdate(
                    uuid,
                    rank,
                    mvpPlusPlusDays,
                    rank == Rank.MVP_PLUS_PLUS,
                    targetMessage
            );
        }
        if (!selfTarget) {
            sender.sendMessage(ChatColor.GREEN + "Set rank for " + name + " to " + toDisplayRank(rank));
        }
        return true;
    }

    private String formatRankSetMessage(Rank rank) {
        return ChatColor.GREEN + "You are now " + toDisplayRank(rank);
    }

    private Rank resolveCurrentRank(UUID uuid, String fallbackName, Player onlineTarget) {
        if (uuid == null) {
            return Rank.DEFAULT;
        }
        Profile cached = plugin.getProfile(uuid);
        if (cached != null) {
            return cached.getRank() == null ? Rank.DEFAULT : cached.getRank();
        }
        ProfileStore store = plugin.getProfileStore();
        if (plugin.isMongoEnabled() && store != null) {
            ProfileStore.ProfileMeta meta = store.loadProfileMeta(uuid, fallbackName);
            if (meta != null) {
                return meta.getRank();
            }
        }
        if (onlineTarget != null) {
            Rank rank = plugin.getRank(uuid);
            return rank == null ? Rank.DEFAULT : rank;
        }
        return Rank.DEFAULT;
    }

    private Rank parseRank(String value) {
        if (value == null) {
            return null;
        }
        String raw = value.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        if (raw.equals("MVP++") || raw.equals("MVP_PLUS_PLUS")) {
            return Rank.MVP_PLUS_PLUS;
        }
        if (raw.equals("MVP+") || raw.equals("MVP_PLUS")) {
            return Rank.MVP_PLUS;
        }
        if (raw.equals("VIP+") || raw.equals("VIP_PLUS")) {
            return Rank.VIP_PLUS;
        }
        try {
            return Rank.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Integer parseMvpPlusPlusDays(String rawDuration) {
        if (rawDuration == null || rawDuration.trim().isEmpty()) {
            return DEFAULT_MVP_PLUS_PLUS_DAYS;
        }
        String normalized = rawDuration.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("30") || normalized.equals("30d")) {
            return 30;
        }
        if (normalized.equals("90") || normalized.equals("90d")) {
            return 90;
        }
        if (normalized.equals("180") || normalized.equals("180d")) {
            return 180;
        }
        if (normalized.equals("365") || normalized.equals("365d")) {
            return 365;
        }
        return null;
    }

    private void sendInvalidUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
        sender.sendMessage(ChatColor.RED + "/rank <player> <rank> [duration]");
    }

    private void sendAvailableRanks(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Unknown rank! Available ranks:");
        for (Rank rank : Rank.values()) {
            sender.sendMessage(ChatColor.RED + toDisplayRank(rank));
        }
    }

    private String toDisplayRank(Rank rank) {
        if (rank == null) {
            return "";
        }
        switch (rank) {
            case VIP_PLUS:
                return "VIP+";
            case MVP_PLUS:
                return "MVP+";
            case MVP_PLUS_PLUS:
                return "MVP++";
            default:
                return rank.name();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String query = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<String>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online == null || !online.isOnline()) {
                    continue;
                }
                String name = online.getName();
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                if (!name.toLowerCase(Locale.ROOT).startsWith(query)) {
                    continue;
                }
                names.add(name);
            }
            return names;
        }
        if (args.length == 2) {
            return filterByPrefix(TAB_RANKS, args[1]);
        }
        if (args.length == 3) {
            Rank rank = parseRank(args[1]);
            if (rank != Rank.MVP_PLUS_PLUS) {
                return Collections.emptyList();
            }
            return filterByPrefix(TAB_MVP_PLUS_PLUS_DURATIONS, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> values, String query) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        String safeQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<String>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (!value.toLowerCase(Locale.ROOT).startsWith(safeQuery)) {
                continue;
            }
            matches.add(value);
        }
        return matches;
    }
}
