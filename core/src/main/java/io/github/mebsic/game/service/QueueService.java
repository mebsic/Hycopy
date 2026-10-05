package io.github.mebsic.game.service;

import io.github.mebsic.game.manager.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class QueueService {
    private final Plugin plugin;
    private final GameManager gameManager;
    private BukkitTask task;

    public QueueService(Plugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void handleJoin(Player player) {
        if (player == null) {
            return;
        }
        if (gameManager == null) {
            return;
        }
        gameManager.handleJoin(player);
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        if (gameManager == null) {
            return;
        }
        gameManager.handleQuit(player);
    }

}
