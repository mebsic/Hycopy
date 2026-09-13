package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.LobbyCosmeticCatalog;
import io.github.mebsic.core.service.LobbyCosmeticDefinition;
import io.github.mebsic.core.util.CommonMessages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CollectiblesMovementMenu extends Menu {
    public static final String TITLE = "Movement";
    private static final int SIZE = 45;
    private static final int[] COSMETIC_SLOTS = new int[]{12, 14};
    private static final int BACK_SLOT = 39;
    private static final int COLLECTIBLES_SLOT = 40;
    private static final int RESET_SLOT = 31;

    private final CoreApi coreApi;
    private final CollectiblesGadgetsMenu parent;
    private final NumberFormat numberFormat;
    private final Map<Integer, LobbyCosmeticDefinition> definitionBySlot;

    public CollectiblesMovementMenu(CoreApi coreApi, CollectiblesGadgetsMenu parent) {
        super(TITLE, SIZE);
        this.coreApi = coreApi;
        this.parent = parent;
        this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
        this.definitionBySlot = new HashMap<Integer, LobbyCosmeticDefinition>();
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        definitionBySlot.clear();
        Profile profile = player == null || coreApi == null ? null : coreApi.getProfile(player.getUniqueId());
        List<LobbyCosmeticDefinition> definitions = CollectiblesCosmeticSupport.definitions(
                coreApi,
                CosmeticType.GADGET,
                LobbyCosmeticCatalog.MOVEMENT_CATEGORY
        );
        for (int i = 0; i < definitions.size() && i < COSMETIC_SLOTS.length; i++) {
            int slot = COSMETIC_SLOTS[i];
            LobbyCosmeticDefinition definition = definitions.get(i);
            definitionBySlot.put(slot, definition);
            set(inventory, slot, cosmeticItem(profile, definition));
        }
        set(inventory, BACK_SLOT, item(Material.ARROW, ChatColor.GREEN + "Go Back", ChatColor.GRAY + "To Gadgets"));
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
        if (click.getRawSlot() == BACK_SLOT) {
            openParent(click.getPlayer());
            return;
        }
        if (click.getRawSlot() == COLLECTIBLES_SLOT) {
            return;
        }
        if (click.getRawSlot() == RESET_SLOT) {
            resetGadget(click.getPlayer());
            return;
        }
        LobbyCosmeticDefinition definition = definitionBySlot.get(click.getRawSlot());
        if (definition != null) {
            handleCosmeticClick(click.getPlayer(), definition);
        }
    }

    private ItemStack cosmeticItem(Profile profile, LobbyCosmeticDefinition definition) {
        if (definition == null) {
            return null;
        }
        boolean unlocked = CollectiblesCosmeticSupport.isUnlocked(profile, definition.getType(), definition.getId());
        boolean selected = CollectiblesCosmeticSupport.isSelected(profile, definition.getType(), definition.getId());
        java.util.List<String> lore = new java.util.ArrayList<String>();
        if (!definition.getDescription().isEmpty()) {
            CollectiblesCosmeticSupport.appendDescriptionLore(lore, definition, ChatColor.GRAY);
            lore.add("");
        }
        lore.add(CollectiblesCosmeticSupport.actionLore(profile, definition));
        ChatColor nameColor = unlocked ? definition.getDisplayColor() : ChatColor.RED;
        ItemStack stack = item(definition.getMaterial(), nameColor + definition.getDisplayName(), lore);
        if (stack != null) {
            stack.setDurability(definition.getDurability());
        }
        return selected ? GiftSupport.addGlow(stack) : stack;
    }

    private void handleCosmeticClick(Player player, LobbyCosmeticDefinition definition) {
        if (player == null || coreApi == null || definition == null) {
            return;
        }
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return;
        }
        boolean unlocked = CollectiblesCosmeticSupport.isUnlocked(profile, definition.getType(), definition.getId());
        if (unlocked) {
            if (CollectiblesCosmeticSupport.isSelected(profile, definition.getType(), definition.getId())) {
                player.sendMessage(CollectiblesCosmeticSupport.alreadySelectedMessage(definition));
                return;
            }
            if (coreApi.selectCosmetic(player.getUniqueId(), definition.getType(), definition.getId())) {
                player.sendMessage(CollectiblesCosmeticSupport.selectedMessage(definition));
                player.closeInventory();
            }
            return;
        }
        if (!CollectiblesCosmeticSupport.hasEnoughMysteryDust(profile, definition.getCost())) {
            player.sendMessage(CollectiblesCosmeticSupport.missingMysteryDustLore(profile, definition.getCost()));
            return;
        }
        new CollectiblesCosmeticConfirmMenu(coreApi, this, definition).open(player);
    }

    private ItemStack resetItem() {
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
}
