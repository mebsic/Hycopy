package io.github.mebsic.core.server;

import java.util.Locale;

public final class ServerRegistryStates {
    public static final String WAITING = "WAITING";
    public static final String STARTING = "STARTING";
    public static final String IN_GAME = "IN_GAME";
    public static final String ENDING = "ENDING";
    public static final String RESTARTING = "RESTARTING";
    public static final String LOCKED = "LOCKED";
    public static final String DRAINING = "DRAINING";
    public static final String WAITING_RESTART = "WAITING_RESTART";

    private ServerRegistryStates() {
    }

    public static boolean isJoinableGameState(String state) {
        String normalized = normalize(state);
        return WAITING.equals(normalized) || STARTING.equals(normalized);
    }

    public static boolean isJoinableGameState(String state, Boolean joinable) {
        String normalized = normalize(state);
        return Boolean.TRUE.equals(joinable) && !normalized.isEmpty() && !isBlockedState(normalized);
    }

    public static boolean isConnectableState(String state) {
        String normalized = normalize(state);
        return normalized.isEmpty() || !isBlockedState(normalized);
    }

    public static boolean isBlockedState(String state) {
        String normalized = normalize(state);
        return IN_GAME.equals(normalized)
                || ENDING.equals(normalized)
                || RESTARTING.equals(normalized)
                || LOCKED.equals(normalized)
                || DRAINING.equals(normalized)
                || WAITING_RESTART.equals(normalized);
    }

    public static String normalize(String state) {
        if (state == null) {
            return "";
        }
        return state.trim().toUpperCase(Locale.ROOT);
    }
}
