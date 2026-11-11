package dev.dreamwalker.bloodbornedw.composite.overlaymixin;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext",remap=false)
public interface PriorityBlockContextAccess {
 @Accessor("pos") BlockPos.Mutable dw$priorityPos();
 @Accessor("state") BlockState dw$priorityState();
}