package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureSectionBounds;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeTerrainUpdates;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
public abstract class StaticArchitectureSectionBuildMixin {
    @Shadow @Final private ChunkBuilder.BuiltChunk field_20839;
    @Inject(method="render",at=@At("HEAD"))
    private void dw$staticArchitectureBegin(float cameraX,float cameraY,float cameraZ,BlockBufferBuilderStorage storage,CallbackInfoReturnable<?> result){StaticArchitectureSectionBounds.begin(field_20839.getOrigin());}
    @Inject(method="render",at=@At("RETURN"))
    private void dw$staticArchitectureEnd(float cameraX,float cameraY,float cameraZ,BlockBufferBuilderStorage storage,CallbackInfoReturnable<?> result){StaticArchitectureSectionBounds.end((StaticArchitectureSectionBounds.Section)(Object)field_20839);}
    /** Retain owned storage/interactions without registering a second visible
     * renderer. Native dispatch then does no per-frame owner/light lookup. */
    @Inject(method="addBlockEntity",at=@At("HEAD"),cancellable=true)
    private void dw$terrainOwner(@Coerce Object renderData,BlockEntity entity,CallbackInfo callback){
        if(!(entity instanceof CompositeBlockEntity owner))return;
        if(entity.getType()==CompositeArchitecture.CELL_ENTITY&&owner.resident()==null&&entity.getCachedState().isOf(CompositeArchitecture.CELL)){
            // These technical cells never emit geometry. Their saved ownership,
            // shapes and sync remain intact; only the no-op BER dispatch stops.
            CompositeTerrainUpdates.helperExcluded();callback.cancel();return;
        }
        if(owner.terrainMeshed())callback.cancel();
    }
}
