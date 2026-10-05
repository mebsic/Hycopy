package io.github.mebsic.core.service;

import io.github.mebsic.core.model.CosmeticType;
import io.github.mebsic.core.model.PrefixCosmeticDefinition;

import java.util.List;

public interface PrefixCatalog {
    List<PrefixCosmeticDefinition> getDefinitions(CosmeticType type);

    PrefixCosmeticDefinition getDefinition(CosmeticType type, String id);

    String getDefaultId(CosmeticType type);

    boolean isNoneScheme(String id);

    boolean supports(CosmeticType type);

    boolean isSpecial(String id);

    String normalize(String id);

    String randomId();

    String randomFavoriteId();
}
