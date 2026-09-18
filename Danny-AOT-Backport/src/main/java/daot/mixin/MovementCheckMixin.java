package daot.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerPlayNetworkHandler.class)
public class MovementCheckMixin {
   @Shadow
   public ServerPlayerEntity player;

   @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 100.0F))
   private float dannysaot$raiseMovementThreshold(float original) {
      return this.player != null
            && this.player.getWorld() != null
            && this.player.getWorld().getRegistryKey().getValue().getPath().contains("paradis")
         ? 10000.0F
         : original;
   }
}
