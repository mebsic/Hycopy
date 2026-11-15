package io.github.mebsic.core.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class CustomHeadUtil {
    private static final String TEXTURES_PROPERTY = "textures";
    private static final Cache<UUID, String> SKIN_TEXTURES = CacheBuilder.newBuilder()
            .maximumSize(1024).expireAfterWrite(5, TimeUnit.MINUTES).build();
    private static final Set<UUID> REQUESTED_TEXTURES = ConcurrentHashMap.newKeySet();

    private CustomHeadUtil() {
    }

    public static ItemStack createPlayerHead(String texture) {
        Material material = Material.matchMaterial("PLAYER_HEAD");
        ItemStack head = material == null ? new ItemStack(Material.SKULL_ITEM, 1, (short) 3) : new ItemStack(material);
        applyTexture(head, texture);
        return head;
    }

    public static String getPlayerTexture(Player player) {
        if (player == null) {
            return null;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object profile = handle.getClass().getMethod("getProfile").invoke(handle);
            Multimap<?, ?> properties = (Multimap<?, ?>) profile.getClass().getMethod("getProperties").invoke(profile);
            Collection<?> textures = properties.asMap().get(TEXTURES_PROPERTY);
            if (textures != null && !textures.isEmpty()) {
                Object texture = textures.iterator().next();
                return (String) texture.getClass().getMethod("getValue").invoke(texture);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public static String getPlayerTexture(Plugin plugin, UUID playerId) {
        String localTexture = getPlayerTexture(Bukkit.getPlayer(playerId));
        if (localTexture != null) {
            return localTexture;
        }
        String texture = SKIN_TEXTURES.getIfPresent(playerId);
        if (texture == null && REQUESTED_TEXTURES.add(playerId)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (SKIN_TEXTURES.getIfPresent(playerId) == null) {
                        String resolved = MojangApi.lookupSkinTexture(playerId);
                        SKIN_TEXTURES.put(playerId, resolved == null ? "" : resolved);
                    }
                } finally {
                    REQUESTED_TEXTURES.remove(playerId);
                }
            });
        }
        return texture;
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
