package com.coraxberg.rpchatui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RpChatUiMod implements ModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("rpchatui-server.json");

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 4096;
    private static final int DEFAULT_LENGTH = 512;

    private static final String TAG_GM = "rpchat_gm";
    private static final String TAG_ADMIN = "rpchat_admin";
    private static final String PROFILE_TAG_GM = "profile_gm";
    private static final String PROFILE_TAG_ADMIN = "profile_admin";

    private static RpChatUiServerConfig config = RpChatUiServerConfig.defaults();

    @Override
    public void onInitialize() {
        loadConfig();
        registerCommands();
        registerJoinSync();
        registerLengthGuard();
    }

    private static void registerLengthGuard() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) -> {
            String raw = message.getContent().getString();
            int max = getEffectiveMaxLength(sender.getGameProfile().getName());

            if (raw.length() > max) {
                sender.sendMessage(Text.literal("Сообщение слишком длинное: " + raw.length() + "/" + max + " символов.")
                        .formatted(Formatting.RED), false);
                return false;
            }

            return true;
        });
    }

    private static void registerJoinSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player));
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("rpchatui")
                    .then(literal("length")
                            .then(literal("global")
                                    .requires(RpChatUiMod::isAdminSource)
                                    .then(argument("length", IntegerArgumentType.integer(MIN_LENGTH, MAX_LENGTH))
                                            .executes(ctx -> setGlobalLength(
                                                    ctx.getSource(),
                                                    IntegerArgumentType.getInteger(ctx, "length")))))
                            .then(literal("player")
                                    .requires(RpChatUiMod::isGmOrAdminSource)
                                    .then(argument("player", StringArgumentType.word())
                                            .suggests((ctx, builder) -> {
                                                MinecraftServer server = ctx.getSource().getServer();
                                                for (String name : server.getPlayerNames()) {
                                                    builder.suggest(name);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .then(argument("length", IntegerArgumentType.integer(MIN_LENGTH, MAX_LENGTH))
                                                    .executes(ctx -> setPlayerLength(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "player"),
                                                            IntegerArgumentType.getInteger(ctx, "length"))))))
                            .then(literal("reset")
                                    .requires(RpChatUiMod::isGmOrAdminSource)
                                    .then(argument("player", StringArgumentType.word())
                                            .suggests((ctx, builder) -> {
                                                MinecraftServer server = ctx.getSource().getServer();
                                                for (String name : server.getPlayerNames()) {
                                                    builder.suggest(name);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> resetPlayerLength(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player")))))
                            .then(literal("get")
                                    .executes(ctx -> getLength(ctx.getSource(), null))
                                    .then(argument("player", StringArgumentType.word())
                                            .suggests((ctx, builder) -> {
                                                MinecraftServer server = ctx.getSource().getServer();
                                                for (String name : server.getPlayerNames()) {
                                                    builder.suggest(name);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> getLength(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player")))))));
        });
    }

    private static int setGlobalLength(ServerCommandSource source, int length) {
        config.globalMaxLength = clampLength(length);
        saveConfig();

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            syncPlayer(player);
        }

        source.sendFeedback(() -> Text.literal("Глобальная максимальная длина сообщения: " + config.globalMaxLength), true);
        return 1;
    }

    private static int setPlayerLength(ServerCommandSource source, String playerName, int length) {
        String key = normalizeName(playerName);
        int clamped = clampLength(length);
        config.playerMaxLengths.put(key, clamped);
        saveConfig();

        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerName);
        if (player != null) {
            syncPlayer(player);
        }

        source.sendFeedback(() -> Text.literal("Максимальная длина сообщения для " + playerName + ": " + clamped), true);
        return 1;
    }

    private static int resetPlayerLength(ServerCommandSource source, String playerName) {
        String key = normalizeName(playerName);
        Integer removed = config.playerMaxLengths.remove(key);
        saveConfig();

        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(playerName);
        if (player != null) {
            syncPlayer(player);
        }

        if (removed == null) {
            source.sendFeedback(() -> Text.literal("У " + playerName + " не было индивидуального лимита."), false);
        } else {
            source.sendFeedback(() -> Text.literal("Индивидуальный лимит для " + playerName + " сброшен. Теперь действует глобальный: " + config.globalMaxLength), true);
        }

        return 1;
    }

    private static int getLength(ServerCommandSource source, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            source.sendFeedback(() -> Text.literal("Глобальная максимальная длина сообщения: " + config.globalMaxLength), false);
            return 1;
        }

        int effective = getEffectiveMaxLength(playerName);
        Integer individual = config.playerMaxLengths.get(normalizeName(playerName));

        if (individual == null) {
            source.sendFeedback(() -> Text.literal(playerName + ": " + effective + " символов (глобальный лимит)"), false);
        } else {
            source.sendFeedback(() -> Text.literal(playerName + ": " + effective + " символов (индивидуальный лимит)"), false);
        }

        return 1;
    }

    private static void syncPlayer(ServerPlayerEntity player) {
        int max = getEffectiveMaxLength(player.getGameProfile().getName());

        if (!ServerPlayNetworking.canSend(player, RpChatUiConstants.MAX_LENGTH_PACKET)) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(max);
        ServerPlayNetworking.send(player, RpChatUiConstants.MAX_LENGTH_PACKET, buf);
    }

    private static int getEffectiveMaxLength(String playerName) {
        Integer individual = config.playerMaxLengths.get(normalizeName(playerName));
        return clampLength(individual == null ? config.globalMaxLength : individual);
    }

    private static int clampLength(int value) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, value));
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isAdminSource(ServerCommandSource source) {
        if (source.hasPermissionLevel(4)) return true;
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return isAdmin(player);
        }
        return false;
    }

    private static boolean isGmOrAdminSource(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) return true;
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return isGmOrAdmin(player);
        }
        return false;
    }

    private static boolean isAdmin(ServerPlayerEntity player) {
        return player.getCommandTags().contains(TAG_ADMIN)
                || player.getCommandTags().contains(PROFILE_TAG_ADMIN);
    }

    private static boolean isGmOrAdmin(ServerPlayerEntity player) {
        return isAdmin(player)
                || player.getCommandTags().contains(TAG_GM)
                || player.getCommandTags().contains(PROFILE_TAG_GM);
    }

    private static void loadConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (!Files.exists(CONFIG_PATH)) {
                config = RpChatUiServerConfig.defaults();
                saveConfig();
                return;
            }

            RpChatUiServerConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), RpChatUiServerConfig.class);
            config = loaded == null ? RpChatUiServerConfig.defaults() : loaded;
            config.normalize();
            saveConfig();
        } catch (Exception ex) {
            System.err.println("[RPChatUI] Could not load server config: " + ex.getMessage());
            config = RpChatUiServerConfig.defaults();
        }
    }

    private static void saveConfig() {
        try {
            config.normalize();
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[RPChatUI] Could not save server config: " + ex.getMessage());
        }
    }
}
