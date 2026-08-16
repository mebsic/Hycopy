package io.github.mebsic.proxy.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PrivateMessageReplyService {
    private static final long REPLY_WINDOW_MILLIS = 5L * 60L * 1000L;

    private final Map<UUID, ReplyTarget> lastPrivateMessagePartner = new ConcurrentHashMap<UUID, ReplyTarget>();

    public void rememberConversation(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return;
        }
        long now = System.currentTimeMillis();
        lastPrivateMessagePartner.put(first, new ReplyTarget(second, now));
        lastPrivateMessagePartner.put(second, new ReplyTarget(first, now));
    }

    public UUID getReplyTarget(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        ReplyTarget replyTarget = lastPrivateMessagePartner.get(playerId);
        if (replyTarget == null) {
            return null;
        }
        if (System.currentTimeMillis() - replyTarget.updatedAtMillis > REPLY_WINDOW_MILLIS) {
            lastPrivateMessagePartner.remove(playerId, replyTarget);
            return null;
        }
        return replyTarget.playerId;
    }

    public void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastPrivateMessagePartner.remove(playerId);
    }

    private static final class ReplyTarget {
        private final UUID playerId;
        private final long updatedAtMillis;

        private ReplyTarget(UUID playerId, long updatedAtMillis) {
            this.playerId = playerId;
            this.updatedAtMillis = updatedAtMillis;
        }
    }
}
