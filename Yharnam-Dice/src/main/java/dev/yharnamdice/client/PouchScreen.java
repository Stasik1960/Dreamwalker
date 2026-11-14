package dev.yharnamdice.client;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.item.DicePouchItem;
import dev.yharnamdice.item.PouchMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public final class PouchScreen extends HandledScreen<PouchMenu> {
    public PouchScreen(PouchMenu handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = PouchMenu.WIDTH;
        backgroundHeight = PouchMenu.HEIGHT;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float tick) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, tick);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.drawTexture(DiceMod.id("textures/gui/pouch.png"),x,y,224,210,0,0,448,420,448,420);
        for (Slot slot : handler.slots) frame(c,x+slot.x,y+slot.y);
        c.fill(x+32, y+110, x+192, y+111, 0xff804d37);
    }

    private static void frame(DrawContext c, int x, int y) {
        c.fill(x-1,y-1,x+17,y+17,0xff796044);
        c.fill(x,y,x+16,y+16,0xe0110c0e);
        c.fill(x,y,x+16,y+1,0xe00e0709);
        c.fill(x,y+15,x+16,y+16,0xe04d3029);
    }

    @Override protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawCenteredTextWithShadow(textRenderer, title, 112, 15, 0xfff0d7ae);
        int occupied = 0;
        for (int i=0; i<DicePouchItem.SIZE; i++) if (handler.getSlot(i).hasStack()) occupied++;
        c.drawCenteredTextWithShadow(textRenderer, Text.translatable("yharnam_dice.pouch.count", occupied, 20), 112, 25, 0xffb89972);
        c.drawText(textRenderer, playerInventoryTitle, 32, 114, 0xffd4b487, false);
    }
}
