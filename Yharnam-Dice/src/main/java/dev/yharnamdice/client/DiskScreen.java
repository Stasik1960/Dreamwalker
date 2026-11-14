package dev.yharnamdice.client;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.Protocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

public final class DiskScreen extends Screen {
    private final long token;
    private String source;
    private EditBoxWidget area;
    private ButtonWidget save;
    private Text feedback;
    private int left,top,w,h;

    DiskScreen(long token, String source) {
        super(Text.translatable("yharnam_dice.disk.title"));
        this.token = token;
        this.source = source;
    }

    @Override protected void init() {
        if (area != null) source = area.getText();
        w = Math.min(420,width-20);
        h = Math.min(253,height-18);
        left = (width-w)/2;
        top = (height-h)/2;
        area = addDrawableChild(new EditBoxWidget(textRenderer,left+15,top+33,w-30,h-91,
                Text.translatable("yharnam_dice.disk.paste"), title));
        area.setMaxLength(Protocol.MAX_JSON);
        area.setText(source);
        setInitialFocus(area);
        save = addDrawableChild(ButtonWidget.builder(Text.translatable("yharnam_dice.disk.save"), button -> {
            save.active = false;
            feedback = Text.translatable("yharnam_dice.disk.checking");
            DiceClient.write(token,area.getText());
        }).dimensions(left+w-210,top+h-31,96,20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(left+w-107,top+h-31,92,20).build());
    }

    void result(long token, boolean ok, String error) {
        if (this.token != token) return;
        if (ok) { close(); return; }
        save.active = true;
        feedback = Text.translatable("yharnam_dice.disk.error",error);
    }

    @Override public void render(DrawContext c, int x, int y, float tick) {
        renderBackground(c);
        c.drawTexture(DiceMod.id("textures/gui/pouch.png"),left,top,w,h,0,0,448,420,448,420);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,top+12,0xffedd3a4);
        c.drawCenteredTextWithShadow(textRenderer,Text.translatable("yharnam_dice.disk.edit_help"),width/2,top+23,0xffbd9d77);
        if (feedback != null) c.drawTextWrapped(textRenderer,feedback,left+15,top+h-52,w-25,0xffedaa87);
        super.render(c,x,y,tick);
    }

    @Override public boolean shouldPause() { return false; }
}
