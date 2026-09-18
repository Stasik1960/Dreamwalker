package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.HandcuffsTracker;
import daot.WarhammerTitanEntity;
import java.util.OptionalInt;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class TitanInventoryBlockMixin {
   @Inject(method = "openHandledScreen", at = @At("HEAD"), cancellable = true)
   private void blockInventoryWhileShifted(NamedScreenHandlerFactory menu, CallbackInfoReturnable<OptionalInt> cir) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (self.getVehicle() instanceof ColossalTitanEntity titan && !titan.isDismounting()) {
         cir.setReturnValue(OptionalInt.empty());
      } else if (self.getVehicle() instanceof AttackTitanEntity titan && !titan.isDismounting()) {
         cir.setReturnValue(OptionalInt.empty());
      } else if (self.getVehicle() instanceof ArmoredTitanEntity titan && !titan.isDismounting()) {
         cir.setReturnValue(OptionalInt.empty());
      } else if (self.getVehicle() instanceof FemaleTitanEntity titan && !titan.isDismounting()) {
         cir.setReturnValue(OptionalInt.empty());
      } else if (self.getVehicle() instanceof WarhammerTitanEntity titan && !titan.isDismounting()) {
         cir.setReturnValue(OptionalInt.empty());
      }

      if (HandcuffsTracker.isCuffed(self.getUuid())) {
         cir.setReturnValue(OptionalInt.empty());
      }
   }
}

