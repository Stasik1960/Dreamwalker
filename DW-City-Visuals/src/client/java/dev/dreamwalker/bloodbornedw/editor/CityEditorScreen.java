package dev.dreamwalker.bloodbornedw.editor;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Compact in-world brush panel. Server-side raycasts remain authoritative. */
final class CityEditorScreen extends Screen {
    private int paletteLeft,paletteTop;
    CityEditorScreen() { super(Text.literal("Редактор фасадов")); }
    @Override protected void init() {
        int left = width / 2 - 150, top = height / 2 - 95;
        paletteLeft=left;paletteTop=top;
        String[] palette = {"База", "Роза", "Шалфей", "Песок", "Красный"};
        for (int i = 0; i < palette.length; i++) {
            int value = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((CityEditorClient.material() == i ? "> " : "") + palette[i]), b -> CityEditorClient.setMaterial(value))
                .dimensions(left + i * 60, top, 58, 20).build());
        }
        String[] modes = {"Один", "Область", "Плоскость", "Заливка", "Шум", "Штамп"};
        String[] modeIds = {"single", "area", "plane", "fill", "noise", "stamp"};
        for (int i = 0; i < modes.length; i++) {
            String value = modeIds[i];
            addDrawableChild(ButtonWidget.builder(Text.literal((CityEditorClient.mode().equals(value) ? "> " : "") + modes[i]), b -> CityEditorClient.setMode(value))
                .dimensions(left + i * 50, top + 25, 49, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Радиус −"), b -> { CityEditorClient.setRadius(CityEditorClient.radius() - 1); refresh(); }).dimensions(left, top + 52, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Радиус " + CityEditorClient.radius()), b -> {}).dimensions(left + 76, top + 52, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Радиус +"), b -> { CityEditorClient.setRadius(CityEditorClient.radius() + 1); refresh(); }).dimensions(left + 148, top + 52, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Образец"), b -> CityEditorClient.request("sample")).dimensions(left, top + 80, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Красить"), b -> CityEditorClient.request("paint")).dimensions(left + 76, top + 80, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Точка 1"), b -> CityEditorClient.request("first")).dimensions(left + 148, top + 80, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Точка 2"), b -> CityEditorClient.request("second")).dimensions(left + 224, top + 80, 76, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Отменить " + CityEditorClient.undoCount()), b -> CityEditorClient.request("undo")).dimensions(left, top + 107, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Повтор " + CityEditorClient.redoCount()), b -> CityEditorClient.request("redo")).dimensions(left + 76, top + 107, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Залить"), b -> CityEditorClient.request("fill")).dimensions(left + 148, top + 107, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Шум"), b -> CityEditorClient.request("noise")).dimensions(left + 224, top + 107, 76, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить"), b -> CityEditorClient.request("reset")).dimensions(left, top + 134, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Образец штампа"), b -> CityEditorClient.request("stamp")).dimensions(left + 76, top + 134, 132, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close()).dimensions(left + 224, top + 134, 76, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Второй −"), b -> { CityEditorClient.setSecondary(CityEditorClient.secondary()-1); refresh(); }).dimensions(left, top + 162, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(CityEditorClient.materialName(CityEditorClient.secondary())), b -> { CityEditorClient.setSecondary((CityEditorClient.secondary()+1)%5); refresh(); }).dimensions(left + 72, top + 162, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Акцент −"), b -> { CityEditorClient.setAccent(CityEditorClient.accent()-1); refresh(); }).dimensions(left + 144, top + 162, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(CityEditorClient.materialName(CityEditorClient.accent())), b -> { CityEditorClient.setAccent((CityEditorClient.accent()+1)%5); refresh(); }).dimensions(left + 216, top + 162, 84, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Основа −"), b -> { CityEditorClient.setPrimaryWeight(CityEditorClient.primaryWeight()-5); refresh(); }).dimensions(left, top + 189, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Основа +"), b -> { CityEditorClient.setPrimaryWeight(CityEditorClient.primaryWeight()+5); refresh(); }).dimensions(left + 72, top + 189, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Второй −"), b -> { CityEditorClient.setSecondaryWeight(CityEditorClient.secondaryWeight()-5); refresh(); }).dimensions(left + 144, top + 189, 68, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Второй +"), b -> { CityEditorClient.setSecondaryWeight(CityEditorClient.secondaryWeight()+5); refresh(); }).dimensions(left + 216, top + 189, 84, 20).build());
    }
    void refresh() { clearAndInit(); }
    @Override public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 120, 0xFFFFFF);
        int[] swatches={0xFF90877C,0xFFC98587,0xFF87947E,0xFFCBB88E,0xFFA44742};
        for(int i=0;i<swatches.length;i++){
            int x=paletteLeft+i*60+4,y=paletteTop-11;
            context.fill(x,y,x+50,y+7,swatches[i]);
            if(CityEditorClient.material()==i)context.drawBorder(x-1,y-1,52,9,0xFFFFFFFF);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Шум: " + CityEditorClient.primaryWeight() + "% / " + CityEditorClient.secondaryWeight() + "% / " + (100-CityEditorClient.primaryWeight()-CityEditorClient.secondaryWeight()) + "%  ·  " + CityEditorClient.materialName(CityEditorClient.material())), width/2, paletteTop+151, 0xFFE8D9B0);
        String message = CityEditorClient.message();
        if (!message.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(message), width / 2, height / 2 + 125, 0xE8D9B0);
    }
    @Override public boolean shouldPause() { return false; }
}
