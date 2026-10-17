package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureSectionBounds;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
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
    @Override public void staticArchitectureBounds(BlockPos expectedOrigin,boolean present){
        BlockPos actual=getOrigin();if(!actual.equals(expectedOrigin))return;
        // The catalog's reviewed pose span is limited to this neighbourhood.
        // Ordinary sections retain their original native bounding box.
        dw$staticArchitectureBounds=present?new Box(actual.getX()-16,actual.getY()-32,actual.getZ()-16,actual.getX()+32,actual.getY()+48,actual.getZ()+32):null;
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
                if(chunk!=null){int index=(origin.getY()>>4)-world.getBottomSectionCoord();var sections=chunk.getSectionArray();boolean present=index>=0&&index<sections.length&&sections[index].hasAny(state->state.getBlock() instanceof StaticArchitectureBlock&&StaticArchitectureCatalog.recipe(state).resident()>=0);staticArchitectureBounds(origin,present);}
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
