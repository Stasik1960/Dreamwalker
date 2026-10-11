package dev.dreamwalker.bloodbornedw.composite;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** One item and type for each new native stair appearance; corners follow neighbours. */
public final class ImportedStairBlock extends CompositeRootBlock {
    public static final EnumProperty<BlockHalf> HALF = EnumProperty.of("half", BlockHalf.class);
    public static final EnumProperty<StairShape> SHAPE = EnumProperty.of("shape", StairShape.class);
    public static final BooleanProperty TRIM = BooleanProperty.of("trim");
    public static final EnumProperty<Connections> CONNECTIONS = EnumProperty.of("connections", Connections.class);
    public enum Connections implements StringIdentifiable {
        AUTO("auto"), MANUAL("manual");
        private final String name;
        Connections(String name) { this.name = name; }
        @Override public String asString() { return name; }
    }
    private static final Map<ServerWorld, Set<BlockPos>> PENDING = new WeakHashMap<>();
    private static boolean initialized;

    public ImportedStairBlock(CompositeSpec spec) {
        super(spec);
        setDefaultState(ImportedStairShapes.normalize(getDefaultState().with(HALF, BlockHalf.BOTTOM).with(SHAPE, StairShape.STRAIGHT).with(TRIM, false).with(CONNECTIONS, Connections.AUTO)));
    }
    public static boolean isStairPath(String path) { return ImportedStairShapes.isStairPath(path); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { super.appendProperties(builder); builder.add(HALF, SHAPE, TRIM, CONNECTIONS); }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_WORLD_TICK.register(ImportedStairBlock::drain);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            // Use the event chunk directly; never synchronously ask World for a loading chunk.
            for (var entity : chunk.getBlockEntities().values()) if (entity.getCachedState().getBlock() instanceof ImportedStairBlock) scheduleNearby(world, entity.getPos());
        });
    }

    public static BlockState placementState(BlockState state, ItemPlacementContext context) {
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state;
        BlockHalf half = context.getSide() == Direction.DOWN || context.getSide() != Direction.UP && context.getHitPos().y - context.getBlockPos().getY() > .5 ? BlockHalf.TOP : BlockHalf.BOTTOM;
        state = state.with(ROTATION, ImportedStairShapes.rotation(context.getHorizontalPlayerFacing())).with(HALF, half).with(SHAPE, StairShape.STRAIGHT).with(CONNECTIONS, Connections.AUTO);
        return connected(state, context.getWorld(), context.getBlockPos());
    }

    /** Placement and rotate/tool operations retain every existing 45-degree pose. */
    public static BlockState connected(BlockState state, World world, BlockPos pos) {
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state;
        if (state.get(CONNECTIONS) == Connections.MANUAL) return ImportedStairShapes.normalize(state);
        if ((state.get(ROTATION) & 1) != 0) return ImportedStairShapes.normalize(state);
        Direction facing = ImportedStairShapes.facing(state);
        BlockState ahead = world.getBlockState(pos.offset(facing));
        if (compatible(state, ahead)) {
            Direction other = facing(ahead);
            if (other.getAxis() != facing.getAxis() && different(state, world.getBlockState(pos.offset(other.getOpposite()))))
                return ImportedStairShapes.normalize(state.with(SHAPE, other == facing.rotateYCounterclockwise() ? StairShape.OUTER_LEFT : StairShape.OUTER_RIGHT));
        }
        BlockState behind = world.getBlockState(pos.offset(facing.getOpposite()));
        if (compatible(state, behind)) {
            Direction other = facing(behind);
            if (other.getAxis() != facing.getAxis() && different(state, world.getBlockState(pos.offset(other))))
                return ImportedStairShapes.normalize(state.with(SHAPE, other == facing.rotateYCounterclockwise() ? StairShape.INNER_LEFT : StairShape.INNER_RIGHT));
        }
        return ImportedStairShapes.normalize(state.with(SHAPE, StairShape.STRAIGHT));
    }

    private static boolean stair(BlockState state) { return state.getBlock() instanceof StairsBlock || state.getBlock() instanceof ImportedStairBlock && (state.get(ROTATION) & 1) == 0; }
    private static Direction facing(BlockState state) { return state.getBlock() instanceof ImportedStairBlock ? ImportedStairShapes.facing(state) : state.get(StairsBlock.FACING); }
    private static BlockHalf half(BlockState state) { return state.getBlock() instanceof ImportedStairBlock ? state.get(HALF) : state.get(StairsBlock.HALF); }
    private static boolean compatible(BlockState own, BlockState other) { return stair(other) && half(other) == own.get(HALF); }
    private static boolean different(BlockState own, BlockState other) { return !stair(other) || facing(other) != ImportedStairShapes.facing(own) || half(other) != own.get(HALF); }

    public static void scheduleNearby(ServerWorld world, BlockPos pos) {
        Set<BlockPos> pending = PENDING.computeIfAbsent(world, ignored -> new LinkedHashSet<>());
        pending.add(pos.toImmutable());
        for (Direction side : Direction.Type.HORIZONTAL) pending.add(pos.offset(side));
    }

    private static void drain(ServerWorld world) {
        Set<BlockPos> tasks = PENDING.remove(world);
        if (tasks == null) return;
        for (BlockPos pos : tasks) {
            if (!world.isChunkLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof ImportedStairBlock)
                    || !(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity) || entity.resident() == null) continue;
            BlockState current = world.getBlockState(pos), next = connected(current, world, pos);
            if (!next.equals(current)) {
                var result = CompositeRuntime.transitionImportedStair(world, entity.resident(), next);
                if (result.outcome() != dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED) scheduleNearby(world, pos);
            }
        }
    }

    @Override public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world instanceof ServerWorld server) scheduleNearby(server, pos);
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction side, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (world instanceof ServerWorld server) scheduleNearby(server, pos);
        return super.getStateForNeighborUpdate(state, side, neighbor, world, pos, neighborPos);
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (world instanceof ServerWorld server) scheduleNearby(server, pos);
        super.onStateReplaced(state, world, pos, next, moved);
    }
    @Override public ItemStack art(BlockState state, NbtCompound payload) {
        ItemStack item = super.art(ImportedStairShapes.normalize(state), payload);
        NbtCompound tag = item.getOrCreateSubNbt("BlockStateTag");
        tag.putString("half", state.get(HALF).asString());
        tag.putString("shape", state.get(SHAPE).asString());
        tag.putString("trim", Boolean.toString(state.get(TRIM)));
        tag.putString("connections", state.get(CONNECTIONS).asString());
        item.getOrCreateNbt().putInt("ImportedTypeVersion", 1);
        return item;
    }

    public static BlockState activateAutomatic(BlockState state, World world, BlockPos pos) {
        return state.getBlock() instanceof ImportedStairBlock ? connected(state.with(CONNECTIONS, Connections.AUTO), world, pos) : state;
    }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (state.get(CONNECTIONS) != Connections.MANUAL || !player.isSneaking() || !player.getStackInHand(hand).isEmpty()) return super.onUse(state, world, pos, player, hand, hit);
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity) || entity.resident() == null) return ActionResult.FAIL;
        var result = CompositeRuntime.transition((ServerWorld)world, entity.resident(), activateAutomatic(state, world, pos), player);
        return result.outcome() == dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED ? ActionResult.CONSUME : ActionResult.FAIL;
    }
}
