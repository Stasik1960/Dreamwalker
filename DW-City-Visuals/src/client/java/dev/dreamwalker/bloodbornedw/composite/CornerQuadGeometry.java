package dev.dreamwalker.bloodbornedw.composite;
/** Classifies a transformed quad using its normal and two adjacent edges. */
public final class CornerQuadGeometry {
 private CornerQuadGeometry() {}
 public static boolean shouldCorrect(float nx,float ny,float nz,float ax,float ay,float az,float bx,float by,float bz) {
  if(!finite(nx,ny,nz)||!finite(ax,ay,az)||!finite(bx,by,bz))return false;
  if(nx*nx+ny*ny+nz*nz<1e-10f||ax*ax+ay*ay+az*az<1e-10f||bx*bx+by*by+bz*bz<1e-10f)return false;
  float cx=ay*bz-az*by,cy=az*bx-ax*bz,cz=ax*by-ay*bx;
  if(!finite(cx,cy,cz)||cx*cx+cy*cy+cz*cz<1e-10f)return false;
  return oblique(nx,ny,nz)||oblique(ax,ay,az)||oblique(bx,by,bz);
 }
 private static boolean finite(float x,float y,float z){return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z);}
 private static boolean oblique(float x,float y,float z){return ((Math.abs(x)>1e-4f?1:0)+(Math.abs(y)>1e-4f?1:0)+(Math.abs(z)>1e-4f?1:0))>1;}
}
