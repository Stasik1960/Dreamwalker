package com.yharnam.casino.client;

import com.yharnam.casino.DenominationMenu;
import com.yharnam.casino.ChipCabinetEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class DenominationScreen extends HandledScreen<DenominationMenu> {
    private final ButtonWidget[] choices = new ButtonWidget[ChipCabinetEntity.DENOMINATIONS];

    DenominationScreen(DenominationMenu handler, PlayerInventory inventory, Text title) {
        super(handler,inventory,title);
        backgroundWidth = 202;
        backgroundHeight = 105;
    }

    @Override
    protected void init() {
        super.init();
        for (int index=0; index<choices.length; index++) {
            int choice = index;
            int offset = index<4 ? index*46 : (index-4)*46+23;
            int row = index<4 ? 26 : 50;
            choices[index] = addDrawableChild(ButtonWidget.builder(label(index),button -> {
                if (client != null && client.interactionManager != null) {
                    client.interactionManager.clickButton(handler.syncId,choice);
                }
            }).dimensions(x+11+offset,y+row,42,20).build());
        }
    }

    private Text label(int index) {
        return Text.literal(Integer.toString(handler.denomination(index)))
                .formatted(handler.accepts(index) ? Formatting.GREEN : Formatting.GRAY);
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw);
        for (int index=0; index<choices.length; index++) {
            choices[index].setMessage(label(index));
        }
        super.render(draw,mouseX,mouseY,delta);
    }

    @Override
    protected void drawBackground(DrawContext draw, float delta, int mouseX, int mouseY) {
        draw.fill(x,y,x+backgroundWidth,y+backgroundHeight,0xff937452);
        draw.fill(x+2,y+2,x+backgroundWidth-2,y+backgroundHeight-2,0xff282127);
        draw.fill(x+8,y+20,x+backgroundWidth-8,y+21,0xff584337);
    }

    @Override
    protected void drawForeground(DrawContext draw, int mouseX, int mouseY) {
        draw.drawText(textRenderer,title,10,9,0xffebd7b6,false);
        draw.drawText(textRenderer,Text.translatable("screen.yharnam_casino.denominations_hint"),
                10,82,0xffc9b8a2,false);
    }
}
