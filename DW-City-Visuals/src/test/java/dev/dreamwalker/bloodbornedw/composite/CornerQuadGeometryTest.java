package dev.dreamwalker.bloodbornedw.composite;
public final class CornerQuadGeometryTest {
 public static void main(String[] args) {
  float[][] faces={{1,0,0,0,1,0,0,0,1},{-1,0,0,0,1,0,0,0,1},{0,1,0,1,0,0,0,0,1},{0,-1,0,1,0,0,0,0,1},{0,0,1,1,0,0,0,1,0},{0,0,-1,1,0,0,0,1,0}};
  for(float[] f:faces){if(test(f))throw new AssertionError("axis-aligned face selected");float[] d=f.clone();for(int i=0;i<9;i+=3){float x=d[i],z=d[i+2];d[i]=(x-z)*.70710677f;d[i+2]=(x+z)*.70710677f;}if(!test(d))throw new AssertionError("45-degree face missed");}
  if(CornerQuadGeometry.shouldCorrect(Float.NaN,0,1,1,0,0,0,1,0)||CornerQuadGeometry.shouldCorrect(0,1,0,0,0,0,0,0,1)||CornerQuadGeometry.shouldCorrect(0,1,0,1,0,0,2,0,0))throw new AssertionError("invalid geometry selected");
  float[] compensated={0,1,0,1,0,0,0,0,1};
  float[] baked={0,1,0,.70710677f,0,.70710677f,-.70710677f,0,.70710677f};
  if(!(test(baked)||test(compensated)))throw new AssertionError("compensated baked rotation missed");
  System.out.println("PASS: six axis faces, six rotated faces, three invalid cases and compensated rotation");
 }
 private static boolean test(float[] f){return CornerQuadGeometry.shouldCorrect(f[0],f[1],f[2],f[3],f[4],f[5],f[6],f[7],f[8]);}
}
