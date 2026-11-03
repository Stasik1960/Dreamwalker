package dev.dreamwalker.bloodbornedw.composite.overlaymixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CornerLightingContext;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadSplitter;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadCellClipper;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope;
import dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient$SourceTintConsumer",remap=false)
public abstract class StaticCornerLightMixin implements CornerLightingContext {
 @Unique private final CornerSurfaceLight dw$surfaceLight=new CornerSurfaceLight();
 @Unique private StaticLightingViewAccess dw$context;
 @Override public void dw$setCornerContext(StaticLightingViewAccess view){dw$context=view;}
 @Inject(method="flatLighting",at=@At("HEAD"),remap=false)
 private void dw$clearCornerContext(CallbackInfo ci){dw$context=null;}
 @WrapOperation(method="method_22920",at=@At(value="INVOKE",target="Lnet/minecraft/class_4588;method_22920(Lnet/minecraft/class_4587$class_4665;Lnet/minecraft/class_777;[FFFF[IIZ)V",remap=false),remap=false)
 private void dw$corner(VertexConsumer delegate,MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] light,int overlay,boolean useColor,Operation<Void> original){
  if(dw$context==null||!dw$surfaceLight.sample(quad,dw$context,light)){
   original.call(delegate,matrix,quad,brightness,r,g,b,light,overlay,useColor);return;
  }
  int mask=dw$surfaceLight.mixedCornerMask();
  var split=CornerQuadSplitter.split(quad,dw$context,mask,brightness,light);
  if(split==null){
   var cells=CornerQuadCellClipper.clip(quad,dw$context,mask,brightness,light);
   if(cells==null){original.call(delegate,matrix,quad,dw$surfaceLight.brightness(),r,g,b,dw$surfaceLight.lights(),overlay,useColor);return;}
   for(var piece:cells)dw$emitCell(quad,piece,delegate,matrix,r,g,b,overlay,useColor,original);
   return;
  }
  dw$emit(quad,split.first(),delegate,matrix,r,g,b,overlay,useColor,original);
  dw$emit(quad,split.second(),delegate,matrix,r,g,b,overlay,useColor,original);
 }
 @Unique private void dw$emit(BakedQuad parent,CornerQuadSplitter.Piece piece,VertexConsumer delegate,MatrixStack.Entry matrix,float r,float g,float b,int overlay,boolean useColor,Operation<Void> original){
  boolean corrected=dw$surfaceLight.sample(piece.quad(),dw$context,piece.light());
  float[] levels=corrected?dw$surfaceLight.brightness():piece.brightness();int[] lights=corrected?dw$surfaceLight.lights():piece.light();
  CornerQuadParentScope.emit(piece.quad(),parent,()->original.call(delegate,matrix,piece.quad(),levels,r,g,b,lights,overlay,useColor));
 }
 @Unique private void dw$emitCell(BakedQuad parent,CornerQuadSplitter.Piece piece,VertexConsumer delegate,MatrixStack.Entry matrix,float r,float g,float b,int overlay,boolean useColor,Operation<Void> original){
  boolean corrected=dw$surfaceLight.sampleQualified(piece.quad(),dw$context,piece.light());
  float[] levels=corrected?dw$surfaceLight.brightness():piece.brightness();int[] lights=corrected?dw$surfaceLight.lights():piece.light();
  CornerQuadParentScope.emit(piece.quad(),parent,()->original.call(delegate,matrix,piece.quad(),levels,r,g,b,lights,overlay,useColor));
 }
}
