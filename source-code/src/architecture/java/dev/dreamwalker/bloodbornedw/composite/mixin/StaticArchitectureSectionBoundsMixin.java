package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureSectionBounds;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.CompositeTerrain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChunkBuilder.BuiltChunk.class)
public abstract class StaticArchitectureSectionBoundsMixin implements StaticArchitectureSectionBounds.Section {
    @Shadow public abstract BlockPos getOrigin();
    @Unique private volatile Box dw$staticArchitectureBounds;
    @Unique private volatile boolean dw$staticArchitectureKnown;
    @Override public void staticArchitectureBounds(BlockPos expectedOrigin,Box bounds){
        BlockPos actual=getOrigin();if(!actual.equals(expectedOrigin))return;
        dw$staticArchitectureBounds=bounds;
        dw$staticArchitectureKnown=true;
    }
    @Inject(method="getBoundingBox",at=@At("RETURN"),cancellable=true)
    private void dw$staticArchitectureBox(CallbackInfoReturnable<Box> result){
        // A root can overhang into the camera before its native section has
        // compiled once. Inspect the already loaded native palette lazily;
        // otherwise frustum rejection could prevent that first compilation.
        if(!dw$staticArchitectureKnown){
            var client=MinecraftClient.getInstance();var world=client.world;
            if(world!=null&&client.isOnThread()){
                BlockPos origin=getOrigin();var chunk=world.getChunkManager().getWorldChunk(origin.getX()>>4,origin.getZ()>>4);
                if(chunk!=null){
                    int index=(origin.getY()>>4)-world.getBottomSectionCoord();var sections=chunk.getSectionArray();
                    Box bounds=StaticArchitectureSectionBounds.nativeBox(origin);
                    if(index>=0&&index<sections.length){var section=sections[index];
                        if(section.hasAny(state->state.getBlock() instanceof StaticArchitectureBlock&&StaticArchitectureCatalog.recipe(state).resident()>=0||CompositeTerrain.eligible(state)))
                            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                                var state=section.getBlockState(x,y,z);
                                if(state.getBlock() instanceof StaticArchitectureBlock)bounds=StaticArchitectureSectionBounds.include(bounds,origin.add(x,y,z),StaticArchitectureCatalog.recipe(state));
                                else if(CompositeTerrain.eligible(state)){var pos=origin.add(x,y,z);var entity=chunk.getBlockEntities().get(pos);bounds=StaticArchitectureSectionBounds.includeOwned(bounds,pos,state,entity==null?null:entity.getRenderData());}
                            }
                    }
                    staticArchitectureBounds(origin,bounds);
                }
            }
        }
        Box bounds=dw$staticArchitectureBounds;if(bounds!=null)result.setReturnValue(bounds);
    }
    @Inject(method="setOrigin",at=@At("HEAD"))
    private void dw$staticArchitectureOrigin(int x,int y,int z,CallbackInfo callback){dw$staticArchitectureBounds=null;dw$staticArchitectureKnown=false;}
    @Inject(method="clear",at=@At("HEAD"))
    private void dw$staticArchitectureClear(CallbackInfo callback){dw$staticArchitectureBounds=null;dw$staticArchitectureKnown=false;}
    @Inject(method="scheduleRebuild",at=@At("HEAD"))
    private void dw$staticArchitectureRebuild(boolean important,CallbackInfo callback){dw$staticArchitectureKnown=false;}
}
