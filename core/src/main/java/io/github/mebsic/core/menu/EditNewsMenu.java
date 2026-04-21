package io.github.mebsic.core.menu;

import io.github.mebsic.core.service.NewsEditService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class EditNewsMenu extends Menu {
    private static final int SIZE = 36;
    private static final int MESSAGE_TYPE_SLOT = 11;
    private static final int MESSAGE_TEXT_SLOT = 13;
    private static final int MESSAGE_COLOR_SLOT = 15;
    private static final int SAVE_SLOT = 27;
    private static final int CANCEL_SLOT = 31;

    private final NewsEditService newsEditService;

    public EditNewsMenu(NewsEditService newsEditService) {
        super("Edit News", SIZE);
        this.newsEditService = newsEditService;
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        NewsEditService.EditSessionSnapshot snapshot = newsEditService == null
                ? null
                : newsEditService.getEditSessionSnapshot(player);
        set(inventory, MESSAGE_TYPE_SLOT, messageTypeItem(snapshot));
        set(inventory, MESSAGE_TEXT_SLOT, messageTextItem(snapshot));
        set(inventory, MESSAGE_COLOR_SLOT, messageColorItem(snapshot));
        set(inventory, SAVE_SLOT, saveItem());
        set(inventory, CANCEL_SLOT, item(Material.BARRIER, ChatColor.RED + "Cancel"));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        Player player = click.getPlayer();
        if (newsEditService == null) {
            player.closeInventory();
            return;
        }
        int slot = click.getRawSlot();
        if (slot == MESSAGE_TYPE_SLOT) {
            newsEditService.toggleMessageType(player);
            open(player);
            return;
        }
        if (slot == MESSAGE_COLOR_SLOT) {
            newsEditService.openColorEditor(player);
            return;
        }
        if (slot == MESSAGE_TEXT_SLOT) {
            newsEditService.startEditMessageInput(player);
            return;
        }
        if (slot == SAVE_SLOT) {
            newsEditService.saveAndFinish(player);
            return;
        }
        if (slot == CANCEL_SLOT) {
            newsEditService.cancelAndFinish(player);
        }
    }

    private org.bukkit.inventory.ItemStack messageTypeItem(NewsEditService.EditSessionSnapshot snapshot) {
        String typeLabel = snapshot == null || snapshot.getMessageType() == null
                ? "FLASH"
                : snapshot.getMessageType().name();
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Set how this message animates.");
        lore.add(ChatColor.GOLD + "Selected Type: " + typeLabel);
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle!");
        return item(
                Material.PAPER,
                ChatColor.GREEN + "Message Type",
                lore
        );
    }

    private org.bukkit.inventory.ItemStack messageColorItem(NewsEditService.EditSessionSnapshot snapshot) {
        NewsEditService.MessageType type = snapshot == null || snapshot.getMessageType() == null
                ? NewsEditService.MessageType.FLASH
                : snapshot.getMessageType();
        List<String> lore = new ArrayList<String>();
        if (type == NewsEditService.MessageType.SWEEP) {
            lore.add(ChatColor.GRAY + "Edit start, sweep, and end colors.");
        } else {
            lore.add(ChatColor.GRAY + "Edit start, flash, and end colors.");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to edit colors!");
        return item(
                Material.SIGN,
                ChatColor.GREEN + "Message Colors",
                lore
        );
    }

    private org.bukkit.inventory.ItemStack messageTextItem(NewsEditService.EditSessionSnapshot snapshot) {
        String currentText = snapshot == null ? "" : safeText(snapshot.getText());
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + (currentText.isEmpty() ? "(empty)" : currentText));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to change!");
        return item(
                Material.PAPER,
                ChatColor.GREEN + "Message",
                lore
        );
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private org.bukkit.inventory.ItemStack saveItem() {
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.GRAY + "Save these edit settings.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to save!");
        return item(Material.BOOK, ChatColor.GREEN + "Save", lore);
    }
}
