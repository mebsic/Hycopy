package io.github.mebsic.core.service;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.Profile;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class CosmeticService {
    public static final String DEFAULT_KNIFE_ID = CosmeticType.DEFAULT_KNIFE_ID;
    public static final String RANDOM_KNIFE_ID = "random";
    public static final String RANDOM_FAVORITE_KNIFE_ID = "random_favorite";
    private final Map<String, CosmeticItem> knifeSkins;
    private PrefixCatalog prefixCatalog;

    public CosmeticService(Map<String, ? extends CosmeticItem> knifeSkins) {
        this.knifeSkins = new HashMap<>();
        setKnifeSkins(knifeSkins);
    }

    public void setKnifeSkins(Map<String, ? extends CosmeticItem> knifeSkins) {
        this.knifeSkins.clear();
        if (knifeSkins != null) {
            for (Map.Entry<String, ? extends CosmeticItem> entry : knifeSkins.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = CosmeticType.KNIFE.normalizeId(entry.getKey());
                if (key.isEmpty()) {
                    continue;
                }
                if (key.equals(CosmeticType.RANDOM_KNIFE_ENTRY_ID)
                        || key.equals(CosmeticType.RANDOM_FAVORITE_KNIFE_ENTRY_ID)) {
                    continue;
                }
                this.knifeSkins.put(key, entry.getValue());
            }
        }
    }

    public void setPrefixCatalog(PrefixCatalog prefixCatalog) {
        this.prefixCatalog = prefixCatalog;
    }

    public List<String> getOptions(CosmeticType type) {
        if (type == CosmeticType.KNIFE) {
            List<String> options = new ArrayList<>();
            options.add(DEFAULT_KNIFE_ID);
            options.add(RANDOM_KNIFE_ID);
            options.add(RANDOM_FAVORITE_KNIFE_ID);
            List<String> remaining = new ArrayList<>();
            for (String key : knifeSkins.keySet()) {
                if (key == null) {
                    continue;
                }
                String normalized = key.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty() || normalized.equals(DEFAULT_KNIFE_ID)) {
                    continue;
                }
                remaining.add(normalized);
            }
            Collections.sort(remaining);
            options.addAll(remaining);
            return Collections.unmodifiableList(options);
        }
        if (prefixCatalog != null && prefixCatalog.supports(type)) {
            List<String> options = new ArrayList<String>();
            String defaultId = prefixCatalog.getDefaultId(type);
            if (!defaultId.isEmpty()) {
                options.add(defaultId);
            }
            for (PrefixCatalog.Entry definition : prefixCatalog.getDefinitions(type)) {
                if (definition != null && !definition.getId().isEmpty()) {
                    if (definition.getId().equals(defaultId)) {
                        continue;
                    }
                    options.add(definition.getId());
                }
            }
            options.add(1, prefixCatalog.randomId());
            options.add(2, prefixCatalog.randomFavoriteId());
            return Collections.unmodifiableList(options);
        }
        if (LobbyCosmeticCatalog.isLobbyType(type)) {
            return LobbyCosmeticCatalog.options(type);
        }
        return Collections.emptyList();
    }

    public ItemStack createKnife(Profile profile) {
        String selected = resolveSelectedKnifeId(profile);
        CosmeticItem skin = resolveKnife(selected);
        ItemStack item = new ItemStack(resolveMaterial(skin));
        applyLegacyVariantData(item, selected);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && skin != null && !DEFAULT_KNIFE_ID.equals(normalizeId(selected))) {
            meta.setDisplayName(colorize(skin.getDisplayName()));
            meta.setLore(Collections.singletonList(colorize(skin.getDescription())));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createBow(Profile profile) {
        return new ItemStack(Material.BOW);
    }

    public boolean unlock(Profile profile, CosmeticType type, String id) {
        if (profile == null) {
            return false;
        }
        if (type == CosmeticType.KNIFE) {
            String normalized = normalizeId(id);
            if (normalized.isEmpty() || isSpecialKnifeId(normalized)) {
                return false;
            }
            if (!getOptions(type).contains(normalized)) {
                return false;
            }
            return profile.getUnlocked().get(type).add(normalized);
        }
        if (prefixCatalog != null && prefixCatalog.supports(type)) {
            String normalized = prefixCatalog.normalize(id);
            if (normalized.isEmpty() || prefixCatalog.isSpecial(normalized)) {
                return false;
            }
            if (prefixCatalog.getDefinition(type, normalized) == null) {
                return false;
            }
            return profile.getUnlocked().get(type).add(normalized);
        }
        if (LobbyCosmeticCatalog.isSelectableLobbyType(type)) {
            String normalized = LobbyCosmeticCatalog.normalizeId(id);
            if (normalized.isEmpty() || LobbyCosmeticCatalog.definition(type, normalized) == null) {
                return false;
            }
            return profile.getUnlocked().get(type).add(normalized);
        }
        return false;
    }

    public boolean select(Profile profile, CosmeticType type, String id) {
        if (profile == null) {
            return false;
        }
        if (type == CosmeticType.KNIFE) {
            String normalized = normalizeId(id);
            if (normalized.equals(CosmeticType.RANDOM_KNIFE_ENTRY_ID)) {
                normalized = RANDOM_KNIFE_ID;
            } else if (normalized.equals(CosmeticType.RANDOM_FAVORITE_KNIFE_ENTRY_ID)) {
                normalized = RANDOM_FAVORITE_KNIFE_ID;
            }
            if (normalized.isEmpty()) {
                return false;
            }
            if (normalized.equals(RANDOM_KNIFE_ID) || normalized.equals(RANDOM_FAVORITE_KNIFE_ID)) {
                profile.getSelected().put(type, normalized);
                return true;
            }
            if (!getOptions(type).contains(normalized)) {
                return false;
            }
            if (!containsNormalized(profile.getUnlocked().get(type), normalized)) {
                return false;
            }
            profile.getSelected().put(type, normalized);
            return true;
        }
        if (prefixCatalog != null && prefixCatalog.supports(type)) {
            String normalized = prefixCatalog.normalize(id);
            if (normalized.isEmpty()) {
                return false;
            }
            if (prefixCatalog.isSpecial(normalized)) {
                profile.getSelected().put(type, normalized);
                return true;
            }
            if (prefixCatalog.getDefinition(type, normalized) == null) {
                return false;
            }
            if (!containsPrefixId(profile.getUnlocked().get(type), normalized)) {
                return false;
            }
            profile.getSelected().put(type, normalized);
            return true;
        }
        if (LobbyCosmeticCatalog.isSelectableLobbyType(type)) {
            String normalized = LobbyCosmeticCatalog.normalizeId(id);
            if (normalized.isEmpty() || LobbyCosmeticCatalog.definition(type, normalized) == null) {
                return false;
            }
            if (!containsLobbyId(profile.getUnlocked().get(type), normalized)) {
                return false;
            }
            profile.getSelected().put(type, normalized);
            return true;
        }
        return false;
    }

    public boolean toggleFavorite(Profile profile, CosmeticType type, String id) {
        if (profile == null) {
            return false;
        }
        if (type == CosmeticType.KNIFE) {
            String normalized = normalizeId(id);
            if (normalized.isEmpty() || isSpecialKnifeId(normalized)) {
                return false;
            }
            if (!containsNormalized(profile.getUnlocked().get(type), normalized)) {
                return false;
            }
            Set<String> favorites = profile.getFavorites().get(type);
            if (containsNormalized(favorites, normalized)) {
                removeNormalized(favorites, normalized);
                return true;
            }
            favorites.add(normalized);
            return true;
        }
        if (prefixCatalog != null && prefixCatalog.supports(type)) {
            String normalized = prefixCatalog.normalize(id);
            if (normalized.isEmpty()
                    || prefixCatalog.isSpecial(normalized)
                    || prefixCatalog.isNoneScheme(normalized)) {
                return false;
            }
            if (!containsPrefixId(profile.getUnlocked().get(type), normalized)) {
                return false;
            }
            Set<String> favorites = profile.getFavorites().get(type);
            if (containsPrefixId(favorites, normalized)) {
                removePrefixId(favorites, normalized);
                return true;
            }
            favorites.add(normalized);
            return true;
        }
        if (LobbyCosmeticCatalog.isSelectableLobbyType(type)) {
            String normalized = LobbyCosmeticCatalog.normalizeId(id);
            if (normalized.isEmpty() || LobbyCosmeticCatalog.definition(type, normalized) == null) {
                return false;
            }
            if (!containsLobbyId(profile.getUnlocked().get(type), normalized)) {
                return false;
            }
            Set<String> favorites = profile.getFavorites().get(type);
            if (containsLobbyId(favorites, normalized)) {
                removeLobbyId(favorites, normalized);
                return true;
            }
            favorites.add(normalized);
            return true;
        }
        return false;
    }

    public boolean isFavorite(Profile profile, CosmeticType type, String id) {
        if (profile == null) {
            return false;
        }
        if (type == CosmeticType.KNIFE) {
            String normalized = normalizeId(id);
            if (normalized.isEmpty()) {
                return false;
            }
            return containsNormalized(profile.getFavorites().get(type), normalized);
        }
        if (prefixCatalog != null && prefixCatalog.supports(type)) {
            String normalized = prefixCatalog.normalize(id);
            if (normalized.isEmpty()) {
                return false;
            }
            return containsPrefixId(profile.getFavorites().get(type), normalized);
        }
        if (LobbyCosmeticCatalog.isLobbyType(type)) {
            String normalized = LobbyCosmeticCatalog.normalizeId(id);
            if (normalized.isEmpty()) {
                return false;
            }
            return containsLobbyId(profile.getFavorites().get(type), normalized);
        }
        return false;
    }

    public void grantDefaults(Profile profile) {
        if (profile == null) {
            return;
        }
        profile.getUnlocked().get(CosmeticType.KNIFE).add(DEFAULT_KNIFE_ID);
        profile.getSelected().putIfAbsent(CosmeticType.KNIFE, DEFAULT_KNIFE_ID);
        profile.getFavorites().get(CosmeticType.KNIFE).retainAll(profile.getUnlocked().get(CosmeticType.KNIFE));
        grantPrefixDefaults(profile, CosmeticType.PREFIX_ICON);
        grantPrefixDefaults(profile, CosmeticType.PREFIX_SCHEME);
    }

    public Map<String, CosmeticItem> getKnifeSkins() {
        return Collections.unmodifiableMap(knifeSkins);
    }

    private CosmeticItem resolveKnife(String id) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            return knifeSkins.get(DEFAULT_KNIFE_ID);
        }
        CosmeticItem definition = knifeSkins.get(normalized);
        return definition == null ? knifeSkins.get(DEFAULT_KNIFE_ID) : definition;
    }

    private String resolveSelectedKnifeId(Profile profile) {
        if (profile == null) {
            return DEFAULT_KNIFE_ID;
        }
        String selected = normalizeId(profile.getSelected().getOrDefault(CosmeticType.KNIFE, DEFAULT_KNIFE_ID));
        if (selected.equals(CosmeticType.RANDOM_KNIFE_ENTRY_ID)) {
            selected = RANDOM_KNIFE_ID;
        } else if (selected.equals(CosmeticType.RANDOM_FAVORITE_KNIFE_ENTRY_ID)) {
            selected = RANDOM_FAVORITE_KNIFE_ID;
        }
        if (selected.equals(RANDOM_KNIFE_ID)) {
            return pickRandomKnife(profile, false);
        }
        if (selected.equals(RANDOM_FAVORITE_KNIFE_ID)) {
            return pickRandomKnife(profile, true);
        }
        if (selected.isEmpty()) {
            return DEFAULT_KNIFE_ID;
        }
        if (!containsNormalized(profile.getUnlocked().get(CosmeticType.KNIFE), selected)) {
            return DEFAULT_KNIFE_ID;
        }
        if (!knifeSkins.containsKey(selected)) {
            return DEFAULT_KNIFE_ID;
        }
        return selected;
    }

    private String pickRandomKnife(Profile profile, boolean favoritesOnly) {
        Set<String> unlocked = normalizeIdSet(profile.getUnlocked().get(CosmeticType.KNIFE));
        if (unlocked == null || unlocked.isEmpty()) {
            return DEFAULT_KNIFE_ID;
        }
        List<String> candidates = new ArrayList<>();
        if (favoritesOnly) {
            Set<String> favorites = profile.getFavorites().get(CosmeticType.KNIFE);
            if (favorites != null) {
                for (String id : favorites) {
                    String normalized = normalizeId(id);
                    if (normalized.isEmpty() || isSpecialKnifeId(normalized)) {
                        continue;
                    }
                    if (unlocked.contains(normalized)) {
                        candidates.add(normalized);
                    }
                }
            }
            if (candidates.isEmpty()) {
                return pickRandomKnife(profile, false);
            }
        } else {
            for (String id : unlocked) {
                String normalized = normalizeId(id);
                if (normalized.isEmpty() || isSpecialKnifeId(normalized)) {
                    continue;
                }
                if (!knifeSkins.containsKey(normalized)) {
                    continue;
                }
                candidates.add(normalized);
            }
        }
        if (candidates.isEmpty()) {
            return DEFAULT_KNIFE_ID;
        }
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index);
    }

    private boolean containsNormalized(Set<String> values, String targetNormalized) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        String normalizedTarget = normalizeId(targetNormalized);
        if (normalizedTarget.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (normalizedTarget.equals(normalizeId(value))) {
                return true;
            }
        }
        return false;
    }

    private void removeNormalized(Set<String> values, String targetNormalized) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String normalizedTarget = normalizeId(targetNormalized);
        if (normalizedTarget.isEmpty()) {
            return;
        }
        java.util.Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            if (normalizedTarget.equals(normalizeId(iterator.next()))) {
                iterator.remove();
            }
        }
    }

    private boolean containsPrefixId(Set<String> values, String targetNormalized) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        String normalizedTarget = prefixCatalog.normalize(targetNormalized);
        if (normalizedTarget.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (normalizedTarget.equals(prefixCatalog.normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private void removePrefixId(Set<String> values, String targetNormalized) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String normalizedTarget = prefixCatalog.normalize(targetNormalized);
        if (normalizedTarget.isEmpty()) {
            return;
        }
        java.util.Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            if (normalizedTarget.equals(prefixCatalog.normalize(iterator.next()))) {
                iterator.remove();
            }
        }
    }

    private boolean containsLobbyId(Set<String> values, String targetNormalized) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        String normalizedTarget = LobbyCosmeticCatalog.normalizeId(targetNormalized);
        if (normalizedTarget.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (normalizedTarget.equals(LobbyCosmeticCatalog.normalizeId(value))) {
                return true;
            }
        }
        return false;
    }

    private void removeLobbyId(Set<String> values, String targetNormalized) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String normalizedTarget = LobbyCosmeticCatalog.normalizeId(targetNormalized);
        if (normalizedTarget.isEmpty()) {
            return;
        }
        java.util.Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            if (normalizedTarget.equals(LobbyCosmeticCatalog.normalizeId(iterator.next()))) {
                iterator.remove();
            }
        }
    }

    private void grantPrefixDefaults(Profile profile, CosmeticType type) {
        if (profile == null || prefixCatalog == null || !prefixCatalog.supports(type)) {
            return;
        }
        int wins = Math.max(0, profile.getStats().getWins());
        Set<String> unlocked = profile.getUnlocked().get(type);
        for (PrefixCatalog.Entry definition : prefixCatalog.getDefinitions(type)) {
            if (definition == null || definition.getId().isEmpty()) {
                continue;
            }
            if (wins >= definition.getRequiredWins()) {
                unlocked.add(prefixCatalog.normalize(definition.getId()));
            }
        }
        String defaultId = prefixCatalog.getDefaultId(type);
        if (!defaultId.isEmpty()) {
            unlocked.add(defaultId);
            profile.getSelected().putIfAbsent(type, defaultId);
        }
        profile.getFavorites().get(type).retainAll(unlocked);
    }

    private Set<String> normalizeIdSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<String>();
        for (String value : values) {
            String normalizedValue = normalizeId(value);
            if (!normalizedValue.isEmpty()) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private boolean isSpecialKnifeId(String id) {
        String normalized = normalizeId(id);
        return normalized.equals(RANDOM_KNIFE_ID)
                || normalized.equals(RANDOM_FAVORITE_KNIFE_ID)
                || normalized.equals(CosmeticType.RANDOM_KNIFE_ENTRY_ID)
                || normalized.equals(CosmeticType.RANDOM_FAVORITE_KNIFE_ENTRY_ID);
    }

    private String normalizeId(String id) {
        return CosmeticType.KNIFE.normalizeId(id);
    }

    private Material resolveMaterial(CosmeticItem skin) {
        if (skin == null || skin.getMaterial() == null) {
            return Material.IRON_SWORD;
        }
        String materialName = skin.getMaterial().trim().toUpperCase(Locale.ROOT);
        if (materialName.isEmpty()) {
            return Material.IRON_SWORD;
        }
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException ignored) {
            if (materialName.endsWith("_SHOVEL")) {
                try {
                    return Material.valueOf(materialName.substring(0, materialName.length() - "_SHOVEL".length()) + "_SPADE");
                } catch (IllegalArgumentException ignoredAgain) {
                    return Material.IRON_SWORD;
                }
            }
            return Material.IRON_SWORD;
        }
    }

    private void applyLegacyVariantData(ItemStack item, String knifeId) {
        if (item == null) {
            return;
        }
        String normalizedId = normalizeId(knifeId);
        if ((CosmeticType.KNIFE_ID_PREFIX + "44").equals(normalizedId) && item.getType() == Material.SAPLING) {
            item.setDurability((short) 3);
            return;
        }
        if ((CosmeticType.KNIFE_ID_PREFIX + "19").equals(normalizedId) && item.getType() == Material.COAL) {
            item.setDurability((short) 1);
            return;
        }
        if ((CosmeticType.KNIFE_ID_PREFIX + "26").equals(normalizedId) && item.getType() == Material.DOUBLE_PLANT) {
            item.setDurability((short) 4);
            return;
        }
        if ((CosmeticType.KNIFE_ID_PREFIX + "39").equals(normalizedId) && item.getType() == Material.INK_SACK) {
            item.setDurability((short) 1);
            return;
        }
        if ((CosmeticType.KNIFE_ID_PREFIX + "33").equals(normalizedId) && item.getType() == Material.INK_SACK) {
            item.setDurability((short) 4);
            return;
        }
        if ((CosmeticType.KNIFE_ID_PREFIX + "38").equals(normalizedId) && item.getType() == Material.RAW_FISH) {
            item.setDurability((short) 1);
        }
    }

    private String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
