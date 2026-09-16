package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Client-only translation around the complete baked model, including emissive passes. */
final class TranslatedBakedModel extends ForwardingBakedModel {
 private final float x,y,z;
 private final Map<BakedQuad,BakedQuad> vanillaCache=new IdentityHashMap<>();

 TranslatedBakedModel(BakedModel model,double[] offset){wrapped=model;x=(float)offset[0];y=(float)offset[1];z=(float)offset[2];}

 @Override public boolean isVanillaAdapter(){return false;}

 private boolean translate(MutableQuadView quad){
  for(int vertex=0;vertex<4;vertex++)quad.pos(vertex,quad.x(vertex)+x,quad.y(vertex)+y,quad.z(vertex)+z);return true;
 }

 @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){
  context.pushTransform(this::translate);try{super.emitBlockQuads(world,state,pos,random,context);}finally{context.popTransform();}
 }

 @Override public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){
  List<BakedQuad> source=wrapped.getQuads(state,face,random);if(source.isEmpty())return source;
  List<BakedQuad> translated=new ArrayList<>(source.size());synchronized(vanillaCache){for(BakedQuad quad:source)translated.add(vanillaCache.computeIfAbsent(quad,this::translateVanilla));}return translated;
 }

 private BakedQuad translateVanilla(BakedQuad quad){
  int[] data=quad.getVertexData().clone();int stride=data.length/4;
  for(int vertex=0;vertex<4;vertex++){int base=vertex*stride;data[base]=Float.floatToRawIntBits(Float.intBitsToFloat(data[base])+x);data[base+1]=Float.floatToRawIntBits(Float.intBitsToFloat(data[base+1])+y);data[base+2]=Float.floatToRawIntBits(Float.intBitsToFloat(data[base+2])+z);}
  return new BakedQuad(data,quad.getColorIndex(),quad.getFace(),quad.getSprite(),quad.hasShade());
 }
}
