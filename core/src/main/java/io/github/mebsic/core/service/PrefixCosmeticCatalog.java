package io.github.mebsic.core.service;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.PrefixCosmeticDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PrefixCosmeticCatalog {
    public static final String RANDOM_ID = "random";
    public static final String RANDOM_FAVORITE_ID = "random_favorite";
    public static final String DEFAULT_ICON_ID = "default";
    public static final String NONE_SCHEME_ID = "none";
    public static final String DEFAULT_SCHEME_ID = NONE_SCHEME_ID;

    private static final PrefixCosmeticDefinition NONE_SCHEME =
            scheme(NONE_SCHEME_ID, "BARRIER", 0, "None", 0, "GRAY", false);

    private static final List<PrefixCosmeticDefinition> ICONS = Collections.unmodifiableList(Arrays.asList(
            icon(DEFAULT_ICON_ID, "SULPHUR", 0, "Default", 0, "✪"),
            icon("phi", "IRON_SWORD", 0, "Divine", 100, "Φ"),
            icon("empty_set", "ROTTEN_FLESH", 0, "Zero", 250, "∅"),
            icon("sigma", "MAP", 0, "Sigma", 750, "Σ"),
            icon("omega", "COAL", 1, "Omega", 1_500, "Ω"),
            icon("alpha", "DIAMOND", 0, "Alpha", 2_500, "α"),
            icon("dollar", "GOLD_INGOT", 0, "Rich", 7_500, "$"),
            icon("triple_bar", "DIAMOND_SWORD", 0, "Equivalence", 4_000, "≡"),
            icon("pi", "EMERALD", 0, "Podium", 10_000, "π"),
            icon("florin", "INK_SACK", 14, "Florin", 20_000, "ƒ")
    ));

    private static final List<PrefixCosmeticDefinition> SCHEMES = Collections.unmodifiableList(Arrays.asList(
            scheme("dull_dark_gray", "INK_SACK", 8, "Dull Dark Gray", 1, "DARK_GRAY", false),
            scheme("sharp_gray", "INK_SACK", 8, "Sharp Gray", 100, "GRAY", false),
            scheme("basic_white", "INK_SACK", 15, "Basic White", 250, "WHITE", false),
            scheme("bloodthirsty_gold", "INK_SACK", 14, "Bloodthirsty Gold", 500, "GOLD", false),
            scheme("obvious_yellow", "INK_SACK", 11, "Obvious Yellow", 750, "YELLOW", false),
            scheme("camo_green", "INK_SACK", 10, "Camo Green", 1_000, "GREEN", false),
            scheme("shady_dark_green", "INK_SACK", 2, "Shady Dark Green", 1_500, "DARK_GREEN", false),
            scheme("passive_aqua", "INK_SACK", 12, "Passive Aqua", 2_000, "AQUA", false),
            scheme("suspicious_dark_aqua", "INK_SACK", 6, "Suspicious Dark Aqua", 2_500, "DARK_AQUA", false),
            scheme("silent_black", "INK_SACK", 0, "Silent Black", 3_000, "BLACK", false),
            scheme("regal_dark_purple", "INK_SACK", 5, "Regal Dark Purple", 4_000, "DARK_PURPLE", false),
            scheme("disguised_blue", "INK_SACK", 4, "Disguised Blue", 5_000, "BLUE", false),
            scheme("ruthless_light_purple", "INK_SACK", 13, "Ruthless Light Purple", 7_500, "LIGHT_PURPLE", false),
            scheme("dried_dark_red", "INK_SACK", 3, "Dried Dark Red", 10_000, "DARK_RED", false),
            scheme("guilty_blood_red", "INK_SACK", 1, "Guilty Blood Red", 15_000, "RED", false),
            scheme("killer_khroma", "INK_SACK", 9, "Killer Khroma", 20_000, "RED", true)
    ));

    private PrefixCosmeticCatalog() {
    }

    public static List<PrefixCosmeticDefinition> definitions(CosmeticType type) {
        if (type == CosmeticType.PREFIX_ICON) {
            return ICONS;
        }
        if (type == CosmeticType.PREFIX_SCHEME) {
            return SCHEMES;
        }
        return Collections.emptyList();
    }

    public static PrefixCosmeticDefinition definition(CosmeticType type, String id) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            return null;
        }
        if (type == CosmeticType.PREFIX_SCHEME && NONE_SCHEME_ID.equals(normalized)) {
            return NONE_SCHEME;
        }
        for (PrefixCosmeticDefinition definition : definitions(type)) {
            if (definition != null && normalized.equals(normalizeId(definition.getId()))) {
                return definition;
            }
        }
        return null;
    }

    public static String defaultId(CosmeticType type) {
        if (type == CosmeticType.PREFIX_ICON) {
            return DEFAULT_ICON_ID;
        }
        if (type == CosmeticType.PREFIX_SCHEME) {
            return DEFAULT_SCHEME_ID;
        }
        return "";
    }

    public static boolean isPrefixType(CosmeticType type) {
        return type == CosmeticType.PREFIX_ICON || type == CosmeticType.PREFIX_SCHEME;
    }

    public static boolean isNoneSchemeId(String id) {
        return NONE_SCHEME_ID.equals(normalizeId(id));
    }

    public static boolean isSpecialId(String id) {
        String normalized = normalizeId(id);
        return RANDOM_ID.equals(normalized) || RANDOM_FAVORITE_ID.equals(normalized);
    }

    public static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static PrefixCosmeticDefinition icon(String id,
                                                 String material,
                                                 int durability,
                                                 String displayName,
                                                 int requiredWins,
                                                 String symbol) {
        return new PrefixCosmeticDefinition(id, material, (short) durability, displayName, requiredWins, symbol, "", false);
    }

    private static PrefixCosmeticDefinition scheme(String id,
                                                   String material,
                                                   int durability,
                                                   String displayName,
                                                   int requiredWins,
                                                   String color,
                                                   boolean chroma) {
        return new PrefixCosmeticDefinition(id, material, (short) durability, displayName, requiredWins, "", color, chroma);
    }
}
