package io.github.mebsic.core.service;

import io.github.mebsic.core.model.CosmeticType;

import java.util.List;

public interface PrefixCatalog {
    List<? extends Entry> getDefinitions(CosmeticType type);

    Entry getDefinition(CosmeticType type, String id);

    String getDefaultId(CosmeticType type);

    boolean isNoneScheme(String id);

    boolean supports(CosmeticType type);

    boolean isSpecial(String id);

    String normalize(String id);

    String randomId();

    String randomFavoriteId();

    interface Entry {
        String getId();

        int getRequiredWins();

        String getSymbol();

        String getColor();

        boolean isChroma();
    }
}
