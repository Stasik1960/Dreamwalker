package dev.dreamwalker.bloodborneblocks;

import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Cached GUI-slot bounds for one already-baked architectural model. */
final class GuiItemBounds {
 static final float SLOT_SPAN=.84F;
 record Box(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
  float width(){return maxX-minX;} float height(){return maxY-minY;}
 }
 record Fit(float scale,float translateX,float translateY) {}
 static final class Cache {
  private final IdentityHashMap<BakedModel,GuiItemBounds> values=new IdentityHashMap<>();
  GuiItemBounds get(BakedModel model){return values.computeIfAbsent(model,GuiItemBounds::fromModel);}
  int size(){return values.size();}
 }
 private final Fit fit;
 private GuiItemBounds(Fit fit){this.fit=fit;}
 static GuiItemBounds fromModel(BakedModel model){
  Box raw=rawBounds(model);if(raw==null)return new GuiItemBounds(new Fit(1,0,0));
  return new GuiItemBounds(fitForBounds(transform(raw,model.getTransformation().getTransformation(ModelTransformationMode.GUI))));
 }
 static Fit fitForBounds(Box bounds){
  float span=Math.max(bounds.width(),bounds.height());if(!(span>0)||!Float.isFinite(span))return new Fit(1,0,0);
  float scale=Math.min(1,SLOT_SPAN/span);float x=-(bounds.minX+bounds.maxX)*.5F*scale,y=-(bounds.minY+bounds.maxY)*.5F*scale;
  return new Fit(scale,x,y);
 }
 Transformation gui(Transformation original){return new FitTransformation(original,fit);}
 static Matrix4f rendererMatrix(Matrix4f parent,Transformation original,Fit fit){
  MatrixStack stack=new MatrixStack();stack.peek().getPositionMatrix().set(parent);new FitTransformation(original,fit).apply(false,stack);stack.translate(-.5,-.5,-.5);return new Matrix4f(stack.peek().getPositionMatrix());
 }
 private static Box rawBounds(BakedModel model){
  float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
  read(model.getQuads(null,null,Random.create()),bounds);for(Direction face:Direction.values())read(model.getQuads(null,face,Random.create()),bounds);
  return Float.isFinite(bounds[0])?new Box(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]):null;
 }
 private static void read(List<BakedQuad> quads,float[] bounds){for(BakedQuad quad:quads){int[] data=quad.getVertexData();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++){int base=vertex*stride;for(int axis=0;axis<3;axis++){float value=Float.intBitsToFloat(data[base+axis]);bounds[axis]=Math.min(bounds[axis],value);bounds[axis+3]=Math.max(bounds[axis+3],value);}}}}
 private static Box transform(Box box,Transformation transform){
  MatrixStack stack=new MatrixStack();transform.apply(false,stack);stack.translate(-.5,-.5,-.5);Matrix4f matrix=stack.peek().getPositionMatrix();float[] out={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};Vector3f point=new Vector3f();
  for(float x:new float[]{box.minX,box.maxX})for(float y:new float[]{box.minY,box.maxY})for(float z:new float[]{box.minZ,box.maxZ}){matrix.transformPosition(x,y,z,point);out[0]=Math.min(out[0],point.x);out[1]=Math.min(out[1],point.y);out[2]=Math.min(out[2],point.z);out[3]=Math.max(out[3],point.x);out[4]=Math.max(out[4],point.y);out[5]=Math.max(out[5],point.z);}return new Box(out[0],out[1],out[2],out[3],out[4],out[5]);
 }
 private static final class FitTransformation extends Transformation {
  private final Transformation original;private final Fit fit;
  FitTransformation(Transformation original,Fit fit){super(new Vector3f(),new Vector3f(),new Vector3f(1));this.original=original;this.fit=fit;}
  @Override public void apply(boolean leftHanded,MatrixStack matrices){matrices.translate(fit.translateX,fit.translateY,0);matrices.scale(fit.scale,fit.scale,fit.scale);original.apply(leftHanded,matrices);}
 }
}
