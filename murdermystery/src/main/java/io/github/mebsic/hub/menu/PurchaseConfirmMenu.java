package io.github.mebsic.hub.menu;

import io.github.mebsic.core.menu.Menu;
import io.github.mebsic.core.menu.MenuClick;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

public final class PurchaseConfirmMenu extends Menu {
    private static final int SIZE = 27;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final String cosmeticName;
    private final int tokenCost;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;

    public PurchaseConfirmMenu(String cosmeticName, int tokenCost,
                               Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        super("Confirm", SIZE);
        this.cosmeticName = cosmeticName;
        this.tokenCost = Math.max(0, tokenCost);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        String cost = NumberFormat.getIntegerInstance(Locale.US).format(tokenCost)
                + (tokenCost == 1 ? " Token" : " Tokens");
        set(inventory, CONFIRM_SLOT, clayButton((short) 13, ChatColor.GREEN + "Confirm",
                ChatColor.GRAY + "Unlock: " + ChatColor.GOLD + cosmeticName,
                ChatColor.GRAY + "Cost: " + ChatColor.DARK_GREEN + cost));
        set(inventory, CANCEL_SLOT, clayButton((short) 14, ChatColor.RED + "Cancel"));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        if (click.getRawSlot() == CANCEL_SLOT) {
            onCancel.accept(click.getPlayer());
        } else if (click.getRawSlot() == CONFIRM_SLOT) {
            onConfirm.accept(click.getPlayer());
        }
    }

    private ItemStack clayButton(short colorData, String name, String... lore) {
        Material terracotta = Material.matchMaterial("STAINED_CLAY");
        Material modern = colorData == 13 ? Material.matchMaterial("GREEN_TERRACOTTA") : Material.matchMaterial("RED_TERRACOTTA");
        Material material = modern != null ? modern : (terracotta == null ? Material.WOOL : terracotta);
        ItemStack stack = item(material, name, lore);
        if (terracotta != null && stack != null && stack.getType() == terracotta) {
            stack.setDurability(colorData);
        }
        return stack;
    }
}
