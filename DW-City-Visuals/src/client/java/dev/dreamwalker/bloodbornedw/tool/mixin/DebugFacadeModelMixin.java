package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.DebugModels;
import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CityFacadesClient.class, remap = false)
abstract class DebugFacadeModelMixin {
    @Inject(method = "terrainModel", at = @At("RETURN"), cancellable = true)
    private static void dw$terrainPose(BakedModel model, BlockPos root, BlockState state, BlockRenderView world,
            CallbackInfoReturnable<BakedModel> cir) {
        if (!(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock))
            cir.setReturnValue(DebugModels.apply(cir.getReturnValue(), root, state));
    }

    @Inject(method = "visibleModel(Lnet/minecraft/class_1087;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_1920;ZLnet/minecraft/class_2960;)Lnet/minecraft/class_1087;",
            at = @At("RETURN"), cancellable = true)
    private static void dw$visiblePose(BakedModel model, BlockPos root, BlockState state, BlockRenderView world,
            boolean shutter, Identifier part, CallbackInfoReturnable<BakedModel> cir) {
        cir.setReturnValue(DebugModels.apply(cir.getReturnValue(), root, state));
    }
}
