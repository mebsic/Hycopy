package io.github.mebsic.murdermystery.command;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.listener.ImageListener;
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

public class ClearImageCommand implements CommandExecutor {
    private static final String IMAGE_URL_KEY = "imageUrl";
    private static final String IMAGE_ENABLED_KEY = "imageEnabled";

    private final CorePlugin corePlugin;
    private final Runnable refreshCallback;

    public ClearImageCommand(CorePlugin corePlugin) {
        this(corePlugin, null);
    }

    public ClearImageCommand(CorePlugin corePlugin, Runnable refreshCallback) {
        this.corePlugin = corePlugin;
        this.refreshCallback = refreshCallback;
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
        if (!RankUtil.hasAtLeast(corePlugin, player, Rank.STAFF)) {
            player.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
            return true;
        }
        if (!isLobby()) {
            player.sendMessage(ChatColor.RED + CommonMessages.LOBBY_ONLY_COMMAND);
            return true;
        }
        if (args != null && args.length > 0) {
            player.sendMessage(ChatColor.RED + "Invalid usage! Correct usage:");
            player.sendMessage(ChatColor.RED + "/clearimage");
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
            Document fields = new Document(IMAGE_URL_KEY, "")
                    .append(IMAGE_ENABLED_KEY, false)
                    .append("updatedAt", now);
            collection.updateOne(
                    new Document("_id", MongoManager.MURDER_MYSTERY_INFORMATION_DOCUMENT_ID),
                    new Document("$set", fields)
                            .append("$setOnInsert", new Document("createdAt", now)),
                    new UpdateOptions().upsert(true)
            );
            publishImageSettingsUpdate();
            refreshLocalDisplay();
            player.sendMessage(ChatColor.GREEN + CommonMessages.DONE);
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Failed to clear lobby image!\n" + ex.getMessage());
        }
        return true;
    }

    private void publishImageSettingsUpdate() {
        if (corePlugin == null || corePlugin.getPubSubService() == null) {
            return;
        }
        try {
            corePlugin.getPubSubService().publish(
                    ImageListener.IMAGE_SETTINGS_UPDATE_CHANNEL,
                    MongoManager.MURDER_MYSTERY_GAME_KEY
            );
        } catch (Exception ignored) {
            // Mongo already updated; cross-server refresh is best effort.
        }
    }

    private void refreshLocalDisplay() {
        if (refreshCallback == null) {
            return;
        }
        try {
            refreshCallback.run();
        } catch (Exception ignored) {
            // Mongo already updated; local refresh will retry through normal listener triggers.
        }
    }

    private boolean isLobby() {
        ServerType serverType = corePlugin.getServerType();
        return serverType != null && serverType.isHub();
    }
}
