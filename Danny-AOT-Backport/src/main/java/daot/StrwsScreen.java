package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class StrwsScreen extends HandledScreen<StrwsMenu> {
   public StrwsScreen(StrwsMenu menu, PlayerInventory playerInventory, Text title) {
      super(menu, playerInventory, title);
   }

   @Override
   protected void init() {
      super.init();
      this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
      int x = this.x;
      int y = this.y;
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Start"), b -> this.client.interactionManager.clickButton(this.handler.syncId, 0))
            .dimensions(x + 8, y + 16, 48, 20)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Stop"), b -> this.client.interactionManager.clickButton(this.handler.syncId, 1))
            .dimensions(x + 120, y + 16, 48, 20)
            .build()
      );
   }

   @Override
   protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
      int x = this.x;
      int y = this.y;
      context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, -804253680);
      context.fill(x + 8, y + 70, x + this.backgroundWidth - 8, y + 78, -16777216);
      context.fill(x + 79, y + 34, x + 97, y + 52, -7631989);
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      int x = this.x;
      int y = this.y;
      context.drawText(this.textRenderer, "Gas: " + this.handler.getGas() + "/500", x + 8, y + 42, 16777215, false);
      Text status = this.handler.isRunning() ? Text.literal("RUNNING") : Text.literal("STOPPED");
      context.drawText(this.textRenderer, status, x + 120, y + 42, this.handler.isRunning() ? 5635925 : 16733525, false);
      this.drawMouseoverTooltip(context, mouseX, mouseY);
   }
}
