package com.coraxberg.poolbilliards.client;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.game.PoolBall;
import com.coraxberg.poolbilliards.game.PoolGameState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class PoolTableScreen extends Screen {
    private static final Identifier CUE_TEXTURE = PoolBilliardsMod.id("textures/gui/cue.png");
    private static final int DARK_WOOD = 0xFF28180F;
    private static final int WOOD = 0xFF603B24;
    private static final int WOOD_LIGHT = 0xFF875735;
    private static final int BRASS = 0xFFD4AD68;
    private static final int PARCHMENT = 0xFFE5D3A9;
    private static final int MUTED = 0xFFBFAE8A;
    private final BlockPos tablePos;
    private PoolGameState state = new PoolGameState();
    private final PoolBallVisuals ballVisuals = new PoolBallVisuals();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tableX;
    private int tableY;
    private int tableW;
    private int tableH;
    private int resetX;
    private int resetY;
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
        resetX = panelX + panelW - 196;
        resetY = panelY + panelH - 52;
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
        ballVisuals.beginFrame();
        drawPanel(context);
        drawTable(context, mouseX, mouseY);
        drawSideBar(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPanel(DrawContext context) {
        fill(context, panelX + 5, panelY + 7, panelX + panelW + 5, panelY + panelH + 7, 0xAA000000);
        fill(context, panelX, panelY, panelX + panelW, panelY + panelH, DARK_WOOD);
        fill(context, panelX + 3, panelY + 3, panelX + panelW - 3, panelY + panelH - 3, WOOD);
        fill(context, panelX + 7, panelY + 7, panelX + panelW - 7, panelY + panelH - 7, DARK_WOOD);
        fill(context, panelX + 7, panelY + 7, panelX + panelW - 7, panelY + 40, WOOD_LIGHT);
        fill(context, panelX + 7, panelY + 39, panelX + panelW - 7, panelY + 41, BRASS);
        context.drawTextWithShadow(textRenderer, "БИЛЬЯРД · ВОСЬМЁРКА", panelX + 20, panelY + 18, PARCHMENT);
        context.drawTextWithShadow(textRenderer, state.status, panelX + 200, panelY + 18, 0xFFF3DEAA);
    }

    private void drawTable(DrawContext context, int mouseX, int mouseY) {
        fill(context, tableX - 23, tableY - 23, tableX + tableW + 23, tableY + tableH + 23, 0xFF170E09);
        fill(context, tableX - 20, tableY - 20, tableX + tableW + 20, tableY + tableH + 20, WOOD);
        fill(context, tableX - 17, tableY - 17, tableX + tableW + 17, tableY + tableH + 17, WOOD_LIGHT);
        fill(context, tableX - 12, tableY - 12, tableX + tableW + 12, tableY + tableH + 12, DARK_WOOD);
        fill(context, tableX - 2, tableY - 2, tableX + tableW + 2, tableY + tableH + 2, 0xFF073C2D);
        fill(context, tableX, tableY, tableX + tableW, tableY + tableH, 0xFF0B6248);
        fill(context, tableX + 5, tableY + 5, tableX + tableW - 5, tableY + tableH - 5, 0xFF0E6A4C);
        for (int i = 1; i <= 6; i++) {
            int markX = tableX + tableW * i / 7;
            fill(context, markX - 2, tableY - 17, markX + 2, tableY - 14, BRASS);
            fill(context, markX - 2, tableY + tableH + 14, markX + 2, tableY + tableH + 17, BRASS);
        }
        for (int i = 1; i <= 3; i++) {
            int markY = tableY + tableH * i / 4;
            fill(context, tableX - 17, markY - 2, tableX - 14, markY + 2, BRASS);
            fill(context, tableX + tableW + 14, markY - 2, tableX + tableW + 17, markY + 2, BRASS);
        }

        drawPocket(context, tableX, tableY);
        drawPocket(context, tableX + tableW / 2, tableY);
        drawPocket(context, tableX + tableW, tableY);
        drawPocket(context, tableX, tableY + tableH);
        drawPocket(context, tableX + tableW / 2, tableY + tableH);
        drawPocket(context, tableX + tableW, tableY + tableH);

        PoolBall aimingBall = state.getBall(0);
        if (dragging && aimingBall != null && !aimingBall.pocketed && canLocalShoot()) {
            PoolBallVisuals.Position visual = ballVisuals.sample(aimingBall);
            int cx = sx(visual.x());
            int cy = sy(visual.y());
            double pull = Math.min(1.0, Math.hypot(mouseX - dragStartX, mouseY - dragStartY) / 180.0);
            drawAimingCue(context, cx, cy, mouseX, mouseY, pull);
        }
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
                fill(context, tableX, tableY + tableH + 27, tableX + 124, tableY + tableH + 34, 0xFF1A120C);
                fill(context, tableX + 2, tableY + tableH + 29, tableX + 2 + power * 120 / 100, tableY + tableH + 32, BRASS);
                context.drawTextWithShadow(textRenderer, "Сила удара: " + power + "%", tableX + 132, tableY + tableH + 26, PARCHMENT);
            }
        }
    }

    private void drawPocket(DrawContext context, int x, int y) {
        fillCircle(context, x, y, 18, 0xFF050505);
        fillCircle(context, x, y, 12, 0xFF000000);
    }

    private void drawAimingCue(DrawContext context, int ballX, int ballY, int mouseX, int mouseY, double power) {
        double angle = Math.atan2(mouseY - ballY, mouseX - ballX);
        int ballRadius = Math.max(6, (int) Math.round(PoolGameState.BALL_R * tableW / PoolGameState.TABLE_W));
        int pullBack = (int) Math.round(power * 15);
        context.getMatrices().push();
        context.getMatrices().translate(ballX, ballY, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) angle));
        context.drawTexture(CUE_TEXTURE, ballRadius + 3 + pullBack, -8, 0, 0, 128, 16, 128, 16);
        context.getMatrices().pop();
    }

    private void drawBall(DrawContext context, PoolBall b) {
        if (b.pocketed) return;
        PoolBallVisuals.Position visual = ballVisuals.sample(b);
        int x = sx(visual.x());
        int y = sy(visual.y());
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
        int bottom = panelY + panelH - 22;
        fill(context, x, y, x + 188, bottom, WOOD_LIGHT);
        fill(context, x + 3, y + 3, x + 185, bottom - 3, DARK_WOOD);
        fill(context, x + 7, y + 7, x + 181, bottom - 7, 0xFF3B2819);
        fill(context, x + 7, y + 7, x + 181, y + 31, WOOD);
        fill(context, x + 9, y + 31, x + 179, y + 32, BRASS);
        context.drawTextWithShadow(textRenderer, "ИГРОКИ ЗА СТОЛОМ", x + 14, y + 15, PARCHMENT);
        int yy = y + 44;
        UUID turn = state.getCurrentPlayerId();
        for (UUID id : state.activePlayers) {
            String name = state.playerNames.getOrDefault(id, "Игрок");
            int score = state.scores.getOrDefault(id, 0);
            boolean vote = state.resetVotes.contains(id);
            fill(context, x + 12, yy - 4, x + 176, yy + 26, id.equals(turn) ? 0xFF63432A : 0xFF4B3321);
            fill(context, x + 12, yy - 4, x + 14, yy + 26, id.equals(turn) ? BRASS : WOOD_LIGHT);
            context.drawTextWithShadow(textRenderer, (id.equals(turn) ? "▶ " : "  ") + name, x + 20, yy,
                    id.equals(turn) ? 0xFFFFE4A8 : PARCHMENT);
            context.drawTextWithShadow(textRenderer, "Забито: " + score + (vote ? " · сброс ✓" : ""), x + 26, yy + 13, MUTED);
            yy += 35;
        }
        if (state.activePlayers.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "ПКМ по столу: войти", x + 14, yy, MUTED);
        } else if (state.activePlayers.size() == 1) {
            context.drawTextWithShadow(textRenderer, "Можно играть одному", x + 14, yy, MUTED);
        } else {
            context.drawTextWithShadow(textRenderer, "За столом: " + state.activePlayers.size() + "/3", x + 14, yy, MUTED);
        }
        int controlsY = Math.max(y + 175, yy + 28);
        fill(context, x + 12, controlsY - 9, x + 176, controlsY - 8, BRASS);
        context.drawTextWithShadow(textRenderer, "КАК ИГРАТЬ", x + 14, controlsY, PARCHMENT);
        context.drawTextWithShadow(textRenderer, "ЛКМ · удержать и отпустить", x + 14, controlsY + 18, MUTED);
        if (resetY - controlsY >= 75) {
            context.drawTextWithShadow(textRenderer, "Мышь · прицеливание", x + 14, controlsY + 32, MUTED);
            context.drawTextWithShadow(textRenderer, "ESC · покинуть стол", x + 14, controlsY + 46, MUTED);
        }
        if (state.gameOver && resetY - controlsY >= 95) {
            context.drawTextWithShadow(textRenderer, "Партия завершена", x + 14, controlsY + 65, 0xFFFFB49B);
        }

        boolean hover = currentMouseX >= resetX && currentMouseX < resetX + 168
                && currentMouseY >= resetY && currentMouseY < resetY + 24;
        fill(context, resetX, resetY, resetX + 168, resetY + 24, BRASS);
        fill(context, resetX + 2, resetY + 2, resetX + 166, resetY + 22, hover ? WOOD_LIGHT : WOOD);
        String buttonText = "НОВАЯ ПАРТИЯ";
        context.drawTextWithShadow(textRenderer, buttonText,
                resetX + (168 - textRenderer.getWidth(buttonText)) / 2, resetY + 8, PARCHMENT);
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= resetX && mouseX < resetX + 168
                && mouseY >= resetY && mouseY < resetY + 24) {
            PoolBilliardsClient.sendReset(tablePos);
            return true;
        }
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
