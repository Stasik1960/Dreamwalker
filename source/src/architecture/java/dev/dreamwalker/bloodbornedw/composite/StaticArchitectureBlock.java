package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

/** A native architectural cell with shared model/shape data and no entity.
 * The scoped break lifecycle removes its finite source component atomically. */
public final class StaticArchitectureBlock extends Block implements NewStateStorage,Waterloggable {
    public static final IntProperty PART=IntProperty.of("part",0,StaticArchitectureCatalog.BANK_SIZE-1);
    public static final net.minecraft.state.property.BooleanProperty WATERLOGGED=net.minecraft.state.property.Properties.WATERLOGGED;
    private static final List<StaticArchitectureBlock> BANKS=new ArrayList<>();
    private final int bank;
    private StaticArchitectureBlock(int bank){
        super(Settings.create().strength(.4F).nonOpaque().dynamicBounds().dropsNothing()
            .solidBlock((state,world,pos)->false).suffocates((state,world,pos)->false).blockVision((state,world,pos)->false)
            .luminance(state->StaticArchitectureCatalog.recipes().get(Math.min(bank*StaticArchitectureCatalog.BANK_SIZE+state.get(PART),StaticArchitectureCatalog.recipes().size()-1)).luminance())
            .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));
        this.bank=bank;setDefaultState(getDefaultState().with(PART,0).with(WATERLOGGED,false));
    }
    static void initialize(int count){if(!BANKS.isEmpty())throw new IllegalStateException("Static banks already registered");for(int bank=0;bank<count;bank++)BANKS.add(Registry.register(Registries.BLOCK,new Identifier("bloodborne_dw","static_architecture_"+bank),new StaticArchitectureBlock(bank)));}
    public static List<StaticArchitectureBlock> banks(){return List.copyOf(BANKS);}
    public int bank(){return bank;}
    public StaticArchitectureCatalog.Recipe recipe(BlockState state){
        int first=bank*StaticArchitectureCatalog.BANK_SIZE,index=first+state.get(PART);
        // Unused tail slots have the last recipe's exact meaning. They are
        // never emitted by the importer and do not introduce a missing state.
        return StaticArchitectureCatalog.recipes().get(Math.min(index,StaticArchitectureCatalog.recipes().size()-1));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(PART,WATERLOGGED);}
    @Override public net.minecraft.fluid.FluidState getFluidState(BlockState state){return state.get(WATERLOGGED)?net.minecraft.fluid.Fluids.WATER.getStill(false):super.getFluidState(state);}
    @Override public boolean canFillWithFluid(BlockView world,BlockPos pos,BlockState state,net.minecraft.fluid.Fluid fluid){return fluid==net.minecraft.fluid.Fluids.WATER&&!state.get(WATERLOGGED)&&!Block.isShapeFullCube(recipe(state).collisionShape());}
    @Override public BlockState getStateForNeighborUpdate(BlockState state,net.minecraft.util.math.Direction side,BlockState neighbor,net.minecraft.world.WorldAccess world,BlockPos pos,BlockPos other){if(state.get(WATERLOGGED))world.scheduleFluidTick(pos,net.minecraft.fluid.Fluids.WATER,net.minecraft.fluid.Fluids.WATER.getTickRate(world));return super.getStateForNeighborUpdate(state,side,neighbor,world,pos,other);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return recipe(state).collisionShape();}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return recipe(state).outlineShape();}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return recipe(state).opacity();}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return true;}
    @Override public void onStateReplaced(BlockState state,net.minecraft.world.World world,BlockPos pos,BlockState next,boolean moved){if(!state.equals(next)&&world instanceof net.minecraft.server.world.ServerWorld server&&(!StaticArchitectureCatalog.contains(next)||StaticArchitectureCatalog.recipe(state)!=StaticArchitectureCatalog.recipe(next)))StaticArchitectureRuntime.replaced(server,pos,state);super.onStateReplaced(state,world,pos,next,moved);}
}
