package daot.mixin;

import daot.GeassManager;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class GeassMovementMixin extends LivingEntity {
   protected GeassMovementMixin(EntityType<? extends LivingEntity> entityType, World level) {
      super(entityType, level);
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void applyGeassOverride(CallbackInfo ci) {
      GeassManager.onPlayerTick((ServerPlayerEntity)(Object)this, this.jumping);
   }

   @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
   private void blockDropIfFrozen(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (GeassManager.isFrozen(self.getUuid()) && !GeassManager.isDropAllowed()) {
         cir.setReturnValue(null);
      }
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   private void blockAttackIfFrozen(Entity target, CallbackInfo ci) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (GeassManager.isFrozen(self.getUuid())) {
         ci.cancel();
      }
   }

   @Inject(method = "openHandledScreen", at = @At("TAIL"))
   private void mirrorMenuToTarget(NamedScreenHandlerFactory menuProvider, CallbackInfoReturnable<OptionalInt> cir) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (menuProvider != null) {
         if (GeassManager.isMindController(self.getUuid())) {
            UUID targetUUID = GeassManager.getMindControlTargetUUID();
            if (targetUUID != null) {
               ServerPlayerEntity target = self.server.getPlayerManager().getPlayer(targetUUID);
               if (target != null) {
                  target.openHandledScreen(menuProvider);
               }
            }
         }
      }
   }
}

