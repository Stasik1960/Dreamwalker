package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetBacked;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

public final class CatalogEntityModel<T extends Entity & GeoAnimatable & AssetBacked> extends GeoModel<T> {
 private final java.util.Map<Entity,java.util.Map<String,String>> choices=new java.util.WeakHashMap<>();private boolean recording;
 @Override public void setCustomAnimations(T entity,long instanceId,software.bernie.geckolib.core.animation.AnimationState<T> state) {
  long started=RpClientDiagnostics.begin(entity,"rp.animation_and_bone_pose_cpu");try {
  super.setCustomAnimations(entity,instanceId,state);
  if(RpClientDiagnostics.enabled()){var actual=getBakedModel(getModelResource(entity));observe(entity,"baked_geometry",actual.getClass().getName()+"|topLevelBones="+actual.topLevelBones().size());}
  if(entity instanceof dev.dreamwalker.bloodbornerp.object.RpObjectEntity object){
   // Explicitly retain GeckoLib child visibility for the authored main/main_2 roots.
   // The renderer also reapplies this per draw, independently of cached animation evaluation.
   if(object.supportsDogVisibility()){var bone=getBone(object.dogRootBone());if(bone.isEmpty())RpClientDiagnostics.error(entity,"missing_authored_bone","Dog visibility bone is unavailable: "+object.dogRootBone(),null);bone.ifPresent(value->{value.setHidden(!object.dogsVisible());value.setChildrenHidden(!object.dogsVisible());});observe(entity,"dogs_pose",object.dogsVisible()+"|"+bone.map(value->value.isHidden()).orElse(false));}
   if(object.assetId().equals("ladder")){var bone=getBone("bottom");if(bone.isEmpty())RpClientDiagnostics.error(entity,"missing_authored_bone","Ladder bottom bone is unavailable",null);bone.ifPresent(value->{value.setPosY((float)object.ladderOffsetY());value.setPosZ((float)object.ladderOffsetZ());});observe(entity,"ladder_motion_state",object.isOpen()+"|"+(object.ladderOffsetY()==0&&object.ladderOffsetZ()==0?"deployed":"moving_or_retracted"));}
   if(object.assetId().equals("wood_gate")){
    double seconds=object.woodGatePulseActive()?Math.min(1.6,(32-object.woodGatePulseTicks()+state.getPartialTick())/20d):1.6;
    for(String name:dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion.gateBones()){var found=getBone(name);if(found.isEmpty())RpClientDiagnostics.error(entity,"missing_authored_bone","Wood gate authored bone is unavailable: "+name,null);found.ifPresent(bone->{
     var rotation=dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion.gateSample(name,"rotation",seconds);var position=dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion.gateSample(name,"position",seconds);var initial=bone.getInitialSnapshot();
     bone.setRotX(initial.getRotX()-(float)Math.toRadians(rotation.x));bone.setRotY(initial.getRotY()-(float)Math.toRadians(rotation.y));bone.setRotZ(initial.getRotZ()+(float)Math.toRadians(rotation.z));
     bone.setPosX((float)position.x);bone.setPosY((float)position.y);bone.setPosZ((float)position.z);
    });}
    observe(entity,"gate_animation_state",object.woodGatePulseActive()?"one_shot_active":"neutral");
   }
  }
  }catch(RuntimeException failure){RpClientDiagnostics.error(entity,"rp_animation_failure","RP custom animation or authored bone evaluation failed",failure);throw failure;}finally{RpClientDiagnostics.finish(entity,"rp.animation_and_bone_pose_cpu",started);}
 }
 private void observe(T entity,String purpose,String value){
  try{
  if(!RpClientDiagnostics.enabled()){if(recording){choices.clear();recording=false;}return;}
  if(!recording){choices.clear();recording=true;}if(!choices.containsKey(entity)&&choices.size()>=1024)choices.remove(choices.keySet().iterator().next());
  com.google.gson.JsonObject accepted=dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.acceptedPlacement(entity.getUuid());
  String operation=accepted!=null&&accepted.has("operation")?accepted.get("operation").getAsString():"NOT_OBSERVED";
  String fingerprint=value+"|acceptedOperation="+operation;var previous=choices.computeIfAbsent(entity,key->new java.util.HashMap<>()).put(purpose,fingerprint);
  if(!fingerprint.equals(previous)){
   java.util.Map<String,Object> fields=new java.util.LinkedHashMap<>();fields.put("choice",value);fields.put("serverType",net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString());fields.put("heldItem","ITEM_NOT_OBSERVED_FOR_LOADED_ENTITY");fields.put("acceptedOperationNumber",operation);fields.put("yaw",entity.getYaw());fields.put("pitch",entity.getPitch());fields.put("originX",entity.getX());fields.put("originY",entity.getY());fields.put("originZ",entity.getZ());
   if(accepted!=null){if(accepted.has("sessionId"))fields.put("acceptedSession",accepted.get("sessionId").getAsString());if(accepted.has("before")){var before=accepted.getAsJsonObject("before");for(String key:java.util.List.of("heldItem","heldTypeId","hand"))if(before.has(key))fields.put(key,before.get(key).getAsString());}if(accepted.has("after")){var after=accepted.getAsJsonObject("after");for(String key:java.util.List.of("registry","typeId","serverTypeId"))if(after.has(key))fields.put("acknowledged_"+key,after.get(key).getAsString());}}
   RpClientDiagnostics.record(entity,"rp_client_"+purpose,previous==null?java.util.Map.of():java.util.Map.of("choice",previous),fields,"actual_client_resource_or_tracker_choice; item only from exact server acknowledgement; no manual visual acceptance");
  }
  }catch(RuntimeException failure){RpClientDiagnostics.error(entity,"rp_telemetry_failure","RP client resource evidence could not be recorded; rendering continues",failure);}
 }
 private Identifier resolve(T entity,String purpose,String path,Identifier fallback) {
  try{
   Identifier requested=path.contains(":")?new Identifier(path):BloodborneRp.id(path);
   boolean present=MinecraftClient.getInstance().getResourceManager().getResource(requested).isPresent();Identifier actual=present?requested:fallback;
   if(!present)RpClientDiagnostics.error(entity,"missing_"+purpose,"Requested RP resource "+requested+" is missing; actual fallback "+fallback,null);
   observe(entity,purpose,requested+" -> "+actual);return actual;
  }catch(RuntimeException failure){RpClientDiagnostics.error(entity,"invalid_"+purpose+"_resource","RP resource ID or resource lookup failed: "+path,failure);throw failure;}
 }
 @Override public Identifier getModelResource(T entity) {
  return resolve(entity,"model",AssetCatalog.get(entity.assetId()).model(),BloodborneRp.id("geo/fallback.geo.json"));
 }
 @Override public Identifier getTextureResource(T entity) {
  return resolve(entity,"texture",AssetCatalog.get(entity.assetId()).texture(),new Identifier("minecraft","textures/entity/zombie/zombie.png"));
 }
 @Override public Identifier getAnimationResource(T entity) {
  return resolve(entity,"animation",AssetCatalog.get(entity.assetId()).animation(),BloodborneRp.id("animations/fallback.animation.json"));
 }
}
