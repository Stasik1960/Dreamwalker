package dev.dreamwalker.serverqa;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.NativePlayerBreak;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRuntime;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.tool.BbToolItem;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import dev.dreamwalker.bloodbornedw.tool.DebugToolModes;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public final class CityFollowupQa implements ModInitializer {
    private static final BlockPos SHIFT_ROOT = new BlockPos(144, 222, -938);
    private static final UUID SHIFT_OWNER = UUID.fromString("a49d55c8-471d-4f22-9467-62c7630015bf");
    private boolean pending;

    @Override public void onInitialize() {
        if (System.getProperty("dw.cityfollowup.qa") == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> pending = true);
        ServerTickEvents.END_SERVER_TICK.register(this::verify);
    }

    private void verify(MinecraftServer server) {
        if (!pending) return;
        pending = false;
        String result;
        try {
            var world = server.getOverworld();
            var player = FakePlayer.get(world, new GameProfile(UUID.randomUUID(), "DreamWalkerQA"));
            player.changeGameMode(GameMode.CREATIVE);
            require(player.isCreative() && player.getAbilities().allowModifyWorld, "Creative game mode fixture");
            if (Boolean.getBoolean("dw.cityfollowup.doors")) {
                DoorOverlapQa.verify(world, player);
                Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")), "PASS: ordinary door is blocked; no-collide door opens and preserves the obstruction");
                server.stop(false);
                return;
            }
            if (Boolean.getBoolean("dw.cityfollowup.reload")) {
                world.getChunk(SHIFT_ROOT);
                world.getChunk(SHIFT_ROOT.west());
                checkShift(world, -0.5, 1);
                require(DebugAdjustments.playersDisabled(world, SHIFT_ROOT)
                        && DebugAdjustments.blocksDisabled(world, SHIFT_ROOT), "Independent flags survive restart");
                Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")), "PASS: visual pose and collision switches survive server restart");
                server.stop(false);
                return;
            }
            player.setYaw(180);
            BlockPos from = SHIFT_ROOT;
            BlockPos west = from.west();
            world.getChunk(from);
            world.getChunk(west);
            require(world.getBlockState(from).isAir() && world.getBlockState(west).isAir(), "Fixture must start in air");
            var brick = Registries.BLOCK.get(new Identifier("bloodborne_dw", "source_stone_bricks"))
                    .getDefaultState().with(CompositeRootBlock.ROTATION, 2);
            require(CompositeRuntime.place(world, from, brick, SHIFT_OWNER, player).outcome() == TransactionCore.Outcome.COMMITTED,
                    "Fixture placement");
            var stack = new ItemStack(BbToolService.ITEM);
            checkCycle(player, stack, DebugToolModes.Tool.MODEL);
            var extra = DebugToolModes.extraItems();
            require(extra.size() == 2, "Two separate registered tool items");
            checkCycle(player, extra.get(0), DebugToolModes.Tool.BLOCK);
            checkCycle(player, extra.get(1), DebugToolModes.Tool.NPC);
            require(extra.get(0).isOf(Registries.ITEM.get(new Identifier("bloodborne_dw", "bb_block_tool")))
                    && extra.get(1).isOf(Registries.ITEM.get(new Identifier("bloodborne_dw", "bb_npc_tool"))),
                    "New items have stable registry IDs");
            player.equipStack(EquipmentSlot.MAINHAND, stack);
            var before = world.getBlockState(from).getCollisionShape(world, from, ShapeContext.of(player)).getBoundingBoxes();
            require(!before.isEmpty(), "Fixture has physical collision");
            setMode(stack, DebugToolModes.Action.LEFT);
            var target = ToolTarget.block(world, from, player);
            require(target != null && SHIFT_OWNER.equals(target.uuid()), "Fixture ownership");
            world.setBlockState(west, Blocks.STONE.getDefaultState());
            BbToolService.apply(player, target);
            checkShift(world, -0.5, 0);
            require(world.getBlockState(west).isOf(Blocks.STONE), "Intersected block is preserved");
            require(world.getBlockState(from).getCollisionShape(world, from, ShapeContext.of(player)).getBoundingBoxes().equals(before),
                    "Visual movement preserves the physical shape");

            setMode(stack, DebugToolModes.Action.RIGHT);
            BbToolService.apply(player, target);
            checkShift(world, 0, 0);
            require(world.getBlockState(west).isOf(Blocks.STONE) && CompositeRuntime.contributions(world, west).stream()
                    .noneMatch(part -> SHIFT_OWNER.equals(part.owner().instanceId())), "Visual shifts do not publish physical contributions");
            setMode(stack, DebugToolModes.Action.LEFT);
            BbToolService.apply(player, target);
            setMode(stack, DebugToolModes.Action.VISUAL_ROTATE);
            BbToolService.apply(player, target);
            checkShift(world, -0.5, 1);
            var blockTool = extra.get(0);
            player.equipStack(EquipmentSlot.MAINHAND, blockTool);
            setMode(blockTool, DebugToolModes.Action.PLAYER_COLLISION);
            BbToolService.apply(player, target);
            require(DebugAdjustments.playersDisabled(world, from) && !DebugAdjustments.blocksDisabled(world, from),
                    "Player collision switch is independent");
            require(world.getBlockState(from).getCollisionShape(world, from, ShapeContext.of(player)).isEmpty(),
                    "Disabled player shape");
            require(!world.getBlockState(from).getCollisionShape(world, from, ShapeContext.absent()).isEmpty(),
                    "Untargeted collision is unchanged");
            setMode(blockTool, DebugToolModes.Action.BLOCK_COLLISION);
            BbToolService.apply(player, target);
            require(DebugAdjustments.blocksDisabled(world, from), "Block placement switch");
            BlockPos secondRoot = from.east(4);
            require(CompositeRuntime.place(world, secondRoot, brick, UUID.randomUUID(), player).outcome()
                    == TransactionCore.Outcome.COMMITTED, "Second rotation fixture");
            setMode(blockTool, DebugToolModes.Action.BLOCK_ROTATE);
            BbToolService.apply(player, ToolTarget.block(world, secondRoot, player));
            require(world.getBlockState(secondRoot).get(CompositeRootBlock.ROTATION) == 3, "Physical block turns by one 45-degree step");
            var npc = MobRegistry.TYPES.values().iterator().next().create(world);
            require(npc != null, "NPC fixture exists");
            npc.refreshPositionAndAngles(from.getX() + 0.5, from.getY(), from.getZ() + 4.5, 0, 0);
            require(world.spawnEntity(npc), "NPC fixture is available by UUID");
            var npcTool = extra.get(1);
            player.equipStack(EquipmentSlot.MAINHAND, npcTool);
            setMode(npcTool, DebugToolModes.Action.NPC_CLOCKWISE);
            BbToolService.apply(player, ToolTarget.entity(npc));
            require(npc.getYaw() == 45 && npc.bodyYaw == 45 && npc.headYaw == 45, "NPC clockwise yaw");
            setMode(npcTool, DebugToolModes.Action.NPC_COUNTERCLOCKWISE);
            BbToolService.apply(player, ToolTarget.entity(npc));
            require(npc.getYaw() == 0 && npc.bodyYaw == 0 && npc.headYaw == 0, "NPC counterclockwise yaw");
            npc.discard();
            var objectType = ObjectRegistry.TYPES.values().iterator().next();
            var object = objectType.create(world);
            require(object != null, "RP decor fixture exists");
            object.refreshPositionAndAngles(from.getX() + 0.5, from.getY(), from.getZ() + 4.5, 0, 0);
            require(world.spawnEntity(object), "RP decor fixture is available by UUID");
            player.equipStack(EquipmentSlot.MAINHAND, stack);
            setMode(stack, DebugToolModes.Action.UP);
            BbToolService.apply(player, ToolTarget.entity(object));
            int objectSettings = DebugAdjustments.objectSettings(object);
            require(DebugAdjustments.y(objectSettings) == 0.5 && object.getY() == from.getY(),
                    "RP decor moves visually and retains the world position");
            var savedObject = object.writeNbt(new NbtCompound());
            var restored = objectType.create(world);
            require(restored != null, "RP reload fixture exists");
            restored.readNbt(savedObject);
            require(DebugAdjustments.objectSettings(restored) == objectSettings, "RP decor debug NBT round trip");
            object.discard();
            if (Boolean.getBoolean("dw.cityfollowup.toolsOnly")) {
                Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")),
                        "PASS: three item cycles, visual movement into stone, independent shape switches, model-only yaw, physical yaw, NPC yaw in both directions");
                server.stop(false);
                return;
            }
            BlockPos window = new BlockPos(208, 72, -940);
            for (int x = -16; x <= 16; x += 16) {
                for (int z = -16; z <= 16; z += 16) world.getChunk(window.add(x, 0, z));
            }
            require(StaticArchitectureCatalog.contains(world.getBlockState(window)), "Window fixture");
            var guestCell = window.west();
            var guests = CompositeRuntime.contributions(world, guestCell).stream().map(part -> part.owner()).toList();
            require(!guests.isEmpty(), "Fixture must contain independent guests");
            player.refreshPositionAndAngles(208.469, 72.036, -936.369, 180, 19.1f);
            var scope = NativePlayerBreak.begin(world, player, guestCell);
            boolean removed = false;
            try {
                removed = Boolean.TRUE.equals(NativePlayerBreak.remove(world, guestCell));
                if (!removed) {
                    var refusal = StaticArchitectureRuntime.breakAt(world, guestCell, player, false);
                    throw new AssertionError("Screenshot window rejected: " + refusal);
                }
            } finally {
                NativePlayerBreak.end(scope, removed);
            }
            require(removed && !StaticArchitectureCatalog.contains(world.getBlockState(window)), "Screenshot window Creative break");
            require(CompositeRuntime.contributions(world, guestCell).isEmpty(),
                    "Creative cleanup must remove the guests of the clicked shared carrier");
            result = "PASS: three tool cycles, visual transform with independent physical state and flags, Creative shared-cell break";
        } catch (Throwable failure) {
            result = "FAIL: " + failure;
            failure.printStackTrace();
        }
        try {
            Files.writeString(Path.of(System.getProperty("dw.cityfollowup.qa")), result);
        } catch (Exception failure) {
            failure.printStackTrace();
        } finally {
            server.stop(false);
        }
    }

    private static void checkCycle(net.minecraft.server.network.ServerPlayerEntity player, ItemStack stack,
            DebugToolModes.Tool tool) {
        player.equipStack(EquipmentSlot.MAINHAND, stack);
        require(DebugToolModes.tool(stack) == tool, "Tool identity");
        for (var action : tool.actions) {
            require(DebugToolModes.action(stack) == action, "Expected " + action);
            BbToolItem.cycle(player);
        }
        require(DebugToolModes.action(stack) == tool.actions.get(0), "Mode cycle wraps");
    }

    private static void setMode(ItemStack stack, DebugToolModes.Action action) {
        var index = DebugToolModes.tool(stack).actions.indexOf(action);
        require(index >= 0, "Wrong tool for " + action);
        stack.getOrCreateNbt().putInt(DebugToolModes.MODE_KEY, index);
    }

    private static void checkShift(ServerWorld world, double x, int turns) {
        var state = world.getBlockState(SHIFT_ROOT);
        require(state.getBlock() instanceof CompositeRootBlock && state.get(CompositeRootBlock.ROTATION) == 2,
                "Root block and its rotation are retained");
        var root = (CompositeBlockEntity) world.getBlockEntity(SHIFT_ROOT);
        require(root != null && root.resident() != null && SHIFT_OWNER.equals(root.resident().instanceId()), "Root UUID is retained");
        int pose = DebugAdjustments.renderPose(world, SHIFT_ROOT, state);
        require(DebugAdjustments.x(pose) == x && DebugAdjustments.z(pose) == 0 && DebugAdjustments.y(pose) == 0
                        && DebugAdjustments.turns(pose) == turns,
                "Expected world-space horizontal offset " + x + " and turns " + turns + ", found " + pose);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
