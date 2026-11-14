package io.github.mebsic.core.menu;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.function.Consumer;

public final class AnvilInput {
    private AnvilInput() {
    }

    public static void open(Plugin plugin, Player player, String inputHint,
                            Consumer<String> onConfirm, Runnable onCancel) {
        ItemStack input = new ItemStack(Material.PAPER);
        ItemMeta inputMeta = input.getItemMeta();
        inputMeta.setDisplayName(" ");
        inputMeta.setLore(Collections.singletonList(inputHint));
        input.setItemMeta(inputMeta);

        ItemStack output = new ItemStack(Material.PAPER);
        ItemMeta outputMeta = output.getItemMeta();
        outputMeta.setDisplayName("Search");
        output.setItemMeta(outputMeta);

        final boolean[] confirmed = {false};
        new AnvilGUI.Builder()
                .plugin(plugin)
                .disableGeyserCompat()
                .itemLeft(input)
                .itemOutput(output)
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.INPUT_LEFT && slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }
                    String text = snapshot.getText();
                    return Collections.singletonList((anvil, viewer) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!viewer.isOnline() || confirmed[0]
                                || !anvil.getInventory().equals(viewer.getOpenInventory().getTopInventory())) {
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
    }
}
