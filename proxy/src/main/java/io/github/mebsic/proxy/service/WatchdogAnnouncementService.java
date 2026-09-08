package io.github.mebsic.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class WatchdogAnnouncementService {
    private static final long INITIAL_ANNOUNCEMENT_DELAY_MINUTES = 5L;
    private static final long ANNOUNCEMENT_INTERVAL_MINUTES = 30L;
    private static final int WATCHDOG_MIN_BANS = 8_000;
    private static final int WATCHDOG_MAX_BANS = 20_000;
    private static final int STAFF_MIN_BANS = 3_000;
    private static final int STAFF_MAX_BANS = 5_000;

    private final ProxyServer proxy;
    private final Object plugin;
    private final NumberFormat numberFormat;
    private ScheduledTask task;

    public WatchdogAnnouncementService(ProxyServer proxy, Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    }

    public void start() {
        if (proxy == null || plugin == null || task != null) {
            return;
        }
        task = proxy.getScheduler()
                .buildTask(plugin, this::broadcastAnnouncement)
                .delay(INITIAL_ANNOUNCEMENT_DELAY_MINUTES, TimeUnit.MINUTES)
                .repeat(ANNOUNCEMENT_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .schedule();
    }

    public void stop() {
        if (task == null) {
            return;
        }
        task.cancel();
        task = null;
    }

    private void broadcastAnnouncement() {
        int watchdogBans = randomInclusive(WATCHDOG_MIN_BANS, WATCHDOG_MAX_BANS);
        int staffBans = randomInclusive(STAFF_MIN_BANS, STAFF_MAX_BANS);
        while (staffBans == watchdogBans) {
            staffBans = randomInclusive(STAFF_MIN_BANS, STAFF_MAX_BANS);
        }

        Component empty = Component.empty();
        Component title = Component.text("[WATCHDOG ANNOUNCEMENT]", NamedTextColor.DARK_RED);
        Component watchdogLine = Component.text("Watchdog has banned ", NamedTextColor.WHITE)
                .append(Component.text(numberFormat.format(watchdogBans), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" players in the last 7 days.", NamedTextColor.WHITE));
        Component staffLine = Component.text("Staff have banned an additional ", NamedTextColor.WHITE)
                .append(Component.text(numberFormat.format(staffBans), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" in the last 7 days.", NamedTextColor.WHITE));
        Component warning = Component.text("Blacklisted modifications are a bannable offense!", NamedTextColor.RED);

        for (Player player : proxy.getAllPlayers()) {
            if (player == null) {
                continue;
            }
            player.sendMessage(empty);
            player.sendMessage(title);
            player.sendMessage(watchdogLine);
            player.sendMessage(staffLine);
            player.sendMessage(warning);
            player.sendMessage(empty);
        }
    }

    private int randomInclusive(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
