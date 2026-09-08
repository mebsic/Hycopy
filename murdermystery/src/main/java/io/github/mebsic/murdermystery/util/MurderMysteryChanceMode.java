package io.github.mebsic.murdermystery.util;

import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;

public final class MurderMysteryChanceMode {
    public static final double MURDERER_CHANCE_MULTIPLIER = 10.0D;

    private MurderMysteryChanceMode() {
    }

    public static boolean canUse(Profile profile) {
        Rank rank = profile == null || profile.getRank() == null ? Rank.DEFAULT : profile.getRank();
        return rank == Rank.YOUTUBE || rank == Rank.STAFF;
    }

    public static boolean isActive(Profile profile) {
        return profile != null && profile.isMurderMysteryTenTimesModeEnabled() && canUse(profile);
    }

    public static double applyMurdererMultiplier(Profile profile, double baseWeight) {
        double safeWeight = Math.max(0.0D, baseWeight);
        if (!isActive(profile)) {
            return safeWeight;
        }
        return safeWeight * MURDERER_CHANCE_MULTIPLIER;
    }
}
