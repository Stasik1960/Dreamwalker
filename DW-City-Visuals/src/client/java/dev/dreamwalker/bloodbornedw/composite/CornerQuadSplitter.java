package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

/** One conservative subdivision along a proven world-cell boundary. */
public final class CornerQuadSplitter {
 private static final double EPS=1e-5;
 public record Piece(BakedQuad quad,float[] brightness,int[] light){}
 public record Split(Piece first,Piece second){}
 private CornerQuadSplitter(){}
 public static Split split(BakedQuad q,StaticLightingViewAccess view,int mask,float[] brightness,int[] light){
  int start=switch(mask){case 9->0;case 3->1;case 6->2;case 12->3;default->-1;};
  if(start<0||brightness==null||brightness.length!=4||light==null||light.length!=4)return null;
  int[] src=q.getVertexData();if(src.length!=32)return null;
  for(int i=1;i<4;i++)if(src[i*8+7]!=src[7])return null;
  int[] index={start,(start+1)%4,(start+2)%4,(start+3)%4};
  double[][] world=new double[4][3];BlockPos root=view.dw$root();
  for(int i=0;i<4;i++){
   int o=index[i]*8;float x=Float.intBitsToFloat(src[o]),y=Float.intBitsToFloat(src[o+1]),z=Float.intBitsToFloat(src[o+2]);
   if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(Float.intBitsToFloat(src[o+4]))||!Float.isFinite(Float.intBitsToFloat(src[o+5]))||!Float.isFinite(brightness[index[i]]))return null;
   Vector3f p=view.dw$transform().transformPosition(new Vector3f(x,y,z));if(!p.isFinite())return null;
   world[i][0]=root.getX()+p.x;world[i][1]=root.getY()+p.y;world[i][2]=root.getZ()+p.z;
  }
  double t=Double.NaN;boolean crossing=false;
  for(int axis=0;axis<3;axis++){
   double a=world[0][axis],b=world[1][axis],c=world[2][axis],d=world[3][axis];
   if(Math.abs(a-d)>EPS||Math.abs(b-c)>EPS||Math.abs(a-b)<EPS)continue;
   double lo=Math.min(a,b),hi=Math.max(a,b),boundary=Math.floor(lo)+1;
   if(boundary<=lo+EPS)boundary+=1;
   if(boundary>=hi-EPS)continue;
   if(boundary+1<hi-EPS)return null;
   double candidate=(boundary-a)/(b-a),other=(boundary-d)/(c-d);
   if(candidate<=EPS||candidate>=1-EPS||Math.abs(candidate-other)>EPS)return null;
   if(crossing&&Math.abs(t-candidate)>EPS)return null;
   crossing=true;t=candidate;
  }
  if(!crossing)return null;
  int[][] v=new int[4][];for(int i=0;i<4;i++){v[i]=new int[8];System.arraycopy(src,index[i]*8,v[i],0,8);}
  int[] a=interpolate(v[0],v[1],t),b=interpolate(v[3],v[2],t);if(a==null||b==null)return null;
  float b0=brightness[index[0]],b1=brightness[index[1]],b2=brightness[index[2]],b3=brightness[index[3]];
  int l0=light[index[0]],l1=light[index[1]],l2=light[index[2]],l3=light[index[3]];
  float ba=(float)(b0+(b1-b0)*t),bb=(float)(b3+(b2-b3)*t);int la=packed(l0,l1,t),lb=packed(l3,l2,t);
  return new Split(piece(q,new int[][]{v[0],a,b,v[3]},new float[]{b0,ba,bb,b3},new int[]{l0,la,lb,l3}),
                   piece(q,new int[][]{a,v[1],v[2],b},new float[]{ba,b1,b2,bb},new int[]{la,l1,l2,lb}));
 }
 private static Piece piece(BakedQuad parent,int[][] v,float[] brightness,int[] light){int[] data=new int[32];for(int i=0;i<4;i++)System.arraycopy(v[i],0,data,i*8,8);return new Piece(new BakedQuad(data,parent.getColorIndex(),parent.getFace(),parent.getSprite(),parent.hasShade()),brightness,light);}
 private static int[] interpolate(int[] a,int[] b,double t){
  if(a[7]!=b[7])return null;int[] out=new int[8];
  for(int i:new int[]{0,1,2,4,5}){float x=Float.intBitsToFloat(a[i]),y=Float.intBitsToFloat(b[i]);float v=(float)(x+(y-x)*t);if(!Float.isFinite(v))return null;out[i]=Float.floatToRawIntBits(v);}
  int color=0;for(int bit=0;bit<32;bit+=8){int x=(a[3]>>>bit)&255,y=(b[3]>>>bit)&255;color|=((int)Math.round(x+(y-x)*t)&255)<<bit;}out[3]=color;
  out[6]=packed(a[6],b[6],t);out[7]=a[7];return out;
 }
 private static int packed(int a,int b,double t){int low=(int)Math.round((a&65535)+((b&65535)-(a&65535))*t);int high=(int)Math.round(((a>>>16)&65535)+(((b>>>16)&65535)-((a>>>16)&65535))*t);return (low&65535)|((high&65535)<<16);}
}
