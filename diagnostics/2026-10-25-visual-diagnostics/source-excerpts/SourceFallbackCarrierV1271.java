package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;

/** Native chunk models for source branches retained by Minecraft's pack merge.
 * Finite saved compatibility states, without items, owners, block entities or
 * callbacks from the original source block. The JSON models retain v15 art.
 */
public final class SourceFallbackCarrierV1271 extends Block implements NewStateStorage {
    public static final Identifier ID=new Identifier("bloodborne_dw","source_fallback_carrier");
    public static final Identifier TRANSLUCENT_ID=new Identifier("bloodborne_dw","source_fallback_translucent_carrier");
    public static final Identifier MULTIPART_ID=new Identifier("bloodborne_dw","source_fallback_multipart_carrier");
    public static final Identifier SOLID_MULTIPART_ID=new Identifier("bloodborne_dw","source_fallback_solid_multipart_carrier");
    private record Loaded(List<BlockState> sources,VoxelShape[] collision,VoxelShape[] outline,boolean[] suppressWallOverlay){}
    private static final Loaded LOADED=load();
    private static final List<BlockState> SOURCES=LOADED.sources();
    public static final IntProperty SOURCE=IntProperty.of("source",0,127);
    public static final IntProperty SOURCE_BANK=IntProperty.of("source_bank",0,1);
    private static SourceFallbackCarrierV1271 instance;
    private static SourceFallbackCarrierV1271 translucentInstance;
    private static SourceFallbackCarrierV1271 multipartInstance;
    private static SourceFallbackCarrierV1271 solidMultipartInstance;

    private static Loaded load(){
        var input=SourceFallbackCarrierV1271.class.getResourceAsStream("/bloodborne_dw/source-fallback-carriers-v1271.json");
        if(input==null)throw new IllegalStateException("Missing source fallback compatibility states");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var doc=JsonParser.parseReader(reader).getAsJsonObject();
            if(doc.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Source fallback compatibility schema");
            List<BlockState> states=new ArrayList<>();
            for(var raw:doc.getAsJsonArray("sourceStates"))states.add(ImportedSourceLightingV127.source(raw.getAsString()));
            if(states.size()<2||states.size()>256)throw new IllegalArgumentException("Source fallback state budget");
            VoxelShape[] collisions=new VoxelShape[states.size()],outlines=new VoxelShape[states.size()];
            if(doc.has("physicalOverrides")){
                var overrides=doc.getAsJsonArray("physicalOverrides");
                if(overrides.size()!=states.size())throw new IllegalArgumentException("Source fallback physical override length");
                for(int i=0;i<states.size();i++)if(!overrides.get(i).isJsonNull()){
                    var row=overrides.get(i).getAsJsonObject();
                    if(row.has("collision"))collisions[i]=shape(row.getAsJsonArray("collision"));
                    if(row.has("outline"))outlines[i]=shape(row.getAsJsonArray("outline"));
                }
            }
            // Distinct fixed source applications may share a native physical
            // state. Their finite saved indices preserve the prepared choice.
            if(doc.has("sourceApplicationSets")&&doc.getAsJsonArray("sourceApplicationSets").size()!=states.size())throw new IllegalArgumentException("Source fallback application length");
            boolean[] suppressWall=new boolean[states.size()];
            for(int i=0;i<states.size();i++)suppressWall[i]=collisions[i]!=null&&!Block.isShapeFullCube(collisions[i])&&states.get(i).shouldBlockVision(EmptyBlockView.INSTANCE,BlockPos.ORIGIN);
            return new Loaded(List.copyOf(states),collisions,outlines,suppressWall);
        }catch(Exception failure){throw new IllegalStateException("Invalid source fallback compatibility states",failure);}
    }
    private static VoxelShape shape(com.google.gson.JsonArray boxes){
        if(boxes.size()>128)throw new IllegalArgumentException("Source fallback physical box budget");
        VoxelShape result=VoxelShapes.empty();
        for(var raw:boxes){var box=raw.getAsJsonObject();var from=box.getAsJsonArray("from");var to=box.getAsJsonArray("to");if(from.size()!=3||to.size()!=3)throw new IllegalArgumentException("Source fallback box dimension");
            double[] low=new double[3],high=new double[3];for(int axis=0;axis<3;axis++){low[axis]=from.get(axis).getAsDouble()/16;high[axis]=to.get(axis).getAsDouble()/16;if(!Double.isFinite(low[axis])||!Double.isFinite(high[axis])||low[axis]>=high[axis]||low[axis]<-2||high[axis]>3)throw new IllegalArgumentException("Invalid source fallback physical box");}
            result=VoxelShapes.union(result,VoxelShapes.cuboid(low[0],low[1],low[2],high[0],high[1],high[2]));
        }
        return result;
    }
    private static Settings settings(boolean opaque){
        Settings settings=Settings.create().dynamicBounds().dropsNothing()
            .solidBlock((state,world,pos)->source(state).isSolidBlock(world,pos))
            .suffocates((state,world,pos)->source(state).shouldSuffocate(world,pos))
            .blockVision((state,world,pos)->source(state).shouldBlockVision(world,pos))
            .luminance(state->source(state).getLuminance());
        return opaque?settings:settings.nonOpaque();
    }
    private SourceFallbackCarrierV1271(boolean opaque){
        super(settings(opaque));
        setDefaultState(getDefaultState().with(SOURCE,0).with(SOURCE_BANK,0).with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.BASE));
    }
    public static void initialize(){if(instance==null){instance=Registry.register(Registries.BLOCK,ID,new SourceFallbackCarrierV1271(true));translucentInstance=Registry.register(Registries.BLOCK,TRANSLUCENT_ID,new SourceFallbackCarrierV1271(false));multipartInstance=Registry.register(Registries.BLOCK,MULTIPART_ID,new SourceFallbackCarrierV1271(false));solidMultipartInstance=Registry.register(Registries.BLOCK,SOLID_MULTIPART_ID,new SourceFallbackCarrierV1271(true));}}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(SOURCE,SOURCE_BANK,CompositeRootBlock.PROFILE);}
    private static int index(BlockState state){int value=state.get(SOURCE)+128*state.get(SOURCE_BANK);return value<SOURCES.size()?value:0;}
    public static BlockState source(BlockState state){return SOURCES.get(index(state));}
    public static VoxelShape collisionOverride(BlockState state){return LOADED.collision()[index(state)];}
    public static VoxelShape outlineOverride(BlockState state){return LOADED.outline()[index(state)];}
    public static boolean suppressesNativeWallOverlay(BlockState state){return state.getBlock() instanceof SourceFallbackCarrierV1271&&LOADED.suppressWallOverlay()[index(state)];}
    static BlockState nativeAdjacent(BlockState state){
        if(state.getBlock() instanceof SourceFallbackCarrierV1271)return source(state);
        if(state.getBlock() instanceof InvisibleSourceCarrierV127)return InvisibleSourceCarrierV127.source(state);
        if(state.getBlock() instanceof CompositeRootBlock block)return ImportedSourceLightingV127.renderCarrier(block.spec,state);
        return state;
    }
    @Override public boolean isSideInvisible(BlockState state,BlockState adjacent,Direction direction){return source(state).isSideInvisible(nativeAdjacent(adjacent),direction);}
    @Override public boolean hasSidedTransparency(BlockState state){return source(state).hasSidedTransparency();}
    @Override public boolean isShapeFullCube(BlockState state,BlockView world,BlockPos pos){return source(state).isFullCube(world,pos);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){var shape=outlineOverride(state);return shape!=null?shape:source(state).getOutlineShape(world,pos,context);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){var shape=collisionOverride(state);return shape!=null?shape:source(state).getCollisionShape(world,pos,context);}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return source(state).getCullingShape(world,pos);}
    @Override public VoxelShape getSidesShape(BlockState state,BlockView world,BlockPos pos){return source(state).getSidesShape(world,pos);}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return source(state).getOpacity(world,pos);}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return source(state).isTransparent(world,pos);}
    @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView world,BlockPos pos){return source(state).getAmbientOcclusionLightLevel(world,pos);}
    @Override public FluidState getFluidState(BlockState state){return source(state).getFluidState();}
    @Override public long getRenderingSeed(BlockState state,BlockPos pos){return source(state).getRenderingSeed(pos);}
    public static List<BlockState> sourceStates(){return SOURCES;}
}
