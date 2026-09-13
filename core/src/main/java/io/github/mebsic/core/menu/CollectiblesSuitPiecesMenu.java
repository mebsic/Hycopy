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

public class CollectiblesSuitPiecesMenu extends Menu {
    private static final int SIZE = 54;
    private static final int HELMET_SLOT = 4;
    private static final int CHESTPLATE_SLOT = 13;
    private static final int LEGGINGS_SLOT = 22;
    private static final int BOOTS_SLOT = 31;
    private static final int RESET_SLOT = 39;
    private static final int EQUIP_ALL_SLOT = 41;
    private static final int BACK_SLOT = 48;
    private static final int COLLECTIBLES_SLOT = 49;

    private final CoreApi coreApi;
    private final CollectiblesSuitsMenu parent;
    private final String suitId;
    private final NumberFormat numberFormat;
    private final Map<Integer, LobbyCosmeticDefinition> definitionBySlot;

    public CollectiblesSuitPiecesMenu(CoreApi coreApi, CollectiblesSuitsMenu parent, String suitId) {
        super(resolveTitle(suitId), SIZE);
        this.coreApi = coreApi;
        this.parent = parent;
        this.suitId = LobbyCosmeticCatalog.normalizeId(suitId);
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
        List<LobbyCosmeticDefinition> pieces = LobbyCosmeticCatalog.suitPieces(suitId);
        for (LobbyCosmeticDefinition piece : pieces) {
            int slot = slotFor(piece);
            if (slot < 0) {
                continue;
            }
            definitionBySlot.put(slot, piece);
            set(inventory, slot, pieceItem(profile, piece));
        }

        set(inventory, RESET_SLOT, resetItem());
        set(inventory, EQUIP_ALL_SLOT, equipAllItem());
        set(inventory, BACK_SLOT, item(Material.ARROW, ChatColor.GREEN + "Go Back", ChatColor.GRAY + "To Suits"));
        set(inventory, COLLECTIBLES_SLOT, item(
                Material.CHEST,
                ChatColor.GREEN + "Collectibles",
                CollectiblesMenu.collectiblesLore(CollectiblesCosmeticSupport.formatDust(profile, numberFormat))
        ));
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
        if (slot == COLLECTIBLES_SLOT) {
            return;
        }
        if (slot == RESET_SLOT) {
            resetSuit(player);
            return;
        }
        if (slot == EQUIP_ALL_SLOT) {
            equipAllUnlocked(player);
            return;
        }
        LobbyCosmeticDefinition definition = definitionBySlot.get(slot);
        if (definition != null) {
            handlePieceClick(player, definition);
        }
    }

    private ItemStack pieceItem(Profile profile, LobbyCosmeticDefinition definition) {
        if (definition == null) {
            return null;
        }
        boolean unlocked = CollectiblesCosmeticSupport.isUnlocked(profile, definition.getType(), definition.getId());
        boolean selected = CollectiblesCosmeticSupport.isSelected(profile, definition.getType(), definition.getId());
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(CollectiblesCosmeticSupport.actionLore(profile, definition));

        ChatColor nameColor = unlocked ? definition.getDisplayColor() : ChatColor.RED;
        ItemStack stack = item(definition.getMaterial(), nameColor + definition.getDisplayName(), lore);
        if (stack != null) {
            stack.setDurability(definition.getDurability());
            CollectiblesCosmeticSupport.applyLeatherColor(stack, definition.getLeatherColor());
        }
        return selected ? GiftSupport.addGlow(stack) : stack;
    }

    private ItemStack resetItem() {
        return item(
                Material.BARRIER,
                ChatColor.RED + "Reset Suit"
        );
    }

    private ItemStack equipAllItem() {
        LobbyCosmeticDefinition suit = LobbyCosmeticCatalog.suit(suitId);
        String suitName = suit == null ? "Suit" : suit.getDisplayName();
        return item(
                Material.LEATHER,
                ChatColor.GREEN + "Equip Entire Suit",
                ChatColor.GRAY + "Equip all pieces of the " + suitName + " that",
                ChatColor.GRAY + "you have unlocked."
        );
    }

    private void handlePieceClick(Player player, LobbyCosmeticDefinition definition) {
        if (player == null || coreApi == null || definition == null) {
            return;
        }
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return;
        }
        if (CollectiblesCosmeticSupport.isUnlocked(profile, definition.getType(), definition.getId())) {
            if (CollectiblesCosmeticSupport.isSelected(profile, definition.getType(), definition.getId())) {
                if (coreApi.resetCosmetic(player.getUniqueId(), definition.getType())) {
                    player.sendMessage(ChatColor.GREEN + "Reset your "
                            + CollectiblesCosmeticSupport.coloredDisplayName(definition));
                    open(player);
                }
                return;
            }
            if (coreApi.selectCosmetic(player.getUniqueId(), definition.getType(), definition.getId())) {
                player.sendMessage(CollectiblesCosmeticSupport.selectedMessage(definition));
                open(player);
            }
            return;
        }
        if (!CollectiblesCosmeticSupport.hasEnoughMysteryDust(profile, definition.getCost())) {
            player.sendMessage(CollectiblesCosmeticSupport.missingMysteryDustLore(profile, definition.getCost()));
            return;
        }
        new CollectiblesCosmeticConfirmMenu(coreApi, this, definition).open(player);
    }

    private void equipAllUnlocked(Player player) {
        if (player == null || coreApi == null) {
            return;
        }
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            player.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return;
        }
        boolean unlockedAny = false;
        boolean changed = false;
        int equippedPieces = 0;
        for (LobbyCosmeticDefinition piece : LobbyCosmeticCatalog.suitPieces(suitId)) {
            if (piece == null || !CollectiblesCosmeticSupport.isUnlocked(profile, piece.getType(), piece.getId())) {
                continue;
            }
            unlockedAny = true;
            equippedPieces++;
            if (CollectiblesCosmeticSupport.isSelected(profile, piece.getType(), piece.getId())) {
                continue;
            }
            if (coreApi.selectCosmetic(player.getUniqueId(), piece.getType(), piece.getId())) {
                changed = true;
            }
        }
        LobbyCosmeticDefinition suit = LobbyCosmeticCatalog.suit(suitId);
        if (!unlockedAny) {
            player.sendMessage(ChatColor.RED + "You haven't unlocked any pieces of "
                    + CollectiblesCosmeticSupport.coloredDisplayName(suit)
                    + ChatColor.RED + " yet!");
            return;
        }
        if (!changed) {
            player.sendMessage(ChatColor.RED + "You already have all unlocked pieces of "
                    + CollectiblesCosmeticSupport.coloredDisplayName(suit)
                    + ChatColor.RED + " selected!");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "You equipped " + equippedPieces + " "
                + pieceLabel(equippedPieces) + " of the "
                + CollectiblesCosmeticSupport.coloredDisplayName(suit));
        open(player);
    }

    private String pieceLabel(int count) {
        return count == 1 ? "piece" : "pieces";
    }

    private void resetSuit(Player player) {
        if (player == null || coreApi == null) {
            return;
        }
        if (coreApi.resetCosmetic(player.getUniqueId(), CosmeticType.SUIT)) {
            player.sendMessage(ChatColor.GREEN + "Reset your " + ChatColor.YELLOW + "Suit");
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

    private int slotFor(LobbyCosmeticDefinition definition) {
        if (definition == null) {
            return -1;
        }
        CosmeticType type = definition.getType();
        if (type == CosmeticType.SUIT_HELMET) {
            return HELMET_SLOT;
        }
        if (type == CosmeticType.SUIT_CHESTPLATE) {
            return CHESTPLATE_SLOT;
        }
        if (type == CosmeticType.SUIT_LEGGINGS) {
            return LEGGINGS_SLOT;
        }
        if (type == CosmeticType.SUIT_BOOTS) {
            return BOOTS_SLOT;
        }
        return -1;
    }

    private static String resolveTitle(String suitId) {
        LobbyCosmeticDefinition suit = LobbyCosmeticCatalog.suit(suitId);
        if (suit == null || suit.getDisplayName().trim().isEmpty()) {
            return "Suit";
        }
        return suit.getDisplayName();
    }
}
