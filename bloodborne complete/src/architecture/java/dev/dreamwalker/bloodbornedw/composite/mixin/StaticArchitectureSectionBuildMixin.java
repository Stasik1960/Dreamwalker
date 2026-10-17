package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureSectionBounds;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
public abstract class StaticArchitectureSectionBuildMixin {
    @Shadow @Final private ChunkBuilder.BuiltChunk field_20839;
    @Inject(method="render",at=@At("HEAD"))
    private void dw$staticArchitectureBegin(float cameraX,float cameraY,float cameraZ,BlockBufferBuilderStorage storage,CallbackInfoReturnable<?> result){StaticArchitectureSectionBounds.begin(field_20839.getOrigin());}
    @Inject(method="render",at=@At("RETURN"))
    private void dw$staticArchitectureEnd(float cameraX,float cameraY,float cameraZ,BlockBufferBuilderStorage storage,CallbackInfoReturnable<?> result){StaticArchitectureSectionBounds.end((StaticArchitectureSectionBounds.Section)(Object)field_20839);}
}
