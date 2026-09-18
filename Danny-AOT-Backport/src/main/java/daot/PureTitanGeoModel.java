package daot;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

@Environment(EnvType.CLIENT)
public abstract class PureTitanGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
   private static final float MAX_HEAD_YAW = 60.0F;
   private static final float MAX_HEAD_PITCH = 45.0F;

   public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
      super.setCustomAnimations(animatable, instanceId, animationState);
      EntityModelData data = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
      if (data != null) {
         Optional<GeoBone> headOpt = this.getBone("head");
         if (headOpt.isEmpty()) {
            headOpt = this.getBone("Head");
         }

         if (!headOpt.isEmpty()) {
            float yaw = MathHelper.clamp(MathHelper.wrapDegrees(data.netHeadYaw()), -60.0F, 60.0F);
            float pitch = MathHelper.clamp(MathHelper.wrapDegrees(data.headPitch()), -45.0F, 45.0F);
            GeoBone head = headOpt.get();
            head.setRotX(head.getRotX() + pitch * (float) (Math.PI / 180.0) * this.headPitchSign());
            head.setRotY(head.getRotY() + yaw * (float) (Math.PI / 180.0) * this.headYawSign());
            head.resetStateChanges();
         }
      }
   }

   protected float headPitchSign() {
      return 1.0F;
   }

   protected float headYawSign() {
      return 1.0F;
   }
}
