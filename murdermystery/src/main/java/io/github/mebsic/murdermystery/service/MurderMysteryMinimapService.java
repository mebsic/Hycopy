package io.github.mebsic.murdermystery.service;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.game.map.GameMap;
import io.github.mebsic.game.model.GameState;
import io.github.mebsic.murdermystery.game.MurderMysteryGamePlayer;
import io.github.mebsic.murdermystery.game.MurderMysteryRole;
import io.github.mebsic.murdermystery.manager.MurderMysteryGameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final long MAP_FIRST_RESEND_DELAY_TICKS = 1L;
    private static final long MAP_JOIN_RESEND_DELAY_TICKS = 20L;
    private static final int MAP_PIXEL_SIZE = 128;
    private static final MapView.Scale STATIC_MAP_SCALE = MapView.Scale.CLOSEST;
    private static final int MAP_CURSOR_MIN = -128;
    private static final int MAP_CURSOR_MAX = 127;
    private static final int PALETTE_LIGHT_GREEN = 4;
    private static final int PALETTE_LIGHT_BROWN = 8;
    private static final int PALETTE_GRAY = 12;
    private static final int PALETTE_RED = 16;
    private static final int PALETTE_PALE_BLUE = 20;
    private static final int PALETTE_DARK_GREEN = 28;
    private static final int PALETTE_WHITE = 32;
    private static final int PALETTE_LIGHT_GRAY = 36;
    private static final int PALETTE_BROWN = 40;
    private static final int PALETTE_DARK_GRAY = 44;
    private static final int PALETTE_BLUE = 48;
    private static final int PALETTE_DARK_BROWN = 52;
    private static final int PALETTE_ORANGE = 60;
    private static final int PALETTE_YELLOW = 72;
    private static final int PALETTE_BLACK = 116;
    private static final int PALETTE_CYAN = 124;
    private static final int PALETTE_DARK_RED = 136;

    private final CorePlugin plugin;
    private final MurderMysteryGameManager gameManager;
    private final Map<UUID, PlayerMinimap> playerMinimaps;
    private final Map<TerrainCacheKey, TerrainSnapshot> terrainSnapshots;
    private final Set<UUID> mapEligiblePlayers;
    private final MurderMysteryMapPacketSender mapPacketSender;
    private final MurderMysteryMapStateWriter mapStateWriter;
    private boolean loggedMapPacketFallback;
    private boolean loggedEmptyTerrainCapture;
    private long mapSessionId;
    private BukkitTask refreshTask;

    public MurderMysteryMinimapService(CorePlugin plugin, MurderMysteryGameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.playerMinimaps = new HashMap<>();
        this.terrainSnapshots = new HashMap<>();
        this.mapEligiblePlayers = new HashSet<>();
        this.mapPacketSender = MurderMysteryMapPacketSender.create();
        this.mapStateWriter = MurderMysteryMapStateWriter.create();
        this.loggedMapPacketFallback = false;
        this.loggedEmptyTerrainCapture = false;
        this.mapSessionId = 1L;
    }

    public void start() {
        if (plugin == null || !plugin.isEnabled() || refreshTask != null) {
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
        applyMinimapItem(player, minimap);
        sendMap(player, minimap);
        scheduleMapSend(player.getUniqueId(), minimap.mapView, minimap.sessionId, MAP_FIRST_RESEND_DELAY_TICKS);
        scheduleMapSend(player.getUniqueId(), minimap.mapView, minimap.sessionId, MAP_JOIN_RESEND_DELAY_TICKS);
    }

    public void removeMap(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        mapEligiblePlayers.remove(uuid);
        PlayerMinimap minimap = playerMinimaps.remove(uuid);
        retireMinimap(player, minimap);
        removeRenderer(minimap);
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

    private void applyMinimapItem(Player player, PlayerMinimap minimap) {
        if (player == null || minimap == null || minimap.mapView == null) {
            return;
        }
        clearMapItems(player);
        player.getInventory().setItem(HOTBAR_SLOT, createMapItem(minimap.mapView));
        player.updateInventory();
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
        mapSessionId++;
        clearAll();
        removeRenderers();
        mapEligiblePlayers.clear();
        playerMinimaps.clear();
        terrainSnapshots.clear();
        loggedEmptyTerrainCapture = false;
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
            sendMap(player, minimap);
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
            MapAnchor anchor = resolveMapAnchor(existing, player);
            if (existing.sessionId == mapSessionId && isReusableMinimap(existing, anchor)) {
                return existing;
            }
            removeRenderer(existing);
            playerMinimaps.remove(uuid);
        }
        MapAnchor anchor = resolveMapAnchor(player);
        World mapWorld = anchor == null || anchor.world == null ? player.getWorld() : anchor.world;
        MapView mapView = Bukkit.createMap(mapWorld);
        applyMapView(mapView, anchor);
        for (MapRenderer existingRenderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(existingRenderer);
        }
        MinimapMarkerRenderer renderer = new MinimapMarkerRenderer(this);
        mapView.addRenderer(renderer);
        PlayerMinimap created = new PlayerMinimap(mapView, renderer, mapSessionId);
        playerMinimaps.put(uuid, created);
        configureMapView(created, player);
        primeStaticMapState(created, player);
        return created;
    }

    private boolean isReusableMinimap(PlayerMinimap minimap, MapAnchor anchor) {
        if (minimap == null || minimap.mapView == null || minimap.renderer == null) {
            return false;
        }
        List<MapRenderer> renderers = minimap.mapView.getRenderers();
        return renderers.size() == 1
                && renderers.get(0) == minimap.renderer
                && isMapViewOnAnchor(minimap.mapView, anchor);
    }

    private boolean isMapViewOnAnchor(MapView mapView, MapAnchor anchor) {
        return mapView != null
                && anchor != null
                && anchor.world != null
                && sameWorld(mapView.getWorld(), anchor.world)
                && mapView.getCenterX() == anchor.centerX
                && mapView.getCenterZ() == anchor.centerZ
                && mapView.getScale() == anchor.scale;
    }

    private void removeRenderers() {
        for (PlayerMinimap minimap : playerMinimaps.values()) {
            removeRenderer(minimap);
        }
    }

    private void removeRenderer(PlayerMinimap minimap) {
        if (minimap == null || minimap.mapView == null) {
            return;
        }
        for (MapRenderer renderer : new ArrayList<>(minimap.mapView.getRenderers())) {
            minimap.mapView.removeRenderer(renderer);
        }
    }

    private void retireMinimap(Player player, PlayerMinimap minimap) {
        if (player == null || minimap == null || minimap.mapView == null) {
            return;
        }
        byte[] pixels = blankPixels();
        if (mapStateWriter != null) {
            mapStateWriter.write(minimap.mapView, pixels);
        }
        Integer mapId = readMapId(minimap.mapView);
        if (mapPacketSender != null && mapId != null) {
            mapPacketSender.send(
                    player,
                    mapId,
                    (byte) scaleValue(minimap.mapView.getScale()),
                    pixels,
                    new MapCursorCollection()
            );
        }
    }

    private byte[] blankPixels() {
        byte[] pixels = new byte[MAP_PIXEL_SIZE * MAP_PIXEL_SIZE];
        Arrays.fill(pixels, shadePalette(PALETTE_LIGHT_BROWN, 1));
        return pixels;
    }

    private void configureMapView(PlayerMinimap minimap, Player player) {
        if (minimap == null || minimap.mapView == null || player == null || player.getWorld() == null) {
            return;
        }
        MapAnchor anchor = resolveMapAnchor(minimap, player);
        applyMapView(minimap.mapView, anchor);
        syncMinimapAnchor(minimap, anchor);
    }

    private void configureMapView(MapView mapView, Player player) {
        if (mapView == null || player == null || player.getWorld() == null) {
            return;
        }
        MapAnchor anchor = activeMapAnchor();
        if (anchor != null) {
            applyMapView(mapView, anchor);
        }
    }

    private MapAnchor resolveMapAnchor(PlayerMinimap minimap, Player player) {
        MapAnchor activeMapAnchor = activeMapAnchor();
        if (activeMapAnchor != null) {
            return activeMapAnchor;
        }
        if (minimap.fallbackAnchor == null || !sameWorld(minimap.fallbackAnchor.world, player.getWorld())) {
            minimap.fallbackAnchor = playerAnchor(player);
        }
        return minimap.fallbackAnchor;
    }

    private MapAnchor resolveMapAnchor(Player player) {
        MapAnchor activeMapAnchor = activeMapAnchor();
        if (activeMapAnchor != null) {
            return activeMapAnchor;
        }
        return playerAnchor(player);
    }

    private MapAnchor playerAnchor(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        Location location = player.getLocation();
        if (location == null) {
            return null;
        }
        return new MapAnchor(player.getWorld(), location.getBlockX(), location.getBlockZ(), STATIC_MAP_SCALE);
    }

    private MapAnchor activeMapAnchor() {
        GameMap activeMap = gameManager == null ? null : gameManager.getActiveGameMap();
        if (activeMap == null) {
            return null;
        }
        List<Location> locations = new ArrayList<>();
        locations.addAll(activeMap.getSpawnPoints());
        locations.addAll(activeMap.getDropItemSpawns());
        locations.addAll(activeMap.getMysteryPotionLocations());
        World world = null;
        int minX = 0;
        int maxX = 0;
        int minZ = 0;
        int maxZ = 0;
        boolean initialized = false;
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) {
                continue;
            }
            if (world == null) {
                world = location.getWorld();
            }
            if (!sameWorld(world, location.getWorld())) {
                continue;
            }
            int x = location.getBlockX();
            int z = location.getBlockZ();
            if (!initialized) {
                minX = x;
                maxX = x;
                minZ = z;
                maxZ = z;
                initialized = true;
                continue;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        if (!initialized || world == null) {
            return null;
        }
        int centerX = (int) Math.round((minX + maxX) / 2.0D);
        int centerZ = (int) Math.round((minZ + maxZ) / 2.0D);
        return new MapAnchor(world, centerX, centerZ, STATIC_MAP_SCALE);
    }

    private void applyMapView(MapView mapView, MapAnchor anchor) {
        if (mapView == null || anchor == null || anchor.world == null) {
            return;
        }
        mapView.setWorld(anchor.world);
        mapView.setCenterX(anchor.centerX);
        mapView.setCenterZ(anchor.centerZ);
        mapView.setScale(anchor.scale);
    }

    private void syncMinimapAnchor(PlayerMinimap minimap, MapAnchor anchor) {
        TerrainCacheKey anchorKey = TerrainCacheKey.from(anchor, mapSessionId);
        if (minimap == null || anchorKey == null || anchorKey.equals(minimap.anchorKey)) {
            return;
        }
        minimap.anchorKey = anchorKey;
        minimap.renderedSampledTerrainVersion = -1L;
        minimap.renderedSampledTerrainKey = null;
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
        return aliveNonMurderers == PLAYER_REVEAL_ALIVE_NON_MURDERERS;
    }

    private void render(MapView mapView, MapCanvas canvas, Player viewer) {
        if (mapView == null || canvas == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        if (!canCarryMap(viewer)) {
            resetCursors(canvas);
            return;
        }
        PlayerMinimap minimap = playerMinimaps.get(viewer.getUniqueId());
        if (minimap != null && minimap.mapView == mapView) {
            configureMapView(minimap, viewer);
            paintTerrain(minimap, canvas, viewer);
        } else {
            configureMapView(mapView, viewer);
            paintActiveMapTerrain(canvas);
        }
        copyCursors(resetCursors(canvas), createCursors(mapView, viewer));
    }

    private MapCursorCollection createCursors(MapView mapView, Player viewer) {
        MapCursorCollection cursors = new MapCursorCollection();
        if (mapView == null || viewer == null || !viewer.isOnline() || !canCarryMap(viewer)) {
            return cursors;
        }
        addLocationCursor(cursors, mapView, viewer.getLocation(), viewerDirection(viewer), MapCursor.Type.GREEN_POINTER);
        addDroppedBowCursor(cursors, mapView);
        addRemainingPlayerCursors(cursors, mapView, viewer);
        return cursors;
    }

    private void copyCursors(MapCursorCollection target, MapCursorCollection source) {
        if (target == null || source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            MapCursor cursor = source.getCursor(i);
            if (cursor != null) {
                target.addCursor(cursor);
            }
        }
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

    private MapCursorCollection resetCursors(MapCanvas canvas) {
        MapCursorCollection cursors = canvas.getCursors();
        while (cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0));
        }
        return cursors;
    }

    private void paintTerrain(PlayerMinimap minimap, MapCanvas canvas, Player viewer) {
        if (minimap == null || canvas == null || viewer == null) {
            return;
        }
        TerrainSnapshot snapshot = terrainSnapshotFor(minimap, viewer);
        if (snapshot == null) {
            return;
        }
        paintSampledTerrain(minimap, canvas, snapshot);
    }

    private void paintActiveMapTerrain(MapCanvas canvas) {
        if (canvas == null) {
            return;
        }
        MapAnchor anchor = activeMapAnchor();
        if (anchor == null || anchor.world == null) {
            return;
        }
        paintSnapshot(canvas, terrainSnapshot(anchor));
    }

    private TerrainSnapshot terrainSnapshotFor(PlayerMinimap minimap, Player player) {
        if (minimap == null || minimap.mapView == null || player == null) {
            return null;
        }
        MapAnchor anchor = resolveMapAnchor(minimap, player);
        TerrainSnapshot snapshot = terrainSnapshot(anchor);
        if (snapshot == null) {
            MapAnchor fallbackAnchor = fallbackAnchor(minimap, player);
            if (!sameAnchor(anchor, fallbackAnchor)) {
                TerrainSnapshot fallbackSnapshot = terrainSnapshot(fallbackAnchor);
                if (fallbackSnapshot != null) {
                    anchor = fallbackAnchor;
                    snapshot = fallbackSnapshot;
                }
            }
        }
        if (snapshot == null || anchor == null) {
            applyMapView(minimap.mapView, anchor);
            syncMinimapAnchor(minimap, anchor);
            return null;
        }
        applyMapView(minimap.mapView, anchor);
        syncMinimapAnchor(minimap, anchor);
        return snapshot;
    }

    private MapAnchor fallbackAnchor(PlayerMinimap minimap, Player player) {
        if (minimap == null || player == null || player.getWorld() == null) {
            return null;
        }
        if (minimap.fallbackAnchor == null || !sameWorld(minimap.fallbackAnchor.world, player.getWorld())) {
            minimap.fallbackAnchor = playerAnchor(player);
        }
        return minimap.fallbackAnchor;
    }

    private boolean sameAnchor(MapAnchor first, MapAnchor second) {
        if (first == second) {
            return true;
        }
        return first != null
                && second != null
                && sameWorld(first.world, second.world)
                && first.centerX == second.centerX
                && first.centerZ == second.centerZ
                && first.scale == second.scale;
    }

    private void paintSampledTerrain(PlayerMinimap minimap, MapCanvas canvas, TerrainSnapshot snapshot) {
        if (snapshot == null || snapshot.pixels == null) {
            return;
        }
        if (minimap.renderedSampledTerrainVersion == snapshot.version
                && snapshot.key.equals(minimap.renderedSampledTerrainKey)) {
            return;
        }
        for (int index = 0; index < MAP_PIXEL_SIZE * MAP_PIXEL_SIZE; index++) {
            setSnapshotPixel(canvas, snapshot.pixels, index);
        }
        minimap.renderedSampledTerrainVersion = snapshot.version;
        minimap.renderedSampledTerrainKey = snapshot.key;
    }

    private void paintSnapshot(MapCanvas canvas, TerrainSnapshot snapshot) {
        if (canvas == null || snapshot == null || snapshot.pixels == null) {
            return;
        }
        for (int index = 0; index < MAP_PIXEL_SIZE * MAP_PIXEL_SIZE; index++) {
            setSnapshotPixel(canvas, snapshot.pixels, index);
        }
    }

    private void setSnapshotPixel(MapCanvas canvas, byte[] pixels, int index) {
        canvas.setPixel(index % MAP_PIXEL_SIZE, index / MAP_PIXEL_SIZE, pixels[index]);
    }

    private TerrainSnapshot terrainSnapshot(MapAnchor anchor) {
        TerrainCacheKey key = TerrainCacheKey.from(anchor, mapSessionId);
        if (key == null) {
            return null;
        }
        TerrainSnapshot snapshot = terrainSnapshots.get(key);
        if (snapshot != null) {
            return snapshot;
        }
        long now = System.currentTimeMillis();
        TerrainSnapshot refreshed = createTerrainSnapshot(key, anchor, now);
        if (refreshed != null) {
            terrainSnapshots.put(key, refreshed);
        }
        return refreshed;
    }

    private TerrainSnapshot createTerrainSnapshot(TerrainCacheKey key, MapAnchor anchor, long now) {
        byte[] pixels = new byte[MAP_PIXEL_SIZE * MAP_PIXEL_SIZE];
        int[] heights = new int[MAP_PIXEL_SIZE * MAP_PIXEL_SIZE];
        boolean[] shadeable = new boolean[MAP_PIXEL_SIZE * MAP_PIXEL_SIZE];
        double blocksPerPixel = blocksPerPixel(anchor.scale);
        loadCaptureChunks(anchor, blocksPerPixel);
        int nonAirSamples = 0;
        for (int z = 0; z < MAP_PIXEL_SIZE; z++) {
            for (int x = 0; x < MAP_PIXEL_SIZE; x++) {
                int index = z * MAP_PIXEL_SIZE + x;
                int worldX = terrainBlockCoordinate(anchor.centerX, x, blocksPerPixel);
                int worldZ = terrainBlockCoordinate(anchor.centerZ, z, blocksPerPixel);
                TerrainSample sample = terrainSample(anchor.world, worldX, worldZ);
                if (!sample.empty) {
                    nonAirSamples++;
                }
                pixels[index] = shadePalette(sample.paletteBase, 1);
                heights[index] = sample.height;
                shadeable[index] = sample.shadeable;
            }
        }
        if (nonAirSamples == 0) {
            logEmptyTerrainCapture(anchor);
            return null;
        }
        for (int z = 0; z < MAP_PIXEL_SIZE; z++) {
            for (int x = 0; x < MAP_PIXEL_SIZE; x++) {
                int index = z * MAP_PIXEL_SIZE + x;
                if (!shadeable[index]) {
                    continue;
                }
                int northHeight = z == 0 ? heights[index] : heights[index - MAP_PIXEL_SIZE];
                pixels[index] = shadePalette(paletteBase(pixels[index]), shadeForHeight(heights[index], northHeight));
            }
        }
        return new TerrainSnapshot(key, pixels, now);
    }

    private void loadCaptureChunks(MapAnchor anchor, double blocksPerPixel) {
        if (anchor == null || anchor.world == null) {
            return;
        }
        int firstX = terrainBlockCoordinate(anchor.centerX, 0, blocksPerPixel);
        int lastX = terrainBlockCoordinate(anchor.centerX, MAP_PIXEL_SIZE - 1, blocksPerPixel);
        int firstZ = terrainBlockCoordinate(anchor.centerZ, 0, blocksPerPixel);
        int lastZ = terrainBlockCoordinate(anchor.centerZ, MAP_PIXEL_SIZE - 1, blocksPerPixel);
        int minChunkX = Math.min(firstX, lastX) >> 4;
        int maxChunkX = Math.max(firstX, lastX) >> 4;
        int minChunkZ = Math.min(firstZ, lastZ) >> 4;
        int maxChunkZ = Math.max(firstZ, lastZ) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                loadChunk(anchor.world, chunkX, chunkZ);
            }
        }
    }

    private void loadChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.getChunkAt(chunkX, chunkZ).load();
            }
        } catch (Throwable ignored) {
        }
    }

    private void logEmptyTerrainCapture(MapAnchor anchor) {
        if (loggedEmptyTerrainCapture || plugin == null || anchor == null || anchor.world == null) {
            return;
        }
        loggedEmptyTerrainCapture = true;
        plugin.getLogger().warning("Murder Mystery minimap terrain capture found no blocks at world "
                + anchor.world.getName() + " around x=" + anchor.centerX + ", z=" + anchor.centerZ
                + ". The map will retry instead of caching a blank canvas.");
    }

    private int terrainBlockCoordinate(int center, int pixel, double blocksPerPixel) {
        return (int) Math.floor(center + (pixel - MAP_PIXEL_SIZE / 2.0D) * blocksPerPixel);
    }

    private TerrainSample terrainSample(World world, int x, int z) {
        if (world == null) {
            return new TerrainSample(PALETTE_LIGHT_BROWN, 0, false, true);
        }
        try {
            Block block = world.getHighestBlockAt(x, z);
            Material material = block == null ? Material.AIR : block.getType();
            return new TerrainSample(
                    terrainPaletteBase(material, readBlockData(block)),
                    block == null ? 0 : block.getY(),
                    isShadeableTerrain(material),
                    material == null || material == Material.AIR
            );
        } catch (Throwable ignored) {
            return new TerrainSample(PALETTE_LIGHT_BROWN, 0, false, true);
        }
    }

    private int shadeForHeight(int height, int northHeight) {
        int delta = height - northHeight;
        if (delta > 1) {
            return 2;
        }
        if (delta < -1) {
            return 0;
        }
        return 1;
    }

    private int paletteBase(byte color) {
        int index = color < 0 ? color + 256 : color;
        if (index < 4) {
            return PALETTE_LIGHT_BROWN;
        }
        return index - (index % 4);
    }

    private byte shadePalette(int paletteBase, int shade) {
        int safeShade = Math.max(0, Math.min(3, shade));
        return (byte) (paletteBase + safeShade);
    }

    private int terrainPaletteBase(Material material, int data) {
        if (material == null) {
            return PALETTE_LIGHT_BROWN;
        }
        switch (material) {
            case AIR:
            case FIRE:
            case PORTAL:
            case ENDER_PORTAL:
                return PALETTE_LIGHT_BROWN;
            case GRASS:
            case LEAVES:
            case LEAVES_2:
            case VINE:
            case LONG_GRASS:
            case DOUBLE_PLANT:
            case SAPLING:
            case CACTUS:
            case MELON_STEM:
            case PUMPKIN_STEM:
            case CROPS:
            case CARROT:
            case POTATO:
                return PALETTE_LIGHT_GREEN;
            case WATER:
            case STATIONARY_WATER:
                return PALETTE_BLUE;
            case LAVA:
            case STATIONARY_LAVA:
                return PALETTE_RED;
            case SAND:
            case SANDSTONE:
            case SANDSTONE_STAIRS:
            case RED_SANDSTONE:
            case RED_SANDSTONE_STAIRS:
            case DOUBLE_STONE_SLAB2:
            case STONE_SLAB2:
            case SOUL_SAND:
                return PALETTE_LIGHT_BROWN;
            case WOOD:
            case LOG:
            case LOG_2:
            case WOOD_STAIRS:
            case WOOD_STEP:
            case WOOD_DOUBLE_STEP:
            case SPRUCE_WOOD_STAIRS:
            case BIRCH_WOOD_STAIRS:
            case JUNGLE_WOOD_STAIRS:
            case ACACIA_STAIRS:
            case DARK_OAK_STAIRS:
            case FENCE:
            case SPRUCE_FENCE:
            case BIRCH_FENCE:
            case JUNGLE_FENCE:
            case DARK_OAK_FENCE:
            case ACACIA_FENCE:
            case FENCE_GATE:
            case SPRUCE_FENCE_GATE:
            case BIRCH_FENCE_GATE:
            case JUNGLE_FENCE_GATE:
            case DARK_OAK_FENCE_GATE:
            case ACACIA_FENCE_GATE:
            case TRAP_DOOR:
            case WORKBENCH:
            case CHEST:
            case TRAPPED_CHEST:
            case JUKEBOX:
            case NOTE_BLOCK:
            case BOOKSHELF:
                return PALETTE_BROWN;
            case DIRT:
            case SOIL:
            case BROWN_MUSHROOM:
            case HUGE_MUSHROOM_1:
            case HUGE_MUSHROOM_2:
            case COCOA:
                return PALETTE_DARK_BROWN;
            case STONE:
            case COBBLESTONE:
            case SMOOTH_BRICK:
            case STEP:
            case DOUBLE_STEP:
            case COBBLESTONE_STAIRS:
            case SMOOTH_STAIRS:
            case GRAVEL:
            case CLAY:
            case IRON_ORE:
            case COAL_ORE:
            case DIAMOND_ORE:
            case EMERALD_ORE:
            case DISPENSER:
            case DROPPER:
            case FURNACE:
            case BURNING_FURNACE:
            case MOB_SPAWNER:
            case MONSTER_EGGS:
            case PISTON_BASE:
            case PISTON_STICKY_BASE:
            case PISTON_EXTENSION:
            case PISTON_MOVING_PIECE:
            case COBBLE_WALL:
            case ANVIL:
                return PALETTE_GRAY;
            case IRON_BLOCK:
            case IRON_FENCE:
            case QUARTZ_BLOCK:
            case QUARTZ_STAIRS:
            case SEA_LANTERN:
                return PALETTE_WHITE;
            case GLASS:
            case THIN_GLASS:
            case ICE:
            case PACKED_ICE:
                return PALETTE_PALE_BLUE;
            case SNOW:
            case SNOW_BLOCK:
                return PALETTE_WHITE;
            case OBSIDIAN:
            case BEDROCK:
            case COAL_BLOCK:
            case HOPPER:
            case CAULDRON:
            case ENCHANTMENT_TABLE:
            case ENDER_CHEST:
                return PALETTE_DARK_GRAY;
            case WOOL:
            case STAINED_CLAY:
            case STAINED_GLASS:
            case STAINED_GLASS_PANE:
            case CARPET:
                return dyedPaletteBase(data);
            case HARD_CLAY:
            case BRICK:
            case BRICK_STAIRS:
            case RED_MUSHROOM:
            case NETHERRACK:
            case NETHER_BRICK:
            case NETHER_BRICK_STAIRS:
            case NETHER_FENCE:
            case NETHER_WARTS:
                return PALETTE_DARK_RED;
            case TNT:
            case REDSTONE_BLOCK:
            case REDSTONE_ORE:
            case GLOWING_REDSTONE_ORE:
                return PALETTE_RED;
            case GOLD_BLOCK:
            case GOLD_ORE:
            case GOLD_PLATE:
            case HAY_BLOCK:
                return PALETTE_YELLOW;
            case LAPIS_BLOCK:
            case LAPIS_ORE:
                return PALETTE_BLUE;
            case DIAMOND_BLOCK:
            case EMERALD_BLOCK:
            case PRISMARINE:
                return PALETTE_CYAN;
            case PUMPKIN:
            case JACK_O_LANTERN:
                return PALETTE_ORANGE;
            case MYCEL:
            case WATER_LILY:
                return PALETTE_DARK_GREEN;
            case WEB:
            case BARRIER:
                return PALETTE_LIGHT_GRAY;
            default:
                return isSolidMaterial(material) ? PALETTE_LIGHT_GRAY : PALETTE_LIGHT_BROWN;
        }
    }

    private int dyedPaletteBase(int data) {
        switch (data & 15) {
            case 0:
                return PALETTE_WHITE;
            case 1:
                return PALETTE_ORANGE;
            case 2:
                return 64;
            case 3:
                return 68;
            case 4:
                return PALETTE_YELLOW;
            case 5:
                return 76;
            case 6:
                return 80;
            case 7:
                return PALETTE_DARK_GRAY;
            case 8:
                return PALETTE_LIGHT_GRAY;
            case 9:
                return PALETTE_CYAN;
            case 10:
                return 92;
            case 11:
                return PALETTE_BLUE;
            case 12:
                return PALETTE_BROWN;
            case 13:
                return PALETTE_DARK_GREEN;
            case 14:
                return PALETTE_RED;
            case 15:
                return PALETTE_BLACK;
            default:
                return PALETTE_WHITE;
        }
    }

    private int readBlockData(Block block) {
        if (block == null) {
            return 0;
        }
        try {
            Object data = block.getClass().getMethod("getData").invoke(block);
            if (data instanceof Number) {
                return ((Number) data).intValue();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private boolean isShadeableTerrain(Material material) {
        return material != null
                && material != Material.AIR
                && material != Material.WATER
                && material != Material.STATIONARY_WATER
                && material != Material.LAVA
                && material != Material.STATIONARY_LAVA
                && material != Material.GLASS
                && material != Material.THIN_GLASS
                && material != Material.STAINED_GLASS
                && material != Material.STAINED_GLASS_PANE;
    }

    private boolean isSolidMaterial(Material material) {
        if (material == null) {
            return false;
        }
        try {
            return material.isSolid();
        } catch (Throwable ignored) {
            return material != Material.AIR;
        }
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

    private void sendMap(Player player, PlayerMinimap minimap) {
        if (player == null || minimap == null || minimap.mapView == null) {
            return;
        }
        TerrainSnapshot snapshot = terrainSnapshotFor(minimap, player);
        writeStaticMapState(minimap, snapshot);
        Integer mapId = readMapId(minimap.mapView);
        MapCursorCollection cursors = createCursors(minimap.mapView, player);
        if (mapPacketSender != null
                && snapshot != null
                && mapId != null
                && mapPacketSender.send(
                        player,
                        mapId,
                        (byte) scaleValue(minimap.mapView.getScale()),
                        snapshot.pixels,
                        cursors
                )) {
            return;
        }
        logMapPacketFallback();
        sendBukkitMap(player, minimap.mapView);
    }

    private void primeStaticMapState(PlayerMinimap minimap, Player player) {
        if (minimap == null || player == null) {
            return;
        }
        writeStaticMapState(minimap, terrainSnapshotFor(minimap, player));
    }

    private void writeStaticMapState(PlayerMinimap minimap, TerrainSnapshot snapshot) {
        if (minimap == null || minimap.mapView == null || snapshot == null || snapshot.pixels == null
                || mapStateWriter == null) {
            return;
        }
        if (snapshot.key.equals(minimap.writtenStaticTerrainKey)
                && minimap.writtenStaticTerrainVersion == snapshot.version) {
            mapStateWriter.clearDecorations(minimap.mapView);
            return;
        }
        if (mapStateWriter.write(minimap.mapView, snapshot.pixels)) {
            minimap.writtenStaticTerrainKey = snapshot.key;
            minimap.writtenStaticTerrainVersion = snapshot.version;
        }
    }

    private void logMapPacketFallback() {
        if (loggedMapPacketFallback || plugin == null) {
            return;
        }
        loggedMapPacketFallback = true;
        plugin.getLogger().warning("Murder Mystery minimap is falling back to Bukkit map sends; "
                + "live cursor-only map updates may be limited on this server version.");
    }

    private void sendBukkitMap(Player player, MapView mapView) {
        if (player == null || mapView == null) {
            return;
        }
        try {
            player.sendMap(mapView);
        } catch (Throwable ignored) {
            // Some server implementations throttle or reject explicit map sends.
        }
    }

    private void scheduleMapSend(UUID playerUuid, MapView mapView, long sessionId, long delayTicks) {
        if (plugin == null || !plugin.isEnabled() || playerUuid == null || mapView == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player == null || !player.isOnline() || !canCarryMap(player)) {
                    return;
                }
                PlayerMinimap minimap = playerMinimaps.get(playerUuid);
                if (minimap == null || minimap.mapView != mapView) {
                    return;
                }
                if (minimap.sessionId != sessionId || mapSessionId != sessionId) {
                    return;
                }
                if (!isAssignedMinimapItem(player.getInventory().getItem(HOTBAR_SLOT), minimap)) {
                    applyMinimapItem(player, minimap);
                }
                configureMapView(minimap, player);
                sendMap(player, minimap);
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private int scaleValue(MapView.Scale scale) {
        if (scale == MapView.Scale.CLOSE) {
            return 1;
        }
        if (scale == MapView.Scale.NORMAL) {
            return 2;
        }
        if (scale == MapView.Scale.FAR) {
            return 3;
        }
        if (scale == MapView.Scale.FARTHEST) {
            return 4;
        }
        return 0;
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
        private final long sessionId;
        private TerrainCacheKey anchorKey;
        private MapAnchor fallbackAnchor;
        private TerrainCacheKey renderedSampledTerrainKey;
        private long renderedSampledTerrainVersion = -1L;
        private TerrainCacheKey writtenStaticTerrainKey;
        private long writtenStaticTerrainVersion = -1L;

        private PlayerMinimap(MapView mapView, MapRenderer renderer, long sessionId) {
            this.mapView = mapView;
            this.renderer = renderer;
            this.sessionId = sessionId;
        }
    }

    private static final class MapAnchor {
        private final World world;
        private final int centerX;
        private final int centerZ;
        private final MapView.Scale scale;

        private MapAnchor(World world, int centerX, int centerZ, MapView.Scale scale) {
            this.world = world;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.scale = scale == null ? MapView.Scale.CLOSEST : scale;
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

    private static final class TerrainCacheKey {
        private final String worldName;
        private final int centerX;
        private final int centerZ;
        private final MapView.Scale scale;
        private final long sessionId;

        private TerrainCacheKey(String worldName, int centerX, int centerZ, MapView.Scale scale, long sessionId) {
            this.worldName = worldName == null ? "" : worldName;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.scale = scale == null ? MapView.Scale.CLOSEST : scale;
            this.sessionId = sessionId;
        }

        private static TerrainCacheKey from(MapAnchor anchor, long sessionId) {
            if (anchor == null || anchor.world == null) {
                return null;
            }
            return new TerrainCacheKey(anchor.world.getName(), anchor.centerX, anchor.centerZ, anchor.scale, sessionId);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TerrainCacheKey)) {
                return false;
            }
            TerrainCacheKey that = (TerrainCacheKey) other;
            return centerX == that.centerX
                    && centerZ == that.centerZ
                    && sessionId == that.sessionId
                    && worldName.equals(that.worldName)
                    && scale == that.scale;
        }

        @Override
        public int hashCode() {
            int result = worldName.hashCode();
            result = 31 * result + centerX;
            result = 31 * result + centerZ;
            result = 31 * result + scale.hashCode();
            result = 31 * result + (int) (sessionId ^ (sessionId >>> 32));
            return result;
        }
    }

    private static final class TerrainSnapshot {
        private final TerrainCacheKey key;
        private final byte[] pixels;
        private final long version;

        private TerrainSnapshot(TerrainCacheKey key, byte[] pixels, long version) {
            this.key = key;
            this.pixels = pixels;
            this.version = version;
        }
    }

    private static final class TerrainSample {
        private final int paletteBase;
        private final int height;
        private final boolean shadeable;
        private final boolean empty;

        private TerrainSample(int paletteBase, int height, boolean shadeable, boolean empty) {
            this.paletteBase = paletteBase;
            this.height = height;
            this.shadeable = shadeable;
            this.empty = empty;
        }
    }
}
