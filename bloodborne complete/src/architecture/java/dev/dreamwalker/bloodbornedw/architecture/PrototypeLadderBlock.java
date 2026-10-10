package dev.dreamwalker.bloodbornedw.architecture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.fluid.Fluids;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;

/** Experimental one-cell ladder: no source beehive properties or neighbor transformations. */
public final class PrototypeLadderBlock extends LadderBlock implements BlockEntityProvider {
    public static final IntProperty VARIANT = IntProperty.of("variant", 0, 2);
    public static final EnumProperty<Profile> PROFILE = EnumProperty.of("profile", Profile.class);
    public static final BooleanProperty DIAGONAL = BooleanProperty.of("diagonal");
    public static final BooleanProperty SOURCE_CLONE = BooleanProperty.of("source_clone");
    public static final BooleanProperty FREESTANDING = BooleanProperty.of("freestanding");
    private final int fixedVariant;
    private final VoxelShape[] collision;
    private final VoxelShape[] outline;
    private final VoxelShape[] sourcePhysical=new VoxelShape[8];

    public enum Profile implements StringIdentifiable {
        BASE("base"), ALT("alt");
        private final String name;
        Profile(String name) { this.name = name; }
        @Override public String asString() { return name; }
    }

    public PrototypeLadderBlock(Settings settings) {
        this(settings,-1);
    }
    public PrototypeLadderBlock(Settings settings,int fixedVariant) {
        super(settings.dynamicBounds());
        this.fixedVariant=fixedVariant;
        JsonObject geometry = geometry();
        collision = shapes(geometry.getAsJsonArray("northCollision"));
        outline = shapes(geometry.getAsJsonArray("northOutline"));
        for(int yaw=0;yaw<8;yaw++)sourcePhysical[yaw]=cheapRotate(new Box(0,0,13/16.0,1,1,1),yaw);
        setDefaultState(getDefaultState().with(VARIANT, Math.max(0,fixedVariant)).with(PROFILE, Profile.BASE).with(DIAGONAL, false).with(SOURCE_CLONE, false).with(FREESTANDING,false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder); builder.add(VARIANT, PROFILE, DIAGONAL, SOURCE_CLONE,FREESTANDING);
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return state.get(SOURCE_CLONE) ? new SourceLadderBlockEntity(pos,state) : new dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity(pos,state); }
    @Override public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if(state.get(SOURCE_CLONE) && world instanceof ServerWorld server) SourceLadderRuntime.remove(server,pos,player,!player.isCreative());
        super.onBreak(world,pos,state,player);
    }
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved) {
        if(state.get(SOURCE_CLONE) && (!next.isOf(this) || !next.get(SOURCE_CLONE)) && world instanceof ServerWorld server && !SourceLadderRuntime.writing()&&!dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.writing()) SourceLadderRuntime.partReplaced(server,pos,state);
        if(!state.isOf(next.getBlock())&&world instanceof ServerWorld server&&!dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.writing())dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.scheduleCleanup(server,pos);
        super.onStateReplaced(state,world,pos,next,moved);
        if(state.get(SOURCE_CLONE)&&next.isOf(this)&&!next.get(SOURCE_CLONE))world.removeBlockEntity(pos);
    }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.shifted(world,pos))return dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.baseShape()?VoxelShapes.empty():dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.cellShape(world,pos,false,state);
        return state.get(SOURCE_CLONE)?sourcePhysical[yaw(state)]:outline[yaw(state)];
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.shifted(world,pos))return dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.baseShape()?VoxelShapes.empty():dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.cellShape(world,pos,true,state,context);
        if(state.get(DIAGONAL)&&context instanceof net.minecraft.block.EntityShapeContext entity&&entity.getEntity() instanceof PlayerEntity
                &&(state.get(SOURCE_CLONE)||hasBackingMount(state,world,pos,0)))return VoxelShapes.empty();
        return state.get(SOURCE_CLONE)?sourcePhysical[yaw(state)]:collision[yaw(state)];
    }
    public VoxelShape climbingShape(BlockState state){return sourcePhysical[yaw(state)];}
    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos(); World world = context.getWorld();
        if (!world.isChunkLoaded(pos)) return null;
        if(context.getSide()==Direction.UP){
            BlockPos below=pos.down();
            if(!world.isChunkLoaded(below))return null;
            BlockState base=world.getBlockState(below);
            VoxelShape surface=base.getCollisionShape(world,below);
            // A grid section has no fractional mounting offset. Do not silently
            // suspend a new section above a bottom slab or bury it in a fence.
            if(base.getBlock() instanceof PrototypeLadderBlock
                    ||(!surface.isEmpty()&&Math.abs(surface.getMax(Direction.Axis.Y)-1)<1e-6)){
                int desired=(MathHelper.floor(context.getPlayerYaw()/45F+.5F))&7;
                return withYaw(getDefaultState().with(FREESTANDING,true),desired).with(WATERLOGGED,world.getFluidState(pos).getFluid()==Fluids.WATER);
            }
            return null;
        }
        if (context.getSide().getAxis().isHorizontal()) {
            // A real wall click is the primary attachment intent. Looking diagonally
            // at a flat wall must not demand an absent second wall.
            if(context.getPlayer()!=null){
                int desired=(MathHelper.floor(context.getPlayerYaw()/45F+.5F))&7;
                if((desired&1)!=0) {
                    // Resolve the actual two touching walls first. The old +180 camera
                    // conversion selected the opposite corner and fell back to a flat wall.
                    for(int distance:new int[]{0,2,-2,4}) {
                        BlockState diagonal=withYaw(getDefaultState(),desired+distance);
                        if(backingDirections(diagonal).contains(context.getSide().getOpposite())&&hasBackingMount(diagonal,world,pos,0))
                            return diagonal.with(WATERLOGGED,world.getFluidState(pos).getFluid()==Fluids.WATER);
                    }
                }
            }
            BlockState clicked=getDefaultState().with(FACING,context.getSide());
            if(clicked.canPlaceAt(world,pos))return clicked.with(WATERLOGGED,world.getFluidState(pos).getFluid()==Fluids.WATER);
            return null; // A clicked vertical face must have an actual attachment pad, not a fence's empty edge.
        }
        if (context.getPlayer() != null) {
            int desired = (MathHelper.floor(context.getPlayerYaw() / 45F + .5F) + 4) & 7;
            BlockState candidate = withYaw(getDefaultState(), desired);
            if (candidate.canPlaceAt(world, pos)) return candidate.with(WATERLOGGED, world.getFluidState(pos).getFluid() == Fluids.WATER);
        }
        for (Direction direction : context.getPlacementDirections()) {
            if (!direction.getAxis().isHorizontal()) continue;
            BlockState candidate = getDefaultState().with(FACING, direction.getOpposite());
            BlockPos support = pos.offset(direction);
            if (world.isChunkLoaded(support) && candidate.canPlaceAt(world, pos))
                return candidate.with(WATERLOGGED, world.getFluidState(pos).getFluid() == Fluids.WATER);
        }
        return null;
    }
    public static int yaw(BlockState state) {
        int turns = switch (state.get(FACING)) { case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> throw new IllegalArgumentException("Vertical ladder facing"); };
        return turns * 2 + (state.get(DIAGONAL) ? 1 : 0);
    }
    public static BlockState withYaw(BlockState state, int yaw) {
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        return state.with(FACING, directions[(yaw & 7) / 2]).with(DIAGONAL, (yaw & 1) != 0);
    }
    public BlockState step45(BlockState state) { return withYaw(state, yaw(state) + 1); }
    public static List<Direction> backingDirections(BlockState state) {
        Direction first = state.get(FACING).getOpposite();
        return state.get(DIAGONAL) ? List.of(first, first.rotateYClockwise()) : List.of(first);
    }
    /** Actual two mounting faces, including a fractional vertical move. No proximity test. */
    public static boolean hasBackingMount(BlockState state,BlockView world,BlockPos root,double offset) {
        if(!state.get(DIAGONAL))return false;
        for(Direction direction:backingDirections(state)) {
            VoxelShape available=VoxelShapes.empty();
            for(int dy=(int)Math.floor(offset);dy<(int)Math.ceil(offset+1);dy++) {
                BlockPos backing=root.offset(direction).up(dy);
                if(world instanceof World loaded&&!loaded.isChunkLoaded(backing))return false;
                VoxelShape actual=dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.nativeCollision(world,backing,ShapeContext.absent());
                available=VoxelShapes.union(available,faceProjection(actual,direction.getOpposite()).offset(0,dy-offset,0));
            }
            if(!ordinaryAttachment(available,direction))return false;
        }
        return true;
    }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        int yaw = yaw(state);
        return withYaw(state, mirror == BlockMirror.LEFT_RIGHT ? 4-yaw : mirror == BlockMirror.FRONT_BACK ? -yaw : yaw);
    }
    @Override public boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
        if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.rawQuery()&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.loadedEntity(world,pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity own&&own.payload().getBoolean("MountEdited"))return true;
        // Self-standing sections retain their existence after construction; removing one
        // section does not cascade through a vertical stack or turn it into a source clone.
        if(!state.get(SOURCE_CLONE)&&(state.get(FREESTANDING)||state.get(DIAGONAL)))return true;
        VoxelShape section = state.get(SOURCE_CLONE)?sourcePhysical[yaw(state)]:collision[yaw(state)];
        for (Direction direction : backingDirections(state)) {
            BlockPos backing = pos.offset(direction);
            if (world instanceof World loaded && !loaded.isChunkLoaded(backing)) return false;
            VoxelShape available = faceProjection(world.getBlockState(backing).getCollisionShape(world, backing), direction.getOpposite());
            if(state.get(SOURCE_CLONE)){
                VoxelShape required=faceProjection(section,direction);
                if(required.isEmpty()||VoxelShapes.matchesAnywhere(required,available,BooleanBiFunction.ONLY_FIRST))return false;
            }else if(!ordinaryAttachment(available,direction))return false;
        }
        return true;
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        if(!state.get(SOURCE_CLONE)&&(state.get(FREESTANDING)||state.get(DIAGONAL)))return state;
        if (!backingDirections(state).contains(direction)) return state;
        if (world instanceof World loaded) for (Direction backing : backingDirections(state)) if (!loaded.isChunkLoaded(pos.offset(backing))) return state;
        return state.canPlaceAt(world, pos) ? state : (state.get(WATERLOGGED) ? net.minecraft.block.Blocks.WATER.getDefaultState() : net.minecraft.block.Blocks.AIR.getDefaultState());
    }
    @Override public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) { return artisticStack(state); }
    @Override public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder context) {
        if(state.get(SOURCE_CLONE)) return List.of(); // The shared owner drops one normal item for either part.
        return List.of(artisticStack(state));
    }
    public ItemStack artisticStack(BlockState state) { ItemStack stack = new ItemStack(PrototypeArchitecture.ladderItem(0)); copyArt(state, stack); return stack; }
    public static int artVariant(BlockState state){return state.getBlock() instanceof PrototypeLadderBlock block&&block.fixedVariant>=0?block.fixedVariant:state.get(VARIANT);}
    public int fixedVariant(){return fixedVariant;}
    private static void copyArt(BlockState state, ItemStack stack) {
        NbtCompound tag = stack.getOrCreateSubNbt("BlockStateTag");
        tag.putString("variant", "0");
        tag.putString("profile", state.get(PROFILE).asString());
    }
    private static JsonObject geometry() {
        try (InputStream stream = PrototypeLadderBlock.class.getResourceAsStream("/bloodborne_dw/prototype-ladder.json")) {
            if (stream == null) throw new IllegalStateException("Missing prototype ladder geometry");
            JsonObject document = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (document.get("schemaVersion").getAsInt() != 1 || document.get("units").getAsInt() != 16)
                throw new IllegalStateException("Unsupported prototype ladder geometry");
            return document;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read prototype ladder geometry", failure); }
    }
    private static VoxelShape[] shapes(JsonArray boxes) {
        if (boxes == null || boxes.isEmpty() || boxes.size() > 5) throw new IllegalArgumentException("Invalid ladder collision budget");
        VoxelShape[] result = new VoxelShape[8];
        for (int yaw = 0; yaw < result.length; yaw++) {
            VoxelShape shape = VoxelShapes.empty();
            for (var value : boxes) {
                JsonArray source = value.getAsJsonArray();
                if (source.size() != 6) throw new IllegalArgumentException("Invalid ladder box");
                Box box = new Box(source.get(0).getAsDouble() / 16, source.get(1).getAsDouble() / 16, source.get(2).getAsDouble() / 16,
                        source.get(3).getAsDouble() / 16, source.get(4).getAsDouble() / 16, source.get(5).getAsDouble() / 16);
                shape = VoxelShapes.union(shape, cheapRotate(box, yaw));
            }
            result[yaw] = shape.simplify();
        }
        return result;
    }
    /** One cached simple box for a section, including approximate diagonal poses. */
    private static VoxelShape cheapRotate(Box box,int yaw){
        if((yaw&1)==0){Box b=box.rotate(yaw/2);return VoxelShapes.cuboid(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
        double c=Math.cos(yaw*Math.PI/4),s=Math.sin(yaw*Math.PI/4),minX=1,minZ=1,maxX=0,maxZ=0;
        for(double x:new double[]{box.minX(),box.maxX()})for(double z:new double[]{box.minZ(),box.maxZ()}){
            double rx=.5+c*(x-.5)-s*(z-.5),rz=.5+s*(x-.5)+c*(z-.5);
            minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minZ=Math.min(minZ,rz);maxZ=Math.max(maxZ,rz);
        }
        return VoxelShapes.cuboid(Math.max(0,minX),box.minY(),Math.max(0,minZ),Math.min(1,maxX),box.maxY(),Math.min(1,maxZ));
    }
    /** A mount needs a real centered pad on either half, not an entire cube face. */
    private static boolean ordinaryAttachment(VoxelShape available,Direction face){
        if(available.isEmpty())return false;
        for(double y:new double[]{.125,.625}){
            VoxelShape pad=face.getAxis()==Direction.Axis.X?VoxelShapes.cuboid(0,y,.4375,1,y+.25,.5625):VoxelShapes.cuboid(.4375,y,0,.5625,y+.25,1);
            if(!VoxelShapes.matchesAnywhere(pad,available,BooleanBiFunction.ONLY_FIRST))return true;
        }
        return false;
    }
    /** Project only a real touching face, never the block's overall bounding box. */
    private static VoxelShape faceProjection(VoxelShape shape, Direction face) {
        VoxelShape result = VoxelShapes.empty();
        for (net.minecraft.util.math.Box box : shape.getBoundingBoxes()) {
            double boundary = switch(face) { case NORTH -> box.minZ; case SOUTH -> box.maxZ; case WEST -> box.minX; case EAST -> box.maxX; default -> throw new IllegalArgumentException("Horizontal support required"); };
            double target = face == Direction.NORTH || face == Direction.WEST ? 0 : 1;
            if (Math.abs(boundary - target) > 1e-6) continue;
            VoxelShape patch = face.getAxis() == Direction.Axis.X ? VoxelShapes.cuboid(0,box.minY,box.minZ,1,box.maxY,box.maxZ)
                : VoxelShapes.cuboid(box.minX,box.minY,0,box.maxX,box.maxY,1);
            result = VoxelShapes.union(result, patch);
        }
        return result;
    }
}
