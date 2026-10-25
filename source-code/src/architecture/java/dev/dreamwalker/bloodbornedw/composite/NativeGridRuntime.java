package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;

/** Only local gameplay operations. There are no placed-cell maps or load hooks. */
public final class NativeGridRuntime {
    private static final ThreadLocal<Boolean> WRITING = ThreadLocal.withInitial(() -> false);
    private NativeGridRuntime() {}

    public static void initialize() {
        // Player removal runs after Fabric BEFORE in NativePlayerBreakMixin.
    }
    public static boolean writing() { return WRITING.get(); }
    private static boolean mayModify(World world, PlayerEntity player, BlockPos pos) {
        return player == null || player.getAbilities().allowModifyWorld && world.canPlayerModifyAt(player, pos);
    }

    public static ItemStack item(int index) {
        NativeGridCatalog.Membership membership = NativeGridCatalog.membership(index);
        if (membership != null) index = membership.object().baseCell();
        if(membership==null)return NativePhysicsBlock.item(index);
        BlockState state = NativeGridCatalog.state(index);
        ItemStack stack = new ItemStack(state.getBlock());
        stack.getOrCreateSubNbt("BlockStateTag").putString("part", Integer.toString(index%128));
        return stack;
    }

    public static boolean canPlace(ItemPlacementContext context, int index) {
        NativeGridCatalog.Membership membership = NativeGridCatalog.membership(index);
        List<NativeGridCatalog.Member> members = membership == null
                ? List.of(new NativeGridCatalog.Member(BlockPos.ORIGIN, index)) : membership.object().cells();
        // A helper item's finite type is never placed as an orphan.
        if (membership != null && !membership.offset().equals(BlockPos.ORIGIN)) return false;
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        for (NativeGridCatalog.Member member : members) {
            BlockPos pos = context.getBlockPos().add(member.offset());
            if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) return false;
            if (!mayModify(world, player, pos)) return false;
            BlockState previous = world.getBlockState(pos), next = NativeGridCatalog.state(member.cell());
            if (world.getBlockEntity(pos) != null || !previous.canReplace(context)) return false;
            if (!world.canPlace(next, pos, player == null ? ShapeContext.absent() : ShapeContext.of(player))) return false;
        }
        return true;
    }

    public static ActionResult placeWhole(ItemPlacementContext context, int index) {
        var membership = NativeGridCatalog.membership(index);
        if (membership == null || !canPlace(context, index)) return ActionResult.FAIL;
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        LinkedHashMap<BlockPos, BlockState> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        for (var member : membership.object().cells()) {
            BlockPos pos = context.getBlockPos().add(member.offset());
            before.put(pos, world.getBlockState(pos));
            after.put(pos, NativeGridCatalog.state(member.cell()));
        }
        if (!write(world, before, after)) return ActionResult.FAIL;
        BlockState base = NativeGridCatalog.state(membership.object().baseCell());
        PlayerEntity player = context.getPlayer();
        var sound = base.getSoundGroup();
        world.playSound(player, context.getBlockPos(), sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * .8F);
        world.emitGameEvent(GameEvent.BLOCK_PLACE, context.getBlockPos(), GameEvent.Emitter.of(player, base));
        if (player == null || !player.getAbilities().creativeMode) context.getStack().decrement(1);
        return ActionResult.CONSUME;
    }

    public static boolean breakWhole(World world, BlockPos clicked, int index, PlayerEntity player, boolean drop) {
        if (world.isClient || WRITING.get()) return false;
        var membership = NativeGridCatalog.membership(index);
        if (membership == null) return false;
        BlockPos base = clicked.subtract(membership.offset());
        LinkedHashMap<BlockPos, BlockState> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        for (var member : membership.object().cells()) {
            BlockPos pos = base.add(member.offset());
            if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos) || !mayModify(world, player, pos)) return false;
            // Mutation-time loading of the finite template is independent of owners/ledgers.
            world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            BlockState current = world.getBlockState(pos);
            if (!current.equals(NativeGridCatalog.state(member.cell())) || world.getBlockEntity(pos) != null) return false;
            before.put(pos, current);
            after.put(pos, current.getFluidState().isEmpty() ? Blocks.AIR.getDefaultState() : current.getFluidState().getBlockState());
        }
        if (world instanceof net.minecraft.server.world.ServerWorld server&&!NativePlayerBreak.allow(server,player,before.keySet()))return false;
        if (!write(world, before, after)) return false;
        if (drop && (player == null || !player.getAbilities().creativeMode)) Block.dropStack(world, base, item(membership.object().baseCell()));
        return true;
    }

    /** Replacement/destruction visits only this finite object's footprint,
     * loading its neighbour chunks once so crossing a chunk edge stays whole. */
    public static void replaced(World world, BlockPos changed, int index) {
        if (world.isClient || WRITING.get()) return;
        var membership = NativeGridCatalog.membership(index);
        if (membership == null) return;
        BlockPos base = changed.subtract(membership.offset());
        Set<Long> chunks = new HashSet<>();
        for (var member : membership.object().cells()) {
            BlockPos pos = base.add(member.offset());
            long chunk = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunks.add(chunk)) world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
        LinkedHashMap<BlockPos, BlockState> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        for (var member : membership.object().cells()) {
            BlockPos pos = base.add(member.offset());
            if (pos.equals(changed)) continue;
            BlockState current = world.getBlockState(pos);
            // Never delete an independently edited or foreign block.
            if (!current.equals(NativeGridCatalog.state(member.cell()))) continue;
            before.put(pos, current);
            after.put(pos, current.getFluidState().isEmpty() ? Blocks.AIR.getDefaultState() : current.getFluidState().getBlockState());
        }
        if (!after.isEmpty() && !write(world, before, after))
            throw new IllegalStateException("Could not complete whole-object replacement at " + base);
    }

    private static boolean write(World world, Map<BlockPos, BlockState> before, Map<BlockPos, BlockState> after) {
        WRITING.set(true);
        try {
            for (var entry : after.entrySet()) {
                if (!world.setBlockState(entry.getKey(), entry.getValue(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS)
                        && !world.getBlockState(entry.getKey()).equals(entry.getValue()))
                    throw new IllegalStateException("Native whole-object state write rejected");
            }
            for (var entry : after.entrySet()) world.updateNeighbors(entry.getKey(), entry.getValue().getBlock());
            return true;
        } catch (RuntimeException failure) {
            RuntimeException rollbackFailure = null;
            for (var entry : before.entrySet()) try {
                if (!world.setBlockState(entry.getKey(), entry.getValue(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS)
                        && !world.getBlockState(entry.getKey()).equals(entry.getValue()))
                    throw new IllegalStateException("Native whole-object rollback rejected");
            } catch (RuntimeException rollback) { rollbackFailure = rollback; }
            if (rollbackFailure != null) { failure.addSuppressed(rollbackFailure); throw failure; }
            return false;
        } finally { WRITING.remove(); }
    }

    public static Optional<BlockPos> climbing(World world, Box body) {
        // Fixed per-cell climbing shapes; no allocation or catalog scan for ordinary air.
        for (BlockPos pos : BlockPos.iterate(MathHelper.floor(body.minX), MathHelper.floor(body.minY), MathHelper.floor(body.minZ),
                MathHelper.floor(body.maxX), MathHelper.floor(body.maxY), MathHelper.floor(body.maxZ))) {
            BlockState state = world.getBlockState(pos);
            boolean ordinary=state.getBlock() instanceof NativeCellBlock;
            if(!ordinary&&!NativeGridBridge.carrier(state))continue;
            int bridge=ordinary?-1:NativeGridBridge.base(world,pos);
            if(!ordinary&&bridge<0)continue;
            var shape = bridge>=0?NativeGridCatalog.cell(bridge).climbing():((NativeCellBlock)state.getBlock()).cell(state).climbing();
            if (shape.isEmpty()) continue;
            for (Box box : shape.getBoundingBoxes()) if (box.offset(pos).intersects(body)) return Optional.of(pos.toImmutable());
        }
        return Optional.empty();
    }
}
