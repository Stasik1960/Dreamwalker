package dev.dreamwalker.bloodbornedw.visual;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** The world is closed before resource preparation starts. Cancel prevents reconnect. */
final class CityVisualPreparationScreen extends Screen {
    private final Runnable cancel;
    private Text status = Text.literal("Закрытие подключения…");
    CityVisualPreparationScreen(Runnable cancel) {
        super(Text.literal("Смена внешнего вида города")); this.cancel = cancel;
    }
    void status(String status) { this.status = Text.literal(status); }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), button -> close())
            .dimensions(width / 2 - 100, height / 2 + 35, 200, 20).build());
    }
    @Override public void close() { cancel.run(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 35, 0xffffff);
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height / 2 - 10, 0xc0c0c0);
        super.render(context, mouseX, mouseY, delta);
    }
}
