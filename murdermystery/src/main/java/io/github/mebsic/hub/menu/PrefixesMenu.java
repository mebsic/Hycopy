package io.github.mebsic.hub.menu;

import io.github.mebsic.core.menu.Menu;
import io.github.mebsic.core.menu.MenuClick;
import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.PrefixCosmeticDefinition;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.PrefixCosmeticCatalog;
import io.github.mebsic.core.util.NetworkConstants;
import io.github.mebsic.murdermystery.stats.MurderMysteryStats;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;

public class PrefixesMenu extends Menu {
    public static final String TITLE = "Prefixes";
    private static final int SIZE = 36;
    private static final int PREFIX_ICONS_SLOT = 12;
    private static final int PREFIX_SCHEMES_SLOT = 14;
    private static final int BACK_SLOT = 30;
    private static final int TOKENS_SLOT = 31;

    private final CoreApi coreApi;
    private final KnifeSkinsMenu knifeSkinsMenu;
    private final PrefixOptionsMenu prefixIconsMenu;
    private final PrefixOptionsMenu prefixSchemesMenu;
    private final NumberFormat numberFormat;

    public PrefixesMenu(CoreApi coreApi, KnifeSkinsMenu knifeSkinsMenu) {
        super(TITLE, SIZE);
        this.coreApi = coreApi;
        this.knifeSkinsMenu = knifeSkinsMenu;
        this.prefixIconsMenu = new PrefixOptionsMenu(coreApi, knifeSkinsMenu, this, CosmeticType.PREFIX_ICON);
        this.prefixSchemesMenu = new PrefixOptionsMenu(coreApi, knifeSkinsMenu, this, CosmeticType.PREFIX_SCHEME);
        this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();

        Profile profile = player == null || coreApi == null ? null : coreApi.getProfile(player.getUniqueId());
        set(inventory, PREFIX_ICONS_SLOT, buildCategoryItem(profile, CosmeticType.PREFIX_ICON, Material.ITEM_FRAME));
        set(inventory, PREFIX_SCHEMES_SLOT, orangeDyeItem(buildCategoryLore(profile, CosmeticType.PREFIX_SCHEME)));
        set(inventory, BACK_SLOT, item(
                Material.ARROW,
                ChatColor.GREEN + "Go Back",
                ChatColor.GRAY + "To My Cosmetics"
        ));
        set(inventory, TOKENS_SLOT, item(
                Material.EMERALD,
                ChatColor.GRAY + "Total Tokens: " + ChatColor.DARK_GREEN + formatTokens(player),
                ChatColor.GOLD + NetworkConstants.storeUrl()
        ));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null) {
            return;
        }
        Player player = click.getPlayer();
        if (player == null) {
            return;
        }
        int slot = click.getRawSlot();
        if (slot == BACK_SLOT) {
            new KnifeMenu(coreApi, knifeSkinsMenu).open(player);
            return;
        }
        if (slot == PREFIX_ICONS_SLOT) {
            prefixIconsMenu.open(player);
            return;
        }
        if (slot == PREFIX_SCHEMES_SLOT) {
            prefixSchemesMenu.open(player);
        }
    }

    private ItemStack buildCategoryItem(Profile profile, CosmeticType type, Material material) {
        String title = type == CosmeticType.PREFIX_SCHEME ? "Prefix Schemes" : "Prefix Icons";
        return item(material, ChatColor.GREEN + title, buildCategoryLore(profile, type));
    }

    private java.util.List<String> buildCategoryLore(Profile profile, CosmeticType type) {
        java.util.List<String> lore = new java.util.ArrayList<String>();
        if (type == CosmeticType.PREFIX_SCHEME) {
            lore.add(ChatColor.GRAY + "Customize what color you want your");
            lore.add(ChatColor.GRAY + "Prefix to be " + ChatColor.DARK_GRAY + "(when enabled)" + ChatColor.GRAY + ".");
        } else {
            lore.add(ChatColor.GRAY + "Customize what icon you want to show");
            lore.add(ChatColor.GRAY + "up in your Prefix " + ChatColor.DARK_GRAY + "(when enabled)" + ChatColor.GRAY + ".");
        }
        lore.add("");
        int unlocked = countUnlocked(profile, type);
        int total = PrefixCosmeticCatalog.definitions(type).size();
        int percent = total <= 0 ? 0 : (unlocked * 100) / total;
        lore.add(ChatColor.GRAY + "Unlocked: " + ChatColor.GREEN + unlocked + "/" + total + " "
                + ChatColor.DARK_GRAY + "(" + percent + "%)");
        lore.add(ChatColor.GRAY + "Currently Selected:");
        lore.add(ChatColor.GREEN + selectedDisplay(profile, type));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to view!");
        return lore;
    }

    private int countUnlocked(Profile profile, CosmeticType type) {
        if (profile == null || !PrefixCosmeticCatalog.isPrefixType(type)) {
            return 0;
        }
        int count = 0;
        for (PrefixCosmeticDefinition definition : PrefixCosmeticCatalog.definitions(type)) {
            if (definition != null && containsId(profile.getUnlocked().get(type), definition.getId())) {
                count++;
            }
        }
        return count;
    }

    private String selectedDisplay(Profile profile, CosmeticType type) {
        if (profile == null || !PrefixCosmeticCatalog.isPrefixType(type)) {
            return "Default";
        }
        String selected = PrefixCosmeticCatalog.normalizeId(profile.getSelected().get(type));
        if (PrefixCosmeticCatalog.RANDOM_ID.equals(selected)) {
            return type == CosmeticType.PREFIX_SCHEME ? "Random Prefix Scheme" : "Random Prefix Icon";
        }
        if (PrefixCosmeticCatalog.RANDOM_FAVORITE_ID.equals(selected)) {
            return type == CosmeticType.PREFIX_SCHEME ? "Random Favorite Prefix Scheme" : "Random Favorite Prefix Icon";
        }
        PrefixCosmeticDefinition definition = PrefixCosmeticCatalog.definition(type, selected);
        if (definition != null) {
            return definition.getDisplayName();
        }
        PrefixCosmeticDefinition defaultDefinition = PrefixCosmeticCatalog.definition(type, PrefixCosmeticCatalog.defaultId(type));
        return defaultDefinition == null ? "Default" : defaultDefinition.getDisplayName();
    }

    private boolean containsId(java.util.Set<String> values, String id) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        String normalized = PrefixCosmeticCatalog.normalizeId(id);
        for (String value : values) {
            if (normalized.equals(PrefixCosmeticCatalog.normalizeId(value))) {
                return true;
            }
        }
        return false;
    }

    private String formatTokens(Player player) {
        if (player == null || coreApi == null) {
            return "0";
        }
        Profile profile = coreApi.getProfile(player.getUniqueId());
        int tokens = profile == null ? 0 : MurderMysteryStats.getTokens(profile.getStats());
        return formatNumber(tokens);
    }

    void sendNeedMoreWinsMessage(Player player, int winsNeeded) {
        if (player == null) {
            return;
        }
        player.sendMessage(ChatColor.RED + "You need " + ChatColor.GOLD + formatNumber(winsNeeded)
                + ChatColor.RED + " more wins!");
    }

    void sendSelectedMessage(Player player, String selectedName) {
        if (player == null) {
            return;
        }
        player.sendMessage(ChatColor.GOLD + "You selected " + ChatColor.GREEN + safeSelectedName(selectedName)
                + ChatColor.GOLD + "!");
    }

    private String formatNumber(int value) {
        return numberFormat.format(Math.max(0, value));
    }

    private String safeSelectedName(String selectedName) {
        if (selectedName == null || selectedName.trim().isEmpty()) {
            return "Prefix";
        }
        String plain = ChatColor.stripColor(selectedName.trim());
        if (plain == null || plain.trim().isEmpty()) {
            return "Prefix";
        }
        return plain.trim();
    }

    private ItemStack orangeDyeItem(java.util.List<String> lore) {
        ItemStack stack = item(Material.INK_SACK, ChatColor.GREEN + "Prefix Schemes", lore);
        if (stack != null) {
            stack.setDurability((short) 14);
        }
        return stack;
    }
}
