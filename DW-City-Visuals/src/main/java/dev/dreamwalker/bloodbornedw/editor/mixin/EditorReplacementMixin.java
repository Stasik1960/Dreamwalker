package dev.dreamwalker.bloodbornedw.editor.mixin;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(WorldChunk.class)
abstract class EditorReplacementMixin {
    @Inject(method="setBlockState",at=@At("RETURN")) private void dw$replace(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir){
        if(cir.getReturnValue()!=null)CityEditorServer.replaced((WorldChunk)(Object)this,pos,cir.getReturnValue(),state);
    }
}
