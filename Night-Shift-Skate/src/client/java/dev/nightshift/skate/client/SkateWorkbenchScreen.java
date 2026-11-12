package dev.nightshift.skate.client;

import dev.nightshift.skate.BoardSetup;
import dev.nightshift.skate.BoardStat;
import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateWorkbenchScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

final class SkateWorkbenchScreen extends HandledScreen<SkateWorkbenchScreenHandler> {
    private static final String KEY = "screen.night_shift_skate.";
    private static final Identifier PANEL = NightShiftSkate.id("textures/gui/workbench.png");
    private static final Identifier SOCKET = NightShiftSkate.id("textures/gui/workbench_slot.png");
    private static final Identifier FLIP_PLATE = NightShiftSkate.id("textures/gui/workbench_button.png");
    private static final Style TITLE_LETTERING = Style.EMPTY.withFont(NightShiftSkate.id("workbench_title"));
    private static final Style SERIF = Style.EMPTY.withFont(NightShiftSkate.id("score_serif"));
    private static final int IVORY = 0xFFF2DFBE;
    private static final int GOLD = 0xFFC5AD8C;
    private static final int MUTED = 0xFFB6A38F;
    private static final int CENTER = 157;
    private static final int STATS_X = 211;
    private static final int STATS_RIGHT = 296;
    private static final int STATS_Y = 38;
    private static final int STAT_ROW_HEIGHT = 14;
    private static final String[] SLOT_LABELS = {"board", "deck", "trucks", "wheels"};
    private static final BoardStat[] STATS = BoardStat.values();
    private boolean underside;
    private ButtonWidget flip;

    SkateWorkbenchScreen(SkateWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 314;
        backgroundHeight = 241;
    }

    @Override
    protected void init() {
        super.init();
        flip = addDrawableChild(new FlipButton(x + CENTER - 35, y + 97, button -> underside = !underside));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        flip.visible = handler.installed() != null;
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        BoardSetup installed = handler.installed();
        if (installed != null && mouseX >= x + STATS_X && mouseX <= x + STATS_RIGHT) {
            int statY = mouseY - y - STATS_Y + 2;
            int statIndex = statY / STAT_ROW_HEIGHT;
            if (statY >= 0 && statIndex < STATS.length) {
                BoardStat stat = STATS[statIndex];
                context.drawTooltip(textRenderer, Text.translatable(KEY + "comparison",
                        stat.value(installed), stat.value(handler.preview(installed))), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawArtwork(context, PANEL, x, y, backgroundWidth, backgroundHeight);
        for (Slot slot : handler.slots) {
            drawRecess(context, slot);
        }
        context.setShaderColor(1, 1, 1, 1);

        BoardSetup installed = handler.installed();
        if (installed != null) {
            drawPreview(context, handler.preview(installed));
        }
    }

    private void drawRecess(DrawContext context, Slot slot) {
        boolean output = slot.id == SkateWorkbenchScreenHandler.OUTPUT;
        boolean input = slot.id < SkateWorkbenchScreenHandler.OUTPUT;
        float tone = output && slot.hasStack() ? 1.0f : input ? 0.84f : 0.63f;
        int inset = input ? 2 : 1;
        int size = 16 + inset * 2;
        context.setShaderColor(tone, tone, tone, 1);
        drawArtwork(context, SOCKET, x + slot.x - inset, y + slot.y - inset, size, size);
    }

    private void drawPreview(DrawContext context, BoardSetup setup) {
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + CENTER, y + 64, 130);
        matrices.scale(62, -62, 62);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-32));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(underside ? -67 : 67));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(22));
        matrices.translate(0, -setup.riderHeight() / 2, 0);
        SkateboardRenderer.renderBoard(setup, matrices, context.getVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE);
        context.draw();
        matrices.pop();
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(CENTER, 10, 0);
        matrices.scale(1.55f, 1.55f, 1);
        context.drawCenteredTextWithShadow(textRenderer, title.copy().setStyle(TITLE_LETTERING), 0, 0, IVORY);
        matrices.pop();
        context.drawCenteredTextWithShadow(textRenderer, serif(playerInventoryTitle), CENTER, 146, GOLD);
        for (int index = 0; index < SLOT_LABELS.length; index++) {
            Slot slot = handler.getSlot(index);
            context.drawTextWithShadow(textRenderer, serif(Text.translatable(KEY + SLOT_LABELS[index])),
                    43, slot.y + 4, index == 0 ? IVORY : GOLD);
        }
        context.drawCenteredTextWithShadow(textRenderer, serif(Text.translatable(KEY + "result")), CENTER, 132, GOLD);

        BoardSetup installed = handler.installed();
        BoardSetup preview = installed == null ? null : handler.preview(installed);
        for (int i = 0; i < STATS.length; i++) {
            BoardStat stat = STATS[i];
            int y = STATS_Y + i * STAT_ROW_HEIGHT;
            Text value = serif(preview == null ? Text.literal("—") : stat.value(preview));
            int valueX = STATS_RIGHT - textRenderer.getWidth(value);
            Text label = serif(stat.label());
            drawStatLabel(context, label, y, valueX - STATS_X - 4);
            int color = IVORY;
            if (preview != null) {
                double change = stat.measure(preview) - stat.measure(installed);
                if (change != 0) color = stat.improves(change) ? 0xFFB7C99A : 0xFFDA8A81;
            }
            context.drawTextWithShadow(textRenderer, value, valueX, y, color);
        }
        if (installed == null) {
            context.drawCenteredTextWithShadow(textRenderer, serif(Text.translatable(KEY + "insert_board")),
                    CENTER, 65, GOLD);
        }
    }

    private void drawStatLabel(DrawContext context, Text label, int y, int availableWidth) {
        float scale = Math.min(1, availableWidth / (float) textRenderer.getWidth(label));
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(STATS_X, y, 0);
        matrices.scale(scale, 1, 1);
        context.drawTextWithShadow(textRenderer, label, 0, 0, MUTED);
        matrices.pop();
    }

    private static void drawArtwork(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        context.drawTexture(texture, x, y, 0, 0, width, height, width, height);
    }

    private static Text serif(Text text) {
        return text.copy().setStyle(SERIF);
    }

    private static final class FlipButton extends ButtonWidget {
        private FlipButton(int x, int y, PressAction onPress) {
            super(x, y, 70, 14, serif(Text.translatable(KEY + "flip")), onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX();
            int top = getY();
            float tone = isSelected() ? 1.0f : 0.83f;
            context.setShaderColor(tone, tone, tone, 1);
            drawArtwork(context, FLIP_PLATE, left, top, width, height);
            context.setShaderColor(1, 1, 1, 1);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    left + width / 2, top + 2, IVORY);
        }
    }
}
