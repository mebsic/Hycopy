package io.github.mebsic.hub.menu;

import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.menu.Menu;
import io.github.mebsic.core.menu.MenuClick;
import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.PrefixCosmeticDefinition;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.PrefixCosmeticCatalog;
import io.github.mebsic.core.util.NetworkConstants;
import io.github.mebsic.core.util.RankFormatUtil;
import io.github.mebsic.murdermystery.stats.MurderMysteryStats;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PrefixOptionsMenu extends Menu {
    private static final ChatColor[] CHROMA_COLORS = new ChatColor[]{
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE
    };
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 48;
    private static final int TOKENS_SLOT = 49;
    private static final int[] COSMETIC_SLOTS = new int[]{
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final CoreApi coreApi;
    private final KnifeSkinsMenu knifeSkinsMenu;
    private final PrefixesMenu parentMenu;
    private final CosmeticType type;
    private final NumberFormat numberFormat;

    public PrefixOptionsMenu(CoreApi coreApi, KnifeSkinsMenu knifeSkinsMenu, PrefixesMenu parentMenu, CosmeticType type) {
        super(resolveTitle(type), SIZE);
        this.coreApi = coreApi;
        this.knifeSkinsMenu = knifeSkinsMenu;
        this.parentMenu = parentMenu;
        this.type = PrefixCosmeticCatalog.isPrefixType(type) ? type : CosmeticType.PREFIX_ICON;
        this.numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (inventory == null || player == null || coreApi == null) {
            return;
        }
        inventory.clear();
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            return;
        }
        int wins = Math.max(0, coreApi.getCounter(player.getUniqueId(), MongoManager.MURDER_MYSTERY_LIFETIME_WINS_KEY));
        List<PrefixEntry> entries = buildEntries(profile, wins);
        for (int i = 0; i < entries.size() && i < COSMETIC_SLOTS.length; i++) {
            set(inventory, COSMETIC_SLOTS[i], buildEntryItem(entries.get(i), profile, wins));
        }
        set(inventory, BACK_SLOT, item(Material.ARROW, ChatColor.GREEN + "Go Back", ChatColor.GRAY + "To Prefixes"));
        set(inventory, TOKENS_SLOT, item(
                Material.EMERALD,
                ChatColor.GRAY + "Total Tokens: " + ChatColor.DARK_GREEN + formatTokens(profile),
                ChatColor.GOLD + NetworkConstants.storeUrl()
        ));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null) {
            return;
        }
        Player player = click.getPlayer();
        if (player == null || coreApi == null) {
            return;
        }
        int slot = click.getRawSlot();
        if (slot == BACK_SLOT) {
            new PrefixesMenu(coreApi, knifeSkinsMenu).open(player);
            return;
        }
        Profile profile = coreApi.getProfile(player.getUniqueId());
        if (profile == null) {
            return;
        }
        int wins = Math.max(0, coreApi.getCounter(player.getUniqueId(), MongoManager.MURDER_MYSTERY_LIFETIME_WINS_KEY));
        PrefixEntry entry = entryForSlot(slot, buildEntries(profile, wins));
        if (entry == null) {
            return;
        }
        if (entry.specialId != null) {
            coreApi.selectCosmetic(player.getUniqueId(), type, entry.specialId);
            parentMenu.sendSelectedMessage(player, entry.displayName);
            new PrefixOptionsMenu(coreApi, knifeSkinsMenu, parentMenu, type).open(player);
            return;
        }
        PrefixCosmeticDefinition definition = entry.definition;
        if (definition == null) {
            return;
        }
        if (wins < definition.getRequiredWins()) {
            parentMenu.sendNeedMoreWinsMessage(player, definition.getRequiredWins() - wins);
            return;
        }
        if (!isUnlocked(profile, definition.getId())) {
            coreApi.unlockCosmetic(player.getUniqueId(), type, definition.getId());
            profile = coreApi.getProfile(player.getUniqueId());
            if (profile == null) {
                return;
            }
        }
        if (click.isShiftClick() && !isNoneSchemeDefinition(definition)) {
            if (!coreApi.toggleFavoriteCosmetic(player.getUniqueId(), type, definition.getId())) {
                return;
            }
            boolean favoriteNow = coreApi.isFavoriteCosmetic(player.getUniqueId(), type, definition.getId());
            if (favoriteNow) {
                player.sendMessage(ChatColor.YELLOW + "Added " + ChatColor.GOLD + "✯ " + definition.getDisplayName()
                        + ChatColor.YELLOW + " to your favorites!");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Removed " + ChatColor.GREEN + definition.getDisplayName()
                        + ChatColor.YELLOW + " from your favorites!");
            }
            new PrefixOptionsMenu(coreApi, knifeSkinsMenu, parentMenu, type).open(player);
            return;
        }
        coreApi.selectCosmetic(player.getUniqueId(), type, definition.getId());
        parentMenu.sendSelectedMessage(player, definition.getDisplayName());
        new PrefixOptionsMenu(coreApi, knifeSkinsMenu, parentMenu, type).open(player);
    }

    private List<PrefixEntry> buildEntries(Profile profile, int wins) {
        List<PrefixEntry> entries = new ArrayList<PrefixEntry>();
        PrefixCosmeticDefinition defaultDefinition =
                PrefixCosmeticCatalog.definition(type, PrefixCosmeticCatalog.defaultId(type));
        if (defaultDefinition != null) {
            entries.add(new PrefixEntry(defaultDefinition));
        }
        entries.add(new PrefixEntry(PrefixCosmeticCatalog.RANDOM_ID, randomDisplayName()));
        entries.add(new PrefixEntry(PrefixCosmeticCatalog.RANDOM_FAVORITE_ID, randomFavoriteDisplayName()));
        for (PrefixCosmeticDefinition definition : PrefixCosmeticCatalog.definitions(type)) {
            if (definition == null) {
                continue;
            }
            if (defaultDefinition != null
                    && PrefixCosmeticCatalog.normalizeId(defaultDefinition.getId())
                    .equals(PrefixCosmeticCatalog.normalizeId(definition.getId()))) {
                continue;
            }
            entries.add(new PrefixEntry(definition));
        }
        return entries;
    }

    private ItemStack buildEntryItem(PrefixEntry entry, Profile profile, int wins) {
        if (entry == null) {
            return null;
        }
        if (entry.specialId != null) {
            boolean selected = PrefixCosmeticCatalog.normalizeId(entry.specialId)
                    .equals(PrefixCosmeticCatalog.normalizeId(profile.getSelected().get(type)));
            Material material = PrefixCosmeticCatalog.RANDOM_FAVORITE_ID.equals(entry.specialId)
                    ? Material.ENDER_CHEST
                    : Material.CHEST;
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.DARK_GRAY + typeLabel());
            lore.add("");
            lore.add(randomSpecialLore(entry.specialId));
            lore.add("");
            lore.add(selected ? ChatColor.GREEN + "SELECTED!" : ChatColor.YELLOW + "Click to select!");
            return item(material, ChatColor.GREEN + entry.displayName, lore);
        }
        PrefixCosmeticDefinition definition = entry.definition;
        if (definition == null) {
            return null;
        }
        boolean unlocked = wins >= definition.getRequiredWins() && isUnlocked(profile, definition.getId());
        boolean selected = PrefixCosmeticCatalog.normalizeId(definition.getId())
                .equals(PrefixCosmeticCatalog.normalizeId(profile.getSelected().get(type)));
        boolean favorite = isFavorite(profile, definition.getId());
        String itemName = ChatColor.GREEN + definition.getDisplayName();
        if (!unlocked) {
            itemName = ChatColor.RED + definition.getDisplayName();
        }
        if (favorite && unlocked && !isNoneSchemeDefinition(definition)) {
            itemName = ChatColor.GOLD + "✯ " + ChatColor.GREEN + definition.getDisplayName();
        }
        List<String> lore = new ArrayList<String>();
        lore.add(ChatColor.DARK_GRAY + typeLabel());
        lore.add("");
        if (isNoneSchemeDefinition(definition)) {
            lore.add(ChatColor.GRAY + "Remove any Prefix Scheme displaying");
            lore.add(ChatColor.GRAY + "next to your name.");
            lore.add(selected ? ChatColor.GREEN + "SELECTED!" : ChatColor.YELLOW + "Click to select!");
            return definitionItem(definition, itemName, lore);
        }
        if (type != CosmeticType.PREFIX_SCHEME) {
            lore.add(ChatColor.GRAY + "Selects the " + definition.getDisplayName() + " " + typeLabel() + ".");
            lore.add("");
        }
        lore.add(ChatColor.GRAY + "Preview: " + buildPreview(profile, definition));
        lore.add("");
        if (unlocked) {
            if (favorite) {
                lore.add(ChatColor.GOLD + "✯ Favorite");
            }
            lore.add(selected ? ChatColor.GREEN + "SELECTED!" : ChatColor.YELLOW + "Click to select!");
            lore.add(ChatColor.YELLOW + "Shift-click to toggle favorite!");
        } else {
            lore.add(ChatColor.RED + "You need " + ChatColor.GOLD
                    + formatNumber(definition.getRequiredWins() - wins) + ChatColor.RED + " more wins!");
        }
        return definitionItem(definition, itemName, lore);
    }

    private PrefixEntry entryForSlot(int slot, List<PrefixEntry> entries) {
        int index = -1;
        for (int i = 0; i < COSMETIC_SLOTS.length; i++) {
            if (COSMETIC_SLOTS[i] == slot) {
                index = i;
                break;
            }
        }
        if (index < 0 || entries == null || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    private boolean isUnlocked(Profile profile, String id) {
        if (profile == null) {
            return false;
        }
        return contains(profile.getUnlocked().get(type), id);
    }

    private boolean isFavorite(Profile profile, String id) {
        if (profile == null) {
            return false;
        }
        return contains(profile.getFavorites().get(type), id);
    }

    private boolean isNoneSchemeDefinition(PrefixCosmeticDefinition definition) {
        return type == CosmeticType.PREFIX_SCHEME
                && definition != null
                && PrefixCosmeticCatalog.isNoneSchemeId(definition.getId());
    }

    private boolean contains(Set<String> values, String id) {
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

    private ItemStack definitionItem(PrefixCosmeticDefinition definition, String name, List<String> lore) {
        Material material = resolveMaterial(definition.getMaterial());
        ItemStack stack = item(material, name, lore);
        if (stack != null) {
            stack.setDurability(definition.getDurability());
        }
        return stack;
    }

    private Material resolveMaterial(String materialName) {
        if (materialName == null || materialName.trim().isEmpty()) {
            return Material.PAPER;
        }
        try {
            return Material.valueOf(materialName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Material.PAPER;
        }
    }

    private String formatTokens(Profile profile) {
        int tokens = profile == null ? 0 : MurderMysteryStats.getTokens(profile.getStats());
        return formatNumber(tokens);
    }

    private String formatNumber(int value) {
        return numberFormat.format(Math.max(0, value));
    }

    private String buildPreview(Profile profile, PrefixCosmeticDefinition definition) {
        if (profile == null || definition == null) {
            return "";
        }
        PrefixCosmeticDefinition icon = type == CosmeticType.PREFIX_ICON
                ? definition
                : resolveSelectedDefinition(profile, CosmeticType.PREFIX_ICON);
        PrefixCosmeticDefinition scheme = type == CosmeticType.PREFIX_SCHEME
                ? definition
                : resolveSelectedDefinition(profile, CosmeticType.PREFIX_SCHEME);
        String symbol = icon == null || icon.getSymbol().isEmpty() ? "✪" : icon.getSymbol();
        int wins = Math.max(0, coreApi.getCounter(profile.getUuid(), MongoManager.MURDER_MYSTERY_LIFETIME_WINS_KEY));
        String winsPrefix = wins == 0
                ? ChatColor.GRAY + "✪"
                : colorPrefix(scheme, "[" + formatMurderMysteryWins(wins) + symbol + "]");
        return winsPrefix + " " + rankedName(profile);
    }

    private PrefixCosmeticDefinition resolveSelectedDefinition(Profile profile, CosmeticType targetType) {
        if (profile == null || !PrefixCosmeticCatalog.isPrefixType(targetType)) {
            return PrefixCosmeticCatalog.definition(targetType, PrefixCosmeticCatalog.defaultId(targetType));
        }
        String selected = PrefixCosmeticCatalog.normalizeId(profile.getSelected().get(targetType));
        if (PrefixCosmeticCatalog.RANDOM_ID.equals(selected)) {
            return firstAvailableDefinition(profile, targetType, false);
        }
        if (PrefixCosmeticCatalog.RANDOM_FAVORITE_ID.equals(selected)) {
            return firstAvailableDefinition(profile, targetType, true);
        }
        PrefixCosmeticDefinition selectedDefinition = PrefixCosmeticCatalog.definition(targetType, selected);
        if (selectedDefinition != null && contains(profile.getUnlocked().get(targetType), selectedDefinition.getId())) {
            return selectedDefinition;
        }
        return PrefixCosmeticCatalog.definition(targetType, PrefixCosmeticCatalog.defaultId(targetType));
    }

    private PrefixCosmeticDefinition firstAvailableDefinition(Profile profile, CosmeticType targetType, boolean favoritesOnly) {
        Set<String> source = favoritesOnly ? profile.getFavorites().get(targetType) : profile.getUnlocked().get(targetType);
        if (source != null) {
            for (String id : source) {
                if (targetType == CosmeticType.PREFIX_SCHEME && PrefixCosmeticCatalog.isNoneSchemeId(id)) {
                    continue;
                }
                PrefixCosmeticDefinition definition = PrefixCosmeticCatalog.definition(targetType, id);
                if (definition != null) {
                    return definition;
                }
            }
        }
        if (favoritesOnly) {
            return firstAvailableDefinition(profile, targetType, false);
        }
        return PrefixCosmeticCatalog.definition(targetType, PrefixCosmeticCatalog.defaultId(targetType));
    }

    private String rankedName(Profile profile) {
        Rank rank = coreApi.getRank(profile.getUuid());
        if (rank == null || rank == Rank.DEFAULT) {
            return ChatColor.GRAY + profile.getName();
        }
        String prefix = RankFormatUtil.buildPrefix(
                rank,
                coreApi.getNetworkLevel(profile.getUuid()),
                profile.getPlusColor(),
                profile.getMvpPlusPlusPrefixColor()
        );
        ChatColor nameColor = RankFormatUtil.baseColor(rank, profile.getMvpPlusPlusPrefixColor());
        return prefix + nameColor + profile.getName();
    }

    private String colorPrefix(PrefixCosmeticDefinition scheme, String prefix) {
        if (scheme != null && scheme.isChroma()) {
            return colorChroma(prefix);
        }
        return parseColor(scheme == null ? null : scheme.getColor()) + prefix;
    }

    private String colorChroma(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        StringBuilder colored = new StringBuilder(prefix.length() * 3);
        for (int i = 0; i < prefix.length(); i++) {
            colored.append(CHROMA_COLORS[i % CHROMA_COLORS.length]).append(prefix.charAt(i));
        }
        return colored.toString();
    }

    private ChatColor parseColor(String colorName) {
        if (colorName != null && !colorName.trim().isEmpty()) {
            try {
                return ChatColor.valueOf(colorName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ChatColor.GRAY;
            }
        }
        return ChatColor.GRAY;
    }

    private String formatMurderMysteryWins(int wins) {
        int safeWins = Math.max(0, wins);
        if (safeWins < 1_000) {
            return Integer.toString(safeWins);
        }
        if (safeWins < 10_000) {
            int tenths = safeWins / 100;
            int whole = tenths / 10;
            int decimal = tenths % 10;
            if (decimal == 0) {
                return whole + "k";
            }
            return whole + "." + decimal + "k";
        }
        return (safeWins / 1_000) + "k";
    }

    private String typeLabel() {
        return type == CosmeticType.PREFIX_SCHEME ? "Prefix Scheme" : "Prefix Icon";
    }

    private String randomDisplayName() {
        return type == CosmeticType.PREFIX_SCHEME ? "Random Prefix Scheme" : "Random Prefix Icon";
    }

    private String randomFavoriteDisplayName() {
        return type == CosmeticType.PREFIX_SCHEME ? "Random Favorite Prefix Scheme" : "Random Favorite Prefix Icon";
    }

    private String randomSpecialLore(String specialId) {
        String subject = type == CosmeticType.PREFIX_SCHEME ? "Prefix Scheme" : "Prefix Icon";
        if (PrefixCosmeticCatalog.RANDOM_FAVORITE_ID.equals(PrefixCosmeticCatalog.normalizeId(specialId))) {
            return ChatColor.GRAY + "Use a Random " + ChatColor.GOLD + "✯ Favorite"
                    + ChatColor.GRAY + " " + subject + "!";
        }
        return ChatColor.GRAY + "Use a random " + subject + "!";
    }

    private static String resolveTitle(CosmeticType type) {
        return type == CosmeticType.PREFIX_SCHEME ? "Prefix Schemes" : "Prefix Icons";
    }

    private static final class PrefixEntry {
        private final PrefixCosmeticDefinition definition;
        private final String specialId;
        private final String displayName;

        private PrefixEntry(PrefixCosmeticDefinition definition) {
            this.definition = definition;
            this.specialId = null;
            this.displayName = definition == null ? "Unknown" : definition.getDisplayName();
        }

        private PrefixEntry(String specialId, String displayName) {
            this.definition = null;
            this.specialId = PrefixCosmeticCatalog.normalizeId(specialId);
            this.displayName = displayName == null || displayName.trim().isEmpty() ? "Random" : displayName.trim();
        }
    }
}
