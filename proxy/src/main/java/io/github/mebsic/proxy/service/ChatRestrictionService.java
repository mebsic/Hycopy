package io.github.mebsic.proxy.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.github.mebsic.core.model.MuteReasonType;
import io.github.mebsic.core.util.NetworkConstants;
import io.github.mebsic.proxy.manager.MongoManager;
import org.bson.Document;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRestrictionService {
    private static final long MUTE_CACHE_MILLIS = 2_000L;
    private static final int MUTE_SEPARATOR_SPACES = 80;
    private static final String MUTE_SEPARATOR_LINE = buildMuteSeparatorLine();
    private final MongoCollection<Document> punishments;
    private final Map<UUID, CachedMuteState> muteCache = new ConcurrentHashMap<>();

    public ChatRestrictionService(MongoDatabase database) {
        this.punishments = database == null ? null : database.getCollection(MongoManager.PUNISHMENTS_COLLECTION);
    }

    public boolean isMuted(UUID playerId) {
        if (playerId == null || punishments == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        CachedMuteState cached = muteCache.get(playerId);
        if (cached != null && cached.expiresAt > now) {
            return cached.muted;
        }

        Document doc = punishments.find(Filters.and(
                        Filters.eq("targetUuid", playerId.toString()),
                        Filters.eq("type", "MUTE"),
                        Filters.eq("active", true)
                ))
                .sort(new Document("createdAt", -1))
                .projection(new Document("expiresAt", 1))
                .first();
        boolean muted = false;
        if (doc != null) {
            Long expiresAt = doc.getLong("expiresAt");
            muted = expiresAt == null || expiresAt <= 0L || now <= expiresAt.longValue();
        }
        muteCache.put(playerId, new CachedMuteState(muted, now + MUTE_CACHE_MILLIS));
        return muted;
    }

    public String formatActiveMuteMessage(UUID playerId) {
        Document doc = findActiveMute(playerId);
        if (doc == null) {
            return null;
        }
        String line = MUTE_SEPARATOR_LINE;
        String reason = normalizeReason(doc.getString("reason"));
        if (reason == null || reason.trim().isEmpty()) {
            reason = "No reason provided";
        }
        MuteReasonType reasonType = MuteReasonType.resolve(reason);
        if (reasonType != null) {
            reason = reasonType.getDescription();
        }
        Long expiresAt = doc.getLong("expiresAt");
        boolean permanent = expiresAt == null || expiresAt <= 0L;
        String reasonForHeader = formatMuteHeaderReason(reason);
        String header = permanent
                ? "§cYou are permanently muted on this server!\n"
                : "§cYou are currently muted for " + reasonForHeader + "\n";
        String time = "";
        if (!permanent) {
            time = "§7Your mute will expire in §c" + formatPrettyTimeLeft(expiresAt) + "\n";
        }
        String space = "§r \n";
        String urlInfo = "§7Find out more here: §e" + resolveMuteFindOutMoreUrl(reason) + "\n";
        String footer = "§7Mute ID: §f" + formatMuteId(doc.getString("_id"));
        return line + header + time + space + urlInfo + footer + line;
    }

    private Document findActiveMute(UUID playerId) {
        if (playerId == null || punishments == null) {
            return null;
        }
        Document doc = punishments.find(Filters.and(
                        Filters.eq("targetUuid", playerId.toString()),
                        Filters.eq("type", "MUTE"),
                        Filters.eq("active", true)
                ))
                .sort(new Document("createdAt", -1))
                .first();
        if (doc == null) {
            return null;
        }
        Long expiresAt = doc.getLong("expiresAt");
        if (expiresAt != null && expiresAt > 0L && System.currentTimeMillis() > expiresAt.longValue()) {
            return null;
        }
        return doc;
    }

    private String resolveMuteFindOutMoreUrl(String reason) {
        MuteReasonType reasonType = MuteReasonType.resolve(reason);
        if (reasonType != null) {
            String url = reasonType.getFindOutMoreUrl();
            if (url != null && !url.trim().isEmpty()) {
                return url.trim();
            }
        }
        return NetworkConstants.mutesUrl();
    }

    private String formatMuteHeaderReason(String reason) {
        String normalized = normalizeReason(reason);
        if (normalized == null || normalized.isEmpty()) {
            return "an unspecified reason";
        }
        String verbosePrefix = "you have been muted for ";
        if (normalized.regionMatches(true, 0, verbosePrefix, 0, verbosePrefix.length())) {
            normalized = normalized.substring(verbosePrefix.length()).trim();
            if (normalized.isEmpty()) {
                return "an unspecified reason";
            }
        }
        if (!normalized.isEmpty()
                && Character.isUpperCase(normalized.charAt(0))
                && (normalized.length() == 1 || Character.isLowerCase(normalized.charAt(1)))) {
            normalized = Character.toLowerCase(normalized.charAt(0)) + normalized.substring(1);
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String formatMuteId(String storedId) {
        if (storedId == null || storedId.trim().isEmpty()) {
            return "unknown";
        }
        String normalized = storedId.trim();
        if (normalized.startsWith("#")) {
            return normalized;
        }
        return "#" + normalized;
    }

    private static String buildMuteSeparatorLine() {
        return "\n§c§m" + repeatSpaces(MUTE_SEPARATOR_SPACES) + "§r\n";
    }

    private static String repeatSpaces(int amount) {
        int safeAmount = Math.max(0, amount);
        StringBuilder builder = new StringBuilder(safeAmount);
        for (int i = 0; i < safeAmount; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private String formatPrettyTimeLeft(Long expiresAt) {
        if (expiresAt == null || expiresAt <= 0L) {
            return "Permanent";
        }
        long remainingMillis = expiresAt.longValue() - System.currentTimeMillis();
        long totalSeconds = Math.max(1L, remainingMillis / 1000L);
        long totalMinutes = totalSeconds / 60L;
        long days = totalMinutes / (24L * 60L);
        totalMinutes %= (24L * 60L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        StringBuilder out = new StringBuilder();
        if (days > 0L) {
            out.append(days).append("d");
        }
        if (hours > 0L) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(hours).append("h");
        }
        if (minutes > 0L || out.length() > 0) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(minutes).append("m");
        }
        if (out.length() > 0) {
            return out.toString();
        }
        return totalSeconds + "s";
    }

    private static final class CachedMuteState {
        private final boolean muted;
        private final long expiresAt;

        private CachedMuteState(boolean muted, long expiresAt) {
            this.muted = muted;
            this.expiresAt = expiresAt;
        }
    }
}
