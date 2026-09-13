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
    public static final int FIRST_MOVEMENT_GADGET_COST = 40;
    public static final int SECOND_MOVEMENT_GADGET_COST = 50;
    public static final int FROG_SUIT_TOTAL_COST = 180;
    public static final int DISCO_SUIT_TOTAL_COST = 200;
    public static final int FROG_SUIT_PIECE_COST = FROG_SUIT_TOTAL_COST / 4;
    public static final int DISCO_SUIT_PIECE_COST = DISCO_SUIT_TOTAL_COST / 4;

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
                    Material.LEATHER_HELMET,
                    0,
                    ChatColor.GOLD,
                    Color.GREEN,
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
                    "disco",
                    "",
                    Material.LEATHER_HELMET,
                    0,
                    ChatColor.GOLD,
                    Color.RED,
                    "Disco Suit",
                    DISCO_SUIT_TOTAL_COST,
                    Arrays.asList(
                            "It was your Dad's favourite dance",
                            "movement - bring back the boogie",
                            "with this Disco Suit!",
                            "",
                            "Full Set Ability: Changes colors when",
                            "equipped!"
                    )
            )
    ));
    private static final List<LobbyCosmeticDefinition> SUIT_PIECES = Collections.unmodifiableList(Arrays.asList(
            suitPiece("frog", "Frog", CosmeticType.SUIT_HELMET, "Helmet", Material.LEATHER_HELMET,
                    Color.GREEN, FROG_SUIT_PIECE_COST),
            suitPiece("frog", "Frog", CosmeticType.SUIT_CHESTPLATE, "Chestplate", Material.LEATHER_CHESTPLATE,
                    Color.GREEN, FROG_SUIT_PIECE_COST),
            suitPiece("frog", "Frog", CosmeticType.SUIT_LEGGINGS, "Leggings", Material.LEATHER_LEGGINGS,
                    Color.GREEN, FROG_SUIT_PIECE_COST),
            suitPiece("frog", "Frog", CosmeticType.SUIT_BOOTS, "Boots", Material.LEATHER_BOOTS,
                    Color.GREEN, FROG_SUIT_PIECE_COST),
            suitPiece("disco", "Disco", CosmeticType.SUIT_HELMET, "Helmet", Material.LEATHER_HELMET,
                    Color.RED, DISCO_SUIT_PIECE_COST),
            suitPiece("disco", "Disco", CosmeticType.SUIT_CHESTPLATE, "Chestplate", Material.LEATHER_CHESTPLATE,
                    Color.YELLOW, DISCO_SUIT_PIECE_COST),
            suitPiece("disco", "Disco", CosmeticType.SUIT_LEGGINGS, "Leggings", Material.LEATHER_LEGGINGS,
                    Color.BLUE, DISCO_SUIT_PIECE_COST),
            suitPiece("disco", "Disco", CosmeticType.SUIT_BOOTS, "Boots", Material.LEATHER_BOOTS,
                    Color.GREEN, DISCO_SUIT_PIECE_COST)
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
        String normalizedSuitId = normalizeId(suitId);
        String normalizedPieceName = normalizeId(pieceName);
        return new LobbyCosmeticDefinition(
                type,
                normalizedSuitId + "_" + normalizedPieceName,
                normalizedSuitId,
                material,
                0,
                ChatColor.GOLD,
                leatherColor,
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
