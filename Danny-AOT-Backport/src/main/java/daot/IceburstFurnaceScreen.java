package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class IceburstFurnaceScreen extends HandledScreen<IceburstFurnaceMenu> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/gui/iceburst_furnace.png");

   public IceburstFurnaceScreen(IceburstFurnaceMenu menu, PlayerInventory playerInventory, Text title) {
      super(menu, playerInventory, title);
   }

   @Override
   protected void init() {
      super.init();
      this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
   }

   @Override
   protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
      int x = (this.width - this.backgroundWidth) / 2;
      int y = (this.height - this.backgroundHeight) / 2;
      context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);
      if (this.handler.isLit()) {
         int litProgress = this.handler.getLitProgress();
         context.drawTexture(TEXTURE, x + 56, y + 36 + 12 - litProgress, 176, 12 - litProgress, 14, litProgress + 1);
      }

      int smeltProgress = this.handler.getSmeltProgress();
      if (smeltProgress > 0) {
         context.drawTexture(TEXTURE, x + 79, y + 34, 176, 14, smeltProgress + 1, 16);
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      this.drawMouseoverTooltip(context, mouseX, mouseY);
   }
}
