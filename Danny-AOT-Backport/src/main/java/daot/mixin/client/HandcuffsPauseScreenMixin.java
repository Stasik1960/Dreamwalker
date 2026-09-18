package daot.mixin.client;

import daot.HandcuffsTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(GameMenuScreen.class)
public abstract class HandcuffsPauseScreenMixin extends Screen {
   @Unique
   private boolean dannysaot_buttonModified = false;

   protected HandcuffsPauseScreenMixin(Text title) {
      super(title);
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void modifyDisconnectButtonOnRender(DrawContext guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (!this.dannysaot_buttonModified) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            boolean isCuffed = HandcuffsTracker.isClientCuffed(player.getUuid()) || player.getCommandTags().contains("handcuffed");
            if (isCuffed) {
               boolean hasShifter = player.getCommandTags().contains("attack")
                  || player.getCommandTags().contains("colossal")
                  || player.getCommandTags().contains("armored")
                  || player.getCommandTags().contains("beast")
                  || player.getCommandTags().contains("female")
                  || player.getCommandTags().contains("warhammer");
               if (hasShifter) {
                  if (findAndModifyButton(this)) {
                     this.dannysaot_buttonModified = true;
                  }
               }
            }
         }
      }
   }

   @Unique
   private static boolean findAndModifyButton(Screen screen) {
      for (Element child : screen.children()) {
         if (tryModifyButton(child)) {
            return true;
         }

         if (child instanceof ParentElement container) {
            for (Element subChild : container.children()) {
               if (tryModifyButton(subChild)) {
                  return true;
               }

               if (subChild instanceof ParentElement subContainer) {
                  for (Element subSubChild : subContainer.children()) {
                     if (tryModifyButton(subSubChild)) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   @Unique
   private static boolean tryModifyButton(Element listener) {
      if (listener instanceof ButtonWidget button) {
         Text msg = button.getMessage();
         String text = msg.getString().toLowerCase();
         String fullContents = msg.toString().toLowerCase();
         if (text.contains("disconnect")
            || text.contains("save and quit")
            || fullContents.contains("menu.disconnect")
            || fullContents.contains("menu.returntomenu")
            || fullContents.contains("menu.quit")) {
            MutableText newLabel = Text.literal("").append(msg).append(Text.literal(" (WILL LOSE SHIFTER)").formatted(Formatting.RED, Formatting.BOLD));
            button.setMessage(newLabel);
            return true;
         }
      }

      return false;
   }
}
