package io.github.mebsic.core.util;

import io.github.mebsic.core.model.Rank;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class HubFlightUtil {
    private static final double MIN_UPWARD_VELOCITY = 0.2D;
    private static final double FLIGHT_ACTIVATION_UPWARD_VELOCITY = 1.0D;

    private HubFlightUtil() {
    }

    public static boolean applyFlight(Player player, Rank rank) {
        return applyFlight(player, rank, true);
    }

    public static boolean applyFlight(Player player, Rank rank, boolean flightEnabled) {
        if (player == null) {
            return false;
        }
        Rank effectiveRank = rank == null ? Rank.DEFAULT : rank;
        boolean enabled = flightEnabled && effectiveRank.isAtLeast(Rank.VIP);
        player.setAllowFlight(enabled);
        if (!enabled) {
            player.setFlying(false);
            return false;
        }
        if (!player.isFlying()) {
            player.setFlying(true);
        }
        return true;
    }

    public static boolean applyVelocityIfFlightEnabled(Player player) {
        return applyVelocityIfFlightEnabled(player, MIN_UPWARD_VELOCITY);
    }

    public static boolean applyActivationVelocityIfFlightEnabled(Player player) {
        return applyVelocityIfFlightEnabled(player, FLIGHT_ACTIVATION_UPWARD_VELOCITY);
    }

    private static boolean applyVelocityIfFlightEnabled(Player player, double minUpwardVelocity) {
        if (player == null) {
            return false;
        }
        if (!player.getAllowFlight()) {
            return true;
        }
        Vector velocity = player.getVelocity();
        if (velocity == null) {
            return false;
        }
        double y = Math.max(minUpwardVelocity, velocity.getY());
        player.setVelocity(new Vector(velocity.getX(), y, velocity.getZ()));
        return true;
    }
}
