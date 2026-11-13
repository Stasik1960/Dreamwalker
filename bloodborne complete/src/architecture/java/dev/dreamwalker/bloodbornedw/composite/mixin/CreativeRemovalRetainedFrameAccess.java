package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.RetainedFrames", remap = false)
public interface CreativeRemovalRetainedFrameAccess {
    @Invoker(value = "get", remap = false)
    static BlockState dreamwalker$get(Chunk chunk, BlockPos pos) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "put", remap = false)
    static void dreamwalker$put(WorldChunk chunk, BlockPos pos, BlockState frame) {
        throw new AssertionError("Untransformed accessor");
    }

    @Invoker(value = "remove", remap = false)
    static void dreamwalker$remove(WorldChunk chunk, BlockPos pos) {
        throw new AssertionError("Untransformed accessor");
    }
}
