package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeOverlapEdit;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class BbHorizontalMoves {
    public static final String MODE_KEY = "BBHalfHorizontalMode";
    public static final int LEFT = 1, RIGHT = 2;

    private BbHorizontalMoves() {}

    public static int mode(ItemStack stack) {
        if (!(stack.getItem() instanceof BbToolItem) || !stack.hasNbt()
                || BbToolItem.mode(stack) != BbToolItem.Mode.DOWN) return 0;
        int value = stack.getNbt().getInt(MODE_KEY);
        return value == LEFT || value == RIGHT ? value : 0;
    }

    public static String title(int mode) {
        return mode == LEFT ? "Сдвиг влево на 0,5 блока" : "Сдвиг вправо на 0,5 блока";
    }

    public static boolean shift(ServerPlayerEntity player, ToolTarget target, int mode) {
        if (target == null || target.kind() != ToolTarget.Kind.ARCHITECTURE
                || !BbToolService.canEdit(player) || !target.valid(player.getServer())) return false;
        ServerWorld world = player.getServerWorld();
        if (target.world(player.getServer()) != world) return false;
        BlockPos pos = BlockPos.fromLong(target.root());
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof CompositeRootBlock)
                || !(world.getBlockEntity(pos) instanceof CompositeBlockEntity root)) return false;
        var owner = root.resident();
        if (owner == null || !owner.instanceId().equals(target.uuid())) return false;

        var facing = player.getHorizontalFacing();
        var direction = mode == LEFT ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
        var delta = new CompositeSourceShift.Shift(direction.getOffsetX() * 0.5, 0,
                direction.getOffsetZ() * 0.5).rotate(-state.get(CompositeRootBlock.ROTATION));
        var payload = root.payload().copy();
        var old = CompositeSourceShift.local(payload);
        var shifted = new CompositeSourceShift.Shift(old.x() + delta.x(), old.y(), old.z() + delta.z());
        var shift = new NbtList();
        shift.add(NbtDouble.of(shifted.x()));
        shift.add(NbtDouble.of(shifted.y()));
        shift.add(NbtDouble.of(shifted.z()));
        payload.put("SourceShift", shift);

        return CompositeOverlapEdit.run(owner, () -> CompositeRuntime.transitionPayload(world, owner, state, payload, player))
                .outcome() == TransactionCore.Outcome.COMMITTED;
    }
}
