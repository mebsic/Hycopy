package io.github.mebsic.core.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class CustomHeadUtil {
    private static final String TEXTURES_PROPERTY = "textures";

    private CustomHeadUtil() {
    }

    public static void applyTexture(ItemStack stack, String textureValue) {
        if (stack == null || textureValue == null || textureValue.trim().isEmpty()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof SkullMeta)) {
            return;
        }
        if (applyTexture(meta, textureValue.trim())) {
            stack.setItemMeta(meta);
        }
    }

    private static boolean applyTexture(ItemMeta meta, String textureValue) {
        try {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8)), "");
            Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object textureProperty = propertyClass
                    .getConstructor(String.class, String.class)
                    .newInstance(TEXTURES_PROPERTY, textureValue);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, TEXTURES_PROPERTY, textureProperty);

            Field profileField = findField(meta.getClass(), "profile");
            if (profileField == null) {
                return false;
            }
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
