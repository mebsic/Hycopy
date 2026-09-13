package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.LobbyCosmeticCatalog;
import io.github.mebsic.core.service.LobbyCosmeticDefinition;
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

public class CollectiblesSuitsMenu extends Menu {
    public static final String TITLE = "Suits";
    private static final int SIZE = 45;
    private static final int[] COSMETIC_SLOTS = new int[]{12, 14};
    private static final int BACK_SLOT = 39;
    private static final int COLLECTIBLES_SLOT = 40;
    private static final int RESET_SLOT = 31;

    private final CoreApi coreApi;
    private final CollectiblesMenu parent;
    private final NumberFormat numberFormat;
    private final Map<Integer, LobbyCosmeticDefinition> definitionBySlot;

    public CollectiblesSuitsMenu(CoreApi coreApi, CollectiblesMenu parent) {
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
        List<LobbyCosmeticDefinition> definitions = LobbyCosmeticCatalog.suits();
        for (int i = 0; i < definitions.size() && i < COSMETIC_SLOTS.length; i++) {
            int slot = COSMETIC_SLOTS[i];
            LobbyCosmeticDefinition definition = definitions.get(i);
            definitionBySlot.put(slot, definition);
            set(inventory, slot, cosmeticItem(profile, definition));
        }
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
        if (click.getRawSlot() == BACK_SLOT) {
            openParent(click.getPlayer());
            return;
        }
        if (click.getRawSlot() == COLLECTIBLES_SLOT) {
            return;
        }
        if (click.getRawSlot() == RESET_SLOT) {
            resetSuit(click.getPlayer());
            return;
        }
        LobbyCosmeticDefinition definition = definitionBySlot.get(click.getRawSlot());
        if (definition != null) {
            new CollectiblesSuitPiecesMenu(coreApi, this, definition.getId()).open(click.getPlayer());
        }
    }

    private ItemStack cosmeticItem(Profile profile, LobbyCosmeticDefinition definition) {
        if (definition == null) {
            return null;
        }
        List<LobbyCosmeticDefinition> pieces = LobbyCosmeticCatalog.suitPieces(definition.getId());
        int unlockedPieces = CollectiblesCosmeticSupport.countUnlocked(profile, pieces);
        int totalPieces = pieces.size();
        java.util.List<String> lore = new java.util.ArrayList<String>();
        if (!definition.getDescription().isEmpty()) {
            CollectiblesCosmeticSupport.appendDescriptionLore(lore, definition, ChatColor.GREEN);
            lore.add("");
        }
        lore.add(ChatColor.YELLOW + "Click to customize outfit!");
        ItemStack stack = item(
                definition.getMaterial(),
                definition.getDisplayColor() + definition.getDisplayName()
                        + " " + ChatColor.YELLOW + "(" + unlockedPieces + "/" + totalPieces + ")",
                lore
        );
        if (stack != null) {
            stack.setDurability(definition.getDurability());
            CollectiblesCosmeticSupport.applyLeatherColor(stack, definition.getLeatherColor());
        }
        return stack;
    }

    private ItemStack resetItem() {
        return item(
                Material.BARRIER,
                ChatColor.RED + "Reset Suit"
        );
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
}
