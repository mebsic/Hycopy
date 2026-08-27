package io.github.mebsic.core.listener;

import com.mongodb.client.MongoCollection;
import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.core.store.MapConfigStore;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mongodb.client.model.Filters.eq;

public class ImageListener implements Listener {
    private static final int IMAGE_SOURCE_CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int IMAGE_SOURCE_READ_TIMEOUT_MILLIS = 10_000;
    private static final String IMAGE_HTTP_USER_AGENT = "Mozilla/5.0 (compatible; HycopyImageService/1.0)";
    private static final String IMAGE_HTTP_ACCEPT = "image/*";
    private static final int IMAGE_GRID_WIDTH = 15;
    private static final int IMAGE_GRID_HEIGHT = 6;
    private static final int IMAGE_TILE_SIZE = 128;
    private static final int IMAGE_ANCHOR_SEARCH_BLOCKS = 8;
    private static final int IMAGE_MAP_SENDS_PER_TICK = 20;
    private static final String IMAGE_URL_KEY = "imageUrl";
    private static final String IMAGE_ENABLED_KEY = "imageEnabled";
    private static final String MAP_CONFIG_UPDATE_CHANNEL = "map_config_update";
    private static final String MAP_CONFIG_UPDATE_PREFIX = "maps:";
    public static final String IMAGE_SETTINGS_UPDATE_CHANNEL = "murdermystery_image_update";

    private final Plugin plugin;
    private final CorePlugin corePlugin;
    private final ServerType serverType;
    private final AtomicBoolean refreshInFlight;
    private final AtomicBoolean refreshQueued;
    private final AtomicBoolean imageDownloadRetryScheduled;
    private final AtomicBoolean updateSubscriptionsStarted;
    private final List<MapTile> mapTiles;
    private final Set<UUID> pendingRuntimeFrameUuids;
    private volatile String activeGameKey;
    private volatile String cachedImageUrl;
    private volatile String cachedImageSource;
    private volatile BufferedImage cachedImage;
    private volatile String imageDownloadBackoffSource;
    private volatile long imageDownloadBackoffUntilMillis;
    private ImageGrid mapTileGrid;
    private String mapTileImageKey;
    private RuntimeImage runtimeImage;

    public ImageListener(Plugin plugin, CorePlugin corePlugin, ServerType serverType) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.serverType = serverType == null ? ServerType.UNKNOWN : serverType;
        this.refreshInFlight = new AtomicBoolean(false);
        this.refreshQueued = new AtomicBoolean(false);
        this.imageDownloadRetryScheduled = new AtomicBoolean(false);
        this.updateSubscriptionsStarted = new AtomicBoolean(false);
        this.activeGameKey = MongoManager.MURDER_MYSTERY_GAME_KEY;
        this.mapTiles = new ArrayList<MapTile>();
        this.pendingRuntimeFrameUuids = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
        this.cachedImageUrl = "";
        this.cachedImageSource = "";
        this.cachedImage = null;
        this.imageDownloadBackoffSource = "";
        this.imageDownloadBackoffUntilMillis = 0L;
        this.mapTileGrid = null;
        this.mapTileImageKey = "";
    }

    public void start() {
        subscribeToUpdateNotifications();
        refreshDisplay();
    }

    public void shutdown() {
        despawnRuntimeImage();
        clearMapTiles();
    }

    public void refreshDisplay() {
        if (plugin == null || !supportsImageDisplayServerType()) {
            return;
        }
        if (!refreshInFlight.compareAndSet(false, true)) {
            refreshQueued.set(true);
            return;
        }
        refreshQueued.set(false);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ResolvedImageConfig config = null;
            BufferedImage image = null;
            try {
                config = resolveImageConfig();
                if (config != null && config.enabled && !config.imageSource.isEmpty()) {
                    String source = safeText(config.imageSource);
                    String cacheKey = imageCacheKey(source, config.imageUpdatedAt);
                    BufferedImage cached = cachedImage;
                    if (!source.isEmpty() && cacheKey.equals(cachedImageSource) && cached != null) {
                        image = cached;
                    } else if (isImageDownloadBackoffActive(source)) {
                        scheduleImageDownloadRetry(imageDownloadBackoffRemainingMillis(source));
                        if (source.equals(cachedImageUrl) && cached != null) {
                            image = cached;
                        }
                    } else {
                        try {
                            BufferedImage loaded = loadImage(source);
                            if (loaded != null) {
                                cachedImageUrl = source;
                                cachedImageSource = cacheKey;
                                cachedImage = loaded;
                                clearImageDownloadBackoff(source);
                                image = loaded;
                            }
                        } catch (ImageRateLimitException ex) {
                            setImageDownloadBackoff(source, ex.retryAfterMillis);
                            if (source.equals(cachedImageUrl) && cached != null) {
                                image = cached;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            final ResolvedImageConfig finalConfig = config;
            final BufferedImage finalImage = image;
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        applyResolvedImage(finalConfig, finalImage);
                    } finally {
                        finishRefresh();
                    }
                });
            } catch (Exception ex) {
                finishRefresh();
            }
        });
    }

    private void finishRefresh() {
        refreshInFlight.set(false);
        if (refreshQueued.getAndSet(false)) {
            scheduleRefreshDisplay();
        }
    }

    private void subscribeToUpdateNotifications() {
        if (corePlugin == null || corePlugin.getPubSubService() == null) {
            return;
        }
        if (!updateSubscriptionsStarted.compareAndSet(false, true)) {
            return;
        }
        corePlugin.getPubSubService().subscribe(MAP_CONFIG_UPDATE_CHANNEL, this::handleMapConfigUpdateMessage);
        corePlugin.getPubSubService().subscribe(IMAGE_SETTINGS_UPDATE_CHANNEL, this::handleImageSettingsUpdateMessage);
    }

    private void handleMapConfigUpdateMessage(String message) {
        String updatedKey = parseMapConfigUpdatedGameKey(message);
        if (updatedKey.isEmpty() || !shouldRefreshForGameKey(updatedKey)) {
            return;
        }
        scheduleRefreshDisplay();
    }

    private void handleImageSettingsUpdateMessage(String message) {
        String updatedKey = MapConfigStore.normalizeGameKey(message);
        if (updatedKey.isEmpty() || !shouldRefreshForGameKey(updatedKey)) {
            return;
        }
        scheduleRefreshDisplay();
    }

    private void scheduleRefreshDisplay() {
        if (plugin == null || plugin.getServer() == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, this::refreshDisplay);
    }

    private String parseMapConfigUpdatedGameKey(String message) {
        String raw = safeText(message);
        if (raw.isEmpty()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(MAP_CONFIG_UPDATE_PREFIX)) {
            return "";
        }
        return MapConfigStore.normalizeGameKey(raw.substring(MAP_CONFIG_UPDATE_PREFIX.length()));
    }

    private boolean shouldRefreshForGameKey(String gameKey) {
        String normalized = MapConfigStore.normalizeGameKey(gameKey);
        return MongoManager.MURDER_MYSTERY_GAME_KEY.equals(normalized);
    }

    private String imageCacheKey(String source, long updatedAt) {
        return safeText(source) + "#" + Math.max(0L, updatedAt);
    }

    private boolean isImageDownloadBackoffActive(String source) {
        return imageDownloadBackoffRemainingMillis(source) > 0L;
    }

    private long imageDownloadBackoffRemainingMillis(String source) {
        String normalized = safeText(source);
        if (normalized.isEmpty() || !normalized.equals(imageDownloadBackoffSource)) {
            return 0L;
        }
        return Math.max(0L, imageDownloadBackoffUntilMillis - System.currentTimeMillis());
    }

    private void setImageDownloadBackoff(String source, long retryAfterMillis) {
        String normalized = safeText(source);
        if (normalized.isEmpty()) {
            return;
        }
        long delay = retryAfterMillis <= 0L ? 60_000L : retryAfterMillis;
        imageDownloadBackoffSource = normalized;
        imageDownloadBackoffUntilMillis = System.currentTimeMillis() + Math.min(delay, 300_000L);
        scheduleImageDownloadRetry(delay);
    }

    private void clearImageDownloadBackoff(String source) {
        String normalized = safeText(source);
        if (!normalized.equals(imageDownloadBackoffSource)) {
            return;
        }
        imageDownloadBackoffSource = "";
        imageDownloadBackoffUntilMillis = 0L;
    }

    private void scheduleImageDownloadRetry(long delayMillis) {
        if (plugin == null || plugin.getServer() == null || delayMillis <= 0L) {
            return;
        }
        if (!imageDownloadRetryScheduled.compareAndSet(false, true)) {
            return;
        }
        long ticks = Math.max(20L, Math.min(6_000L, ((delayMillis + 999L) / 1000L) * 20L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            imageDownloadRetryScheduled.set(false);
            refreshDisplay();
        }, ticks);
    }

    private void applyResolvedImage(ResolvedImageConfig config, BufferedImage image) {
        if (config == null || !config.enabled) {
            despawnRuntimeImage();
            return;
        }
        this.activeGameKey = config.gameKey;
        if (config.location == null || config.imageSource.isEmpty()) {
            despawnRuntimeImage();
            return;
        }

        World world = resolveWorld(config.location.worldName);
        if (world == null) {
            despawnRuntimeImage();
            return;
        }
        BlockFace facing = resolveFacing(config.location);
        if (facing == null || facing == BlockFace.SELF || facing == BlockFace.UP || facing == BlockFace.DOWN) {
            facing = BlockFace.SOUTH;
        }
        ImageGrid grid = resolveImageGrid();
        String imageKey = imageCacheKey(config.imageSource, config.imageUpdatedAt);
        boolean imageChanged = !imageKey.equals(mapTileImageKey);
        if (image == null && imageChanged) {
            despawnRuntimeImage();
            return;
        }
        ImageLocation frameAnchor = resolveFrameAnchor(world, config.location, facing, grid);
        ensureImageAreaChunksLoaded(world, frameAnchor, facing, grid);
        ensureMapTiles(world, grid, imageChanged);
        if (grid == null || mapTiles.size() != grid.totalTiles) {
            return;
        }

        boolean imageApplied = false;
        if (image != null) {
            BufferedImage canvas = composeImageForGrid(image, grid);
            if (canvas == null) {
                return;
            }
            applyMapTileImages(canvas, grid);
            imageApplied = true;
        }
        ensureRuntimeFrames(world, frameAnchor, facing, grid, imageChanged, imageKey);
        if (imageApplied) {
            mapTileImageKey = imageKey;
            sendMapTilesToOnlinePlayers(grid);
        }
    }

    private boolean isSolidSupport(Material type) {
        if (type == null || type == Material.AIR) {
            return false;
        }
        try {
            return type.isSolid();
        } catch (Exception ignored) {
            return type != Material.AIR;
        }
    }

    private ImageGrid resolveImageGrid() {
        return new ImageGrid(IMAGE_GRID_WIDTH, IMAGE_GRID_HEIGHT);
    }

    private ImageLocation resolveFrameAnchor(World world, ImageLocation savedAnchor, BlockFace facing, ImageGrid grid) {
        if (world == null || savedAnchor == null || facing == null || grid == null) {
            return savedAnchor;
        }
        ImageLocation best = savedAnchor;
        int bestSupported = countSupportedTiles(world, best, facing, grid);

        for (int distance = 1; distance <= IMAGE_ANCHOR_SEARCH_BLOCKS && bestSupported < grid.totalTiles; distance++) {
            ImageLocation forward = offsetAnchor(savedAnchor, facing, distance);
            int forwardSupported = countSupportedTiles(world, forward, facing, grid);
            if (forwardSupported > bestSupported) {
                best = forward;
                bestSupported = forwardSupported;
            }

            ImageLocation backward = offsetAnchor(savedAnchor, facing.getOppositeFace(), distance);
            int backwardSupported = countSupportedTiles(world, backward, facing, grid);
            if (backwardSupported > bestSupported) {
                best = backward;
                bestSupported = backwardSupported;
            }
        }
        return best;
    }

    private int countSupportedTiles(World world, ImageLocation anchor, BlockFace facing, ImageGrid grid) {
        if (world == null || anchor == null || facing == null || grid == null) {
            return 0;
        }
        ensureImageAreaChunksLoaded(world, anchor, facing, grid);
        int supported = 0;
        for (int rowTop = 0; rowTop < grid.height; rowTop++) {
            for (int col = 0; col < grid.width; col++) {
                Location tile = tileLocation(world, anchor, facing, col, rowTop, grid);
                if (hasSupportForTile(tile, facing)) {
                    supported++;
                }
            }
        }
        return supported;
    }

    private ImageLocation offsetAnchor(ImageLocation anchor, BlockFace direction, int distance) {
        if (anchor == null || direction == null) {
            return anchor;
        }
        int offset = Math.max(0, distance);
        double x = anchor.x;
        double z = anchor.z;
        if (direction == BlockFace.NORTH) {
            z -= offset;
        } else if (direction == BlockFace.SOUTH) {
            z += offset;
        } else if (direction == BlockFace.EAST) {
            x += offset;
        } else if (direction == BlockFace.WEST) {
            x -= offset;
        }
        return new ImageLocation(anchor.worldName, x, anchor.y, z, anchor.yaw, anchor.pitch);
    }

    private BufferedImage composeImageForGrid(BufferedImage source, ImageGrid grid) {
        if (grid == null || grid.totalWidth <= 0 || grid.totalHeight <= 0) {
            return null;
        }
        BufferedImage canvas = new BufferedImage(grid.totalWidth, grid.totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, grid.totalWidth, grid.totalHeight);
            if (source != null && source.getWidth() > 0 && source.getHeight() > 0) {
                double scale = Math.min(
                        (double) grid.totalWidth / (double) source.getWidth(),
                        (double) grid.totalHeight / (double) source.getHeight()
                );
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                int x = (grid.totalWidth - width) / 2;
                int y = (grid.totalHeight - height) / 2;
                graphics.drawImage(source, x, y, width, height, null);
            }
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private void ensureMapTiles(World world, ImageGrid grid, boolean forceNewMaps) {
        if (world == null || grid == null) {
            return;
        }
        if (!forceNewMaps && mapTiles.size() == grid.totalTiles && grid.matches(mapTileGrid)) {
            return;
        }
        clearMapTiles();
        mapTileGrid = grid;
        for (int i = 0; i < grid.totalTiles; i++) {
            MapView view = Bukkit.createMap(world);
            StaticImageMapRenderer renderer = new StaticImageMapRenderer();
            for (MapRenderer existing : new ArrayList<MapRenderer>(view.getRenderers())) {
                view.removeRenderer(existing);
            }
            view.addRenderer(renderer);
            mapTiles.add(new MapTile(view, renderer));
        }
    }

    private void clearMapTiles() {
        mapTiles.clear();
        mapTileGrid = null;
        mapTileImageKey = "";
    }

    private void applyMapTileImages(BufferedImage canvas, ImageGrid grid) {
        if (canvas == null || grid == null || mapTiles.size() != grid.totalTiles) {
            return;
        }
        for (int rowTop = 0; rowTop < grid.height; rowTop++) {
            for (int col = 0; col < grid.width; col++) {
                int index = rowTop * grid.width + col;
                MapTile tile = mapTiles.get(index);
                if (tile == null || tile.renderer == null) {
                    continue;
                }
                BufferedImage tileImage = canvas.getSubimage(
                        col * IMAGE_TILE_SIZE,
                        rowTop * IMAGE_TILE_SIZE,
                        IMAGE_TILE_SIZE,
                        IMAGE_TILE_SIZE
                );
                tile.renderer.setImage(tileImage);
            }
        }
    }

    private void sendMapTilesToOnlinePlayers(ImageGrid grid) {
        if (grid == null || mapTiles.size() != grid.totalTiles) {
            return;
        }
        List<Player> players = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isImageMapViewer(player)) {
                players.add(player);
            }
        }
        sendMapTilesToPlayers(players, grid);
    }

    private void sendMapTilesToPlayer(Player player, ImageGrid grid) {
        if (player == null || !player.isOnline() || grid == null || mapTiles.size() != grid.totalTiles) {
            return;
        }
        List<Player> players = new ArrayList<Player>(1);
        players.add(player);
        sendMapTilesToPlayers(players, grid);
    }

    private void sendMapTilesToPlayers(List<Player> players, ImageGrid grid) {
        if (plugin == null || plugin.getServer() == null || players == null || players.isEmpty()
                || grid == null || mapTiles.size() != grid.totalTiles) {
            return;
        }
        List<MapView> views = new ArrayList<MapView>(mapTiles.size());
        for (MapTile tile : mapTiles) {
            if (tile == null || tile.mapView == null) {
                continue;
            }
            views.add(tile.mapView);
        }
        if (views.isEmpty()) {
            return;
        }

        final List<Player> targetPlayers = new ArrayList<Player>(players);
        final List<MapView> targetViews = new ArrayList<MapView>(views);
        final int totalSends = targetPlayers.size() * targetViews.size();
        final int[] cursor = new int[] {0};
        final org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (plugin == null || !plugin.isEnabled()) {
                if (task[0] != null) {
                    task[0].cancel();
                }
                return;
            }
            int sent = 0;
            while (cursor[0] < totalSends && sent < IMAGE_MAP_SENDS_PER_TICK) {
                int current = cursor[0]++;
                int playerIndex = current / targetViews.size();
                int mapIndex = current % targetViews.size();
                Player target = targetPlayers.get(playerIndex);
                MapView view = targetViews.get(mapIndex);
                if (target != null && target.isOnline() && view != null) {
                    try {
                        target.sendMap(view);
                    } catch (Exception ignored) {
                    }
                }
                sent++;
            }
            if (cursor[0] >= totalSends && task[0] != null) {
                task[0].cancel();
            }
        }, 1L, 1L);
    }

    private boolean isImageMapViewer(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        RuntimeImage runtime = runtimeImage;
        if (runtime == null || runtime.location == null) {
            return true;
        }
        World world = resolveWorld(runtime.location.worldName);
        return world == null || player.getWorld() == null || world.equals(player.getWorld());
    }

    private void ensureRuntimeFrames(World world,
                                     ImageLocation location,
                                     BlockFace facing,
                                     ImageGrid grid,
                                     boolean refreshFrameItems,
                                     String imageKey) {
        if (world == null || location == null || facing == null || grid == null || mapTiles.size() != grid.totalTiles) {
            return;
        }
        List<UUID> previous = runtimeImage == null || runtimeImage.frameUuids == null
                ? new ArrayList<UUID>()
                : new ArrayList<UUID>(runtimeImage.frameUuids);
        Set<UUID> previousSet = new HashSet<UUID>(previous);
        removeConflictingFramesNearGrid(world, location, facing, previousSet, grid);
        List<UUID> next = new ArrayList<UUID>(grid.totalTiles);
        List<FrameItemAssignment> delayedItems = refreshFrameItems
                ? new ArrayList<FrameItemAssignment>(grid.totalTiles)
                : null;

        for (int rowTop = 0; rowTop < grid.height; rowTop++) {
            for (int col = 0; col < grid.width; col++) {
                int index = rowTop * grid.width + col;
                Location tileLocation = tileLocation(world, location, facing, col, rowTop, grid);
                if (tileLocation == null) {
                    continue;
                }

                ItemFrame frame = index < previous.size() ? resolveFrame(previous.get(index)) : null;
                if (frame != null && !matchesFramePlacement(frame, tileLocation, facing)) {
                    frame.remove();
                    frame = null;
                }
                if (frame == null) {
                    frame = spawnItemFrame(world, tileLocation, facing);
                }
                if (frame == null) {
                    continue;
                }
                if (frame.getUniqueId() != null) {
                    // Protect newly spawned frames immediately so physics cannot break
                    // unsupported/floating placements before runtimeImage is updated.
                    pendingRuntimeFrameUuids.add(frame.getUniqueId());
                }
                if (!applyFrameFacing(frame, facing)) {
                    frame.remove();
                    continue;
                }

                MapTile tile = mapTiles.get(index);
                if (tile != null && tile.mapView != null) {
                    ItemStack mapItem = mapItem(tile.mapView);
                    if (refreshFrameItems && delayedItems != null) {
                        frame.setItem(new ItemStack(Material.AIR));
                        delayedItems.add(new FrameItemAssignment(frame.getUniqueId(), mapItem));
                    } else {
                        frame.setItem(mapItem);
                    }
                    frame.setRotation(Rotation.NONE);
                }
                if (frame.getUniqueId() != null) {
                    next.add(frame.getUniqueId());
                }
            }
        }
        Set<UUID> nextSet = new HashSet<UUID>(next);
        for (UUID oldUuid : previous) {
            if (oldUuid == null || nextSet.contains(oldUuid)) {
                continue;
            }
            ItemFrame oldFrame = resolveFrame(oldUuid);
            if (oldFrame != null) {
                oldFrame.remove();
            }
        }

        RuntimeImage runtime = runtimeImage == null ? new RuntimeImage() : runtimeImage;
        runtime.location = location;
        runtime.facing = facing;
        runtime.imageSource = "";
        runtime.frameUuids = next;
        runtimeImage = runtime;
        pendingRuntimeFrameUuids.clear();
        scheduleFrameItemRefresh(delayedItems, imageKey);
    }

    private void scheduleFrameItemRefresh(List<FrameItemAssignment> assignments, String imageKey) {
        if (plugin == null || plugin.getServer() == null || assignments == null || assignments.isEmpty()) {
            return;
        }
        final List<FrameItemAssignment> pendingItems = new ArrayList<FrameItemAssignment>(assignments);
        final String expectedImageKey = safeText(imageKey);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!expectedImageKey.equals(mapTileImageKey)) {
                return;
            }
            for (FrameItemAssignment assignment : pendingItems) {
                if (assignment == null || assignment.frameUuid == null || assignment.item == null) {
                    continue;
                }
                ItemFrame frame = resolveFrame(assignment.frameUuid);
                if (frame == null || !isRuntimeImageFrameEntity(frame)) {
                    continue;
                }
                frame.setItem(assignment.item);
                frame.setRotation(Rotation.NONE);
            }
        }, 1L);
    }

    private boolean hasSupportForTile(Location tileLocation, BlockFace facing) {
        if (tileLocation == null || facing == null) {
            return false;
        }
        Block support = tileLocation.getBlock().getRelative(facing.getOppositeFace());
        return isSolidSupport(support == null ? null : support.getType());
    }

    private ItemFrame spawnItemFrame(World world, Location tileLocation, BlockFace facing) {
        ItemFrame frame = spawnItemFrameWithNativeFacing(world, tileLocation, facing);
        if (frame != null) {
            return frame;
        }
        try {
            return world.spawn(spawnLocationForFacing(tileLocation, facing), ItemFrame.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ItemStack mapItem(MapView mapView) {
        Material material = filledMapMaterial();
        ItemStack item = new ItemStack(material, 1);
        if (mapView != null && !applyMapViewMeta(item, mapView)) {
            item.setDurability((short) mapView.getId());
        }
        return item;
    }

    private Material filledMapMaterial() {
        try {
            return Material.valueOf("FILLED_MAP");
        } catch (Exception ignored) {
            return Material.MAP;
        }
    }

    private boolean applyMapViewMeta(ItemStack item, MapView mapView) {
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
                item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) meta);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
            meta.getClass().getMethod("setMapId", int.class).invoke(meta, mapView.getId());
            item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) meta);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ItemFrame spawnItemFrameWithNativeFacing(World world, Location tileLocation, BlockFace facing) {
        if (world == null || tileLocation == null || facing == null) {
            return null;
        }
        String version = craftBukkitVersion();
        if (version.isEmpty()) {
            return null;
        }
        try {
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");
            if (!craftWorldClass.isInstance(world)) {
                return null;
            }
            Object worldHandle = craftWorldClass.getMethod("getHandle").invoke(world);
            Class<?> nmsWorldClass = Class.forName("net.minecraft.server." + version + ".World");
            Class<?> nmsEntityClass = Class.forName("net.minecraft.server." + version + ".Entity");
            Class<?> blockPositionClass = Class.forName("net.minecraft.server." + version + ".BlockPosition");
            Class<?> enumDirectionClass = Class.forName("net.minecraft.server." + version + ".EnumDirection");
            Class<?> entityItemFrameClass = Class.forName("net.minecraft.server." + version + ".EntityItemFrame");
            Object blockPosition = blockPositionClass
                    .getConstructor(int.class, int.class, int.class)
                    .newInstance(tileLocation.getBlockX(), tileLocation.getBlockY(), tileLocation.getBlockZ());
            Object direction = Enum.valueOf((Class) enumDirectionClass, nmsDirectionName(facing));
            Object nmsFrame = entityItemFrameClass
                    .getConstructor(nmsWorldClass, blockPositionClass, enumDirectionClass)
                    .newInstance(worldHandle, blockPosition, direction);
            Object added = worldHandle.getClass()
                    .getMethod("addEntity", nmsEntityClass)
                    .invoke(worldHandle, nmsFrame);
            if (added instanceof Boolean && !((Boolean) added)) {
                return null;
            }
            Object bukkitEntity = entityItemFrameClass.getMethod("getBukkitEntity").invoke(nmsFrame);
            return bukkitEntity instanceof ItemFrame ? (ItemFrame) bukkitEntity : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String craftBukkitVersion() {
        if (Bukkit.getServer() == null || Bukkit.getServer().getClass() == null) {
            return "";
        }
        Package serverPackage = Bukkit.getServer().getClass().getPackage();
        String packageName = serverPackage == null ? "" : safeText(serverPackage.getName());
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= packageName.length() - 1) {
            return "";
        }
        String version = packageName.substring(lastDot + 1);
        return version.startsWith("v") ? version : "";
    }

    private String nmsDirectionName(BlockFace facing) {
        if (facing == BlockFace.NORTH) {
            return "NORTH";
        }
        if (facing == BlockFace.EAST) {
            return "EAST";
        }
        if (facing == BlockFace.WEST) {
            return "WEST";
        }
        return "SOUTH";
    }

    private void ensureImageAreaChunksLoaded(World world,
                                             ImageLocation location,
                                             BlockFace facing,
                                             ImageGrid grid) {
        if (world == null || location == null || facing == null || grid == null) {
            return;
        }
        BlockFace supportFace = facing.getOppositeFace();
        for (int yOffset = 0; yOffset < grid.height; yOffset++) {
            for (int col = 0; col < grid.width; col++) {
                Location tile = tileLocationFromBottom(world, location, facing, col, yOffset, grid.width);
                ensureLocationChunkLoaded(tile);
                if (tile == null) {
                    continue;
                }
                Block support = tile.getBlock().getRelative(supportFace);
                ensureBlockChunkLoaded(support);
            }
        }
    }

    private void ensureLocationChunkLoaded(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        ensureChunkLoaded(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private void ensureBlockChunkLoaded(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        ensureChunkLoaded(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private void ensureChunkLoaded(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, false);
            }
        } catch (Exception ignored) {
            // Keep the renderer best-effort if the world rejects a chunk load.
        }
    }

    private void removeConflictingFramesNearGrid(World world,
                                                 ImageLocation location,
                                                 BlockFace facing,
                                                 Set<UUID> preserve,
                                                 ImageGrid grid) {
        if (world == null || location == null || facing == null || grid == null) {
            return;
        }
        Set<UUID> scanned = new HashSet<UUID>();
        for (int rowTop = 0; rowTop < grid.height; rowTop++) {
            for (int col = 0; col < grid.width; col++) {
                Location tile = tileLocation(world, location, facing, col, rowTop, grid);
                if (tile == null) {
                    continue;
                }
                for (Entity nearby : world.getNearbyEntities(tile, 0.75d, 0.75d, 0.75d)) {
                    if (!(nearby instanceof ItemFrame) || nearby.getUniqueId() == null) {
                        continue;
                    }
                    UUID id = nearby.getUniqueId();
                    if (!scanned.add(id)) {
                        continue;
                    }
                    if (preserve != null && preserve.contains(id)) {
                        continue;
                    }
                    ItemFrame frame = (ItemFrame) nearby;
                    ItemStack item = frame.getItem();
                    if (item == null || item.getType() != Material.MAP) {
                        continue;
                    }
                    try {
                        frame.remove();
                    } catch (Exception ignored) {
                        // Best effort cleanup.
                    }
                }
            }
        }
    }

    private Location tileLocation(World world, ImageLocation anchor, BlockFace facing, int col, int rowTop, ImageGrid grid) {
        if (grid == null) {
            return null;
        }
        int yOffset = (Math.max(1, grid.height) - 1) - Math.max(0, rowTop);
        return tileLocationFromBottom(world, anchor, facing, col, yOffset, grid.width);
    }

    private Location tileLocationFromBottom(World world,
                                            ImageLocation anchor,
                                            BlockFace facing,
                                            int col,
                                            int yOffset,
                                            int gridWidth) {
        if (world == null || anchor == null || facing == null) {
            return null;
        }
        int[] step = widthStepForFacing(facing);
        int xStep = step[0];
        int zStep = step[1];
        int horizontalOffset = Math.max(0, col) - (Math.max(1, gridWidth) - 1);

        double baseX = Math.floor(anchor.x) + 0.5d;
        double baseY = Math.floor(anchor.y) + 0.5d;
        double baseZ = Math.floor(anchor.z) + 0.5d;
        return new Location(
                world,
                baseX + (xStep * horizontalOffset),
                baseY + Math.max(0, yOffset),
                baseZ + (zStep * horizontalOffset),
                anchor.yaw,
                anchor.pitch
        );
    }

    private int[] widthStepForFacing(BlockFace facing) {
        if (facing == BlockFace.NORTH) {
            return new int[] {-1, 0};
        }
        if (facing == BlockFace.SOUTH) {
            return new int[] {1, 0};
        }
        if (facing == BlockFace.EAST) {
            return new int[] {0, -1};
        }
        if (facing == BlockFace.WEST) {
            return new int[] {0, 1};
        }
        return new int[] {-1, 0};
    }

    private Location spawnLocationForFacing(Location tileLocation, BlockFace facing) {
        Location spawnLocation = tileLocation == null ? null : tileLocation.clone();
        if (spawnLocation == null) {
            return null;
        }
        spawnLocation.setYaw(yawForFacing(facing));
        spawnLocation.setPitch(0.0f);
        return spawnLocation;
    }

    private float yawForFacing(BlockFace facing) {
        if (facing == BlockFace.NORTH) {
            return 180.0f;
        }
        if (facing == BlockFace.WEST) {
            return 90.0f;
        }
        if (facing == BlockFace.EAST) {
            return 270.0f;
        }
        return 0.0f;
    }

    private boolean applyFrameFacing(ItemFrame frame, BlockFace facing) {
        if (frame == null || facing == null) {
            return false;
        }
        try {
            return frame.setFacingDirection(facing, true);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesFramePlacement(ItemFrame frame, Location target, BlockFace facing) {
        if (frame == null || target == null || target.getWorld() == null || facing == null) {
            return false;
        }
        Location current = frame.getLocation();
        if (current == null || current.getWorld() == null) {
            return false;
        }
        if (!target.getWorld().equals(current.getWorld())) {
            return false;
        }
        if (current.distanceSquared(target) > 0.01d) {
            return false;
        }
        return facing == frame.getFacing();
    }

    private void despawnRuntimeImage() {
        RuntimeImage runtime = runtimeImage;
        runtimeImage = null;
        pendingRuntimeFrameUuids.clear();
        clearMapTiles();
        if (runtime == null || runtime.frameUuids == null || runtime.frameUuids.isEmpty()) {
            return;
        }
        for (UUID uuid : runtime.frameUuids) {
            ItemFrame frame = resolveFrame(uuid);
            if (frame != null) {
                frame.remove();
            }
        }
    }

    private ItemFrame resolveFrame(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world == null) {
                continue;
            }
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof ItemFrame) || entity.getUniqueId() == null) {
                    continue;
                }
                if (uuid.equals(entity.getUniqueId())) {
                    return (ItemFrame) entity;
                }
            }
        }
        return null;
    }

    private BufferedImage loadImage(String rawSource) throws Exception {
        String source = safeText(rawSource);
        if (source.isEmpty()) {
            return null;
        }
        boolean extensionSupported = hasSupportedImageExtension(source);
        if (source.toLowerCase(Locale.ROOT).startsWith("http://")
                || source.toLowerCase(Locale.ROOT).startsWith("https://")) {
            URLConnection connection = new URL(source).openConnection();
            connection.setConnectTimeout(IMAGE_SOURCE_CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(IMAGE_SOURCE_READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", IMAGE_HTTP_USER_AGENT);
            connection.setRequestProperty("Accept", IMAGE_HTTP_ACCEPT);
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) connection;
                http.setInstanceFollowRedirects(true);
                int status = http.getResponseCode();
                if (status >= 400) {
                    if (status == 429) {
                        throw new ImageRateLimitException(retryAfterMillis(http));
                    }
                    if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                        throw new IllegalArgumentException("HTTP 403 (forbidden). The image host blocked access; use a direct public image URL.");
                    }
                    throw new IllegalArgumentException("HTTP " + status + " while downloading image URL.");
                }
            }
            String contentType = safeText(connection.getContentType()).toLowerCase(Locale.ROOT);
            if (!extensionSupported && !isSupportedImageContentType(contentType)) {
                throw new IllegalArgumentException("Only PNG/JPG image sources are supported.");
            }
            try (InputStream stream = connection.getInputStream()) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) {
                    return null;
                }
                if (!extensionSupported && !isSupportedImageContentType(contentType)) {
                    return null;
                }
                return image;
            }
        }
        if (!extensionSupported) {
            throw new IllegalArgumentException("Only PNG/JPG image files are supported.");
        }
        Path path = Paths.get(source);
        if (!path.isAbsolute() && plugin != null && plugin.getDataFolder() != null) {
            path = plugin.getDataFolder().toPath().resolve(source).normalize();
        }
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            return ImageIO.read(stream);
        }
    }

    private boolean hasSupportedImageExtension(String source) {
        String clean = safeText(source).toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) {
            return false;
        }
        int query = clean.indexOf('?');
        if (query >= 0) {
            clean = clean.substring(0, query);
        }
        int fragment = clean.indexOf('#');
        if (fragment >= 0) {
            clean = clean.substring(0, fragment);
        }
        return clean.endsWith(".png")
                || clean.endsWith(".jpg")
                || clean.endsWith(".jpeg");
    }

    private boolean isSupportedImageContentType(String contentType) {
        String normalized = safeText(contentType).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.startsWith("image/png")
                || normalized.startsWith("image/jpeg")
                || normalized.startsWith("image/jpg");
    }

    private long retryAfterMillis(HttpURLConnection connection) {
        if (connection == null) {
            return 0L;
        }
        String retryAfter = safeText(connection.getHeaderField("Retry-After"));
        if (retryAfter.isEmpty()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(retryAfter)) * 1000L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private BlockFace resolveFacing(ImageLocation location) {
        return location == null ? BlockFace.SOUTH : cardinalFacingFromYaw(location.yaw);
    }

    private BlockFace cardinalFacingFromYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        if (normalized >= 45.0f && normalized < 135.0f) {
            return BlockFace.WEST;
        }
        if (normalized >= 135.0f && normalized < 225.0f) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225.0f && normalized < 315.0f) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private boolean supportsImageDisplayServerType() {
        return serverType == ServerType.MURDER_MYSTERY_HUB;
    }

    public boolean isRuntimeImageFrameEntity(Entity entity) {
        if (!(entity instanceof ItemFrame) || entity.getUniqueId() == null) {
            return false;
        }
        RuntimeImage runtime = runtimeImage;
        UUID entityId = entity.getUniqueId();
        if (entityId != null && pendingRuntimeFrameUuids.contains(entityId)) {
            return true;
        }
        if (runtime == null || runtime.frameUuids == null || runtime.frameUuids.isEmpty()) {
            return false;
        }
        for (UUID frameUuid : runtime.frameUuids) {
            if (frameUuid != null && frameUuid.equals(entityId)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!supportsImageDisplayServerType() || event == null || event.getPlayer() == null) {
            return;
        }
        if (plugin == null || plugin.getServer() == null) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendMapTilesToPlayer(player, mapTileGrid), 20L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (event == null || event.getWorld() == null) {
            return;
        }
        refreshDisplay();
    }

    private ResolvedImageConfig resolveImageConfig() {
        RuntimeImageSettings runtimeSettings = loadRuntimeImageSettings();
        if (runtimeSettings == null) {
            runtimeSettings = RuntimeImageSettings.DEFAULT;
        }
        Document root = loadRoot(MongoManager.MURDER_MYSTERY_GAME_KEY);
        if (root == null) {
            return null;
        }
        Document gameSection = resolveGameSection(root, MongoManager.MURDER_MYSTERY_GAME_KEY);
        if (gameSection == null) {
            return null;
        }
        Document serverTypeSection = resolveServerTypeSection(gameSection, ServerType.MURDER_MYSTERY_HUB);
        if (serverTypeSection == null) {
            return null;
        }
        Document information = asDocument(serverTypeSection.get(MongoManager.MAP_INFORMATION_KEY));
        if (information == null) {
            return null;
        }
        ImageInformation parsed = parseInformation(information);
        if (parsed == null) {
            return null;
        }
        String resolvedSource = runtimeSettings.imageSource;
        boolean resolvedEnabled = parsed.enabled && runtimeSettings.enabled;
        return new ResolvedImageConfig(
                MongoManager.MURDER_MYSTERY_GAME_KEY,
                parsed.location,
                resolvedSource,
                resolvedEnabled,
                runtimeSettings.updatedAt
        );
    }

    private ImageInformation parseInformation(Document information) {
        if (information == null) {
            return null;
        }
        Document imageDisplaySection = asDocument(information.get(MongoManager.MAP_INFORMATION_IMAGE_DISPLAY_KEY));
        if (imageDisplaySection == null) {
            return null;
        }
        ImageLocation location = parseImageLocation(imageDisplaySection);
        Boolean enabledValue = readBoolean(imageDisplaySection.get("enabled"));
        boolean enabled = enabledValue == null || enabledValue;
        if (!enabled && location == null) {
            return new ImageInformation(null, false);
        }
        if (location == null) {
            return null;
        }
        return new ImageInformation(location, enabled);
    }

    private RuntimeImageSettings loadRuntimeImageSettings() {
        if (corePlugin == null || corePlugin.getMongoManager() == null) {
            return RuntimeImageSettings.DEFAULT;
        }
        MongoCollection<Document> informationCollection = corePlugin.getMongoManager()
                .getCollection(MongoManager.MURDER_MYSTERY_INFORMATION_COLLECTION);
        if (informationCollection == null) {
            return RuntimeImageSettings.DEFAULT;
        }
        Document information = informationCollection
                .find(eq("_id", MongoManager.MURDER_MYSTERY_INFORMATION_DOCUMENT_ID))
                .first();
        if (information == null) {
            return RuntimeImageSettings.DEFAULT;
        }
        String imageUrl = safeText(information.get(IMAGE_URL_KEY));
        Boolean enabled = readBoolean(information.get(IMAGE_ENABLED_KEY));
        Long updatedAt = readLong(information.get("updatedAt"));
        return new RuntimeImageSettings(imageUrl, enabled == null || enabled, updatedAt == null ? 0L : updatedAt);
    }

    private ImageLocation parseImageLocation(Object rawLocation) {
        Document locationDoc = asDocument(rawLocation);
        if (locationDoc == null) {
            return null;
        }
        String world = safeText(locationDoc.get("world"));
        Double x = readDouble(locationDoc.get("x"));
        Double y = readDouble(locationDoc.get("y"));
        Double z = readDouble(locationDoc.get("z"));
        if (world.isEmpty() || x == null || y == null || z == null) {
            return null;
        }
        float yaw = readFloat(locationDoc.get("yaw"), 0.0f);
        float pitch = readFloat(locationDoc.get("pitch"), 0.0f);
        return new ImageLocation(world, x, y, z, yaw, pitch);
    }

    private Document resolveServerTypeSection(Document gameSection, ServerType type) {
        if (gameSection == null || type == null || type == ServerType.UNKNOWN) {
            return null;
        }
        Document serverTypes = asDocument(gameSection.get(MongoManager.MAP_SERVER_TYPES_KEY));
        if (serverTypes == null) {
            return null;
        }
        return asDocument(serverTypes.get(type.name()));
    }

    private Long readLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        String text = safeText(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Document loadRoot(String gameKey) {
        if (corePlugin == null || corePlugin.getMongoManager() == null) {
            return null;
        }
        MongoCollection<Document> maps = corePlugin.getMongoManager().getCollection(MongoManager.MAPS_COLLECTION);
        if (maps == null || gameKey == null || gameKey.trim().isEmpty()) {
            return null;
        }
        return maps.find(eq("_id", gameKey)).first();
    }

    private Document resolveGameSection(Document root, String gameKey) {
        if (root == null || gameKey == null || gameKey.trim().isEmpty()) {
            return null;
        }
        Document gameTypes = asDocument(root.get("gameTypes"));
        return asDocument(gameTypes == null ? null : gameTypes.get(gameKey));
    }

    private World resolveWorld(String worldName) {
        String target = safeText(worldName);
        if (!target.isEmpty()) {
            World world = Bukkit.getWorld(target);
            if (world != null) {
                return world;
            }
            for (World current : Bukkit.getWorlds()) {
                if (current != null && target.equalsIgnoreCase(current.getName())) {
                    return current;
                }
            }
        }
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    private Double readDouble(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        String text = safeText(raw);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private float readFloat(Object raw, float fallback) {
        Double value = readDouble(raw);
        return value == null ? fallback : value.floatValue();
    }

    private Boolean readBoolean(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        String text = safeText(raw).toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text) || "no".equals(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private String safeText(Object raw) {
        if (raw == null) {
            return "";
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private Document asDocument(Object raw) {
        if (raw instanceof Document) {
            return (Document) raw;
        }
        if (raw instanceof Map<?, ?>) {
            return new Document((Map<String, Object>) raw);
        }
        return null;
    }

    private static final class MapTile {
        private final MapView mapView;
        private final StaticImageMapRenderer renderer;

        private MapTile(MapView mapView, StaticImageMapRenderer renderer) {
            this.mapView = mapView;
            this.renderer = renderer;
        }
    }

    private static final class FrameItemAssignment {
        private final UUID frameUuid;
        private final ItemStack item;

        private FrameItemAssignment(UUID frameUuid, ItemStack item) {
            this.frameUuid = frameUuid;
            this.item = item;
        }
    }

    private static final class ImageGrid {
        private final int width;
        private final int height;
        private final int totalWidth;
        private final int totalHeight;
        private final int totalTiles;

        private ImageGrid(int width, int height) {
            this.width = Math.max(1, Math.min(IMAGE_GRID_WIDTH, width));
            this.height = Math.max(1, Math.min(IMAGE_GRID_HEIGHT, height));
            this.totalWidth = this.width * IMAGE_TILE_SIZE;
            this.totalHeight = this.height * IMAGE_TILE_SIZE;
            this.totalTiles = this.width * this.height;
        }

        private boolean matches(ImageGrid other) {
            return other != null && width == other.width && height == other.height;
        }
    }

    private static final class ImageInformation {
        private final ImageLocation location;
        private final boolean enabled;

        private ImageInformation(ImageLocation location, boolean enabled) {
            this.location = location;
            this.enabled = enabled;
        }
    }

    private static final class ResolvedImageConfig {
        private final String gameKey;
        private final ImageLocation location;
        private final String imageSource;
        private final boolean enabled;
        private final long imageUpdatedAt;

        private ResolvedImageConfig(String gameKey,
                                    ImageLocation location,
                                    String imageSource,
                                    boolean enabled,
                                    long imageUpdatedAt) {
            this.gameKey = gameKey == null ? MongoManager.MURDER_MYSTERY_GAME_KEY : gameKey;
            this.location = location;
            this.imageSource = imageSource == null ? "" : imageSource;
            this.enabled = enabled;
            this.imageUpdatedAt = imageUpdatedAt;
        }
    }

    private static final class RuntimeImageSettings {
        private static final RuntimeImageSettings DEFAULT = new RuntimeImageSettings("", true, 0L);
        private final String imageSource;
        private final boolean enabled;
        private final long updatedAt;

        private RuntimeImageSettings(String imageSource, boolean enabled, long updatedAt) {
            this.imageSource = imageSource == null ? "" : imageSource;
            this.enabled = enabled;
            this.updatedAt = updatedAt;
        }
    }

    private static final class ImageRateLimitException extends Exception {
        private final long retryAfterMillis;

        private ImageRateLimitException(long retryAfterMillis) {
            this.retryAfterMillis = retryAfterMillis;
        }
    }

    private static final class ImageLocation {
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private ImageLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
            this.worldName = worldName == null ? "" : worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class RuntimeImage {
        private List<UUID> frameUuids;
        private ImageLocation location;
        private BlockFace facing;
        private String imageSource;
    }

    private static final class StaticImageMapRenderer extends MapRenderer {
        private BufferedImage image;
        private long imageVersion;
        private long renderedVersion;

        private StaticImageMapRenderer() {
            this.imageVersion = 0L;
            this.renderedVersion = -1L;
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            this.imageVersion++;
            this.renderedVersion = -1L;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (canvas == null || image == null) {
                return;
            }
            if (renderedVersion == imageVersion) {
                return;
            }
            canvas.drawImage(0, 0, image);
            renderedVersion = imageVersion;
        }
    }
}
