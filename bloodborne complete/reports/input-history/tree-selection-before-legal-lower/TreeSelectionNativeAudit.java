
package dev.dreamwalker.bloodbornedw.composite;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.nio.file.*;
import java.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
public final class TreeSelectionNativeAudit{
 static double[] vec(JsonArray a){return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
 static Map<Cell,List<Box>> distribute(JsonArray boxes,int yaw,boolean legacy)throws Exception{
  Class<?> type=legacy?ReviewLegacyCompositeSpec.class:CompositeSpec.class;
  Class<?> v=Class.forName(type.getName()+"$Vec"),b=Class.forName(type.getName()+"$Bounds");
  var vc=v.getConstructor(double.class,double.class,double.class);var bc=b.getConstructor(v,v);
  var method=type.getDeclaredMethod("distribute",b,int.class,double.class,Map.class);method.setAccessible(true);
  Map<Cell,List<Box>> result=new TreeMap<>();for(JsonElement element:boxes){JsonObject box=element.getAsJsonObject();double[] f=vec(box.getAsJsonArray("from")),t=vec(box.getAsJsonArray("to"));method.invoke(null,bc.newInstance(vc.newInstance(f[0],f[1],f[2]),vc.newInstance(t[0],t[1],t[2])),yaw,0d,result);}return result;
 }
 static Map<String,Object> counts(Map<Cell,List<Box>> selection,Map<Cell,List<Box>> collision){
  TreeSet<Cell> cells=new TreeSet<>(selection.keySet());cells.addAll(collision.keySet());cells.add(Cell.ORIGIN);
  int nativeBoxes=0,max=0,nonempty=0,inputs=0;for(var entry:selection.entrySet()){int count=CompositeShapes.of(entry.getValue()).getBoundingBoxes().size();nativeBoxes+=count;max=Math.max(max,count);if(count>0)nonempty++;inputs+=entry.getValue().size();}
  return Map.of("footprintCellsIncludingRoot",cells.size(),"helperCandidateCells",cells.size()-1,"selectionNonemptyCells",nonempty,"nativeSelectionBoxesTotal",nativeBoxes,"nativeSelectionBoxesMaxPerCell",max,"distributedSelectionInputs",inputs);
 }
 static Vec3d global(double[] p,int yaw){double a=Math.toRadians(yaw*45),c=Math.cos(a),s=Math.sin(a),x=p[0]/16-.5,z=p[2]/16-.5;return new Vec3d(.5+c*x-s*z,p[1]/16,.5+s*x+c*z);}
 public static void main(String[] args)throws Exception{
  JsonObject input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();List<Map<String,Object>> rows=new ArrayList<>();int hits=0,miss=0;
  for(int yaw=0;yaw<8;yaw++){
   Map<Cell,List<Box>> collision=distribute(input.getAsJsonArray("collision"),yaw,false);
   Map<Cell,List<Box>> after=distribute(input.getAsJsonArray("after"),yaw,false);
   Map<String,Object> row=new LinkedHashMap<>();row.put("yaw",yaw);row.put("v7Original",counts(distribute(input.getAsJsonArray("v7"),yaw,true),collision));row.put("beforeTwoPlanes",counts(distribute(input.getAsJsonArray("before"),yaw,false),collision));row.put("afterFourPlanes",counts(after,collision));
   int checked=0,failed=0;List<Map<String,Object>> failures=new ArrayList<>();
   for(JsonElement element:input.getAsJsonArray("targets")){
    JsonObject target=element.getAsJsonObject();Vec3d point=global(vec(target.getAsJsonArray("point")),yaw);
    double[] n=vec(target.getAsJsonArray("normal"));double a=Math.toRadians(yaw*45);Vec3d normal=new Vec3d(Math.cos(a)*n[0]-Math.sin(a)*n[2],n[1],Math.sin(a)*n[0]+Math.cos(a)*n[2]);
    for(int sign:List.of(-1,1)){
     Vec3d start=point.add(normal.multiply(.75*sign)),end=point.subtract(normal.multiply(.75*sign));boolean hit=false;
     for(var entry:after.entrySet()){var cell=entry.getKey();if(CompositeShapes.of(entry.getValue()).raycast(start,end,new BlockPos(cell.x(),cell.y(),cell.z()))!=null){hit=true;break;}}
     checked++;if(hit)hits++;else{miss++;failed++;failures.add(Map.of("variant",target.get("variant").getAsInt(),"part",target.get("part").getAsInt(),"element",target.get("element").getAsInt(),"point",List.of(point.x,point.y,point.z),"side",sign));}
    }
   }
   row.put("actualNativeRays",checked);row.put("nativeRayMisses",failed);row.put("failures",failures);rows.add(row);
  }
  System.out.println(new Gson().toJson(Map.of("status",miss==0?"PASS_NATIVE_SOURCE_TARGET_RAYCASTS":"FAIL_NATIVE_SOURCE_TARGET_RAYCASTS","actualNativeRayHits",hits,"actualNativeRayMisses",miss,"rows",rows)));
  if(miss!=0)throw new AssertionError("native source-art ray targets missed");
 }
}
