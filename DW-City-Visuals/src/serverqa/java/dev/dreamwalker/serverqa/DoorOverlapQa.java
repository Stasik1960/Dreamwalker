package dev.dreamwalker.serverqa;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeOverlapEdit;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class DoorOverlapQa {
    private DoorOverlapQa() {}

    static void verify(ServerWorld world, ServerPlayerEntity player) {
        verifyDoor(world, player, new BlockPos(151, 226, -938), false);
        verifyDoor(world, player, new BlockPos(166, 226, -938), true);
    }

    private static void verifyDoor(ServerWorld world, ServerPlayerEntity player, BlockPos pos, boolean noCollide) {
        world.getChunk(pos);
        var item = (BlockItem) Registries.ITEM.get(new Identifier("bloodborne_dw", "prototype_double_door"));
        player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item));
        player.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() - 4.0, 0, 0);
        BlockPos support = pos.down();
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        if (noCollide) require(NoCollidePlacement.arm(player), "Could not arm no-collide for the door item");
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false);
        require(item.place(new ItemPlacementContext(player, Hand.MAIN_HAND, player.getMainHandStack(), hit)).isAccepted(),
                "Door item placement failed");
        var state = world.getBlockState(pos);
        require(state.isOf(item.getBlock()) && !state.get(CompositeRootBlock.OPEN), "Expected a closed door at its root");
        var root = (CompositeBlockEntity) world.getBlockEntity(pos);
        var owner = root.resident();
        require(root.payload().getBoolean(CompositeOverlapEdit.OPEN_WITH_OVERLAP) == noCollide, "Incorrect persisted door flag");
        NoCollidePlacement.invalidate(player);
        require(!NoCollidePlacement.armed(player) && !NoCollidePlacement.ignoringBlocks(),
                "Opening must be tested after leaving placement mode");
        var openState = state.with(CompositeRootBlock.OPEN, true);
        var closed = CompositeRuntime.instance(world, pos, state, owner.instanceId(), root.payload());
        var open = CompositeRuntime.instance(world, pos, openState, owner.instanceId(), root.payload());
        var obstruction = open.cells().entrySet().stream()
                .filter(cell -> !cell.getValue().collision().isEmpty())
                .filter(cell -> !closed.cells().containsKey(cell.getKey()) || closed.cells().get(cell.getKey()).collision().isEmpty())
                .map(cell -> CompositeData.pos(owner.root().add(cell.getKey())))
                .filter(point -> world.getBlockState(point).isAir())
                .findFirst().orElseThrow(() -> new AssertionError("No open-only cell for the obstruction"));
        world.setBlockState(obstruction, Blocks.STONE.getDefaultState());
        var transition = CompositeRuntime.transition(world, owner, openState, player);
        require((transition.outcome() == TransactionCore.Outcome.COMMITTED) == noCollide,
                "Opening with a stone obstruction and no-collide=" + noCollide + ": " + transition);
        require(world.getBlockState(pos).get(CompositeRootBlock.OPEN) == noCollide, "Incorrect final door state");
        require(world.getBlockState(obstruction).isOf(Blocks.STONE), "Door must preserve the obstructing block");
        require(!NoCollidePlacement.ignoringBlocks(), "Collision scope leaked after the door action");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
