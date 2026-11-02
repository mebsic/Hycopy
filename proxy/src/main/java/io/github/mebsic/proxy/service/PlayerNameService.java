package io.github.mebsic.proxy.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.mebsic.core.util.MojangApi;
import org.bson.Document;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class PlayerNameService {
    private final ProxyServer proxy;
    private final MongoCollection<Document> collection;
    private final Map<UUID, String> lastKnownName = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private volatile boolean stopped;

    public PlayerNameService(ProxyServer proxy, MongoDatabase database, String collectionName) {
        this.proxy = proxy;
        this.collection = database == null || collectionName == null || collectionName.trim().isEmpty()
                ? null : database.getCollection(collectionName);
    }

    public String getName(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        Optional<Player> online = proxy == null ? Optional.empty() : proxy.getPlayer(uuid);
        if (online.isPresent()) {
            String name = online.get().getUsername();
            rememberName(uuid, name);
            return name;
        }
        String cached = lastKnownName.get(uuid);
        if (cached != null && !cached.trim().isEmpty()) {
            return cached;
        }
        if (collection == null) {
            return "Unknown";
        }
        Document doc = collection.find(eq("uuid", uuid.toString()))
                .projection(new Document("name", 1))
                .first();
        String loaded = doc == null ? null : doc.getString("name");
        if (loaded == null || loaded.trim().isEmpty()) {
            return "Unknown";
        }
        String current = lastKnownName.putIfAbsent(uuid, loaded);
        return current == null ? loaded : current;
    }

    public void rememberName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.trim().isEmpty()) {
            lastKnownName.put(uuid, name);
        }
    }

    public void rememberStoredName(UUID uuid, String name) {
        if (uuid != null && name != null && !name.trim().isEmpty()) {
            lastKnownName.putIfAbsent(uuid, name);
        }
    }

    public UUID resolveByName(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<UUID, String> entry : lastKnownName.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void refreshNames() {
        if (stopped || !refreshRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            Set<UUID> playerIds = new LinkedHashSet<>();
            Map<UUID, String> storedNames = new HashMap<>();
            if (collection != null) {
                try (MongoCursor<Document> cursor = collection.find()
                        .projection(new Document("uuid", 1).append("name", 1).append("friends", 1)
                                .append("blockedPlayers", 1))
                        .iterator()) {
                    while (cursor.hasNext()) {
                        if (stopped) {
                            return;
                        }
                        Document doc = cursor.next();
                        UUID playerId = parseUuid(doc.get("uuid"));
                        String storedName = doc.getString("name");
                        if (playerId != null) {
                            storedNames.put(playerId, storedName);
                            rememberStoredName(playerId, storedName);
                        }
                        for (String field : new String[]{"friends", "blockedPlayers"}) {
                            Object rawPlayers = doc.get(field);
                            if (rawPlayers instanceof List<?>) {
                                for (Object rawPlayer : (List<?>) rawPlayers) {
                                    UUID id = parseUuid(rawPlayer);
                                    if (id != null) {
                                        playerIds.add(id);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                playerIds.addAll(lastKnownName.keySet());
            }
            for (UUID playerId : playerIds) {
                if (stopped || Thread.currentThread().isInterrupted()) {
                    return;
                }
                Optional<Player> online = proxy == null ? Optional.empty() : proxy.getPlayer(playerId);
                if (online.isPresent()) {
                    rememberName(playerId, online.get().getUsername());
                    continue;
                }
                String previousName = lastKnownName.get(playerId);
                String refreshedName = MojangApi.lookupCurrentName(playerId);
                if (stopped) {
                    return;
                }
                online = proxy == null ? Optional.empty() : proxy.getPlayer(playerId);
                if (online.isPresent()) {
                    rememberName(playerId, online.get().getUsername());
                } else if (refreshedName != null) {
                    boolean updated = previousName == null
                            ? lastKnownName.putIfAbsent(playerId, refreshedName) == null
                            : lastKnownName.replace(playerId, previousName, refreshedName);
                    if (updated && collection != null && !refreshedName.equals(storedNames.get(playerId))) {
                        collection.updateOne(
                                and(eq("uuid", playerId.toString()), eq("name", storedNames.get(playerId))),
                                new Document("$set", new Document("name", refreshedName))
                        );
                    }
                }
                Thread.sleep(1_000L);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            refreshRunning.set(false);
        }
    }

    public void stop() {
        stopped = true;
    }

    private UUID parseUuid(Object raw) {
        if (!(raw instanceof String)) {
            return null;
        }
        try {
            return UUID.fromString((String) raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
