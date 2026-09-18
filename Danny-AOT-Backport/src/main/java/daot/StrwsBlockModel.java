package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class StrwsBlockModel extends GeoModel<StrwsBlockEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/strws.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/block/strws.png");

   public Identifier getModelResource(StrwsBlockEntity blockEntity) {
      return MODEL;
   }

   public Identifier getTextureResource(StrwsBlockEntity blockEntity) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(StrwsBlockEntity blockEntity) {
      return null;
   }

   public void setCustomAnimations(StrwsBlockEntity be, long instanceId, AnimationState<StrwsBlockEntity> animationState) {
      super.setCustomAnimations(be, instanceId, animationState);
      if (!be.clientRenderInit) {
         be.clientRenderYaw = be.getAimYaw();
         be.clientRenderPitch = be.getAimPitch();
         be.clientRenderInit = true;
      }

      be.clientRenderYaw = MathHelper.lerpAngleDegrees(0.4F, be.clientRenderYaw, be.getAimYaw());
      be.clientRenderPitch = MathHelper.lerp(0.4F, be.clientRenderPitch, be.getAimPitch());
      GeoBone main = ((software.bernie.geckolib.cache.object.GeoBone) this.getAnimationProcessor().getBone("main"));
      GeoBone aimable = ((software.bernie.geckolib.cache.object.GeoBone) this.getAnimationProcessor().getBone("aimable"));
      if (main != null) {
         main.setRotY((float)Math.toRadians(be.clientRenderYaw));
      }

      if (aimable != null) {
         aimable.setRotX((float)Math.toRadians(be.clientRenderPitch));
      }
   }
}
