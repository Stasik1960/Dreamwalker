package ru.modelprops.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.Objects;

/** Small paginated picker for canonical animation clip names on the currently held model. */
public final class AnimationSelectionScreen extends Screen {
    private static final int MAX_ROWS = 6;

    private final Screen parent;
    private final Hand hand;
    private final List<String> clipNames;
    private int page;

    public AnimationSelectionScreen(Screen parent, Hand hand, List<String> clipNames) {
        super(Text.translatable("screen.modelprops.animation_selection.title"));
        this.parent = parent;
        this.hand = Objects.requireNonNull(hand, "hand");
        this.clipNames = List.copyOf(clipNames);
        if (this.clipNames.isEmpty()) {
            throw new IllegalArgumentException("Animation picker needs at least one clip");
        }
    }

    @Override
    protected void init() {
        int perPage = itemsPerPage();
        int pages = pageCount(perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        int width = Math.min(260, this.width - 40);
        int x = (this.width - width) / 2;
        int start = page * perPage;
        int end = Math.min(clipNames.size(), start + perPage);
        int y = 42;
        for (int index = start; index < end; index++) {
            String clipName = clipNames.get(index);
            addDrawableChild(ButtonWidget.builder(Text.literal(clipName), button -> {
                        if (ClientAnimationController.triggerClip(hand, clipName)) {
                            close();
                        }
                    })
                    .dimensions(x, y, width, 20)
                    .build());
            y += 24;
        }

        int navigationY = this.height - 52;
        int navigationWidth = Math.max(70, (width - 8) / 3);
        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.animation_selection.previous"), button -> {
                            page--;
                            clearAndInit();
                        })
                .dimensions(x, navigationY, navigationWidth, 20)
                .build());
        previous.active = page > 0;

        ButtonWidget next = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.animation_selection.next"), button -> {
                            page++;
                            clearAndInit();
                        })
                .dimensions(x + width - navigationWidth, navigationY, navigationWidth, 20)
                .build());
        next.active = page + 1 < pages;

        int cancelWidth = Math.max(70, width - navigationWidth * 2 - 8);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(x + navigationWidth + 4, navigationY, cancelWidth, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF);
        int pages = pageCount(itemsPerPage());
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.modelprops.animation_selection.page", page + 1, pages),
                width / 2, 29, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int itemsPerPage() {
        return Math.max(1, Math.min(MAX_ROWS, (height - 110) / 24));
    }

    private int pageCount(int perPage) {
        return Math.max(1, (clipNames.size() + perPage - 1) / perPage);
    }
}
