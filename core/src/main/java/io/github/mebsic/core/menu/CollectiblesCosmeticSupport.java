package io.github.mebsic.core.menu;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.service.CoreApi;
import io.github.mebsic.core.service.LobbyCosmeticCatalog;
import io.github.mebsic.core.service.LobbyCosmeticDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CollectiblesCosmeticSupport {
    private CollectiblesCosmeticSupport() {
    }

    static String unlockedLore(Profile profile, CoreApi coreApi, CosmeticType type, ChatColor countColor) {
        List<LobbyCosmeticDefinition> definitions = definitions(coreApi, type);
        int total = definitions.isEmpty() ? availableOptions(coreApi, type).size() : definitions.size();
        int unlocked = definitions.isEmpty() ? countUnlocked(profile, coreApi, type) : countUnlocked(profile, definitions);
        int percent = total <= 0 ? 0 : (unlocked * 100) / total;
        ChatColor resolvedCountColor = countColor == null ? ChatColor.GREEN : countColor;
        return ChatColor.GRAY + "Unlocked: " + resolvedCountColor + unlocked + "/" + total + " "
                + ChatColor.DARK_GRAY + "(" + percent + "%)";
    }

    static String unlockedLore(Profile profile,
                               CosmeticType type,
                               List<LobbyCosmeticDefinition> definitions,
                               ChatColor countColor) {
        int total = definitions == null ? 0 : definitions.size();
        int unlocked = countUnlocked(profile, definitions);
        int percent = total <= 0 ? 0 : (unlocked * 100) / total;
        ChatColor resolvedCountColor = countColor == null ? ChatColor.GREEN : countColor;
        return ChatColor.GRAY + "Unlocked: " + resolvedCountColor + unlocked + "/" + total + " "
                + ChatColor.DARK_GRAY + "(" + percent + "%)";
    }

    static int countUnlocked(Profile profile, List<LobbyCosmeticDefinition> definitions) {
        if (profile == null || definitions == null || definitions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (LobbyCosmeticDefinition definition : definitions) {
            if (definition != null && isUnlocked(profile, definition.getType(), definition.getId())) {
                count++;
            }
        }
        return count;
    }

    static List<LobbyCosmeticDefinition> definitions(CoreApi coreApi, CosmeticType type) {
        return definitions(coreApi, type, "");
    }

    static List<LobbyCosmeticDefinition> definitions(CoreApi coreApi, CosmeticType type, String category) {
        if (coreApi == null || type == null) {
            return Collections.emptyList();
        }
        Set<String> available = normalizeIdSet(new HashSet<String>(availableOptions(coreApi, type)));
        if (available.isEmpty()) {
            return Collections.emptyList();
        }
        List<LobbyCosmeticDefinition> source = LobbyCosmeticCatalog.definitions(type, category);
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        List<LobbyCosmeticDefinition> definitions = new ArrayList<LobbyCosmeticDefinition>();
        for (LobbyCosmeticDefinition definition : source) {
            if (definition != null && available.contains(LobbyCosmeticCatalog.normalizeId(definition.getId()))) {
                definitions.add(definition);
            }
        }
        return Collections.unmodifiableList(definitions);
    }

    static boolean isUnlocked(Profile profile, CosmeticType type, String id) {
        if (profile == null || type == null) {
            return false;
        }
        String normalized = LobbyCosmeticCatalog.normalizeId(id);
        if (normalized.isEmpty()) {
            return false;
        }
        return normalizeIdSet(profile.getUnlocked().get(type)).contains(normalized);
    }

    static boolean isSelected(Profile profile, CosmeticType type, String id) {
        if (profile == null || type == null) {
            return false;
        }
        String normalized = LobbyCosmeticCatalog.normalizeId(id);
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.equals(LobbyCosmeticCatalog.normalizeId(profile.getSelected().get(type)));
    }

    static boolean hasEnoughMysteryDust(Profile profile, int cost) {
        int mysteryDust = profile == null ? 0 : Math.max(0, profile.getMysteryDust());
        return mysteryDust >= Math.max(0, cost);
    }

    static String missingMysteryDustLore(Profile profile, int cost) {
        int mysteryDust = profile == null ? 0 : Math.max(0, profile.getMysteryDust());
        int missing = Math.max(0, Math.max(0, cost) - mysteryDust);
        return ChatColor.RED + "You need " + ChatColor.AQUA + formatDust(missing)
                + ChatColor.RED + " more Mystery Dust!";
    }

    static String actionLore(Profile profile, LobbyCosmeticDefinition definition) {
        if (definition == null) {
            return "";
        }
        if (isSelected(profile, definition.getType(), definition.getId())) {
            return ChatColor.GREEN + "Currently selected!";
        }
        if (isUnlocked(profile, definition.getType(), definition.getId())) {
            return ChatColor.YELLOW + "Click to select!";
        }
        if (!hasEnoughMysteryDust(profile, definition.getCost())) {
            return missingMysteryDustLore(profile, definition.getCost());
        }
        return purchaseLore(definition);
    }

    static void appendDescriptionLore(List<String> lore, LobbyCosmeticDefinition definition, ChatColor color) {
        if (lore == null || definition == null || definition.getDescription().isEmpty()) {
            return;
        }
        ChatColor resolvedColor = color == null ? ChatColor.GRAY : color;
        for (String line : definition.getDescription()) {
            if (line == null || line.trim().isEmpty()) {
                lore.add("");
                continue;
            }
            if (line.indexOf(ChatColor.COLOR_CHAR) >= 0) {
                lore.add(line);
                continue;
            }
            lore.add(resolvedColor + line);
        }
    }

    static String purchaseLore(LobbyCosmeticDefinition definition) {
        return ChatColor.YELLOW + "Click to purchase for " + ChatColor.AQUA
                + formatDust(definition == null ? 0 : definition.getCost())
                + ChatColor.YELLOW + " Mystery Dust!";
    }

    static String purchaseMessage(LobbyCosmeticDefinition definition) {
        return ChatColor.GREEN + "You purchased "
                + coloredDisplayName(definition)
                + ChatColor.GREEN + " for "
                + ChatColor.AQUA + formatDust(definition == null ? 0 : definition.getCost())
                + ChatColor.GREEN + " Mystery Dust!";
    }

    static String selectedMessage(LobbyCosmeticDefinition definition) {
        return ChatColor.GREEN + "You selected "
                + coloredDisplayName(definition);
    }

    static String alreadySelectedMessage(LobbyCosmeticDefinition definition) {
        return ChatColor.RED + "You already have "
                + coloredDisplayName(definition)
                + ChatColor.RED + " selected!";
    }

    static String coloredDisplayName(LobbyCosmeticDefinition definition) {
        return ChatColor.YELLOW + selectedDisplayName(definition);
    }

    static void applyLeatherColor(ItemStack stack, Color color) {
        if (stack == null || color == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof LeatherArmorMeta)) {
            return;
        }
        ((LeatherArmorMeta) meta).setColor(color);
        stack.setItemMeta(meta);
    }

    static String formatDust(int amount) {
        return String.format(Locale.US, "%,d", Math.max(0, amount));
    }

    static String formatDust(Profile profile, NumberFormat numberFormat) {
        int mysteryDust = profile == null ? 0 : Math.max(0, profile.getMysteryDust());
        NumberFormat formatter = numberFormat == null ? NumberFormat.getIntegerInstance(Locale.US) : numberFormat;
        return formatter.format(mysteryDust);
    }

    private static int countUnlocked(Profile profile, CoreApi coreApi, CosmeticType type) {
        return countUnlocked(profile, type, availableOptions(coreApi, type));
    }

    private static int countUnlocked(Profile profile, CosmeticType type, List<String> options) {
        if (profile == null || options == null || options.isEmpty() || type == null) {
            return 0;
        }
        Set<String> unlocked = normalizeIdSet(profile.getUnlocked().get(type));
        if (unlocked.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String option : options) {
            String normalized = LobbyCosmeticCatalog.normalizeId(option);
            if (!normalized.isEmpty() && unlocked.contains(normalized)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> availableOptions(CoreApi coreApi, CosmeticType type) {
        if (coreApi == null || type == null) {
            return Collections.emptyList();
        }
        List<String> options = coreApi.getAvailableCosmetics(type);
        return options == null ? Collections.<String>emptyList() : options;
    }

    private static String selectedDisplayName(LobbyCosmeticDefinition definition) {
        if (definition == null) {
            return "Cosmetic";
        }
        String displayName = definition.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "Cosmetic";
        } else {
            displayName = displayName.trim();
        }
        if (definition.getType() == CosmeticType.GADGET
                && !displayName.toLowerCase(Locale.ROOT).endsWith("gadget")) {
            return displayName + " Gadget";
        }
        return displayName;
    }

    private static Set<String> normalizeIdSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<String>();
        for (String value : values) {
            String id = LobbyCosmeticCatalog.normalizeId(value);
            if (!id.isEmpty()) {
                normalized.add(id);
            }
        }
        return normalized;
    }
}
