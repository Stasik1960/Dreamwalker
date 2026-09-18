package daot.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
   @Accessor("vehicle")
   Entity getVehicleField();

   @Accessor("vehicle")
   void setVehicleField(Entity var1);

   @Invoker("removePassenger")
   void invokeRemovePassenger(Entity var1);
}
