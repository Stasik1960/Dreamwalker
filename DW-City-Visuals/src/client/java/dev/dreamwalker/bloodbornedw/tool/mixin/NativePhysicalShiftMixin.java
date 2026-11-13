package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.tool.DebugModels;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.NativeGridClient$PhysicalModel", remap = false)
abstract class NativePhysicalShiftMixin {
    @WrapMethod(method = "emitBlockQuads", remap = false)
    private void dw$applyPose(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> random,
            RenderContext context, Operation<Void> original) {
        DebugModels.emitNative(pos, state, context, () -> original.call(world, state, pos, random, context));
    }
}
