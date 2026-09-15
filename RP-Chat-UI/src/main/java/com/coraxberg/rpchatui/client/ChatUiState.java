package com.coraxberg.rpchatui.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatUiState {
    public static final String CAT_ALL = "__all__";
    public static final String CAT_GENERAL = "general";
    public static final String CAT_OOC = "ooc";
    public static final String CAT_GM = "gm";
    public static final String CAT_PM = "pm";
    public static final String CAT_FAVORITES = "__favorites__";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("rpchatui-client.json");
    private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\[([^\\[\\]]{2,28})\\]");

    private static final int MIN_W = 260;
    private static final int MIN_H = 170;
    private static final int PAD = 6;
    private static final int RESIZE_EDGE = 3;
    private static final int SIDE_W = 34;
    private static final int MAX_MESSAGES = 1000;
    private static final int DEFAULT_MAX_CHAT_LENGTH = 512;
    private static int serverMaxChatLength = DEFAULT_MAX_CHAT_LENGTH;
    private static final long HUD_MESSAGE_MS = 15_000L;
    private static final long HUD_FADE_MS = 1_500L;

    private static ChatUiConfig config = ChatUiConfig.defaults();
    // Saved user geometry is separate from the temporary fit at a larger GUI scale.
    private static PreferredGeometry preferredGeometry;
    private static int layoutScreenWidth = -1;
    private static int layoutScreenHeight = -1;

    private record PreferredGeometry(ChatLayout.Rect main, ChatLayout.Rect tab, ChatLayout.Rect chat) {
        static PreferredGeometry capture() {
            return new PreferredGeometry(new ChatLayout.Rect(config.x, config.y, config.width, config.height),
                    new ChatLayout.Rect(config.settingsX, config.settingsY, config.settingsW, config.settingsH),
                    new ChatLayout.Rect(config.chatSettingsX, config.chatSettingsY, config.chatSettingsW, config.chatSettingsH));
        }

        void apply() {
            applyMain(main); applyTab(tab); applyChat(chat);
        }
    }

    private static void applyMain(ChatLayout.Rect r) {
        config.x = r.x(); config.y = r.y(); config.width = r.width(); config.height = r.height();
    }
    private static void applyTab(ChatLayout.Rect r) {
        config.settingsX = r.x(); config.settingsY = r.y(); config.settingsW = r.width(); config.settingsH = r.height();
    }
    private static void applyChat(ChatLayout.Rect r) {
        config.chatSettingsX = r.x(); config.chatSettingsY = r.y(); config.chatSettingsW = r.width(); config.chatSettingsH = r.height();
    }

    private static final List<ChatEntry> entries = new ArrayList<>();

    private static String chatInput = "";
    private static int chatCursor = 0;
    private static boolean selectingChatInput = false;
    private static int chatSelectionAnchor = 0;
    private static int chatSelectionCursor = 0;

    private static String searchInput = "";
    private static int searchCursor = 0;
    private static int searchSelectionAnchor = 0;
    private static int searchSelectionCursor = 0;
    private static int searchHit = -1;

    private static int messageScrollLines = 0;
    private static int maxScrollLines = 0;
    private static boolean draggingMessageScrollbar = false;
    private static int scrollbarDragOffsetY = 0;

    private static final List<LinkHitBox> linkHitBoxes = new ArrayList<>();
    private static final List<MessageHitBox> messageHitBoxes = new ArrayList<>();

    private static boolean copyPopupOpen = false;
    private static int copyPopupX = 0;
    private static int copyPopupY = 0;
    private static String copyPopupText = "";
    private static String copyPopupBody = "";
    private static boolean colorPaletteOpen = false;
    private static final Identifier SUBMIT_TEXT = new Identifier("rpchat", "submit_text");
    private static final List<String> tabCompletions = new ArrayList<>();
    private static String tabCompletionPrefix = "";
    private static int tabCompletionIndex = -1;
    private static CompletionKind tabCompletionKind = CompletionKind.NONE;
    private static int tabCompletionStart = -1;
    private static int tabCompletionLastEnd = -1;

    private static final List<String> sentHistory = new ArrayList<>();
    private static int historyIndex = -1;

    private static final List<InputSnapshot> chatUndoStack = new ArrayList<>();

    private static Focus focus = Focus.CHAT_INPUT;

    private static boolean draggingWindow = false;
    private static boolean resizing = false;
    private static int resizeMask = 0;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static int resizeStartX;
    private static int resizeStartY;
    private static int resizeStartW;
    private static int resizeStartH;
    private static int resizeStartMouseX;
    private static int resizeStartMouseY;

    private static int draggingTabIndex = -1;
    private static int firstVisibleTab = 0;
    private static int categoryScroll = 0;

    private static boolean tabSettingsOpen = false;
    private static boolean draggingTabSettings = false;
    private static boolean resizingTabSettings = false;
    private static int tabSettingsResizeMask = 0;
    private static int tabSettingsDragOffsetX;
    private static int tabSettingsDragOffsetY;
    private static int tabSettingsResizeStartX;
    private static int tabSettingsResizeStartY;
    private static int tabSettingsResizeStartW;
    private static int tabSettingsResizeStartH;
    private static int tabSettingsResizeStartMouseX;
    private static int tabSettingsResizeStartMouseY;
    private static String settingsTabId = "";
    private static String settingsName = "";
    private static int settingsNameCursor = 0;
    private static int settingsNameSelectionAnchor = 0;
    private static int settingsNameSelectionCursor = 0;
    private static Set<String> settingsCategories = new LinkedHashSet<>();

    private static boolean chatSettingsOpen = false;
    private static boolean draggingChatSettings = false;
    private static boolean resizingChatSettings = false;
    private static int chatSettingsResizeMask = 0;
    private static int chatSettingsDragOffsetX;
    private static int chatSettingsDragOffsetY;
    private static int chatSettingsResizeStartX;
    private static int chatSettingsResizeStartY;
    private static int chatSettingsResizeStartW;
    private static int chatSettingsResizeStartH;
    private static int chatSettingsResizeStartMouseX;
    private static int chatSettingsResizeStartMouseY;

    private ChatUiState() {
    }

    public static void focusChatInput() {
        focus = Focus.CHAT_INPUT;
        chatCursor = Math.max(0, Math.min(chatCursor, chatInput.length()));
        setChatSelection(chatCursor, chatCursor);
        historyIndex = -1;
    }

    public static void setServerMaxChatLength(int maxLength) {
        serverMaxChatLength = Math.max(1, Math.min(4096, maxLength));

        if (chatInput.length() > serverMaxChatLength) {
            chatInput = chatInput.substring(0, serverMaxChatLength);
            chatCursor = Math.min(chatCursor, chatInput.length());
            setChatSelection(chatCursor, chatCursor);
        }
    }

    private static int getMaxChatLength() {
        return supportsExtendedChat() ? Math.max(1, serverMaxChatLength) : Math.min(256, Math.max(1, serverMaxChatLength));
    }

    private static boolean supportsExtendedChat() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.getNetworkHandler() != null && ClientPlayNetworking.canSend(SUBMIT_TEXT);
    }

    public static void resetConnection() {
        serverMaxChatLength = DEFAULT_MAX_CHAT_LENGTH;
        entries.clear();
        messageHitBoxes.clear(); linkHitBoxes.clear(); copyPopupOpen = false;
        messageScrollLines = 0;
    }

    public static void captureInitialVanillaChatText(String initialText) {
        if (initialText != null && !initialText.isEmpty() && chatInput.isEmpty()) {
            chatInput = initialText;
            chatCursor = chatInput.length();
            setChatSelection(chatCursor, chatCursor);
        }
    }

    private static boolean isChatUiOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.currentScreen instanceof RpChatScreen;
    }

    public static void loadConfig() {
        preferredGeometry = null;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                config = ChatUiConfig.defaults();
                saveConfig();
                return;
            }

            ChatUiConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), ChatUiConfig.class);
            if (loaded == null) loaded = ChatUiConfig.defaults();
            loaded.normalize();
            config = loaded;
            saveConfig();
        } catch (Exception ex) {
            System.err.println("[RPChatUI] Could not load config: " + ex.getMessage());
            config = ChatUiConfig.defaults();
        }
    }

    public static void saveConfig() {
        PreferredGeometry visible = PreferredGeometry.capture();
        try {
            if (preferredGeometry != null) preferredGeometry.apply();
            config.normalize();
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            System.err.println("[RPChatUI] Could not save config: " + ex.getMessage());
        } finally {
            visible.apply();
        }
    }

    public static boolean shouldRenderInHud() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || !(client.currentScreen instanceof RpChatScreen);
    }

    public static void addMessage(Text text) {
        if (text == null) return;

        String plain = text.getString();
        long now = System.currentTimeMillis();


        String category = detectCategory(plain);
        rememberCategory(category);
        maybeCreateAutoTab(category);

        entries.add(new ChatEntry(text.copy(), plain, category, now, new HashMap<>()));
        if (!isChatUiOpen()) {
            messageScrollLines = 0;
        }
        while (entries.size() > MAX_MESSAGES) {
            entries.remove(0);
        }
        updateSearchHitIfNeeded();
    }

    public static void render(DrawContext context, int currentTick, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.getWindow() == null) return;

        config.normalize();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        if (screenWidth != layoutScreenWidth || screenHeight != layoutScreenHeight) {
            // Mouse offsets are in GUI pixels and become invalid when the viewport changes.
            selectingChatInput = false;
            draggingWindow = resizing = draggingTabSettings = resizingTabSettings = false;
            draggingChatSettings = resizingChatSettings = draggingMessageScrollbar = false;
            draggingTabIndex = -1;
            resizeMask = tabSettingsResizeMask = chatSettingsResizeMask = 0;
            layoutScreenWidth = screenWidth;
            layoutScreenHeight = screenHeight;
        }
        if (preferredGeometry == null) preferredGeometry = PreferredGeometry.capture();
        if (!draggingWindow && !resizing) applyMain(preferredGeometry.main());
        if (!draggingTabSettings && !resizingTabSettings) applyTab(preferredGeometry.tab());
        if (!draggingChatSettings && !resizingChatSettings) applyChat(preferredGeometry.chat());
        clampToScreen(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        clampTabSettingsToScreen();
        clampChatSettingsToScreen();

        boolean chatOpen = isChatUiOpen();
        boolean panelVisible = chatOpen || config.alwaysShowWindow || draggingWindow || resizing;

        if (panelVisible) {
            drawWindow(context, mouseX, mouseY);
        }

        drawMessages(context, panelVisible, chatOpen);

        if (chatOpen) {
            drawChatInput(context, mouseX, mouseY);
        }

        if (chatOpen && config.searchOpen) {
            drawSearchPanel(context, mouseX, mouseY);
        }

        if (!chatOpen || (!tabSettingsOpen && !chatSettingsOpen && !copyPopupOpen && !colorPaletteOpen)) return;
        // Flush queued text before overlays and give their backgrounds a real
        // depth above the main window. Paint order alone mixes GUI/font batches.
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 100);
        if (chatOpen && tabSettingsOpen) {
            drawTabSettingsWindow(context, mouseX, mouseY);
        }

        if (chatOpen && chatSettingsOpen) {
            drawChatSettingsWindow(context, mouseX, mouseY);
        }
        context.draw();
        context.getMatrices().translate(0, 0, 100);

        if (chatOpen && copyPopupOpen) drawCopyPopup(context, mouseX, mouseY);
        if (chatOpen && colorPaletteOpen) drawColorPalette(context, mouseX, mouseY);
        context.getMatrices().pop();
    }

    private static void drawWindow(DrawContext context, int mouseX, int mouseY) {
        int x = config.x;
        int y = config.y;
        int w = config.width;
        int h = config.height;

        int panel = withAlpha(config.tintColor(), config.opacity);
        int header = withAlpha(config.tintColor(), Math.min(255, config.opacity + 35));

        context.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x66000000);
        context.fill(x, y, x + w, y + h, panel);
        context.fill(x, y, x + w, y + headerHeight(), header);
        context.fill(x, y, x + w, y + 2, 0xAA000000);
        context.fill(x, y + h - 2, x + w, y + h, 0xAA000000);
        context.fill(x, y, x + 2, y + h, 0xAA000000);
        context.fill(x + w - 2, y, x + w, y + h, 0xAA000000);
        context.fill(x + 2, y + headerHeight(), x + w - 2, y + headerHeight() + 1, 0xAA7580A0);

        drawWoodFrame(context, x, y, w, h);
        drawTabs(context, mouseX, mouseY);
        drawSideButtons(context, mouseX, mouseY);

        int cornerX = x + w - 10;
        int cornerY = y + h - 10;
        context.fill(cornerX, cornerY + 7, cornerX + 8, cornerY + 9, 0x99D8D8E8);
        context.fill(cornerX + 3, cornerY + 4, cornerX + 8, cornerY + 6, 0x99D8D8E8);
    }

    private static void drawTabs(DrawContext context, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = config.x + 5;
        int y = config.y + 4;
        ensureSelectedTabVisible();

        drawButton(context, x, y, 28, 17, "[↔]", isMouseIn(mouseX, mouseY, x, y, 28, 17), false);
        drawButton(context, config.x + 39, y, 20, 17, "‹", isMouseIn(mouseX, mouseY, config.x + 39, y, 20, 17), false);
        drawButton(context, config.x + ChatLayout.tabsEnd(config.width) + 3, y, 20, 17, "›",
                isMouseIn(mouseX, mouseY, config.x + ChatLayout.tabsEnd(config.width) + 3, y, 20, 17), false);
        x = config.x + 63;

        for (int i = firstVisibleTab; i < config.tabs.size(); i++) {
            ChatTab tab = config.tabs.get(i);
            int tw = ChatLayout.tabWidth(config.width, tabWidth(tab));
            if (x + tw > config.x + ChatLayout.tabsEnd(config.width)) break;
            boolean selected = i == config.selectedTab;
            boolean hover = isMouseIn(mouseX, mouseY, x, y, tw, 17);

            int bg = selected ? withAlpha(lighten(config.tintColor(), 0x22), 230) : (hover ? 0xCC22273A : 0xAA171B28);
            int border = selected ? 0xFFB8C2E8 : 0xAA000000;
            context.fill(x, y, x + tw, y + 17, bg);
            context.fill(x, y, x + tw, y + 1, border);
            context.fill(x, y, x + 1, y + 17, 0x882A2F40);
            context.fill(x + tw - 1, y, x + tw, y + 17, 0xDD000000);

            String label = fit(tab.name, Math.max(20, tw - (tab.closable ? 24 : 12)));
            context.drawTextWithShadow(tr, label, x + 8, y + 5, selected ? 0xFFFFFFFF : 0xFFD9D9E6);

            if (tab.closable) {
                int cx = x + tw - 16;
                context.drawTextWithShadow(tr, "×", cx, y + 5, hover ? 0xFFFFA0A0 : 0xFFB8B8C8);
            }

            x += tw + 4;
        }

        int plusX = config.x + ChatLayout.toolbarX(config.width);
        y = config.y + ChatLayout.toolbarY(config.width);
        drawButton(context, plusX, y, 24, 17, "+", isMouseIn(mouseX, mouseY, plusX, y, 24, 17), false);

        int gearX = plusX + 28;
        drawButton(context, gearX, y, 24, 17, "⚙", isMouseIn(mouseX, mouseY, gearX, y, 24, 17), false);

        int searchX = gearX + 28;
        drawButton(context, searchX, y, 24, 17, "⌕", isMouseIn(mouseX, mouseY, searchX, y, 24, 17), config.searchOpen);

        int cfgX = searchX + 28;
        drawButton(context, cfgX, y, 24, 17, "☰", isMouseIn(mouseX, mouseY, cfgX, y, 24, 17), chatSettingsOpen);

        int favX = cfgX + 28;
        drawButton(context, favX, y, 24, 17, "★", isMouseIn(mouseX, mouseY, favX, y, 24, 17), isFavoritesTab(selectedTab()));
    }

    private static void drawSideButtons(DrawContext context, int mouseX, int mouseY) {
        int x = config.x + config.width + 5;
        int y = config.y + headerHeight() + 6;

        drawButton(context, x, y, 24, 20, "+", isMouseIn(mouseX, mouseY, x, y, 24, 20), false);
        drawButton(context, x, y + 24, 24, 20, "−", isMouseIn(mouseX, mouseY, x, y + 24, 24, 20), false);

        String auto = config.autoCreateTabs ? "A+" : "A−";
        drawButton(context, x, y + 52, 24, 20, auto, isMouseIn(mouseX, mouseY, x, y + 52, 24, 20), !config.autoCreateTabs);
    }

    private static void drawButton(DrawContext context, int x, int y, int w, int h, String text, boolean hover, boolean activeGlow) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int bg = activeGlow ? withAlpha(lighten(config.tintColor(), 0x33), 220)
                : woodTheme() ? withAlpha(lighten(config.tintColor(), hover ? 0x24 : 0x0A), 230) : (hover ? 0xDD3F4A69 : 0xCC202638);
        context.fill(x, y, x + w, y + h, bg);
        context.fill(x, y, x + w, y + 1, woodTheme() ? 0xFFD6B678 : hover ? 0xFFDCE2FF : 0xFF657089);
        context.fill(x, y, x + 1, y + h, 0x663A4058);
        context.fill(x + w - 1, y, x + w, y + h, 0xDD000000);
        context.fill(x, y + h - 1, x + w, y + h, 0xDD000000);
        context.drawCenteredTextWithShadow(tr, fit(text, Math.max(1, w - 8)), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private static boolean woodTheme() {
        return config.tint.equals("oak") || config.tint.equals("walnut") || config.tint.equals("elven");
    }

    private static void drawWoodFrame(DrawContext context, int x, int y, int w, int h) {
        if (!woodTheme()) return;
        int trim = config.tint.equals("elven") ? 0xFF9CBD91 : config.tint.equals("walnut") ? 0xFFA6865D : 0xFFC4A265;
        context.drawBorder(x + 1, y + 1, w - 2, h - 2, trim);
        // Static grain is restricted to the header and frame, leaving text clear.
        for (int i = 0; i < 5; i++) {
            int yy = y + 3 + i * 4;
            context.fill(x + 5, yy, x + w - 5, yy + 1, 0x22301908);
        }
        for (int xx : new int[]{x + 3, x + w - 7}) {
            for (int yy : new int[]{y + 3, y + h - 7}) {
                context.fill(xx, yy, xx + 4, yy + 4, trim);
                context.fill(xx + 1, yy + 1, xx + 3, yy + 3, 0xFF352817);
            }
        }
    }

    private static void drawMessages(DrawContext context, boolean panelVisible, boolean chatOpen) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        linkHitBoxes.clear();
        messageHitBoxes.clear();

        int x = config.x + PAD;
        int yTop = config.y + headerHeight() + PAD;
        int yBottom = config.y + config.height - (chatOpen ? getInputAreaHeight() : PAD);
        int screenW = client.getWindow().getScaledWidth();
        int hudTop = client.getWindow().getScaledHeight() - 66;
        if (client.player != null) {
            int healthRows = (int) Math.ceil((client.player.getMaxHealth() + client.player.getAbsorptionAmount()) / 20.0);
            hudTop -= Math.max(0, healthRows - 1) * 10;
        }
        int screenTextWidth = config.width - PAD * 2 - 8;
        if (!chatOpen && ChatLayout.hudLineWidth(x, screenTextWidth, screenW, yBottom - 12, 12, hudTop)
                < Math.min(screenTextWidth, (int) (100 * config.textScale))) {
            yBottom = Math.min(yBottom, hudTop);
        }
        int maxWidth = Math.max(20, (int) (screenTextWidth / config.textScale));
        int lineHeight = Math.max(8, (int) Math.ceil(10 * config.textScale));
        if (!chatOpen) yTop = Math.min(yTop, Math.max(4, yBottom - 3 * lineHeight));
        int visibleLines = Math.max(1, (yBottom - yTop) / lineHeight);
        ChatTab tab = selectedTab();
        long now = System.currentTimeMillis();
        String query = searchInput.strip();

        List<DisplayLine> allLines = new ArrayList<>();

        if (isFavoritesTab(tab)) {
            for (int i = config.favoriteMessages.size() - 1; i >= 0; i--) {
                FavoriteMessage favorite = config.favoriteMessages.get(i);
                int wrapWidth = chatOpen ? maxWidth : Math.max(20, (int) (ChatLayout.hudLineWidth(x, screenTextWidth,
                        screenW, yBottom - (allLines.size() + 1) * lineHeight, lineHeight, hudTop) / config.textScale));
                List<OrderedText> wrapped = tr.wrapLines(Text.literal(favorite.text), wrapWidth);
                for (int j = wrapped.size() - 1; j >= 0; j--) {
                    allLines.add(new DisplayLine(wrapped.get(j), orderedPlain(wrapped.get(j)), CAT_FAVORITES, 255, -1, i,
                            favorite.text, favorite.body == null ? favorite.text : favorite.body));
                }
            }
        } else {
            for (int i = entries.size() - 1; i >= 0; i--) {
                ChatEntry entry = entries.get(i);
                int alpha = 255;

                if (!chatOpen && !config.alwaysShowWindow) {
                    long age = now - entry.timeMs;
                    if (age > HUD_MESSAGE_MS) continue;
                    if (age > HUD_MESSAGE_MS - HUD_FADE_MS) {
                        float t = (HUD_MESSAGE_MS - age) / (float) HUD_FADE_MS;
                        alpha = Math.max(0, Math.min(255, (int) (255 * t)));
                    }
                }

                if (!tabAccepts(tab, entry.category)) continue;

                int wrapWidth = chatOpen ? maxWidth : Math.max(20, (int) (ChatLayout.hudLineWidth(x, screenTextWidth,
                        screenW, yBottom - (allLines.size() + 1) * lineHeight, lineHeight, hudTop) / config.textScale));
                if (entry.wrapped.size() > 4) entry.wrapped.clear();
                List<OrderedText> wrapped = entry.wrapped.computeIfAbsent(wrapWidth, w -> tr.wrapLines(entry.text, w));
                String body = ChatMessageContent.body(entry.text);
                for (int j = wrapped.size() - 1; j >= 0; j--) {
                    allLines.add(new DisplayLine(wrapped.get(j), orderedPlain(wrapped.get(j)), entry.category, alpha, i, -1, entry.plain, body));
                }
            }
        }

        maxScrollLines = Math.max(0, allLines.size() - visibleLines);
        if (config.searchOpen && !query.isBlank()) {
            int hitEntry = currentSearchEntryIndex();
            if (hitEntry >= 0) {
                int lineIndex = 0;
                for (int i = 0; i < allLines.size(); i++) {
                    if (allLines.get(i).entryIndex == hitEntry) {
                        lineIndex = i;
                        break;
                    }
                }
                messageScrollLines = Math.max(0, Math.min(maxScrollLines, lineIndex - visibleLines / 2));
            }
        } else {
            messageScrollLines = Math.max(0, Math.min(maxScrollLines, messageScrollLines));
        }

        int drawY = yBottom - lineHeight;
        context.enableScissor(x, yTop, config.x + config.width - 8, Math.max(yTop, yBottom));
        context.getMatrices().push();
        context.getMatrices().scale(config.textScale, config.textScale, 1.0F);

        int sx = (int) (x / config.textScale);
        int startLine = Math.max(0, Math.min(messageScrollLines, allLines.size()));
        for (int i = startLine; i < allLines.size(); i++) {
            DisplayLine line = allLines.get(i);
            if (drawY < yTop) break;
            int sy = (int) (drawY / config.textScale);
            int color = withAlpha(categoryColor(line.category), line.alpha);
            context.drawTextWithShadow(tr, line.text, sx, sy, color);
            // Original OrderedText retains link styles and all player formatting.

            int screenY = (int) (sy * config.textScale);
            int screenX = (int) (sx * config.textScale);
            int screenLineH = Math.max(10, (int) (11 * config.textScale));
            collectMessageWords(tr, line, sx, sy);

            if (!query.isBlank() && line.entryIndex == currentSearchEntryIndex()) {
                drawSearchHighlights(context, tr, line.plain, query, sx, sy);
            }
            collectLinksForLine(tr, line.plain, sx, sy, config.textScale);

            drawY -= lineHeight;
        }

        context.getMatrices().pop();
        context.disableScissor();

        if (panelVisible && entries.isEmpty()) {
            context.drawTextWithShadow(tr, "Сообщений пока нет", x, yTop + 4, 0xFFB8B8C8);
        }

        if (chatOpen && maxScrollLines > 0) {
            drawMessageScrollbar(context, yTop, yBottom);
        }
    }

    private static String orderedPlain(OrderedText text) {
        StringBuilder plain = new StringBuilder();
        text.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
        return plain.toString();
    }

    private static void collectMessageWords(TextRenderer tr, DisplayLine line, int sx, int sy) {
        if (!isChatUiOpen()) return;
        int[] advance = {0};
        line.text.accept((index, style, cp) -> {
            int w = tr.getWidth(Text.literal(new String(Character.toChars(cp))).setStyle(style));
            if (!Character.isWhitespace(cp) && w > 0) {
                messageHitBoxes.add(new MessageHitBox((int) ((sx + advance[0]) * config.textScale),
                        (int) (sy * config.textScale), (int) Math.ceil((sx + advance[0] + w) * config.textScale),
                        (int) Math.ceil((sy + 9) * config.textScale), line.fullText, line.copyText));
            }
            advance[0] += w;
            return true;
        });
    }

    private static void drawMessageScrollbar(DrawContext context, int yTop, int yBottom) {
        int x = config.x + config.width - 6;
        int trackH = Math.max(1, yBottom - yTop);
        int handleH = Math.max(18, trackH * Math.max(1, trackH - maxScrollLines) / Math.max(trackH, trackH + maxScrollLines));
        int free = Math.max(1, trackH - handleH);
        int handleY = yTop + (maxScrollLines <= 0 ? 0 : ((maxScrollLines - messageScrollLines) * free / maxScrollLines));

        context.fill(x, yTop, x + 4, yBottom, 0x662F3B46);
        context.fill(x, handleY, x + 4, handleY + handleH, draggingMessageScrollbar ? 0xFFDCE2FF : 0xCC7580A0);
    }

    private static boolean startMessageScrollbarDrag(int mx, int my) {
        int yTop = config.y + headerHeight() + PAD;
        int yBottom = config.y + config.height - getInputAreaHeight();
        if (maxScrollLines <= 0) return false;
        int x = config.x + config.width - 8;
        if (!isMouseIn(mx, my, x - 3, yTop, 12, yBottom - yTop)) return false;

        int trackH = Math.max(1, yBottom - yTop);
        int handleH = Math.max(18, trackH * Math.max(1, trackH - maxScrollLines) / Math.max(trackH, trackH + maxScrollLines));
        int free = Math.max(1, trackH - handleH);
        int handleY = yTop + ((maxScrollLines - messageScrollLines) * free / maxScrollLines);
        scrollbarDragOffsetY = Math.max(0, Math.min(handleH, my - handleY));
        draggingMessageScrollbar = true;
        updateMessageScrollbarDrag(my);
        return true;
    }

    private static void updateMessageScrollbarDrag(int my) {
        int yTop = config.y + headerHeight() + PAD;
        int yBottom = config.y + config.height - getInputAreaHeight();
        int trackH = Math.max(1, yBottom - yTop);
        int handleH = Math.max(18, trackH * Math.max(1, trackH - maxScrollLines) / Math.max(trackH, trackH + maxScrollLines));
        int free = Math.max(1, trackH - handleH);
        int handleY = Math.max(yTop, Math.min(yTop + free, my - scrollbarDragOffsetY));
        messageScrollLines = Math.max(0, Math.min(maxScrollLines, maxScrollLines - ((handleY - yTop) * maxScrollLines / free)));
    }

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s]+|www\\.[^\\s]+)");

    private static void collectLinksForLine(TextRenderer tr, String line, int scaledX, int scaledY, float scale) {
        if (!isChatUiOpen()) return;
        Matcher matcher = URL_PATTERN.matcher(line);
        while (matcher.find()) {
            String raw = matcher.group(1);
            int x0 = (int) ((scaledX + tr.getWidth(line.substring(0, matcher.start()))) * scale);
            int x1 = (int) ((scaledX + tr.getWidth(line.substring(0, matcher.end()))) * scale);
            int y0 = (int) (scaledY * scale);
            int y1 = (int) ((scaledY + 10) * scale);
            linkHitBoxes.add(new LinkHitBox(x0, y0, x1, y1, raw));
        }
    }

    private static boolean clickLink(int mx, int my) {
        for (LinkHitBox box : linkHitBoxes) {
            if (mx >= box.x0 && mx <= box.x1 && my >= box.y0 && my <= box.y1) {
                String url = box.url;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                try {
                    Util.getOperatingSystem().open(url);
                } catch (Exception ex) {
                    System.err.println("[RPChatUI] Could not open link: " + ex.getMessage());
                }
                return true;
            }
        }
        return false;
    }

    private static void drawSearchHighlights(DrawContext context, TextRenderer tr, String line, String query, int sx, int sy) {
        String lower = line.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;

        int idx = lower.indexOf(q);
        while (idx >= 0) {
            int x0 = sx + tr.getWidth(line.substring(0, idx));
            int x1 = sx + tr.getWidth(line.substring(0, Math.min(line.length(), idx + query.length())));

            context.fill(x0 - 1, sy - 1, x1 + 1, sy, 0xFFFFFF00);
            context.fill(x0 - 1, sy + 10, x1 + 1, sy + 11, 0xFFFFFF00);
            context.fill(x0 - 1, sy - 1, x0, sy + 11, 0xFFFFFF00);
            context.fill(x1, sy - 1, x1 + 1, sy + 11, 0xFFFFFF00);

            idx = lower.indexOf(q, idx + Math.max(1, q.length()));
        }
    }

    private static int currentSearchEntryIndex() {
        ChatTab tab = selectedTab();
        if (!config.searchOpen || searchInput.strip().isEmpty()) return -1;
        List<Integer> hits = searchHits(tab);
        if (hits.isEmpty()) return -1;
        searchHit = Math.max(0, Math.min(searchHit, hits.size() - 1));
        return hits.get(searchHit);
    }

    private static List<Integer> searchHits(ChatTab tab) {
        String q = searchInput.strip().toLowerCase(Locale.ROOT);
        List<Integer> hits = new ArrayList<>();
        if (q.isEmpty()) return hits;

        for (int i = 0; i < entries.size(); i++) {
            ChatEntry entry = entries.get(i);
            if (!tabAccepts(tab, entry.category)) continue;
            if (entry.plain.toLowerCase(Locale.ROOT).contains(q)) hits.add(i);
        }
        return hits;
    }

    private static void updateSearchHitIfNeeded() {
        if (config.searchOpen && !searchInput.strip().isEmpty()) {
            List<Integer> hits = searchHits(selectedTab());
            if (hits.isEmpty()) searchHit = -1;
            else if (searchHit < 0) searchHit = hits.size() - 1;
            else searchHit = Math.min(searchHit, hits.size() - 1);
        }
    }

    private static void jumpSearch(int delta) {
        if (!config.searchOpen || searchInput.strip().isEmpty()) return;
        List<Integer> hits = searchHits(selectedTab());
        if (hits.isEmpty()) {
            searchHit = -1;
            return;
        }
        if (searchHit < 0) searchHit = delta >= 0 ? 0 : hits.size() - 1;
        else searchHit = Math.floorMod(searchHit + delta, hits.size());
    }

    private static int inputTextWidth() {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int counterWidth = tr.getWidth(getMaxChatLength() + "/" + getMaxChatLength());
        return Math.max(20, config.width - PAD * 2 - counterWidth - 14);
    }

    private static List<ChatInputLayout.Line> inputLines() {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        return ChatInputLayout.wrap(chatInput, inputTextWidth(), tr::getWidth);
    }

    private static int getInputAreaHeight() {
        int lines = ChatLayout.inputLines(config.height, headerHeight(), inputLines().size());
        return 38 + lines * 12;
    }

    private static void drawChatInput(DrawContext context, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = config.x + PAD;
        int areaH = getInputAreaHeight();
        int toolbarY = config.y + config.height - areaH + 3;
        String[] labels = {"B", "I", "U", "S", "Цвет", "Сброс"};
        for (int i = 0, bx = x; i < labels.length; i++) {
            int bw = i < 4 ? 22 : 44;
            drawButton(context, bx, toolbarY, bw, 17, labels[i], isMouseIn(mouseX, mouseY, bx, toolbarY, bw, 17), false);
            bx += bw + 3;
        }
        int y = toolbarY + 22;
        int w = config.width - PAD * 2;
        int boxH = areaH - 30;
        drawTextBox(context, x, y, w, boxH, focus == Focus.CHAT_INPUT);
        List<ChatInputLayout.Line> lines = inputLines();
        int visible = Math.max(1, (areaH - 38) / 12);
        int cursorLine = ChatInputLayout.cursorLine(lines, chatCursor);
        int firstLine = Math.max(0, cursorLine - visible + 1);
        int yy = y + 5;
        context.enableScissor(x + 2, y + 2, x + 5 + inputTextWidth(), y + boxH - 1);
        for (int i = firstLine; i < Math.min(lines.size(), firstLine + visible); i++) {
            ChatInputLayout.Line line = lines.get(i);
            int length = line.text().length();
            int anchor = Math.max(0, Math.min(length, chatSelectionAnchor - line.start()));
            int selection = Math.max(0, Math.min(length, chatSelectionCursor - line.start()));
            // Draw selection on every affected line, cursor on its own line only.
            if (focus == Focus.CHAT_INPUT && anchor != selection) {
                int a = Math.min(anchor, selection), b = Math.max(anchor, selection);
                context.fill(x + 5 + tr.getWidth(line.text().substring(0, a)), yy - 1,
                        x + 5 + tr.getWidth(line.text().substring(0, b)), yy + 10, 0xAA3F4A69);
            }
            context.drawTextWithShadow(tr, Text.literal(line.text()), x + 5, yy, 0xFFFFFFFF);
            if (focus == Focus.CHAT_INPUT && i == cursorLine && blink()) {
                int caret = Math.max(0, Math.min(length, chatCursor - line.start()));
                int cx = x + 5 + tr.getWidth(line.text().substring(0, caret));
                context.fill(cx, yy - 1, cx + 1, yy + 10, 0xFFFFFFFF);
            }
            yy += 12;
        }
        context.draw();
        context.disableScissor();
        String count = chatInput.length() + "/" + getMaxChatLength();
        context.drawTextWithShadow(tr, count, x + w - tr.getWidth(count) - 4, y + boxH - 12,
                chatInput.length() >= getMaxChatLength() ? 0xFFFF9090 : 0xFF9AA4BD);
    }

    private static boolean handleFormatClick(int mx, int my) {
        int x = config.x + PAD, y = config.y + config.height - getInputAreaHeight() + 3;
        String[] codes = {"&l", "&o", "&n", "&m", "", "&r"};
        for (int i = 0; i < codes.length; i++) {
            int w = i < 4 ? 22 : 44;
            if (isMouseIn(mx, my, x, y, w, 17)) {
                if (i == 4) colorPaletteOpen = !colorPaletteOpen;
                else applyFormatting(codes[i]);
                return true;
            }
            x += w + 3;
        }
        return false;
    }

    private static void applyFormatting(String code) {
        int a = Math.min(chatSelectionAnchor, chatSelectionCursor), b = Math.max(chatSelectionAnchor, chatSelectionCursor);
        String replacement = code + chatInput.substring(a, b) + (a == b ? "" : "&r");
        if (chatInput.length() - (b - a) + replacement.length() > getMaxChatLength()) return;
        insertChat(replacement);
        focus = Focus.CHAT_INPUT;
        colorPaletteOpen = false;
    }

    private static final int[] FORMAT_COLORS = {0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
            0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
    private static void drawColorPalette(DrawContext context, int mx, int my) {
        int x = config.x + PAD, y = config.y + config.height - getInputAreaHeight() - 41;
        context.fill(x, y, x + 180, y + 40, 0xFF171A22);
        for (int i = 0; i < 16; i++) {
            int px = x + 3 + (i % 8) * 22, py = y + 3 + (i / 8) * 18;
            context.fill(px, py, px + 18, py + 14, 0xFF000000 | FORMAT_COLORS[i]);
            if (isMouseIn(mx, my, px, py, 18, 14)) context.drawBorder(px - 1, py - 1, 20, 16, 0xFFFFFFFF);
        }
    }
    private static boolean handleColorClick(int mx, int my) {
        if (!colorPaletteOpen) return false;
        int x = config.x + PAD, y = config.y + config.height - getInputAreaHeight() - 41;
        for (int i = 0; i < 16; i++) {
            if (isMouseIn(mx, my, x + 3 + (i % 8) * 22, y + 3 + (i / 8) * 18, 18, 14)) {
                applyFormatting("&" + "0123456789abcdef".charAt(i)); return true;
            }
        }
        colorPaletteOpen = false;
        return true;
    }

    private static void drawTabSettingsWindow(DrawContext context, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ChatTab tab = getTabById(settingsTabId);
        if (tab == null) {
            tabSettingsOpen = false;
            return;
        }

        int x = config.settingsX;
        int y = config.settingsY;
        int w = config.settingsW;
        int h = config.settingsH;

        drawPanelShell(context, x, y, w, h, "Настройка вкладки", mouseX, mouseY);

        context.drawTextWithShadow(tr, "Название", x + 12, y + 34, 0xFFD9D9E6);
        drawTextBox(context, x + 12, y + 46, w - 24, 18, focus == Focus.SETTINGS_NAME);
        drawSingleLineText(context, tr, w - 34, settingsName, settingsNameCursor, settingsNameSelectionAnchor, settingsNameSelectionCursor,
                x + 17, y + 51, focus == Focus.SETTINGS_NAME, 0xFFFFFFFF);

        context.drawTextWithShadow(tr, "Типы сообщений", x + 12, y + 74, 0xFFD9D9E6);

        int cy = y + 88;
        List<String> cats = knownCategories();
        categoryScroll = Math.max(0, Math.min(categoryScroll, Math.max(0, cats.size() - ChatLayout.categoryRows(h))));
        for (int ci = categoryScroll; ci < Math.min(cats.size(), categoryScroll + ChatLayout.categoryRows(h)); ci++) {
            String cat = cats.get(ci);
            boolean selected = settingsCategories.contains(cat);
            drawButton(context, x + 12, cy, w - 24, 18, (selected ? "✓ " : "— ") + displayName(cat),
                    isMouseIn(mouseX, mouseY, x + 12, cy, w - 24, 18), selected);
            cy += 22;
        }

        drawButton(context, x + 12, y + h - 54, 24, 18, "‹", isMouseIn(mouseX, mouseY, x + 12, y + h - 54, 24, 18), false);
        drawButton(context, x + w - 36, y + h - 54, 24, 18, "›", isMouseIn(mouseX, mouseY, x + w - 36, y + h - 54, 24, 18), false);
        context.drawCenteredTextWithShadow(tr, (Math.min(cats.size(), categoryScroll + 1)) + "–" + Math.min(cats.size(), categoryScroll + ChatLayout.categoryRows(h)) + "/" + cats.size(), x + w / 2, y + h - 49, 0xFFD9D9E6);

        drawButton(context, x + 12, y + h - 28, 96, 20, "Сохранить", isMouseIn(mouseX, mouseY, x + 12, y + h - 28, 96, 20), false);
        drawButton(context, x + w - 108, y + h - 28, 96, 20, "Отмена", isMouseIn(mouseX, mouseY, x + w - 108, y + h - 28, 96, 20), false);
        drawResizeCorner(context, x, y, w, h);
    }

    private static void drawChatSettingsWindow(DrawContext context, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = config.chatSettingsX;
        int y = config.chatSettingsY;
        int w = config.chatSettingsW;
        int h = config.chatSettingsH;

        drawPanelShell(context, x, y, w, h, "Настройки чата", mouseX, mouseY);

        int cy = y + 30;
        context.drawTextWithShadow(tr, "Оформление", x + 12, cy, 0xFFD9D9E6);
        drawButton(context, x + 12, cy + 14, 30, 20, "<", isMouseIn(mouseX, mouseY, x + 12, cy + 14, 30, 20), false);
        drawButton(context, x + 48, cy + 14, w - 96, 20, tintName(config.tint), false, true);
        drawButton(context, x + w - 42, cy + 14, 30, 20, ">", isMouseIn(mouseX, mouseY, x + w - 42, cy + 14, 30, 20), false);

        cy += 36;
        context.drawTextWithShadow(tr, "Прозрачность: " + config.opacity, x + 12, cy, 0xFFD9D9E6);
        drawButton(context, x + 12, cy + 14, 30, 20, "−", isMouseIn(mouseX, mouseY, x + 12, cy + 14, 30, 20), false);
        drawButton(context, x + w - 42, cy + 14, 30, 20, "+", isMouseIn(mouseX, mouseY, x + w - 42, cy + 14, 30, 20), false);

        cy += 36;
        drawButton(context, x + 12, cy, w - 24, 20,
                (config.alwaysShowWindow ? "✓ " : "— ") + "Всегда показывать окно",
                isMouseIn(mouseX, mouseY, x + 12, cy, w - 24, 20), config.alwaysShowWindow);

        drawButton(context, x + 12, cy + 24, w - 24, 20,
                (config.closeAfterSend ? "✓ " : "— ") + "Закрывать после отправки",
                isMouseIn(mouseX, mouseY, x + 12, cy + 24, w - 24, 20), config.closeAfterSend);

        drawButton(context, x + 12, y + h - 28, 96, 20, "Закрыть", isMouseIn(mouseX, mouseY, x + 12, y + h - 28, 96, 20), false);
        drawResizeCorner(context, x, y, w, h);
    }

    private static void drawPanelShell(DrawContext context, int x, int y, int w, int h, String title, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        context.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x66000000);
        context.fill(x, y, x + w, y + h, withAlpha(config.tintColor(), 255));
        context.fill(x, y, x + w, y + 24, withAlpha(lighten(config.tintColor(), 0x22), 255));
        context.fill(x, y + 24, x + w, y + 25, 0xFF7580A0);
        context.fill(x, y, x + w, y + 2, 0xAA000000);
        context.fill(x, y + h - 2, x + w, y + h, 0xAA000000);
        context.fill(x, y, x + 2, y + h, 0xAA000000);
        context.fill(x + w - 2, y, x + w, y + h, 0xAA000000);

        drawWoodFrame(context, x, y, w, h);
        context.drawTextWithShadow(tr, title, x + 10, y + 8, 0xFFFFFFFF);
        drawButton(context, x + w - 24, y + 4, 18, 16, "×", isMouseIn(mouseX, mouseY, x + w - 24, y + 4, 18, 16), false);
    }

    private static void drawResizeCorner(DrawContext context, int x, int y, int w, int h) {
        int cornerX = x + w - 10;
        int cornerY = y + h - 10;
        context.fill(cornerX, cornerY + 7, cornerX + 8, cornerY + 9, 0x99D8D8E8);
        context.fill(cornerX + 3, cornerY + 4, cornerX + 8, cornerY + 6, 0x99D8D8E8);
    }

    private static void drawSearchPanel(DrawContext context, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = config.x;
        int y = config.y + ChatLayout.baseHeader(config.width) + 10;
        int w = Math.min(config.width, 430);

        context.fill(x + 4, y + 4, x + w + 4, y + 27, 0x66000000);
        context.fill(x, y, x + w, y + 24, withAlpha(config.tintColor(), Math.min(240, config.opacity + 45)));
        context.fill(x, y, x + w, y + 1, 0xFF7580A0);

        context.drawTextWithShadow(tr, "⌕", x + 7, y + 8, 0xFFFFFFFF);
        drawTextBox(context, x + 24, y + 3, w - 96, 18, focus == Focus.SEARCH_INPUT);
        drawSingleLineText(context, tr, w - 106, searchInput, searchCursor, searchSelectionAnchor, searchSelectionCursor,
                x + 29, y + 8, focus == Focus.SEARCH_INPUT, 0xFFFFFFFF);

        drawButton(context, x + w - 66, y + 3, 18, 18, "‹", isMouseIn(mouseX, mouseY, x + w - 66, y + 3, 18, 18), false);
        drawButton(context, x + w - 45, y + 3, 18, 18, "›", isMouseIn(mouseX, mouseY, x + w - 45, y + 3, 18, 18), false);
        drawButton(context, x + w - 24, y + 3, 18, 18, "×", isMouseIn(mouseX, mouseY, x + w - 24, y + 3, 18, 18), false);

        String count = searchCountText();
        context.drawTextWithShadow(tr, count, x + 7, y - 10, 0xFFE8E8A0);
    }

    private static String searchCountText() {
        if (searchInput.strip().isEmpty()) return "";
        List<Integer> hits = searchHits(selectedTab());
        if (hits.isEmpty()) return "не найдено";
        searchHit = Math.max(0, Math.min(searchHit, hits.size() - 1));
        return (searchHit + 1) + "/" + hits.size();
    }

    private static void drawCopyPopup(DrawContext context, int mouseX, int mouseY) {
        int w = 132;
        int h = 46;
        clampCopyPopupToScreen(w, h);

        int x = copyPopupX;
        int y = copyPopupY;

        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 800.0F);

        context.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x88000000);
        context.fill(x, y, x + w, y + h, withAlpha(config.tintColor(), Math.min(250, config.opacity + 70)));
        context.fill(x, y, x + w, y + 1, 0xFFE5EAFF);
        context.fill(x, y, x + 1, y + h, 0xFF657089);
        context.fill(x + w - 1, y, x + w, y + h, 0xEE000000);
        context.fill(x, y + h - 1, x + w, y + h, 0xEE000000);

        drawButton(context, x + 4, y + 4, w - 8, 18, "копировать", isMouseIn(mouseX, mouseY, x + 4, y + 4, w - 8, 18), false);

        String favText = isFavorited(copyPopupText) ? "убрать из избранного" : "в избранное";
        drawButton(context, x + 4, y + 24, w - 8, 18, favText, isMouseIn(mouseX, mouseY, x + 4, y + 24, w - 8, 18), isFavorited(copyPopupText));

        context.getMatrices().pop();
    }

    private static void clampCopyPopupToScreen(int popupW, int popupH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        copyPopupX = Math.max(4, Math.min(copyPopupX, screenW - popupW - 4));
        copyPopupY = Math.max(4, Math.min(copyPopupY, screenH - popupH - 4));
    }

    private static void drawTextBox(DrawContext context, int x, int y, int w, int h, boolean focused) {
        context.fill(x, y, x + w, y + h, 0xCC05070B);
        context.fill(x, y, x + w, y + 1, focused ? 0xFFDCE2FF : 0xFF657089);
        context.fill(x, y, x + 1, y + h, 0x663A4058);
        context.fill(x + w - 1, y, x + w, y + h, 0xDD000000);
        context.fill(x, y + h - 1, x + w, y + h, 0xDD000000);
    }

    private static void drawSelectableText(DrawContext context, TextRenderer tr, String text, int cursor, int anchor, int selectionCursor,
                                           int x, int y, boolean focused, int color) {
        int a = Math.min(anchor, selectionCursor);
        int b = Math.max(anchor, selectionCursor);

        if (focused && a != b) {
            int x0 = x + tr.getWidth(text.substring(0, Math.max(0, Math.min(a, text.length()))));
            int x1 = x + tr.getWidth(text.substring(0, Math.max(0, Math.min(b, text.length()))));
            context.fill(x0, y - 1, x1, y + 10, 0xAA3F4A69);
        }

        context.drawTextWithShadow(tr, text, x, y, color);

        if (focused && blink()) {
            int cx = x + tr.getWidth(text.substring(0, Math.max(0, Math.min(cursor, text.length()))));
            context.fill(cx, y - 1, cx + 1, y + 10, 0xFFFFFFFF);
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isChatUiOpen() && !tabSettingsOpen && !chatSettingsOpen) {
            copyPopupOpen = false;
            return clickMessage((int) mouseX, (int) mouseY);
        }
        if (button != 0) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isChatUiOpen()) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (handleColorClick(mx, my)) return true;
        if (handleCopyPopupClick(mx, my)) return true;
        if (tabSettingsOpen && handleTabSettingsClick(mx, my)) return true;
        if (chatSettingsOpen && handleChatSettingsClick(mx, my)) return true;
        if (config.searchOpen && handleSearchClick(mx, my)) return true;
        if (handleFormatClick(mx, my)) return true;
        if (clickLink(mx, my)) return true;
        if (clickMessage(mx, my)) return true;
        if (startMessageScrollbarDrag(mx, my)) return true;

        if (!isInsideWhole(mx, my)) {
            focus = Focus.CHAT_INPUT;
            return true;
        }

        int resize = hitResize(mx, my);
        if (resize != 0) {
            resizing = true;
            resizeMask = resize;
            resizeStartMouseX = mx;
            resizeStartMouseY = my;
            resizeStartX = config.x;
            resizeStartY = config.y;
            resizeStartW = config.width;
            resizeStartH = config.height;
            return true;
        }

        if (handleSideClick(mx, my)) return true;

        if (isMouseIn(mx, my, config.x + 39, config.y + 4, 20, 17)) { cycleTab(-1); return true; }
        if (isMouseIn(mx, my, config.x + ChatLayout.tabsEnd(config.width) + 3, config.y + 4, 20, 17)) { cycleTab(1); return true; }

        int plusX = config.x + ChatLayout.toolbarX(config.width);
        int tabY = config.y + ChatLayout.toolbarY(config.width);
        if (isMouseIn(mx, my, plusX, tabY, 24, 17)) {
            createCustomTab();
            return true;
        }
        if (isMouseIn(mx, my, plusX + 28, tabY, 24, 17)) {
            openSelectedTabSettings();
            return true;
        }
        if (isMouseIn(mx, my, plusX + 56, tabY, 24, 17)) {
            config.searchOpen = !config.searchOpen;
            if (config.searchOpen) focus = Focus.SEARCH_INPUT;
            saveConfig();
            return true;
        }
        if (isMouseIn(mx, my, plusX + 84, tabY, 24, 17)) {
            openChatSettings();
            return true;
        }
        if (isMouseIn(mx, my, plusX + 112, tabY, 24, 17)) {
            openFavoritesTab();
            return true;
        }

        if (isMouseIn(mx, my, config.x + PAD, config.y + config.height - getInputAreaHeight() + 25, config.width - PAD * 2, getInputAreaHeight() - 30)) {
            focus = Focus.CHAT_INPUT;
            chatCursor = chatCursorAt(mx, my);
            selectingChatInput = true;
            setChatSelection(chatCursor, chatCursor);
            return true;
        }

        if (isMouseIn(mx, my, config.x + 5, config.y + 4, 28, 17)) {
            draggingWindow = true;
            dragOffsetX = mx - config.x;
            dragOffsetY = my - config.y;
            return true;
        }

        TabHit hit = hitTab(mx, my);
        if (hit != null) {
            if (hit.close) {
                closeTab(hit.index);
                return true;
            }

            config.selectedTab = hit.index;
            draggingTabIndex = hit.index;
            focus = Focus.CHAT_INPUT;
            saveConfig();
            return true;
        }

        if (my >= config.y && my <= config.y + headerHeight()) {
            draggingWindow = true;
            dragOffsetX = mx - config.x;
            dragOffsetY = my - config.y;
            return true;
        }

        focus = Focus.CHAT_INPUT;
        return true;
    }

    private static int chatCursorAt(int mx, int my) {
        List<ChatInputLayout.Line> lines = inputLines();
        int visible = Math.max(1, (getInputAreaHeight() - 38) / 12);
        int first = Math.max(0, ChatInputLayout.cursorLine(lines, chatCursor) - visible + 1);
        int row = first + Math.max(0, (my - (config.y + config.height - getInputAreaHeight() + 30)) / 12);
        ChatInputLayout.Line line = lines.get(Math.min(row, lines.size() - 1));
        int local = 0, relX = mx - (config.x + PAD + 5);
        while (local < line.text().length() && MinecraftClient.getInstance().textRenderer.getWidth(line.text().substring(0, local + 1)) <= relX) local++;
        return line.start() + local;
    }

    private static boolean handleTabSettingsClick(int mx, int my) {
        int x = config.settingsX;
        int y = config.settingsY;
        int w = config.settingsW;
        int h = config.settingsH;

        int resize = hitPanelResize(mx, my, x, y, w, h);
        if (resize != 0) {
            resizingTabSettings = true;
            tabSettingsResizeMask = resize;
            tabSettingsResizeStartMouseX = mx;
            tabSettingsResizeStartMouseY = my;
            tabSettingsResizeStartX = x;
            tabSettingsResizeStartY = y;
            tabSettingsResizeStartW = w;
            tabSettingsResizeStartH = h;
            return true;
        }

        if (!isMouseIn(mx, my, x, y, w, h)) return false;

        if (isMouseIn(mx, my, x + w - 24, y + 4, 18, 16)) {
            tabSettingsOpen = false;
            focus = Focus.CHAT_INPUT;
            return true;
        }

        if (isMouseIn(mx, my, x + 12, y + 46, w - 24, 18)) {
            focus = Focus.SETTINGS_NAME;
            settingsNameCursor = settingsName.length();
            setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
            return true;
        }

        if (isMouseIn(mx, my, x + 12, y + h - 54, 24, 18)) { categoryScroll = Math.max(0, categoryScroll - ChatLayout.categoryRows(h)); return true; }
        if (isMouseIn(mx, my, x + w - 36, y + h - 54, 24, 18)) { categoryScroll = Math.min(Math.max(0, knownCategories().size() - ChatLayout.categoryRows(h)), categoryScroll + ChatLayout.categoryRows(h)); return true; }

        int cy = y + 88;
        List<String> cats = knownCategories();
        categoryScroll = Math.max(0, Math.min(categoryScroll, Math.max(0, cats.size() - ChatLayout.categoryRows(h))));
        for (int ci = categoryScroll; ci < Math.min(cats.size(), categoryScroll + ChatLayout.categoryRows(h)); ci++) {
            String cat = cats.get(ci);
            if (isMouseIn(mx, my, x + 12, cy, w - 24, 18)) {
                if (settingsCategories.contains(cat)) settingsCategories.remove(cat);
                else settingsCategories.add(cat);
                return true;
            }
            cy += 22;
        }

        if (isMouseIn(mx, my, x + 12, y + h - 28, 96, 20)) {
            ChatTab tab = getTabById(settingsTabId);
            saveTab(tab, settingsName, settingsCategories);
            tabSettingsOpen = false;
            focus = Focus.CHAT_INPUT;
            return true;
        }

        if (isMouseIn(mx, my, x + w - 108, y + h - 28, 96, 20)) {
            tabSettingsOpen = false;
            focus = Focus.CHAT_INPUT;
            return true;
        }

        if (my >= y && my <= y + 24) {
            draggingTabSettings = true;
            tabSettingsDragOffsetX = mx - x;
            tabSettingsDragOffsetY = my - y;
            return true;
        }

        return true;
    }

    private static boolean handleChatSettingsClick(int mx, int my) {
        int x = config.chatSettingsX;
        int y = config.chatSettingsY;
        int w = config.chatSettingsW;
        int h = config.chatSettingsH;

        int resize = hitPanelResize(mx, my, x, y, w, h);
        if (resize != 0) {
            resizingChatSettings = true;
            chatSettingsResizeMask = resize;
            chatSettingsResizeStartMouseX = mx;
            chatSettingsResizeStartMouseY = my;
            chatSettingsResizeStartX = x;
            chatSettingsResizeStartY = y;
            chatSettingsResizeStartW = w;
            chatSettingsResizeStartH = h;
            return true;
        }

        if (!isMouseIn(mx, my, x, y, w, h)) return false;

        if (isMouseIn(mx, my, x + w - 24, y + 4, 18, 16) || isMouseIn(mx, my, x + 12, y + h - 28, 96, 20)) {
            chatSettingsOpen = false;
            focus = Focus.CHAT_INPUT;
            saveConfig();
            return true;
        }

        int cy = y + 30;
        if (isMouseIn(mx, my, x + 12, cy + 14, 30, 20)) {
            cycleTint(-1);
            saveConfig();
            return true;
        }
        if (isMouseIn(mx, my, x + w - 42, cy + 14, 30, 20)) {
            cycleTint(1);
            saveConfig();
            return true;
        }

        cy += 36;
        if (isMouseIn(mx, my, x + 12, cy + 14, 30, 20)) {
            config.opacity = Math.max(40, config.opacity - 15);
            saveConfig();
            return true;
        }
        if (isMouseIn(mx, my, x + w - 42, cy + 14, 30, 20)) {
            config.opacity = Math.min(245, config.opacity + 15);
            saveConfig();
            return true;
        }

        cy += 36;
        if (isMouseIn(mx, my, x + 12, cy, w - 24, 20)) {
            config.alwaysShowWindow = !config.alwaysShowWindow;
            saveConfig();
            return true;
        }

        if (isMouseIn(mx, my, x + 12, cy + 24, w - 24, 20)) {
            config.closeAfterSend = !config.closeAfterSend;
            saveConfig();
            return true;
        }

        if (my >= y && my <= y + 24) {
            draggingChatSettings = true;
            chatSettingsDragOffsetX = mx - x;
            chatSettingsDragOffsetY = my - y;
            return true;
        }

        return true;
    }

    private static boolean handleSearchClick(int mx, int my) {
        int x = config.x;
        int y = config.y + ChatLayout.baseHeader(config.width) + 10;
        int w = Math.min(config.width, 430);

        if (!isMouseIn(mx, my, x, y, w, 24) && !isMouseIn(mx, my, x, y - 12, 100, 12)) return false;

        if (isMouseIn(mx, my, x + w - 24, y + 3, 18, 18)) {
            config.searchOpen = false;
            searchInput = "";
            searchCursor = 0;
            setSearchSelection(0, 0);
            searchHit = -1;
            focus = Focus.CHAT_INPUT;
            saveConfig();
            return true;
        }

        if (isMouseIn(mx, my, x + w - 66, y + 3, 18, 18)) {
            jumpSearch(-1);
            return true;
        }

        if (isMouseIn(mx, my, x + w - 45, y + 3, 18, 18)) {
            jumpSearch(1);
            return true;
        }

        if (isMouseIn(mx, my, x + 24, y + 3, w - 96, 18)) {
            focus = Focus.SEARCH_INPUT;
            searchCursor = searchInput.length();
            setSearchSelection(searchCursor, searchCursor);
            return true;
        }

        return true;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (selectingChatInput) {
            chatCursor = chatCursorAt(mx, my);
            setChatSelection(chatSelectionAnchor, chatCursor);
            return true;
        }

        if (draggingMessageScrollbar) {
            updateMessageScrollbarDrag(my);
            return true;
        }

        if (resizingTabSettings) {
            resizePanel(mx, my, true);
            return true;
        }

        if (draggingTabSettings) {
            config.settingsX = mx - tabSettingsDragOffsetX;
            config.settingsY = my - tabSettingsDragOffsetY;
            clampTabSettingsToScreen();
            return true;
        }

        if (resizingChatSettings) {
            resizePanel(mx, my, false);
            return true;
        }

        if (draggingChatSettings) {
            config.chatSettingsX = mx - chatSettingsDragOffsetX;
            config.chatSettingsY = my - chatSettingsDragOffsetY;
            clampChatSettingsToScreen();
            return true;
        }

        if (resizing) {
            resizeMain(mx, my);
            return true;
        }

        if (draggingWindow) {
            config.x = mx - dragOffsetX;
            config.y = my - dragOffsetY;
            clampToScreen(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
            return true;
        }

        if (draggingTabIndex >= 0) {
            TabHit hover = hitTab(mx, my);
            if (hover != null && hover.index != draggingTabIndex && hover.index > 0) {
                Collections.swap(config.tabs, draggingTabIndex, hover.index);
                config.selectedTab = hover.index;
                draggingTabIndex = hover.index;
            }
            return true;
        }

        return false;
    }

    private static void resizeMain(int mx, int my) {
        int dx = mx - resizeStartMouseX;
        int dy = my - resizeStartMouseY;
        int nx = resizeStartX;
        int ny = resizeStartY;
        int nw = resizeStartW;
        int nh = resizeStartH;

        if ((resizeMask & 1) != 0) { nx = resizeStartX + dx; nw = resizeStartW - dx; }
        if ((resizeMask & 2) != 0) { nw = resizeStartW + dx; }
        if ((resizeMask & 4) != 0) { ny = resizeStartY + dy; nh = resizeStartH - dy; }
        if ((resizeMask & 8) != 0) { nh = resizeStartH + dy; }

        if (nw < MIN_W) { if ((resizeMask & 1) != 0) nx -= MIN_W - nw; nw = MIN_W; }
        if (nh < MIN_H) { if ((resizeMask & 4) != 0) ny -= MIN_H - nh; nh = MIN_H; }

        config.x = nx;
        config.y = ny;
        config.width = nw;
        config.height = nh;
        clampToScreen(MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
    }

    private static void resizePanel(int mx, int my, boolean tabPanel) {
        int startX = tabPanel ? tabSettingsResizeStartX : chatSettingsResizeStartX;
        int startY = tabPanel ? tabSettingsResizeStartY : chatSettingsResizeStartY;
        int startW = tabPanel ? tabSettingsResizeStartW : chatSettingsResizeStartW;
        int startH = tabPanel ? tabSettingsResizeStartH : chatSettingsResizeStartH;
        int startMouseX = tabPanel ? tabSettingsResizeStartMouseX : chatSettingsResizeStartMouseX;
        int startMouseY = tabPanel ? tabSettingsResizeStartMouseY : chatSettingsResizeStartMouseY;
        int mask = tabPanel ? tabSettingsResizeMask : chatSettingsResizeMask;

        int dx = mx - startMouseX;
        int dy = my - startMouseY;
        int nx = startX;
        int ny = startY;
        int nw = startW;
        int nh = startH;

        if ((mask & 1) != 0) { nx = startX + dx; nw = startW - dx; }
        if ((mask & 2) != 0) { nw = startW + dx; }
        if ((mask & 4) != 0) { ny = startY + dy; nh = startH - dy; }
        if ((mask & 8) != 0) { nh = startH + dy; }

        if (tabPanel) {
            config.settingsX = nx;
            config.settingsY = ny;
            config.settingsW = Math.max(260, nw);
            config.settingsH = Math.max(170, nh);
            clampTabSettingsToScreen();
        } else {
            config.chatSettingsX = nx;
            config.chatSettingsY = ny;
            config.chatSettingsW = Math.max(280, nw);
            config.chatSettingsH = Math.max(180, nh);
            clampChatSettingsToScreen();
        }
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (selectingChatInput) { selectingChatInput = false; return true; }
        boolean handled = draggingWindow || resizing || draggingTabIndex >= 0 || draggingTabSettings || resizingTabSettings || draggingChatSettings || resizingChatSettings || draggingMessageScrollbar;
        PreferredGeometry visible = PreferredGeometry.capture();
        if (preferredGeometry == null) preferredGeometry = visible;
        preferredGeometry = new PreferredGeometry(
                draggingWindow || resizing ? visible.main() : preferredGeometry.main(),
                draggingTabSettings || resizingTabSettings ? visible.tab() : preferredGeometry.tab(),
                draggingChatSettings || resizingChatSettings ? visible.chat() : preferredGeometry.chat());
        draggingMessageScrollbar = false;
        draggingWindow = false;
        resizing = false;
        resizeMask = 0;
        draggingTabIndex = -1;
        draggingTabSettings = false;
        resizingTabSettings = false;
        tabSettingsResizeMask = 0;
        draggingChatSettings = false;
        resizingChatSettings = false;
        chatSettingsResizeMask = 0;

        if (handled) {
            saveConfig();
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (tabSettingsOpen && isMouseIn(mx, my, config.settingsX, config.settingsY, config.settingsW, config.settingsH)) {
            categoryScroll = Math.max(0, Math.min(Math.max(0, knownCategories().size() - ChatLayout.categoryRows(config.settingsH)), categoryScroll - (int) Math.signum(amount)));
            return true;
        }
        if (chatSettingsOpen && isMouseIn(mx, my, config.chatSettingsX, config.chatSettingsY, config.chatSettingsW, config.chatSettingsH)) return true;
        if (isMouseIn(mx, my, config.x, config.y, config.width, 24)) { cycleTab(amount > 0 ? -1 : 1); return true; }
        if (isInsideWhole(mx, my)) {
            messageScrollLines = Math.max(0, Math.min(maxScrollLines, messageScrollLines + (int) (amount * 3)));
            return true;
        }
        return (tabSettingsOpen && isMouseIn(mx, my, config.settingsX, config.settingsY, config.settingsW, config.settingsH))
                || (chatSettingsOpen && isMouseIn(mx, my, config.chatSettingsX, config.chatSettingsY, config.chatSettingsW, config.chatSettingsH));
    }

    public static boolean charTyped(char chr, int modifiers) {
        if (chr < 32 || chr == 127) return true;

        switch (focus) {
            case CHAT_INPUT -> insertChat(String.valueOf(chr));
            case SEARCH_INPUT -> {
                insertSearch(String.valueOf(chr));
                searchHit = -1;
                jumpSearch(1);
            }
            case SETTINGS_NAME -> insertSettingsName(String.valueOf(chr));
            default -> {
            }
        }
        return true;
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (colorPaletteOpen) { colorPaletteOpen = false; return true; }
            if (copyPopupOpen) { copyPopupOpen = false; return true; }
            if (tabSettingsOpen) {
                tabSettingsOpen = false;
                focus = Focus.CHAT_INPUT;
                return true;
            }
            if (chatSettingsOpen) {
                chatSettingsOpen = false;
                focus = Focus.CHAT_INPUT;
                saveConfig();
                return true;
            }
            if (config.searchOpen) {
                config.searchOpen = false;
                searchInput = "";
                searchCursor = 0;
                setSearchSelection(0, 0);
                searchHit = -1;
                focus = Focus.CHAT_INPUT;
                saveConfig();
                return true;
            }
            client.setScreen(null);
            return true;
        }

        if (ctrl && focus == Focus.CHAT_INPUT && (keyCode == GLFW.GLFW_KEY_B || keyCode == GLFW.GLFW_KEY_I || keyCode == GLFW.GLFW_KEY_U)) {
            applyFormatting(keyCode == GLFW.GLFW_KEY_B ? "&l" : keyCode == GLFW.GLFW_KEY_I ? "&o" : "&n");
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z && focus == Focus.CHAT_INPUT) {
            undoChatInput();
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAllFocused();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB && focus == Focus.CHAT_INPUT) {
            completeCommand();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (focus == Focus.CHAT_INPUT) {
                sendChatInput();
                return true;
            }
            if (focus == Focus.SEARCH_INPUT) {
                jumpSearch(1);
                return true;
            }
            if (focus == Focus.SETTINGS_NAME) {
                focus = Focus.CHAT_INPUT;
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_UP && focus == Focus.CHAT_INPUT) {
            history(-1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN && focus == Focus.CHAT_INPUT) {
            history(1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            backspaceFocused();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            deleteFocused();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            moveCursorFocused(-1, shift);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            moveCursorFocused(1, shift);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_HOME) {
            setCursorFocused(0, shift);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_END) {
            setCursorFocused(999999, shift);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V && ctrl) {
            try {
                String clip = client.keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    charTypedString(clip);
                }
            } catch (Exception ignored) {
            }
            return true;
        }

        return true;
    }

    private static void sendChatInput() {
        MinecraftClient client = MinecraftClient.getInstance();
        String msg = chatInput.strip();
        if (msg.isEmpty()) return;

        if (client.player == null || client.player.networkHandler == null) return;
        if (msg.length() > getMaxChatLength()) {
            addMessage(Text.literal("Сообщение превышает лимит " + getMaxChatLength()).formatted(net.minecraft.util.Formatting.RED));
            return;
        }
        if (supportsExtendedChat() && !msg.startsWith("/")) {
            var payload = PacketByteBufs.create();
            payload.writeString(msg, 4096);
            ClientPlayNetworking.send(SUBMIT_TEXT, payload);
        } else if (msg.startsWith("/")) {
            if (msg.length() > 256) {
                if (supportsExtendedChat() && msg.matches("(?is)^/(?:m|me|r|roll|rename) .+")) {
                    var payload = PacketByteBufs.create(); payload.writeString(msg, 4096);
                    ClientPlayNetworking.send(SUBMIT_TEXT, payload);
                } else {
                    addMessage(Text.literal("Эта команда ограничена 256 символами.").formatted(net.minecraft.util.Formatting.RED));
                    return;
                }
            } else client.player.networkHandler.sendChatCommand(msg.substring(1));
        } else client.player.networkHandler.sendChatMessage(msg);

        if (sentHistory.isEmpty() || !sentHistory.get(sentHistory.size() - 1).equals(msg)) {
            sentHistory.add(msg);
            while (sentHistory.size() > 100) sentHistory.remove(0);
        }
        historyIndex = -1;

        chatInput = "";
        chatCursor = 0;
        setChatSelection(0, 0);
        resetTabCompletion();
        focus = Focus.CHAT_INPUT;
        if (config.closeAfterSend) client.setScreen(null);
    }

    private static void completeCommand() {
        CompletionContext context = currentCompletionContext();
        if (context == null || context.prefix == null) return;

        boolean repeat = !tabCompletions.isEmpty()
                && tabCompletionKind == context.kind
                && tabCompletionStart == context.start
                && tabCompletionLastEnd == context.end;

        if (!repeat) {
            tabCompletions.clear();
            tabCompletionPrefix = context.prefix.toLowerCase(Locale.ROOT);
            tabCompletionIndex = -1;
            tabCompletionKind = context.kind;
            tabCompletionStart = context.start;

            List<String> source = context.kind == CompletionKind.COMMAND ? availableCommands() : onlinePlayerNames();
            for (String candidate : source) {
                if (candidate.toLowerCase(Locale.ROOT).startsWith(tabCompletionPrefix)) {
                    tabCompletions.add(candidate);
                }
            }

            tabCompletions.sort(String.CASE_INSENSITIVE_ORDER);
        }

        if (tabCompletions.isEmpty()) return;

        pushChatUndo();

        tabCompletionIndex = Math.floorMod(tabCompletionIndex + 1, tabCompletions.size());
        String completed = tabCompletions.get(tabCompletionIndex);

        chatInput = chatInput.substring(0, context.start) + completed + chatInput.substring(context.end);
        chatCursor = context.start + completed.length();
        tabCompletionLastEnd = chatCursor;
        setChatSelection(chatCursor, chatCursor);
    }

    private static CompletionContext currentCompletionContext() {
        if (chatInput == null) return null;

        int cursor = Math.max(0, Math.min(chatCursor, chatInput.length()));
        int start = cursor;
        while (start > 0 && !Character.isWhitespace(chatInput.charAt(start - 1))) {
            start--;
        }

        int end = cursor;
        while (end < chatInput.length() && !Character.isWhitespace(chatInput.charAt(end))) {
            end++;
        }

        if (chatInput.startsWith("/") && start == 0) {
            int commandStart = 1;
            int commandEnd = Math.max(commandStart, end);
            String prefix = chatInput.substring(commandStart, Math.max(commandStart, Math.min(cursor, commandEnd)));
            return new CompletionContext(CompletionKind.COMMAND, commandStart, commandEnd, prefix);
        }

        String prefix = chatInput.substring(start, Math.max(start, Math.min(cursor, end)));
        return new CompletionContext(CompletionKind.PLAYER, start, end, prefix);
    }

    private static List<String> availableCommands() {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
                for (CommandNode<?> node : client.getNetworkHandler().getCommandDispatcher().getRoot().getChildren()) {
                    String name = node.getName();
                    if (name != null && !name.isBlank()) {
                        out.add(name);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback на случай, если клиент ещё не получил command tree от сервера.
        Collections.addAll(out, "help", "me", "msg", "m", "r", "tell", "w", "list", "listen", "rpchat", "rpchatui", "profile", "gamemode", "tp", "time", "weather", "spawnpoint", "give");

        return new ArrayList<>(out);
    }

    private static List<String> onlinePlayerNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        MinecraftClient client = MinecraftClient.getInstance();

        try {
            if (client != null && client.getNetworkHandler() != null) {
                for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                    if (entry != null && entry.getProfile() != null) {
                        String name = entry.getProfile().getName();
                        if (name != null && !name.isBlank()) {
                            out.add(name);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (client != null && client.player != null) {
                out.add(client.player.getGameProfile().getName());
            }
        } catch (Exception ignored) {
        }

        return new ArrayList<>(out);
    }

    private static void history(int direction) {
        if (sentHistory.isEmpty()) return;

        pushChatUndo();

        if (historyIndex < 0) historyIndex = sentHistory.size();
        historyIndex += direction;
        historyIndex = Math.max(0, Math.min(sentHistory.size(), historyIndex));

        if (historyIndex >= sentHistory.size()) {
            chatInput = "";
        } else {
            chatInput = sentHistory.get(historyIndex);
        }
        chatCursor = chatInput.length();
        setChatSelection(chatCursor, chatCursor);
    }

    private static void charTypedString(String text) {
        String clean = text.replace("\r", "").replace("\n", " ");
        switch (focus) {
            case CHAT_INPUT -> insertChat(clean);
            case SEARCH_INPUT -> {
                insertSearch(clean);
                searchHit = -1;
                jumpSearch(1);
            }
            case SETTINGS_NAME -> insertSettingsName(clean);
            default -> {
            }
        }
    }

    private static void insertChat(String s) {
        pushChatUndo();
        resetTabCompletion();
        replaceChatSelection(s);
        if (chatInput.length() > getMaxChatLength()) {
            chatInput = chatInput.substring(0, getMaxChatLength());
            chatCursor = Math.min(chatCursor, chatInput.length());
        }
        setChatSelection(chatCursor, chatCursor);
        historyIndex = -1;
    }

    private static void insertSearch(String s) {
        replaceSearchSelection(s);
        setSearchSelection(searchCursor, searchCursor);
    }

    private static void insertSettingsName(String s) {
        replaceSettingsNameSelection(s);
        setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
    }

    private static void replaceChatSelection(String replacement) {
        int a = Math.min(chatSelectionAnchor, chatSelectionCursor);
        int b = Math.max(chatSelectionAnchor, chatSelectionCursor);
        chatInput = chatInput.substring(0, a) + replacement + chatInput.substring(b);
        chatCursor = a + replacement.length();
    }

    private static void replaceSearchSelection(String replacement) {
        int a = Math.min(searchSelectionAnchor, searchSelectionCursor);
        int b = Math.max(searchSelectionAnchor, searchSelectionCursor);
        searchInput = searchInput.substring(0, a) + replacement + searchInput.substring(b);
        searchCursor = a + replacement.length();
    }

    private static void replaceSettingsNameSelection(String replacement) {
        int a = Math.min(settingsNameSelectionAnchor, settingsNameSelectionCursor);
        int b = Math.max(settingsNameSelectionAnchor, settingsNameSelectionCursor);
        settingsName = settingsName.substring(0, a) + replacement + settingsName.substring(b);
        settingsNameCursor = a + replacement.length();
    }

    private static void backspaceFocused() {
        switch (focus) {
            case CHAT_INPUT -> {
                if (hasChatSelection() || chatCursor > 0) {
                    pushChatUndo();
                }
                if (hasChatSelection()) {
                    replaceChatSelection("");
                } else if (chatCursor > 0) {
                    chatInput = chatInput.substring(0, chatCursor - 1) + chatInput.substring(chatCursor);
                    chatCursor--;
                }
                setChatSelection(chatCursor, chatCursor);
            }
            case SEARCH_INPUT -> {
                if (hasSearchSelection()) {
                    replaceSearchSelection("");
                } else if (searchCursor > 0) {
                    searchInput = searchInput.substring(0, searchCursor - 1) + searchInput.substring(searchCursor);
                    searchCursor--;
                }
                setSearchSelection(searchCursor, searchCursor);
                searchHit = -1;
                jumpSearch(1);
            }
            case SETTINGS_NAME -> {
                if (hasSettingsNameSelection()) {
                    replaceSettingsNameSelection("");
                } else if (settingsNameCursor > 0) {
                    settingsName = settingsName.substring(0, settingsNameCursor - 1) + settingsName.substring(settingsNameCursor);
                    settingsNameCursor--;
                }
                setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
            }
            default -> {
            }
        }
    }

    private static void deleteFocused() {
        switch (focus) {
            case CHAT_INPUT -> {
                if (hasChatSelection() || chatCursor < chatInput.length()) {
                    pushChatUndo();
                }
                if (hasChatSelection()) {
                    replaceChatSelection("");
                } else if (chatCursor < chatInput.length()) {
                    chatInput = chatInput.substring(0, chatCursor) + chatInput.substring(chatCursor + 1);
                }
                setChatSelection(chatCursor, chatCursor);
            }
            case SEARCH_INPUT -> {
                if (hasSearchSelection()) {
                    replaceSearchSelection("");
                } else if (searchCursor < searchInput.length()) {
                    searchInput = searchInput.substring(0, searchCursor) + searchInput.substring(searchCursor + 1);
                }
                setSearchSelection(searchCursor, searchCursor);
                searchHit = -1;
                jumpSearch(1);
            }
            case SETTINGS_NAME -> {
                if (hasSettingsNameSelection()) {
                    replaceSettingsNameSelection("");
                } else if (settingsNameCursor < settingsName.length()) {
                    settingsName = settingsName.substring(0, settingsNameCursor) + settingsName.substring(settingsNameCursor + 1);
                }
                setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
            }
            default -> {
            }
        }
    }

    private static void moveCursorFocused(int delta, boolean shift) {
        switch (focus) {
            case CHAT_INPUT -> {
                int next = Math.max(0, Math.min(chatInput.length(), chatCursor + delta));
                chatCursor = next;
                if (shift) setChatSelection(chatSelectionAnchor, chatCursor);
                else setChatSelection(chatCursor, chatCursor);
            }
            case SEARCH_INPUT -> {
                int next = Math.max(0, Math.min(searchInput.length(), searchCursor + delta));
                searchCursor = next;
                if (shift) setSearchSelection(searchSelectionAnchor, searchCursor);
                else setSearchSelection(searchCursor, searchCursor);
            }
            case SETTINGS_NAME -> {
                int next = Math.max(0, Math.min(settingsName.length(), settingsNameCursor + delta));
                settingsNameCursor = next;
                if (shift) setSettingsNameSelection(settingsNameSelectionAnchor, settingsNameCursor);
                else setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
            }
            default -> {
            }
        }
    }

    private static void setCursorFocused(int pos, boolean shift) {
        switch (focus) {
            case CHAT_INPUT -> {
                chatCursor = Math.max(0, Math.min(chatInput.length(), pos));
                if (shift) setChatSelection(chatSelectionAnchor, chatCursor);
                else setChatSelection(chatCursor, chatCursor);
            }
            case SEARCH_INPUT -> {
                searchCursor = Math.max(0, Math.min(searchInput.length(), pos));
                if (shift) setSearchSelection(searchSelectionAnchor, searchCursor);
                else setSearchSelection(searchCursor, searchCursor);
            }
            case SETTINGS_NAME -> {
                settingsNameCursor = Math.max(0, Math.min(settingsName.length(), pos));
                if (shift) setSettingsNameSelection(settingsNameSelectionAnchor, settingsNameCursor);
                else setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
            }
            default -> {
            }
        }
    }

    private static void selectAllFocused() {
        switch (focus) {
            case CHAT_INPUT -> {
                chatCursor = chatInput.length();
                setChatSelection(0, chatInput.length());
            }
            case SEARCH_INPUT -> {
                searchCursor = searchInput.length();
                setSearchSelection(0, searchInput.length());
            }
            case SETTINGS_NAME -> {
                settingsNameCursor = settingsName.length();
                setSettingsNameSelection(0, settingsName.length());
            }
            default -> {
            }
        }
    }

    private static void resetTabCompletion() {
        tabCompletions.clear();
        tabCompletionPrefix = "";
        tabCompletionIndex = -1;
        tabCompletionKind = CompletionKind.NONE;
        tabCompletionStart = -1;
        tabCompletionLastEnd = -1;
    }

    private static void pushChatUndo() {
        InputSnapshot snapshot = new InputSnapshot(chatInput, chatCursor, chatSelectionAnchor, chatSelectionCursor);
        if (!chatUndoStack.isEmpty()) {
            InputSnapshot last = chatUndoStack.get(chatUndoStack.size() - 1);
            if (last.equals(snapshot)) return;
        }

        chatUndoStack.add(snapshot);
        while (chatUndoStack.size() > 100) {
            chatUndoStack.remove(0);
        }
    }

    private static void undoChatInput() {
        if (chatUndoStack.isEmpty()) return;

        InputSnapshot snapshot = chatUndoStack.remove(chatUndoStack.size() - 1);
        chatInput = snapshot.text();
        chatCursor = Math.max(0, Math.min(chatInput.length(), snapshot.cursor()));
        chatSelectionAnchor = Math.max(0, Math.min(chatInput.length(), snapshot.selectionAnchor()));
        chatSelectionCursor = Math.max(0, Math.min(chatInput.length(), snapshot.selectionCursor()));

        resetTabCompletion();
        historyIndex = -1;
    }

    private static boolean hasChatSelection() {
        return chatSelectionAnchor != chatSelectionCursor;
    }

    private static boolean hasSearchSelection() {
        return searchSelectionAnchor != searchSelectionCursor;
    }

    private static boolean hasSettingsNameSelection() {
        return settingsNameSelectionAnchor != settingsNameSelectionCursor;
    }

    private static void setChatSelection(int anchor, int cursor) {
        chatSelectionAnchor = Math.max(0, Math.min(chatInput.length(), anchor));
        chatSelectionCursor = Math.max(0, Math.min(chatInput.length(), cursor));
    }

    private static void setSearchSelection(int anchor, int cursor) {
        searchSelectionAnchor = Math.max(0, Math.min(searchInput.length(), anchor));
        searchSelectionCursor = Math.max(0, Math.min(searchInput.length(), cursor));
    }

    private static void setSettingsNameSelection(int anchor, int cursor) {
        settingsNameSelectionAnchor = Math.max(0, Math.min(settingsName.length(), anchor));
        settingsNameSelectionCursor = Math.max(0, Math.min(settingsName.length(), cursor));
    }

    private static boolean handleCopyPopupClick(int mx, int my) {
        if (!copyPopupOpen) return false;

        int w = 132;
        int h = 46;

        if (isMouseIn(mx, my, copyPopupX + 4, copyPopupY + 4, w - 8, 18)) {
            MinecraftClient.getInstance().keyboard.setClipboard(copyPopupBody == null ? "" : copyPopupBody);
            copyPopupOpen = false;
            return true;
        }

        if (isMouseIn(mx, my, copyPopupX + 4, copyPopupY + 24, w - 8, 18)) {
            toggleFavorite(copyPopupText, -1);
            copyPopupOpen = false;
            return true;
        }

        if (isMouseIn(mx, my, copyPopupX, copyPopupY, w, h)) {
            return true;
        }

        copyPopupOpen = false;
        return false;
    }

    private static boolean clickMessage(int mx, int my) {
        for (MessageHitBox box : messageHitBoxes) {
            if (mx >= box.x0 && mx <= box.x1 && my >= box.y0 && my <= box.y1) {
                copyPopupOpen = true;
                copyPopupX = mx + 8;
                copyPopupY = box.y0 - 50;
                copyPopupText = box.text;
                copyPopupBody = box.body;
                clampCopyPopupToScreen(132, 46);
                return true;
            }
        }
        return false;
    }

    private static void toggleFavorite(String text, int favoriteIndex) {
        if (text == null || text.isBlank()) return;

        if (favoriteIndex >= 0 && favoriteIndex < config.favoriteMessages.size()) {
            config.favoriteMessages.remove(favoriteIndex);
            saveConfig();
            return;
        }

        for (int i = 0; i < config.favoriteMessages.size(); i++) {
            if (Objects.equals(config.favoriteMessages.get(i).text, text)) {
                config.favoriteMessages.remove(i);
                saveConfig();
                return;
            }
        }

        FavoriteMessage favorite = new FavoriteMessage();
        favorite.text = text;
        favorite.body = Objects.equals(text, copyPopupText) ? copyPopupBody : text;
        favorite.timeMs = System.currentTimeMillis();
        config.favoriteMessages.add(favorite);
        openFavoritesTab();
        saveConfig();
    }

    private static boolean isFavorited(String text) {
        for (FavoriteMessage favorite : config.favoriteMessages) {
            if (Objects.equals(favorite.text, text)) return true;
        }
        return false;
    }

    private static boolean handleSideClick(int mx, int my) {
        int x = config.x + config.width + 5;
        int y = config.y + headerHeight() + 6;

        if (isMouseIn(mx, my, x, y, 24, 20)) {
            config.textScale = Math.min(2.5F, config.textScale + 0.1F);
            saveConfig();
            return true;
        }
        if (isMouseIn(mx, my, x, y + 24, 24, 20)) {
            config.textScale = Math.max(0.5F, config.textScale - 0.1F);
            saveConfig();
            return true;
        }
        if (isMouseIn(mx, my, x, y + 52, 24, 20)) {
            config.autoCreateTabs = !config.autoCreateTabs;
            saveConfig();
            return true;
        }

        return false;
    }

    private static int hitResize(int mx, int my) {
        if (!isMouseIn(mx, my, config.x, config.y, config.width, config.height)) return 0;
        int mask = 0;
        if (mx <= config.x + RESIZE_EDGE) mask |= 1;
        if (mx >= config.x + config.width - RESIZE_EDGE) mask |= 2;
        if (my <= config.y + RESIZE_EDGE) mask |= 4;
        if (my >= config.y + config.height - RESIZE_EDGE) mask |= 8;
        return mask;
    }

    private static int hitPanelResize(int mx, int my, int x, int y, int w, int h) {
        if (!isMouseIn(mx, my, x, y, w, h)) return 0;
        int mask = 0;
        if (mx <= x + RESIZE_EDGE) mask |= 1;
        if (mx >= x + w - RESIZE_EDGE) mask |= 2;
        if (my <= y + RESIZE_EDGE) mask |= 4;
        if (my >= y + h - RESIZE_EDGE) mask |= 8;
        return mask;
    }

    private static TabHit hitTab(int mx, int my) {
        ensureSelectedTabVisible();
        int x = config.x + 63;
        int y = config.y + 4;
        for (int i = firstVisibleTab; i < config.tabs.size(); i++) {
            ChatTab tab = config.tabs.get(i);
            int tw = ChatLayout.tabWidth(config.width, tabWidth(tab));
            if (x + tw > config.x + ChatLayout.tabsEnd(config.width)) break;

            if (isMouseIn(mx, my, x, y, tw, 17)) {
                boolean close = tab.closable && mx >= x + tw - 18;
                return new TabHit(i, close);
            }

            x += tw + 4;
        }
        return null;
    }

    private static int headerHeight() {
        return ChatLayout.header(config.width, config.searchOpen && isChatUiOpen());
    }

    private static void cycleTab(int delta) {
        config.selectedTab = Math.floorMod(config.selectedTab + delta, config.tabs.size());
        messageScrollLines = 0;
        ensureSelectedTabVisible();
        saveConfig();
    }

    private static void ensureSelectedTabVisible() {
        int[] widths = config.tabs.stream().mapToInt(ChatUiState::tabWidth).toArray();
        firstVisibleTab = ChatLayout.firstTab(firstVisibleTab, config.selectedTab, config.width, widths);
    }

    private static void drawSingleLineText(DrawContext context, TextRenderer tr, int width, String text,
                                           int cursor, int anchor, int selectionCursor, int x, int y, boolean focused, int color) {
        int caret = tr.getWidth(text.substring(0, Math.min(cursor, text.length())));
        int offset = Math.max(0, caret - width + 2);
        context.enableScissor(x, y - 2, x + width, y + 11);
        drawSelectableText(context, tr, text, cursor, anchor, selectionCursor, x - offset, y, focused, color);
        context.disableScissor();
    }

    private static int tabWidth(ChatTab tab) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        return Math.max(58, Math.min(142, tr.getWidth(tab.name) + (tab.closable ? 30 : 18)));
    }

    private static void createCustomTab() {
        int n = 1;
        while (findTabByName("Вкладка " + n) != null) n++;

        ChatTab tab = new ChatTab();
        tab.id = "custom_" + UUID.randomUUID();
        tab.name = "Вкладка " + n;
        tab.closable = true;
        tab.custom = true;
        tab.categories = new LinkedHashSet<>();
        tab.categories.add(CAT_GENERAL);

        config.tabs.add(tab);
        config.selectedTab = config.tabs.size() - 1;
        saveConfig();
        openSelectedTabSettings();
    }

    private static void openSelectedTabSettings() {
        ChatTab tab = selectedTab();
        if (tab == null) return;

        tabSettingsOpen = true;
        settingsTabId = tab.id;
        settingsName = tab.name;
        settingsNameCursor = settingsName.length();
        setSettingsNameSelection(settingsNameCursor, settingsNameCursor);
        settingsCategories = new LinkedHashSet<>(tab.categories);
        categoryScroll = 0;
        chatSettingsOpen = false;
        focus = Focus.SETTINGS_NAME;

        if (preferredGeometry != null && preferredGeometry.tab().x() <= 0 && preferredGeometry.tab().y() <= 0) {
            ChatLayout.Rect r = preferredGeometry.tab();
            preferredGeometry = new PreferredGeometry(preferredGeometry.main(),
                    new ChatLayout.Rect(config.x + config.width + SIDE_W + 8, config.y, r.width(), r.height()), preferredGeometry.chat());
            applyTab(preferredGeometry.tab());
        }
        clampTabSettingsToScreen();
    }

    private static void openChatSettings() {
        chatSettingsOpen = true;
        tabSettingsOpen = false;
        if (preferredGeometry != null && preferredGeometry.chat().x() <= 0 && preferredGeometry.chat().y() <= 0) {
            ChatLayout.Rect r = preferredGeometry.chat();
            preferredGeometry = new PreferredGeometry(preferredGeometry.main(), preferredGeometry.tab(),
                    new ChatLayout.Rect(config.x + config.width + SIDE_W + 8, config.y + 30, r.width(), r.height()));
            applyChat(preferredGeometry.chat());
        }
        clampChatSettingsToScreen();
    }

    private static void openFavoritesTab() {
        for (int i = 0; i < config.tabs.size(); i++) {
            ChatTab tab = config.tabs.get(i);
            if (isFavoritesTab(tab)) {
                config.selectedTab = i;
                saveConfig();
                return;
            }
        }

        ChatTab tab = new ChatTab();
        tab.id = CAT_FAVORITES;
        tab.name = "Избранное";
        tab.closable = true;
        tab.custom = false;
        tab.categories = new LinkedHashSet<>();
        tab.categories.add(CAT_FAVORITES);
        config.tabs.add(tab);
        config.selectedTab = config.tabs.size() - 1;
        saveConfig();
    }

    private static boolean isFavoritesTab(ChatTab tab) {
        return tab != null && (CAT_FAVORITES.equals(tab.id) || (tab.categories != null && tab.categories.contains(CAT_FAVORITES)));
    }

    private static void closeTab(int index) {
        if (index <= 0 || index >= config.tabs.size()) return;
        config.tabs.remove(index);
        if (config.selectedTab >= config.tabs.size()) config.selectedTab = config.tabs.size() - 1;
        if (config.selectedTab < 0) config.selectedTab = 0;
        saveConfig();
    }

    private static void maybeCreateAutoTab(String category) {
        if (!config.autoCreateTabs) return;
        if (category == null || category.equals(CAT_GENERAL) || category.equals(CAT_ALL) || category.equals(CAT_FAVORITES)) return;

        for (ChatTab tab : config.tabs) {
            if (tab.categories != null && tab.categories.size() == 1 && tab.categories.contains(category)) return;
        }

        ChatTab tab = new ChatTab();
        tab.id = "auto_" + sanitizeId(category);
        tab.name = displayName(category);
        tab.closable = true;
        tab.custom = false;
        tab.categories = new LinkedHashSet<>();
        tab.categories.add(category);
        config.tabs.add(tab);
        saveConfig();
    }

    private static void rememberCategory(String category) {
        if (category == null || category.isBlank()) category = CAT_GENERAL;
        if (config.knownCategories.add(category)) {
            saveConfig();
        }
    }

    private static String detectCategory(String plain) {
        if (plain == null) return CAT_GENERAL;
        String s = plain.strip();

        if (s.startsWith("[GM]") || s.contains("[GM]")) return CAT_GM;
        if (s.startsWith("[ЛС") || s.contains("[ЛС ") || s.startsWith("[PM") || s.contains("[PM]") || s.contains(" whispers: ")) return CAT_PM;
        if (s.contains("((") && s.contains("))")) return CAT_OOC;

        Matcher m = BRACKET_PREFIX.matcher(s);
        if (m.find()) {
            String raw = m.group(1).strip();
            if (!raw.equalsIgnoreCase("GM") && !raw.equalsIgnoreCase("PM") && !raw.equalsIgnoreCase("ЛС")) {
                return "tag:" + raw.toLowerCase(Locale.ROOT).replace(' ', '_');
            }
        }

        return CAT_GENERAL;
    }

    public static List<String> knownCategories() {
        config.normalize();
        return new ArrayList<>(config.knownCategories);
    }

    public static String displayName(String category) {
        if (category == null) return "Общий";
        return switch (category) {
            case CAT_ALL -> "Все";
            case CAT_GENERAL -> "Общий";
            case CAT_OOC -> "OOC";
            case CAT_GM -> "GM";
            case CAT_PM -> "ЛС";
            case CAT_FAVORITES -> "Избранное";
            default -> category.startsWith("tag:") ? category.substring(4) : category;
        };
    }

    static ChatTab getTabById(String id) {
        for (ChatTab tab : config.tabs) {
            if (Objects.equals(tab.id, id)) return tab;
        }
        return null;
    }

    static void saveTab(ChatTab tab, String newName, Set<String> categories) {
        if (tab == null) return;
        tab.name = newName == null || newName.isBlank() ? tab.name : newName.strip();
        if (tab.closable) {
            tab.categories = new LinkedHashSet<>(categories);
            if (tab.categories.isEmpty()) tab.categories.add(CAT_GENERAL);
        }
        saveConfig();
    }

    private static ChatTab findTabByName(String name) {
        for (ChatTab tab : config.tabs) {
            if (Objects.equals(tab.name, name)) return tab;
        }
        return null;
    }

    private static ChatTab selectedTab() {
        config.normalize();
        if (config.tabs.isEmpty()) return null;
        config.selectedTab = Math.max(0, Math.min(config.selectedTab, config.tabs.size() - 1));
        return config.tabs.get(config.selectedTab);
    }

    private static boolean tabAccepts(ChatTab tab, String category) {
        if (tab == null) return true;
        if (isFavoritesTab(tab)) return CAT_FAVORITES.equals(category);
        if (tab.categories == null || tab.categories.contains(CAT_ALL)) return true;
        return tab.categories.contains(category);
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case CAT_OOC -> 0xFFFF80FF;
            case CAT_GM -> 0xFFFFD35C;
            case CAT_PM -> 0xFF80E8FF;
            default -> 0xFFFFFFFF;
        };
    }

    private static void cycleTint(int delta) {
        String[] tints = {"blue", "purple", "green", "red", "gray", "oak", "walnut", "elven"};
        int idx = 0;
        for (int i = 0; i < tints.length; i++) {
            if (Objects.equals(config.tint, tints[i])) idx = i;
        }
        idx = Math.floorMod(idx + delta, tints.length);
        config.tint = tints[idx];
    }

    private static String tintName(String tint) {
        return switch (tint) {
            case "purple" -> "фиолетовый";
            case "green" -> "зелёный";
            case "red" -> "красный";
            case "gray" -> "серый";
            case "oak" -> "Дуб и латунь";
            case "walnut" -> "Тёмный орех";
            case "elven" -> "Эльфийское дерево";
            default -> "синий";
        };
    }

    private static void clampToScreen(int screenW, int screenH) {
        ChatLayout.Rect r = ChatLayout.fit(config.x, config.y, config.width, config.height, MIN_W, MIN_H,
                screenW, screenH, SIDE_W);
        config.x = r.x(); config.y = r.y(); config.width = r.width(); config.height = r.height();
    }

    private static void clampTabSettingsToScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        ChatLayout.Rect r = ChatLayout.fit(config.settingsX, config.settingsY, config.settingsW, config.settingsH,
                260, 200, sw, sh, 0);
        config.settingsX = r.x(); config.settingsY = r.y(); config.settingsW = r.width(); config.settingsH = r.height();
    }

    private static void clampChatSettingsToScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        ChatLayout.Rect r = ChatLayout.fit(config.chatSettingsX, config.chatSettingsY, config.chatSettingsW, config.chatSettingsH,
                280, 200, sw, sh, 0);
        config.chatSettingsX = r.x(); config.chatSettingsY = r.y(); config.chatSettingsW = r.width(); config.chatSettingsH = r.height();
    }

    private static boolean isInsideWhole(int mx, int my) {
        return isMouseIn(mx, my, config.x, config.y, config.width + SIDE_W, config.height);
    }

    private static boolean isMouseIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String fit(String text, int maxPx) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr.getWidth(text) <= maxPx) return text;
        String s = text;
        while (!s.isEmpty() && tr.getWidth(s + "…") > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static boolean blink() {
        return (System.currentTimeMillis() / 450L) % 2L == 0L;
    }

    private static String sanitizeId(String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9_:-]", "_");
    }

    private static int withAlpha(int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static int lighten(int rgb, int amount) {
        int r = Math.min(255, ((rgb >> 16) & 255) + amount);
        int g = Math.min(255, ((rgb >> 8) & 255) + amount);
        int b = Math.min(255, (rgb & 255) + amount);
        return (r << 16) | (g << 8) | b;
    }

    private enum Focus {
        NONE,
        CHAT_INPUT,
        SEARCH_INPUT,
        SETTINGS_NAME
    }

    private enum CompletionKind {
        NONE,
        COMMAND,
        PLAYER
    }

    private record CompletionContext(CompletionKind kind, int start, int end, String prefix) {
    }

    private record InputSnapshot(String text, int cursor, int selectionAnchor, int selectionCursor) {
    }

    private record ChatEntry(Text text, String plain, String category, long timeMs, Map<Integer, List<OrderedText>> wrapped) {
    }

    private record DisplayLine(OrderedText text, String plain, String category, int alpha, int entryIndex, int favoriteIndex, String fullText, String copyText) {
    }

    private record LinkHitBox(int x0, int y0, int x1, int y1, String url) {
    }

    private record MessageHitBox(int x0, int y0, int x1, int y1, String text, String body) {
    }


    private record TabHit(int index, boolean close) {
    }
    public static class FavoriteMessage {
        public String text = "";
        public String body = null;
        public long timeMs = 0L;
    }

    public static class ChatUiConfig {
        public int x = 12;
        public int y = 160;
        public int width = 520;
        public int height = 220;
        public int settingsX = 0;
        public int settingsY = 0;
        public int settingsW = 360;
        public int settingsH = 260;
        public int chatSettingsX = 0;
        public int chatSettingsY = 0;
        public int chatSettingsW = 360;
        public int chatSettingsH = 220;
        public float textScale = 1.0F;
        public boolean autoCreateTabs = true;
        public boolean searchOpen = false;
        public boolean alwaysShowWindow = false;
        public boolean closeAfterSend = false;
        public String tint = "blue";
        public int opacity = 181;
        public int selectedTab = 0;
        public List<ChatTab> tabs = new ArrayList<>();
        public LinkedHashSet<String> knownCategories = new LinkedHashSet<>();
        public List<FavoriteMessage> favoriteMessages = new ArrayList<>();

        static ChatUiConfig defaults() {
            ChatUiConfig cfg = new ChatUiConfig();
            cfg.normalize();
            return cfg;
        }

        int tintColor() {
            return switch (tint) {
                case "purple" -> 0x2B2038;
                case "green" -> 0x1F3028;
                case "red" -> 0x382020;
                case "gray" -> 0x252832;
                case "oak" -> 0x493018;
                case "walnut" -> 0x291A16;
                case "elven" -> 0x24342B;
                default -> 0x1B2030;
            };
        }

        void normalize() {
            if (width <= 0) width = 520;
            if (height <= 0) height = 220;
            if (settingsW <= 0) settingsW = 360;
            if (settingsH <= 0) settingsH = 260;
            if (chatSettingsW <= 0) chatSettingsW = 360;
            if (chatSettingsH <= 0) chatSettingsH = 220;
            width = Math.max(MIN_W, width);
            height = Math.max(MIN_H, height);
            settingsW = Math.max(260, settingsW);
            settingsH = Math.max(170, settingsH);
            chatSettingsW = Math.max(280, chatSettingsW);
            chatSettingsH = Math.max(180, chatSettingsH);
            textScale = Math.max(0.5F, Math.min(2.5F, textScale));
            opacity = Math.max(40, Math.min(245, opacity));
            if (tint == null || tint.isBlank()) tint = "blue";

            if (tabs == null) tabs = new ArrayList<>();
            if (knownCategories == null) knownCategories = new LinkedHashSet<>();
            if (favoriteMessages == null) favoriteMessages = new ArrayList<>();
            favoriteMessages.removeIf(fav -> fav == null || fav.text == null || fav.text.isBlank());

            knownCategories.add(CAT_GENERAL);
            knownCategories.add(CAT_OOC);
            knownCategories.add(CAT_GM);
            knownCategories.add(CAT_PM);
            knownCategories.add(CAT_FAVORITES);

            if (tabs.isEmpty() || tabs.get(0) == null || !CAT_ALL.equals(tabs.get(0).id)) {
                ChatTab all = new ChatTab();
                all.id = CAT_ALL;
                all.name = "local";
                all.closable = false;
                all.custom = false;
                all.categories = new LinkedHashSet<>();
                all.categories.add(CAT_ALL);

                List<ChatTab> old = new ArrayList<>(tabs);
                tabs.clear();
                tabs.add(all);
                for (ChatTab tab : old) {
                    if (tab != null && !CAT_ALL.equals(tab.id)) tabs.add(tab);
                }
            }

            for (ChatTab tab : tabs) {
                tab.normalize();
            }

            selectedTab = Math.max(0, Math.min(selectedTab, tabs.size() - 1));
        }
    }

    public static class ChatTab {
        public String id = "";
        public String name = "Вкладка";
        public boolean closable = true;
        public boolean custom = true;
        public LinkedHashSet<String> categories = new LinkedHashSet<>();

        void normalize() {
            if (id == null || id.isBlank()) id = "tab_" + UUID.randomUUID();
            if (name == null || name.isBlank()) name = "Вкладка";
            if (categories == null) categories = new LinkedHashSet<>();
            if (categories.isEmpty()) categories.add(CAT_GENERAL);
        }
    }
}
