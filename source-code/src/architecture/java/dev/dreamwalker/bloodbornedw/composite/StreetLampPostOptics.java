package dev.dreamwalker.bloodbornedw.composite;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import net.minecraft.block.BlockState;
/** Finite carrier shadows of the consumed post parts, shared by every copy. */
final class StreetLampPostOptics {
 static final String ID="bloodborne_dw:source_street_lamp_post";
 private static final List<Map<Cell,Integer>> VARIANTS=load();
 private static List<Map<Cell,Integer>> load(){try(var input=StreetLampPostOptics.class.getResourceAsStream("/bloodborne_dw/street-lamp-post-optics.json")){
  if(input==null)return List.of();var doc=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();List<Map<Cell,Integer>> rows=new ArrayList<>();
  for(var v:doc.getAsJsonArray("variants")){Map<Cell,Integer> row=new HashMap<>();for(var value:v.getAsJsonArray()){var r=value.getAsJsonObject();var p=r.getAsJsonArray("offset");int n=r.get("opacity").getAsInt();if(n<0||n>15)throw new IllegalArgumentException("Post opacity");row.put(new Cell(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()),n);}rows.add(Map.copyOf(row));}return List.copyOf(rows);
 }catch(IOException e){throw new IllegalStateException(e);}}
 static int value(int variant,int rotation,Cell offset){if(variant<0||variant>=VARIANTS.size())return 0;double a=rotation*Math.PI/4,c=Math.cos(a),s=Math.sin(a);var local=new Cell((int)Math.rint(offset.x()*c-offset.z()*s),offset.y(),(int)Math.rint(offset.x()*s+offset.z()*c));return VARIANTS.get(variant).getOrDefault(local,0);}
 static boolean post(BlockState state){return state.getBlock() instanceof CompositeRootBlock b&&b.spec.id.toString().equals(ID);}
 static int root(BlockState state){return value(CompositeSpec.poseVariant(state),state.get(CompositeRootBlock.ROTATION),Cell.ORIGIN);}
 static int published(Cell cell,List<CompositeData.Contribution> entries){int result=0;for(var e:entries)if(e.owner().registryId().equals(ID)){var p=e.rootData().properties();result=Math.max(result,value(Integer.parseInt(p.getOrDefault("variant","0"))+16*Integer.parseInt(p.getOrDefault("art_bank","0")),Integer.parseInt(p.getOrDefault("rotation","0")),cell.subtract(e.owner().root())));}return result;}
}
