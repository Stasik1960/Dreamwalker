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
 private Identifier resolve(String path,Identifier fallback) {
  Identifier requested=path.contains(":")?new Identifier(path):BloodborneRp.id(path);
  return MinecraftClient.getInstance().getResourceManager().getResource(requested).isPresent()?requested:fallback;
 }
 @Override public Identifier getModelResource(T entity) {
  return resolve(AssetCatalog.get(entity.assetId()).model(),BloodborneRp.id("geo/fallback.geo.json"));
 }
 @Override public Identifier getTextureResource(T entity) {
  return resolve(AssetCatalog.get(entity.assetId()).texture(),new Identifier("minecraft","textures/entity/zombie/zombie.png"));
 }
 @Override public Identifier getAnimationResource(T entity) {
  return resolve(AssetCatalog.get(entity.assetId()).animation(),BloodborneRp.id("animations/fallback.animation.json"));
 }
}
