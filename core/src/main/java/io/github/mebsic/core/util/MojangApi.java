package io.github.mebsic.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MojangApi {
    private static final String MOJANG_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SERVICES_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String SESSION_ENDPOINT = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final long NAME_CACHE_TTL_MILLIS = 30_000L;
    private static final long NAME_RETRY_TTL_MILLIS = 60_000L;
    private static final ConcurrentHashMap<UUID, CachedName> NAME_CACHE = new ConcurrentHashMap<>();

    private MojangApi() {
    }

    public static UUID lookupUuid(String username) {
        UUID uuid = fetchUuid(SERVICES_ENDPOINT + username);
        if (uuid != null) {
            return uuid;
        }
        return fetchUuid(MOJANG_ENDPOINT + username);
    }

    public static String lookupName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        CachedName cached = NAME_CACHE.compute(uuid, (id, previous) -> {
            if (previous != null && System.currentTimeMillis() < previous.expiresAtMillis) {
                return previous;
            }
            String refreshed = fetchName(id);
            String name = refreshed == null && previous != null ? previous.name : refreshed;
            long ttl = refreshed == null ? NAME_RETRY_TTL_MILLIS : NAME_CACHE_TTL_MILLIS;
            return new CachedName(name, System.currentTimeMillis() + ttl);
        });
        return cached.name;
    }

    public static String lookupCurrentName(UUID uuid) {
        return uuid == null ? null : fetchName(uuid);
    }

    private static String fetchName(UUID uuid) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SESSION_ENDPOINT + uuid.toString().replace("-", "")).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                JsonObject obj = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!obj.has("id") || !uuid.equals(formatUuid(obj.get("id").getAsString())) || !obj.has("name")) {
                    return null;
                }
                String name = obj.get("name").getAsString().trim();
                return name.matches("[A-Za-z0-9_]{1,16}") ? name : null;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static UUID fetchUuid(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) {
                return null;
            }
            if (status != 200) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                JsonObject obj = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!obj.has("id")) {
                    return null;
                }
                String raw = obj.get("id").getAsString();
                return formatUuid(raw);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static UUID formatUuid(String raw) {
        if (raw == null || raw.length() != 32) {
            return null;
        }
        String formatted = raw.substring(0, 8) + "-" +
                raw.substring(8, 12) + "-" +
                raw.substring(12, 16) + "-" +
                raw.substring(16, 20) + "-" +
                raw.substring(20);
        return UUID.fromString(formatted);
    }

    private static final class CachedName {
        private final String name;
        private final long expiresAtMillis;

        private CachedName(String name, long expiresAtMillis) {
            this.name = name;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
