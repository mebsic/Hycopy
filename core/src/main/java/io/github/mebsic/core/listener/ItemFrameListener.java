package io.github.mebsic.core.listener;

import io.github.mebsic.core.server.ServerType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class ItemFrameListener implements Listener {
    private final ServerType serverType;
    private final ImageListener imageListener;

    public ItemFrameListener(ServerType serverType, ImageListener imageListener) {
        this.serverType = serverType == null ? ServerType.UNKNOWN : serverType;
        this.imageListener = imageListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event == null || event.getRightClicked() == null) {
            return;
        }
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }
        if (!isProtectedFrame(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }
        if (!isProtectedFrame(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameDamage(EntityDamageEvent event) {
        if (event == null || event instanceof EntityDamageByEntityEvent || event.getEntity() == null) {
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }
        if (!isProtectedFrame(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameBreak(HangingBreakEvent event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }
        if (!isProtectedFrame(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean isProtectedFrame(Entity entity) {
        if (entity == null || serverType == null || serverType == ServerType.UNKNOWN) {
            return false;
        }
        if (serverType.isGame()) {
            return true;
        }
        if (!serverType.isHub() || imageListener == null) {
            return false;
        }
        return imageListener.isRuntimeImageFrameEntity(entity);
    }
}
