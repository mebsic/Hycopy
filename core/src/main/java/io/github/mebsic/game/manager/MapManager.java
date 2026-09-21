package io.github.mebsic.game.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.store.MapConfigStore;
import io.github.mebsic.game.map.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MapManager {
    private final CorePlugin plugin;
    private final Map<String, GameMap> maps;
    private final Map<String, Location> pregameSpawns;
    private final Map<String, String> mapAliases;
    private String configuredMapDisplayName;
    private String activeMapName;
    private Location pregameSpawn;
    private List<String> rotation;

    public MapManager(CorePlugin plugin) {
        this.plugin = plugin;
        this.maps = new LinkedHashMap<>();
        this.pregameSpawns = new HashMap<>();
        this.mapAliases = new LinkedHashMap<>();
        this.configuredMapDisplayName = "";
        this.pregameSpawn = null;
        this.rotation = new ArrayList<>();
    }

    public void loadMaps() {
        loadMaps(false);
    }

    public void loadMaps(boolean preserveActiveMap) {
        String previousActiveMap = preserveActiveMap ? safeText(activeMapName) : "";
        maps.clear();
        pregameSpawns.clear();
        mapAliases.clear();
        configuredMapDisplayName = "";
        pregameSpawn = null;
        MapConfig config = loadConfig();
        Location configuredPregame = config == null ? null : toLocation(config.pregameSpawn);
        if (configuredPregame != null) {
            pregameSpawn = configuredPregame;
        }
        if (config != null && config.maps != null) {
            for (MapEntry entry : config.maps) {
                String worldDirectory = safeText(entry == null ? null : entry.worldDirectory);
                String displayName = safeText(entry == null ? null : entry.name);
                String mapName = worldDirectory;
                if (mapName.isEmpty() || isPlaceholderMapName(mapName)) {
                    mapName = displayName;
                }
                if (mapName.isEmpty()) {
                    continue;
                }
                if (displayName.isEmpty()) {
                    displayName = MapConfigStore.displayNameFromWorldDirectory(mapName);
                }
                boolean nightTime = entry.nightTime != null && entry.nightTime;
                GameMap map = new GameMap(mapName, displayName, nightTime);
                if (entry.spawns != null) {
                    for (LocationEntry loc : entry.spawns) {
                        Location location = toLocation(loc);
                        if (location != null) {
                            map.getSpawnPoints().add(location);
                        }
                    }
                }
                if (entry.dropItem != null) {
                    for (LocationEntry loc : entry.dropItem) {
                        if (loc == null) {
                            continue;
                        }
                        Location location = toLocation(loc);
                        if (location != null) {
                            map.getDropItemSpawns().add(location);
                            map.getDropItemStacks().add(toDropItemStack(loc));
                        }
                    }
                }
                if (entry.mysteryPotions != null) {
                    for (LocationEntry loc : entry.mysteryPotions) {
                        Location location = toLocation(loc);
                        if (location != null) {
                            map.getMysteryPotionLocations().add(location);
                        }
                    }
                }
                maps.put(normalizeMapKey(map.getName()), map);
                registerMapAlias(map.getName(), map.getName());
                registerMapAlias(worldDirectory, map.getName());
                registerMapAlias(displayName, map.getName());
                Location mapPregame = toLocation(entry.pregameSpawn);
                if (mapPregame != null) {
                    pregameSpawns.put(normalizeMapKey(map.getName()), mapPregame);
                    registerPregameAlias(worldDirectory, mapPregame);
                    registerPregameAlias(displayName, mapPregame);
                }
            }
        }
        if (maps.isEmpty()) {
            plugin.getLogger().warning("No maps loaded from MongoDB collection " + MongoManager.MAPS_COLLECTION + ".");
        }
        this.rotation = config != null && config.rotation != null ? new ArrayList<>(config.rotation) : new ArrayList<>();
        registerConfiguredAliases(config);
        String preferredActiveMap = resolvePreferredActiveMapName(config);
        configuredMapDisplayName = resolveConfiguredDisplayName(config, preferredActiveMap);
        if (!previousActiveMap.isEmpty()) {
            String preserved = firstMatchingMapForServerKind(previousActiveMap);
            if (preserved != null && !isPlaceholderMapName(preserved)) {
                this.activeMapName = preserved;
                return;
            }
        }
        this.activeMapName = preferredActiveMap;
    }

    public void saveMaps() {
        // Map metadata is sourced from MongoDB maps; no-op.
    }

    public GameMap getActiveMap() {
        String current = safeText(activeMapName);
        GameMap active = current.isEmpty() ? null : maps.get(normalizeMapKey(current));
        if (active != null && !isPlaceholderMapName(active.getName())) {
            return active;
        }

        String runtimePreferred = resolvePreferredMapFromRuntimeWorld();
        if (runtimePreferred != null) {
            GameMap runtimeMap = maps.get(normalizeMapKey(runtimePreferred));
            if (runtimeMap != null && runtimeMap.getName() != null) {
                activeMapName = runtimeMap.getName();
                return runtimeMap;
            }
        }

        if (active != null) {
            return active;
        }

        String fallback = getFirstMapName();
        if (fallback == null) {
            return null;
        }
        GameMap fallbackMap = maps.get(normalizeMapKey(fallback));
        if (fallbackMap != null && fallbackMap.getName() != null) {
            activeMapName = fallbackMap.getName();
        }
        return fallbackMap;
    }

    public String getActiveMapDisplayName() {
        GameMap active = getActiveMap();
        if (active == null) {
            return "";
        }
        String display = safeText(active.getDisplayName());
        if (!display.isEmpty() && !isPlaceholderMapName(display)) {
            return display;
        }
        String runtimeMarkerMap = readMapNameFromWorldMarker();
        if (!runtimeMarkerMap.isEmpty() && !isPlaceholderMapName(runtimeMarkerMap)) {
            String runtimeDisplay = MapConfigStore.displayNameFromWorldDirectory(runtimeMarkerMap);
            if (!runtimeDisplay.isEmpty()) {
                return runtimeDisplay;
            }
        }
        String fallbackConfigured = safeText(configuredMapDisplayName);
        if (!fallbackConfigured.isEmpty() && !isPlaceholderMapName(fallbackConfigured)) {
            return fallbackConfigured;
        }
        String canonical = safeText(active.getName());
        for (Map.Entry<String, String> entry : mapAliases.entrySet()) {
            if (entry == null) {
                continue;
            }
            String alias = safeText(entry.getKey());
            String mapped = safeText(entry.getValue());
            if (alias.isEmpty() || mapped.isEmpty() || isPlaceholderMapName(alias)) {
                continue;
            }
            if (mapped.equalsIgnoreCase(canonical)) {
                return MapConfigStore.displayNameFromWorldDirectory(alias);
            }
        }
        if (!canonical.isEmpty()) {
            return MapConfigStore.displayNameFromWorldDirectory(canonical);
        }
        return "";
    }

    public boolean setActiveMap(String name) {
        String resolved = resolveCanonicalMapName(name);
        if (resolved == null) {
            return false;
        }
        this.activeMapName = resolved;
        if (!containsIgnoreCase(rotation, resolved)) {
            rotation.add(resolved);
        }
        return true;
    }

    public void rotateToNextMap() {
        List<String> eligibleMaps = eligibleRotationMaps();
        if (eligibleMaps.isEmpty()) {
            return;
        }

        String current = safeText(activeMapName);
        List<String> candidates = new ArrayList<>();
        for (String mapName : eligibleMaps) {
            if (!current.equalsIgnoreCase(mapName)) {
                candidates.add(mapName);
            }
        }
        if (candidates.isEmpty()) {
            candidates = eligibleMaps;
        }
        String candidate = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        String resolved = resolveCanonicalMapName(candidate);
        activeMapName = resolved == null ? candidate : resolved;
    }

    public List<String> getMapNames() {
        List<String> names = new ArrayList<>();
        for (GameMap map : maps.values()) {
            names.add(map.getName());
        }
        Collections.sort(names);
        return names;
    }

    public Location getPregameSpawnForActiveMap() {
        String runtimePreferred = resolvePreferredMapFromRuntimeWorld();
        Location perMap = resolvePregameSpawnForMap(runtimePreferred);
        if (perMap != null) {
            return perMap.clone();
        }

        perMap = resolvePregameSpawnForMap(activeMapName);
        if (perMap != null) {
            return perMap.clone();
        }

        Location singleMapPregame = singleMapPregameSpawnForServerKind();
        if (singleMapPregame != null) {
            return singleMapPregame.clone();
        }

        if (pregameSpawn != null) {
            return pregameSpawn.clone();
        }
        GameMap active = getActiveMap();
        if (active == null || active.getSpawnPoints().isEmpty()) {
            return null;
        }
        Location firstSpawn = active.getSpawnPoints().get(0);
        return firstSpawn == null ? null : firstSpawn.clone();
    }

    private Location resolvePregameSpawnForMap(String rawMapName) {
        String resolved = resolveCanonicalMapName(rawMapName);
        String key = normalizeMapKey(resolved == null ? rawMapName : resolved);
        if (key.isEmpty()) {
            return null;
        }
        return pregameSpawns.get(key);
    }

    public void addSpawn(Location location) {
        // Disabled: spawns are managed by build tooling and persisted in MongoDB maps.
    }

    public void addDropItemSpawn(Location location) {
        // Disabled: spawns are managed by build tooling and persisted in MongoDB maps.
    }

    private MapConfig loadConfig() {
        Gson gson = new Gson();
        JsonObject root = loadRootFromMongo();
        if (root == null) {
            return null;
        }
        String gameKey = MapConfigStore.normalizeGameKey(plugin.getConfig().getString("server.group", ""));
        if (gameKey.isEmpty()) {
            return null;
        }
        return parseScopedMapConfig(root, gameKey, gson);
    }

    private JsonObject loadRootFromMongo() {
        if (plugin == null || plugin.getMongoManager() == null) {
            return null;
        }
        String gameKey = MapConfigStore.normalizeGameKey(plugin.getConfig().getString("server.group", ""));
        if (gameKey.isEmpty()) {
            return null;
        }
        MapConfigStore store = new MapConfigStore(plugin.getMongoManager());
        return store.loadRoot(gameKey);
    }

    private MapConfig parseScopedMapConfig(JsonObject root, String gameTypeKey, Gson gson) {
        if (root == null || gameTypeKey == null || gameTypeKey.trim().isEmpty() || gson == null) {
            return null;
        }
        JsonObject gameTypes = child(root, "gameTypes");
        JsonObject section = child(gameTypes, gameTypeKey);
        if (section == null) {
            return null;
        }
        return gson.fromJson(section, MapConfig.class);
    }

    private JsonObject child(JsonObject root, String key) {
        if (root == null || key == null || !root.has(key)) {
            return null;
        }
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonObject()) {
            return null;
        }
        return value.getAsJsonObject();
    }

    private Location toLocation(LocationEntry entry) {
        if (entry == null) {
            return null;
        }
        World world = resolveWorld(entry.world);
        if (world == null) {
            return null;
        }
        return new Location(world, entry.x, entry.y, entry.z, entry.yaw, entry.pitch);
    }

    private ItemStack toDropItemStack(LocationEntry entry) {
        Material material = Material.AIR;
        short data = 0;
        if (entry != null) {
            String configuredItem = safeText(entry.item);
            if (!configuredItem.isEmpty()) {
                Material exact = Material.matchMaterial(configuredItem);
                if (exact == null) {
                    exact = Material.matchMaterial(configuredItem.toUpperCase(Locale.ROOT));
                }
                if (exact != null) {
                    material = exact;
                }
            }
            int configuredData = entry.itemData;
            if (configuredData > 0) {
                data = (short) Math.min(Short.MAX_VALUE, configuredData);
            }
        }
        if (material == Material.AIR) {
            return null;
        }
        return new ItemStack(material, 1, data);
    }

    private World resolveWorld(String name) {
        String target = safeText(name);
        if (!target.isEmpty()) {
            World direct = Bukkit.getWorld(target);
            if (direct != null) {
                return direct;
            }
            for (World world : Bukkit.getWorlds()) {
                if (world == null || world.getName() == null) {
                    continue;
                }
                if (target.equalsIgnoreCase(world.getName())) {
                    return world;
                }
            }
        }
        World defaultWorld = Bukkit.getWorld("world");
        if (defaultWorld != null) {
            return defaultWorld;
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        return Bukkit.getWorlds().get(0);
    }

    private String getFirstMapName() {
        for (GameMap map : maps.values()) {
            if (map == null || map.getName() == null) {
                continue;
            }
            if (!isPlaceholderMapName(map.getName())) {
                return map.getName();
            }
        }
        for (GameMap map : maps.values()) {
            if (map == null || map.getName() == null) {
                continue;
            }
            return map.getName();
        }
        return null;
    }

    private String resolvePreferredActiveMapName(MapConfig config) {
        String runtimePreferred = resolvePreferredMapFromRuntimeWorld();
        if (runtimePreferred != null && !isPlaceholderMapName(runtimePreferred)) {
            return runtimePreferred;
        }

        String configured = config == null ? "" : safeText(config.activeMap);
        String preferred = firstMatchingMapForServerKind(configured);
        if (preferred != null && !isPlaceholderMapName(preferred)) {
            return preferred;
        }

        if (rotation != null) {
            for (String mapName : rotation) {
                preferred = firstMatchingMapForServerKind(mapName);
                if (preferred != null && !isPlaceholderMapName(preferred)) {
                    return preferred;
                }
            }
        }

        String fallback = configured;
        if (fallback.isEmpty() && rotation != null && !rotation.isEmpty()) {
            fallback = safeText(rotation.get(0));
        }
        String resolvedFallback = resolveCanonicalMapName(fallback);
        if (resolvedFallback != null && !isPlaceholderMapName(resolvedFallback)) {
            return resolvedFallback;
        }
        if (runtimePreferred != null) {
            return runtimePreferred;
        }
        if (resolvedFallback != null) {
            return resolvedFallback;
        }
        return getFirstMapName();
    }

    private void registerConfiguredAliases(MapConfig config) {
        if (config == null) {
            return;
        }
        List<String> configuredNames = new ArrayList<>();
        configuredNames.add(safeText(config.activeMap));
        if (config.rotation != null) {
            configuredNames.addAll(config.rotation);
        }
        for (String raw : configuredNames) {
            String alias = safeText(raw);
            if (alias.isEmpty() || isPlaceholderMapName(alias)) {
                continue;
            }
            if (mapAliases.containsKey(normalizeMapKey(alias))) {
                continue;
            }
            String mapped = resolveCanonicalMapName(alias);
            if (mapped == null || mapped.isEmpty()) {
                mapped = singleMapForServerKind();
            }
            if (mapped == null || mapped.isEmpty()) {
                continue;
            }
            registerMapAlias(alias, mapped);
        }
    }

    private String resolveConfiguredDisplayName(MapConfig config, String preferredActiveMap) {
        if (config == null) {
            return "";
        }
        List<String> configuredNames = new ArrayList<>();
        configuredNames.add(safeText(config.activeMap));
        if (config.rotation != null) {
            configuredNames.addAll(config.rotation);
        }
        String preferred = safeText(preferredActiveMap);
        for (String raw : configuredNames) {
            String alias = safeText(raw);
            if (alias.isEmpty() || isPlaceholderMapName(alias)) {
                continue;
            }
            String mapped = resolveCanonicalMapName(alias);
            if (!preferred.isEmpty() && mapped != null && !mapped.equalsIgnoreCase(preferred)) {
                continue;
            }
            return MapConfigStore.displayNameFromWorldDirectory(alias);
        }
        return "";
    }

    private String resolvePreferredMapFromRuntimeWorld() {
        String markerMap = readMapNameFromWorldMarker();
        if (!markerMap.isEmpty()) {
            String byMarker = firstMatchingMapForServerKind(markerMap);
            if (byMarker != null) {
                return byMarker;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            if (world == null) {
                continue;
            }
            String worldName = safeText(world.getName());
            if (worldName.isEmpty() || isPlaceholderMapName(worldName)) {
                continue;
            }
            String byWorldName = firstMatchingMapForServerKind(worldName);
            if (byWorldName != null) {
                return byWorldName;
            }
        }
        return null;
    }

    private String readMapNameFromWorldMarker() {
        for (World world : Bukkit.getWorlds()) {
            if (world == null || world.getWorldFolder() == null) {
                continue;
            }
            Path markerPath = world.getWorldFolder().toPath().resolve(".hycopy-map-source");
            if (!Files.exists(markerPath)) {
                continue;
            }
            try {
                String raw = safeText(new String(Files.readAllBytes(markerPath), StandardCharsets.UTF_8));
                if (raw.isEmpty()) {
                    continue;
                }
                int slash = raw.lastIndexOf('/');
                String mapName = slash >= 0 ? raw.substring(slash + 1) : raw;
                mapName = safeText(mapName);
                if (!mapName.isEmpty()) {
                    return mapName;
                }
            } catch (IOException ignored) {
                // Best-effort marker resolution.
            }
        }
        return "";
    }

    private List<String> eligibleRotationMaps() {
        List<String> eligible = new ArrayList<>();
        if (rotation == null) {
            return eligible;
        }
        for (String configured : rotation) {
            String resolved = firstMatchingMapForServerKind(configured);
            if (resolved == null || isPlaceholderMapName(resolved) || containsIgnoreCase(eligible, resolved)) {
                continue;
            }
            eligible.add(resolved);
        }
        return eligible;
    }

    private String firstMatchingMapForServerKind(String mapName) {
        String candidate = resolveCanonicalMapName(mapName);
        if (candidate == null) {
            return null;
        }
        GameMap map = maps.get(normalizeMapKey(candidate));
        if (map == null || map.getName() == null) {
            return null;
        }
        ServerType type = plugin.getServerType() == null ? ServerType.UNKNOWN : plugin.getServerType();
        if (type == ServerType.UNKNOWN) {
            return map.getName();
        }
        boolean hubMap = MapConfigStore.isHubMapName(map.getName());
        if (type.isHub() && hubMap) {
            return map.getName();
        }
        if (type.isGame() && !hubMap) {
            return map.getName();
        }
        return null;
    }

    private String singleMapForServerKind() {
        String only = null;
        for (GameMap map : maps.values()) {
            if (map == null || map.getName() == null) {
                continue;
            }
            ServerType type = plugin.getServerType() == null ? ServerType.UNKNOWN : plugin.getServerType();
            if (type != ServerType.UNKNOWN) {
                boolean hubMap = MapConfigStore.isHubMapName(map.getName());
                if (type.isHub() && !hubMap) {
                    continue;
                }
                if (type.isGame() && hubMap) {
                    continue;
                }
            }
            if (only != null) {
                return null;
            }
            only = map.getName();
        }
        return only;
    }

    private Location singleMapPregameSpawnForServerKind() {
        Location only = null;
        for (Map.Entry<String, Location> entry : pregameSpawns.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            GameMap map = maps.get(normalizeMapKey(entry.getKey()));
            if (map == null || map.getName() == null) {
                continue;
            }
            ServerType type = plugin.getServerType() == null ? ServerType.UNKNOWN : plugin.getServerType();
            if (type != ServerType.UNKNOWN) {
                boolean hubMap = MapConfigStore.isHubMapName(map.getName());
                if (type.isHub() && !hubMap) {
                    continue;
                }
                if (type.isGame() && hubMap) {
                    continue;
                }
            }
            if (only != null) {
                return null;
            }
            only = entry.getValue();
        }
        return only;
    }

    private void registerMapAlias(String alias, String canonicalMapName) {
        String key = normalizeMapKey(alias);
        String canonical = safeText(canonicalMapName);
        if (key.isEmpty() || canonical.isEmpty() || isPlaceholderMapName(key)) {
            return;
        }
        mapAliases.put(key, canonical);
    }

    private boolean isPlaceholderMapName(String mapName) {
        String normalized = normalizeMapKey(mapName);
        if (normalized.isEmpty()) {
            return false;
        }
        return "world".equals(normalized)
                || "world_nether".equals(normalized)
                || "world_the_end".equals(normalized)
                || "default".equals(normalized);
    }

    private void registerPregameAlias(String alias, Location location) {
        if (location == null) {
            return;
        }
        String aliasKey = normalizeMapKey(alias);
        if (aliasKey.isEmpty()) {
            return;
        }
        String canonical = mapAliases.get(aliasKey);
        if (canonical == null) {
            return;
        }
        pregameSpawns.put(normalizeMapKey(canonical), location);
    }

    private String resolveCanonicalMapName(String raw) {
        String key = normalizeMapKey(raw);
        if (key.isEmpty()) {
            return null;
        }
        GameMap direct = maps.get(key);
        if (direct != null && direct.getName() != null) {
            return direct.getName();
        }
        String alias = mapAliases.get(key);
        return alias == null || alias.trim().isEmpty() ? null : alias;
    }

    private String normalizeMapKey(String raw) {
        String value = safeText(raw);
        if (value.isEmpty()) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_");
        while (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null || target.trim().isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (value != null && target.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static class MapConfig {
        private List<MapEntry> maps;
        private List<String> rotation;
        private String activeMap;
        private LocationEntry pregameSpawn;
    }

    private static class MapEntry {
        private String name;
        private String worldDirectory;
        private Boolean nightTime;
        private List<LocationEntry> spawns;
        private List<LocationEntry> dropItem;
        private List<LocationEntry> mysteryPotions;
        private LocationEntry pregameSpawn;
    }

    private static class LocationEntry {
        private String world;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private String item;
        private int itemData;
    }
}
