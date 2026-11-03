package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Conservative, bounded horizontal subdivision at world-cell boundaries. */
public final class CornerQuadCellClipper {
 private static final double EPS=1e-7,AREA_EPS=1e-10;
 private static final int MAX_AXIS_CELLS=3,MAX_TRIANGLES=64;
 // x/z world offsets; local XYZ; UV; four ABGR bytes; baked and native light channels; brightness.
 private static final int WX=0,WZ=1,LX=2,LY=3,LZ=4,U=5,V=6,COLOR=7,BAKED=11,NATIVE=13,BRIGHT=15,FIELDS=16;
 private CornerQuadCellClipper(){}

 /** Returns null without emitting anything if geometry or subdivision is unsuitable. */
 public static List<CornerQuadSplitter.Piece> clip(BakedQuad quad,StaticLightingViewAccess view,int mask,float[] brightness,int[] nativeLight){
  if(quad==null||view==null||view.dw$root()==null||view.dw$transform()==null||view.dw$normals()==null||mask<=0||mask>=15||brightness==null||brightness.length!=4||nativeLight==null||nativeLight.length!=4)return null;
  int[] data=quad.getVertexData();if(data==null||data.length!=32)return null;
  for(int i=1;i<4;i++)if(data[i*8+7]!=data[7])return null;
  Matrix4f matrix=view.dw$transform();
  if(!matrix.isFinite()||Math.abs(matrix.m03())>EPS||Math.abs(matrix.m13())>EPS||Math.abs(matrix.m23())>EPS||Math.abs(matrix.m33()-1)>EPS)return null;
  BlockPos root=view.dw$root();double[][] vertices=new double[4][FIELDS];double[] ys=new double[4];
  for(int i=0;i<4;i++){
   int o=i*8;double[] p=vertices[i];
   p[LX]=Float.intBitsToFloat(data[o]);p[LY]=Float.intBitsToFloat(data[o+1]);p[LZ]=Float.intBitsToFloat(data[o+2]);p[U]=Float.intBitsToFloat(data[o+4]);p[V]=Float.intBitsToFloat(data[o+5]);p[BRIGHT]=brightness[i];
   for(int j=LX;j<=V;j++)if(!Double.isFinite(p[j]))return null;
   if(!Double.isFinite(p[BRIGHT]))return null;
   // Keep world coordinates as double through clipping, including negative positions.
   p[WX]=root.getX()+(double)matrix.m00()*p[LX]+(double)matrix.m10()*p[LY]+(double)matrix.m20()*p[LZ]+matrix.m30();
   ys[i]=root.getY()+(double)matrix.m01()*p[LX]+(double)matrix.m11()*p[LY]+(double)matrix.m21()*p[LZ]+matrix.m31();
   p[WZ]=root.getZ()+(double)matrix.m02()*p[LX]+(double)matrix.m12()*p[LY]+(double)matrix.m22()*p[LZ]+matrix.m32();
   if(!Double.isFinite(p[WX])||!Double.isFinite(ys[i])||!Double.isFinite(p[WZ]))return null;
   for(int j=0;j<4;j++)p[COLOR+j]=(data[o+3]>>>(8*j))&255;
   p[BAKED]=data[o+6]&65535;p[BAKED+1]=(data[o+6]>>>16)&65535;
   p[NATIVE]=nativeLight[i]&65535;p[NATIVE+1]=(nativeLight[i]>>>16)&65535;
  }
  // The geometry and transformed normal must both be horizontal; reject skewed/non-convex quads.
  for(int i=1;i<4;i++)if(Math.abs(ys[i]-ys[0])>EPS)return null;
  Vector3f a=new Vector3f((float)(vertices[1][LX]-vertices[0][LX]),(float)(vertices[1][LY]-vertices[0][LY]),(float)(vertices[1][LZ]-vertices[0][LZ]));
  Vector3f b=new Vector3f((float)(vertices[3][LX]-vertices[0][LX]),(float)(vertices[3][LY]-vertices[0][LY]),(float)(vertices[3][LZ]-vertices[0][LZ]));
  Vector3f n=a.cross(b);view.dw$normals().transform(n);
  if(!n.isFinite()||n.lengthSquared()<1e-10f||Math.abs(n.x)>Math.abs(n.y)*1e-5||Math.abs(n.z)>Math.abs(n.y)*1e-5)return null;
  double direction=0;
  for(int i=0;i<4;i++){
   double cross=area(vertices[i],vertices[(i+1)%4],vertices[(i+2)%4]);
   if(!Double.isFinite(cross)||Math.abs(cross)<AREA_EPS)return null;
   if(direction==0)direction=Math.signum(cross);else if(cross*direction<=0)return null;
  }
  double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=minX,maxZ=maxX;
  for(double[] p:vertices){minX=Math.min(minX,p[WX]);maxX=Math.max(maxX,p[WX]);minZ=Math.min(minZ,p[WZ]);maxZ=Math.max(maxZ,p[WZ]);}
  if(minX<Integer.MIN_VALUE+2d||maxX>Integer.MAX_VALUE-2d||minZ<Integer.MIN_VALUE+2d||maxZ>Integer.MAX_VALUE-2d)return null;
  int x0=(int)Math.floor(minX+EPS),x1=(int)Math.ceil(maxX-EPS)-1,z0=(int)Math.floor(minZ+EPS),z1=(int)Math.ceil(maxZ-EPS)-1;
  if(x1<x0||z1<z0||x1-x0+1>MAX_AXIS_CELLS||z1-z0+1>MAX_AXIS_CELLS||(x0==x1&&z0==z1))return null;
  List<CornerQuadSplitter.Piece> result=new ArrayList<>();int[][] triangles={{0,1,2},{0,2,3}};
  for(int[] triangle:triangles)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
   List<double[]> polygon=new ArrayList<>();for(int index:triangle)polygon.add(vertices[index]);
   polygon=clipEdge(polygon,WX,x,true);polygon=clipEdge(polygon,WX,x+1d,false);
   polygon=clipEdge(polygon,WZ,z,true);polygon=clipEdge(polygon,WZ,z+1d,false);
   if(polygon.size()<3)continue;
   for(int i=1;i+1<polygon.size();i++){
    double[] p=polygon.get(0),q=polygon.get(i),r=polygon.get(i+1);double signed=area(p,q,r);
    if(Math.abs(signed)<=AREA_EPS)continue;
    if(!Double.isFinite(signed)||signed*direction<=0||result.size()>=MAX_TRIANGLES)return null;
    CornerQuadSplitter.Piece piece=piece(quad,p,q,r,data[7]);if(piece==null||!probesStayInCell(piece.quad(),view,x,z))return null;result.add(piece);
   }
  }
  return result.isEmpty()?null:List.copyOf(result);
 }

 private static List<double[]> clipEdge(List<double[]> input,int axis,double boundary,boolean lower){
  List<double[]> result=new ArrayList<>();if(input.isEmpty())return result;
  double[] previous=input.get(input.size()-1);boolean previousInside=inside(previous[axis],boundary,lower);
  for(double[] current:input){
   boolean currentInside=inside(current[axis],boundary,lower);
   if(currentInside!=previousInside){
    double denominator=current[axis]-previous[axis];
    if(Math.abs(denominator)>1e-15){double t=(boundary-previous[axis])/denominator;double[] cut=new double[FIELDS];for(int j=0;j<FIELDS;j++)cut[j]=previous[j]+(current[j]-previous[j])*t;cut[axis]=boundary;result.add(cut);}
   }
   if(currentInside)result.add(current);
   previous=current;previousInside=currentInside;
  }
  return result;
 }
 private static boolean inside(double value,double boundary,boolean lower){return lower?value>=boundary:value<=boundary;}
 private static double area(double[] p,double[] q,double[] r){return (q[WX]-p[WX])*(r[WZ]-p[WZ])-(q[WZ]-p[WZ])*(r[WX]-p[WX]);}
 private static CornerQuadSplitter.Piece piece(BakedQuad parent,double[] a,double[] b,double[] c,int normal){
  double[][] points={a,b,c,c};int[] data=new int[32],light=new int[4];float[] brightness=new float[4];
  for(int i=0;i<4;i++){
   double[] p=points[i];int o=i*8;
   for(int j=0;j<3;j++){float value=(float)p[LX+j];if(!Float.isFinite(value))return null;data[o+j]=Float.floatToRawIntBits(value);}
   int color=0;for(int j=0;j<4;j++)color|=(clamp(p[COLOR+j],255)<<(j*8));data[o+3]=color;
   float u=(float)p[U],v=(float)p[V],shade=(float)p[BRIGHT];if(!Float.isFinite(u)||!Float.isFinite(v)||!Float.isFinite(shade))return null;
   data[o+4]=Float.floatToRawIntBits(u);data[o+5]=Float.floatToRawIntBits(v);data[o+6]=packed(p[BAKED],p[BAKED+1]);data[o+7]=normal;
   light[i]=packed(p[NATIVE],p[NATIVE+1]);brightness[i]=shade;
  }
  // A rounded local vertex must still form a finite nondegenerate triangle.
  double ax=Float.intBitsToFloat(data[8])-Float.intBitsToFloat(data[0]),ay=Float.intBitsToFloat(data[9])-Float.intBitsToFloat(data[1]),az=Float.intBitsToFloat(data[10])-Float.intBitsToFloat(data[2]);
  double bx=Float.intBitsToFloat(data[16])-Float.intBitsToFloat(data[0]),by=Float.intBitsToFloat(data[17])-Float.intBitsToFloat(data[1]),bz=Float.intBitsToFloat(data[18])-Float.intBitsToFloat(data[2]);
  double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;if(nx*nx+ny*ny+nz*nz<1e-16)return null;
  return new CornerQuadSplitter.Piece(new BakedQuad(data,parent.getColorIndex(),parent.getFace(),parent.getSprite(),parent.hasShade()),brightness,light);
 }
 private static int clamp(double value,int max){return (int)Math.max(0,Math.min(max,Math.round(value)));}
 private static int packed(double low,double high){return clamp(low,65535)|(clamp(high,65535)<<16);}
 /** Match the qualified sampler's actual float transforms before accepting the entire parent. */
 private static boolean probesStayInCell(BakedQuad quad,StaticLightingViewAccess view,int cellX,int cellZ){
  int[] data=quad.getVertexData();Vector3f[] p=new Vector3f[4];Vector3f center=new Vector3f();
  for(int i=0;i<4;i++){int o=i*8;p[i]=new Vector3f(Float.intBitsToFloat(data[o]),Float.intBitsToFloat(data[o+1]),Float.intBitsToFloat(data[o+2]));center.add(p[i]);}
  Vector3f e1=new Vector3f(p[1]).sub(p[0]),e2=new Vector3f(p[3]).sub(p[0]),normal=e1.cross(e2);
  var face=quad.getFace();if(normal.x*face.getOffsetX()+normal.y*face.getOffsetY()+normal.z*face.getOffsetZ()<0)normal.negate();
  view.dw$normals().transform(normal);if(!normal.isFinite()||normal.lengthSquared()<1e-10f)return false;normal.normalize();
  center.mul(.25f);view.dw$transform().transformPosition(center);
  Vector3f point=new Vector3f(center).add(normal.x*.001f,normal.y*.001f,normal.z*.001f);
  if(!inCell(point,view.dw$root(),cellX,cellZ))return false;
  for(Vector3f vertex:p){point.set(vertex);view.dw$transform().transformPosition(point);point.lerp(center,.01f).add(normal.x*.001f,normal.y*.001f,normal.z*.001f);if(!inCell(point,view.dw$root(),cellX,cellZ))return false;}
  return true;
 }
 private static boolean inCell(Vector3f point,BlockPos root,int x,int z){return point.isFinite()&&Math.floor((double)root.getX()+point.x)==x&&Math.floor((double)root.getZ()+point.z)==z;}
}
