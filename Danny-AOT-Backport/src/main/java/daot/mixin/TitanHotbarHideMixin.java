package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.ColossalTitanEntity;
import daot.CombatModeState;
import daot.DannysAot;
import daot.FemaleTitanEntity;
import daot.FounderAbilityBarState;
import daot.HandcuffsTracker;
import daot.HookPoint;
import daot.ODMTickHandler;
import daot.WarhammerTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class TitanHotbarHideMixin {
   private static boolean isRidingTitanNotInEject() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player == null) {
         return false;
      } else if (player.getVehicle() instanceof ColossalTitanEntity titan) {
         return !titan.isDismounting();
      } else if (player.getVehicle() instanceof AttackTitanEntity titan) {
         return !titan.isDismounting();
      } else if (player.getVehicle() instanceof ArmoredTitanEntity titan) {
         return !titan.isDismounting();
      } else if (player.getVehicle() instanceof FemaleTitanEntity titan) {
         return !titan.isDismounting();
      } else if (player.getVehicle() instanceof BeastTitanEntity titan) {
         return !titan.isDismounting();
      } else {
         return player.getVehicle() instanceof WarhammerTitanEntity titan ? !titan.isDismounting() : false;
      }
   }

   private static boolean isHandcuffed() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      return player == null ? false : HandcuffsTracker.isClientCuffed(player.getUuid()) || player.getCommandTags().contains("handcuffed");
   }

   @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
   private void hideHotbar(float tickDelta, DrawContext guiGraphics, CallbackInfo ci) {
      if (isRidingTitanNotInEject() || isHandcuffed() || FounderAbilityBarState.isActive()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
   private void hideExperienceBar(DrawContext guiGraphics, int x, CallbackInfo ci) {
      if (isRidingTitanNotInEject()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
   private void hideSelectedItemName(DrawContext guiGraphics, CallbackInfo ci) {
      if (isRidingTitanNotInEject() || FounderAbilityBarState.isActive()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
   private void hidePlayerHealth(DrawContext guiGraphics, CallbackInfo ci) {
      if (isRidingTitanNotInEject()) {
         ci.cancel();
      }
   }

   @Redirect(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"))
   private boolean showCrosshairWhenAimingRocks(Perspective cameraType) {
      if (CombatModeState.isEnabled() && !CombatModeState.isShiftLockEnabled()) {
         return false;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && mc.player.getVehicle() instanceof BeastTitanEntity beast) {
            int phase = beast.getRockThrowPhase();
            if ((phase == 2 || phase == 3) && beast.isThrowTurning()) {
               return true;
            }
         }

         return mc.player != null && !isRidingTitanNotInEject() && DannysAot.isODMGear(mc.player.getEquippedStack(EquipmentSlot.LEGS).getItem())
            ? true
            : cameraType.isFirstPerson();
      }
   }

   @Inject(method = "renderCrosshair", at = @At("TAIL"))
   private void renderHookIndicators(DrawContext guiGraphics, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         if (DannysAot.isODMGear(mc.player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
            HookPoint leftHook = ODMTickHandler.getLeftHook();
            HookPoint rightHook = ODMTickHandler.getRightHook();
            boolean leftActive = leftHook != null && leftHook.active && !leftHook.isRetracting;
            boolean rightActive = rightHook != null && rightHook.active && !rightHook.isRetracting;
            if (leftActive || rightActive) {
               TextRenderer font = mc.textRenderer;
               int centerX = guiGraphics.getScaledWindowWidth() / 2;
               int centerY = guiGraphics.getScaledWindowHeight() / 2;
               if (CombatModeState.isEnabled()) {
                  centerX += (int)CombatModeState.getCursorScreenX();
                  centerY += (int)CombatModeState.getCursorScreenY();
               }

               int color = -855638017;
               if (leftActive) {
                  guiGraphics.drawText(font, "<", centerX - 15, centerY - 4, color, true);
               }

               if (rightActive) {
                  guiGraphics.drawText(font, ">", centerX + 10, centerY - 4, color, true);
               }
            }
         }
      }
   }

   @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
   private void suppressTitanMountMessage(Text component, boolean animate, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null
         && (
            player.getVehicle() instanceof ColossalTitanEntity
               || player.getVehicle() instanceof AttackTitanEntity
               || player.getVehicle() instanceof ArmoredTitanEntity
               || player.getVehicle() instanceof FemaleTitanEntity
               || player.getVehicle() instanceof BeastTitanEntity
               || player.getVehicle() instanceof WarhammerTitanEntity
         )) {
         ci.cancel();
      }
   }
}
