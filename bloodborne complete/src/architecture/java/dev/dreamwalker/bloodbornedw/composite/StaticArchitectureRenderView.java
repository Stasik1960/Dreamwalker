package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.joml.*;

/** Source-local neighbourhood sampling, backed by the native chunk render
 * region. Shared recipe membership replaces an individual owner's UUID. */
@Environment(EnvType.CLIENT)
public final class StaticArchitectureRenderView implements BlockRenderView,ProjectedBlockView {
    private final BlockRenderView delegate;
    private final World snapshots;
    private final BlockPos root;
    private final Matrix4f transform;
    private final Matrix3f normals;
    private final StaticArchitectureCatalog.Pose pose;
    public StaticArchitectureRenderView(BlockRenderView delegate,BlockPos root,Matrix4f transform,Matrix3f normals,StaticArchitectureCatalog.Pose pose){
        this.delegate=delegate;this.root=root.toImmutable();this.transform=new Matrix4f(transform);this.normals=new Matrix3f(normals);this.pose=pose;
        snapshots=delegate instanceof World world?world:delegate instanceof ProjectedBlockView projected?projected.backingWorld():MinecraftClient.getInstance().world;
    }
    private BlockPos at(BlockPos local){var p=new Vector3f(local.getX()+.5f,local.getY()+.5f,local.getZ()+.5f);transform.transformPosition(p);return BlockPos.ofFloored(root.getX()+p.x,root.getY()+p.y,root.getZ()+p.z);}
    @Override public World backingWorld(){return snapshots;}
    @Override public BlockPos worldPosition(BlockPos local){return at(local);}
    @Override public BlockState getBlockState(BlockPos local){
        BlockPos point=at(local);BlockState actual=delegate.getBlockState(point);
        if(actual.getBlock() instanceof StaticArchitectureBlock){
            var recipe=StaticArchitectureCatalog.recipe(actual);
            if(recipe.resident()<0&&recipe.opacity()==15)return Blocks.STONE.getDefaultState();
            // A resident belonging to another component preserves its native
            // shadow even when this cell also contains one of our fragments.
            if(recipe.resident()>=0){var resident=recipe.fragments().get(recipe.resident());if(!same(point,resident))return recipe.renderCarrier(actual);}
            for(var fragment:recipe.fragments())if(fragment.member()&&same(point,fragment))return Blocks.AIR.getDefaultState();
            return recipe.renderCarrier(actual);
        }
        if(actual.getBlock() instanceof CompositeCellBlock&&CompositeShapeSnapshots.sourceCarrierOpacity(snapshots,point)==15){var carrier=ImportedWholeSourceOpticsV127.opaqueRenderCarrier();if(carrier!=null)return carrier;}
        if(actual.getBlock() instanceof InvisibleSourceCarrierV127)return InvisibleSourceCarrierV127.source(actual);
        if(actual.getBlock() instanceof SourceFallbackCarrierV1271)return SourceFallbackCarrierV1271.source(actual);
        if(actual.getBlock() instanceof StaticGrassMasonryBlock)return Blocks.GRASS_BLOCK.getDefaultState();
        if(actual.getBlock() instanceof CompositeRootBlock block)return ImportedSourceLightingV127.renderCarrier(block.spec,actual);
        return actual;
    }
    private boolean same(BlockPos point,StaticArchitectureCatalog.Fragment fragment){Cell delta=fragment.origin();return fragment.pose()==pose&&point.getX()+delta.x()==root.getX()&&point.getY()+delta.y()==root.getY()&&point.getZ()+delta.z()==root.getZ();}
    @Override public BlockEntity getBlockEntity(BlockPos local){return delegate.getBlockEntity(at(local));}
    @Override public FluidState getFluidState(BlockPos local){return delegate.getFluidState(at(local));}
    @Override public int getHeight(){return delegate.getHeight();}
    @Override public int getBottomY(){return delegate.getBottomY();}
    @Override public LightingProvider getLightingProvider(){return delegate.getLightingProvider();}
    @Override public int getLightLevel(LightType type,BlockPos local){return delegate.getLightLevel(type,at(local));}
    @Override public int getColor(BlockPos local,ColorResolver resolver){return delegate.getColor(at(local),resolver);}
    @Override public float getBrightness(Direction direction,boolean shade){var n=new Vector3f(direction.getOffsetX(),direction.getOffsetY(),direction.getOffsetZ());normals.transform(n);return delegate.getBrightness(Direction.getFacing(n.x,n.y,n.z),shade);}
}
