package io.github.mebsic.core.store;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.model.BossBarMessage;
import io.github.mebsic.core.server.ServerType;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.mongodb.client.model.Filters.eq;

public class BossBarMessageStore {
    public static final String SCOPE_GAME = "GAME";
    public static final String SCOPE_HUB = "HUB";

    private static final float MIN_VALUE = 0.01f;
    private static final float MAX_VALUE = 1.0f;

    private final MongoCollection<Document> collection;

    public BossBarMessageStore(MongoManager mongo) {
        this.collection = mongo == null ? null : mongo.getBossBarMessages();
    }

    public void ensureDefaults() {
        if (collection == null) {
            return;
        }
        long now = System.currentTimeMillis();
        upsertDefault(new Document("id", "game_default_banner")
                .append("scope", SCOPE_GAME)
                .append("serverType", "ANY")
                .append("text", "Playing {gameType} on {domain}")
                .append("value", 1.0)
                .append("enabled", true)
                .append("createdAt", now)
                .append("updatedAt", now));
        upsertDefault(new Document("id", "hub_news_1")
                .append("scope", SCOPE_HUB)
                .append("serverType", "ANY")
                .append("text", "")
                .append("value", 1.0)
                .append("animationType", "NONE")
                .append("animationColor", "WHITE")
                .append("firstColor", "WHITE")
                .append("secondColor", "WHITE")
                .append("startColor", "WHITE")
                .append("endColor", "WHITE")
                .append("enabled", true)
                .append("createdAt", now)
                .append("updatedAt", now));
        upsertDefault(new Document("id", "hub_news_2")
                .append("scope", SCOPE_HUB)
                .append("serverType", "ANY")
                .append("text", "")
                .append("value", 1.0)
                .append("animationType", "NONE")
                .append("animationColor", "WHITE")
                .append("firstColor", "WHITE")
                .append("secondColor", "WHITE")
                .append("startColor", "WHITE")
                .append("endColor", "WHITE")
                .append("enabled", true)
                .append("createdAt", now)
                .append("updatedAt", now));
        upsertDefault(new Document("id", "hub_news_3")
                .append("scope", SCOPE_HUB)
                .append("serverType", "ANY")
                .append("text", "")
                .append("value", 1.0)
                .append("animationType", "NONE")
                .append("animationColor", "WHITE")
                .append("firstColor", "WHITE")
                .append("secondColor", "WHITE")
                .append("startColor", "WHITE")
                .append("endColor", "WHITE")
                .append("enabled", true)
                .append("createdAt", now)
                .append("updatedAt", now));
    }

    private void upsertDefault(Document defaults) {
        if (collection == null || defaults == null) {
            return;
        }
        String id = defaults.getString("id");
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        collection.updateOne(eq("id", id),
                new Document("$setOnInsert", defaults),
                new UpdateOptions().upsert(true));
    }

    public List<BossBarMessage> loadByScope(String scope, ServerType type) {
        List<BossBarMessage> messages = new ArrayList<>();
        if (collection == null) {
            return messages;
        }
        String normalizedScope = normalizeScope(scope);
        for (Document doc : collection.find(eq("scope", normalizedScope))) {
            if (doc == null) {
                continue;
            }
            Boolean enabled = doc.getBoolean("enabled", true);
            if (enabled != null && !enabled) {
                continue;
            }
            String targetServerType = doc.getString("serverType");
            if (!matchesServerType(targetServerType, type)) {
                continue;
            }
            String text = doc.getString("text");
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            BossBarMessage message = toBossBarMessage(doc, normalizedScope);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    public List<BossBarMessage> loadEditableHubNews(boolean onlyWithText) {
        List<BossBarMessage> messages = new ArrayList<BossBarMessage>();
        if (collection == null) {
            return messages;
        }
        List<Document> documents = new ArrayList<Document>();
        for (Document doc : collection.find(eq("scope", SCOPE_HUB))) {
            if (doc == null) {
                continue;
            }
            int index = parseHubNewsIndex(doc.getString("id"));
            if (index <= 0) {
                continue;
            }
            if (onlyWithText) {
                String text = safeText(doc.getString("text"));
                if (text.isEmpty()) {
                    continue;
                }
            }
            documents.add(doc);
        }
        Collections.sort(documents, Comparator.comparingInt(document -> parseHubNewsIndex(document.getString("id"))));
        for (Document doc : documents) {
            BossBarMessage message = toBossBarMessage(doc, SCOPE_HUB);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    public BossBarMessage loadHubNewsById(String id) {
        if (collection == null) {
            return null;
        }
        String resolvedId = safeText(id);
        if (resolvedId.isEmpty()) {
            return null;
        }
        if (parseHubNewsIndex(resolvedId) <= 0) {
            return null;
        }
        Document doc = collection.find(eq("id", resolvedId)).first();
        if (doc == null) {
            return null;
        }
        if (!SCOPE_HUB.equals(normalizeScope(doc.getString("scope")))) {
            return null;
        }
        return toBossBarMessage(doc, SCOPE_HUB);
    }

    public BossBarMessage addHubNewsItem(String text) {
        if (collection == null) {
            return null;
        }
        String normalizedText = safeText(text);
        if (normalizedText.isEmpty()) {
            return null;
        }
        ensureDefaults();
        String targetId = "";
        int highestIndex = 0;
        for (Document doc : collection.find(eq("scope", SCOPE_HUB))) {
            if (doc == null) {
                continue;
            }
            String id = safeText(doc.getString("id"));
            int index = parseHubNewsIndex(id);
            if (index <= 0) {
                continue;
            }
            highestIndex = Math.max(highestIndex, index);
            if (!targetId.isEmpty()) {
                continue;
            }
            String existingText = safeText(doc.getString("text"));
            if (existingText.isEmpty()) {
                targetId = id;
            }
        }
        if (targetId.isEmpty()) {
            targetId = "hub_news_" + (highestIndex + 1);
        }
        long now = System.currentTimeMillis();
        Document set = new Document("scope", SCOPE_HUB)
                .append("serverType", "ANY")
                .append("animationType", "FLASH")
                .append("animationColor", "WHITE")
                .append("firstColor", "WHITE")
                .append("secondColor", "WHITE")
                .append("startColor", "WHITE")
                .append("endColor", "WHITE")
                .append("text", normalizedText)
                .append("updatedAt", now);
        Document setOnInsert = new Document("createdAt", now)
                .append("value", 1.0)
                .append("enabled", true);
        UpdateResult updateResult = collection.updateOne(
                eq("id", targetId),
                new Document("$set", set)
                        .append("$setOnInsert", setOnInsert),
                new UpdateOptions().upsert(true)
        );
        if (updateResult == null) {
            return null;
        }
        BossBarMessage saved = loadHubNewsById(targetId);
        if (saved != null) {
            return saved;
        }
        return new BossBarMessage(
                targetId,
                normalizedText,
                1.0f,
                SCOPE_HUB,
                "ANY",
                "FLASH",
                "WHITE",
                "WHITE",
                "WHITE",
                "WHITE",
                "WHITE"
        );
    }

    public boolean saveHubNewsProperties(String id,
                                         String text,
                                         String animationType,
                                         String startColor,
                                         String sweepColor,
                                         String endColor) {
        if (collection == null) {
            return false;
        }
        String resolvedId = safeText(id);
        if (resolvedId.isEmpty() || parseHubNewsIndex(resolvedId) <= 0) {
            return false;
        }
        String resolvedText = safeText(text);
        if (resolvedText.isEmpty()) {
            return false;
        }
        String resolvedType = "SWEEP".equalsIgnoreCase(safeText(animationType)) ? "SWEEP" : "FLASH";
        String resolvedStart = normalizeColorName(startColor);
        String resolvedSweep = normalizeColorName(sweepColor);
        String resolvedEnd = normalizeColorName(endColor);

        Document set = new Document("text", resolvedText)
                .append("animationType", resolvedType)
                .append("startColor", resolvedStart)
                .append("endColor", resolvedEnd)
                .append("updatedAt", System.currentTimeMillis());
        set.append("animationColor", resolvedSweep);
        if ("SWEEP".equals(resolvedType)) {
            set.append("firstColor", resolvedStart);
            set.append("secondColor", resolvedEnd);
        } else {
            set.append("firstColor", resolvedSweep);
            set.append("secondColor", resolvedEnd);
        }
        UpdateResult result = collection.updateOne(
                eq("id", resolvedId),
                new Document("$set", set)
        );
        return result != null && result.getMatchedCount() > 0;
    }

    public boolean deleteEditableHubNewsById(String id) {
        if (collection == null) {
            return false;
        }
        String resolvedId = safeText(id);
        if (resolvedId.isEmpty() || parseHubNewsIndex(resolvedId) <= 0) {
            return false;
        }
        DeleteResult result = collection.deleteOne(eq("id", resolvedId));
        return result != null && result.getDeletedCount() > 0;
    }

    public int deleteAllEditableHubNews() {
        if (collection == null) {
            return 0;
        }
        List<String> ids = new ArrayList<String>();
        for (Document doc : collection.find(eq("scope", SCOPE_HUB))) {
            if (doc == null) {
                continue;
            }
            String id = safeText(doc.getString("id"));
            if (parseHubNewsIndex(id) <= 0) {
                continue;
            }
            ids.add(id);
        }
        int deleted = 0;
        for (String id : ids) {
            DeleteResult result = collection.deleteOne(eq("id", id));
            if (result != null && result.getDeletedCount() > 0) {
                deleted += (int) result.getDeletedCount();
            }
        }
        return deleted;
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return SCOPE_GAME;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (SCOPE_HUB.equals(normalized)) {
            return SCOPE_HUB;
        }
        return SCOPE_GAME;
    }

    private boolean matchesServerType(String targetServerType, ServerType type) {
        if (targetServerType == null || targetServerType.trim().isEmpty()) {
            return true;
        }
        String normalized = targetServerType.trim().toUpperCase(Locale.ROOT);
        if ("ANY".equals(normalized) || "ALL".equals(normalized)) {
            return true;
        }
        if ("HUB".equals(normalized)) {
            return type != null && type.isHub();
        }
        if ("GAME".equals(normalized)) {
            return type != null && type.isGame();
        }
        return type != null && normalized.equals(type.name());
    }

    private float sanitizeValue(float raw) {
        if (Float.isNaN(raw) || Float.isInfinite(raw)) {
            return 1.0f;
        }
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, raw));
    }

    private BossBarMessage toBossBarMessage(Document doc, String normalizedScope) {
        if (doc == null) {
            return null;
        }
        String id = safeText(doc.getString("id"));
        String text = doc.getString("text");
        Number valueNumber = doc.get("value", Number.class);
        float value = sanitizeValue(valueNumber == null ? 1.0f : valueNumber.floatValue());
        String targetServerType = doc.getString("serverType");
        String animationType = doc.getString("animationType");
        String animationColor = doc.getString("animationColor");
        String firstColor = doc.getString("firstColor");
        String secondColor = doc.getString("secondColor");
        String startColor = doc.getString("startColor");
        String endColor = doc.getString("endColor");
        return new BossBarMessage(
                id,
                text,
                value,
                normalizedScope,
                targetServerType,
                animationType,
                animationColor,
                firstColor,
                secondColor,
                startColor,
                endColor
        );
    }

    private int parseHubNewsIndex(String id) {
        String normalized = safeText(id).toLowerCase(Locale.ROOT);
        String prefix = "hub_news_";
        if (!normalized.startsWith(prefix)) {
            return -1;
        }
        String rawIndex = normalized.substring(prefix.length());
        if (rawIndex.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < rawIndex.length(); i++) {
            char current = rawIndex.charAt(i);
            if (current < '0' || current > '9') {
                return -1;
            }
        }
        try {
            return Integer.parseInt(rawIndex);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private String normalizeColorName(String value) {
        String normalized = safeText(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "WHITE";
        }
        return normalized;
    }
}
