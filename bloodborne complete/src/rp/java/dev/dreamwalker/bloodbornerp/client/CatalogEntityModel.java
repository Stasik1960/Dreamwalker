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
 @Override public void setCustomAnimations(T entity,long instanceId,software.bernie.geckolib.core.animation.AnimationState<T> state) {
  super.setCustomAnimations(entity,instanceId,state);
  if(entity instanceof dev.dreamwalker.bloodbornerp.object.RpObjectEntity object){
   // Reapply on every entity render, including visible=true; baked bones are shared by type.
   if(object.supportsDogVisibility()){var bone=getBone(object.dogRootBone());bone.ifPresent(value->{value.setHidden(!object.dogsVisible());value.setChildrenHidden(!object.dogsVisible());});;}
   if(object.assetId().equals("ladder")){var bone=getBone("bottom");bone.ifPresent(value->{value.setPosY((float)object.ladderOffsetY());value.setPosZ((float)object.ladderOffsetZ());});;}
   if(object.assetId().equals("wood_gate")){
    double seconds=object.woodGatePulseActive()?Math.min(1.6,(32-object.woodGatePulseTicks()+state.getPartialTick())/20d):1.6;
    for(String name:dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion.gateBones()){var found=getBone(name);found.ifPresent(bone->{
     var rotation=dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion.gateSample(name,"rotation",seconds);var position=dev.dreamwalker.bloodbornerp.object.AuthoredObjectMotion.gateSample(name,"position",seconds);var initial=bone.getInitialSnapshot();
     bone.setRotX(initial.getRotX()-(float)Math.toRadians(rotation.x));bone.setRotY(initial.getRotY()-(float)Math.toRadians(rotation.y));bone.setRotZ(initial.getRotZ()+(float)Math.toRadians(rotation.z));
     bone.setPosX((float)position.x);bone.setPosY((float)position.y);bone.setPosZ((float)position.z);
    });}
    ;
   }
  }
 }

 private Identifier resolve(T entity,String purpose,String path,Identifier fallback) {
        Identifier requested=path.contains(":")?new Identifier(path):BloodborneRp.id(path);
        return MinecraftClient.getInstance().getResourceManager().getResource(requested).isPresent()?requested:fallback;
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
