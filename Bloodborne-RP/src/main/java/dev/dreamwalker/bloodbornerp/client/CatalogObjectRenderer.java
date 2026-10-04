package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Nonliving objects need their own yaw; the default renderer only reads LivingEntity body yaw. */
public final class CatalogObjectRenderer extends GeoEntityRenderer<RpObjectEntity> {
 public CatalogObjectRenderer(EntityRendererFactory.Context context,float scale) {
  super(context,new CatalogEntityModel<>()); withScale(scale); shadowRadius=0;
 }
 @Override protected void applyRotations(RpObjectEntity entity,MatrixStack matrices,float age,float yaw,float delta) {
  super.applyRotations(entity,matrices,age,entity.getYaw(),delta);
 }
}
