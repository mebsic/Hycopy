package io.github.mebsic.core.menu;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.function.Consumer;

public final class AnvilInput {
    private static final String SEARCH_LABEL = ChatColor.GREEN + "Search";

    private AnvilInput() {
    }

    public static void open(Plugin plugin, Player player, String inputHint,
                            Consumer<String> onConfirm, Runnable onCancel) {
        ItemStack input = new ItemStack(Material.PAPER);
        ItemMeta inputMeta = input.getItemMeta();
        inputMeta.setDisplayName(" ");
        inputMeta.setLore(Collections.singletonList(ChatColor.GRAY + inputHint));
        input.setItemMeta(inputMeta);

        ItemStack output = new ItemStack(Material.PAPER);
        ItemMeta outputMeta = output.getItemMeta();
        outputMeta.setDisplayName(SEARCH_LABEL);
        output.setItemMeta(outputMeta);

        final boolean[] confirmed = {false};
        SearchInput search = new SearchInput();
        AnvilGUI gui = new AnvilGUI.Builder()
                .plugin(plugin)
                .disableGeyserCompat()
                .itemLeft(input)
                .itemOutput(output)
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }
                    String text = search.readQuery(snapshot.getText());
                    return Collections.singletonList((anvil, viewer) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (confirmed[0] || !isOpen(anvil, viewer)) {
                            return;
                        }
                        confirmed[0] = true;
                        anvil.closeInventory();
                        onConfirm.accept(text);
                    }));
                })
                .onClose(snapshot -> {
                    if (!confirmed[0]) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline() && player.getOpenInventory().getType() == InventoryType.CRAFTING) {
                                onCancel.run();
                            }
                        });
                    }
                })
                .open(player);

        new BukkitRunnable() {
            private boolean sendSlotUpdates = true;

            @Override
            public void run() {
                if (!isOpen(gui, player)) {
                    cancel();
                    return;
                }
                if (search.update(gui.getInventory()) && sendSlotUpdates) {
                    try {
                        sendOutputSlot(player);
                    } catch (ReflectiveOperationException ignored) {
                        sendSlotUpdates = false;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean isOpen(AnvilGUI anvil, Player viewer) {
        return viewer.isOnline() && anvil.getInventory().equals(viewer.getOpenInventory().getTopInventory());
    }

    private static void sendOutputSlot(Player player) throws ReflectiveOperationException {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object container = handle.getClass().getField("activeContainer").get(handle);
        int windowId = container.getClass().getField("windowId").getInt(container);
        Method getSlot = container.getClass().getMethod("getSlot", int.class);
        Object slot = getSlot.invoke(container, AnvilGUI.Slot.OUTPUT);
        Method getItem = getSlot.getReturnType().getMethod("getItem");
        Class<?> itemType = getItem.getReturnType();
        String serverPackage = itemType.getPackage().getName();
        Object packet = Class.forName(serverPackage + ".PacketPlayOutSetSlot")
                .getConstructor(int.class, int.class, itemType)
                .newInstance(windowId, AnvilGUI.Slot.OUTPUT, getItem.invoke(slot));
        Object connection = handle.getClass().getField("playerConnection").get(handle);
        connection.getClass().getMethod("sendPacket", Class.forName(serverPackage + ".Packet"))
                .invoke(connection, packet);
    }

    private static final class SearchInput {
        private String query = "";

        private String readQuery(String outputName) {
            if (SEARCH_LABEL.equals(outputName)) {
                return query;
            }
            return outputName == null ? "" : outputName.trim();
        }

        private boolean update(Inventory inventory) {
            ItemStack output = inventory.getItem(AnvilGUI.Slot.OUTPUT);
            ItemMeta meta = output == null ? null : output.getItemMeta();
            if (meta == null || SEARCH_LABEL.equals(meta.getDisplayName())) {
                return false;
            }
            query = readQuery(meta.getDisplayName());
            meta.setDisplayName(SEARCH_LABEL);
            output.setItemMeta(meta);
            inventory.setItem(AnvilGUI.Slot.OUTPUT, output);
            return true;
        }
    }
}
