package io.github.mebsic.core.service;

import io.github.mebsic.core.model.CosmeticType;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LobbyCosmeticCatalog {
    public static final String MOVEMENT_CATEGORY = "movement";
    public static final String PEPE_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFlYmM3YWFkNWE2NTZkNTg0MmQ0ODExNjdiNWI3Yjk4ZWFmOWQ5MjRjMmRiYjkzNDhhMzEyMDMzMzAyNjMifX19";
    public static final String FLASH_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2Y5MjMxZTkzOTkwZTQ0MzllMWJhZDIyNzI4ODQ2ZDQ5N2Q4ZDI4NzU2NDQ2NDg2MTljOWE1Nzg4YjM3In19fQ==";
    public static final Color FROG_ARMOR_COLOR = Color.fromRGB(0, 85, 0);
    public static final int FIRST_MOVEMENT_GADGET_COST = 40;
    public static final int SECOND_MOVEMENT_GADGET_COST = 50;
    public static final int FROG_SUIT_TOTAL_COST = 180;
    public static final int SPEEDSTER_SUIT_TOTAL_COST = 200;
    public static final int FROG_SUIT_PIECE_COST = FROG_SUIT_TOTAL_COST / 4;
    public static final int SPEEDSTER_SUIT_PIECE_COST = SPEEDSTER_SUIT_TOTAL_COST / 4;
    public static final Color SPEEDSTER_ARMOR_COLOR = Color.fromRGB(85, 0, 0);

    private static final List<CosmeticType> SUIT_PIECE_TYPES = Collections.unmodifiableList(Arrays.asList(
            CosmeticType.SUIT_HELMET,
            CosmeticType.SUIT_CHESTPLATE,
            CosmeticType.SUIT_LEGGINGS,
            CosmeticType.SUIT_BOOTS
    ));

    private static final List<LobbyCosmeticDefinition> GADGETS = Collections.unmodifiableList(Arrays.asList(
            new LobbyCosmeticDefinition(
                    CosmeticType.GADGET,
                    "cowboy",
                    MOVEMENT_CATEGORY,
                    Material.LEASH,
                    0,
                    ChatColor.AQUA,
                    "Cowboy Gadget",
                    FIRST_MOVEMENT_GADGET_COST,
                    Arrays.asList(
                            "Allows you to ride the nearest",
                            "player. Create towers by riding pets",
                            "and then riding other players!"
                    )
            ),
            new LobbyCosmeticDefinition(
                    CosmeticType.GADGET,
                    "grappling_hook",
                    MOVEMENT_CATEGORY,
                    Material.FISHING_ROD,
                    0,
                    ChatColor.GOLD,
                    "Grappling Hook Gadget",
                    SECOND_MOVEMENT_GADGET_COST,
                    Arrays.asList(
                            "Sometimes you have to grapple with",
                            "serious issues... and other times you",
                            "just have to grapple! Swing around",
                            "your favourite lobby with this",
                            "Grappling Hook gadget."
                    )
            )
    ));
    private static final List<LobbyCosmeticDefinition> SUITS = Collections.unmodifiableList(Arrays.asList(
            new LobbyCosmeticDefinition(
                    CosmeticType.SUIT,
                    "frog",
                    "",
                    Material.SKULL_ITEM,
                    3,
                    ChatColor.GOLD,
                    null,
                    PEPE_HEAD_TEXTURE,
                    "Frog Suit",
                    FROG_SUIT_TOTAL_COST,
                    Arrays.asList(
                            "One of the rarest suits around.",
                            "Browse dank memes from far above",
                            "when you equip this Frog Suit.",
                            "",
                            "Full Set Ability: Super high jump."
                    )
            ),
            new LobbyCosmeticDefinition(
                    CosmeticType.SUIT,
                    "speedster",
                    "",
                    Material.SKULL_ITEM,
                    3,
                    ChatColor.GOLD,
                    null,
                    FLASH_HEAD_TEXTURE,
                    "Speedster Suit",
                    SPEEDSTER_SUIT_TOTAL_COST,
                    Arrays.asList(
                            "Nothing moves faster than the speed",
                            "of light... except you when you're",
                            "wearing this Speedster Suit!",
                            "",
                            "Full Set Ability: Sprint to run",
                            "extremely fast."
                    )
            )
    ));
    private static final List<LobbyCosmeticDefinition> SUIT_PIECES = Collections.unmodifiableList(Arrays.asList(
            suitPiece("frog", "Frog", CosmeticType.SUIT_HELMET, "Helmet", Material.SKULL_ITEM,
                    3, null, PEPE_HEAD_TEXTURE, FROG_SUIT_PIECE_COST),
            suitPiece("frog", "Frog", CosmeticType.SUIT_CHESTPLATE, "Chestplate", Material.LEATHER_CHESTPLATE,
                    FROG_ARMOR_COLOR, FROG_SUIT_PIECE_COST),
            suitPiece("frog", "Frog", CosmeticType.SUIT_LEGGINGS, "Leggings", Material.LEATHER_LEGGINGS,
                    FROG_ARMOR_COLOR, FROG_SUIT_PIECE_COST),
            suitPiece("frog", "Frog", CosmeticType.SUIT_BOOTS, "Boots", Material.LEATHER_BOOTS,
                    FROG_ARMOR_COLOR, FROG_SUIT_PIECE_COST),
            suitPiece("speedster", "Speedster", CosmeticType.SUIT_HELMET, "Helmet", Material.SKULL_ITEM,
                    3, null, FLASH_HEAD_TEXTURE, SPEEDSTER_SUIT_PIECE_COST),
            suitPiece("speedster", "Speedster", CosmeticType.SUIT_CHESTPLATE, "Chestplate", Material.LEATHER_CHESTPLATE,
                    SPEEDSTER_ARMOR_COLOR, SPEEDSTER_SUIT_PIECE_COST),
            suitPiece("speedster", "Speedster", CosmeticType.SUIT_LEGGINGS, "Leggings", Material.LEATHER_LEGGINGS,
                    SPEEDSTER_ARMOR_COLOR, SPEEDSTER_SUIT_PIECE_COST),
            suitPiece("speedster", "Speedster", CosmeticType.SUIT_BOOTS, "Boots", Material.GOLD_BOOTS,
                    null, SPEEDSTER_SUIT_PIECE_COST)
    ));

    private LobbyCosmeticCatalog() {
    }

    public static boolean isLobbyType(CosmeticType type) {
        return type == CosmeticType.GADGET || type == CosmeticType.SUIT || isSuitPieceType(type);
    }

    public static boolean isSelectableLobbyType(CosmeticType type) {
        return type == CosmeticType.GADGET || isSuitPieceType(type);
    }

    public static boolean isSuitPieceType(CosmeticType type) {
        return type == CosmeticType.SUIT_HELMET
                || type == CosmeticType.SUIT_CHESTPLATE
                || type == CosmeticType.SUIT_LEGGINGS
                || type == CosmeticType.SUIT_BOOTS;
    }

    public static List<CosmeticType> suitPieceTypes() {
        return SUIT_PIECE_TYPES;
    }

    public static List<LobbyCosmeticDefinition> suits() {
        return SUITS;
    }

    public static LobbyCosmeticDefinition suit(String id) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            return null;
        }
        for (LobbyCosmeticDefinition definition : SUITS) {
            if (definition != null && normalized.equals(definition.getId())) {
                return definition;
            }
        }
        return null;
    }

    public static List<LobbyCosmeticDefinition> suitPieces() {
        return SUIT_PIECES;
    }

    public static List<LobbyCosmeticDefinition> suitPieces(String suitId) {
        return definitions(CosmeticType.SUIT, suitId);
    }

    public static List<String> options(CosmeticType type) {
        List<LobbyCosmeticDefinition> definitions = definitions(type);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (LobbyCosmeticDefinition definition : definitions) {
            if (definition != null && !definition.getId().isEmpty()) {
                ids.add(definition.getId());
            }
        }
        return Collections.unmodifiableList(ids);
    }

    public static List<String> options(CosmeticType type, String category) {
        List<LobbyCosmeticDefinition> definitions = definitions(type, category);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (LobbyCosmeticDefinition definition : definitions) {
            if (definition != null && !definition.getId().isEmpty()) {
                ids.add(definition.getId());
            }
        }
        return Collections.unmodifiableList(ids);
    }

    public static boolean contains(CosmeticType type, String id) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            return false;
        }
        return options(type).contains(normalized);
    }

    public static List<LobbyCosmeticDefinition> definitions(CosmeticType type) {
        if (type == CosmeticType.GADGET) {
            return GADGETS;
        }
        if (type == CosmeticType.SUIT) {
            return SUIT_PIECES;
        }
        if (isSuitPieceType(type)) {
            java.util.List<LobbyCosmeticDefinition> matches = new java.util.ArrayList<LobbyCosmeticDefinition>();
            for (LobbyCosmeticDefinition definition : SUIT_PIECES) {
                if (definition != null && type == definition.getType()) {
                    matches.add(definition);
                }
            }
            return Collections.unmodifiableList(matches);
        }
        return Collections.emptyList();
    }

    public static List<LobbyCosmeticDefinition> definitions(CosmeticType type, String category) {
        String normalizedCategory = normalizeId(category);
        if (normalizedCategory.isEmpty()) {
            return definitions(type);
        }
        List<LobbyCosmeticDefinition> definitions = definitions(type);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.List<LobbyCosmeticDefinition> matches = new java.util.ArrayList<LobbyCosmeticDefinition>();
        for (LobbyCosmeticDefinition definition : definitions) {
            if (definition != null && normalizedCategory.equals(definition.getCategory())) {
                matches.add(definition);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public static LobbyCosmeticDefinition definition(CosmeticType type, String id) {
        String normalized = normalizeId(id);
        if (normalized.isEmpty()) {
            return null;
        }
        for (LobbyCosmeticDefinition definition : definitions(type)) {
            if (definition != null && normalized.equals(normalizeId(definition.getId()))) {
                return definition;
            }
        }
        return null;
    }

    public static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static LobbyCosmeticDefinition suitPiece(String suitId,
                                                     String suitName,
                                                     CosmeticType type,
                                                     String pieceName,
                                                     Material material,
                                                     Color leatherColor,
                                                     int cost) {
        return suitPiece(suitId, suitName, type, pieceName, material, 0, leatherColor, "", cost);
    }

    private static LobbyCosmeticDefinition suitPiece(String suitId,
                                                     String suitName,
                                                     CosmeticType type,
                                                     String pieceName,
                                                     Material material,
                                                     int durability,
                                                     Color leatherColor,
                                                     String headTexture,
                                                     int cost) {
        String normalizedSuitId = normalizeId(suitId);
        String normalizedPieceName = normalizeId(pieceName);
        return new LobbyCosmeticDefinition(
                type,
                normalizedSuitId + "_" + normalizedPieceName,
                normalizedSuitId,
                material,
                durability,
                ChatColor.GOLD,
                leatherColor,
                headTexture,
                safeDisplay(suitName) + " Suit " + safeDisplay(pieceName),
                cost,
                Collections.<String>emptyList()
        );
    }

    private static String safeDisplay(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
