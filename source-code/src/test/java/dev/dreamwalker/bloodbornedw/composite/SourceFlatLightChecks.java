package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.random.Random;

/** Coordinate regression: an exterior face must not read its opaque root.
 * Also protect ordinary/inset cube semantics and transformed negative positions.
 */
public final class SourceFlatLightChecks {
    private static int checks;
    private static int[] quad(float... xyz){
        int[] data=new int[32];for(int i=0;i<4;i++)for(int j=0;j<3;j++)data[i*8+j]=Float.floatToRawIntBits(xyz[i*3+j]);return data;
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static BlockPos sample(int[] data,Direction face,BlockPos root,Matrix4f transform){
        var s=SourceFlatLight.calculate(data,face);check(s.projected(),"Extended/diagonal source face needs surface coordinates");
        var p=transform.transformPosition(new Vector3f(s.x(),s.y(),s.z()));
        return BlockPos.ofFloored(root.getX()+p.x,root.getY()+p.y,root.getZ()+p.z);
    }
    public static void main(String[] args){
        check(!SourceFlatLight.calculate(quad(0,0,0, 0,1,0, 1,1,0, 1,0,0),Direction.NORTH).projected(),"Unit face keeps native light");
        check(!SourceFlatLight.calculate(quad(0,0,.25f, 0,1,.25f, 1,1,.25f, 1,0,.25f),Direction.NORTH).projected(),"Inset cube face keeps native semantics");
        int[] diamond=quad(.5f,0,-.5f, .5f,1,-.5f, 1.5f,1,.5f, 1.5f,0,.5f);
        check(sample(diamond,Direction.NORTH,new BlockPos(253,66,-915),new Matrix4f()).equals(new BlockPos(254,66,-916)),"Rotated rescaled brick samples the real NE exterior, not root 253/66/-915");
        check(sample(diamond,Direction.NORTH,new BlockPos(-20,66,-20),new Matrix4f().rotateY((float)Math.PI/2)).equals(new BlockPos(-21,66,-22)),"Part rotation and negative coordinates project before flooring");
        int[] extended=quad(-2,3,-1, -2,3,2, 3,3,2, 3,3,-1);
        check(!SourceFlatLight.calculate(extended,Direction.UP).projected(),"Extended cardinal face preserves its native neighbour, not an unproved centre query");
        check(sample(diamond,Direction.NORTH,new BlockPos(0,0,0),new Matrix4f().translation(0,.25f,0)).equals(new BlockPos(1,0,-1)),"Part translation is included once");
        check(!SourceFlatLight.calculate(quad(2,0,0, 2,0,0, 2,0,0, 2,0,0),Direction.EAST).projected(),"Degenerate geometry keeps established light");
        check(!SourceFlatLight.calculate(new int[5],Direction.UP).projected(),"Unknown layout is not guessed");
        int[] invalid=diamond.clone();invalid[0]=Float.floatToIntBits(Float.NaN);
        check(!SourceFlatLight.calculate(invalid,Direction.NORTH).projected(),"Invalid geometry is not projected");
        BakedQuad general=new BakedQuad(diamond.clone(),-1,Direction.NORTH,null,true);
        BakedQuad directional=new BakedQuad(diamond.clone(),-1,Direction.NORTH,null,true);
        var raw=new FixtureModel(general,directional);
        var rootQuads=SourceFlatLight.rootQuads(raw,null);
        check(rootQuads.contains(general),"General face uses the native root-light branch");
        check(!rootQuads.contains(directional),"Equal-looking directional/cull face keeps its native neighbour-light branch");
        check(rootQuads==SourceFlatLight.rootQuads(raw,null),"One resource model reuses finite face membership");
        SourceFlatLight.reload();
        check(rootQuads!=SourceFlatLight.rootQuads(raw,null),"Resource reload releases face-membership metadata");
        // Actual basalt band: centroid lies inside its opacity-15 root, but
        // all four slightly inset exterior corners lie in lit neighbouring cells.
        int[] basalt=quad(1.0625f,1.125f,.9375f, 1.0625f,.25f,.9375f, .0625f,.25f,-.0625f, .0625f,1.125f,-.0625f);
        var basaltQuad=new BakedQuad(basalt,-1,Direction.NORTH,null,true);
        var basaltRoot=new BlockPos(258,71,-919);
        var basaltTransform=new Matrix4f().translate(.5f,0,.5f).rotateY(-(float)Math.PI/2).translate(-.5f,0,-.5f);
        check(SourceFlatLight.worldSample(basaltQuad,basaltRoot,basaltTransform).equals(basaltRoot),"Basalt centroid is inside its opaque carrier");
        check(SourceFlatLight.outsideRoot(SourceFlatLight.worldCorners(basaltQuad,basaltRoot,basaltTransform),basaltRoot),"All four authored basalt corners are exterior after source rotation");
        check(!SourceFlatLight.outsideRoot(new BlockPos[]{basaltRoot,basaltRoot.east(),basaltRoot.west(),basaltRoot.up()},basaltRoot),"One interior corner does not prove the exterior-average case");
        check(!SourceFlatLight.outsideRoot(new BlockPos[0],basaltRoot),"Missing samples cannot prove exposure");
        check(SourceFlatLight.averageLight(new int[]{15<<20,15<<20,15<<20,15<<20})==15<<20,"Uniform exterior daylight is preserved");
        check(SourceFlatLight.averageLight(new int[]{0,0,0,0})==0,"Dark exterior remains dark without a light floor");
        check(SourceFlatLight.averageLight(new int[]{(12<<20)|(8<<4),0,(8<<20)|(4<<4),(4<<20)|(4<<4)})==((6<<20)|(4<<4)),"Sky and block light average independently including zero");
        System.out.println("PASS SourceFlatLightChecks: "+checks+" coordinate and preservation assertions");
    }
    private record FixtureModel(BakedQuad general,BakedQuad directional) implements BakedModel {
        public List<BakedQuad> getQuads(BlockState state,Direction side,Random random){return side==null?List.of(general):side==Direction.NORTH?List.of(directional):List.of();}
        public boolean useAmbientOcclusion(){return true;}public boolean hasDepth(){return true;}public boolean isSideLit(){return true;}public boolean isBuiltin(){return false;}
        public Sprite getParticleSprite(){return null;}public ModelTransformation getTransformation(){return ModelTransformation.NONE;}public ModelOverrideList getOverrides(){return ModelOverrideList.EMPTY;}
    }
}
