package dev.dreamwalker.bloodbornedw.architecture.wall;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/** Native WallBlock topology, authored art and independent bounded cached physics. */
public class PrototypeWallBlock extends WallBlock implements net.minecraft.block.BlockEntityProvider {
    public static final EnumProperty<WallShape> NORTH=NORTH_SHAPE,EAST=EAST_SHAPE,SOUTH=SOUTH_SHAPE,WEST=WEST_SHAPE;
    public static final BooleanProperty POST=UP;
    public static final IntProperty MATERIAL=IntProperty.of("material",0,7), ROTATION=IntProperty.of("rotation",0,7);
    public static final EnumProperty<Profile> PROFILE=EnumProperty.of("profile",Profile.class);
    public static final EnumProperty<Connections> CONNECTIONS=EnumProperty.of("connections",Connections.class);
    private static final WallGeometry geometry=new WallGeometry();
    public static int diagnosticJunctionCacheSize(){return WallStackGeometry.cacheSize();}
    /** Legacy item predicate/authoring helper only: no shared height property exists. */
    public enum Course implements StringIdentifiable { LOW("low"),TALL("tall"); private final String name;Course(String name){this.name=name;}public String asString(){return name;} }
    public enum Profile implements StringIdentifiable { BASE("base"),ALT("alt");private final String name;Profile(String name){this.name=name;}public String asString(){return name;} }
    public enum Connections implements StringIdentifiable { AUTO("auto"),MANUAL("manual");private final String name;Connections(String name){this.name=name;}public String asString(){return name;} }
    public PrototypeWallBlock(Settings settings) {
        super(settings.dynamicBounds());
        BlockState state=getDefaultState().with(ROTATION,0).with(PROFILE,Profile.BASE).with(CONNECTIONS,Connections.AUTO);
        if(state.contains(MATERIAL))state=state.with(MATERIAL,6);
        setDefaultState(state);
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { super.appendProperties(builder);builder.add(MATERIAL,ROTATION,PROFILE,CONNECTIONS); }
    protected final void appendFixedProperties(StateManager.Builder<Block,BlockState> builder) { super.appendProperties(builder);builder.add(ROTATION,PROFILE,CONNECTIONS); }
    public static int material(BlockState state) { return state.contains(MATERIAL)?state.get(MATERIAL):((FixedMaterialWallBlock)state.getBlock()).material(); }
    public static boolean diagonalPost(BlockState state) { return material(state)==1; }
    public static boolean retainedFence(BlockState state) { return material(state)==6||diagonalPost(state); }
    public static BlockState canonicalForm(BlockState state) {
        if(diagonalPost(state)) {
            state=state.with(ROTATION,state.get(ROTATION)|1).with(CONNECTIONS,Connections.MANUAL).with(POST,true);
            for(Direction direction:Direction.Type.HORIZONTAL)state=state.with(property(direction),WallShape.NONE);
        } else if(material(state)==6)state=state.with(ROTATION,state.get(ROTATION)&6);
        return state;
    }
    public static EnumProperty<WallShape> property(Direction direction) { return switch(direction){case NORTH->NORTH;case EAST->EAST;case SOUTH->SOUTH;case WEST->WEST;default->throw new IllegalArgumentException("Horizontal wall direction required");}; }
    public static int sideCode(BlockState state){int code=0,factor=1;for(Direction direction:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){code+=(switch(state.get(property(direction))){case NONE->0;case LOW->1;case TALL->2;})*factor;factor*=3;}return code;}
    public static boolean hasTallSide(BlockState state){for(Direction direction:Direction.Type.HORIZONTAL)if(state.get(property(direction))==WallShape.TALL)return true;return false;}
    /** Compatibility authoring helper; height is stored independently on each present arm. */
    public BlockState withCourse(BlockState state,Course course){for(Direction direction:Direction.Type.HORIZONTAL)if(state.get(property(direction))!=WallShape.NONE)state=state.with(property(direction),course==Course.TALL?WallShape.TALL:WallShape.LOW);return state;}
    public static VoxelShape rawCollision(BlockState state) { state=canonicalForm(state);return geometry.collision(sideCode(state),state.get(POST),state.get(ROTATION)); }
    @Override public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity(pos,state);}
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){if(!state.isOf(next.getBlock())&&world instanceof net.minecraft.server.world.ServerWorld server&&!dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.writing())dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.scheduleCleanup(server,pos);super.onStateReplaced(state,world,pos,next,moved);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        World measuredWorld=!dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots.worker(world)&&world instanceof World value?value:null;long started=measuredWorld==null?0:dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.begin(measuredWorld,true);
        try{return collisionInternal(state,world,pos,context);}finally{if(started!=0)dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.finish(measuredWorld,state,dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.rootId(pos),pos,"architecture.native_wall_collision",started);}
    }
    private VoxelShape collisionInternal(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.shifted(world,pos))return dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.baseShape()?VoxelShapes.empty():dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.cellShape(world,pos,true,state);
        VoxelShape raw=rawCollision(state);if(world==null||pos==null)return raw;
        // Lighting has no safe neighbor FULL future. The native raw guard is
        // the same physical union: main-thread stack trimming only attributes
        // shared solid overlap to the neighboring native wall/cap once.
        if(dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots.worker(world))return raw;
        BlockPos below=pos.down();BlockState lower=world.getBlockState(below);
        // The normal1.5-high movement guard overlaps a wall in the next grid
        // cell. Attribute that shared volume to the lower native wall once;
        // the complete player collider union remains exactly unchanged.
        VoxelShape lowerRaw=lower.getBlock() instanceof PrototypeWallBlock?rawCollision(lower):lower.getBlock() instanceof WallBlock
                ?dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.nativeCollision(world,below,context):VoxelShapes.empty();
        // A non-owned native cap already owns its bottom volume. Remove only
        // that duplicate upper extension from this wall. Own walls above keep
        // the opposite assignment, so the seam is never removed by both ends.
        BlockPos above=pos.up();BlockState upper=world.getBlockState(above);VoxelShape cap=VoxelShapes.empty();
        if(!(upper.getBlock() instanceof PrototypeWallBlock)
                &&!(upper.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock)
                &&!upper.isOf(dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture.CELL)
                &&dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.contributions(world,above).isEmpty()){
            VoxelShape nativeUpper=dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.nativeCollision(world,above,context);
            if(upper.getBlock() instanceof WallBlock||(Registries.BLOCK.getId(upper.getBlock()).getNamespace().equals("minecraft")&&Block.isShapeFullCube(nativeUpper)))cap=nativeUpper;
        }
        return WallStackGeometry.of(raw,lowerRaw,cap);
    }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.shifted(world,pos))return dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.baseShape()?VoxelShapes.empty():dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.cellShape(world,pos,false,state,context);
        state=canonicalForm(state);
        if(context instanceof net.minecraft.block.EntityShapeContext actor&&actor.getEntity() instanceof net.minecraft.entity.player.PlayerEntity player&&dev.dreamwalker.bloodbornedw.architecture.BuildingTool.isHeld(player))return geometry.outline(sideCode(state),state.get(POST),state.get(ROTATION));
        return getCollisionShape(state,world,pos,context);
    }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }
    @Override public FluidState getFluidState(BlockState state) { return state.get(WATERLOGGED)?Fluids.WATER.getStill(false):super.getFluidState(state); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos=context.getBlockPos();World world=context.getWorld();if(!world.isChunkLoaded(pos))return null;
        return super.getPlacementState(context);
    }
    private static Direction worldDirection(Direction local,int yaw){Direction result=local;for(int i=0;i<yaw/2;i++)result=result.rotateYClockwise();return result;}
    public BlockState nativeState(BlockState state){BlockState result=state;for(Direction local:Direction.Type.HORIZONTAL)result=result.with(property(worldDirection(local,state.get(ROTATION))),state.get(property(local)));return result;}
    public BlockState fromNativeState(BlockState original,BlockState nativeState){BlockState result=original.with(POST,nativeState.get(POST));for(Direction local:Direction.Type.HORIZONTAL)result=result.with(property(local),nativeState.get(property(worldDirection(local,original.get(ROTATION)))));return result;}
    public BlockState reconnect(BlockState state,WorldAccess world,BlockPos pos) {
        state=canonicalForm(state);
        if(diagonalPost(state))return state;
        if(state.get(CONNECTIONS)==Connections.MANUAL)return state;
        if((state.get(ROTATION)&1)!=0)return state.with(CONNECTIONS,Connections.MANUAL);
        BlockState nativeState=nativeState(state);
        for(Direction direction:Direction.Type.HORIZONTAL){BlockPos neighbor=pos.offset(direction);if(world instanceof World loaded&&!loaded.isChunkLoaded(neighbor))continue;nativeState=super.getStateForNeighborUpdate(nativeState,direction,world.getBlockState(neighbor),world,pos,neighbor);}
        if(!(world instanceof World loaded)||loaded.isChunkLoaded(pos.up()))nativeState=super.getStateForNeighborUpdate(nativeState,Direction.UP,world.getBlockState(pos.up()),world,pos,pos.up());
        return fromNativeState(state,nativeState);
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos) {
        if(world instanceof net.minecraft.server.world.ServerWorld server&&!dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.writing()&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.loadedEntity(world,pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity own&&!own.contributions().isEmpty())dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.refreshNative(server,pos);
        World measuredWorld=world instanceof World value?value:null;long started=measuredWorld==null?0:dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.begin(measuredWorld,true);
        try{return neighborInternal(state,direction,neighbor,world,pos,neighborPos);}finally{if(started!=0)dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.finish(measuredWorld,state,dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.rootId(pos),pos,"architecture.native_wall_neighbor_update",started);}
    }
    private BlockState neighborInternal(BlockState state,Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos){
        // Native AUTO updates already schedule water ticks. MANUAL still needs it.
        if(state.get(CONNECTIONS)==Connections.MANUAL&&state.get(WATERLOGGED))world.scheduleFluidTick(pos,Fluids.WATER,Fluids.WATER.getTickRate(world));
        if(direction==Direction.DOWN&&(!(world instanceof World loaded)||loaded.isChunkLoaded(pos.down()))&&!canPlaceAt(state,world,pos))
            return state.get(WATERLOGGED)?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState();
        return direction==Direction.UP||direction.getAxis().isHorizontal()?reconnect(state,world,pos):state;
    }
    @Override public boolean canPlaceAt(BlockState state,WorldView world,BlockPos pos) {
        if(retainedFence(state))return true;
        if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.rawQuery()&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.loadedEntity(world,pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity own&&own.payload().getBoolean("MountEdited"))return true;
        if(world instanceof World loaded&&!loaded.isChunkLoaded(pos.down()))return false;
        VoxelShape body=getCollisionShape(state,world,pos,ShapeContext.absent());if(body.isEmpty())return false;
        // Selection/collision AABBs do not reserve neighboring cells or determine support.
        // A narrow straight lower wall can support an upper post, as in native
        // WallBlock. Do not require the entire decorative post's wider footprint.
        VoxelShape required=VoxelShapes.cuboid(.375,0,.375,.625,1,.625);
        VoxelShape actual=VoxelShapes.empty();
        for(var box:world.getBlockState(pos.down()).getCollisionShape(world,pos.down()).getBoundingBoxes())if(box.maxY>1-1e-7)
            actual=VoxelShapes.union(actual,VoxelShapes.cuboid(box.minX,0,box.minZ,box.maxX,1,box.maxZ));
        return !VoxelShapes.matchesAnywhere(required,actual,BooleanBiFunction.ONLY_FIRST);
    }
    /** A 45-degree builder turn freezes the current form rather than redirecting live grid links. */
    public BlockState rotate45(BlockState state) { return canonicalForm(state.with(CONNECTIONS,retainedFence(state)&&!diagonalPost(state)?state.get(CONNECTIONS):Connections.MANUAL).with(ROTATION,(state.get(ROTATION)+(retainedFence(state)?2:1))&7)); }
    @Override public BlockState rotate(BlockState state,BlockRotation rotation) { return state.with(ROTATION,(state.get(ROTATION)+switch(rotation){case NONE->0;case CLOCKWISE_90->2;case CLOCKWISE_180->4;case COUNTERCLOCKWISE_90->6;})&7); }
    @Override public BlockState mirror(BlockState state,BlockMirror mirror) {
        if(mirror==BlockMirror.NONE)return state;
        BlockState result=state;
        if(mirror==BlockMirror.LEFT_RIGHT)result=result.with(NORTH,state.get(SOUTH)).with(SOUTH,state.get(NORTH));
        else result=result.with(EAST,state.get(WEST)).with(WEST,state.get(EAST));
        return result.with(ROTATION,(-state.get(ROTATION))&7);
    }
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state) { return artisticStack(state); }
    @Override public List<ItemStack> getDroppedStacks(BlockState state,LootContextParameterSet.Builder context) {
        List<ItemStack> drops=super.getDroppedStacks(state,context);for(int i=0;i<drops.size();i++)if(drops.get(i).isOf(asItem())){drops=new java.util.ArrayList<>(drops);drops.set(i,artisticStack(state));}return drops;
    }
    public ItemStack artisticStack(BlockState state) { ItemStack stack=new ItemStack(PrototypeWallArchitecture.materialBlock(material(state)));copyForm(state,stack);return stack; }
    private static void copyForm(BlockState state,ItemStack stack) {
        NbtCompound tag=stack.getOrCreateSubNbt("BlockStateTag");tag.putString("material",Integer.toString(material(state)));
        tag.putString("profile",state.get(PROFILE).asString());
        tag.putString("connections",state.get(CONNECTIONS).asString());tag.putString("rotation",Integer.toString(state.get(ROTATION)));
        if(state.get(CONNECTIONS)==Connections.MANUAL) { tag.putString("up",Boolean.toString(state.get(POST)));for(Direction direction:Direction.Type.HORIZONTAL)tag.putString(direction.asString(),state.get(property(direction)).asString()); }
    }
}
