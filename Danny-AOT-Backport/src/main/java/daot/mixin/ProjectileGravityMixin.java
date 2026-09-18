package daot.mixin;

import daot.compat.ProjectileGravityProvider;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PersistentProjectileEntity.class)
public abstract class ProjectileGravityMixin {
   @ModifyConstant(method = "tick", constant = @Constant(floatValue = 0.05F))
   private float daot$useProjectileGravity(float vanillaGravity) {
      return (Object)this instanceof ProjectileGravityProvider provider
         ? (float)provider.daotGravity()
         : vanillaGravity;
   }
}
