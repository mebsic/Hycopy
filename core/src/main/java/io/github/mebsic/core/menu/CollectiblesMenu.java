package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.service.CoreApi;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.text.NumberFormat;
import java.util.Locale;

public class CollectiblesMenu extends Menu {
    public static final String TITLE = "Collectibles";
    private static final int SIZE = 36;
    private static final int RANKS_SLOT = 11;
    private static final int SUITS_SLOT = 13;
    private static final int GADGETS_SLOT = 15;
    private static final int COLLECTIBLES_SLOT = 31;

    private final CoreApi coreApi;
    private final CollectiblesRanksMenu ranksMenu;
    private final CollectiblesSuitsMenu suitsMenu;
    private final CollectiblesGadgetsMenu gadgetsMenu;
    private final NumberFormat numberFormat;

    public CollectiblesMenu(CoreApi coreApi) {
        super(TITLE, SIZE);
        this.coreApi = coreApi;
        this.ranksMenu = new CollectiblesRanksMenu(coreApi, this);
        this.suitsMenu = new CollectiblesSuitsMenu(coreApi, this);
        this.gadgetsMenu = new CollectiblesGadgetsMenu(coreApi, this);
        this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        Profile profile = player == null || coreApi == null ? null : coreApi.getProfile(player.getUniqueId());
        set(inventory, RANKS_SLOT, item(
                Material.SIGN,
                ChatColor.GREEN + "Ranks",
                ranksLore(player)
        ));
        set(inventory, SUITS_SLOT, item(
                Material.GOLD_LEGGINGS,
                ChatColor.GREEN + "Suits",
                suitsLore(profile)
        ));
        set(inventory, GADGETS_SLOT, item(
                Material.PISTON_BASE,
                ChatColor.GREEN + "Gadgets",
                gadgetsLore(profile)
        ));
        set(inventory, COLLECTIBLES_SLOT, item(
                Material.CHEST,
                ChatColor.GREEN + "Collectibles",
                collectiblesLore(formatMysteryDust(player))
        ));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null || click.getPlayer() == null) {
            return;
        }
        if (click.getRawSlot() == RANKS_SLOT) {
            ranksMenu.open(click.getPlayer());
            return;
        }
        if (click.getRawSlot() == SUITS_SLOT) {
            suitsMenu.open(click.getPlayer());
            return;
        }
        if (click.getRawSlot() == GADGETS_SLOT) {
            gadgetsMenu.open(click.getPlayer());
        }
    }

    private java.util.List<String> ranksLore(Player player) {
        java.util.List<String> lore = new java.util.ArrayList<String>();
        int total = resolveRankTotal();
        int unlocked = resolveRankUnlocked(player);
        int percent = total <= 0 ? 0 : (unlocked * 100) / total;
        lore.add(ChatColor.GRAY + "You can select a rank and change it here!");
        lore.add(ChatColor.GRAY + "Ranks display in lobbies and games.");
        lore.add("");
        lore.add(ChatColor.GRAY + "Unlocked: " + ChatColor.YELLOW + unlocked + "/" + total + " "
                + ChatColor.DARK_GRAY + "(" + percent + "%)");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to browse!");
        return lore;
    }

    private java.util.List<String> suitsLore(Profile profile) {
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(ChatColor.GRAY + "Collect and wear all the pieces from");
        lore.add(ChatColor.GRAY + "a specific suit while in a lobby to");
        lore.add(ChatColor.GRAY + "gain unique effects!");
        lore.add("");
        lore.add(CollectiblesCosmeticSupport.unlockedLore(profile, coreApi, CosmeticType.SUIT, ChatColor.YELLOW));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to browse!");
        return lore;
    }

    private java.util.List<String> gadgetsLore(Profile profile) {
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(ChatColor.GRAY + "Ever wanted to make your friends");
        lore.add(ChatColor.GRAY + "jealous by making a trampoline");
        lore.add(ChatColor.GRAY + "appear or just feel like throwing a");
        lore.add(ChatColor.GRAY + "poop bomb at them? Then Gadgets");
        lore.add(ChatColor.GRAY + "are for you! These fun little toys");
        lore.add(ChatColor.GRAY + "can be used while in a lobby.");
        lore.add("");
        lore.add(CollectiblesCosmeticSupport.unlockedLore(profile, coreApi, CosmeticType.GADGET, ChatColor.YELLOW));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to browse!");
        return lore;
    }

    public static java.util.List<String> collectiblesLore(String mysteryDust) {
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(ChatColor.GRAY + "Mystery Dust: " + ChatColor.AQUA + safeAmount(mysteryDust));
        lore.add("");
        lore.add(ChatColor.GRAY + "Collect fun cosmetic items! Unlock new items");
        lore.add(ChatColor.GRAY + "using " + ChatColor.AQUA + "Mystery Dust"
                + ChatColor.GRAY + " or hitting milestone");
        lore.add(ChatColor.GRAY + "rewards.");
        lore.add("");
        lore.add(ChatColor.AQUA + "Mystery Dust" + ChatColor.GRAY + " is randomly given after playing");
        lore.add(ChatColor.GRAY + "games.");
        return lore;
    }

    private String formatMysteryDust(Player player) {
        if (player == null || coreApi == null) {
            return "0";
        }
        return numberFormat.format(Math.max(0, coreApi.getMysteryDust(player.getUniqueId())));
    }

    private int resolveRankTotal() {
        return CollectiblesRankSupport.rankOptions().size();
    }

    private int resolveRankUnlocked(Player player) {
        if (player == null || coreApi == null) {
            return 0;
        }
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            return 0;
        }
        int count = 0;
        for (Rank rank : CollectiblesRankSupport.rankOptions()) {
            if (CollectiblesRankSupport.isUnlocked(profile, rank)) {
                count++;
            }
        }
        return count;
    }

    private static String safeAmount(String mysteryDust) {
        if (mysteryDust == null || mysteryDust.trim().isEmpty()) {
            return "0";
        }
        return mysteryDust;
    }
}
