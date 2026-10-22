package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Finite, invisible original carrier states. No ledger, block entity or ticker.
 * Original source shapes and optics remain native rather than being replaced
 * with air or an invented cube. These states are conversion compatibility only.
 */
public final class InvisibleSourceCarrierV127 extends Block implements NewStateStorage {
    public static final Identifier ID=new Identifier("bloodborne_dw","source_invisible_carrier");
    public static final Identifier SOLID_ID=new Identifier("bloodborne_dw","source_invisible_solid_carrier");
    private static final List<BlockState> SOURCES=load();
    public static final IntProperty SOURCE=IntProperty.of("source",0,SOURCES.size()-1);
    private static InvisibleSourceCarrierV127 instance;
    private static InvisibleSourceCarrierV127 solidInstance;

    private static List<BlockState> load(){
        var input=InvisibleSourceCarrierV127.class.getResourceAsStream("/bloodborne_dw/invisible-source-carriers-v1271.json");
        if(input==null)throw new IllegalStateException("Missing invisible source compatibility states");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var doc=JsonParser.parseReader(reader).getAsJsonObject();
            if(doc.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Invisible source compatibility schema");
            List<BlockState> states=new ArrayList<>();
            for(var raw:doc.getAsJsonArray("sourceStates"))states.add(ImportedSourceLightingV127.source(raw.getAsString()));
            if(states.size()<2||states.size()>128||new java.util.HashSet<>(states).size()!=states.size())throw new IllegalArgumentException("Invisible source state budget");
            return List.copyOf(states);
        }catch(Exception failure){throw new IllegalStateException("Invalid invisible source compatibility states",failure);}
    }
    private static Settings settings(boolean opaque){
        Settings settings=Settings.create().dynamicBounds().dropsNothing()
            .solidBlock((state,world,pos)->source(state).isSolidBlock(world,pos))
            .suffocates((state,world,pos)->source(state).shouldSuffocate(world,pos))
            .blockVision((state,world,pos)->source(state).shouldBlockVision(world,pos))
            .luminance(state->source(state).getLuminance());
        return opaque?settings:settings.nonOpaque();
    }
    private InvisibleSourceCarrierV127(boolean opaque){
        super(settings(opaque));
        setDefaultState(getDefaultState().with(SOURCE,0).with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.BASE));
    }
    public static void initialize(){if(instance==null){instance=Registry.register(Registries.BLOCK,ID,new InvisibleSourceCarrierV127(false));solidInstance=Registry.register(Registries.BLOCK,SOLID_ID,new InvisibleSourceCarrierV127(true));}}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(SOURCE,CompositeRootBlock.PROFILE);}
    public static BlockState source(BlockState state){return SOURCES.get(state.get(SOURCE));}
    @Override public boolean isSideInvisible(BlockState state,BlockState adjacent,Direction direction){return source(state).isSideInvisible(SourceFallbackCarrierV1271.nativeAdjacent(adjacent),direction);}
    @Override public boolean hasSidedTransparency(BlockState state){return source(state).hasSidedTransparency();}
    @Override public boolean isShapeFullCube(BlockState state,BlockView world,BlockPos pos){return source(state).isFullCube(world,pos);}
    public static boolean climbing(BlockState state){return state.getBlock() instanceof InvisibleSourceCarrierV127&&source(state).isIn(BlockTags.CLIMBABLE);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return source(state).getOutlineShape(world,pos,context);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return source(state).getCollisionShape(world,pos,context);}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return source(state).getCullingShape(world,pos);}
    @Override public VoxelShape getSidesShape(BlockState state,BlockView world,BlockPos pos){return source(state).getSidesShape(world,pos);}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return source(state).getOpacity(world,pos);}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return source(state).isTransparent(world,pos);}
    @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView world,BlockPos pos){return source(state).getAmbientOcclusionLightLevel(world,pos);}
    @Override public FluidState getFluidState(BlockState state){return source(state).getFluidState();}
    public static List<BlockState> sourceStates(){return SOURCES;}
}
