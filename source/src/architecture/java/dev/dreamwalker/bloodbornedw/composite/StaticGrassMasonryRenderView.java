package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

/** The former source-part lighting view for this one immutable, unshifted pose.
 * Chunk workers read states/light from their actual render region. The client
 * world reference is used only for already published immutable helper optics. */
@Environment(EnvType.CLIENT)
public final class StaticGrassMasonryRenderView implements BlockRenderView,ProjectedBlockView {
    private final BlockRenderView delegate;
    private final BlockPos root;
    private final World snapshots;
    public StaticGrassMasonryRenderView(BlockRenderView delegate,BlockPos root){
        this.delegate=delegate;this.root=root.toImmutable();
        snapshots=delegate instanceof World world?world:delegate instanceof ProjectedBlockView projected?projected.backingWorld():MinecraftClient.getInstance().world;
    }
    @Override public World backingWorld(){return snapshots;}
    @Override public BlockPos worldPosition(BlockPos pos){return pos;}
    @Override public BlockState getBlockState(BlockPos pos){
        // The former owner renderer masked only its synthetic root. This pose
        // has exactly one occupied cell and therefore no synthetic helpers.
        if(pos.equals(root))return Blocks.AIR.getDefaultState();
        BlockState actual=delegate.getBlockState(pos);
        if(actual.getBlock() instanceof CompositeCellBlock&&CompositeShapeSnapshots.sourceCarrierOpacity(snapshots,pos)==15){
            var carrier=ImportedWholeSourceOpticsV127.opaqueRenderCarrier();if(carrier!=null)return carrier;
        }
        if(actual.getBlock() instanceof StaticGrassMasonryBlock)return Blocks.GRASS_BLOCK.getDefaultState();
        if(actual.getBlock() instanceof InvisibleSourceCarrierV127)return InvisibleSourceCarrierV127.source(actual);
        if(actual.getBlock() instanceof SourceFallbackCarrierV1271)return SourceFallbackCarrierV1271.source(actual);
        if(actual.getBlock() instanceof CompositeRootBlock block)return ImportedSourceLightingV127.renderCarrier(block.spec,actual);
        return actual;
    }
    @Override public BlockEntity getBlockEntity(BlockPos pos){return delegate.getBlockEntity(pos);}
    @Override public FluidState getFluidState(BlockPos pos){return delegate.getFluidState(pos);}
    @Override public int getHeight(){return delegate.getHeight();}
    @Override public int getBottomY(){return delegate.getBottomY();}
    @Override public LightingProvider getLightingProvider(){return delegate.getLightingProvider();}
    @Override public int getLightLevel(LightType type,BlockPos pos){return delegate.getLightLevel(type,pos);}
    @Override public int getColor(BlockPos pos,ColorResolver resolver){
        return delegate.getColor(pos,resolver==BiomeColors.GRASS_COLOR?RemainingSourceTint.grassResolver():resolver);
    }
    @Override public float getBrightness(Direction face,boolean shade){return delegate.getBrightness(face,shade);}
}
