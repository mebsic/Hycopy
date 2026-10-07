package io.github.mebsic.core.service;

import java.util.Locale;

public interface CosmeticItem {
    String getId();

    String getMaterial();

    String getDisplayName();

    String getDescription();

    int getCost();

    String getRarity();

    static String normalizeRarity(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("common")
                || normalized.equals("rare")
                || normalized.equals("epic")
                || normalized.equals("legendary")) {
            return normalized;
        }
        return "common";
    }
}
