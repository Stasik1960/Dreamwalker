package dev.dreamwalker.bloodbornedw.architecture;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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

/** Rotate the complete baked source once; nested authored rotations and UVs survive. */
@Environment(EnvType.CLIENT)
final class DiagonalBakedModel extends ForwardingBakedModel {
    private static final float C=(float)Math.sqrt(.5);
    private final Map<BakedQuad,BakedQuad> cache=new IdentityHashMap<>();
    DiagonalBakedModel(BakedModel source){wrapped=source;}
    @Override public boolean isVanillaAdapter(){return false;}
    private static Direction rotatedFace(Direction face){return face==null||!face.getAxis().isHorizontal()?face:Direction.getFacing(C*(face.getOffsetX()-face.getOffsetZ()),face.getOffsetY(),C*(face.getOffsetX()+face.getOffsetZ()));}
    private boolean rotate(MutableQuadView quad){
        Direction face=quad.nominalFace();
        for(int vertex=0;vertex<4;vertex++){
            float x=quad.x(vertex)-.5F,z=quad.z(vertex)-.5F;
            quad.pos(vertex,.5F+C*(x-z),quad.y(vertex),.5F+C*(x+z));
            if(quad.hasNormal(vertex)){float nx=quad.normalX(vertex),nz=quad.normalZ(vertex);quad.normal(vertex,C*(nx-nz),quad.normalY(vertex),C*(nx+nz));}
        }
        quad.cullFace(null);quad.nominalFace(rotatedFace(face));return true;
    }
    @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){
        context.pushTransform(this::rotate);try{super.emitBlockQuads(world,state,pos,random,context);}finally{context.popTransform();}
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){
        if(face!=null)return List.of(); // Diagonal faces cannot be culled against a cardinal neighbor.
        List<BakedQuad> result=new ArrayList<>();long seed=random.nextLong();
        synchronized(cache){
            for(Direction direction:Direction.values())for(BakedQuad quad:wrapped.getQuads(state,direction,Random.create(seed)))result.add(cache.computeIfAbsent(quad,this::rotateVanilla));
            for(BakedQuad quad:wrapped.getQuads(state,null,Random.create(seed)))result.add(cache.computeIfAbsent(quad,this::rotateVanilla));
        }
        return result;
    }
    private BakedQuad rotateVanilla(BakedQuad quad){
        int[] data=quad.getVertexData().clone();int stride=data.length/4;
        if(stride<8)throw new IllegalStateException("Unexpected baked vertex format");
        for(int vertex=0;vertex<4;vertex++){
            int base=vertex*stride;float x=Float.intBitsToFloat(data[base])-.5F,z=Float.intBitsToFloat(data[base+2])-.5F;
            data[base]=Float.floatToRawIntBits(.5F+C*(x-z));data[base+2]=Float.floatToRawIntBits(.5F+C*(x+z));
            int normal=data[base+7];float nx=(byte)(normal&255)/127F,nz=(byte)((normal>>>16)&255)/127F;
            int rx=Math.round(Math.max(-1,Math.min(1,C*(nx-nz)))*127)&255,rz=Math.round(Math.max(-1,Math.min(1,C*(nx+nz)))*127)&255;
            data[base+7]=(normal&0xFF00FF00)|rx|(rz<<16);
        }
        return new BakedQuad(data,quad.getColorIndex(),rotatedFace(quad.getFace()),quad.getSprite(),quad.hasShade());
    }
}
