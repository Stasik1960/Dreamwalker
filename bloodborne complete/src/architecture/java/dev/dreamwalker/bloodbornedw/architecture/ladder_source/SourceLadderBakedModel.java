package dev.dreamwalker.bloodbornedw.architecture.ladder_source;

import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Original outward-facing mount, shared by ordinary sections and recognized source assemblies.
 * The source model's rungs lie at17.25/16: raw baking hides them inside the
 * backing. The original pair mount puts them at14.75/16, preserving every UV.
 */
@Environment(EnvType.CLIENT)
final class SourceLadderBakedModel extends ForwardingBakedModel {
    private final float shiftX,shiftZ;
    private final Map<BakedQuad,BakedQuad> cache=new IdentityHashMap<>();
    SourceLadderBakedModel(BakedModel model,Direction physicalFacing){wrapped=model;shiftX=-physicalFacing.getOffsetX();shiftZ=-physicalFacing.getOffsetZ();}
    private static Direction face(Direction face){return face!=null&&face.getAxis().isHorizontal()?face.getOpposite():face;}
    private boolean transform(MutableQuadView quad){Direction nominal=quad.nominalFace();for(int i=0;i<4;i++){quad.pos(i,1-quad.x(i)+shiftX,quad.y(i),1-quad.z(i)+shiftZ);if(quad.hasNormal(i))quad.normal(i,-quad.normalX(i),quad.normalY(i),-quad.normalZ(i));}quad.cullFace(null);quad.nominalFace(face(nominal));return true;}
    @Override public boolean isVanillaAdapter(){return false;}
    @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){context.pushTransform(this::transform);try{super.emitBlockQuads(world,state,pos,random,context);}finally{context.popTransform();}}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){if(face!=null)return List.of();List<BakedQuad> result=new ArrayList<>();long seed=random.nextLong();synchronized(cache){for(Direction side:Direction.values())for(BakedQuad quad:wrapped.getQuads(state,side,Random.create(seed)))result.add(cache.computeIfAbsent(quad,this::bake));for(BakedQuad quad:wrapped.getQuads(state,null,Random.create(seed)))result.add(cache.computeIfAbsent(quad,this::bake));}return result;}
    private BakedQuad bake(BakedQuad quad){int[] data=quad.getVertexData().clone();int stride=data.length/4;if(stride<8)throw new IllegalStateException("Unexpected vertex format");for(int i=0;i<4;i++){int b=i*stride;data[b]=Float.floatToRawIntBits(1-Float.intBitsToFloat(data[b])+shiftX);data[b+2]=Float.floatToRawIntBits(1-Float.intBitsToFloat(data[b+2])+shiftZ);int normal=data[b+7];int nx=(-(byte)(normal&255))&255,nz=(-(byte)((normal>>>16)&255))&255;data[b+7]=(normal&0xFF00FF00)|nx|(nz<<16);}return new BakedQuad(data,quad.getColorIndex(),face(quad.getFace()),quad.getSprite(),quad.hasShade());}
}
