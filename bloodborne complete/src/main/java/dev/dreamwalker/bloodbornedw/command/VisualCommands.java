package dev.dreamwalker.bloodbornedw.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.block.Visual;
import dev.dreamwalker.bloodbornedw.visual.VisualService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.lang.reflect.Method;

/** OP-only server commands. Client input is not involved. */
public final class VisualCommands {
    private VisualCommands() { }
    public static void register() { CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> { dispatcher.register(root("bloodborne")); dispatcher.register(root("bb")); }); }
    private static LiteralArgumentBuilder<ServerCommandSource> root(String name) {
        return CommandManager.literal(name)
            .then(CommandManager.literal("debug").executes(context -> debug(context.getSource())))
            .then(CommandManager.literal("visual").requires(source -> source.hasPermissionLevel(2)).then(mode("base", Visual.BASE)).then(mode("alt", Visual.ALT)));
    }
    private static LiteralArgumentBuilder<ServerCommandSource> mode(String name, Visual visual) {
        return CommandManager.literal(name)
            .then(CommandManager.literal("look").executes(c -> look(c.getSource(), visual, List.of())).then(CommandManager.argument("ids", StringArgumentType.greedyString()).executes(c -> look(c.getSource(), visual, ids(c)))))
            .then(CommandManager.literal("all").executes(c -> all(c.getSource(), visual, List.of(), false)).then(CommandManager.argument("ids", StringArgumentType.greedyString()).executes(c -> all(c.getSource(), visual, ids(c), false))))
            .then(CommandManager.literal("reset").executes(c -> all(c.getSource(), visual, List.of(), true)).then(CommandManager.argument("ids", StringArgumentType.greedyString()).executes(c -> all(c.getSource(), visual, ids(c), true))))
            .then(CommandManager.literal("selection").executes(c -> selection(c.getSource(), visual, List.of())).then(CommandManager.argument("ids", StringArgumentType.greedyString()).executes(c -> selection(c.getSource(), visual, ids(c)))))
            .then(CommandManager.literal("area").then(CommandManager.argument("from", BlockPosArgumentType.blockPos()).then(CommandManager.argument("to", BlockPosArgumentType.blockPos()).executes(c -> area(c.getSource(), visual, BlockPosArgumentType.getLoadedBlockPos(c,"from"), BlockPosArgumentType.getLoadedBlockPos(c,"to"), List.of())).then(CommandManager.argument("ids", StringArgumentType.greedyString()).executes(c -> area(c.getSource(),visual,BlockPosArgumentType.getLoadedBlockPos(c,"from"),BlockPosArgumentType.getLoadedBlockPos(c,"to"),ids(c)))))));
    }
    private static int look(ServerCommandSource source, Visual visual, Collection<String> ignored) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow(); HitResult ray = player.raycast(8.0, 0.0F, false); if (!(ray instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) throw new IllegalArgumentException("Look at a Bloodborne DW block"); BlockPos pos=hit.getBlockPos(); BlockState state=player.getServerWorld().getBlockState(pos); String id=VisualService.id(state); if(id==null || (!ignored.isEmpty() && !ignored.contains(id))) throw new IllegalArgumentException("Look at a matching Bloodborne DW block");
        VisualService.setPoint(player.getServerWorld(),pos,id,visual); player.getServerWorld().setBlockState(pos,state.with(DwBlocks.VISUAL,visual),2); source.sendFeedback(()->Text.literal("Visual " + visual.asString() + " set at " + pos.toShortString()),true); return 1;
    }
    private static int all(ServerCommandSource source, Visual visual, Collection<String> ids, boolean reset) { ServerWorld world=source.getWorld(); if(reset)VisualService.reset(world,ids,visual); else VisualService.addAllRule(world,ids,visual); source.sendFeedback(()->Text.literal("Visual rule saved"),true); return 1; }
    private static int area(ServerCommandSource source, Visual visual, BlockPos first, BlockPos second, Collection<String> ids) { VisualService.addAreaRule(source.getWorld(),first,second,ids,visual); source.sendFeedback(()->Text.literal("Visual area rule saved"),true); return 1; }
    private static int selection(ServerCommandSource source, Visual visual, Collection<String> ids) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow(); BlockPos[] cuboid = worldEditCuboid(player);
        VisualService.addAreaRule(player.getServerWorld(), cuboid[0], cuboid[1], ids, visual);
        source.sendFeedback(() -> Text.literal("Visual WorldEdit cuboid rule saved"), true); return 1;
    }
    /** All WorldEdit symbols are loaded only after confirming its optional Fabric mod is present. */
    private static BlockPos[] worldEditCuboid(ServerPlayerEntity player) throws CommandSyntaxException {
        if (!FabricLoader.getInstance().isModLoaded("worldedit")) fail("WorldEdit is not installed");
        try {
            Class<?> adapter = Class.forName("com.sk89q.worldedit.fabric.FabricAdapter");
            Object wePlayer = adapter.getMethod("adaptPlayer", ServerPlayerEntity.class).invoke(null, player);
            Class<?> worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit"); Object instance = worldEdit.getMethod("getInstance").invoke(null);
            Object sessions = worldEdit.getMethod("getSessionManager").invoke(instance);
            Method get = find(sessions.getClass(), "get", 1); Object session = get.invoke(sessions, wePlayer);
            Object weWorld = find(wePlayer.getClass(), "getWorld", 0).invoke(wePlayer);
            Object region = find(session.getClass(), "getSelection", 1).invoke(session, weWorld);
            Class<?> cuboid = Class.forName("com.sk89q.worldedit.regions.CuboidRegion");
            if (!cuboid.isInstance(region)) fail("WorldEdit selection must be cuboid; use //sel cuboid");
            Object min = find(region.getClass(), "getMinimumPoint", 0).invoke(region); Object max = find(region.getClass(), "getMaximumPoint", 0).invoke(region);
            BlockPos first = vector(min), second = vector(max);
            if (first.getY() < player.getServerWorld().getBottomY() || second.getY() >= player.getServerWorld().getTopY()) fail("WorldEdit selection is outside this dimension's build height");
            return new BlockPos[]{first, second};
        } catch (CommandSyntaxException exception) { throw exception; }
        catch (ReflectiveOperationException | RuntimeException exception) { fail("WorldEdit selection is unavailable; make a cuboid selection in this world"); return null; }
    }
    private static Method find(Class<?> type, String name, int arguments) throws NoSuchMethodException { for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == arguments) return method; throw new NoSuchMethodException(name); }
    private static BlockPos vector(Object vector) throws ReflectiveOperationException { return new BlockPos(((Number)find(vector.getClass(),"getBlockX",0).invoke(vector)).intValue(), ((Number)find(vector.getClass(),"getBlockY",0).invoke(vector)).intValue(), ((Number)find(vector.getClass(),"getBlockZ",0).invoke(vector)).intValue()); }
    private static void fail(String message) throws CommandSyntaxException { throw new SimpleCommandExceptionType(Text.literal(message)).create(); }
    private static int debug(ServerCommandSource source) throws CommandSyntaxException { ServerPlayerEntity player=source.getPlayerOrThrow(); HitResult ray=player.raycast(8.0,0.0F,false); if(!(ray instanceof BlockHitResult hit))throw new IllegalArgumentException("Look at a block"); BlockPos pos=hit.getBlockPos(); BlockState state=player.getServerWorld().getBlockState(pos); String id=VisualService.id(state); source.sendFeedback(()->Text.literal("id="+(id==null?"none":id)+" source="+(id==null?"none":DwBlocks.byId(id).source())+" pos="+pos.toShortString()+" properties="+state.getEntries()+" collision="+!state.getCollisionShape(player.getServerWorld(),pos).isEmpty()+" light="+state.getLuminance()+" "+String.join(" ",VisualService.describe(player.getServerWorld(),pos,state))),false); return 1; }
    private static List<String> ids(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) { String raw=StringArgumentType.getString(context,"ids"); ArrayList<String> result=new ArrayList<>(); for(String token:raw.trim().split("\\s+")){String id=token.startsWith("bloodborne_dw:")?token.substring("bloodborne_dw:".length()):token; if(!id.matches("\\d{5}")||DwBlocks.byId(id)==null)throw new IllegalArgumentException("Expected known bloodborne_dw:##### id"); result.add(id);} return result; }
}
