package dev.dreamwalker.bloodbornedw.composite;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Exercises the real sampler with a deterministic native-world light view. */
public final class CornerSurfaceLightTest {
 private static final class Context implements StaticLightingViewAccess {
  int sky,block,calls,opaqueMode; BlockPos sampled; final Matrix4f matrix=new Matrix4f(); final Matrix3f normals=new Matrix3f();
  BlockPos root=new BlockPos(10,20,30);
  final BlockRenderView world=(BlockRenderView)Proxy.newProxyInstance(BlockRenderView.class.getClassLoader(),new Class<?>[]{BlockRenderView.class},(proxy,method,args)->{
   if(args!=null&&args.length==1&&args[0] instanceof BlockPos){BlockPos pos=(BlockPos)args[0];boolean center=pos.equals(new BlockPos(9,20,29));boolean corner=pos.equals(new BlockPos(9,20,30));return (opaqueMode!=0&&center)||((opaqueMode==2||opaqueMode==3)&&corner)?net.minecraft.block.Blocks.STONE.getDefaultState():net.minecraft.block.Blocks.AIR.getDefaultState();}
   if(args!=null&&args.length==2&&args[0] instanceof LightType&&args[1] instanceof BlockPos){calls++;BlockPos p=(BlockPos)args[1];sampled=p.toImmutable();if(opaqueMode!=0&&opaqueMode!=3){boolean left=p.getX()==9;return args[0]==LightType.SKY?(left?3:10):(left?1:6);}return args[0]==LightType.SKY?sky:block;}
   if(args!=null&&args.length==2&&args[0] instanceof Direction&&args[1] instanceof Boolean)return args[0]==Direction.WEST?.6f:args[0]==Direction.NORTH?.8f:1f;
   if(method.getName().equals("toString"))return "test-world";
   throw new AssertionError("Unexpected world call: "+method.getName());
  });
  public BlockRenderView dw$delegate(){return world;} public BlockPos dw$root(){return root;}
  public Matrix4f dw$transform(){return matrix;} public Matrix3f dw$normals(){return normals;}
  void reset(int sky,int block){this.sky=sky;this.block=block;calls=0;sampled=null;opaqueMode=0;}
 }
 private static BakedQuad quad(Direction direction,float... xyz){int[] data=new int[32];for(int i=0;i<4;i++)for(int j=0;j<3;j++)data[i*8+j]=Float.floatToRawIntBits(xyz[i*3+j]);return new BakedQuad(data,-1,direction,null,true);}
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 private static void light(CornerSurfaceLight s,int expected){for(int v:s.lights())check(v==expected,"packed light "+Arrays.toString(s.lights())+" expected "+expected);}
 public static void main(String[] args){
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  Context v=new Context();CornerSurfaceLight s=new CornerSurfaceLight();int[] nativeLights={1,2,3,4};
  BakedQuad axis=quad(Direction.NORTH,0,0,0,1,0,0,1,1,0,0,1,0);
  BakedQuad diagonal=quad(Direction.NORTH,-.5f,0,.5f,.5f,0,-.5f,.5f,1,-.5f,-.5f,1,.5f);
  BakedQuad top=quad(Direction.UP,.5f,1,-.5f,1.5f,1,.5f,.5f,1,1.5f,-.5f,1,.5f);
  v.reset(0,0);check(!s.sample(axis,v,nativeLights)&&v.calls==0,"axis changed");
  check(s.sample(diagonal,v,nativeLights),"diagonal skipped");check(v.sampled.equals(new BlockPos(9,20,29)),"outside center "+v.sampled);light(s,0);
  v.reset(15,0);check(s.sample(top,v,nativeLights),"diamond top skipped");check(v.sampled.equals(new BlockPos(10,21,30)),"top outside "+v.sampled);light(s,LightmapTextureManager.pack(0,15));check(s.lights()[0]!=LightmapTextureManager.MAX_LIGHT_COORDINATE,"sky only fullbright");
  v.reset(0,7);check(s.sample(diagonal,v,nativeLights),"block light skipped");light(s,LightmapTextureManager.pack(7,0));
  v.reset(0,0);v.opaqueMode=1;check(s.sample(diagonal,v,nativeLights),"four corners skipped");light(s,(14*4)|((26*4)<<16));check(Math.abs(s.brightness()[0]-.7f)<.0001f,"directional shade lost");
  v.reset(0,0);v.opaqueMode=2;check(s.sample(diagonal,v,nativeLights),"fallback skipped");check(Arrays.equals(s.lights(),nativeLights),"mixed opaque fallback changed native light");check(Math.abs(s.brightness()[0]-.7f)<.0001f,"fallback directional shade lost");
  v.reset(0,0);v.opaqueMode=3;check(s.sample(diagonal,v,nativeLights),"mixed parent skipped");check(s.mixedCornerMask()==6,"mixed mask");
  var pieces=CornerQuadSplitter.split(diagonal,v,s.mixedCornerMask(),new float[]{1,1,1,1},nativeLights);check(pieces!=null,"mixed split skipped");
  check(s.sample(pieces.first().quad(),v,pieces.first().light()),"open child skipped");light(s,0);
  check(s.sample(pieces.second().quad(),v,pieces.second().light()),"closed child skipped");check(Arrays.equals(s.lights(),pieces.second().light()),"closed child lost its interpolated native light");
  v.matrix.rotationY((float)(-Math.PI/4));v.normals.rotationY((float)(-Math.PI/4));v.reset(0,0);check(s.sample(diagonal,v,nativeLights),"compensated rotation skipped");
  v.matrix.identity();v.normals.identity();v.reset(0,0);BakedQuad malformed=quad(Direction.NORTH,Float.NaN,0,0,1,0,0,1,1,0,0,1,0);check(!s.sample(malformed,v,nativeLights)&&v.calls==0,"malformed geometry sampled");
  System.out.println("PASS: axis bypass, exterior position, night zero, sky only, block 7, diamond top, translated root, four-corner channel average, mixed opaque fallback, dark open child and closed child, directional shade, compensated rotation, malformed geometry");
 }
}
