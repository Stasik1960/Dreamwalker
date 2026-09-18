package daot.mixin.compat.attributes;

import daot.compat.BaseDimensionsProvider;
import daot.compat.EntityEyeHeights;
import daot.compat.attributes.DaotEntityAttributes;
import daot.compat.attributes.LivingEntityAttributeCompat;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAttributeMixin {
   @Unique
   private float daot$lastScale = Float.NaN;

   @Inject(method = "createLivingAttributes", at = @At("RETURN"))
   private static void daot$addBackportedAttributes(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
      cir.getReturnValue()
         .add(DaotEntityAttributes.STEP_HEIGHT)
         .add(DaotEntityAttributes.SCALE)
         .add(DaotEntityAttributes.GRAVITY)
         .add(DaotEntityAttributes.SAFE_FALL_DISTANCE)
         .add(DaotEntityAttributes.JUMP_STRENGTH)
         .add(DaotEntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE)
         .add(DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY);
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void daot$refreshDimensionsWhenScaleChanges(CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      float scale = LivingEntityAttributeCompat.getScale(self);
      if (Float.compare(scale, this.daot$lastScale) != 0) {
         this.daot$lastScale = scale;
         self.calculateDimensions();
      }
   }

   @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
   private void daot$scaleDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
      if (pose != EntityPose.SLEEPING) {
         LivingEntity self = (LivingEntity)(Object)this;
         float scale = LivingEntityAttributeCompat.getScale(self);
         if ((Object)this instanceof BaseDimensionsProvider provider) {
            cir.setReturnValue(provider.getBaseDimensions(pose).scaled(scale * self.getScaleFactor()));
         } else {
            cir.setReturnValue(cir.getReturnValue().scaled(scale));
         }
      }
   }

   @Inject(method = "getActiveEyeHeight", at = @At("HEAD"), cancellable = true)
   private void daot$useConfiguredStandingEyeHeight(
      EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir
   ) {
      if (pose == EntityPose.STANDING) {
         LivingEntity self = (LivingEntity)(Object)this;
         Float eyeHeight = EntityEyeHeights.get(self.getType());
         if (eyeHeight != null) {
            cir.setReturnValue(eyeHeight * LivingEntityAttributeCompat.getScale(self) * self.getScaleFactor());
         }
      }
   }

   @Inject(method = "getStepHeight", at = @At("HEAD"), cancellable = true)
   private void daot$useStepHeightAttribute(CallbackInfoReturnable<Float> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      float height = (float)self.getAttributeValue(DaotEntityAttributes.STEP_HEIGHT);
      if (self.getControllingPassenger() instanceof PlayerEntity) {
         height = Math.max(height, 1.0F);
      }
      cir.setReturnValue(height);
   }

   @ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.08))
   private double daot$useGravityAttribute(double vanillaGravity) {
      return ((LivingEntity)(Object)this).getAttributeValue(DaotEntityAttributes.GRAVITY);
   }

   @ModifyConstant(method = "getJumpVelocity", constant = @Constant(floatValue = 0.42F))
   private float daot$useJumpStrengthAttribute(float vanillaJumpStrength) {
      return (float)((LivingEntity)(Object)this).getAttributeValue(DaotEntityAttributes.JUMP_STRENGTH);
   }

   @ModifyConstant(method = "computeFallDamage", constant = @Constant(floatValue = 3.0F))
   private float daot$useSafeFallDistanceForDamage(float vanillaSafeDistance) {
      return (float)((LivingEntity)(Object)this).getAttributeValue(DaotEntityAttributes.SAFE_FALL_DISTANCE);
   }

   @ModifyConstant(method = "fall", constant = @Constant(floatValue = 3.0F, ordinal = 0))
   private float daot$useSafeFallDistanceForLandingEffects(float vanillaSafeDistance) {
      return (float)((LivingEntity)(Object)this).getAttributeValue(DaotEntityAttributes.SAFE_FALL_DISTANCE);
   }

   @ModifyVariable(method = "travel", at = @At(value = "STORE"), index = 10, ordinal = 0)
   private float daot$applyWaterMovementEfficiency(float depthStriderLevel) {
      LivingEntity self = (LivingEntity)(Object)this;
      float efficiency = (float)self.getAttributeValue(DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY);
      return Math.max(depthStriderLevel, efficiency * 3.0F);
   }
}


