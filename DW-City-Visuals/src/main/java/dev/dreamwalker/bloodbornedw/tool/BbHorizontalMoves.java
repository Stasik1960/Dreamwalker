package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.BuildPermissions;
import dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.FabricCompositeWorld;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import dev.dreamwalker.bloodbornedw.link.ArchitectureReferenceMigration;
import dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class BbHorizontalMoves {
    public static final String MODE_KEY = "BBHorizontalMode";
    public static final int LEFT = 1, RIGHT = 2;

    private BbHorizontalMoves() {}

    public static int mode(ItemStack stack) {
        if (!(stack.getItem() instanceof BbToolItem) || !stack.hasNbt()
                || BbToolItem.mode(stack) != BbToolItem.Mode.ROTATE) return 0;
        int value = stack.getNbt().getInt(MODE_KEY);
        return value == LEFT || value == RIGHT ? value : 0;
    }

    public static String title(int mode) {
        return mode == LEFT ? "Сдвиг влево на 1 блок" : "Сдвиг вправо на 1 блок";
    }

    public static boolean shift(ServerPlayerEntity player, ToolTarget target, int mode) {
        if (target == null || target.kind() != ToolTarget.Kind.ARCHITECTURE
                || !BbToolService.canEdit(player) || !target.valid(player.getServer())) return false;
        ServerWorld world = player.getServerWorld();
        if (target.world(player.getServer()) != world) return false;
        BlockPos from = BlockPos.fromLong(target.root());
        var facing = player.getHorizontalFacing();
        BlockPos to = from.offset(mode == LEFT ? facing.rotateYCounterclockwise() : facing.rotateYClockwise());
        if (!editable(world, player, from) || !editable(world, player, to)
                || FauxWaterlogging.logged(world, from) || FauxWaterlogging.logged(world, to)
                || !CityEditorServer.canQueueAppearance(world, to)) return false;
        if (!(world.getBlockEntity(from) instanceof CompositeBlockEntity root)) return false;
        var owner = VerticalMount.owner(world, from);
        if (owner == null || !owner.equals(root.resident())) return false;
        var block = world.getBlockState(from);
        if (FauxWaterlogging.wet(block)) return false;
        var before = CompositeRuntime.instance(world, from, block, owner.instanceId(), root.payload());
        var after = CompositeRuntime.instance(world, to, block, owner.instanceId(), root.payload());
        if (!editableFootprint(world, player, before) || !editableFootprint(world, player, after)) return false;

        var refs = ArchitectureReferenceMigration.prepare(player.getServer(), Map.of(
                CompositeMechanismBridge.reference(world, owner),
                CompositeMechanismBridge.reference(world, after.owner())));
        var access = new FabricCompositeWorld(world, Map.of(after.owner(), after)) {
            private boolean restoring;
            @Override public boolean allowsReviewedOverlap(ObjectInstance incoming, ObjectInstance existing, Cell cell) {
                return incoming.owner().equals(after.owner());
            }

            @Override public boolean restoreSilently(Cell cell, CellSnapshot previous) {
                restoring = true;
                refs.rollback();
                return super.restoreSilently(cell, previous);
            }

            @Override public void publishCommitted(List<Cell> cells) {
                if (!restoring) refs.commit();
                super.publishCommitted(cells);
            }
        };
        var transition = CompositeRuntime.core().transition(access, owner, after, List.of());
        if (!transition.accepted()) return false;
        int paint = CityEditorServer.storedAppearance(world, from);
        CityEditorServer.beginPlacement();
        boolean committed = false;
        try {
            committed = CompositeRuntime.execute(access, transition).outcome() == TransactionCore.Outcome.COMMITTED;
            if (!committed) refs.rollback();
        } finally {
            CityEditorServer.endPlacement(committed);
        }
        if (committed && paint >= 0) CityEditorServer.queueAppearance(world, to, paint);
        return committed;
    }

    private static boolean editableFootprint(ServerWorld world, ServerPlayerEntity player, ObjectInstance object) {
        return object.cells().keySet().stream().allMatch(offset ->
                editable(world, player, CompositeData.pos(object.owner().root().add(offset))));
    }

    private static boolean editable(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        return world.isChunkLoaded(pos) && world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos)
                && BuildPermissions.canEdit(world, player, pos);
    }
}
