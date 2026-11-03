package dev.dreamwalker.bloodbornedw.editor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.EditorMeshUpdates;
import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh", remap = false)
abstract class CompositeEditorModelMixin {
    @Inject(method = "bake", at = @At("HEAD"), remap = false)
    private static void dw$trackEditorRoot(MinecraftClient client, CompositeBlockEntity owner,
                                           CompositeRootBlock block, BlockState state, NbtCompound payload,
                                           int overlay, Map<Long, Long> dependencies, CallbackInfoReturnable<?> cir) {
        long section = ChunkSectionPos.from(owner.getPos()).asLong();
        dependencies.put(section, EditorMeshUpdates.stamp(section));
    }

    @ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1092;getModel(Lnet/minecraft/class_2960;)Lnet/minecraft/class_1087;"), remap = false)
    private static BakedModel dw$prepareEditorModel(BakedModel model, @Coerce Object request) {
        CompositeMeshRequestAccessor data = (CompositeMeshRequestAccessor) request;
        return CityFacadesClient.terrainModel(model, data.dw$owner().getPos(), data.dw$state(), data.dw$world());
    }

    @ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1092;getModel(Lnet/minecraft/class_2960;)Lnet/minecraft/class_1087;"), remap = false)
    private static BakedModel dw$bakeEditorModel(BakedModel model, MinecraftClient client, CompositeBlockEntity owner,
                                                 CompositeRootBlock block, BlockState state, NbtCompound payload,
                                                 int overlay, Map<Long, Long> dependencies) {
        return CityFacadesClient.terrainModel(model, owner.getPos(), state, owner.getWorld());
    }
}
