package io.github.mebsic.core.book;

import io.github.mebsic.core.CorePlugin;
import io.github.mebsic.core.menu.GiftSupport;
import io.github.mebsic.core.model.Profile;
import io.github.mebsic.core.model.Rank;
import io.github.mebsic.core.service.GiftDecisionHandler;
import io.github.mebsic.core.service.GiftRequestService;
import io.github.mebsic.core.util.NetworkConstants;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.UUID;

public class GiftDecisionBookPrompt extends InteractiveBookPrompt {
    private static final String PROMPT_COMMAND = "/bookprompt";
    private static final int INDENT_SPACES = 10;

    private final UUID gifterUuid;
    private final String gifterName;
    private final Rank giftedRank;
    private final Integer mvpPlusPlusDays;

    public GiftDecisionBookPrompt(UUID viewerUuid,
                                  UUID gifterUuid,
                                  String gifterName,
                                  Rank giftedRank,
                                  Integer mvpPlusPlusDays) {
        super(viewerUuid);
        this.gifterUuid = gifterUuid;
        this.gifterName = gifterName == null ? "" : gifterName.trim();
        this.giftedRank = GiftSupport.safeRank(giftedRank);
        this.mvpPlusPlusDays = mvpPlusPlusDays == null ? null : Math.max(0, mvpPlusPlusDays);
    }

    @Override
    public ItemStack buildBook(CorePlugin plugin, String token) {
        ItemStack book = new ItemStack(resolveWrittenBookMaterial(), 1);
        ItemMeta rawMeta = book.getItemMeta();
        if (!(rawMeta instanceof BookMeta)) {
            return null;
        }
        BookMeta meta = (BookMeta) rawMeta;
        meta.setTitle("Gift Request");
        meta.setAuthor(resolveAuthor());
        book.setItemMeta(meta);

        BaseComponent[] page = buildPage(plugin, token);
        if (page != null && page.length > 0) {
            String json = ComponentSerializer.toString(page[0]);
            ItemStack jsonBook = applyJsonPage(book, json);
            if (jsonBook != null) {
                return jsonBook;
            }
        }
        return book;
    }

    @Override
    public void onYes(CorePlugin plugin, Player viewer) {
        GiftDecisionHandler.accept(plugin, viewer);
    }

    @Override
    public void onNo(CorePlugin plugin, Player viewer) {
        GiftDecisionHandler.decline(plugin, viewer);
    }

    @Override
    public void onCancel(CorePlugin plugin, UUID viewerUuid) {
        if (plugin == null || viewerUuid == null) {
            return;
        }
        GiftRequestService service = plugin.getGiftRequestService();
        if (service == null) {
            return;
        }
        GiftRequestService.GiftRequest request = service.remove(viewerUuid);
        if (request == null) {
            return;
        }

        String rankDisplay = GiftSupport.buildGiftRankText(request.getGiftedRank(), null);
        String senderDurationDisplay = formatSenderExpiryDuration(request.getGiftedRank(), request.getMvpPlusPlusDays());
        String receiverDurationDisplay = formatReceiverExpiryDuration(request.getGiftedRank(), request.getMvpPlusPlusDays());

        Player receiver = Bukkit.getPlayer(viewerUuid);
        if (receiver != null && receiver.isOnline()) {
            Profile senderProfile = plugin.getProfile(request.getGifterUuid());
            String senderDisplay = GiftSupport.buildTargetNameWithRankColor(senderProfile, request.getGifterName());
            receiver.sendMessage(ChatColor.RED + "The gift from "
                    + senderDisplay
                    + ChatColor.RED + " for "
                    + rankDisplay
                    + receiverDurationDisplay
                    + ChatColor.RED + " has expired!");
        }

        Player gifter = request.getGifterUuid() == null ? null : Bukkit.getPlayer(request.getGifterUuid());
        if (gifter == null || !gifter.isOnline()) {
            return;
        }
        Profile receiverProfile = plugin.getProfile(viewerUuid);
        String receiverDisplay = GiftSupport.buildTargetNameWithRankColor(receiverProfile, request.getTargetName());
        gifter.sendMessage(ChatColor.RED + "Your gift to "
                + receiverDisplay
                + ChatColor.RED + " for "
                + rankDisplay
                + senderDurationDisplay
                + ChatColor.RED + " has expired!");
    }

    private String formatSenderExpiryDuration(Rank rank, Integer mvpPlusPlusDays) {
        Rank safeRank = GiftSupport.safeRank(rank);
        int safeDays = mvpPlusPlusDays == null ? 0 : Math.max(0, mvpPlusPlusDays);
        if (safeRank != Rank.MVP_PLUS_PLUS || safeDays <= 0) {
            return "";
        }
        return ChatColor.RED + " for " + ChatColor.GOLD + safeDays + " Days";
    }

    private String formatReceiverExpiryDuration(Rank rank, Integer mvpPlusPlusDays) {
        Rank safeRank = GiftSupport.safeRank(rank);
        int safeDays = mvpPlusPlusDays == null ? 0 : Math.max(0, mvpPlusPlusDays);
        if (safeRank != Rank.MVP_PLUS_PLUS || safeDays <= 0) {
            return "";
        }
        return ChatColor.GOLD + " " + safeDays + " Days";
    }

    private BaseComponent[] buildPage(CorePlugin plugin, String token) {
        String gifterDisplay = resolveGifterDisplay(plugin);
        String rankDisplay = GiftSupport.buildGiftRankText(giftedRank, mvpPlusPlusDays);
        String supportDomain = "support." + NetworkConstants.DOMAIN;

        TextComponent root = new TextComponent("");
        addLegacy(root, gifterDisplay);
        addLegacy(root, ChatColor.BLACK + " wants to gift you ");
        addLegacy(root, rankDisplay);
        addLegacy(root, ChatColor.BLACK + "!\n");
        addLegacy(root, ChatColor.BLACK + "Will you accept?\n\n");

        TextComponent yes = new TextComponent("");
        yes.addExtra(new TextComponent(buildChoicePadding()));
        TextComponent yesLabel = new TextComponent("YES");
        yesLabel.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        yesLabel.setBold(true);
        yesLabel.setUnderlined(true);
        yesLabel.setItalic(false);
        yesLabel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, buildDecisionCommand(token, true)));
        yesLabel.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[] {new TextComponent(ChatColor.GREEN + "Click here to accept the gift!")}
        ));
        yes.addExtra(yesLabel);
        root.addExtra(yes);
        root.addExtra(new TextComponent("\n\n"));

        TextComponent no = new TextComponent("");
        no.addExtra(new TextComponent(buildChoicePadding()));
        TextComponent noLabel = new TextComponent("NO");
        noLabel.setColor(net.md_5.bungee.api.ChatColor.RED);
        noLabel.setBold(true);
        noLabel.setUnderlined(true);
        noLabel.setItalic(false);
        noLabel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, buildDecisionCommand(token, false)));
        noLabel.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[] {new TextComponent(ChatColor.RED + "Click here to decline the gift!")}
        ));
        no.addExtra(noLabel);
        root.addExtra(no);
        root.addExtra(new TextComponent("\n\n"));

        addLegacy(root, ChatColor.BLACK + "Issues? Contact the Help Desk at\n");
        TextComponent support = new TextComponent(
                ChatColor.RESET + "" + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + ChatColor.UNDERLINE + supportDomain
        );
        support.setColor(net.md_5.bungee.api.ChatColor.LIGHT_PURPLE);
        support.setBold(true);
        support.setUnderlined(true);
        support.setItalic(false);
        support.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://" + supportDomain));
        support.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[] {new TextComponent(ChatColor.GRAY + "Click to open the link!")}
        ));
        root.addExtra(support);
        return new BaseComponent[] {root};
    }

    private String resolveGifterDisplay(CorePlugin plugin) {
        String fallback = gifterName.isEmpty() ? "Player" : gifterName;
        if (plugin == null || gifterUuid == null) {
            return ChatColor.WHITE + fallback;
        }
        Profile gifterProfile = plugin.getProfile(gifterUuid);
        return GiftSupport.buildTargetDisplayName(gifterProfile, fallback);
    }

    private String resolveAuthor() {
        if (gifterName == null || gifterName.isEmpty()) {
            return "Gift System";
        }
        return gifterName;
    }

    private String buildDecisionCommand(String token, boolean yes) {
        String safeToken = token == null ? "" : token.trim();
        return PROMPT_COMMAND + " " + safeToken + " " + (yes ? "yes" : "no");
    }

    private String buildChoicePadding() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < INDENT_SPACES; i++) {
            builder.append(' ');
        }
        return builder.toString();
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
                tag = nbtCompoundClass.newInstance();
            }

            Object pages = nbtListClass.newInstance();
            Object nbtJsonPage = nbtStringClass.getConstructor(String.class).newInstance(jsonPage);
            Method addToList = findSingleArgMethod(nbtListClass, "add", nbtBaseClass);
            if (addToList == null) {
                return null;
            }
            addToList.invoke(pages, nbtJsonPage);

            Method setInCompound = findCompoundSetMethod(nbtCompoundClass, nbtBaseClass);
            if (setInCompound == null) {
                return null;
            }
            setInCompound.invoke(tag, "pages", pages);
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

    private Method findSingleArgMethod(Class<?> owner, String name, Class<?> valueType) {
        if (owner == null || name == null || name.trim().isEmpty()) {
            return null;
        }
        Method fallback = null;
        for (Method method : owner.getMethods()) {
            if (method == null) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params == null || params.length != 1) {
                continue;
            }
            boolean compatible = valueType == null || params[0].isAssignableFrom(valueType) || valueType.isAssignableFrom(params[0]);
            if (!compatible) {
                continue;
            }
            if (name.equals(method.getName())) {
                return method;
            }
            fallback = method;
        }
        return fallback;
    }

    private Method findCompoundSetMethod(Class<?> owner, Class<?> valueType) {
        if (owner == null) {
            return null;
        }
        Method fallback = null;
        for (Method method : owner.getMethods()) {
            if (method == null) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params == null || params.length != 2) {
                continue;
            }
            if (params[0] != String.class) {
                continue;
            }
            boolean compatible = valueType == null || params[1].isAssignableFrom(valueType) || valueType.isAssignableFrom(params[1]);
            if (!compatible) {
                continue;
            }
            if ("set".equals(method.getName())) {
                return method;
            }
            fallback = method;
        }
        return fallback;
    }

    private Material resolveWrittenBookMaterial() {
        Material written = Material.matchMaterial("WRITTEN_BOOK");
        if (written != null) {
            return written;
        }
        Material legacy = Material.matchMaterial("BOOK_AND_QUILL");
        if (legacy != null) {
            return legacy;
        }
        return Material.BOOK;
    }
}
