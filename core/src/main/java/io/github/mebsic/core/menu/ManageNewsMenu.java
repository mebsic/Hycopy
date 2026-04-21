package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.BossBarMessage;
import io.github.mebsic.core.service.NewsEditService;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageNewsMenu extends Menu {
    private static final String DONE_MESSAGE = ChatColor.GREEN + CommonMessages.DONE;
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 21;
    private static final int[] NEWS_SLOTS = new int[] {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int EMPTY_SLOT = 22;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int DELETE_ALL_SLOT = 50;
    private static final int NEXT_SLOT = 53;

    private final NewsEditService newsEditService;
    private final int page;

    public ManageNewsMenu(NewsEditService newsEditService, int page) {
        super("Manage News", SIZE);
        this.newsEditService = newsEditService;
        this.page = Math.max(1, page);
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        PagedView view = viewForPage();
        set(inventory, BACK_SLOT, item(Material.ARROW, ChatColor.GREEN + "Go Back"));
        set(inventory, CLOSE_SLOT, item(Material.BARRIER, ChatColor.RED + "Close"));

        if (view.totalPages > 1 && view.page > 1) {
            set(inventory, PREVIOUS_SLOT, paginationItem(ChatColor.GREEN + "Page " + (view.page - 1)));
        }
        if (view.totalPages > 1 && view.page < view.totalPages) {
            set(inventory, NEXT_SLOT, paginationItem(ChatColor.GREEN + "Page " + (view.page + 1)));
        }
        if (!view.entries.isEmpty()) {
            set(inventory, DELETE_ALL_SLOT, deleteAllNewsItem());
        }
        if (view.entries.isEmpty()) {
            set(inventory, EMPTY_SLOT, noNewsItem());
            return;
        }
        for (int i = 0; i < NEWS_SLOTS.length; i++) {
            int index = view.start + i;
            if (index < 0 || index >= view.entries.size()) {
                break;
            }
            BossBarMessage entry = view.entries.get(index);
            set(inventory, NEWS_SLOTS[i], newsItem(index + 1, entry));
        }
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        Player player = click.getPlayer();
        int slot = click.getRawSlot();
        PagedView view = viewForPage();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == BACK_SLOT) {
            new NewsMenu(newsEditService).open(player);
            return;
        }
        if (slot == PREVIOUS_SLOT && view.page > 1) {
            openPage(player, view.page - 1);
            return;
        }
        if (slot == NEXT_SLOT && view.page < view.totalPages) {
            openPage(player, view.page + 1);
            return;
        }
        if (slot == DELETE_ALL_SLOT) {
            if (newsEditService == null || view.entries.isEmpty()) {
                return;
            }
            int deleted = newsEditService.deleteAllNews();
            if (deleted > 0) {
                player.sendMessage(DONE_MESSAGE);
            }
            openPage(player, view.page);
            return;
        }

        int indexOnPage = slotIndex(slot);
        if (indexOnPage < 0) {
            return;
        }
        int absoluteIndex = view.start + indexOnPage;
        if (absoluteIndex < 0 || absoluteIndex >= view.entries.size()) {
            return;
        }
        BossBarMessage selected = view.entries.get(absoluteIndex);
        if (newsEditService == null) {
            player.closeInventory();
            return;
        }
        BossBarMessage refreshed = newsEditService.loadNewsById(selected.getId());
        if (refreshed == null) {
            player.sendMessage(ChatColor.RED + "That news item is no longer available!");
            openPage(player, view.page);
            return;
        }
        if (click.isRightClick()) {
            boolean deleted = newsEditService.deleteNewsById(refreshed.getId());
            if (!deleted) {
                player.sendMessage(ChatColor.RED + "That news item is no longer available!");
            } else {
                player.sendMessage(DONE_MESSAGE);
            }
            openPage(player, view.page);
            return;
        }
        newsEditService.openEditNewsFromManage(player, refreshed, view.page);
    }

    private void openPage(Player player, int targetPage) {
        if (player == null) {
            return;
        }
        new ManageNewsMenu(newsEditService, targetPage).open(player);
    }

    private PagedView viewForPage() {
        List<BossBarMessage> entries = newsEditService == null
                ? Collections.emptyList()
                : newsEditService.loadAddedNews();
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int resolvedPage = Math.max(1, Math.min(page, totalPages));
        int start = (resolvedPage - 1) * PAGE_SIZE;
        return new PagedView(entries, totalPages, resolvedPage, start);
    }

    private int slotIndex(int slot) {
        for (int i = 0; i < NEWS_SLOTS.length; i++) {
            if (NEWS_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private org.bukkit.inventory.ItemStack newsItem(int number, BossBarMessage message) {
        String text = message == null ? "" : safeText(message.getText());
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + (text.isEmpty() ? "(empty)" : text));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to edit!");
        lore.add(ChatColor.DARK_GRAY + "Right-click to delete");
        return item(
                resolveSignMaterial(),
                ChatColor.GREEN + "News #" + number,
                lore
        );
    }

    private org.bukkit.inventory.ItemStack noNewsItem() {
        List<String> lore = new ArrayList<String>(2);
        lore.add(ChatColor.GRAY + "To manage saved news messages,");
        lore.add(ChatColor.GRAY + "add one first!");
        Material redTerracotta = Material.matchMaterial("RED_TERRACOTTA");
        if (redTerracotta != null) {
            return item(redTerracotta, ChatColor.RED + "No News", lore);
        }
        return item(Material.PAPER, ChatColor.RED + "No News", lore);
    }

    private org.bukkit.inventory.ItemStack paginationItem(String displayName) {
        return item(Material.ARROW, displayName);
    }

    private org.bukkit.inventory.ItemStack deleteAllNewsItem() {
        List<String> lore = new ArrayList<String>(5);
        lore.add("");
        lore.add(ChatColor.RED.toString() + ChatColor.BOLD + "WARNING");
        lore.add(ChatColor.GRAY + "This will delete all saved news items!");
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to delete!");
        Material lavaBucket = Material.matchMaterial("LAVA_BUCKET");
        if (lavaBucket != null) {
            return item(lavaBucket, ChatColor.RED + "Delete All News", lore);
        }
        return item(Material.BUCKET, ChatColor.RED + "Delete All News", lore);
    }

    private Material resolveSignMaterial() {
        Material sign = Material.matchMaterial("SIGN");
        return sign == null ? Material.PAPER : sign;
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static final class PagedView {
        private final List<BossBarMessage> entries;
        private final int totalPages;
        private final int page;
        private final int start;

        private PagedView(List<BossBarMessage> entries, int totalPages, int page, int start) {
            this.entries = entries == null ? Collections.emptyList() : entries;
            this.totalPages = totalPages;
            this.page = page;
            this.start = start;
        }
    }
}
