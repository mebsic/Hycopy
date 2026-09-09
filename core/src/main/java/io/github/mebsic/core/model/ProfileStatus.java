package io.github.mebsic.core.model;

import java.util.Locale;

public enum ProfileStatus {
    ONLINE("Online"),
    AWAY("Away"),
    BUSY("Busy"),
    APPEAR_OFFLINE("Appear Offline");

    private final String displayName;

    ProfileStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProfileStatus fromStoredName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
