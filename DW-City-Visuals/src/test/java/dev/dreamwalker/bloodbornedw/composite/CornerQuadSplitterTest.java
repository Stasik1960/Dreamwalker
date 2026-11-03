package dev.dreamwalker.bloodbornedw.composite;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.Arrays;
public final class CornerQuadSplitterTest {
 private static final class View implements StaticLightingViewAccess {
  BlockPos root=new BlockPos(-10,20,-30);Matrix4f matrix=new Matrix4f();
  public BlockRenderView dw$delegate(){return null;} public BlockPos dw$root(){return root;}
  public Matrix4f dw$transform(){return matrix;} public Matrix3f dw$normals(){return new Matrix3f();}
 }
 private static BakedQuad quad(){
  float[][] pts={{-.5f,0,.5f},{.5f,0,-.5f},{.5f,1,-.5f},{-.5f,1,.5f}};int[] d=new int[32];
  for(int i=0;i<4;i++){int o=8*i;for(int j=0;j<3;j++)d[o+j]=Float.floatToRawIntBits(pts[i][j]);d[o+3]=i==0||i==3?0xff000000:0xffffffff;d[o+4]=Float.floatToRawIntBits(i==0||i==3?0f:1f);d[o+5]=Float.floatToRawIntBits(i<2?0f:1f);d[o+6]=i==0||i==3?0:0x00e00070;d[o+7]=0x007f0000;}
  return new BakedQuad(d,2,Direction.NORTH,null,true);
 }
 private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 private static float f(int x){return Float.intBitsToFloat(x);}
 public static void main(String[]args){
  View v=new View();BakedQuad parent=quad();float[] br={.1f,.9f,.8f,.2f};int[] light={0,0x00e00070,0x00e00070,0};
  var split=CornerQuadSplitter.split(parent,v,9,br,light);check(split!=null,"diagonal crossing");int[] a=split.first().quad().getVertexData(),b=split.second().quad().getVertexData();
  check(f(a[8])==0f&&f(a[10])==0f,"negative-coordinate cut");check(Arrays.equals(Arrays.copyOfRange(a,8,16),Arrays.copyOfRange(b,0,8)),"shared edge vertex A");check(Arrays.equals(Arrays.copyOfRange(a,16,24),Arrays.copyOfRange(b,24,32)),"shared edge vertex B");
  check(f(a[12])==.5f&&a[11]==0xff808080,"UV and ABGR interpolation");check(a[14]==0x00700038&&split.first().light()[1]==0x00700038,"packed light interpolation");
  check(Math.abs(split.first().brightness()[1]-.5f)<.0001f,"brightness interpolation");check(a[0]==parent.getVertexData()[0]&&b[8]==parent.getVertexData()[8],"coverage and winding");
  check(split.first().quad().getColorIndex()==2&&split.first().quad().getFace()==Direction.NORTH&&split.first().quad().hasShade(),"metadata");
  check(CornerQuadSplitter.split(parent,v,5,br,light)==null,"nonadjacent mask rejected");
  int[] bad=parent.getVertexData().clone();bad[15]=0;check(CornerQuadSplitter.split(new BakedQuad(bad,2,Direction.NORTH,null,true),v,9,br,light)==null,"normal mismatch rejected");
  v.matrix.translation(.25f,0,0);check(CornerQuadSplitter.split(parent,v,9,br,light)==null,"different crossings rejected");
  check(CornerQuadSplitter.split(new BakedQuad(new int[28],2,Direction.NORTH,null,true),new View(),9,br,light)==null,"unsupported layout rejected");
  System.out.println("PASS: negative boundary, coverage, winding, shared edge, UV/color/light/brightness interpolation, metadata, ambiguous mask/crossings, normal and layout fallback");
 }
}
