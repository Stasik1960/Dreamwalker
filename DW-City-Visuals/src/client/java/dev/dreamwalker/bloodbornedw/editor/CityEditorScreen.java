package dev.dreamwalker.bloodbornedw.editor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Compact in-world brush panel. */
final class CityEditorScreen extends Screen {
    private int left, top;
    private final ButtonWidget[] palette = new ButtonWidget[5];
    private ButtonWidget mode, size, undo, redo;
    private static final String[] MODES = {"single", "plane", "area", "fill", "noise"};
    private static final String[] NAMES = {"База", "Роза", "Шалфей", "Песок", "Красный"};
    CityEditorScreen() { super(Text.literal("Редактор фасадов")); }
    @Override protected void init() {
        left = width / 2 - 150; top = height / 2 - 70;
        for (int i=0;i<5;i++) {
            int value=i;
            palette[i]=addDrawableChild(ButtonWidget.builder(Text.empty(), b -> { CityEditorClient.setMaterial(value); refresh(); }).dimensions(left+i*60, top, 58, 20).build());
        }
        mode=addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            int i=java.util.Arrays.asList(MODES).indexOf(CityEditorClient.mode());
            CityEditorClient.setMode(MODES[(i+1)%MODES.length]); refresh();
        }).dimensions(left,top+28,145,20).build());
        size=addDrawableChild(ButtonWidget.builder(Text.empty(), b -> { CityEditorClient.setRadius((CityEditorClient.radius()+1)%5); refresh(); }).dimensions(left+155,top+28,145,20).build());
        undo=addDrawableChild(ButtonWidget.builder(Text.empty(), b -> CityEditorClient.request("undo")).dimensions(left,top+56,145,20).build());
        redo=addDrawableChild(ButtonWidget.builder(Text.empty(), b -> CityEditorClient.request("redo")).dimensions(left+155,top+56,145,20).build());
        refresh();
    }
    void refresh() {
        if (mode==null) return;
        for(int i=0;i<5;i++) palette[i].setMessage(Text.literal((CityEditorClient.material()==i ? "> " : "")+NAMES[i]));
        mode.setMessage(Text.literal("Режим: "+CityEditorClient.modeName(CityEditorClient.mode())));
        int n=CityEditorClient.radius()*2+1;
        size.setMessage(Text.literal("Размер: "+n+"×"+n));
        size.active=CityEditorClient.mode().equals("plane");
        undo.setMessage(Text.literal("Отменить ("+CityEditorClient.undoCount()+")"));
        redo.setMessage(Text.literal("Повтор ("+CityEditorClient.redoCount()+")"));
        undo.active=CityEditorClient.undoCount()>0&&!CityEditorClient.busy();
        redo.active=CityEditorClient.redoCount()>0&&!CityEditorClient.busy();
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context); super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,top-27,0xFFFFFF);
        int[] colors={0xFF90877C,0xFFC98587,0xFF87947E,0xFFCBB88E,0xFFA44742};
        for(int i=0;i<5;i++) context.fill(left+i*60+4,top-9,left+i*60+54,top-3,colors[i]);
        context.drawCenteredTextWithShadow(textRenderer,Text.literal("ЛКМ: красить · ПКМ: взять цвет"),width/2,top+84,0xFFE8D9B0);
        context.drawCenteredTextWithShadow(textRenderer,Text.literal("Shift + ЛКМ / ПКМ: первая / вторая точка"),width/2,top+96,0xFFE8D9B0);
        context.drawCenteredTextWithShadow(textRenderer,Text.literal(CityEditorClient.selectionText()),width/2,top+108,0xFFE8D9B0);
        String message=CityEditorClient.message();
        if(!message.isEmpty()) context.drawTextWrapped(textRenderer,Text.literal(message),left,top+122,300,0xFFE8D9B0);
    }
    @Override public boolean shouldPause() { return false; }
}
