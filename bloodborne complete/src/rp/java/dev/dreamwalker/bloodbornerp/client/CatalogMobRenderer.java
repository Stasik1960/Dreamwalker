package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The source mobs provide death poses in their animations rather than vanilla sideways rotation. */
public final class CatalogMobRenderer extends GeoEntityRenderer<RpMobEntity> {
 public CatalogMobRenderer(EntityRendererFactory.Context context,float scale){super(context,new CatalogEntityModel<>());withScale(scale);}

 @Override protected float getDeathMaxRotation(RpMobEntity entity){return 0;}
}
