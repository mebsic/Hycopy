package io.github.mebsic.game.menu;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.menu.Menu;
import io.github.mebsic.core.menu.MenuClick;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.game.manager.GameManager;
import io.github.mebsic.game.model.GamePlayer;
import io.github.mebsic.game.model.GameState;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SpectatorSettingsMenu extends Menu {
    private static final int SIZE = 36;

    private static final int SPEED_NONE_SLOT = 11;
    private static final int SPEED_ONE_SLOT = 12;
    private static final int SPEED_TWO_SLOT = 13;
    private static final int SPEED_THREE_SLOT = 14;
    private static final int SPEED_FOUR_SLOT = 15;

    private static final int AUTO_TELEPORT_SLOT = 20;
    private static final int NIGHT_VISION_SLOT = 21;
    private static final int FIRST_PERSON_SLOT = 23;
    private static final int HIDE_SPECTATORS_SLOT = 24;

    private static final String TITLE = "Spectator Settings";
    private static final String SPEED_REMOVED_MESSAGE =
            ChatColor.RED + "You no longer have any speed effects!";
    private static final String SPEED_SET_MESSAGE_PREFIX =
            ChatColor.GREEN + "You now have Speed ";
    private static final String AUTO_TELEPORT_ENABLED_MESSAGE =
            ChatColor.GREEN + "Once you select a player using your compass, it will auto teleport you to them!";
    private static final String AUTO_TELEPORT_DISABLED_MESSAGE =
            ChatColor.RED + "You will no longer auto-teleport to targets!";
    private static final String NIGHT_VISION_ENABLED_MESSAGE =
            ChatColor.GREEN + "You now have night vision!";
    private static final String NIGHT_VISION_DISABLED_MESSAGE =
            ChatColor.RED + "You no longer have night vision!";
    private static final String FIRST_PERSON_ENABLED_MESSAGE =
            ChatColor.GREEN + "You will now by default use First Person spectating!";
    private static final String FIRST_PERSON_DISABLED_MESSAGE =
            ChatColor.RED + "You will now by default use Third Person spectating!";
    private static final String HIDE_SPECTATORS_ENABLED_MESSAGE =
            ChatColor.RED + "You can no longer see other spectators!";
    private static final String HIDE_SPECTATORS_DISABLED_MESSAGE =
            ChatColor.GREEN + "You can now see other spectators!";

    private final CorePlugin plugin;
    private final GameManager gameManager;

    public SpectatorSettingsMenu(CorePlugin plugin, GameManager gameManager) {
        super(TITLE, SIZE);
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    protected void populate(Player player, Inventory inventory) {
        if (player == null || inventory == null || plugin == null) {
            return;
        }
        inventory.clear();
        Profile profile = plugin.getProfile(player.getUniqueId());
        if (profile == null) {
            return;
        }
        set(inventory, SPEED_NONE_SLOT, speedItem(resolveSpeedBootMaterial(0), "No Speed"));
        set(inventory, SPEED_ONE_SLOT, speedItem(resolveSpeedBootMaterial(1), "Speed I"));
        set(inventory, SPEED_TWO_SLOT, speedItem(resolveSpeedBootMaterial(2), "Speed II"));
        set(inventory, SPEED_THREE_SLOT, speedItem(resolveSpeedBootMaterial(3), "Speed III"));
        set(inventory, SPEED_FOUR_SLOT, speedItem(resolveSpeedBootMaterial(4), "Speed IV"));

        set(inventory, AUTO_TELEPORT_SLOT, autoTeleportItem(profile.isSpectatorAutoTeleportEnabled()));
        set(inventory, NIGHT_VISION_SLOT, nightVisionItem(profile.isSpectatorNightVisionEnabled()));
        set(inventory, FIRST_PERSON_SLOT, firstPersonItem(profile.isSpectatorFirstPersonEnabled()));
        set(inventory, HIDE_SPECTATORS_SLOT, hideSpectatorsItem(profile.isSpectatorHideOtherSpectatorsEnabled()));
    }

    @Override
    public void onClick(MenuClick click) {
        if (click == null) {
            return;
        }
        Player player = click.getPlayer();
        if (player == null || plugin == null) {
            return;
        }
        Profile profile = plugin.getProfile(player.getUniqueId());
        if (profile == null) {
            return;
        }
        boolean changed = false;
        boolean needsSpectatorStateRefresh = false;
        int slot = click.getRawSlot();
        if (slot == SPEED_NONE_SLOT) {
            if (profile.getSpectatorSpeedLevel() != 0) {
                profile.setSpectatorSpeedLevel(0);
                changed = true;
                needsSpectatorStateRefresh = true;
                player.sendMessage(SPEED_REMOVED_MESSAGE);
            }
        } else if (slot == SPEED_ONE_SLOT) {
            if (profile.getSpectatorSpeedLevel() != 1) {
                profile.setSpectatorSpeedLevel(1);
                changed = true;
                needsSpectatorStateRefresh = true;
                player.sendMessage(SPEED_SET_MESSAGE_PREFIX + "I!");
            }
        } else if (slot == SPEED_TWO_SLOT) {
            if (profile.getSpectatorSpeedLevel() != 2) {
                profile.setSpectatorSpeedLevel(2);
                changed = true;
                needsSpectatorStateRefresh = true;
                player.sendMessage(SPEED_SET_MESSAGE_PREFIX + "II!");
            }
        } else if (slot == SPEED_THREE_SLOT) {
            if (profile.getSpectatorSpeedLevel() != 3) {
                profile.setSpectatorSpeedLevel(3);
                changed = true;
                needsSpectatorStateRefresh = true;
                player.sendMessage(SPEED_SET_MESSAGE_PREFIX + "III!");
            }
        } else if (slot == SPEED_FOUR_SLOT) {
            if (profile.getSpectatorSpeedLevel() != 4) {
                profile.setSpectatorSpeedLevel(4);
                changed = true;
                needsSpectatorStateRefresh = true;
                player.sendMessage(SPEED_SET_MESSAGE_PREFIX + "IV!");
            }
        } else if (slot == AUTO_TELEPORT_SLOT) {
            profile.setSpectatorAutoTeleportEnabled(!profile.isSpectatorAutoTeleportEnabled());
            changed = true;
            if (profile.isSpectatorAutoTeleportEnabled()) {
                player.sendMessage(AUTO_TELEPORT_ENABLED_MESSAGE);
            } else {
                player.sendMessage(AUTO_TELEPORT_DISABLED_MESSAGE);
            }
        } else if (slot == NIGHT_VISION_SLOT) {
            profile.setSpectatorNightVisionEnabled(!profile.isSpectatorNightVisionEnabled());
            changed = true;
            needsSpectatorStateRefresh = true;
            if (profile.isSpectatorNightVisionEnabled()) {
                player.sendMessage(NIGHT_VISION_ENABLED_MESSAGE);
            } else {
                player.sendMessage(NIGHT_VISION_DISABLED_MESSAGE);
            }
        } else if (slot == FIRST_PERSON_SLOT) {
            profile.setSpectatorFirstPersonEnabled(!profile.isSpectatorFirstPersonEnabled());
            changed = true;
            if (profile.isSpectatorFirstPersonEnabled()) {
                player.sendMessage(FIRST_PERSON_ENABLED_MESSAGE);
            } else {
                player.sendMessage(FIRST_PERSON_DISABLED_MESSAGE);
            }
        } else if (slot == HIDE_SPECTATORS_SLOT) {
            profile.setSpectatorHideOtherSpectatorsEnabled(!profile.isSpectatorHideOtherSpectatorsEnabled());
            changed = true;
            needsSpectatorStateRefresh = true;
            if (profile.isSpectatorHideOtherSpectatorsEnabled()) {
                player.sendMessage(HIDE_SPECTATORS_ENABLED_MESSAGE);
            } else {
                player.sendMessage(HIDE_SPECTATORS_DISABLED_MESSAGE);
            }
        }
        if (!changed) {
            return;
        }
        plugin.saveProfile(profile);
        if (needsSpectatorStateRefresh) {
            refreshDeadSpectatorState(player);
        }
        open(player);
    }

    private void refreshDeadSpectatorState(Player player) {
        if (player == null || gameManager == null) {
            return;
        }
        if (gameManager.getState() != GameState.IN_GAME) {
            return;
        }
        GamePlayer gamePlayer = gameManager.getPlayer(player);
        if (gamePlayer == null || gamePlayer.isAlive()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        gameManager.restoreDeadSpectatorState(player);
    }

    private ItemStack speedItem(Material material, String label) {
        return item(material, ChatColor.GREEN + label);
    }

    private ItemStack autoTeleportItem(boolean enabled) {
        if (enabled) {
            return item(resolveCompassMaterial(),
                    ChatColor.RED + "Disable Auto Teleport",
                    actionLore("disable", " auto teleport!"));
        }
        return item(resolveCompassMaterial(),
                ChatColor.GREEN + "Enable Auto Teleport",
                actionLore("enable", " auto teleport!"));
    }

    private ItemStack nightVisionItem(boolean enabled) {
        Material material = enabled ? resolveNightVisionEnabledMaterial() : resolveNightVisionDisabledMaterial();
        if (enabled) {
            return item(material,
                    ChatColor.RED + "Disable Night Vision",
                    actionLore("disable", " night vision!"));
        }
        return item(material,
                ChatColor.GREEN + "Enable Night Vision",
                actionLore("enable", " night vision!"));
    }

    private ItemStack firstPersonItem(boolean enabled) {
        Material watch = Material.matchMaterial("WATCH");
        if (watch == null) {
            watch = Material.matchMaterial("CLOCK");
        }
        if (watch == null) {
            watch = Material.PAPER;
        }
        if (enabled) {
            return item(watch,
                    ChatColor.RED + "Disable First Person",
                    firstPersonLore("disable"));
        }
        return item(watch,
                ChatColor.GREEN + "Enable First Person",
                firstPersonLore("enable"));
    }

    private ItemStack hideSpectatorsItem(boolean hideEnabled) {
        Material material = hideEnabled ? resolveGlowstoneMaterial() : resolveRedstoneDustMaterial();
        if (hideEnabled) {
            return item(material,
                    ChatColor.GREEN + "Show Spectators",
                    actionLore("show", " other spectators!"));
        }
        return item(material,
                ChatColor.RED + "Hide Spectators",
                actionLore("hide", " other spectators!"));
    }

    private String actionLore(String action, String suffix) {
        return ChatColor.GRAY + "Click to " + action + suffix;
    }

    private List<String> firstPersonLore(String action) {
        List<String> lore = new ArrayList<>();
        lore.add(actionLore(action, " first person"));
        lore.add(ChatColor.GRAY + "spectating when using the compass!");
        lore.add(ChatColor.GRAY + "You can also right-click a player");
        lore.add(ChatColor.GRAY + "to spectate them in first person.");
        return lore;
    }

    private Material resolveNightVisionEnabledMaterial() {
        Material pearl = Material.matchMaterial("ENDER_PEARL");
        return pearl == null ? resolveNightVisionDisabledMaterial() : pearl;
    }

    private Material resolveNightVisionDisabledMaterial() {
        Material eye = Material.matchMaterial("EYE_OF_ENDER");
        if (eye != null) {
            return eye;
        }
        eye = Material.matchMaterial("ENDER_EYE");
        return eye == null ? Material.PAPER : eye;
    }

    private Material resolveRedstoneDustMaterial() {
        Material redstone = Material.matchMaterial("REDSTONE");
        return redstone == null ? Material.PAPER : redstone;
    }

    private Material resolveGlowstoneMaterial() {
        Material glowstone = Material.matchMaterial("GLOWSTONE_DUST");
        if (glowstone != null) {
            return glowstone;
        }
        glowstone = Material.matchMaterial("GLOWSTONE");
        return glowstone == null ? Material.PAPER : glowstone;
    }

    private Material resolveSpeedBootMaterial(int level) {
        switch (level) {
            case 0:
                return Material.LEATHER_BOOTS;
            case 1:
                Material chain = Material.matchMaterial("CHAINMAIL_BOOTS");
                return chain == null ? Material.IRON_BOOTS : chain;
            case 2:
                return Material.IRON_BOOTS;
            case 3:
                return Material.GOLD_BOOTS;
            default:
                return Material.DIAMOND_BOOTS;
        }
    }

    private Material resolveCompassMaterial() {
        Material compass = Material.matchMaterial("COMPASS");
        return compass == null ? Material.WATCH : compass;
    }

}
