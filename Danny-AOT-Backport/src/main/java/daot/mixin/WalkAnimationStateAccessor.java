package daot.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface WalkAnimationStateAccessor {
   @Accessor("pos")
   float getPosition();

   @Accessor("speed")
   float getSpeed();

   @Accessor("prevSpeed")
   float getSpeedOld();

   @Accessor("pos")
   void setPosition(float var1);

   @Accessor("speed")
   void setSpeed(float var1);

   @Accessor("prevSpeed")
   void setSpeedOld(float var1);
}
