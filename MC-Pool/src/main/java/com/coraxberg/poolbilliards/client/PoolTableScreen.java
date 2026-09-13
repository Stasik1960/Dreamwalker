package com.coraxberg.poolbilliards.client;

import com.coraxberg.poolbilliards.game.PoolBall;
import com.coraxberg.poolbilliards.game.PoolGameState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class PoolTableScreen extends Screen {
    private final BlockPos tablePos;
    private PoolGameState state = new PoolGameState();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tableX;
    private int tableY;
    private int tableW;
    private int tableH;
    private ButtonWidget resetButton;
    private boolean dragging = false;
    private double dragStartX;
    private double dragStartY;
    private double currentMouseX;
    private double currentMouseY;

    public PoolTableScreen(BlockPos tablePos) {
        super(Text.literal("Бильярд"));
        this.tablePos = tablePos;
    }

    public boolean isForTable(BlockPos pos) {
        return tablePos.equals(pos);
    }

    public void setState(PoolGameState state) {
        this.state = state;
    }

    @Override
    protected void init() {
        rebuildLayout();
        resetButton = ButtonWidget.builder(Text.literal("Начать заново"), button -> PoolBilliardsClient.sendReset(tablePos))
                .dimensions(panelX + panelW - 150, panelY + panelH - 28, 132, 20)
                .build();
        addDrawableChild(resetButton);
    }

    private void rebuildLayout() {
        panelW = Math.min(width - 30, 1040);
        panelH = Math.min(height - 30, 680);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int sideW = 210;
        tableX = panelX + 24;
        tableY = panelY + 54;
        tableW = panelW - sideW - 56;
        tableH = (int) Math.round(tableW * (PoolGameState.TABLE_H / PoolGameState.TABLE_W));
        int maxH = panelH - 110;
        if (tableH > maxH) {
            tableH = maxH;
            tableW = (int) Math.round(tableH * (PoolGameState.TABLE_W / PoolGameState.TABLE_H));
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        rebuildLayout();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        currentMouseX = mouseX;
        currentMouseY = mouseY;
        renderBackground(context);
        drawPanel(context);
        drawTable(context, mouseX, mouseY);
        drawSideBar(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPanel(DrawContext context) {
        fill(context, panelX, panelY, panelX + panelW, panelY + panelH, 0xEE15191D);
        fill(context, panelX, panelY, panelX + panelW, panelY + 36, 0xFF202A30);
        fill(context, panelX, panelY + 36, panelX + panelW, panelY + 37, 0xFF3E575D);
        context.drawTextWithShadow(textRenderer, "Пул: восьмёрка", panelX + 16, panelY + 12, 0xDFFAFF);
        context.drawTextWithShadow(textRenderer, state.status, panelX + 150, panelY + 12, 0xA9F0D0);
    }

    private void drawTable(DrawContext context, int mouseX, int mouseY) {
        fill(context, tableX - 20, tableY - 20, tableX + tableW + 20, tableY + tableH + 20, 0xFF4A2A15);
        fill(context, tableX - 10, tableY - 10, tableX + tableW + 10, tableY + tableH + 10, 0xFF6B3A1D);
        fill(context, tableX, tableY, tableX + tableW, tableY + tableH, 0xFF0C6E49);
        fill(context, tableX + 6, tableY + 6, tableX + tableW - 6, tableY + tableH - 6, 0xFF138357);

        drawPocket(context, tableX, tableY);
        drawPocket(context, tableX + tableW / 2, tableY);
        drawPocket(context, tableX + tableW, tableY);
        drawPocket(context, tableX, tableY + tableH);
        drawPocket(context, tableX + tableW / 2, tableY + tableH);
        drawPocket(context, tableX + tableW, tableY + tableH);

        for (PoolBall b : state.balls) drawBall(context, b);

        PoolBall cue = state.getBall(0);
        if (cue != null && !cue.pocketed && canLocalShoot()) {
            int cx = sx(cue.x);
            int cy = sy(cue.y);
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double len = Math.max(1, Math.sqrt(dx * dx + dy * dy));
            int lineColor = dragging ? 0xFFFFE9A8 : 0x99FFFFFF;
            drawLine(context, cx, cy, (int)(cx - dx / len * 90), (int)(cy - dy / len * 90), lineColor);
            if (dragging) {
                int power = (int)(Math.min(1.0, Math.hypot(mouseX - dragStartX, mouseY - dragStartY) / 180.0) * 100);
                context.drawTextWithShadow(textRenderer, "Сила: " + power + "%", tableX, tableY + tableH + 28, 0xFFFFFF);
            }
        }
    }

    private void drawPocket(DrawContext context, int x, int y) {
        fillCircle(context, x, y, 18, 0xFF050505);
        fillCircle(context, x, y, 12, 0xFF000000);
    }

    private void drawBall(DrawContext context, PoolBall b) {
        if (b.pocketed) return;
        int x = sx(b.x);
        int y = sy(b.y);
        int r = Math.max(6, (int)Math.round(PoolGameState.BALL_R * tableW / PoolGameState.TABLE_W));
        int color = ballColor(b.id);

        fillCircle(context, x + 1, y + 1, r, 0xAA000000);
        fillCircle(context, x, y, r, 0xFF111111);
        fillCircle(context, x, y, r - 1, color);

        if (b.id >= 9 && b.id != 0) {
            int stripeH = Math.max(3, r / 2);
            fillCircleStripe(context, x, y, r - 2, stripeH, 0xFFFFFFFF);
        }
        if (b.id == 0) fillCircle(context, x - r / 3, y - r / 3, Math.max(2, r / 4), 0xAAFFFFFF);

        String label = b.id == 0 ? "" : String.valueOf(b.id);
        if (!label.isEmpty()) {
            int labelR = Math.max(5, r / 2);
            fillCircle(context, x, y, labelR, b.id == 8 ? 0xFF111111 : 0xEFFFFFFF);
            int lw = textRenderer.getWidth(label);
            context.drawText(textRenderer, label, x - lw / 2, y - 4, b.id == 8 ? 0xFFFFFF : 0x111111, false);
        }
    }

    private int ballColor(int id) {
        return switch (id) {
            case 0 -> 0xFFEFEFEF;
            case 1, 9 -> 0xFFFFD84D;
            case 2, 10 -> 0xFF3E7BFF;
            case 3, 11 -> 0xFFFF4F4F;
            case 4, 12 -> 0xFF7B3EFF;
            case 5, 13 -> 0xFFFF9F2E;
            case 6, 14 -> 0xFF32B36B;
            case 7, 15 -> 0xFF8B3E2A;
            case 8 -> 0xFF171717;
            default -> 0xFFFFFFFF;
        };
    }

    private void drawSideBar(DrawContext context) {
        int x = panelX + panelW - 210;
        int y = panelY + 54;
        fill(context, x, y, x + 188, y + panelH - 96, 0x66283236);
        context.drawTextWithShadow(textRenderer, "Участники", x + 12, y + 12, 0xDFFAFF);
        int yy = y + 32;
        UUID turn = state.getCurrentPlayerId();
        for (UUID id : state.activePlayers) {
            String name = state.playerNames.getOrDefault(id, "Игрок");
            int score = state.scores.getOrDefault(id, 0);
            boolean vote = state.resetVotes.contains(id);
            String mark = id.equals(turn) ? "▶ " : "  ";
            context.drawTextWithShadow(textRenderer, mark + name, x + 12, yy, id.equals(turn) ? 0xFFE9A8 : 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "шары: " + score + (vote ? "  сброс ✓" : ""), x + 24, yy + 12, 0xB6C8C8);
            yy += 31;
        }
        if (state.activePlayers.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "ПКМ по столу, чтобы войти", x + 12, yy, 0xB6C8C8);
        } else if (state.activePlayers.size() == 1) {
            context.drawTextWithShadow(textRenderer, "Можно играть одному", x + 12, yy, 0xB6C8C8);
        } else {
            context.drawTextWithShadow(textRenderer, "За столом: " + state.activePlayers.size() + "/3", x + 12, yy, 0xB6C8C8);
        }
        yy += 18;
        context.drawTextWithShadow(textRenderer, "Управление", x + 12, yy + 10, 0xDFFAFF);
        context.drawTextWithShadow(textRenderer, "ЛКМ: зажать и отпустить", x + 12, yy + 28, 0xB6C8C8);
        context.drawTextWithShadow(textRenderer, "Мышь: направление кия", x + 12, yy + 40, 0xB6C8C8);
        context.drawTextWithShadow(textRenderer, "ESC: выйти из стола", x + 12, yy + 52, 0xB6C8C8);
        if (state.gameOver) context.drawTextWithShadow(textRenderer, "Партия завершена", x + 12, yy + 76, 0xFFB0B0);
    }

    private boolean canLocalShoot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return state.canShoot(client.player.getUuid());
    }

    private int sx(double gameX) {
        return tableX + (int)Math.round(gameX / PoolGameState.TABLE_W * tableW);
    }

    private int sy(double gameY) {
        return tableY + (int)Math.round(gameY / PoolGameState.TABLE_H * tableH);
    }

    private double gx(double screenX) {
        return (screenX - tableX) / (double)tableW * PoolGameState.TABLE_W;
    }

    private double gy(double screenY) {
        return (screenY - tableY) / (double)tableH * PoolGameState.TABLE_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideTable(mouseX, mouseY) && canLocalShoot()) {
            dragging = true;
            dragStartX = mouseX;
            dragStartY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            PoolBall cue = state.getBall(0);
            if (cue != null) {
                double cx = sx(cue.x);
                double cy = sy(cue.y);
                double angle = Math.atan2(mouseY - cy, mouseX - cx) + Math.PI;
                double power = Math.min(1.0, Math.hypot(mouseX - dragStartX, mouseY - dragStartY) / 180.0);
                PoolBilliardsClient.sendShot(tablePos, angle, power);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInsideTable(double mouseX, double mouseY) {
        return mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= tableY && mouseY <= tableY + tableH;
    }

    private void fillCircle(DrawContext context, int cx, int cy, int r, int color) {
        int rr = r * r;
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int)Math.floor(Math.sqrt(Math.max(0, rr - dy * dy)));
            fill(context, cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void fillCircleStripe(DrawContext context, int cx, int cy, int r, int stripeH, int color) {
        int rr = r * r;
        int half = Math.max(1, stripeH / 2);
        for (int dy = -half; dy <= half; dy++) {
            int dx = (int)Math.floor(Math.sqrt(Math.max(0, rr - dy * dy)));
            fill(context, cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            fill(context, x, y, x + 1, y + 1, color);
        }
    }

    private void fill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void removed() {
        PoolBilliardsClient.sendClose(tablePos);
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
