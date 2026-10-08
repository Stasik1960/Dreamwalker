package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Preserve GeckoLib 3 GeoProjectilesRenderer's decoration coordinate convention. */
public final class CatalogObjectRenderer extends GeoEntityRenderer<RpObjectEntity> {
 public CatalogObjectRenderer(EntityRendererFactory.Context context,float scale) {
  super(context,new CatalogEntityModel<>()); withScale(scale); shadowRadius=0;
 }
 @Override public void render(RpObjectEntity entity,float yaw,float delta,MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light){
  long started=RpClientDiagnostics.begin(entity,"rp.render_cpu_submission");try{withScale(entity.asset().scale()*entity.objectScale());super.render(entity,yaw,delta,matrices,vertices,light);}catch(RuntimeException failure){RpClientDiagnostics.error(entity,"rp_render_failure","RP object render CPU submission failed",failure);throw failure;}finally{RpClientDiagnostics.finish(entity,"rp.render_cpu_submission",started);}
 }
 @Override protected void applyRotations(RpObjectEntity entity,MatrixStack matrices,float age,float yaw,float delta) {
  matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta,entity.prevYaw,entity.getYaw())-90f));
  matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(net.minecraft.util.math.MathHelper.lerp(delta,entity.prevPitch,entity.getPitch())));
 }
}
