package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The source mobs provide death poses in their animations rather than vanilla sideways rotation. */
public final class CatalogMobRenderer extends GeoEntityRenderer<RpMobEntity> {
 public CatalogMobRenderer(EntityRendererFactory.Context context,float scale){super(context,new CatalogEntityModel<>());withScale(scale);}

 @Override protected float getDeathMaxRotation(RpMobEntity entity){return 0;}
 @Override protected void applyRotations(RpMobEntity entity,net.minecraft.client.util.math.MatrixStack matrices,float age,float yaw,float tick){
  super.applyRotations(entity,matrices,age,yaw,tick);
  if(entity.isVisualCorpse()&&dev.dreamwalker.bloodbornerp.mob.MobAnimationSelector.death(entity.asset()).isEmpty()){
   matrices.translate(entity.getHeight()*.5,entity.getWidth()*.5,0);matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(90));
  }
 }
}
