package io.github.mebsic.core.model;

import java.util.Locale;

public enum CosmeticType {
    KNIFE,
    PREFIX_ICON,
    PREFIX_SCHEME,
    GADGET,
    SUIT,
    SUIT_HELMET,
    SUIT_CHESTPLATE,
    SUIT_LEGGINGS,
    SUIT_BOOTS;

    public static final String KNIFE_ID_PREFIX = "mm_knife_skin_";
    public static final String DEFAULT_KNIFE_ID = KNIFE_ID_PREFIX + "01";
    public static final String RANDOM_KNIFE_ENTRY_ID = KNIFE_ID_PREFIX + "02";
    public static final String RANDOM_FAVORITE_KNIFE_ENTRY_ID = KNIFE_ID_PREFIX + "03";

    public String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (this != KNIFE || !normalized.startsWith(KNIFE_ID_PREFIX)) {
            return normalized;
        }
        String numberToken = normalized.substring(KNIFE_ID_PREFIX.length());
        if (numberToken.isEmpty()) {
            return normalized;
        }
        for (int i = 0; i < numberToken.length(); i++) {
            if (!Character.isDigit(numberToken.charAt(i))) {
                return normalized;
            }
        }
        try {
            int number = Integer.parseInt(numberToken);
            return number > 0 ? KNIFE_ID_PREFIX + (number < 10 ? "0" : "") + number : normalized;
        } catch (NumberFormatException ignored) {
            return normalized;
        }
    }
}
