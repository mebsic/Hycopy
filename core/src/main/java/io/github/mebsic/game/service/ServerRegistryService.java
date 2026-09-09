package io.github.mebsic.game.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import io.github.mebsic.core.manager.MongoManager;
import org.bson.conversions.Bson;
import io.github.mebsic.core.server.ServerIdentityResolver;
import io.github.mebsic.core.server.ServerRegistryStates;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.server.ServerTypeResolver;
import io.github.mebsic.game.manager.GameManager;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerRegistryService {
    private static final long STALE_HEARTBEAT_MILLIS = 2 * 60 * 1000L;
    private static final long MONGO_CONNECT_TIMEOUT_SECONDS = 3L;
    private static final long MONGO_SOCKET_TIMEOUT_SECONDS = 5L;
    private static final long MONGO_SERVER_SELECTION_TIMEOUT_SECONDS = 3L;
    private static final long RESTART_MARKER_MAX_AGE_MILLIS = 10L * 60L * 1000L;
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final boolean mongoEnabled;
    private final String serverId;
    private final ServerType type;
    private final String group;
    private final String address;
    private final int port;
    private final int heartbeatSeconds;
    private final Integer forcedMaxPlayers;
    private final String mongoUri;
    private final String mongoDatabase;
    private MongoClient client;
    private MongoCollection<Document> collection;
    private BukkitTask task;
    private final AtomicBoolean immediateUpdateQueued;

    public ServerRegistryService(JavaPlugin plugin, FileConfiguration config, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.mongoEnabled = config.getBoolean("mongo.enabled", false);
        String identity = ServerIdentityResolver.resolveIdentity(config, "mini1A");
        this.serverId = identity;
        this.type = ServerTypeResolver.resolve(config, ServerType.MURDER_MYSTERY);
        this.group = config.getString("server.group", MongoManager.MURDER_MYSTERY_GAME_KEY);
        this.address = config.getString("server.address", "game");
        this.port = config.getInt("server.port", 25565);
        this.heartbeatSeconds = Math.max(1, config.getInt("server.heartbeatSeconds", 2));
        this.forcedMaxPlayers = parsePositiveInt(System.getenv("SERVER_MAX_PLAYERS"));
        this.mongoUri = config.getString("mongo.uri", "mongodb://mongo:27017");
        this.mongoDatabase = config.getString("mongo.database", "hycopy");
        this.immediateUpdateQueued = new AtomicBoolean(false);
    }

    public void start() {
        if (!mongoEnabled) {
            return;
        }
        if (serverId == null || serverId.trim().isEmpty()) {
            return;
        }
        if (collection != null) {
            return;
        }
        this.client = createMongoClient(mongoUri);
        MongoDatabase db = client.getDatabase(mongoDatabase);
        this.collection = db.getCollection(MongoManager.SERVER_REGISTRY_COLLECTION);
        sendUpdate("online", true);
        this.task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> sendUpdate("online", false),
                20L,
                heartbeatSeconds * 20L
        );
    }

    public void stop() {
        sendUpdate("offline", false);
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (client != null) {
            client.close();
            client = null;
        }
        collection = null;
    }

    public void requestUpdate() {
        if (!mongoEnabled || plugin == null || collection == null) {
            return;
        }
        if (!immediateUpdateQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    sendUpdate("online", false);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Failed to publish server registry update!\n" + ex.getMessage());
                } finally {
                    immediateUpdateQueued.set(false);
                }
            });
        } catch (RuntimeException ex) {
            immediateUpdateQueued.set(false);
            plugin.getLogger().warning("Failed to schedule server registry update!\n" + ex.getMessage());
        }
    }

    private MongoClient createMongoClient(String uri) {
        ConnectionString connectionString = new ConnectionString(uri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(MONGO_SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .applyToSocketSettings(builder -> {
                    builder.connectTimeout((int) MONGO_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    builder.readTimeout((int) MONGO_SOCKET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                })
                .build();
        return MongoClients.create(settings);
    }

    private void sendUpdate(String status, boolean clearRestartingFlag) {
        if (collection == null) {
            return;
        }
        cleanupStaleRecords();
        int players = gameManager == null ? plugin.getServer().getOnlinePlayers().size() : gameManager.getPlayerCount();
        int maxPlayers = gameManager == null ? plugin.getServer().getMaxPlayers() : gameManager.getMaxPlayers();
        if (forcedMaxPlayers != null) {
            maxPlayers = forcedMaxPlayers;
        }
        List<String> onlinePlayerUuids = new ArrayList<>();
        List<String> onlinePlayerNames = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            onlinePlayerUuids.add(player.getUniqueId().toString());
            onlinePlayerNames.add(player.getName());
        }
        long now = System.currentTimeMillis();
        boolean restartMarkerActive = !clearRestartingFlag && hasActiveRestartMarker(now);
        String state = gameManager == null ? "" : gameManager.getRegistryState();
        if (restartMarkerActive) {
            status = "restarting";
            state = ServerRegistryStates.WAITING_RESTART;
        }
        boolean gameJoinable = gameManager != null && gameManager.isJoinable();
        boolean joinable = status != null
                && status.equalsIgnoreCase("online")
                && !restartMarkerActive
                && gameJoinable;
        Document doc = new Document("_id", serverId)
                .append("type", type.getId())
                .append("group", group)
                .append("address", address)
                .append("port", port)
                .append("players", players)
                .append("maxPlayers", maxPlayers)
                .append("onlinePlayerUuids", onlinePlayerUuids)
                .append("onlinePlayerNames", onlinePlayerNames)
                .append("state", state)
                .append("joinable", joinable)
                .append("status", status)
                .append("lastHeartbeat", now);
        Document update = new Document("$set", doc);
        if (clearRestartingFlag) {
            update.append("$unset", new Document("restarting", "")
                    .append("restartRequestedAt", ""));
        }
        collection.updateOne(new Document("_id", serverId), update, new UpdateOptions().upsert(true));
    }

    private boolean hasActiveRestartMarker(long now) {
        if (collection == null || serverId == null || serverId.trim().isEmpty()) {
            return false;
        }
        try {
            Document doc = collection.find(Filters.eq("_id", serverId)).first();
            if (doc == null) {
                return false;
            }
            Object restartingValue = doc.get("restarting");
            if (!(restartingValue instanceof Boolean) || !((Boolean) restartingValue)) {
                return false;
            }
            Long requestedAt = doc.getLong("restartRequestedAt");
            if (requestedAt == null || requestedAt <= 0L) {
                return false;
            }
            long age = now - requestedAt;
            return age >= 0L && age <= RESTART_MARKER_MAX_AGE_MILLIS;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void cleanupStaleRecords() {
        long cutoff = System.currentTimeMillis() - STALE_HEARTBEAT_MILLIS;
        Bson staleFilter = Filters.or(
                Filters.exists("lastHeartbeat", false),
                Filters.lt("lastHeartbeat", cutoff)
        );
        Bson scopedFilter = (group == null || group.trim().isEmpty())
                ? staleFilter
                : Filters.and(Filters.eq("group", group), staleFilter);
        collection.updateMany(scopedFilter, new Document("$set", new Document("status", "offline")));
        collection.deleteMany(scopedFilter);
    }

    private Integer parsePositiveInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
