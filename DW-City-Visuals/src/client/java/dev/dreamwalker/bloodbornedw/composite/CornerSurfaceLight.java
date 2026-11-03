package dev.dreamwalker.bloodbornedw.composite;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.CompositeLightingViewAccess;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import org.joml.Vector3f;

/** Lighting for oblique source geometry, evaluated only while rebuilding terrain. */
public final class CornerSurfaceLight {
 private final float[] levels=new float[4];
 private final int[] lights=new int[4];
 private int mixedCornerMask=-1;
 public int mixedCornerMask(){return mixedCornerMask;}
 private final Vector3f[] p={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
 private final Vector3f edge1=new Vector3f(),edge2=new Vector3f(),n=new Vector3f(),center=new Vector3f(),localCenter=new Vector3f(),near=new Vector3f();
 public float[] brightness(){return levels;}
 public int[] lights(){return lights;}
 public boolean sample(BakedQuad quad,StaticLightingViewAccess view,int[] nativeLights){
  return sampleInternal(quad,view,nativeLights,false);
 }
 /** Only for children produced by the strictly qualified cell clipper. */
 public boolean sampleQualified(BakedQuad quad,StaticLightingViewAccess view,int[] nativeLights){
  return sampleInternal(quad,view,nativeLights,true);
 }
 private boolean sampleInternal(BakedQuad quad,StaticLightingViewAccess view,int[] nativeLights,boolean qualified){
  mixedCornerMask=-1;
  if(nativeLights==null||nativeLights.length!=4)return false;
  int[] data=quad.getVertexData();
  if(data.length%4!=0||data.length/4<3){return false;}
  int stride=data.length/4;
  for(int i=0;i<4;i++){int j=i*stride;p[i].set(Float.intBitsToFloat(data[j]),Float.intBitsToFloat(data[j+1]),Float.intBitsToFloat(data[j+2]));if(!p[i].isFinite()){return false;}}
  edge1.set(p[1]).sub(p[0]);edge2.set(p[3]).sub(p[0]);
  n.set(edge1).cross(edge2);
  if(n.lengthSquared()<1e-10f){return false;}
  Direction face=quad.getFace();
  if(n.x*face.getOffsetX()+n.y*face.getOffsetY()+n.z*face.getOffsetZ()<0)n.negate();
  boolean localOblique=CornerQuadGeometry.shouldCorrect(n.x,n.y,n.z,edge1.x,edge1.y,edge1.z,edge2.x,edge2.y,edge2.z);
  view.dw$normals().transform(n);
  view.dw$transform().transformDirection(edge1);view.dw$transform().transformDirection(edge2);
  if(!n.isFinite()||!edge1.isFinite()||!edge2.isFinite()||(!qualified&&!localOblique&&!CornerQuadGeometry.shouldCorrect(n.x,n.y,n.z,edge1.x,edge1.y,edge1.z,edge2.x,edge2.y,edge2.z))){return false;}
  if(!Float.isFinite(n.lengthSquared())||n.lengthSquared()<1e-10f){return false;}
  n.normalize();
  localCenter.zero();for(Vector3f point:p)localCenter.add(point);localCenter.mul(.25f);
  center.set(localCenter);
  view.dw$transform().transformPosition(center);
  near.set(center).add(n.x*.001f,n.y*.001f,n.z*.001f);
  if(!center.isFinite()||!near.isFinite()){return false;}
  BlockPos root=view.dw$root();BlockPos sample=position(root,near,qualified);
  var world=view.dw$delegate();track(view,sample);
  if(world.getBlockState(sample).getOpacity(world,sample)<15){
   int light=LightmapTextureManager.pack(world.getLightLevel(LightType.BLOCK,sample),world.getLightLevel(LightType.SKY,sample));
   java.util.Arrays.fill(lights,light);
  }else{
   int block=0,sky=0,mask=0;boolean valid=true;
   for(int i=0;i<4;i++){
    Vector3f point=p[i];
    near.set(point);view.dw$transform().transformPosition(near);near.lerp(center,.01f).add(n.x*.001f,n.y*.001f,n.z*.001f);
    if(!near.isFinite()){valid=false;continue;}
    BlockPos corner=position(root,near,qualified);track(view,corner);
    if(world.getBlockState(corner).getOpacity(world,corner)>=15){valid=false;continue;}
    mask|=1<<i;block+=world.getLightLevel(LightType.BLOCK,corner);sky+=world.getLightLevel(LightType.SKY,corner);
   }
   if(valid)java.util.Arrays.fill(lights,((block*4)&0xffff)|(((sky*4)&0xffff)<<16));
   else {mixedCornerMask=mask;System.arraycopy(nativeLights,0,lights,0,4);}
  }
  float x=Math.abs(n.x),y=Math.abs(n.y),z=Math.abs(n.z),total=x+y+z;
  float shade=(x*world.getBrightness(n.x<0?Direction.WEST:Direction.EAST,quad.hasShade())+y*world.getBrightness(n.y<0?Direction.DOWN:Direction.UP,quad.hasShade())+z*world.getBrightness(n.z<0?Direction.NORTH:Direction.SOUTH,quad.hasShade()))/total;
  java.util.Arrays.fill(levels,shade);
  return true;
 }
 private static BlockPos position(BlockPos root,Vector3f point,boolean qualified){
  if(qualified)return BlockPos.ofFloored((double)root.getX()+point.x,(double)root.getY()+point.y,(double)root.getZ()+point.z);
  return BlockPos.ofFloored(root.getX()+point.x,root.getY()+point.y,root.getZ()+point.z);
 }
 private static void track(StaticLightingViewAccess view,BlockPos pos){if(view instanceof CompositeLightingViewAccess composite)composite.dw$dependency(pos);}
}
