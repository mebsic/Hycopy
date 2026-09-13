package io.github.mebsic.core.service;

import io.github.mebsic.core.model.CosmeticType;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LobbyCosmeticDefinition {
    private final CosmeticType type;
    private final String id;
    private final String category;
    private final Material material;
    private final short durability;
    private final ChatColor displayColor;
    private final Color leatherColor;
    private final String displayName;
    private final int cost;
    private final List<String> description;

    public LobbyCosmeticDefinition(CosmeticType type,
                                   String id,
                                   String category,
                                   Material material,
                                   int durability,
                                   ChatColor displayColor,
                                   String displayName,
                                   int cost,
                                   List<String> description) {
        this(type, id, category, material, durability, displayColor, null, displayName, cost, description);
    }

    public LobbyCosmeticDefinition(CosmeticType type,
                                   String id,
                                   String category,
                                   Material material,
                                   int durability,
                                   ChatColor displayColor,
                                   Color leatherColor,
                                   String displayName,
                                   int cost,
                                   List<String> description) {
        this.type = type;
        this.id = normalize(id);
        this.category = normalize(category);
        this.material = material;
        this.durability = (short) Math.max(0, durability);
        this.displayColor = displayColor == null ? ChatColor.GREEN : displayColor;
        this.leatherColor = leatherColor;
        this.displayName = safe(displayName);
        this.cost = Math.max(0, cost);
        this.description = immutableDescription(description);
    }

    public CosmeticType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public Material getMaterial() {
        return material;
    }

    public short getDurability() {
        return durability;
    }

    public ChatColor getDisplayColor() {
        return displayColor;
    }

    public Color getLeatherColor() {
        return leatherColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCost() {
        return cost;
    }

    public List<String> getDescription() {
        return description;
    }

    private static List<String> immutableDescription(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<String>();
        for (String line : source) {
            copy.add(safe(line));
        }
        return Collections.unmodifiableList(copy);
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
