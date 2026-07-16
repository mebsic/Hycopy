package io.github.mebsic.murdermystery.command;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.core.util.RankUtil;
import org.bson.Document;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class ImageCommand implements CommandExecutor {
    private static final String IMAGE_URL_KEY = "imageUrl";
    private static final String IMAGE_ENABLED_KEY = "imageEnabled";

    private final CorePlugin corePlugin;

    public ImageCommand(CorePlugin corePlugin) {
        this.corePlugin = corePlugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + CommonMessages.ONLY_PLAYERS_COMMAND);
            return true;
        }
        Player player = (Player) sender;
        if (corePlugin == null) {
            return true;
        }
        if (args == null || args.length != 1) {
            player.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            player.sendMessage(ChatColor.RED + "/image <URL>");
            return true;
        }
        if (!isLobby()) {
            player.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        if (!RankUtil.hasAtLeast(corePlugin, player, Rank.STAFF)) {
            player.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
            return true;
        }

        String imageUrl = safe(args[0]);
        if (!isSupportedImageUrl(imageUrl)) {
            player.sendMessage(ChatColor.RED + "Invalid image URL!");
            return true;
        }
        if (!corePlugin.isMongoEnabled() || corePlugin.getMongoManager() == null) {
            player.sendMessage(ChatColor.RED + "MongoDB is not enabled!");
            return true;
        }

        try {
            corePlugin.getMongoManager().ensureCollection(MongoManager.MURDER_MYSTERY_INFORMATION_COLLECTION);
            MongoCollection<Document> collection = corePlugin.getMongoManager()
                    .getCollection(MongoManager.MURDER_MYSTERY_INFORMATION_COLLECTION);
            if (collection == null) {
                player.sendMessage(ChatColor.RED + "MongoDB image settings are unavailable!");
                return true;
            }

            long now = System.currentTimeMillis();
            Document fields = new Document(IMAGE_URL_KEY, imageUrl)
                    .append(IMAGE_ENABLED_KEY, true)
                    .append("updatedAt", now);
            collection.updateOne(
                    new Document("_id", MongoManager.MURDER_MYSTERY_INFORMATION_DOCUMENT_ID),
                    new Document("$set", fields)
                            .append("$setOnInsert", new Document("createdAt", now)),
                    new UpdateOptions().upsert(true)
            );
            player.sendMessage(ChatColor.GREEN + CommonMessages.DONE);
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Failed to update lobby image!\n" + ex.getMessage());
        }
        return true;
    }

    private boolean isLobby() {
        ServerType serverType = corePlugin.getServerType();
        return serverType != null && serverType.isHub();
    }

    private boolean isSupportedImageUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = safe(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = safe(uri.getHost());
            return ("http".equals(scheme) || "https".equals(scheme))
                    && !host.isEmpty()
                    && hasSupportedImageExtension(uri.getPath());
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private boolean hasSupportedImageExtension(String path) {
        String normalized = safe(path).toLowerCase(Locale.ROOT);
        return normalized.endsWith(".png")
                || normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg");
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
