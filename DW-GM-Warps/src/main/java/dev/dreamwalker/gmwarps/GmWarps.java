package dev.dreamwalker.gmwarps;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** Server-side commands for saved locations and returning after a warp. */
public final class GmWarps implements ModInitializer {
    private static final int GM_PERMISSION_LEVEL = 2;
    private static final SimpleCommandExceptionType INVALID_NAME = error(
            "Название варпа должно состоять из букв, цифр, дефиса или подчёркивания.");
    private static final SimpleCommandExceptionType INVALID_COORDINATES = error(
            "Укажите координаты в формате X,Y,Z в допустимых границах мира.");
    private static final SimpleCommandExceptionType NO_BACK = error(
            "Нет сохранённой позиции для возвращения после варпа.");
    private static final SimpleCommandExceptionType NO_CONSOLE_TARGET = error(
            "Для телепортации из консоли укажите хотя бы одного игрока.");
    private static final DynamicCommandExceptionType UNKNOWN_WARP = new DynamicCommandExceptionType(
            name -> Text.literal("Варп «" + name + "» не найден."));
    private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(
            name -> Text.literal("Игрок «" + name + "» сейчас не в сети."));
    private static final DynamicCommandExceptionType UNAVAILABLE_WORLD = new DynamicCommandExceptionType(
            id -> Text.literal("Измерение «" + id + "» сейчас недоступно."));
    private static final DynamicCommandExceptionType TELEPORT_FAILED = new DynamicCommandExceptionType(
            name -> Text.literal("Не удалось телепортировать игрока «" + name + "»."));

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("warp")
                    .requires(source -> source.hasPermissionLevel(GM_PERMISSION_LEVEL))
                    .executes(context -> listWarps(context.getSource()))
                    .then(CommandManager.argument("input", StringArgumentType.greedyString())
                            .suggests(GmWarps::suggestWarpInput)
                            .executes(context -> warp(context.getSource(), StringArgumentType.getString(context, "input")))));

            dispatcher.register(CommandManager.literal("setwarp")
                    .requires(source -> source.hasPermissionLevel(GM_PERMISSION_LEVEL))
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(context -> setWarp(context.getSource(), StringArgumentType.getString(context, "name")))));

            dispatcher.register(CommandManager.literal("delwarp")
                    .requires(source -> source.hasPermissionLevel(GM_PERMISSION_LEVEL))
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                            .suggests(GmWarps::suggestWarpNames)
                            .executes(context -> deleteWarp(context.getSource(), StringArgumentType.getString(context, "name")))));

            dispatcher.register(CommandManager.literal("back")
                    .requires(source -> source.hasPermissionLevel(GM_PERMISSION_LEVEL))
                    .executes(context -> back(context.getSource())));
        });
    }

    private static SimpleCommandExceptionType error(String message) {
        return new SimpleCommandExceptionType(Text.literal(message));
    }

    private static int listWarps(ServerCommandSource source) {
        List<String> names = WarpData.get(source.getServer()).warpNames();
        String message = names.isEmpty() ? "Сохранённых варпов пока нет."
                : "Варпы (" + names.size() + "): " + String.join(", ", names);
        source.sendFeedback(() -> Text.literal(message), false);
        return names.size();
    }

    private static int setWarp(ServerCommandSource source, String input) throws CommandSyntaxException {
        String name = input.trim();
        if (!WarpData.validName(name)) throw INVALID_NAME.create();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        boolean replaced = WarpData.get(source.getServer()).setWarp(name, WarpLocation.capture(player));
        String message = replaced ? "Варп «" + name + "» обновлён." : "Варп «" + name + "» сохранён.";
        source.sendFeedback(() -> Text.literal(message), true);
        return 1;
    }

    private static int deleteWarp(ServerCommandSource source, String input) throws CommandSyntaxException {
        String name = input.trim();
        if (!WarpData.validName(name)) throw INVALID_NAME.create();
        if (!WarpData.get(source.getServer()).removeWarp(name)) throw UNKNOWN_WARP.create(name);
        source.sendFeedback(() -> Text.literal("Варп «" + name + "» удалён."), true);
        return 1;
    }

    private static int warp(ServerCommandSource source, String input) throws CommandSyntaxException {
        String[] words = input.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return listWarps(source);

        // Resolve every destination and target before moving anyone.
        WarpData data = WarpData.get(source.getServer());
        Destination destination = resolveDestination(source, data, words[0]);
        List<ServerPlayerEntity> targets = resolveTargets(source, words);
        int moved = 0;
        for (ServerPlayerEntity player : targets) {
            WarpLocation before = WarpLocation.capture(player);
            float yaw = destination.coordinates ? player.getYaw() : destination.location.yaw();
            float pitch = destination.coordinates ? player.getPitch() : destination.location.pitch();
            teleport(player, destination.world, destination.location, yaw, pitch);
            data.rememberBack(player.getUuid(), before);
            moved++;
        }
        int count = moved;
        source.sendFeedback(() -> Text.literal("Телепортировано игроков: " + count + "."), false);
        return moved;
    }

    private static Destination resolveDestination(ServerCommandSource source, WarpData data, String text)
            throws CommandSyntaxException {
        if (text.indexOf(',') >= 0) {
            String[] parts = text.split(",", -1);
            if (parts.length != 3) throw INVALID_COORDINATES.create();
            double x;
            double y;
            double z;
            try {
                x = Double.parseDouble(parts[0]);
                y = Double.parseDouble(parts[1]);
                z = Double.parseDouble(parts[2]);
            } catch (NumberFormatException invalid) {
                throw INVALID_COORDINATES.create();
            }
            ServerPlayerEntity sender = source.getPlayer();
            ServerWorld world = sender == null ? source.getServer().getOverworld() : sender.getServerWorld();
            WarpLocation location = new WarpLocation(world.getRegistryKey().getValue(), x, y, z, 0, 0);
            if (!location.valid()) throw INVALID_COORDINATES.create();
            return new Destination(world, location, true);
        }

        if (!WarpData.validName(text)) throw INVALID_NAME.create();
        WarpLocation location = data.findWarp(text);
        if (location == null) throw UNKNOWN_WARP.create(text);
        if (!location.valid()) throw INVALID_COORDINATES.create();
        return new Destination(findWorld(source, location), location, false);
    }

    private static ServerWorld findWorld(ServerCommandSource source, WarpLocation location)
            throws CommandSyntaxException {
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, location.dimension());
        ServerWorld world = source.getServer().getWorld(key);
        if (world == null) throw UNAVAILABLE_WORLD.create(location.dimension());
        return world;
    }

    private static List<ServerPlayerEntity> resolveTargets(ServerCommandSource source, String[] words)
            throws CommandSyntaxException {
        if (words.length == 1) {
            ServerPlayerEntity self = source.getPlayer();
            if (self == null) throw NO_CONSOLE_TARGET.create();
            return List.of(self);
        }
        Map<UUID, ServerPlayerEntity> targets = new LinkedHashMap<>();
        for (int index = 1; index < words.length; index++) {
            String name = words[index];
            ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(name);
            if (player == null) throw UNKNOWN_PLAYER.create(name);
            targets.putIfAbsent(player.getUuid(), player);
        }
        return new ArrayList<>(targets.values());
    }

    private static int back(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        WarpData data = WarpData.get(source.getServer());
        WarpLocation location = data.back(player.getUuid());
        if (location == null) throw NO_BACK.create();
        if (!location.valid()) throw INVALID_COORDINATES.create();
        ServerWorld world = findWorld(source, location);
        teleport(player, world, location, location.yaw(), location.pitch());
        data.clearBack(player.getUuid());
        source.sendFeedback(() -> Text.literal("Вы вернулись на позицию до варпа."), false);
        return 1;
    }

    private static void teleport(ServerPlayerEntity player, ServerWorld world, WarpLocation location,
                                 float yaw, float pitch) throws CommandSyntaxException {
        if (!player.teleport(world, location.x(), location.y(), location.z(), Set.of(), yaw, pitch)) {
            throw TELEPORT_FAILED.create(player.getGameProfile().getName());
        }
        // Match vanilla /teleport so a previous fall does not continue after arrival.
        if (!player.isFallFlying()) {
            player.setVelocity(player.getVelocity().multiply(1, 0, 1));
            player.setOnGround(true);
        }
    }

    private static CompletableFuture<Suggestions> suggestWarpNames(CommandContext<ServerCommandSource> context,
                                                                    SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(WarpData.get(context.getSource().getServer()).warpNames(), builder);
    }

    private static CompletableFuture<Suggestions> suggestWarpInput(CommandContext<ServerCommandSource> context,
                                                                    SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = -1;
        for (int index = 0; index < remaining.length(); index++) {
            if (Character.isWhitespace(remaining.charAt(index))) lastSpace = index;
        }
        if (lastSpace < 0) return suggestWarpNames(context, builder);
        SuggestionsBuilder names = builder.createOffset(builder.getStart() + lastSpace + 1);
        return CommandSource.suggestMatching(context.getSource().getServer().getPlayerManager().getPlayerList().stream()
                .map(player -> player.getGameProfile().getName()), names);
    }

    private record Destination(ServerWorld world, WarpLocation location, boolean coordinates) {}
}
