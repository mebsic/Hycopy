package io.github.mebsic.murdermystery.book;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.book.BookPromptService;
import io.github.mebsic.core.book.InteractiveBookPrompt;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.util.CommonMessages;
import io.github.mebsic.murdermystery.util.MurderMysteryChanceMode;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class MurderMysteryChanceBookPrompt extends InteractiveBookPrompt {
    private static final String PROMPT_COMMAND = "/bookprompt";
    private static final int BOOK_LINE_WIDTH = 19;

    private final boolean desiredEnabled;

    public MurderMysteryChanceBookPrompt(UUID viewerUuid, boolean desiredEnabled) {
        super(viewerUuid);
        this.desiredEnabled = desiredEnabled;
    }

    @Override
    public ItemStack buildBook(CorePlugin plugin, String token) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK, 1);
        ItemMeta rawMeta = book.getItemMeta();
        if (!(rawMeta instanceof BookMeta)) {
            return null;
        }
        BookMeta meta = (BookMeta) rawMeta;
        meta.setTitle("YT Rank 10X Mode");
        meta.setAuthor("Hycopy");
        book.setItemMeta(meta);

        BaseComponent page = buildPage(plugin, token);
        if (page == null) {
            return null;
        }
        String json = ComponentSerializer.toString(page);
        return applyJsonPage(book, json);
    }

    @Override
    public void onYes(CorePlugin plugin, Player viewer) {
        if (plugin == null || viewer == null) {
            return;
        }
        Profile profile = plugin.getProfile(viewer.getUniqueId());
        if (profile == null) {
            viewer.sendMessage(ChatColor.RED + CommonMessages.PROFILE_LOADING);
            return;
        }
        if (!MurderMysteryChanceMode.canUse(profile)) {
            viewer.sendMessage(ChatColor.RED + CommonMessages.NO_PERMISSION_COMMAND);
            return;
        }
        if (!plugin.setMurderMysteryTenTimesModeEnabled(viewer.getUniqueId(), desiredEnabled)) {
            viewer.sendMessage(ChatColor.RED + "Unable to update 10X Mode right now!");
            return;
        }
        if (desiredEnabled) {
            viewer.sendMessage(ChatColor.GREEN + "Enabled 10X Mode!");
        } else {
            viewer.sendMessage(ChatColor.RED + "Disabled 10X Mode!");
        }
        openUpdatedPrompt(plugin, viewer);
    }

    @Override
    public void onNo(CorePlugin plugin, Player viewer) {
        if (viewer != null) {
            viewer.sendMessage(ChatColor.GRAY + "No changes made to 10X Mode.");
        }
    }

    private BaseComponent buildPage(CorePlugin plugin, String token) {
        TextComponent root = new TextComponent("");
        boolean active = isCurrentlyActive(plugin);
        addCenteredLegacy(root, ChatColor.BLACK.toString() + ChatColor.BOLD, "YT Rank 10X Mode");
        root.addExtra(new TextComponent("\n\n"));
        addLegacy(root, ChatColor.BLACK + "10X Mode is currently\n");
        addStatus(root, active ? "ACTIVE" : "NOT ACTIVE", active);
        root.addExtra(new TextComponent("\n\n"));
        if (active) {
            addLegacy(root, ChatColor.BLACK + "Your stats will be\ntracked\n\n");
        } else {
            root.addExtra(new TextComponent("\n\n"));
        }
        addCenteredAction(root, desiredEnabled ? "TURN ON 10X MODE" : "TURN OFF 10X MODE", token);
        return root;
    }

    private void addStatus(TextComponent root, String text, boolean active) {
        TextComponent status = new TextComponent(text);
        status.setColor(active ? net.md_5.bungee.api.ChatColor.GREEN : net.md_5.bungee.api.ChatColor.RED);
        status.setBold(true);
        status.setItalic(false);
        root.addExtra(status);
    }

    private void addCenteredAction(TextComponent root, String text, String token) {
        root.addExtra(new TextComponent(desiredEnabled ? centerPadding(text) : ""));
        TextComponent action = new TextComponent(text);
        action.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        action.setBold(true);
        action.setUnderlined(true);
        action.setItalic(false);
        action.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, buildDecisionCommand(token, true)));
        action.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[] {new TextComponent(ChatColor.YELLOW + "Click to toggle!")}
        ));
        root.addExtra(action);
    }

    private boolean isCurrentlyActive(CorePlugin plugin) {
        Profile profile = plugin == null || getViewerUuid() == null ? null : plugin.getProfile(getViewerUuid());
        return MurderMysteryChanceMode.isActive(profile);
    }

    private void openUpdatedPrompt(CorePlugin plugin, Player viewer) {
        if (plugin == null || viewer == null || !viewer.isOnline()) {
            return;
        }
        BookPromptService bookPromptService = plugin.getBookPromptService();
        if (bookPromptService == null) {
            return;
        }
        Profile profile = plugin.getProfile(viewer.getUniqueId());
        boolean active = MurderMysteryChanceMode.isActive(profile);
        MurderMysteryChanceBookPrompt prompt = new MurderMysteryChanceBookPrompt(viewer.getUniqueId(), !active);
        bookPromptService.openPrompt(viewer, prompt);
    }

    private String buildDecisionCommand(String token, boolean yes) {
        String safeToken = token == null ? "" : token.trim();
        return PROMPT_COMMAND + " " + safeToken + " " + (yes ? "yes" : "no");
    }

    private void addCenteredLegacy(TextComponent root, String format, String text) {
        addLegacy(root, format + centerPadding(text) + text);
    }

    private String centerPadding(String text) {
        int padding = Math.max(0, (BOOK_LINE_WIDTH - safeLength(text)) / 2);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < padding; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private void addLegacy(TextComponent root, String text) {
        if (root == null || text == null || text.isEmpty()) {
            return;
        }
        BaseComponent[] converted = TextComponent.fromLegacyText(text);
        if (converted == null || converted.length == 0) {
            return;
        }
        for (BaseComponent part : converted) {
            if (part != null) {
                root.addExtra(part);
            }
        }
    }

    private ItemStack applyJsonPage(ItemStack bukkitBook, String jsonPage) {
        if (bukkitBook == null || jsonPage == null || jsonPage.trim().isEmpty()) {
            return null;
        }
        try {
            String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
            String version = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
            String nmsPackage = "net.minecraft.server." + version;

            Class<?> craftItemStackClass = Class.forName(craftPackage + ".inventory.CraftItemStack");
            Class<?> nmsItemStackClass = Class.forName(nmsPackage + ".ItemStack");
            Class<?> nbtBaseClass = Class.forName(nmsPackage + ".NBTBase");
            Class<?> nbtCompoundClass = Class.forName(nmsPackage + ".NBTTagCompound");
            Class<?> nbtListClass = Class.forName(nmsPackage + ".NBTTagList");
            Class<?> nbtStringClass = Class.forName(nmsPackage + ".NBTTagString");

            Object nmsBook = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, bukkitBook);
            if (nmsBook == null) {
                return null;
            }

            Object tag = nmsItemStackClass.getMethod("getTag").invoke(nmsBook);
            if (tag == null) {
                tag = nbtCompoundClass.getDeclaredConstructor().newInstance();
            }

            Object pages = nbtListClass.getDeclaredConstructor().newInstance();
            Object nbtJsonPage = nbtStringClass.getConstructor(String.class).newInstance(jsonPage);
            nbtListClass.getMethod("add", nbtBaseClass).invoke(pages, nbtJsonPage);
            nbtCompoundClass.getMethod("set", String.class, nbtBaseClass).invoke(tag, "pages", pages);
            nmsItemStackClass.getMethod("setTag", nbtCompoundClass).invoke(nmsBook, tag);

            Object bukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass).invoke(null, nmsBook);
            if (bukkitCopy instanceof ItemStack) {
                return (ItemStack) bukkitCopy;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
