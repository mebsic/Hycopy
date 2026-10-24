package io.github.mebsic.murdermystery.model;

import io.github.mebsic.core.service.PrefixCatalog;

public class PrefixCosmeticDefinition implements PrefixCatalog.Entry {
    private final String id;
    private final String material;
    private final short durability;
    private final String displayName;
    private final int tokenCost;
    private final String symbol;
    private final String color;
    private final boolean chroma;

    public PrefixCosmeticDefinition(String id,
                                    String material,
                                    short durability,
                                    String displayName,
                                    int tokenCost,
                                    String symbol,
                                    String color,
                                    boolean chroma) {
        this.id = safe(id);
        this.material = safe(material);
        this.durability = durability;
        this.displayName = safe(displayName);
        this.tokenCost = Math.max(0, tokenCost);
        this.symbol = symbol == null ? "" : symbol;
        this.color = safe(color);
        this.chroma = chroma;
    }

    public String getId() {
        return id;
    }

    public String getMaterial() {
        return material;
    }

    public short getDurability() {
        return durability;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTokenCost() {
        return tokenCost;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getColor() {
        return color;
    }

    public boolean isChroma() {
        return chroma;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
