package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.client.lamp.LampClient;
import dev.dreamwalker.bloodbornerp.client.weapon.WeaponClient;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BloodborneRpClient implements ClientModInitializer {
 @Override public void onInitializeClient() {
  RpObjectIndexClient.initialize();
  MobRegistry.TYPES.forEach((id,type)->EntityRendererRegistry.register(type,context->
      new CatalogMobRenderer(context,AssetCatalog.get(id).scale())));
  ObjectRegistry.TYPES.forEach((id,type)->EntityRendererRegistry.register(type,context->
      new CatalogObjectRenderer(context,AssetCatalog.get(id).scale())));
  WeaponClient.initialize();
  LampClient.initialize();
 }
}
