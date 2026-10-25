package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.joml.*;
import dev.dreamwalker.bloodbornedw.composite.mixin.StaticArchitectureSpriteAccess;

/** Opt-in bounded observation of the actual prelit vertex output. No minimum
 * brightness, light changes or rendering overrides are applied by this probe. */
public final class SourceLightingDiagnostics {
    private static final String OUTPUT=System.getProperty("dw.source.light.output");
    private static final Set<String> SEEN=new HashSet<>();
    private static String phase="AO_ON";
    public static synchronized void phase(String value){phase=value;SEEN.clear();}
    private SourceLightingDiagnostics(){}
    private static boolean near(BlockPos p,int x,int y,int z,int radius){return java.lang.Math.abs(p.getX()-x)<=radius&&java.lang.Math.abs(p.getY()-y)<=radius&&java.lang.Math.abs(p.getZ()-z)<=radius;}
    public static boolean enabled(BlockPos root){return OUTPUT!=null&&(near(root,75,26,-851,3)||near(root,30,0,-961,4)||near(root,6,23,-864,12)||near(root,85,48,-1011,5));}
    public static synchronized void record(BlockRenderView world,BlockPos root,Identifier model,BlockState carrier,Matrix4f transform,BakedQuad quad,float[] brightness,int[] lights){
        recordAdjusted(world,root,model,carrier,transform,quad,brightness,brightness,lights);
    }
    public static synchronized void recordAdjusted(BlockRenderView world,BlockPos root,Identifier model,BlockState carrier,Matrix4f transform,BakedQuad quad,float[] rawBrightness,float[] brightness,int[] lights){
        if(!enabled(root)||SEEN.size()>=12000)return;
        int[] data=quad.getVertexData();int stride=data.length/4;List<List<Float>> points=new ArrayList<>();float x=0,y=0,z=0;
        for(int v=0;v<4;v++){int at=v*stride;var point=transform.transformPosition(new Vector3f(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2])));points.add(List.of(point.x+root.getX(),point.y+root.getY(),point.z+root.getZ()));x+=point.x/4;y+=point.y/4;z+=point.z/4;}
        String key=root+"/"+model+"/"+points+"/"+quad.getSprite().getContents().getId();if(!SEEN.add(key))return;
        var face=quad.getFace();var normal=new Vector3f(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());new Matrix3f(transform).transform(normal).normalize();
        var surface=BlockPos.ofFloored(root.getX()+x+normal.x*.001,root.getY()+y+normal.y*.001,root.getZ()+z+normal.z*.001);
        Map<String,Object> row=new LinkedHashMap<>();row.put("phase",phase);row.put("root",List.of(root.getX(),root.getY(),root.getZ()));row.put("state",world.getBlockState(root).toString());row.put("model",model.toString());row.put("carrier",carrier.toString());row.put("sprite",quad.getSprite().getContents().getId().toString());row.put("face",face.asString());row.put("vertices",points);row.put("brightness",brightness);row.put("lights",lights);row.put("surface",List.of(surface.getX(),surface.getY(),surface.getZ()));row.put("surfaceSky",world.getLightLevel(LightType.SKY,surface));row.put("surfaceBlock",world.getLightLevel(LightType.BLOCK,surface));row.put("surfaceState",world.getBlockState(surface).toString());row.put("rootSky",world.getLightLevel(LightType.SKY,root));row.put("opaqueQuad",StaticArchitectureGeometry.opaqueQuad(quad));
        row.put("rawBrightness",rawBrightness);
        var sprite=quad.getSprite();var images=((StaticArchitectureSpriteAccess)(Object)sprite.getContents()).staticArchitectureMipmapLevelsImages();
        if(images!=null&&images.length>0){float u=0,v=0;for(int i=0;i<4;i++){u+=Float.intBitsToFloat(data[i*stride+4])/4;v+=Float.intBitsToFloat(data[i*stride+5])/4;}var image=images[0];int tx=(int)((u-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU())*image.getWidth()),ty=(int)((v-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV())*image.getHeight());if(tx>=0&&tx<image.getWidth()&&ty>=0&&ty<image.getHeight())row.put("textureCenterABGR",Integer.toHexString(image.getColor(tx,ty)));}
        try{Path path=Path.of(OUTPUT);Files.createDirectories(path.getParent());Files.writeString(path,new Gson().toJson(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
