package dev.dreamwalker.citypick;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

final class OriginalChoiceScreen extends Screen {
    private final List<ItemStack> choices;
    private final Consumer<ItemStack> selected;
    private int page;

    OriginalChoiceScreen(List<ItemStack> choices, Consumer<ItemStack> selected) {
        super(Text.translatable("screen.dw_city_original_pick.choose"));
        this.choices = List.copyOf(choices);
        this.selected = selected;
    }

    @Override
    protected void init() {
        int rows = Math.max(1, Math.min(7, (height - 65) / 23));
        int pages = (choices.size() + rows - 1) / rows;
        page = Math.min(page, pages - 1);
        int width = Math.min(340, this.width - 24);
        int left = (this.width - width) / 2;
        int top = Math.max(28, (height - Math.min(rows, choices.size()) * 23 - 24) / 2);
        for (int i = page * rows, y = top; i < Math.min(choices.size(), (page + 1) * rows); i++, y += 23) {
            ItemStack choice = choices.get(i);
            addDrawableChild(ButtonWidget.builder(choice.getName(), button -> {
                close();
                selected.accept(choice);
            }).dimensions(left, y, width, 20).build());
        }
        if (pages > 1) {
            int y = top + rows * 23;
            var previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> changePage(-1))
                    .dimensions(this.width / 2 - 44, y, 40, 20).build());
            previous.active = page > 0;
            var next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> changePage(1))
                    .dimensions(this.width / 2 + 4, y, 40, 20).build());
            next.active = page + 1 < pages;
        }
    }

    private void changePage(int amount) {
        page += amount;
        clearAndInit();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xffffff);
    }
}
