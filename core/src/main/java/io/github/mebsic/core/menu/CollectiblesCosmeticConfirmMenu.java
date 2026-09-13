package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.LobbyCosmeticDefinition;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CollectiblesCosmeticConfirmMenu extends Menu {
    private static final String TITLE = "Confirm";
    private static final int SIZE = 27;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final CoreApi coreApi;
    private final Menu previousMenu;
    private final LobbyCosmeticDefinition definition;

    public CollectiblesCosmeticConfirmMenu(CoreApi coreApi,
                                           Menu previousMenu,
                                           LobbyCosmeticDefinition definition) {
        super(TITLE, SIZE);
        this.coreApi = coreApi;
        this.previousMenu = previousMenu;
        this.definition = definition;
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        set(inventory, CONFIRM_SLOT, clayButton(true, ChatColor.GREEN + "Confirm",
                ChatColor.GRAY + "Purchase: " + CollectiblesCosmeticSupport.coloredDisplayName(definition),
                ChatColor.GRAY + "Cost: " + ChatColor.AQUA + CollectiblesCosmeticSupport.formatDust(cost())
                        + ChatColor.GRAY + " Mystery Dust"));
        set(inventory, CANCEL_SLOT, clayButton(false, ChatColor.RED + "Cancel"));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        Player player = click.getPlayer();
        int slot = click.getRawSlot();
        if (slot == CANCEL_SLOT) {
            openPreviousMenu(player);
            return;
        }
        if (slot != CONFIRM_SLOT) {
            return;
        }
        confirmPurchase(player);
    }

    private void confirmPurchase(Player player) {
        if (player == null || coreApi == null || definition == null) {
            return;
        }
        CosmeticType type = definition.getType();
        String id = definition.getId();
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return;
        }
        if (!CollectiblesCosmeticSupport.isUnlocked(profile, type, id)) {
            int currentDust = Math.max(0, profile.getMysteryDust());
            int cost = cost();
            if (currentDust < cost) {
                player.closeInventory();
                player.sendMessage(CollectiblesCosmeticSupport.missingMysteryDustLore(profile, cost));
                return;
            }
            if (!coreApi.unlockCosmetic(player.getUniqueId(), type, id)) {
                openPreviousMenu(player);
                return;
            }
            coreApi.setMysteryDust(player.getUniqueId(), currentDust - cost);
            player.sendMessage(CollectiblesCosmeticSupport.purchaseMessage(definition));
        }
        boolean selected = coreApi.selectCosmetic(player.getUniqueId(), type, id);
        if (selected) {
            player.sendMessage(CollectiblesCosmeticSupport.selectedMessage(definition));
        }
        if (selected && type == CosmeticType.GADGET) {
            player.closeInventory();
            return;
        }
        openPreviousMenu(player);
    }

    private void openPreviousMenu(Player player) {
        if (player == null) {
            return;
        }
        if (previousMenu == null) {
            player.closeInventory();
            return;
        }
        previousMenu.open(player);
    }

    private int cost() {
        return definition == null ? 0 : Math.max(0, definition.getCost());
    }

    private ItemStack clayButton(boolean green, String name, String... lore) {
        ItemStack stack = item(Material.STAINED_CLAY, name, lore);
        if (stack != null) {
            stack.setDurability((short) (green ? 13 : 14));
        }
        return stack;
    }
}
