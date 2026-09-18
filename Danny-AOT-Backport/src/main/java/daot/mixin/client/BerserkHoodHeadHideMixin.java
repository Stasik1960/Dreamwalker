package daot.mixin.client;

import daot.CloakItem;
import daot.DannysAot;
import daot.HoodTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class BerserkHoodHeadHideMixin {
   @Inject(method = "setModelPose", at = @At("RETURN"))
   private void dannysaot_hideHeadUnderBerserkHood(AbstractClientPlayerEntity player, CallbackInfo ci) {
      ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
      if (helmet.getItem() instanceof CloakItem) {
         if ("BERSERK".equals(daot.compat.components.Components.get(helmet, DannysAot.CLOAK_SKIN))) {
            if (DannysAot.BERSERK_AUTHORIZED_UUIDS.contains(player.getUuid())) {
               if (HoodTracker.isHoodUpClient(player.getUuid())) {
                  PlayerEntityRenderer self = (PlayerEntityRenderer)(Object)this;
                  PlayerEntityModel<AbstractClientPlayerEntity> m = self.getModel();
                  m.head.visible = false;
                  m.hat.visible = false;
               }
            }
         }
      }
   }
}

