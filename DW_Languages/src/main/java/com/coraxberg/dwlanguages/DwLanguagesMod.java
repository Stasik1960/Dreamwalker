package com.coraxberg.dwlanguages;

import com.coraxberg.rpchat.RpChatMod;
import com.coraxberg.rpchat.api.RpChatEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.*;

public final class DwLanguagesMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("DWLanguages");
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("dw_languages/languages");
    private final LanguageRegistry registry = new LanguageRegistry();
    private final PlayerLanguages players = new PlayerLanguages();

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                registry.initialize(directory);
                players.load(server.getSavePath(WorldSavePath.ROOT).resolve("dw_languages/players.json"));
                LOGGER.info("Loaded {} roleplay languages", registry.enabled().size());
            } catch (IOException ex) {
                // Never silently overwrite broken knowledge data or send unprotected speech.
                throw new IllegalStateException("DW Languages: исправьте конфиг/данные перед запуском: " + ex.getMessage(), ex);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> players.clear());
        RpChatEvents.PREPARE_IC_BODY.register((sender, original, previous) -> {
            String active = players.get(sender.getUuid()).active();
            if (active.equals("common")) return previous;
            Language language = registry.get(active);
            if (language == null || !knows(sender, language.code)) {
                // A reload or external permission revocation cannot silently turn foreign speech into common.
                sender.sendMessage(Text.literal("Выбранный язык недоступен. Выберите /lang <язык> или /lang off.")
                        .formatted(Formatting.RED), false);
                return recipient -> "[Речь на недоступном языке]";
            }
            String transformed = language.transform(original);
            String prefix = "[" + language.name + "] ";
            return recipient -> prefix + (knows(recipient, language.code) ? previous.forRecipient(recipient) : transformed);
        });
        RpChatEvents.CHAT_CONTROL.register((sender, raw) -> {
            // Only a standalone +code is a control; ordinary phrases beginning with '+' stay chat.
            if (!raw.matches("\\+[\\p{L}\\p{Nd}_-]+")) return false;
            select(sender.getCommandSource(), raw.substring(1));
            return true;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            var root = literal("lang")
                    .executes(ctx -> help(ctx.getSource()))
                    .then(literal("off").executes(ctx -> select(ctx.getSource(), "common")))
                    .then(literal("common").executes(ctx -> select(ctx.getSource(), "common")))
                    .then(literal("list").executes(ctx -> list(ctx.getSource())))
                    .then(literal("reload").requires(DwLanguagesMod::admin).executes(ctx -> reload(ctx.getSource())));
            for (String action : new String[]{"add", "del"}) {
                boolean grant = action.equals("add");
                root.then(literal(action).requires(DwLanguagesMod::manage)
                        .then(argument("player", GameProfileArgumentType.gameProfile())
                                .then(argument("language", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(registry.enabled().stream().map(l -> l.code), builder))
                                        .executes(ctx -> {
                                            String code = StringArgumentType.getString(ctx, "language").toLowerCase(Locale.ROOT);
                                            if (registry.get(code) == null) return error(ctx.getSource(), "Язык не найден или отключён: " + code);
                                            try {
                                                for (var profile : GameProfileArgumentType.getProfileArgument(ctx, "player")) {
                                                    players.grant(profile.getId(), code, grant);
                                                    ctx.getSource().sendFeedback(() -> Text.literal(profile.getName() + ": " + (grant ? "добавлен " : "удалён ") + code), false);
                                                }
                                                return 1;
                                            } catch (IOException ex) { return storageError(ctx.getSource(), ex); }
                                        }))));
            }
            root.then(argument("language", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(registry.enabled().stream()
                            .filter(l -> ctx.getSource().getEntity() instanceof ServerPlayerEntity p && knows(p, l.code)).map(l -> l.code), builder))
                    .executes(ctx -> select(ctx.getSource(), StringArgumentType.getString(ctx, "language"))));
            dispatcher.register(root);
        });
    }

    private boolean knows(ServerPlayerEntity player, String code) {
        if (RpChatMod.isGameMasterOrAdmin(player)) return true;
        Boolean grant = players.get(player.getUuid()).grants().get(code);
        if (grant != null) return grant;
        return Permissions.check(player, "lang.know." + code, false);
    }
    private static boolean admin(ServerCommandSource source) {
        if (source.hasPermissionLevel(2) || Permissions.check(source, "lang.admin", false)) return true;
        return source.getEntity() instanceof ServerPlayerEntity p &&
                (p.getCommandTags().contains("rpchat_admin") || p.getCommandTags().contains("profile_admin"));
    }
    private static boolean manage(ServerCommandSource source) {
        return admin(source) || Permissions.check(source, "lang.manage", false)
                || source.getEntity() instanceof ServerPlayerEntity p && RpChatMod.isGameMasterOrAdmin(p);
    }
    private int select(ServerCommandSource source, String input) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return error(source, "Выбор языка доступен только игроку.");
        String code = input.toLowerCase(Locale.ROOT);
        if (code.equals("off")) code = "common";
        Language language = registry.get(code);
        if (!code.equals("common")) {
            if (language == null) return error(source, "Язык не найден или отключён. Список: /lang list");
            if (!knows(player, code)) return error(source, "Ваш персонаж не знает этот язык.");
        }
        try {
            players.select(player.getUuid(), code);
            String label = code.equals("common") ? "Общий" : language.name;
            source.sendFeedback(() -> Text.literal("Язык: " + label).formatted(Formatting.GREEN), false);
            return 1;
        } catch (IOException ex) { return storageError(source, ex); }
    }
    private int list(ServerCommandSource source) {
        StringBuilder message = new StringBuilder("Доступные языки: common — Общий");
        for (Language language : registry.enabled()) {
            if (!(source.getEntity() instanceof ServerPlayerEntity player) || knows(player, language.code)) {
                message.append(", ").append(language.code).append(" — ").append(language.name);
            }
        }
        source.sendFeedback(() -> Text.literal(message.toString()), false);
        return 1;
    }
    private int reload(ServerCommandSource source) {
        try {
            registry.reload(directory);
            source.sendFeedback(() -> Text.literal("Языки перезагружены: " + registry.enabled().size()), false);
            return 1;
        } catch (IOException ex) {
            LOGGER.error("Language reload rejected", ex);
            return error(source, "Ошибка: " + ex.getMessage() + ". Предыдущие языки сохранены.");
        }
    }
    private int help(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("/lang list · /lang <код> · /lang off · +<код>"), false);
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            String active = players.get(player.getUuid()).active();
            source.sendFeedback(() -> Text.literal("Выбранный язык: " + active), false);
        }
        if (manage(source)) source.sendFeedback(() -> Text.literal("/lang add <ник> <код> · /lang del <ник> <код>"), false);
        if (admin(source)) source.sendFeedback(() -> Text.literal("/lang reload"), false);
        return 1;
    }
    private static int storageError(ServerCommandSource source, IOException ex) {
        LOGGER.error("Cannot save player languages", ex);
        return error(source, "Не удалось сохранить данные. Изменение не применено; подробности в журнале сервера.");
    }
    private static int error(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }
}
