package io.github.mebsic.murdermystery.model;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.service.CosmeticItem;

public class KnifeSkinDefinition implements CosmeticItem {
    public static final String ID_PREFIX = CosmeticType.KNIFE_ID_PREFIX;
    public static final String DEFAULT_ID = CosmeticType.DEFAULT_KNIFE_ID;
    public static final String RANDOM_ID = CosmeticType.RANDOM_KNIFE_ENTRY_ID;
    public static final String RANDOM_FAVORITE_ID = CosmeticType.RANDOM_FAVORITE_KNIFE_ENTRY_ID;
    public static final String DEFAULT_RARITY = "common";

    private String id;
    private String material;
    private String displayName;
    private String description;
    private int cost;
    private String rarity;

    public KnifeSkinDefinition() {
        this.rarity = DEFAULT_RARITY;
    }

    public KnifeSkinDefinition(String id, String material, String displayName, String description, int cost) {
        this(id, material, displayName, description, cost, DEFAULT_RARITY);
    }

    public KnifeSkinDefinition(String id, String material, String displayName, String description, int cost, String rarity) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.description = description;
        this.cost = cost;
        this.rarity = normalizeRarity(rarity);
    }

    public String getId() {
        return id;
    }

    public String getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getCost() {
        return cost;
    }

    public String getRarity() {
        return normalizeRarity(rarity);
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public void setRarity(String rarity) {
        this.rarity = normalizeRarity(rarity);
    }

    public static String normalizeId(String value) {
        return CosmeticType.KNIFE.normalizeId(value);
    }

    private String normalizeRarity(String value) {
        return CosmeticItem.normalizeRarity(value);
    }
}
