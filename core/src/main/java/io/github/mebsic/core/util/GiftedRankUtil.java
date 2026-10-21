package io.github.mebsic.core.util;

import java.util.Map;

public final class GiftedRankUtil {
    private static final String COUNTER_KEY = "ranksGifted";

    private GiftedRankUtil() {
    }

    public static int readGiftedRanks(Map<?, ?> profile) {
        if (profile == null) {
            return 0;
        }
        Object value = profile.get(COUNTER_KEY);
        if (value instanceof Number) {
            return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, ((Number) value).longValue()));
        }
        if (value instanceof String) {
            try {
                return Math.max(0, Integer.parseInt(((String) value).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
