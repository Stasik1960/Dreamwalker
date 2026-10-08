package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The source mobs provide death poses in their animations rather than vanilla sideways rotation. */
public final class CatalogMobRenderer extends GeoEntityRenderer<RpMobEntity> {
 public CatalogMobRenderer(EntityRendererFactory.Context context,float scale){super(context,new CatalogEntityModel<>());withScale(scale);}
 @Override public void render(RpMobEntity entity,float yaw,float delta,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light){long started=RpClientDiagnostics.begin(entity,"rp.mob_render_cpu_submission");try{super.render(entity,yaw,delta,matrices,vertices,light);}catch(RuntimeException failure){RpClientDiagnostics.error(entity,"rp_mob_render_failure","RP mob render CPU submission failed",failure);throw failure;}finally{RpClientDiagnostics.finish(entity,"rp.mob_render_cpu_submission",started);}}
 @Override protected float getDeathMaxRotation(RpMobEntity entity){return 0;}
}
