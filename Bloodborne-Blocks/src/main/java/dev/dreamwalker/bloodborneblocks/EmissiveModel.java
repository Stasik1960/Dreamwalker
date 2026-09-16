package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/** An extra emissive pass over _e companions. All overlay data stays local to this pack's namespace. */
public final class EmissiveModel extends ForwardingBakedModel {
 public record Pair(Sprite base,Sprite glow){}
 private final List<Pair>pairs;
 private volatile RenderMaterial glowMaterial;
 private static Object continuityApi;
 private static Method continuityGet;
 static {
  if(FabricLoader.getInstance().isModLoaded("continuity"))try{
   Class<?>api=Class.forName("me.pepperbell.continuity.api.client.EmissiveSpriteApi");continuityApi=api.getMethod("get").invoke(null);continuityGet=api.getMethod("getEmissiveSprite",Sprite.class);
  }catch(ReflectiveOperationException ignored){continuityApi=null;}
 }
 public EmissiveModel(BakedModel model,List<Pair>pairs){wrapped=model;this.pairs=List.copyOf(pairs);}
 @Override public boolean isVanillaAdapter(){return false;}
 private RenderMaterial material(){
  var renderer=RendererAccess.INSTANCE.getRenderer();if(renderer==null)return null;
  RenderMaterial m=glowMaterial;if(m==null){m=renderer.materialFinder().blendMode(BlendMode.CUTOUT).emissive(true).disableDiffuse(true).ambientOcclusion(TriState.FALSE).find();glowMaterial=m;}return m;
 }
 private boolean overlay(MutableQuadView quad){
  RenderMaterial m=material();if(m==null)return false;
  float u=0,v=0;for(int i=0;i<4;i++){u+=quad.u(i);v+=quad.v(i);}u/=4;v/=4;
  for(Pair p:pairs){Sprite b=p.base();if(u<b.getMinU()||u>b.getMaxU()||v<b.getMinV()||v>b.getMaxV())continue;
   // If an active Continuity resource pack already supplies this companion, avoid duplicate overlays.
   if(continuityApi!=null)try{if(continuityGet.invoke(continuityApi,b)!=null)return false;}catch(ReflectiveOperationException ignored){}
   Sprite e=p.glow();for(int i=0;i<4;i++){
    float su=(quad.u(i)-b.getMinU())/(b.getMaxU()-b.getMinU()),sv=(quad.v(i)-b.getMinV())/(b.getMaxV()-b.getMinV());quad.uv(i,e.getMinU()+su*(e.getMaxU()-e.getMinU()),e.getMinV()+sv*(e.getMaxV()-e.getMinV()));quad.lightmap(i,0x00F000F0);
   }quad.material(m);return true;
  }return false;
 }
 @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random>random,RenderContext context){
  super.emitBlockQuads(world,state,pos,random,context);context.pushTransform(this::overlay);try{super.emitBlockQuads(world,state,pos,random,context);}finally{context.popTransform();}
 }
 @Override public void emitItemQuads(ItemStack stack,Supplier<Random>random,RenderContext context){
  super.emitItemQuads(stack,random,context);context.pushTransform(this::overlay);try{super.emitItemQuads(stack,random,context);}finally{context.popTransform();}
 }
}
