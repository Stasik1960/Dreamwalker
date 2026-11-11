package dev.dreamwalker.bloodbornedw.composite.overlaymixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder$Vertex",remap=false)
public interface PriorityVertexAccess {
 @Accessor("x") float dw$x();
 @Accessor("y") float dw$y();
 @Accessor("z") float dw$z();
 @Accessor("x") void dw$x(float value);
 @Accessor("y") void dw$y(float value);
 @Accessor("z") void dw$z(float value);
}