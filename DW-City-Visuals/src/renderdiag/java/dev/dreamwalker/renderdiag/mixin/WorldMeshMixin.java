package dev.dreamwalker.renderdiag.mixin;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.renderdiag.RenderDiag;
import dev.dreamwalker.renderdiag.RootSignature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh",remap=false)
abstract class WorldMeshMixin {
    @Inject(method="prepare",at=@At("HEAD"),remap=false) private static void prepare(CallbackInfoReturnable<?> ci) { if(RenderDiag.ENABLED) RenderDiag.prepare.increment(); }
    @Inject(method="bake",at=@At("HEAD"),remap=false) private static void bake(CallbackInfoReturnable<?> ci) { if(RenderDiag.ENABLED) RenderDiag.bake.increment(); }
    @Inject(method="bakePrepared",at=@At("HEAD"),remap=false) private static void prepared(CallbackInfoReturnable<?> ci) { if(RenderDiag.ENABLED) (MinecraftClient.getInstance().isOnThread()?RenderDiag.preparedMain:RenderDiag.preparedWorker).increment(); }
    @Inject(method="install",at=@At("HEAD"),remap=false) private static void install(CompositeBlockEntity owner,@Coerce Object signature,@Coerce Object mesh,CallbackInfoReturnable<?> ci) { if(RenderDiag.ENABLED) ((RootSignature)signature).dwdiag$setRoot(ChunkSectionPos.from(owner.getPos()).asLong()); }
}
