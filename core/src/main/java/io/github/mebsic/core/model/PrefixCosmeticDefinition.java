package io.github.mebsic.core.model;

public class PrefixCosmeticDefinition {
    private final String id;
    private final String material;
    private final short durability;
    private final String displayName;
    private final int requiredWins;
    private final String symbol;
    private final String color;
    private final boolean chroma;

    public PrefixCosmeticDefinition(String id,
                                    String material,
                                    short durability,
                                    String displayName,
                                    int requiredWins,
                                    String symbol,
                                    String color,
                                    boolean chroma) {
        this.id = safe(id);
        this.material = safe(material);
        this.durability = durability;
        this.displayName = safe(displayName);
        this.requiredWins = Math.max(0, requiredWins);
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

    public int getRequiredWins() {
        return requiredWins;
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
