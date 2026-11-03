package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Deterministic topology and light fixtures; real screenshots are tested separately. */
public final class CornerQuadCellClipperTest {
 private static final class View implements StaticLightingViewAccess {
  final BlockPos root=new BlockPos(-10,20,-30);final Matrix4f matrix=new Matrix4f().translation(.3f,0,.25f);final Matrix3f normals=new Matrix3f();boolean opaque;
  final BlockRenderView world=(BlockRenderView)Proxy.newProxyInstance(BlockRenderView.class.getClassLoader(),new Class<?>[]{BlockRenderView.class},(proxy,method,args)->{
   if(args!=null&&args.length==1&&args[0] instanceof BlockPos)return (opaque?Blocks.STONE:Blocks.AIR).getDefaultState();
   if(args!=null&&args.length==2&&args[0] instanceof LightType&&args[1] instanceof BlockPos){if(opaque)throw new AssertionError("Opaque cell light was borrowed");return 0;}
   if(args!=null&&args.length==2&&args[0] instanceof Direction&&args[1] instanceof Boolean)return 1f;
   if(method.getName().equals("toString"))return "clip-test-world";
   throw new AssertionError("Unexpected world call: "+method.getName());
  });
  public BlockRenderView dw$delegate(){return world;}public BlockPos dw$root(){return root;}
  public Matrix4f dw$transform(){return matrix;}public Matrix3f dw$normals(){return normals;}
 }
 private static BakedQuad quad(float[][] points){
  int[] data=new int[32];
  for(int i=0;i<4;i++){
   int o=i*8;for(int k=0;k<3;k++)data[o+k]=Float.floatToRawIntBits(points[i][k]);
   int channel=Math.round((points[i][0]+.8f)*255/1.6f);data[o+3]=0xff000000|(channel<<16)|(channel<<8)|channel;
   data[o+4]=Float.floatToRawIntBits((points[i][0]+1)/2);data[o+5]=Float.floatToRawIntBits((points[i][2]+1)/2);
   data[o+6]=(channel<<16)|channel;data[o+7]=0x00007f00;
  }
  return new BakedQuad(data,3,Direction.UP,null,true);
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static float f(int bits){return Float.intBitsToFloat(bits);}
 public static void main(String[]args){
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  View view=new View();float[][] points={{0,0,-.8f},{-.8f,0,0},{0,0,.8f},{.8f,0,0}};BakedQuad parent=quad(points);
  float[] brightness={.5f,0,.5f,1};int[] nativeLight={0x00800040,0,0x00800040,0x01000080};
  var pieces=CornerQuadCellClipper.clip(parent,view,5,brightness,nativeLight);check(pieces!=null&&pieces.size()>2&&pieces.size()<=64,"diamond clipped into bounded cells");
  Map<String,float[]> shared=new HashMap<>();double area=0;
  for(var piece:pieces){
   int[] d=piece.quad().getVertexData();check(piece.quad().getFace()==Direction.UP&&piece.quad().getColorIndex()==3&&piece.quad().hasShade(),"parent metadata");
   check(Arrays.equals(Arrays.copyOfRange(d,16,24),Arrays.copyOfRange(d,24,32)),"fourth vertex duplicates third");
   double x0=f(d[0]),z0=f(d[2]),x1=f(d[8]),z1=f(d[10]),x2=f(d[16]),z2=f(d[18]);
   double signed=(x1-x0)*(z2-z0)-(z1-z0)*(x2-x0);check(signed<0,"winding preserved");area-=signed/2;
   double worldX=view.root.getX()+.3+(x0+x1+x2)/3,worldZ=view.root.getZ()+.25+(z0+z1+z2)/3;
   int cellX=(int)Math.floor(worldX),cellZ=(int)Math.floor(worldZ);
   for(int i=0;i<3;i++){
    int o=i*8;float x=f(d[o]),z=f(d[o+2]),u=f(d[o+4]),v=f(d[o+5]);
    check(view.root.getX()+.3+x>=cellX-1e-5&&view.root.getX()+.3+x<=cellX+1+1e-5&&view.root.getZ()+.25+z>=cellZ-1e-5&&view.root.getZ()+.25+z<=cellZ+1+1e-5,"cell containment at negative coordinates");
    check(Math.abs(u-(x+1)/2)<1e-5&&Math.abs(v-(z+1)/2)<1e-5,"affine UV interpolation");
    String key=Math.round(x*100000)+":"+Math.round(z*100000);float[] previous=shared.putIfAbsent(key,new float[]{u,v,piece.brightness()[i],piece.light()[i]&65535,(piece.light()[i]>>>16)&65535});
    if(previous!=null)check(Math.abs(previous[0]-u)<1e-5&&Math.abs(previous[1]-v)<1e-5&&Math.abs(previous[2]-piece.brightness()[i])<1e-5&&Math.abs(previous[3]-(piece.light()[i]&65535))<=1&&Math.abs(previous[4]-((piece.light()[i]>>>16)&65535))<=1,"shared edge attributes");
   }
   CornerQuadParentScope.emit(piece.quad(),parent,()->check(CornerQuadParentScope.parentFor(piece.quad())==parent,"child parent scope"));
   check(CornerQuadParentScope.parentFor(piece.quad())==null,"parent scope cleanup");
   CornerSurfaceLight sampler=new CornerSurfaceLight();view.opaque=true;
   check(sampler.sampleQualified(piece.quad(),view,piece.light()),"qualified opaque child sampled");check(Arrays.equals(sampler.lights(),piece.light()),"opaque child preserves native lights");
   view.opaque=false;check(sampler.sampleQualified(piece.quad(),view,piece.light()),"qualified transparent child sampled");for(int light:sampler.lights())check(light==0,"dark transparent child did not remain dark");
  }
  check(Math.abs(area-1.28)<1e-5,"area coverage "+area);
  check(CornerQuadCellClipper.clip(parent,view,0,brightness,nativeLight)==null&&CornerQuadCellClipper.clip(parent,view,15,brightness,nativeLight)==null,"only mixed masks accepted");
  int[] mismatch=parent.getVertexData().clone();mismatch[15]^=1;check(CornerQuadCellClipper.clip(new BakedQuad(mismatch,3,Direction.UP,null,true),view,5,brightness,nativeLight)==null,"mismatched normals rejected");
  check(CornerQuadCellClipper.clip(new BakedQuad(new int[28],3,Direction.UP,null,true),view,5,brightness,nativeLight)==null,"invalid layout rejected");
  check(CornerQuadCellClipper.clip(quad(new float[][]{{0,0,-3},{-3,0,0},{0,0,3},{3,0,0}}),view,5,brightness,nativeLight)==null,"cell extent cap");
  check(CornerQuadCellClipper.clip(quad(new float[][]{{0,0,0},{1,0,0},{.5f,0,.2f},{0,0,1}}),view,5,brightness,nativeLight)==null,"concave geometry rejected");
  check(CornerQuadCellClipper.clip(quad(new float[][]{{0,0,0},{1,0,0},{2,0,0},{0,0,1}}),view,5,brightness,nativeLight)==null,"degenerate geometry rejected");
  System.out.println("PASS: bounded negative-coordinate cell coverage and winding, continuous UV/brightness/native light, inherited parent scope, opaque and zero-light fallback, mask/layout/normal/cell/convexity guards");
 }
}
