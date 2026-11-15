package io.github.mebsic.core.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class HeightLimitListener implements Listener {
    private static final double CEILING_BUFFER = 1.0D;
    private static final double DOWNWARD_VELOCITY = -0.3D;
    private static final double FLYING_DOWNWARD_VELOCITY = -0.4D;
    private static final long RETRY_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(2L);
    private static final long WARNING_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(1L);

    private final Map<UUID, Long> lastCorrectionByPlayer = new HashMap<>();
    private final Map<UUID, Long> lastWarningByPlayer = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        double maxHeight = to.getWorld().getMaxHeight();
        double toY = to.getY();
        if (toY < maxHeight - CEILING_BUFFER) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location from = event.getFrom();
        long now = System.nanoTime();
        Long lastCorrection = lastCorrectionByPlayer.get(uuid);
        boolean recentCorrection = lastCorrection != null && now - lastCorrection < RETRY_WINDOW_NANOS;
        boolean flying = player.isFlying();

        if (toY > maxHeight) {
            if (from.getY() <= maxHeight) {
                event.setCancelled(true);
            } else {
                Location corrected = from.clone();
                corrected.setY(maxHeight);
                event.setTo(corrected);
            }
        } else if (!recentCorrection || toY <= from.getY()) {
            return;
        }

        Vector velocity = player.getVelocity();
        double downwardVelocity = flying ? FLYING_DOWNWARD_VELOCITY : DOWNWARD_VELOCITY;
        velocity.setY(Math.min(velocity.getY(), downwardVelocity));
        player.setVelocity(velocity);
        lastCorrectionByPlayer.put(uuid, now);

        Long lastWarning = lastWarningByPlayer.get(uuid);
        if (lastWarning == null || now - lastWarning >= WARNING_COOLDOWN_NANOS) {
            player.sendMessage(ChatColor.RED + "You are not allowed to leave this area!");
            lastWarningByPlayer.put(uuid, now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastCorrectionByPlayer.remove(uuid);
        lastWarningByPlayer.remove(uuid);
    }
}
