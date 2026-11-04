package dev.dreamwalker.bloodbornedw.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** In-world brush panel. Editing a field only changes the draft until it is applied. */
final class CityEditorScreen extends Screen {
    private static final int PANEL = 306;
    private static final String[] LEGACY = {"База", "Роза", "Шалфей", "Песок", "Красный"};
    private static final String[] SLOTS = {"Основной", "Второй", "Акцент"};
    private static final int[] LEGACY_COLORS = {0x90877c,0xc98587,0x87947e,0xcbb88e,0xa44742};
    private static final int[] PRESETS = {0x000000,0xffffff,0xa44338,0xc98b46,0xb8a176,0x70866e,0x58788e,0x785e83};
    private static final Deque<Integer> RECENT = new ArrayDeque<>();
    private final ButtonWidget[] palette = new ButtonWidget[5], recent = new ButtonWidget[6];
    private final TextFieldWidget[] channels = new TextFieldWidget[3];
    private ButtonWidget mode, size, undo, redo, reset, slot, weightsApply;
    private TextFieldWidget hex, primaryWeight, secondaryWeight;
    private int left, top, activeSlot, preview;
    private String inputError = "";

    CityEditorScreen() { super(Text.literal("Редактор фасадов")); }

    @Override protected void init() {
        left = (width - PANEL) / 2;
        top = Math.max(13,(height - 228) / 2);
        for (int i = 0; i < palette.length; i++) {
            int value = i;
            palette[i] = button("",i*62,18,58,() -> choose(value));
        }
        for (int i = 0; i < channels.length; i++) channels[i] = field("RGB".substring(i,i+1),i*51+11,45,36,12);
        hex = field("HEX",159,45,65,16);
        button("RGB ✓",230,44,53,this::applyChannels);
        button("HEX ✓",230,67,53,this::applyHex);
        for (int i = 0; i < PRESETS.length; i++) {
            int rgb = PRESETS[i];
            button("",i*20,68,18,() -> choose(EditorCells.rgb(rgb)));
        }
        for (int i = 0; i < recent.length; i++) {
            int index = i;
            recent[i] = button("",i*20,92,18,() -> {
                Integer rgb = recentAt(index);
                if (rgb != null) choose(EditorCells.rgb(rgb));
            });
        }
        slot = button("",127,92,93,() -> {
            activeSlot = (activeSlot+1)%3;
            setDraft(current());
            refresh();
        });
        mode = button("",0,116,146,() -> {
            int i = EditorProtocol.MODES.indexOf(CityEditorClient.mode());
            CityEditorClient.setMode(EditorProtocol.MODES.get((i+1)%EditorProtocol.MODES.size()));
            if (!CityEditorClient.mode().equals("noise")) activeSlot=0;
            setDraft(current());
            refresh();
        });
        size = button("",153,116,153,() -> {
            CityEditorClient.setRadius((CityEditorClient.radius()+1)%5);
            refresh();
        });
        undo = button("",0,140,70,() -> CityEditorClient.request("undo"));
        redo = button("",74,140,70,() -> CityEditorClient.request("redo"));
        reset = button("",149,140,157,() -> { CityEditorClient.toggleReset(); refresh(); });
        primaryWeight = field("Основной %",88,166,33,12);
        secondaryWeight = field("Второй %",172,166,33,12);
        primaryWeight.setText(Integer.toString(CityEditorClient.primaryWeight()));
        secondaryWeight.setText(Integer.toString(CityEditorClient.secondaryWeight()));
        weightsApply = button("Доли ✓",222,165,84,this::applyWeights);
        setDraft(current());
        refresh();
    }

    private ButtonWidget button(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label),b -> action.run())
                .dimensions(left+x,top+y,w,20).build());
    }

    private TextFieldWidget field(String label, int x, int y, int w, int maxLength) {
        var field = new TextFieldWidget(textRenderer,left+x,top+y,w,18,Text.literal(label));
        field.setMaxLength(maxLength);
        return addDrawableChild(field);
    }

    private int current() {
        return switch (activeSlot) {
            case 1 -> CityEditorClient.secondary();
            case 2 -> CityEditorClient.accent();
            default -> CityEditorClient.material();
        };
    }

    private void choose(int value) {
        switch (activeSlot) {
            case 1 -> CityEditorClient.setSecondary(value);
            case 2 -> CityEditorClient.setAccent(value);
            default -> CityEditorClient.setMaterial(value);
        }
        if (EditorCells.isRgb(value)) remember(value & 0xffffff);
        setDraft(value);
        refresh();
    }

    static void remember(int rgb) {
        RECENT.remove(rgb);
        RECENT.addFirst(rgb);
        while (RECENT.size() > 6) RECENT.removeLast();
    }

    private static Integer recentAt(int index) {
        for (int color : RECENT) if (index-- == 0) return color;
        return null;
    }

    private void setDraft(int value) {
        preview = EditorCells.isRgb(value) ? value & 0xffffff : LEGACY_COLORS[value];
        for (int i = 0; i < channels.length; i++)
            channels[i].setText(Integer.toString((preview >> (16-i*8)) & 255));
        hex.setText(String.format(Locale.ROOT,"#%06X",preview));
        inputError = "";
    }

    private static int number(TextFieldWidget field, int max) {
        String text = field.getText();
        if (!text.matches("[0-9]{1,3}")) throw new IllegalArgumentException();
        int value = Integer.parseInt(text);
        if (value > max) throw new IllegalArgumentException();
        return value;
    }

    private void applyChannels() {
        try {
            choose(EditorCells.rgb(number(channels[0],255)<<16 | number(channels[1],255)<<8 | number(channels[2],255)));
        } catch (IllegalArgumentException invalid) { inputError = "Каналы R, G, B принимают целые числа 0–255"; }
    }

    private void applyHex() {
        String value = hex.getText();
        if (!value.matches("#?[0-9a-fA-F]{6}")) {
            inputError = "Введите HEX в формате #RRGGBB";
            return;
        }
        choose(EditorCells.rgb(Integer.parseInt(value.startsWith("#") ? value.substring(1) : value,16)));
    }

    private void applyWeights() {
        try {
            int primary = number(primaryWeight,100), secondary = number(secondaryWeight,100);
            if (primary + secondary > 100) throw new IllegalArgumentException();
            CityEditorClient.setWeights(primary,secondary);
            inputError = "";
        } catch (IllegalArgumentException invalid) { inputError = "Сумма первых двух долей должна быть от 0 до 100%"; }
    }

    void refresh() {
        if (mode == null) return;
        for (int i = 0; i < palette.length; i++)
            palette[i].setMessage(Text.literal((current() == i && !CityEditorClient.resetBrush() ? "› " : "")+LEGACY[i]));
        mode.setMessage(Text.literal("Режим " + CityEditorClient.modeName(CityEditorClient.mode())));
        int n = CityEditorClient.radius()*2+1;
        size.setMessage(Text.literal("Размер " + n + "×" + n));
        size.active = CityEditorClient.mode().equals("plane");
        undo.setMessage(Text.literal("Отмена " + CityEditorClient.undoCount()));
        redo.setMessage(Text.literal("Повтор " + CityEditorClient.redoCount()));
        undo.active = CityEditorClient.undoCount() > 0 && !CityEditorClient.busy();
        redo.active = CityEditorClient.redoCount() > 0 && !CityEditorClient.busy();
        reset.setMessage(Text.literal(CityEditorClient.resetBrush() ? "✓ Исходный цвет" : "Вернуть исходный цвет"));
        boolean noise = CityEditorClient.mode().equals("noise");
        slot.visible = primaryWeight.visible = secondaryWeight.visible = weightsApply.visible = noise;
        slot.setMessage(Text.literal(SLOTS[activeSlot]));
        for (int i = 0; i < recent.length; i++) recent[i].active = i < RECENT.size();
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx,mouseX,mouseY,delta);
        ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,top-10,0xffffff);
        label(ctx,"Для покраски требуется ALT-вид",0,3);
        for (int i = 0; i < channels.length; i++) label(ctx,"RGB".substring(i,i+1),i*51,50);
        ctx.fill(left+287,top+45,left+306,top+86,0xff000000 | preview);
        for (int i = 0; i < PRESETS.length; i++) swatch(ctx,i*20,68,PRESETS[i]);
        for (int i = 0; i < recent.length; i++) {
            Integer color = recentAt(i);
            if (color != null) swatch(ctx,i*20,92,color);
        }
        if (weightsApply.visible) {
            label(ctx,"Доли шума %",0,170);
            label(ctx,"1",78,170);
            label(ctx,"2",162,170);
        } else label(ctx,"ЛКМ — кисть · ПКМ — пипетка",0,170);
        label(ctx,"Shift + ЛКМ / ПКМ — границы выделения",0,190);
        label(ctx,CityEditorClient.selectionText(),0,202);
        ctx.drawTextWrapped(textRenderer,Text.literal(inputError.isEmpty() ? CityEditorClient.message() : inputError),
                left,top+214,PANEL,inputError.isEmpty() ? 0xffe8d9b0 : 0xffff9d96);
    }

    private void label(DrawContext ctx, String text, int x, int y) {
        ctx.drawTextWithShadow(textRenderer,Text.literal(text),left+x,top+y,0xffe8d9b0);
    }

    private void swatch(DrawContext ctx, int x, int y, int rgb) {
        ctx.fill(left+x+3,top+y+3,left+x+15,top+y+17,0xff000000 | rgb);
    }

    @Override public boolean shouldPause() { return false; }
}
