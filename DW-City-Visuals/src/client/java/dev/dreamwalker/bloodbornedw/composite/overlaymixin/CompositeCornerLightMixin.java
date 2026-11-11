package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeCornerLighting;
import dev.dreamwalker.bloodbornedw.composite.CoplanarQuads;
import dev.dreamwalker.bloodbornedw.composite.CornerLightingContext;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadCellClipper;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadSplitter;
import dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the correction at the final Recorder default-quad call, after tint and flat lighting. */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh$Recorder",remap=false)
public abstract class CompositeCornerLightMixin implements CornerLightingContext {
 @Unique private final CornerSurfaceLight dw$surfaceLight=new CornerSurfaceLight();
 @Unique private final CoplanarQuads dw$coplanar = new CoplanarQuads();
 @Unique private StaticLightingViewAccess dw$context;
 @Override public void dw$setCornerContext(StaticLightingViewAccess view){dw$context=view;}
 @Inject(method="flatLighting",at=@At("HEAD"),remap=false)
 private void dw$clearCornerContext(CallbackInfo ci){dw$context=null;}
 @WrapOperation(method="method_22920",at=@At(value="INVOKE",target="Lnet/minecraft/class_4588;method_22920(Lnet/minecraft/class_4587$class_4665;Lnet/minecraft/class_777;[FFFF[IIZ)V",remap=false),remap=false)
 private void dw$corner(@Coerce VertexConsumer delegate,MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] light,int overlay,boolean useColor,Operation<Void> original){
  if(dw$context==null||!CompositeCornerLighting.sample(dw$surfaceLight,quad,dw$context,light)){
   dw$write(delegate,matrix,quad,brightness,r,g,b,light,overlay,useColor,original);return;
  }
  int mask=dw$surfaceLight.mixedCornerMask();
  var split=CornerQuadSplitter.split(quad,dw$context,mask,brightness,light);
  if(split==null){
   var cells=CornerQuadCellClipper.clip(quad,dw$context,mask,brightness,light);
   if(cells==null){dw$write(delegate,matrix,quad,dw$surfaceLight.brightness(),r,g,b,dw$surfaceLight.lights(),overlay,useColor,original);return;}
   for(var piece:cells)dw$emit(quad,piece,true,delegate,matrix,r,g,b,overlay,useColor,original);
   return;
  }
  dw$emit(quad,split.first(),false,delegate,matrix,r,g,b,overlay,useColor,original);
  dw$emit(quad,split.second(),false,delegate,matrix,r,g,b,overlay,useColor,original);
 }
 @Unique private void dw$emit(BakedQuad parent,CornerQuadSplitter.Piece piece,boolean qualified,VertexConsumer delegate,MatrixStack.Entry matrix,float r,float g,float b,int overlay,boolean useColor,Operation<Void> original){
  boolean corrected=qualified?dw$surfaceLight.sampleQualified(piece.quad(),dw$context,piece.light()):dw$surfaceLight.sample(piece.quad(),dw$context,piece.light());
  float[] levels=corrected?dw$surfaceLight.brightness():piece.brightness();int[] lights=corrected?dw$surfaceLight.lights():piece.light();
  CornerQuadParentScope.emit(piece.quad(),parent,()->dw$write(delegate,matrix,piece.quad(),levels,r,g,b,lights,overlay,useColor,original));
 }

 @Unique
 private void dw$write(VertexConsumer delegate, MatrixStack.Entry matrix, BakedQuad quad, float[] brightness,
                       float red, float green, float blue, int[] lights, int overlay, boolean useColor,
                       Operation<Void> original) {
  var pieces = dw$coplanar.clip(matrix.getPositionMatrix(), quad, brightness, lights);
  if (pieces == null) {
   original.call(delegate, matrix, quad, brightness, red, green, blue, lights, overlay, useColor);
   return;
  }
  Object source = CornerQuadParentScope.parentFor(quad);
  Object parent = source == null ? quad : source;
  for (var piece : pieces) {
   CornerQuadParentScope.emit(piece.quad(), parent, () ->
       original.call(delegate, matrix, piece.quad(), piece.brightness(), red, green, blue, piece.light(), overlay, useColor));
  }
 }
}
