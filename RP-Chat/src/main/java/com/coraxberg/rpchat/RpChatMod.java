package com.coraxberg.rpchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RpChatMod implements ModInitializer {
    public static final String MOD_ID = "rpchat";

    private static final int MAX_PREFIX_COUNT = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("rpchat.json");

    private static final String TAG_GM = "rpchat_gm";
    private static final String TAG_ADMIN = "rpchat_admin";

    // Compatibility with DnD Profiles / RP Suite role tags.
    private static final String PROFILE_TAG_GM = "profile_gm";
    private static final String PROFILE_TAG_ADMIN = "profile_admin";

    private static RpChatConfig config = RpChatConfig.defaults();

    private static final Map<UUID, Integer> persistentRadius = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> persistentOoc = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> persistentGm = new ConcurrentHashMap<>();
    private static final Map<UUID, ListenSetting> listenSettings = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> lastPrivateContact = new ConcurrentHashMap<>();

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> loadConfig(server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> applyNickname(handler.player));
        registerChatInterceptor();
        registerCommands();
    }

    private void registerChatInterceptor() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) -> {
            String raw = message.getContent().getString();
            handleChatMessage(sender, raw);
            return false;
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("listen")
                    .requires(source -> source.getEntity() instanceof ServerPlayerEntity player && isGameMasterOrAdmin(player))
                    .executes(ctx -> toggleListenAll(ctx.getSource()))
                    .then(argument("radius", IntegerArgumentType.integer(1, 10000))
                            .executes(ctx -> listenRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));

            dispatcher.register(literal("m")
                    .then(argument("target", EntityArgumentType.player())
                            .then(argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> privateMessage(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "target"),
                                            StringArgumentType.getString(ctx, "message"))))));

            dispatcher.register(literal("r")
                    .then(argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> replyPrivateMessage(ctx.getSource(), StringArgumentType.getString(ctx, "message")))));


            dispatcher.register(literal("rename")
                    .requires(RpChatMod::canManageNicknames)
                    .executes(ctx -> clearOwnNickname(ctx.getSource()))
                    .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> clearNickname(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "target")))
                            .then(literal("clear")
                                    .executes(ctx -> clearNickname(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "target"))))
                            .then(argument("newnick", StringArgumentType.greedyString())
                                    .executes(ctx -> renamePlayer(
                                            ctx.getSource(),
                                            EntityArgumentType.getPlayer(ctx, "target"),
                                            StringArgumentType.getString(ctx, "newnick"))))));

            dispatcher.register(literal("rpchat")
                    .executes(ctx -> showHelp(ctx.getSource()))
                    .then(literal("help")
                            .executes(ctx -> showHelp(ctx.getSource())))
                    .then(literal("reload")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(ctx -> reloadConfigCommand(ctx.getSource())))
                    .then(literal("role")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(argument("target", EntityArgumentType.player())
                                    .then(argument("role", StringArgumentType.word())
                                            .suggests((ctx, builder) -> {
                                                builder.suggest("user");
                                                builder.suggest("gm");
                                                builder.suggest("admin");
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> setRole(
                                                    ctx.getSource(),
                                                    EntityArgumentType.getPlayer(ctx, "target"),
                                                    StringArgumentType.getString(ctx, "role")))))));
        });
    }

    private static void handleChatMessage(ServerPlayerEntity sender, String rawMessage) {
        if (rawMessage == null) return;
        String raw = rawMessage.strip();
        if (raw.isEmpty()) return;

        if (handleModeSwitch(sender, raw)) return;

        if (raw.startsWith("-") && raw.length() > 1) {
            String gmText = raw.substring(1).strip();
            if (!gmText.isEmpty()) {
                sendGmChat(sender, gmText);
            }
            return;
        }

        if (persistentGm.getOrDefault(sender.getUuid(), false)) {
            sendGmChat(sender, raw);
            return;
        }

        ParsedChat parsed = parseChat(sender, raw);
        if (parsed.message().isBlank()) return;

        sendLocalChat(sender, parsed.message(), parsed.radius(), parsed.ooc(), parsed.volumeLabel());
    }

    private static boolean handleModeSwitch(ServerPlayerEntity player, String raw) {
        if (raw.matches("={1,3}")) {
            int radius = equalRadius(raw.length());
            togglePersistentRadius(player, radius);
            return true;
        }

        if (raw.matches("!{1,3}")) {
            int radius = bangRadius(raw.length());
            togglePersistentRadius(player, radius);
            return true;
        }

        if (raw.equals("_")) {
            boolean next = !persistentOoc.getOrDefault(player.getUuid(), false);
            persistentOoc.put(player.getUuid(), next);
            player.sendMessage(Text.literal(next ? "OOC-чат включён." : "OOC-чат выключен.").formatted(Formatting.LIGHT_PURPLE), false);
            logSystem(player.getServer(), player.getGameProfile().getName() + " switched OOC " + (next ? "on" : "off"));
            return true;
        }

        if (raw.equals("-")) {
            boolean next = !persistentGm.getOrDefault(player.getUuid(), false);
            persistentGm.put(player.getUuid(), next);
            player.sendMessage(Text.literal(next ? "GM-чат включён." : "GM-чат выключен.").formatted(Formatting.GOLD), false);
            logSystem(player.getServer(), player.getGameProfile().getName() + " switched GM chat " + (next ? "on" : "off"));
            return true;
        }

        return false;
    }

    private static ParsedChat parseChat(ServerPlayerEntity player, String raw) {
        int radius = persistentRadius.getOrDefault(player.getUuid(), config.defaultRadius());
        boolean ooc = persistentOoc.getOrDefault(player.getUuid(), false);

        int index = 0;
        while (index < raw.length()) {
            char c = raw.charAt(index);
            if (c == '=' || c == '!') {
                int start = index;
                while (index < raw.length() && raw.charAt(index) == c) {
                    index++;
                }
                int count = Math.min(MAX_PREFIX_COUNT, index - start);
                radius = c == '=' ? equalRadius(count) : bangRadius(count);
                continue;
            }

            if (c == '_') {
                ooc = true;
                index++;
                continue;
            }

            break;
        }

        String message = raw.substring(index).strip();
        return new ParsedChat(radius, ooc, message, volumeLabel(radius));
    }

    private static String volumeLabel(int radius) {
        if (radius == config.defaultRadius()) return "";
        if (radius == config.quietRadius()) return config.quietLabel();
        if (radius == config.whisperRadius()) return config.whisperLabel();
        if (radius == config.barelyAudibleRadius()) return config.barelyAudibleLabel();
        if (radius == config.loudRadius()) return config.loudLabel();
        if (radius == config.shoutRadius()) return config.shoutLabel();
        if (radius == config.screamRadius()) return config.screamLabel();
        return "";
    }

    private static void togglePersistentRadius(ServerPlayerEntity player, int radius) {
        UUID uuid = player.getUuid();
        int current = persistentRadius.getOrDefault(uuid, config.defaultRadius());

        if (current == radius) {
            persistentRadius.remove(uuid);
            player.sendMessage(Text.literal("Радиус чата сброшен: " + config.defaultRadius() + " блоков.").formatted(Formatting.GRAY), false);
            logSystem(player.getServer(), player.getGameProfile().getName() + " reset chat radius to " + config.defaultRadius());
        } else {
            persistentRadius.put(uuid, radius);
            player.sendMessage(Text.literal("Радиус чата: " + radius + " блоков.").formatted(Formatting.GRAY), false);
            logSystem(player.getServer(), player.getGameProfile().getName() + " switched chat radius to " + radius);
        }
    }

    private static int equalRadius(int count) {
        return switch (Math.max(1, Math.min(MAX_PREFIX_COUNT, count))) {
            case 1 -> config.quietRadius();
            case 2 -> config.whisperRadius();
            default -> config.barelyAudibleRadius();
        };
    }

    private static int bangRadius(int count) {
        return switch (Math.max(1, Math.min(MAX_PREFIX_COUNT, count))) {
            case 1 -> config.loudRadius();
            case 2 -> config.shoutRadius();
            default -> config.screamRadius();
        };
    }

    private static void sendLocalChat(ServerPlayerEntity sender, String message, int radius, boolean ooc, String volumeLabel) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;

        Text text = formatLocalMessage(sender, message, ooc, volumeLabel);
        int recipients = 0;

        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (shouldReceiveLocal(sender, target, radius)) {
                target.sendMessage(text, false);
                recipients++;
            }
        }

        String mode = ooc ? "OOC" : "IC";
        log(server, "[" + mode + "] [radius=" + radius + "] " + sender.getGameProfile().getName() + ": " + message + " | recipients=" + recipients);
    }

    private static Text formatLocalMessage(ServerPlayerEntity sender, String message, boolean ooc, String volumeLabel) {
        Formatting mainColor = ooc ? Formatting.LIGHT_PURPLE : Formatting.WHITE;
        MutableText result = displayNameText(sender);
        result.append(Text.literal(": ").formatted(mainColor));

        if (volumeLabel != null && !volumeLabel.isBlank()) {
            result.append(Text.literal("(" + volumeLabel + ") ").formatted(Formatting.GRAY));
        }

        if (ooc) {
            result.append(Text.literal("((" + message + "))").formatted(Formatting.LIGHT_PURPLE));
        } else {
            result.append(Text.literal(message).formatted(Formatting.WHITE));
        }

        return result;
    }

    private static boolean shouldReceiveLocal(ServerPlayerEntity sender, ServerPlayerEntity target, int messageRadius) {
        if (sender.getUuid().equals(target.getUuid())) return true;

        if (sameWorldAndInRadius(sender, target, messageRadius)) return true;

        ListenSetting listen = listenSettings.get(target.getUuid());
        if (listen == null || !isGameMasterOrAdmin(target)) return false;

        if (listen.listensAll()) return true;

        return sameWorldAndInRadius(sender, target, listen.radius());
    }

    private static boolean sameWorldAndInRadius(ServerPlayerEntity a, ServerPlayerEntity b, int radius) {
        if (radius < 0) return true;
        if (a.getWorld() != b.getWorld()) return false;

        Vec3d pa = a.getPos();
        Vec3d pb = b.getPos();
        double max = Math.max(0, radius);
        return pa.squaredDistanceTo(pb) <= max * max;
    }

    private static void sendGmChat(ServerPlayerEntity sender, String message) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;

        MutableText text = Text.literal("[GM] ").formatted(Formatting.GOLD)
                .append(displayNameText(sender))
                .append(Text.literal(": " + message).formatted(Formatting.GOLD));

        int recipients = 0;
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (target.getUuid().equals(sender.getUuid()) || isGameMasterOrAdmin(target)) {
                target.sendMessage(text, false);
                recipients++;
            }
        }

        log(server, "[GM] " + sender.getGameProfile().getName() + ": " + message + " | recipients=" + recipients);
    }

    private static int privateMessage(ServerCommandSource source, ServerPlayerEntity target, String message) {
        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) return 0;

        String clean = message == null ? "" : message.strip();
        if (clean.isEmpty()) return 0;

        Text toTarget = Text.literal("[ЛС от ").formatted(Formatting.AQUA)
                .append(displayNameText(sender))
                .append(Text.literal("] " + clean).formatted(Formatting.AQUA));
        Text toSender = Text.literal("[ЛС к ").formatted(Formatting.GRAY)
                .append(displayNameText(target))
                .append(Text.literal("] " + clean).formatted(Formatting.GRAY));

        target.sendMessage(toTarget, false);
        sender.sendMessage(toSender, false);

        lastPrivateContact.put(sender.getUuid(), target.getUuid());
        lastPrivateContact.put(target.getUuid(), sender.getUuid());

        log(sender.getServer(), "[PM] " + sender.getGameProfile().getName() + " -> " + target.getGameProfile().getName() + ": " + clean);
        return 1;
    }

    private static int replyPrivateMessage(ServerCommandSource source, String message) {
        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) return 0;

        UUID targetUuid = lastPrivateContact.get(sender.getUuid());
        if (targetUuid == null) {
            sender.sendMessage(Text.literal("Некому отвечать: личных сообщений ещё не было.").formatted(Formatting.RED), false);
            return 0;
        }

        ServerPlayerEntity target = sender.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null) {
            sender.sendMessage(Text.literal("Этот игрок сейчас не в сети.").formatted(Formatting.RED), false);
            return 0;
        }

        return privateMessage(source, target, message);
    }

    private static int toggleListenAll(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        ListenSetting current = listenSettings.get(player.getUuid());
        if (current != null) {
            listenSettings.remove(player.getUuid());
            player.sendMessage(Text.literal("/listen выключен. Чат снова слышен только в обычном радиусе.").formatted(Formatting.GRAY), false);
            logSystem(player.getServer(), player.getGameProfile().getName() + " disabled listen");
        } else {
            listenSettings.put(player.getUuid(), ListenSetting.allMode());
            player.sendMessage(Text.literal("/listen включён: слышен весь чат на любом расстоянии.").formatted(Formatting.GREEN), false);
            logSystem(player.getServer(), player.getGameProfile().getName() + " enabled listen all");
        }

        return 1;
    }

    private static int listenRadius(ServerCommandSource source, int radius) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        listenSettings.put(player.getUuid(), ListenSetting.radius(radius));
        player.sendMessage(Text.literal("/listen: слышен весь чат в радиусе " + radius + " блоков. Для выключения введи /listen.").formatted(Formatting.GREEN), false);
        logSystem(player.getServer(), player.getGameProfile().getName() + " enabled listen radius " + radius);
        return 1;
    }

    private static int reloadConfigCommand(ServerCommandSource source) {
        boolean ok = loadConfig(source.getServer());
        if (ok) {
            source.sendFeedback(() -> Text.literal("RP Chat config перезагружен: " + CONFIG_PATH).formatted(Formatting.GREEN), true);
            logSystem(source.getServer(), "Config reloaded by " + source.getName());
            return 1;
        }

        source.sendError(Text.literal("Не удалось перезагрузить RP Chat config. Подробности смотри в консоли."));
        return 0;
    }

    private static int showHelp(ServerCommandSource source) {
        sendHelp(source, "===== RP Chat help =====", Formatting.GOLD);
        sendHelp(source, "Обычный чат: 18 блоков или defaultRadius из config/rpchat.json", Formatting.GRAY);
        sendHelp(source, "= текст / == текст / === текст: тише, шёпот, едва слышно", Formatting.WHITE);
        sendHelp(source, "! текст / !! текст / !!! текст: громко, кричит, орёт", Formatting.WHITE);
        sendHelp(source, "=, ==, ===, !, !!, !!! отдельным сообщением: переключить постоянный радиус", Formatting.WHITE);
        sendHelp(source, "_текст: OOC. _ отдельным сообщением: включить/выключить OOC", Formatting.LIGHT_PURPLE);
        sendHelp(source, "-текст: GM-чат. - отдельным сообщением: включить/выключить GM-чат", Formatting.GOLD);
        sendHelp(source, "/m игрок сообщение: личное сообщение", Formatting.AQUA);
        sendHelp(source, "/r сообщение: ответ на последнее ЛС", Formatting.AQUA);
        sendHelp(source, "/listen: GM/Admin слышит весь чат; повтор /listen выключает", Formatting.GREEN);
        sendHelp(source, "/listen 500: GM/Admin слышит чат в радиусе 500; выключение через /listen", Formatting.GREEN);
        sendHelp(source, "/rpchat reload: перезагрузить серверный config/rpchat.json", Formatting.YELLOW);
        sendHelp(source, "/rpchat role игрок user|gm|admin: выдать роль RP Chat", Formatting.YELLOW);
        sendHelp(source, "/rename игрок &aНовыйНик: сменить ник в чате, над головой и в Tab", Formatting.YELLOW);
        sendHelp(source, "/rename: сбросить свой ник", Formatting.YELLOW);
        sendHelp(source, "/rename игрок: вернуть стандартный ник игроку", Formatting.YELLOW);
        sendHelp(source, "/rename игрок clear: тоже вернуть стандартный ник", Formatting.YELLOW);
        sendHelp(source, "Цвета ника: &a зелёный, &c красный, &6 золотой, &r сброс", Formatting.GRAY);
        sendHelp(source, "Логи: world/rpchat/logs/YYYY-MM-DD.log", Formatting.GRAY);
        return 1;
    }

    private static void sendHelp(ServerCommandSource source, String line, Formatting color) {
        source.sendFeedback(() -> Text.literal(line).formatted(color), false);
    }

    private static int setRole(ServerCommandSource source, ServerPlayerEntity target, String roleRaw) {
        String role = roleRaw.toLowerCase(Locale.ROOT);
        target.getCommandTags().remove(TAG_GM);
        target.getCommandTags().remove(TAG_ADMIN);

        switch (role) {
            case "user" -> {
            }
            case "gm" -> target.addCommandTag(TAG_GM);
            case "admin" -> target.addCommandTag(TAG_ADMIN);
            default -> {
                source.sendError(Text.literal("Роль должна быть user, gm или admin."));
                return 0;
            }
        }

        applyNickname(target);

        source.sendFeedback(() -> Text.literal("Роль RP Chat для " + target.getGameProfile().getName() + ": " + role), true);
        logSystem(source.getServer(), "Role set: " + target.getGameProfile().getName() + " -> " + role + " by " + source.getName());
        return 1;
    }

    private static boolean canManageNicknames(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) return true;
        return source.getEntity() instanceof ServerPlayerEntity player && isGameMasterOrAdmin(player);
    }

    private static int renamePlayer(ServerCommandSource source, ServerPlayerEntity target, String newNickRaw) {
        String newNick = newNickRaw == null ? "" : newNickRaw.strip();
        if (newNick.isBlank()) {
            source.sendError(Text.literal("Новый ник не может быть пустым."));
            return 0;
        }

        if (newNick.length() > 64) {
            source.sendError(Text.literal("Ник слишком длинный. Максимум 64 символа, включая цветовые коды."));
            return 0;
        }

        config.nicknames.put(target.getUuid().toString(), newNick);
        try {
            saveConfig();
        } catch (IOException ex) {
            source.sendError(Text.literal("Ник изменён в памяти, но не удалось сохранить config/rpchat.json."));
            System.err.println("[RPChat] Could not save nickname config: " + ex.getMessage());
        }

        applyNickname(target);

        MutableText feedback = Text.literal("Ник игрока ").formatted(Formatting.GRAY)
                .append(Text.literal(target.getGameProfile().getName()).formatted(Formatting.WHITE))
                .append(Text.literal(" изменён на ").formatted(Formatting.GRAY))
                .append(displayNameText(target));
        source.sendFeedback(() -> feedback, true);
        logSystem(source.getServer(), "Nickname set: " + target.getGameProfile().getName() + " -> " + newNick + " by " + source.getName());
        return 1;
    }

    private static int clearOwnNickname(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Из консоли нужно указать игрока: /rename nick"));
            return 0;
        }

        return clearNickname(source, player);
    }

    private static int clearNickname(ServerCommandSource source, ServerPlayerEntity target) {
        config.nicknames.remove(target.getUuid().toString());
        try {
            saveConfig();
        } catch (IOException ex) {
            source.sendError(Text.literal("Ник сброшен в памяти, но не удалось сохранить config/rpchat.json."));
            System.err.println("[RPChat] Could not save nickname config: " + ex.getMessage());
        }

        applyNickname(target);
        source.sendFeedback(() -> Text.literal("Ник игрока " + target.getGameProfile().getName() + " сброшен.").formatted(Formatting.GRAY), true);
        logSystem(source.getServer(), "Nickname cleared: " + target.getGameProfile().getName() + " by " + source.getName());
        return 1;
    }

    private static void applyAllNicknames(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyNickname(player);
        }
    }

    private static void applyNickname(ServerPlayerEntity player) {
        if (player == null) return;

        player.setCustomName(displayNameTextClean(player));
        player.setCustomNameVisible(true);
        updatePlayerListName(player);
    }

    private static void updatePlayerListName(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME),
                List.of(player)
        );

        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            target.networkHandler.sendPacket(packet);
        }
    }

    public static MutableText displayNameText(ServerPlayerEntity player) {
        return displayNameText(player, true);
    }

    public static MutableText displayNameTextClean(ServerPlayerEntity player) {
        return displayNameText(player, false);
    }

    private static MutableText displayNameText(ServerPlayerEntity player, boolean includeLegacyCodes) {
        String raw = config.nicknames.get(player.getUuid().toString());
        if (raw == null || raw.isBlank()) {
            raw = player.getGameProfile().getName();
        }

        return parseLegacyText(raw, defaultNameColor(player), includeLegacyCodes);
    }

    private static Formatting defaultNameColor(ServerPlayerEntity player) {
        return isGameMasterOrAdmin(player) ? Formatting.RED : Formatting.GREEN;
    }

    private static MutableText parseLegacyText(String raw, Formatting defaultColor, boolean includeLegacyCodes) {
        MutableText result = null;
        StringBuilder segment = new StringBuilder();

        Formatting currentColor = defaultColor;
        boolean obfuscated = false;
        boolean bold = false;
        boolean strikethrough = false;
        boolean underline = false;
        boolean italic = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < raw.length()) {
                Formatting formatting = legacyFormatting(raw.charAt(i + 1));
                if (formatting != null) {
                    result = appendStyledSegment(result, segment, currentColor, obfuscated, bold, strikethrough, underline, italic, includeLegacyCodes);

                    if (formatting == Formatting.RESET) {
                        currentColor = defaultColor;
                        obfuscated = false;
                        bold = false;
                        strikethrough = false;
                        underline = false;
                        italic = false;
                    } else if (formatting.isColor()) {
                        currentColor = formatting;
                        // Vanilla color codes reset active decorations.
                        obfuscated = false;
                        bold = false;
                        strikethrough = false;
                        underline = false;
                        italic = false;
                    } else {
                        switch (formatting) {
                            case OBFUSCATED -> obfuscated = true;
                            case BOLD -> bold = true;
                            case STRIKETHROUGH -> strikethrough = true;
                            case UNDERLINE -> underline = true;
                            case ITALIC -> italic = true;
                            default -> {
                            }
                        }
                    }

                    i++;
                    continue;
                }
            }

            segment.append(c);
        }

        result = appendStyledSegment(result, segment, currentColor, obfuscated, bold, strikethrough, underline, italic, includeLegacyCodes);
        if (result == null) {
            result = Text.literal(legacyCodes(defaultColor, false, false, false, false, false))
                    .formatted(defaultColor);
        }

        // Compatibility mode: some client chat UI mods render Text via getString() and lose JSON styles.
        // Keeping legacy § codes inside the literal text lets such UIs preserve color too.
        if (includeLegacyCodes) {
            result.append(Text.literal("§r").formatted(Formatting.RESET));
        }
        return result;
    }

    private static MutableText appendStyledSegment(MutableText result, StringBuilder segment, Formatting color,
                                                   boolean obfuscated, boolean bold, boolean strikethrough,
                                                   boolean underline, boolean italic, boolean includeLegacyCodes) {
        if (segment.isEmpty()) return result;

        ArrayList<Formatting> formatting = new ArrayList<>();
        if (color != null) formatting.add(color);
        if (obfuscated) formatting.add(Formatting.OBFUSCATED);
        if (bold) formatting.add(Formatting.BOLD);
        if (strikethrough) formatting.add(Formatting.STRIKETHROUGH);
        if (underline) formatting.add(Formatting.UNDERLINE);
        if (italic) formatting.add(Formatting.ITALIC);

        String legacyPrefix = includeLegacyCodes ? legacyCodes(color, obfuscated, bold, strikethrough, underline, italic) : "";
        MutableText part = Text.literal(legacyPrefix + segment)
                .formatted(formatting.toArray(new Formatting[0]));

        segment.setLength(0);

        if (result == null) return part;
        result.append(part);
        return result;
    }

    private static String legacyCodes(Formatting color, boolean obfuscated, boolean bold, boolean strikethrough,
                                      boolean underline, boolean italic) {
        StringBuilder codes = new StringBuilder();
        Character colorCode = legacyCode(color);
        if (colorCode != null) codes.append('§').append(colorCode);
        if (obfuscated) codes.append("§k");
        if (bold) codes.append("§l");
        if (strikethrough) codes.append("§m");
        if (underline) codes.append("§n");
        if (italic) codes.append("§o");
        return codes.toString();
    }

    private static Formatting legacyFormatting(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> Formatting.BLACK;
            case '1' -> Formatting.DARK_BLUE;
            case '2' -> Formatting.DARK_GREEN;
            case '3' -> Formatting.DARK_AQUA;
            case '4' -> Formatting.DARK_RED;
            case '5' -> Formatting.DARK_PURPLE;
            case '6' -> Formatting.GOLD;
            case '7' -> Formatting.GRAY;
            case '8' -> Formatting.DARK_GRAY;
            case '9' -> Formatting.BLUE;
            case 'a' -> Formatting.GREEN;
            case 'b' -> Formatting.AQUA;
            case 'c' -> Formatting.RED;
            case 'd' -> Formatting.LIGHT_PURPLE;
            case 'e' -> Formatting.YELLOW;
            case 'f' -> Formatting.WHITE;
            case 'k' -> Formatting.OBFUSCATED;
            case 'l' -> Formatting.BOLD;
            case 'm' -> Formatting.STRIKETHROUGH;
            case 'n' -> Formatting.UNDERLINE;
            case 'o' -> Formatting.ITALIC;
            case 'r' -> Formatting.RESET;
            default -> null;
        };
    }

    private static Character legacyCode(Formatting formatting) {
        if (formatting == null) return null;
        return switch (formatting) {
            case BLACK -> '0';
            case DARK_BLUE -> '1';
            case DARK_GREEN -> '2';
            case DARK_AQUA -> '3';
            case DARK_RED -> '4';
            case DARK_PURPLE -> '5';
            case GOLD -> '6';
            case GRAY -> '7';
            case DARK_GRAY -> '8';
            case BLUE -> '9';
            case GREEN -> 'a';
            case AQUA -> 'b';
            case RED -> 'c';
            case LIGHT_PURPLE -> 'd';
            case YELLOW -> 'e';
            case WHITE -> 'f';
            case OBFUSCATED -> 'k';
            case BOLD -> 'l';
            case STRIKETHROUGH -> 'm';
            case UNDERLINE -> 'n';
            case ITALIC -> 'o';
            case RESET -> 'r';
        };
    }

    private static boolean isGameMasterOrAdmin(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2)
                || player.getCommandTags().contains(TAG_GM)
                || player.getCommandTags().contains(TAG_ADMIN)
                || player.getCommandTags().contains(PROFILE_TAG_GM)
                || player.getCommandTags().contains(PROFILE_TAG_ADMIN);
    }

    private static boolean loadConfig(MinecraftServer server) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (!Files.exists(CONFIG_PATH)) {
                config = RpChatConfig.defaults();
                saveConfig();
                applyAllNicknames(server);
                logSystem(server, "Created default config at " + CONFIG_PATH);
                return true;
            }

            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            RpChatConfig loaded = GSON.fromJson(json, RpChatConfig.class);
            if (loaded == null) loaded = RpChatConfig.defaults();

            loaded.normalize();
            config = loaded;
            saveConfig();
            applyAllNicknames(server);
            return true;
        } catch (Exception ex) {
            System.err.println("[RPChat] Could not load config " + CONFIG_PATH + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    private static void saveConfig() throws IOException {
        Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void logSystem(MinecraftServer server, String message) {
        log(server, "[SYSTEM] " + message);
    }

    private static void log(MinecraftServer server, String line) {
        if (server == null || !config.enableChatLogging()) return;

        String stamped = "[" + LocalDateTime.now().format(LOG_TIME) + "] " + line;
        System.out.println("[RPChat] " + stamped);

        try {
            Path dir = server.getSavePath(WorldSavePath.ROOT).resolve("rpchat").resolve("logs");
            Files.createDirectories(dir);
            Path file = dir.resolve(LocalDate.now() + ".log");
            Files.writeString(file, stamped + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.println("[RPChat] Could not write chat log: " + ex.getMessage());
        }
    }

    private record ParsedChat(int radius, boolean ooc, String message, String volumeLabel) {
    }

    private record ListenSetting(boolean listensAll, int radius) {
        static ListenSetting allMode() {
            return new ListenSetting(true, -1);
        }

        static ListenSetting radius(int radius) {
            return new ListenSetting(false, Math.max(1, radius));
        }
    }

    private static class RpChatConfig {
        private int defaultRadius = 18;

        private int quietRadius = 9;
        private int whisperRadius = 3;
        private int barelyAudibleRadius = 1;

        private int loudRadius = 30;
        private int shoutRadius = 50;
        private int screamRadius = 80;

        private String quietLabel = "тихо";
        private String whisperLabel = "шёпотом";
        private String barelyAudibleLabel = "едва слышно";

        private String loudLabel = "громко";
        private String shoutLabel = "кричит";
        private String screamLabel = "орёт";

        private boolean enableChatLogging = true;

        private Map<String, String> nicknames = new HashMap<>();

        static RpChatConfig defaults() {
            return new RpChatConfig();
        }

        void normalize() {
            defaultRadius = positive(defaultRadius, 18);

            quietRadius = positive(quietRadius, 9);
            whisperRadius = positive(whisperRadius, 3);
            barelyAudibleRadius = positive(barelyAudibleRadius, 1);

            loudRadius = positive(loudRadius, 30);
            shoutRadius = positive(shoutRadius, 50);
            screamRadius = positive(screamRadius, 80);

            quietLabel = nonBlank(quietLabel, "тихо");
            whisperLabel = nonBlank(whisperLabel, "шёпотом");
            barelyAudibleLabel = nonBlank(barelyAudibleLabel, "едва слышно");

            loudLabel = nonBlank(loudLabel, "громко");
            shoutLabel = nonBlank(shoutLabel, "кричит");
            screamLabel = nonBlank(screamLabel, "орёт");

            if (nicknames == null) {
                nicknames = new HashMap<>();
            }
        }

        private int positive(int value, int fallback) {
            return value > 0 ? value : fallback;
        }

        private String nonBlank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        int defaultRadius() {
            return defaultRadius;
        }

        int quietRadius() {
            return quietRadius;
        }

        int whisperRadius() {
            return whisperRadius;
        }

        int barelyAudibleRadius() {
            return barelyAudibleRadius;
        }

        int loudRadius() {
            return loudRadius;
        }

        int shoutRadius() {
            return shoutRadius;
        }

        int screamRadius() {
            return screamRadius;
        }

        String quietLabel() {
            return quietLabel;
        }

        String whisperLabel() {
            return whisperLabel;
        }

        String barelyAudibleLabel() {
            return barelyAudibleLabel;
        }

        String loudLabel() {
            return loudLabel;
        }

        String shoutLabel() {
            return shoutLabel;
        }

        String screamLabel() {
            return screamLabel;
        }

        boolean enableChatLogging() {
            return enableChatLogging;
        }
    }
}
