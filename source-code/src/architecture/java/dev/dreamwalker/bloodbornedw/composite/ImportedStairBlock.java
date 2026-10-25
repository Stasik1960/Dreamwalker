package dev.dreamwalker.bloodbornedw.composite;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
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
public class ImportedStairBlock extends CompositeRootBlock implements NewStateStorage {
    public static final BooleanProperty TRIM = BooleanProperty.of("trim");
    public static final BooleanProperty WATERLOGGED = net.minecraft.state.property.Properties.WATERLOGGED;
    public static final EnumProperty<Connections> CONNECTIONS = EnumProperty.of("connections", Connections.class);
    public enum Connections implements StringIdentifiable {
        AUTO("auto"), MANUAL("manual");
        private final String name;
        Connections(String name) { this.name = name; }
        @Override public String asString() { return name; }
    }
    private static final class Work {
        final LinkedHashMap<Long,LinkedHashSet<BlockPos>> chunks=new LinkedHashMap<>();
        long pending,processed,deferred,transitions,maxDrainNanos;
        void add(BlockPos pos){long key=net.minecraft.util.math.ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4);if(chunks.computeIfAbsent(key,ignored->new LinkedHashSet<>()).add(pos.toImmutable()))pending++;}
        BlockPos poll(){var iterator=chunks.entrySet().iterator();var next=iterator.next();long key=next.getKey();var positions=next.getValue();iterator.remove();var cells=positions.iterator();BlockPos pos=cells.next();cells.remove();pending--;if(!positions.isEmpty())chunks.put(key,positions);return pos;}
    }
    private static final Map<ServerWorld, Work> PENDING = new WeakHashMap<>();
    private static boolean initialized;

    public ImportedStairBlock(CompositeSpec spec) {
        super(spec);
        BlockState state = getDefaultState().with(CONNECTIONS, Connections.AUTO);
        if (state.contains(TRIM)) state = state.with(TRIM, false);
        if (state.contains(WATERLOGGED)) state = state.with(WATERLOGGED, false);
        setDefaultState(ImportedStairShapes.normalize(state));
    }
    public static ImportedStairBlock create(CompositeSpec spec) {
        return spec.id.getPath().equals("source_stairs_polished_deepslate_stairs_bottom") ? new Trimmed(spec) : new ImportedStairBlock(spec);
    }
    private static final class Trimmed extends ImportedStairBlock {
        private Trimmed(CompositeSpec spec) { super(spec); }
        @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { super.appendProperties(builder); builder.add(TRIM,WATERLOGGED); }
    }
    public static boolean isStairPath(String path) { return ImportedStairShapes.isStairPath(path); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { super.appendProperties(builder); builder.add(CONNECTIONS); }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_WORLD_TICK.register(ImportedStairBlock::drain);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            // Use the event chunk directly; never synchronously ask World for a loading chunk.
            for (var entity : chunk.getBlockEntities().values()) if (entity.getCachedState().getBlock() instanceof ImportedStairBlock) scheduleNearby(world, entity.getPos());
            // A border stair retained its saved shape while this adjacent
            // chunk was absent. Retry only the attached neighbour's border.
            for(Direction side:Direction.Type.HORIZONTAL){
                var neighbour=world.getChunkManager().getWorldChunk(chunk.getPos().x+side.getOffsetX(),chunk.getPos().z+side.getOffsetZ());
                if(neighbour==null)continue;
                for(var entity:neighbour.getBlockEntities().values())if(entity.getCachedState().getBlock() instanceof ImportedStairBlock){
                    BlockPos pos=entity.getPos();boolean border=side==Direction.EAST?(pos.getX()&15)==0:side==Direction.WEST?(pos.getX()&15)==15:side==Direction.SOUTH?(pos.getZ()&15)==0:(pos.getZ()&15)==15;
                    if(border)scheduleNearby(world,pos);
                }
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{Work work=PENDING.get(world);if(work!=null){var cells=work.chunks.remove(chunk.getPos().toLong());if(cells!=null)work.pending-=cells.size();}});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server,world)->PENDING.remove(world));
    }

    public static BlockState placementState(BlockState state, ItemPlacementContext context) {
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state;
        BlockHalf half = context.getSide() == Direction.DOWN || context.getSide() != Direction.UP && context.getHitPos().y - context.getBlockPos().getY() > .5 ? BlockHalf.TOP : BlockHalf.BOTTOM;
        state = ImportedStairShapes.withHalf(ImportedStairShapes.withShape(state, StairShape.STRAIGHT), half).with(ROTATION, ImportedStairShapes.rotation(context.getHorizontalPlayerFacing())).with(CONNECTIONS, Connections.AUTO);
        return connected(state, context.getWorld(), context.getBlockPos());
    }

    /** Placement and rotate/tool operations retain every existing 45-degree pose. */
    public static BlockState connected(BlockState state, World world, BlockPos pos) {
        if (!(state.getBlock() instanceof ImportedStairBlock)) return state;
        if (state.get(CONNECTIONS) == Connections.MANUAL) return ImportedStairShapes.normalize(state);
        if ((state.get(ROTATION) & 1) != 0) return ImportedStairShapes.normalize(state);
        // ServerWorld.getBlockState may synchronously complete a neighbouring
        // FULL future. isChunkLoaded only checks its ticket level; getWorldChunk
        // is the verified nonblocking getNow accessor on ServerChunkManager.
        if(world instanceof ServerWorld server)for(Direction side:Direction.Type.HORIZONTAL){BlockPos neighbour=pos.offset(side);if(server.getChunkManager().getWorldChunk(neighbour.getX()>>4,neighbour.getZ()>>4)==null)return ImportedStairShapes.normalize(state);}
        Direction facing = ImportedStairShapes.facing(state);
        BlockState ahead = world.getBlockState(pos.offset(facing));
        if (compatible(state, ahead)) {
            Direction other = facing(ahead);
            if (other.getAxis() != facing.getAxis() && different(state, world.getBlockState(pos.offset(other.getOpposite()))))
                return ImportedStairShapes.withShape(state, other == facing.rotateYCounterclockwise() ? StairShape.OUTER_LEFT : StairShape.OUTER_RIGHT);
        }
        BlockState behind = world.getBlockState(pos.offset(facing.getOpposite()));
        if (compatible(state, behind)) {
            Direction other = facing(behind);
            if (other.getAxis() != facing.getAxis() && different(state, world.getBlockState(pos.offset(other))))
                return ImportedStairShapes.withShape(state, other == facing.rotateYCounterclockwise() ? StairShape.INNER_LEFT : StairShape.INNER_RIGHT);
        }
        return ImportedStairShapes.withShape(state, StairShape.STRAIGHT);
    }

    private static boolean stair(BlockState state) { return state.getBlock() instanceof StairsBlock || state.getBlock() instanceof ImportedStairBlock && (state.get(ROTATION) & 1) == 0; }
    private static Direction facing(BlockState state) { return state.getBlock() instanceof ImportedStairBlock ? ImportedStairShapes.facing(state) : state.get(StairsBlock.FACING); }
    private static BlockHalf half(BlockState state) { return state.getBlock() instanceof ImportedStairBlock ? ImportedStairShapes.half(state) : state.get(StairsBlock.HALF); }
    private static boolean compatible(BlockState own, BlockState other) { return stair(other) && half(other) == ImportedStairShapes.half(own); }
    private static boolean different(BlockState own, BlockState other) { return !stair(other) || facing(other) != ImportedStairShapes.facing(own) || half(other) != ImportedStairShapes.half(own); }

    public static void scheduleNearby(ServerWorld world, BlockPos pos) {
        Work pending = PENDING.computeIfAbsent(world, ignored -> new Work());
        pending.add(pos);
        for (Direction side : Direction.Type.HORIZONTAL) pending.add(pos.offset(side));
    }

    private static void drain(ServerWorld world) {
        Work tasks = PENDING.get(world);
        if (tasks == null||tasks.pending==0) return;
        long started=System.nanoTime(),initial=tasks.pending;int processed=0;
        try{while(tasks.pending>0&&processed<initial&&processed<512&&(processed==0||System.nanoTime()-started<2_000_000L)) {
            BlockPos pos=tasks.poll();processed++;tasks.processed++;
            var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null){if(world.isChunkLoaded(pos)){tasks.add(pos);tasks.deferred++;}continue;}
            BlockState current=chunk.getBlockState(pos);
            if (!(current.getBlock() instanceof ImportedStairBlock)||!(chunk.getBlockEntity(pos) instanceof CompositeBlockEntity entity)||entity.resident()==null)continue;
            BlockState next=connected(current,world,pos);
            if(next.equals(current))continue;
            // A legacy migration can defer until its other owned cells load.
            // Never reinterpret an old source VARIANT before its alias mapping.
            if (ImportedTypeAliases.needsMigration(current, entity.payload())) {
                scheduleNearby(world, pos);
                continue;
            }
            if (!next.equals(current)) {
                tasks.transitions++;
                var result = CompositeRuntime.transitionImportedStair(world, entity.resident(), next);
                if (result.outcome() != dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED) scheduleNearby(world, pos);
            }
        }}finally{tasks.maxDrainNanos=Math.max(tasks.maxDrainNanos,System.nanoTime()-started);}
    }
    public static Map<String,Object> streamingMetrics(ServerWorld world){Work work=PENDING.get(world);return work==null?Map.of("pendingStairPositions",0L):Map.of("pendingStairPositions",work.pending,"pendingStairChunks",work.chunks.size(),"stairProcessed",work.processed,"stairDeferred",work.deferred,"stairTransitions",work.transitions,"stairMaxDrainNanos",work.maxDrainNanos);}

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
        if (ImportedTypeAliases.needsMigration(state, payload)) {
            NbtCompound transformed = ImportedTypeAliases.transformPayload(state, payload);
            BlockState canonical = ImportedTypeAliases.target(state);
            return ((CompositeRootBlock) canonical.getBlock()).art(canonical, transformed);
        }
        ItemStack item = super.art(ImportedStairShapes.normalize(state), payload);
        NbtCompound tag = item.getOrCreateSubNbt("BlockStateTag");
        tag.putString("half", ImportedStairShapes.half(state).asString());
        tag.putString("shape", ImportedStairShapes.shape(state).asString());
        if (state.contains(TRIM)) tag.putString("trim", Boolean.toString(state.get(TRIM)));
        tag.putString("connections", state.get(CONNECTIONS).asString());
        item.getOrCreateNbt().putInt("ImportedTypeVersion", 1);
        return item;
    }

    public static BlockState activateAutomatic(BlockState state, World world, BlockPos pos) {
        return state.getBlock() instanceof ImportedStairBlock ? connected(state.with(CONNECTIONS, Connections.AUTO), world, pos) : state;
    }
    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!player.isSneaking() || !player.getStackInHand(hand).isEmpty()) return super.onUse(state, world, pos, player, hand, hit);
        boolean activate = state.get(CONNECTIONS) == Connections.MANUAL;
        if (!activate && !state.contains(TRIM)) return super.onUse(state, world, pos, player, hand, hit);
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity) || entity.resident() == null) return ActionResult.FAIL;
        if (ImportedTypeAliases.needsMigration(state, entity.payload())) return ActionResult.FAIL;
        // The legacy manual corner first resumes automatic connections. A later
        // action on the sole trimmed NEW type cycles its small appearance plates.
        BlockState next = activate ? activateAutomatic(state, world, pos) : state.with(TRIM, !state.get(TRIM));
        var result = CompositeRuntime.transition((ServerWorld)world, entity.resident(), next, player);
        return result.outcome() == dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED ? ActionResult.CONSUME : ActionResult.FAIL;
    }
}
