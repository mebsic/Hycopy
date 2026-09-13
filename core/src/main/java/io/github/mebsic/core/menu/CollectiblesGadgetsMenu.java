package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.LobbyCosmeticCatalog;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.text.NumberFormat;
import java.util.Locale;

public class CollectiblesGadgetsMenu extends Menu {
    public static final String TITLE = "Gadgets";
    private static final int SIZE = 45;
    private static final int MOVEMENT_SLOT = 13;
    private static final int BACK_SLOT = 39;
    private static final int COLLECTIBLES_SLOT = 40;
    private static final int RESET_SLOT = 31;

    private final CoreApi coreApi;
    private final CollectiblesMenu parent;
    private final CollectiblesMovementMenu movementMenu;
    private final NumberFormat numberFormat;

    public CollectiblesGadgetsMenu(CoreApi coreApi, CollectiblesMenu parent) {
        super(TITLE, SIZE);
        this.coreApi = coreApi;
        this.parent = parent;
        this.movementMenu = new CollectiblesMovementMenu(coreApi, this);
        this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        Profile profile = player == null || coreApi == null ? null : coreApi.getProfile(player.getUniqueId());
        set(inventory, MOVEMENT_SLOT, item(
                Material.GOLD_BOOTS,
                ChatColor.YELLOW + "Movement",
                movementLore(profile)
        ));
        set(inventory, BACK_SLOT, item(Material.ARROW, ChatColor.GREEN + "Go Back", ChatColor.GRAY + "To Collectibles"));
        set(inventory, COLLECTIBLES_SLOT, item(
                Material.CHEST,
                ChatColor.GREEN + "Collectibles",
                CollectiblesMenu.collectiblesLore(CollectiblesCosmeticSupport.formatDust(profile, numberFormat))
        ));
        set(inventory, RESET_SLOT, resetItem());
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        Player player = click.getPlayer();
        int slot = click.getRawSlot();
        if (slot == BACK_SLOT) {
            openParent(player);
            return;
        }
        if (slot == MOVEMENT_SLOT && movementMenu != null) {
            movementMenu.open(player);
            return;
        }
        if (slot == RESET_SLOT) {
            resetGadget(player);
        }
    }

    private java.util.List<String> movementLore(Profile profile) {
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(ChatColor.GRAY + "Change the way you walk, run or fly");
        lore.add(ChatColor.GRAY + "around lobbies with these");
        lore.add(ChatColor.GRAY + "movement-altering gadgets!");
        lore.add("");
        lore.add(CollectiblesCosmeticSupport.unlockedLore(
                profile,
                CosmeticType.GADGET,
                CollectiblesCosmeticSupport.definitions(coreApi, CosmeticType.GADGET, LobbyCosmeticCatalog.MOVEMENT_CATEGORY),
                ChatColor.GREEN
        ));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to browse!");
        return lore;
    }

    private void openParent(Player player) {
        if (player == null) {
            return;
        }
        if (parent == null) {
            player.closeInventory();
            return;
        }
        parent.open(player);
    }

    private org.bukkit.inventory.ItemStack resetItem() {
        return item(
                Material.BARRIER,
                ChatColor.RED + "Reset Gadget"
        );
    }

    private void resetGadget(Player player) {
        if (player == null || coreApi == null) {
            return;
        }
        if (coreApi.resetCosmetic(player.getUniqueId(), CosmeticType.GADGET)) {
            player.sendMessage(ChatColor.GREEN + "Reset your " + ChatColor.YELLOW + "Gadget");
            player.closeInventory();
        }
    }
}
