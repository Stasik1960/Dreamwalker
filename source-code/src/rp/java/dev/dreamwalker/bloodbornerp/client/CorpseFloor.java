package dev.dreamwalker.bloodbornerp.client;
import dev.dreamwalker.bloodbornerp.mob.*;
import java.util.WeakHashMap;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.util.RenderUtils;

/** Keep authored death geometry above its feet plane; cache the settled pose. */
public final class CorpseFloor {
 private static final WeakHashMap<RpMobEntity,State> CACHE=new WeakHashMap<>();
 private static final class State {int start;double time=Double.NaN,lift;boolean settled;State(int age){start=age;}}
 private CorpseFloor(){}
 public static double offset(RpMobEntity entity,BakedGeoModel model,float partial){
  String death=MobAnimationSelector.death(entity.asset());if(!entity.isVisualCorpse()||death.isEmpty()){CACHE.remove(entity);return 0;}
  State s=CACHE.get(entity);if(s==null||entity.age<s.start){s=new State(entity.age);CACHE.put(entity,s);}double time=entity.age+partial;
  if(!s.settled&&s.time!=time){double min=minimum(model);s.lift=Double.isFinite(min)?Math.max(0,-min):0;s.time=time;if(entity.age-s.start>entity.asset().clip(death).seconds()*20+10)s.settled=true;}
  return s.lift;
 }
 public static double minimum(BakedGeoModel model){var matrices=new MatrixStack();double min=Double.POSITIVE_INFINITY;var scratch=new Vector3f();for(var bone:model.topLevelBones())min=Math.min(min,minimum(matrices,bone,scratch));return min;}
 private static double minimum(MatrixStack matrices,GeoBone bone,Vector3f scratch){
  matrices.push();RenderUtils.prepMatrixForBone(matrices,bone);double min=Double.POSITIVE_INFINITY;
  if(!bone.isHidden())for(var cube:bone.getCubes()){matrices.push();RenderUtils.translateToPivotPoint(matrices,cube);RenderUtils.rotateMatrixAroundCube(matrices,cube);RenderUtils.translateAwayFromPivotPoint(matrices,cube);var matrix=matrices.peek().getPositionMatrix();for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices()){scratch.set(vertex.position()).mulPosition(matrix);min=Math.min(min,scratch.y());}matrices.pop();}
  if(!bone.isHidingChildren())for(var child:bone.getChildBones())min=Math.min(min,minimum(matrices,child,scratch));matrices.pop();return min;
 }
}
