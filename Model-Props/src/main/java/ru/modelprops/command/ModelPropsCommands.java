package ru.modelprops.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.modelprops.ModelPropItem;
import ru.modelprops.net.ModelPropsNetworking;
import ru.modelprops.server.ServerModelCatalog;
import ru.modelprops.server.ServerModelEntry;

import java.io.IOException;
import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ModelPropsCommands {
    private ModelPropsCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("modelprops")
                        .then(literal("list").executes(ModelPropsCommands::list))
                        .then(literal("reload")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(ModelPropsCommands::reload))
                        .then(literal("give")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(argument("targets", EntityArgumentType.players())
                                        .then(argument("model", StringArgumentType.string())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                                        ServerModelCatalog.INSTANCE.entries().keySet(), builder))
                                                .executes(context -> give(context, 1))
                                                .then(argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(context -> give(context,
                                                                IntegerArgumentType.getInteger(context, "count")))))))
                        .then(literal("reset")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(argument("model", StringArgumentType.string())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                                ServerModelCatalog.INSTANCE.entries().keySet(), builder))
                                        .executes(ModelPropsCommands::reset)))
        ));
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        ServerModelCatalog catalog = ServerModelCatalog.INSTANCE;
        context.getSource().sendFeedback(() -> Text.literal("Model Props: " + catalog.entries().size() + " model(s)"), false);
        int shown = 0;
        for (ServerModelEntry entry : catalog.entries().values()) {
            if (shown++ >= 50) {
                context.getSource().sendFeedback(() -> Text.literal("...and more (see the server log)."), false);
                break;
            }
            context.getSource().sendFeedback(() -> Text.literal("- " + entry.id() + " — " + entry.displayName()), false);
        }
        context.getSource().sendFeedback(() -> Text.translatable("message.modelprops.reload_hint"), false);
        return catalog.entries().size();
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        ServerModelCatalog.ReloadResult result = ServerModelCatalog.INSTANCE.reload();
        ModelPropsNetworking.broadcastFullSync(context.getSource().getServer());
        context.getSource().sendFeedback(
                () -> Text.literal("Loaded " + result.loaded() + " model(s); " + result.errors().size() + " error(s)."), true);
        for (String error : result.errors()) {
            context.getSource().sendError(Text.literal(error));
        }
        return result.loaded();
    }

    private static int give(CommandContext<ServerCommandSource> context, int count) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "model");
        ServerModelEntry entry = ServerModelCatalog.INSTANCE.get(id);
        if (entry == null) {
            context.getSource().sendError(Text.literal("Unknown model: " + id));
            return 0;
        }
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "targets");
        for (ServerPlayerEntity player : targets) {
            var stack = ModelPropItem.createStack(id, entry.displayName(), count);
            player.giveItemStack(stack);
            if (!stack.isEmpty()) {
                player.dropItem(stack, false);
            }
        }
        context.getSource().sendFeedback(
                () -> Text.literal("Gave " + entry.displayName() + " ×" + count + " to " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int reset(CommandContext<ServerCommandSource> context) {
        String id = StringArgumentType.getString(context, "model");
        if (!ServerModelCatalog.INSTANCE.contains(id)) {
            context.getSource().sendError(Text.literal("Unknown model: " + id));
            return 0;
        }
        try {
            var transform = ServerModelCatalog.INSTANCE.transforms().reset(id);
            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                ModelPropsNetworking.sendTransform(player, id, transform);
            }
            context.getSource().sendFeedback(() -> Text.literal("Reset transform for " + id), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendError(Text.literal("Could not save transforms.json: " + exception.getMessage()));
            return 0;
        }
    }
}
