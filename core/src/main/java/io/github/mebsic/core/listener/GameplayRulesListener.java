package io.github.mebsic.core.listener;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.server.ServerType;
import io.github.mebsic.game.model.GameState;
import org.bukkit.Achievement;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

public class GameplayRulesListener implements Listener {
    private final CorePlugin plugin;
    private final ServerType serverType;
    private final boolean hungerLossEnabled;
    private final boolean healthLossEnabled;
    private final boolean blockBreakEnabled;
    private final boolean containerInteractionBlocked;
    private final boolean mechanismInteractionBlocked;
    private final boolean farmlandTrampleBlocked;
    private final boolean paintingBreakBlocked;
    private final boolean beaconInteractionBlocked;
    private final boolean leverBreakBlocked;
    private final boolean blockSpreadBlocked;
    private final boolean weatherCycleEnabled;
    private final boolean vanillaAchievementsEnabled;

    public GameplayRulesListener(CorePlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.serverType = plugin.getServerType() == null ? ServerType.UNKNOWN : plugin.getServerType();
        String serverName = normalize(config.getString("server.id", ""));

        this.hungerLossEnabled = resolveToggle(config, "gameplay.hungerLoss", this.serverType, serverName, false);
        this.healthLossEnabled = resolveToggle(config, "gameplay.healthLoss", this.serverType, serverName, false);
        this.blockBreakEnabled = resolveToggle(config, "gameplay.blockBreak", this.serverType, serverName, true);
        this.containerInteractionBlocked = this.serverType == ServerType.MURDER_MYSTERY_HUB || this.serverType == ServerType.MURDER_MYSTERY;
        this.mechanismInteractionBlocked = this.serverType == ServerType.MURDER_MYSTERY_HUB;
        this.farmlandTrampleBlocked = this.serverType == ServerType.MURDER_MYSTERY_HUB || this.serverType == ServerType.MURDER_MYSTERY;
        this.paintingBreakBlocked = this.serverType != null && this.serverType.isHub();
        this.beaconInteractionBlocked = this.serverType != null && this.serverType.isHub();
        this.leverBreakBlocked = this.serverType == ServerType.MURDER_MYSTERY;
        this.blockSpreadBlocked = this.serverType == ServerType.MURDER_MYSTERY_HUB || this.serverType == ServerType.MURDER_MYSTERY;
        boolean weatherEnabled = resolveToggle(config, "gameplay.weatherCycle", this.serverType, serverName, true);
        if (this.serverType == ServerType.MURDER_MYSTERY_HUB || this.serverType == ServerType.MURDER_MYSTERY) {
            weatherEnabled = false;
        }
        this.weatherCycleEnabled = weatherEnabled;
        boolean resolvedVanillaAchievements =
                resolveToggle(config, "gameplay.vanillaAchievements", this.serverType, serverName, false);
        if (shouldDisableVanillaAchievements(this.serverType)) {
            resolvedVanillaAchievements = false;
        }
        this.vanillaAchievementsEnabled = resolvedVanillaAchievements;

        plugin.getLogger().info(
                "Gameplay rules resolved for " + this.serverType.name()
                        + " (" + (serverName.isEmpty() ? "unknown-server" : serverName) + "): "
                        + "hungerLoss=" + hungerLossEnabled
                        + ", healthLoss=" + healthLossEnabled
                        + ", blockBreak=" + blockBreakEnabled
                        + ", containerInteractionBlocked=" + containerInteractionBlocked
                        + ", mechanismInteractionBlocked=" + mechanismInteractionBlocked
                        + ", farmlandTrampleBlocked=" + farmlandTrampleBlocked
                        + ", paintingBreakBlocked=" + paintingBreakBlocked
                        + ", beaconInteractionBlocked=" + beaconInteractionBlocked
                        + ", leverBreakBlocked=" + leverBreakBlocked
                        + ", blockSpreadBlocked=" + blockSpreadBlocked
                        + ", weatherCycle=" + weatherCycleEnabled
                        + ", vanillaAchievements=" + vanillaAchievementsEnabled
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyRules(event.getPlayer());
        applyVanillaAchievementRules(event.getPlayer());
        grantDisabledServerAchievements(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAchievementAwarded(PlayerAchievementAwardedEvent event) {
        if (event == null) {
            return;
        }
        if (!shouldDisableVanillaAchievements() && vanillaAchievementsEnabled) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (hungerLossEnabled || !(event.getEntity() instanceof Player)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getEntity();
        if (player.getFoodLevel() < 20) {
            player.setFoodLevel(20);
        }
        if (player.getSaturation() < 20.0f) {
            player.setSaturation(20.0f);
        }
        player.setExhaustion(0.0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (healthLossEnabled || !(event.getEntity() instanceof Player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (leverBreakBlocked && isLever(event.getBlock()) && !plugin.isBuildModeActive(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (blockBreakEnabled || plugin.isBuildModeActive(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!leverBreakBlocked || event == null || event.getPlayer() == null) {
            return;
        }
        if (plugin.isBuildModeActive(event.getPlayer().getUniqueId())) {
            return;
        }
        if (isLever(event.getBlock())) {
            event.setInstaBreak(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (blockBreakEnabled || plugin.isBuildModeActive(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (!leverBreakBlocked || event == null) {
            return;
        }
        if (isLever(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!blockSpreadBlocked || event == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPaintingBreak(HangingBreakByEntityEvent event) {
        if (!paintingBreakBlocked || event == null || event.getRemover() == null) {
            return;
        }
        if (!(event.getRemover() instanceof Player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }
        Action action = event.getAction();
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && handleBuildModeSpawnEggUse(event)) {
            return;
        }
        Material type = clicked.getType();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (shouldBlockMechanismUntilInGame() && isBlockedMechanismInteractionMaterial(type)) {
                event.setCancelled(true);
                return;
            }
            if (containerInteractionBlocked && isBlockedContainerInteractionMaterial(type)) {
                event.setCancelled(true);
                return;
            }
            if (beaconInteractionBlocked && isBeacon(type)) {
                event.setCancelled(true);
                return;
            }
            if (mechanismInteractionBlocked && isBlockedMechanismInteractionMaterial(type)) {
                event.setCancelled(true);
                return;
            }
        }
        if (farmlandTrampleBlocked && action == Action.PHYSICAL && isFarmland(type)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockMechanismUntilInGame() {
        if (plugin == null || serverType == null || !serverType.isGame()) {
            return false;
        }
        return plugin.getCurrentGameState() != GameState.IN_GAME;
    }

    private boolean handleBuildModeSpawnEggUse(PlayerInteractEvent event) {
        if (event == null || plugin == null) {
            return false;
        }
        Player player = event.getPlayer();
        if (player == null || !plugin.isBuildModeActive(player.getUniqueId())) {
            return false;
        }
        EntityType entityType = resolveSpawnEggEntityType(event.getItem());
        if (entityType == null) {
            return false;
        }
        Location spawnLocation = resolveSpawnEggLocation(event);
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return false;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        try {
            spawnLocation.getWorld().spawnEntity(spawnLocation, entityType);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + "That mob cannot be spawned here!");
        }
        return true;
    }

    private EntityType resolveSpawnEggEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.MONSTER_EGG) {
            return null;
        }
        EntityType entityType = EntityType.fromId(item.getDurability());
        if (entityType == null || !entityType.isAlive() || !entityType.isSpawnable()) {
            return null;
        }
        return entityType;
    }

    private Location resolveSpawnEggLocation(PlayerInteractEvent event) {
        if (event == null || event.getClickedBlock() == null) {
            return null;
        }
        BlockFace face = event.getBlockFace();
        if (face == null) {
            face = BlockFace.UP;
        }
        Block targetBlock = event.getClickedBlock().getRelative(face);
        if (targetBlock == null) {
            return null;
        }
        Location location = targetBlock.getLocation();
        if (location == null) {
            return null;
        }
        location = location.add(0.5D, 0.0D, 0.5D);
        Player player = event.getPlayer();
        if (player != null && player.getLocation() != null) {
            location.setYaw(player.getLocation().getYaw());
            location.setPitch(0.0f);
        }
        World world = location.getWorld();
        return world == null ? null : location;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (weatherCycleEnabled || event == null) {
            return;
        }
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (weatherCycleEnabled || event == null) {
            return;
        }
        if (event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    private void applyRules(Player player) {
        if (player == null) {
            return;
        }
        if (!hungerLossEnabled) {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
        }
        if (!healthLossEnabled) {
            double maxHealth = player.getMaxHealth();
            if (player.getHealth() < maxHealth) {
                player.setHealth(maxHealth);
            }
        }
        if (!weatherCycleEnabled && player.getWorld() != null) {
            player.getWorld().setStorm(false);
            player.getWorld().setThundering(false);
            player.getWorld().setGameRuleValue("doWeatherCycle", "false");
        }
    }

    private void applyVanillaAchievementRules(Player player) {
        if (player == null || player.getWorld() == null) {
            return;
        }
        String value = Boolean.toString(!shouldDisableVanillaAchievements() && vanillaAchievementsEnabled);
        player.getWorld().setGameRuleValue("announceAchievements", value);
        player.getWorld().setGameRuleValue("announceAdvancements", value);
    }

    private void grantDisabledServerAchievements(Player player) {
        if (player == null || !shouldDisableVanillaAchievements()) {
            return;
        }
        silentlyGrantAchievements(player);
    }

    @SuppressWarnings("unchecked")
    private void silentlyGrantAchievements(Player player) {
        try {
            String version = player.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
            Object statisticManager = handle.getClass().getMethod("getStatisticManager").invoke(handle);
            Class<?> craftStatisticClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftStatistic");
            Method getNmsAchievement = craftStatisticClass.getMethod("getNMSAchievement", Achievement.class);
            Class<?> statisticManagerClass = Class.forName("net.minecraft.server." + version + ".StatisticManager");
            Field statisticsField = statisticManagerClass.getDeclaredField("a");
            statisticsField.setAccessible(true);
            Map<Object, Object> statistics = (Map<Object, Object>) statisticsField.get(statisticManager);
            Class<?> statisticWrapperClass = Class.forName("net.minecraft.server." + version + ".StatisticWrapper");
            Method setStatisticValue = statisticWrapperClass.getMethod("a", int.class);
            boolean changed = false;

            for (Achievement achievement : Achievement.values()) {
                if (player.hasAchievement(achievement)) {
                    continue;
                }
                Object nmsAchievement = getNmsAchievement.invoke(null, achievement);
                if (nmsAchievement == null) {
                    continue;
                }
                Object statisticWrapper = statistics.get(nmsAchievement);
                if (statisticWrapper == null) {
                    statisticWrapper = statisticWrapperClass.getConstructor().newInstance();
                    statistics.put(nmsAchievement, statisticWrapper);
                }
                setStatisticValue.invoke(statisticWrapper, 1);
                changed = true;
            }

            if (!changed) {
                return;
            }
            statisticManager.getClass().getMethod("updateStatistics", handle.getClass()).invoke(statisticManager, handle);
            saveStatistics(statisticManager);
        } catch (Exception ignored) {
        }
    }

    private void saveStatistics(Object statisticManager) {
        if (statisticManager == null) {
            return;
        }
        try {
            statisticManager.getClass().getMethod("b").invoke(statisticManager);
        } catch (Exception ignored) {
        }
    }

    private boolean shouldDisableVanillaAchievements() {
        return shouldDisableVanillaAchievements(serverType);
    }

    private boolean shouldDisableVanillaAchievements(ServerType type) {
        return type != null && (type.isHub() || type.isGame());
    }

    private boolean isBlockedContainerInteractionMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if ("HOPPER".equals(name)
                || "CHEST".equals(name)
                || "TRAPPED_CHEST".equals(name)
                || "ENDER_CHEST".equals(name)
                || "WORKBENCH".equals(name)
                || "CRAFTING_TABLE".equals(name)
                || "ANVIL".equals(name)
                || name.endsWith("_ANVIL")) {
            return true;
        }
        return false;
    }

    private boolean isBlockedMechanismInteractionMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if ("FENCE_GATE".equals(name) || name.endsWith("_FENCE_GATE")) {
            return true;
        }
        if ("LEVER".equals(name)) {
            return true;
        }
        if (name.endsWith("_BUTTON")) {
            return true;
        }
        if ("TRAP_DOOR".equals(name) || name.endsWith("_TRAPDOOR") || name.endsWith("TRAPDOOR")) {
            return true;
        }
        return (name.endsWith("_DOOR") || name.endsWith("_DOOR_BLOCK")) && !name.contains("TRAP");
    }

    private boolean isFarmland(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return "SOIL".equals(name) || "FARMLAND".equals(name);
    }

    private boolean isBeacon(Material material) {
        if (material == null) {
            return false;
        }
        return "BEACON".equals(material.name());
    }

    private boolean isLever(Block block) {
        return block != null && isLever(block.getType());
    }

    private boolean isLever(Material material) {
        if (material == null) {
            return false;
        }
        return "LEVER".equals(material.name());
    }

    private boolean resolveToggle(FileConfiguration config,
                                  String path,
                                  ServerType serverType,
                                  String serverName,
                                  boolean fallbackDefault) {
        boolean enabled = config.getBoolean(path + ".defaultEnabled", fallbackDefault);

        if (serverType != null) {
            enabled = override(enabled, readBoolean(config, path + ".byServerType." + serverType.name()));
            if (serverType.isHub()) {
                enabled = override(enabled, readBoolean(config, path + ".byServerType.HUB"));
            } else if (serverType.isGame()) {
                enabled = override(enabled, readBoolean(config, path + ".byServerType.GAME"));
            }
        }

        if (!serverName.isEmpty()) {
            enabled = override(enabled, readBoolean(config, path + ".byServerId." + serverName));
            enabled = override(enabled, readBoolean(config, path + ".byServerId." + serverName.toLowerCase(Locale.ROOT)));
        }

        return enabled;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private boolean override(boolean current, Boolean override) {
        return override == null ? current : override;
    }

    private Boolean readBoolean(FileConfiguration config, String path) {
        if (config == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        if (!config.isSet(path)) {
            return null;
        }
        Object raw = config.get(path);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw == null) {
            return null;
        }
        String normalized = raw.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
