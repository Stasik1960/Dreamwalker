package dev.dreamwalker.editorqa.mixin;
import dev.dreamwalker.editorqa.EditorModelProbe;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.block.BlockState;
import net.minecraft.world.BlockRenderView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(targets="dev.dreamwalker.bloodbornedw.visual.FacadeModel",remap=false)
public abstract class EditorFacadeProbeMixin {
 @Inject(method="emitBlockQuads",at=@At("HEAD"),remap=false)
 private void head(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context,CallbackInfo ci){EditorModelProbe.emit(true,pos,world,this);}
 @Inject(method="emitBlockQuads",at=@At("RETURN"),remap=false)
 private void tail(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context,CallbackInfo ci){EditorModelProbe.emit(false,pos,world,this);}
 @WrapMethod(method="method_4707",remap=false)
 private List<BakedQuad> quads(BlockState state,Direction side,Random random,Operation<List<BakedQuad>> original){
  EditorModelProbe.QuadContext previous=EditorModelProbe.beginModelQuads(this);
  try{List<BakedQuad> result=original.call(state,side,random);EditorModelProbe.quads(this,result==null?-1:result.size());return result;}
  finally{EditorModelProbe.endModelQuads(previous);}
 }
}
