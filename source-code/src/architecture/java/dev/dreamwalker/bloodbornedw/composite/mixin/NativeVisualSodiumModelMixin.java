package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.NativeGridClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext",remap=false)
public abstract class NativeVisualSodiumModelMixin {
    @Unique private BlockRenderView dw$visualSlice;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void dw$slice(@Coerce Object world,CallbackInfo ci){dw$visualSlice=(BlockRenderView)world;}
    @ModifyVariable(method="update",at=@At("HEAD"),argsOnly=true,remap=false)
    private BakedModel dw$resolve(BakedModel model,BlockPos pos,BlockPos origin,BlockState state,BakedModel original,long seed){return NativeGridClient.terrainModel(dw$visualSlice,state,pos,model);}
}
