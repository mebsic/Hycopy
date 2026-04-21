package io.github.mebsic.core.menu;

import io.github.mebsic.core.service.NewsEditService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class NewsMenu extends Menu {
    private static final int SIZE = 36;
    private static final int ADD_NEWS_SLOT = 12;
    private static final int MANAGE_NEWS_SLOT = 14;
    private static final int CLOSE_SLOT = 31;

    private final NewsEditService newsEditService;

    public NewsMenu(NewsEditService newsEditService) {
        super("News", SIZE);
        this.newsEditService = newsEditService;
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        set(inventory, ADD_NEWS_SLOT, item(
                resolveAnvilMaterial(),
                ChatColor.GREEN + "Add News",
                ChatColor.GRAY + "Create a hub news message.",
                "",
                ChatColor.YELLOW + "Click to add!"
        ));
        set(inventory, MANAGE_NEWS_SLOT, item(
                resolveSignMaterial(),
                ChatColor.GREEN + "Manage News",
                ChatColor.GRAY + "View, edit, and delete saved news.",
                "",
                ChatColor.YELLOW + "Click to view!"
        ));
        set(inventory, CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "Close"));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        Player player = click.getPlayer();
        int slot = click.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (newsEditService == null) {
            player.closeInventory();
            return;
        }
        if (slot == ADD_NEWS_SLOT) {
            player.closeInventory();
            newsEditService.startAddNewsInput(player);
            return;
        }
        if (slot == MANAGE_NEWS_SLOT) {
            newsEditService.openManageNewsMenu(player, 1);
        }
    }

    private Material resolveAnvilMaterial() {
        Material anvil = Material.matchMaterial("ANVIL");
        return anvil == null ? Material.IRON_BLOCK : anvil;
    }

    private Material resolveSignMaterial() {
        Material sign = Material.matchMaterial("SIGN");
        return sign == null ? Material.PAPER : sign;
    }
}
