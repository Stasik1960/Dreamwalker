package dev.dreamwalker.bloodbornedw.composite;

import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** One reviewed, immobile source pose stored entirely in the native chunk palette.
 * Other poses and hand-built instances keep their existing owner representation. */
public final class StaticGrassMasonryBlock extends Block implements NewStateStorage {
    public static final String LEGACY_PATH="source_bricks_grass_block_0";
    public static final Identifier ID=new Identifier("bloodborne_dw",LEGACY_PATH+"_static");
    public static final Identifier MODEL=new Identifier("bloodborne_dw","remaining/source/minecraft/block/bricks/grass_block_0");
    private static final VoxelShape CUBE=VoxelShapes.fullCube();
    private static StaticGrassMasonryBlock instance;

    private StaticGrassMasonryBlock(){
        super(Settings.create().strength(.4F).nonOpaque().dynamicBounds().dropsNothing()
            .solidBlock((state,world,pos)->false).suffocates((state,world,pos)->false)
            .blockVision((state,world,pos)->false).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));
    }
    public static void initialize(){
        if(instance==null)instance=Registry.register(Registries.BLOCK,ID,new StaticGrassMasonryBlock());
    }
    public static StaticGrassMasonryBlock block(){
        if(instance==null)throw new IllegalStateException("Static grass masonry is not registered");
        return instance;
    }
    public static BlockState state(){return block().getDefaultState();}
    /** A saved state carries only the one pose actually used by the prepared city.
     * Typed payload validation prevents silently dropping private/user data. */
    public static boolean eligible(BlockState state,NbtCompound payload){
        return state.equals(CompositeArchitecture.kindBlock(LEGACY_PATH).getDefaultState())
            &&payload!=null&&payload.getKeys().equals(Set.of(ImportedTypeAliases.VERSION_KEY))
            &&payload.contains(ImportedTypeAliases.VERSION_KEY,NbtElement.INT_TYPE)
            &&payload.getInt(ImportedTypeAliases.VERSION_KEY)==ImportedTypeAliases.VERSION;
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return CUBE;}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return CUBE;}
    // Preserve the old root's world-lighting carrier flags. The client renderer
    // separately retains the original source part's masked lighting view.
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return 15;}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return true;}
}
