package io.github.mebsic.murdermystery.service;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.game.model.GameState;
import io.github.mebsic.murdermystery.game.MurderMysteryGamePlayer;
import io.github.mebsic.murdermystery.game.MurderMysteryRole;
import io.github.mebsic.murdermystery.manager.MurderMysteryGameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MurderMysteryMinimapService {
    public static final int HOTBAR_SLOT = 4;

    private static final String DISPLAY_NAME = ChatColor.GREEN + "Minimap";
    private static final String LORE_LINE = ChatColor.GRAY + "Use this Map to navigate in the world.";
    private static final int PLAYER_REVEAL_REMAINING_SECONDS = 30;
    private static final int PLAYER_REVEAL_ALIVE_NON_MURDERERS = 2;
    private static final double PLAYER_REVEAL_MAX_Y_DELTA = 5.0D;
    private static final long REFRESH_PERIOD_TICKS = 1L;
    private static final int MAP_CURSOR_MIN = -128;
    private static final int MAP_CURSOR_MAX = 127;

    private final CorePlugin plugin;
    private final MurderMysteryGameManager gameManager;
    private final Map<UUID, PlayerMinimap> playerMinimaps;
    private final Set<UUID> mapEligiblePlayers;
    private BukkitTask refreshTask;

    public MurderMysteryMinimapService(CorePlugin plugin, MurderMysteryGameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.playerMinimaps = new HashMap<>();
        this.mapEligiblePlayers = new HashSet<>();
    }

    public void start() {
        if (plugin == null || refreshTask != null) {
            return;
        }
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refreshCarriedMaps,
                REFRESH_PERIOD_TICKS,
                REFRESH_PERIOD_TICKS
        );
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        resetAll();
    }

    public void giveMap(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        start();
        if (!canCarryMap(player)) {
            removeMap(player);
            return;
        }
        PlayerMinimap minimap = getOrCreateMinimap(player);
        if (minimap == null) {
            return;
        }
        mapEligiblePlayers.add(player.getUniqueId());
        configureMapView(minimap, player);
        clearMapItems(player);
        player.getInventory().setItem(HOTBAR_SLOT, createMapItem(minimap.mapView));
        player.updateInventory();
        sendMap(player, minimap.mapView);
    }

    public void removeMap(Player player) {
        if (player == null) {
            return;
        }
        mapEligiblePlayers.remove(player.getUniqueId());
        if (clearMapItems(player)) {
            player.updateInventory();
        }
    }

    private boolean clearMapItems(Player player) {
        if (player == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        if (inventory == null) {
            return false;
        }
        boolean changed = false;
        ItemStack[] contents = inventory.getContents();
        if (contents == null) {
            return false;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            if (!isMinimapItem(contents[slot])) {
                continue;
            }
            inventory.setItem(slot, null);
            changed = true;
        }
        return changed;
    }

    public void clearAll() {
        Set<UUID> uuids = new HashSet<>(playerMinimaps.keySet());
        uuids.addAll(mapEligiblePlayers);
        if (gameManager != null) {
            for (MurderMysteryGamePlayer mmPlayer : gameManager.getMurderMysteryPlayersSnapshot()) {
                if (mmPlayer != null) {
                    uuids.add(mmPlayer.getUuid());
                }
            }
        }
        for (UUID uuid : uuids) {
            mapEligiblePlayers.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeMap(player);
            }
        }
    }

    public void resetAll() {
        clearAll();
        removeRenderers();
        mapEligiblePlayers.clear();
        playerMinimaps.clear();
    }

    private void refreshCarriedMaps() {
        pruneOfflineMinimaps();
        if (gameManager == null) {
            return;
        }
        GameState state = gameManager.getState();
        if (state == GameState.ENDING) {
            resetAll();
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline() || !gameManager.isInGame(player)) {
                continue;
            }
            ItemStack heldSlotItem = player.getInventory().getItem(HOTBAR_SLOT);
            boolean hasMinimap = isMinimapItem(heldSlotItem);
            if (!canCarryMap(player)) {
                removeMap(player);
                continue;
            }
            UUID playerUuid = player.getUniqueId();
            PlayerMinimap minimap = playerMinimaps.get(playerUuid);
            boolean shouldMaintainMap = mapEligiblePlayers.contains(playerUuid)
                    || state == GameState.WAITING
                    || state == GameState.STARTING
                    || state == GameState.IN_GAME;
            if (shouldMaintainMap && (!hasMinimap || !isAssignedMinimapItem(heldSlotItem, minimap))) {
                giveMap(player);
                continue;
            }
            if (!hasMinimap) {
                continue;
            }
            if (minimap == null) {
                giveMap(player);
                continue;
            }
            configureMapView(minimap, player);
            sendMap(player, minimap.mapView);
        }
    }

    private void pruneOfflineMinimaps() {
        if (playerMinimaps.isEmpty()) {
            return;
        }
        List<UUID> stale = new ArrayList<>();
        for (UUID uuid : playerMinimaps.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                stale.add(uuid);
            }
        }
        for (UUID uuid : stale) {
            removeRenderer(playerMinimaps.remove(uuid));
            mapEligiblePlayers.remove(uuid);
        }
    }

    private boolean canCarryMap(Player player) {
        if (player == null || gameManager == null || !gameManager.isInGame(player)) {
            return false;
        }
        GameState state = gameManager.getState();
        if (state == GameState.WAITING || state == GameState.STARTING) {
            return true;
        }
        if (state != GameState.IN_GAME) {
            return false;
        }
        MurderMysteryGamePlayer mmPlayer = gameManager.getMurderMysteryPlayer(player);
        return mmPlayer != null && mmPlayer.isAlive();
    }

    private PlayerMinimap getOrCreateMinimap(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        PlayerMinimap existing = playerMinimaps.get(uuid);
        if (existing != null) {
            return existing;
        }
        MapView mapView = Bukkit.createMap(player.getWorld());
        MinimapMarkerRenderer renderer = new MinimapMarkerRenderer(this);
        mapView.addRenderer(renderer);
        PlayerMinimap created = new PlayerMinimap(mapView, renderer);
        playerMinimaps.put(uuid, created);
        return created;
    }

    private void removeRenderers() {
        for (PlayerMinimap minimap : playerMinimaps.values()) {
            removeRenderer(minimap);
        }
    }

    private void removeRenderer(PlayerMinimap minimap) {
        if (minimap == null || minimap.mapView == null || minimap.renderer == null) {
            return;
        }
        minimap.mapView.removeRenderer(minimap.renderer);
    }

    private void configureMapView(PlayerMinimap minimap, Player player) {
        if (minimap == null || minimap.mapView == null || player == null || player.getWorld() == null) {
            return;
        }
        Location location = player.getLocation();
        if (location == null) {
            return;
        }
        updateFollowCenter(minimap, player.getWorld(), location);
        applyMapView(minimap.mapView, player.getWorld(), minimap.centerX, minimap.centerZ);
    }

    private void configureMapView(MapView mapView, Player player) {
        if (mapView == null || player == null || player.getWorld() == null) {
            return;
        }
        Location location = player.getLocation();
        if (location == null) {
            return;
        }
        applyMapView(mapView, player.getWorld(), location.getBlockX(), location.getBlockZ());
    }

    private void applyMapView(MapView mapView, World world, int centerX, int centerZ) {
        if (mapView == null || world == null) {
            return;
        }
        mapView.setWorld(world);
        mapView.setCenterX(centerX);
        mapView.setCenterZ(centerZ);
        mapView.setScale(MapView.Scale.CLOSEST);
    }

    private void updateFollowCenter(PlayerMinimap minimap, World world, Location location) {
        if (minimap == null || world == null || location == null) {
            return;
        }
        minimap.centerX = location.getBlockX();
        minimap.centerZ = location.getBlockZ();
    }

    private boolean shouldRevealRemainingPlayers() {
        if (gameManager.getState() != GameState.IN_GAME) {
            return false;
        }
        if (gameManager.getRemainingGameSeconds() <= PLAYER_REVEAL_REMAINING_SECONDS) {
            return true;
        }
        int aliveNonMurderers = 0;
        for (MurderMysteryGamePlayer mmPlayer : gameManager.getMurderMysteryPlayersSnapshot()) {
            if (mmPlayer != null && mmPlayer.isAlive() && mmPlayer.getRole() != MurderMysteryRole.MURDERER) {
                aliveNonMurderers++;
            }
        }
        return aliveNonMurderers > 0 && aliveNonMurderers <= PLAYER_REVEAL_ALIVE_NON_MURDERERS;
    }

    private void render(MapView mapView, MapCanvas canvas, Player viewer) {
        if (mapView == null || canvas == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        MapCursorCollection cursors = new MapCursorCollection();
        if (!canCarryMap(viewer)) {
            canvas.setCursors(cursors);
            return;
        }
        PlayerMinimap minimap = playerMinimaps.get(viewer.getUniqueId());
        if (minimap != null && minimap.mapView == mapView) {
            configureMapView(minimap, viewer);
        } else {
            configureMapView(mapView, viewer);
        }
        addLocationCursor(cursors, mapView, viewer.getLocation(), viewerDirection(viewer), MapCursor.Type.GREEN_POINTER);
        addDroppedBowCursor(cursors, mapView);
        addRemainingPlayerCursors(cursors, mapView, viewer);
        canvas.setCursors(cursors);
    }

    private void addDroppedBowCursor(MapCursorCollection cursors, MapView mapView) {
        Location droppedBow = gameManager.getDroppedBowLocation();
        if (droppedBow == null) {
            return;
        }
        addLocationCursor(cursors, mapView, droppedBow, (byte) 0, MapCursor.Type.BLUE_POINTER);
    }

    private void addRemainingPlayerCursors(MapCursorCollection cursors, MapView mapView, Player viewer) {
        if (!shouldRevealRemainingPlayers() || viewer == null) {
            return;
        }
        for (MurderMysteryGamePlayer mmPlayer : gameManager.getMurderMysteryPlayersSnapshot()) {
            if (mmPlayer == null) {
                continue;
            }
            Player target = Bukkit.getPlayer(mmPlayer.getUuid());
            if (!isVisibleRemainingPlayerForViewer(viewer, mmPlayer, target)) {
                continue;
            }
            addLocationCursor(cursors, mapView, target.getLocation(), viewerDirection(target), MapCursor.Type.RED_POINTER);
        }
    }

    private boolean isVisibleRemainingPlayerForViewer(Player viewer, MurderMysteryGamePlayer mmPlayer, Player target) {
        if (viewer == null || mmPlayer == null || !mmPlayer.isAlive() || target == null || !target.isOnline()) {
            return false;
        }
        if (viewer.getUniqueId().equals(mmPlayer.getUuid())) {
            return false;
        }
        Location viewerLocation = viewer.getLocation();
        Location targetLocation = target.getLocation();
        if (!sameWorld(viewerLocation, targetLocation)) {
            return false;
        }
        return Math.abs(targetLocation.getY() - viewerLocation.getY()) <= PLAYER_REVEAL_MAX_Y_DELTA;
    }

    private void addLocationCursor(MapCursorCollection cursors,
                                   MapView mapView,
                                   Location location,
                                   byte direction,
                                   MapCursor.Type type) {
        CursorPosition position = cursorPosition(mapView, location);
        if (position == null) {
            return;
        }
        MapCursor.Type cursorType = type == null ? MapCursor.Type.WHITE_POINTER : type;
        MapCursor cursor = cursors.addCursor(position.x, position.y, direction);
        cursor.setType(cursorType);
        cursor.setVisible(true);
    }

    private CursorPosition cursorPosition(MapView mapView, Location location) {
        if (mapView == null || location == null || location.getWorld() == null || mapView.getWorld() == null) {
            return null;
        }
        if (!sameWorld(mapView.getWorld(), location.getWorld())) {
            return null;
        }
        double blocksPerPixel = blocksPerPixel(mapView.getScale());
        int cursorX = (int) Math.round(((location.getX() - mapView.getCenterX()) / blocksPerPixel) * 2.0D);
        int cursorY = (int) Math.round(((location.getZ() - mapView.getCenterZ()) / blocksPerPixel) * 2.0D);
        return new CursorPosition(clampCursorCoordinate(cursorX), clampCursorCoordinate(cursorY));
    }

    private int clampCursorCoordinate(int coordinate) {
        if (coordinate < MAP_CURSOR_MIN) {
            return MAP_CURSOR_MIN;
        }
        if (coordinate > MAP_CURSOR_MAX) {
            return MAP_CURSOR_MAX;
        }
        return coordinate;
    }

    private double blocksPerPixel(MapView.Scale scale) {
        if (scale == MapView.Scale.CLOSE) {
            return 2.0D;
        }
        if (scale == MapView.Scale.NORMAL) {
            return 4.0D;
        }
        if (scale == MapView.Scale.FAR) {
            return 8.0D;
        }
        if (scale == MapView.Scale.FARTHEST) {
            return 16.0D;
        }
        return 1.0D;
    }

    private byte viewerDirection(Player player) {
        if (player == null) {
            return 0;
        }
        return (byte) ((int) Math.floor((player.getLocation().getYaw() * 16.0F / 360.0F) + 0.5D) & 15);
    }

    private void sendMap(Player player, MapView mapView) {
        if (player == null || mapView == null) {
            return;
        }
        try {
            player.sendMap(mapView);
        } catch (Throwable ignored) {
            // Some server implementations throttle or reject explicit map sends.
        }
    }

    private ItemStack createMapItem(MapView mapView) {
        Material material = filledMapMaterial();
        ItemStack item = new ItemStack(material == null ? Material.MAP : material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DISPLAY_NAME);
            meta.setLore(Collections.singletonList(LORE_LINE));
            item.setItemMeta(meta);
        }
        Integer mapId = readMapId(mapView);
        if (mapView != null && !applyMapViewMeta(item, mapView, mapId) && mapId != null) {
            item.setDurability(mapId.shortValue());
        }
        return item;
    }

    public boolean isMinimapItem(ItemStack item) {
        if (item == null || !isFilledMapMaterial(item.getType()) || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !DISPLAY_NAME.equals(meta.getDisplayName())) {
            return false;
        }
        List<String> lore = meta.getLore();
        return lore != null && !lore.isEmpty() && LORE_LINE.equals(lore.get(0));
    }

    private boolean isAssignedMinimapItem(ItemStack item, PlayerMinimap minimap) {
        if (!isMinimapItem(item) || minimap == null || minimap.mapView == null) {
            return false;
        }
        Integer expectedMapId = readMapId(minimap.mapView);
        Integer heldMapId = readMapItemId(item);
        return expectedMapId == null || heldMapId == null || expectedMapId.equals(heldMapId);
    }

    private boolean applyMapViewMeta(ItemStack item, MapView mapView, Integer mapId) {
        if (item == null || mapView == null) {
            return false;
        }
        try {
            Object meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }
            try {
                meta.getClass().getMethod("setMapView", MapView.class).invoke(meta, mapView);
                item.setItemMeta((ItemMeta) meta);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
            if (mapId == null) {
                return false;
            }
            meta.getClass().getMethod("setMapId", int.class).invoke(meta, (int) mapId);
            item.setItemMeta((ItemMeta) meta);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Integer readMapId(MapView mapView) {
        if (mapView == null) {
            return null;
        }
        try {
            Object id = MapView.class.getMethod("getId").invoke(mapView);
            if (id instanceof Number) {
                return ((Number) id).intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer readMapItemId(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Integer mapMetaId = readMapItemIdFromMeta(meta);
            if (mapMetaId != null) {
                return mapMetaId;
            }
        }
        try {
            Object durability = ItemStack.class.getMethod("getDurability").invoke(item);
            if (durability instanceof Number) {
                return ((Number) durability).intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer readMapItemIdFromMeta(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        try {
            Object mapView = meta.getClass().getMethod("getMapView").invoke(meta);
            if (mapView instanceof MapView) {
                return readMapId((MapView) mapView);
            }
        } catch (Exception ignored) {
        }
        try {
            Object mapId = meta.getClass().getMethod("getMapId").invoke(meta);
            if (mapId instanceof Number) {
                return ((Number) mapId).intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Material filledMapMaterial() {
        try {
            return Material.valueOf("FILLED_MAP");
        } catch (Exception ignored) {
            return Material.MAP;
        }
    }

    private boolean isFilledMapMaterial(Material material) {
        if (material == null) {
            return false;
        }
        return material == Material.MAP || "FILLED_MAP".equals(material.name());
    }

    private boolean sameWorld(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }
        return sameWorld(first.getWorld(), second.getWorld());
    }

    private boolean sameWorld(World first, World second) {
        if (first == null || second == null) {
            return false;
        }
        return first.getName().equalsIgnoreCase(second.getName());
    }

    private static final class PlayerMinimap {
        private final MapView mapView;
        private final MapRenderer renderer;
        private int centerX;
        private int centerZ;

        private PlayerMinimap(MapView mapView, MapRenderer renderer) {
            this.mapView = mapView;
            this.renderer = renderer;
        }
    }

    private static final class MinimapMarkerRenderer extends MapRenderer {
        private final MurderMysteryMinimapService service;

        private MinimapMarkerRenderer(MurderMysteryMinimapService service) {
            super(true);
            this.service = service;
        }

        @Override
        public void render(MapView mapView, MapCanvas canvas, Player player) {
            service.render(mapView, canvas, player);
        }
    }

    private static final class CursorPosition {
        private final int x;
        private final int y;

        private CursorPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
