package com.coraxberg.poolbilliards.client;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.game.PoolBall;
import com.coraxberg.poolbilliards.game.PoolGameState;
import com.coraxberg.poolbilliards.game.PoolTableGeometry;
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
    private PoolScreenLayout layout;
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
        layout = PoolScreenLayout.fit(width, height);
        panelX = layout.x();
        panelY = layout.y();
        panelW = layout.width();
        panelH = layout.height();
        tableX = layout.tableX();
        tableY = layout.tableY();
        tableW = layout.tableWidth();
        tableH = layout.tableHeight();
        resetX = layout.resetX();
        resetY = layout.resetY();
        dragging = false;
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
        if (layout.sidebar()) drawSideBar(context);
        else drawCompactPlayers(context);
        drawFooter(context);
        super.render(context, mouseX, mouseY, delta);
        // The cue may extend beyond the felt; render it above the sidebar and
        // every other screen element so the butt never disappears underneath.
        PoolBall aimingBall = state.getBall(0);
        if (dragging && aimingBall != null && !aimingBall.pocketed && canLocalShoot()) {
            PoolBallVisuals.Position visual = ballVisuals.sample(aimingBall);
            double pull = Math.min(1.0, Math.hypot(mouseX - dragStartX, mouseY - dragStartY) / layout.pullDistance());
            drawAimingCue(context, sx(visual.x()), sy(visual.y()), mouseX, mouseY, pull);
        }
    }

    private void drawPanel(DrawContext context) {
        fill(context, panelX + 5, panelY + 7, panelX + panelW + 5, panelY + panelH + 7, 0xAA000000);
        fill(context, panelX, panelY, panelX + panelW, panelY + panelH, DARK_WOOD);
        fill(context, panelX + 3, panelY + 3, panelX + panelW - 3, panelY + panelH - 3, WOOD);
        fill(context, panelX + 7, panelY + 7, panelX + panelW - 7, panelY + panelH - 7, DARK_WOOD);
        fill(context, panelX + 7, panelY + 7, panelX + panelW - 7, panelY + 29, WOOD_LIGHT);
        fill(context, panelX + 7, panelY + 29, panelX + panelW - 7, panelY + 30, BRASS);
        drawFitted(context, "БИЛЬЯРД · ВОСЬМЁРКА", panelX + 12, panelY + 9, panelW - 24, PARCHMENT);
        drawFitted(context, state.status, panelX + 12, panelY + 20, panelW - 24, 0xFFF3DEAA);
    }

    private void drawTable(DrawContext context, int mouseX, int mouseY) {
        int rail = layout.rail();
        fill(context, tableX - rail, tableY - rail, tableX + tableW + rail, tableY + tableH + rail, DARK_WOOD);
        fill(context, tableX - rail + 2, tableY - rail + 2, tableX + tableW + rail - 2, tableY + tableH + rail - 2, WOOD_LIGHT);
        fill(context, tableX - 3, tableY - 3, tableX + tableW + 3, tableY + tableH + 3, 0xFF073C2D);
        fill(context, tableX, tableY, tableX + tableW, tableY + tableH, 0xFF0E6A4C);

        for (int i = 1; i <= 6; i++) {
            int markX = tableX + tableW * i / 7;
            fill(context, markX - 2, tableY - rail + 3, markX + 2, tableY - rail + 5, BRASS);
            fill(context, markX - 2, tableY + tableH + rail - 5, markX + 2, tableY + tableH + rail - 3, BRASS);
        }
        for (int i = 1; i <= 3; i++) {
            int markY = tableY + tableH * i / 4;
            fill(context, tableX - rail + 3, markY - 2, tableX - rail + 5, markY + 2, BRASS);
            fill(context, tableX + tableW + rail - 5, markY - 2, tableX + tableW + rail - 3, markY + 2, BRASS);
        }

        for (PoolTableGeometry.Pocket pocket : PoolTableGeometry.POCKETS) {
            drawPocket(context, pocket);
        }

        for (PoolBall b : state.balls) drawBall(context, b);

        PoolBall cue = state.getBall(0);
        if (cue != null && !cue.pocketed && canLocalShoot()) {
            int cx = sx(cue.x);
            int cy = sy(cue.y);
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double len = Math.max(1, Math.sqrt(dx * dx + dy * dy));
            int lineColor = dragging ? 0xCCFFE9A8 : 0x80FFFFFF;
            double aimLength = Math.min(90, tableW * 0.20);
            double start = ballRadius() + 2;
            context.enableScissor(tableX, tableY, tableX + tableW, tableY + tableH);
            drawLine(context, cx - dx / len * start, cy - dy / len * start,
                    cx - dx / len * aimLength, cy - dy / len * aimLength, lineColor);
            context.disableScissor();
        }
    }

    private void drawPocket(DrawContext context, PoolTableGeometry.Pocket pocket) {
        // Use the model's position and two radii, including the side-pocket
        // setback. Subpixel geometry keeps the opening round at large GUI scale.
        float rx = (float) (pocket.rx() * tableW / PoolGameState.TABLE_W);
        float ry = (float) (pocket.ry() * tableH / PoolGameState.TABLE_H);
        context.getMatrices().push();
        context.getMatrices().translate(tableX + pocket.x() * tableW / PoolGameState.TABLE_W,
                tableY + pocket.y() * tableH / PoolGameState.TABLE_H, 0);
        context.getMatrices().scale(rx / 48, ry / 48, 1);
        fillCircle(context, 0, 0, 48, 0xFF19110E);
        fillCircle(context, 0, 0, 46, 0xFF020303);
        context.getMatrices().pop();
    }

    private void drawAimingCue(DrawContext context, int ballX, int ballY, int mouseX, int mouseY, double power) {
        double angle = Math.atan2(mouseY - ballY, mouseX - ballX);
        int ballRadius = ballRadius();
        double pullBack = power * tableW * 0.025;
        context.getMatrices().push();
        context.getMatrices().translate(ballX, ballY, 300);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) angle));
        context.getMatrices().translate(ballRadius + 3 + pullBack, 0, 0);
        // Length follows the table rather than being capped at 128 GUI pixels.
        // Scale width independently to retain a slender shaft and tapered tip.
        context.getMatrices().scale(tableW * 0.40f / 128, Math.max(3, ballRadius * 0.85f) / 16, 1);
        context.drawTexture(CUE_TEXTURE, 0, -8, 0, 0, 128, 16, 128, 16);
        context.getMatrices().pop();
    }

    private void drawBall(DrawContext context, PoolBall b) {
        if (b.pocketed) return;
        PoolBallVisuals.Position visual = ballVisuals.sample(b);
        // Draw at subpixel precision so small balls keep their coloured rim
        // instead of the white number badge covering the entire ball.
        context.getMatrices().push();
        context.getMatrices().translate(sx(visual.x()), sy(visual.y()), 0);
        context.getMatrices().scale(1.0f / 3, 1.0f / 3, 1);
        int x = 0;
        int y = 0;
        int r = Math.max(6, (int) Math.round(PoolGameState.BALL_R * tableW / PoolGameState.TABLE_W * 3));
        int color = ballColor(b.id);

        fillCircle(context, x + 1, y + 1, r, 0xAA000000);
        fillCircle(context, x, y, r, 0xFF111111);
        fillCircle(context, x, y, r - 1, color);

        if (b.id >= 9 && !state.monochromeBalls) {
            int stripeH = Math.max(1, r / 2);
            fillCircleStripe(context, x, y, Math.max(1, r - 1), stripeH, 0xFFFFFFFF);
        }
        if (b.id == 0) fillCircle(context, x - r / 3, y - r / 3, Math.max(1, r / 4), 0xAAFFFFFF);

        String label = b.id == 0 ? "" : String.valueOf(b.id);
        if (!label.isEmpty()) {
            int labelR = Math.max(2, r / 2);
            fillCircle(context, x, y, labelR, b.id == 8 && !state.monochromeBalls ? 0xFF111111 : 0xEFFFFFFF);
            int lw = textRenderer.getWidth(label);
            float scale = Math.min(3.0f, Math.min(labelR * 1.8f / Math.max(1, lw), labelR * 1.8f / 9.0f));
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawText(textRenderer, label, -lw / 2, -4, b.id == 8 && !state.monochromeBalls ? 0xFFFFFF : 0x111111, false);
            context.getMatrices().pop();
        }
        context.getMatrices().pop();
    }

    private int ballRadius() {
        return Math.max(2, (int) Math.round(PoolGameState.BALL_R * tableW / PoolGameState.TABLE_W));
    }

    private int ballColor(int id) {
        if (state.monochromeBalls) return id == 0 ? 0xFFFFD680 : 0xFFF7F4EC;
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
        int x = layout.sidebarX();
        int y = panelY + 34;
        int bottom = resetY - 6;
        fill(context, x, y, x + 188, bottom, WOOD_LIGHT);
        fill(context, x + 2, y + 2, x + 186, bottom - 2, DARK_WOOD);
        context.drawTextWithShadow(textRenderer, "ИГРОКИ ЗА СТОЛОМ", x + 10, y + 10, PARCHMENT);
        int yy = y + 30;
        UUID turn = state.getCurrentPlayerId();
        for (UUID id : state.activePlayers) {
            if (yy + 31 > bottom) break;
            fill(context, x + 7, yy, x + 181, yy + 31, id.equals(turn) ? WOOD : 0xFF3B2819);
            drawFitted(context, (id.equals(turn) ? "▶ " : "") + state.playerNames.getOrDefault(id, "Игрок"), x + 12, yy + 4, 164, PARCHMENT);
            drawFitted(context, "Забито: " + state.scores.getOrDefault(id, 0) + (state.resetVotes.contains(id) ? " · сброс ✓" : ""), x + 12, yy + 17, 164, MUTED);
            yy += 35;
        }
        yy += 10;
        String[] help = {"КАК ИГРАТЬ", "ЛКМ · удержать и отпустить", "Мышь · прицеливание", "ESC · покинуть стол"};
        for (String line : help) {
            if (yy + 10 > bottom - 6) break;
            drawFitted(context, line, x + 10, yy, 168, MUTED);
            yy += 15;
        }
    }

    private void drawCompactPlayers(DrawContext context) {
        int count = Math.max(1, state.activePlayers.size());
        int slotW = (panelW - 16) / count;
        int x = panelX + 8;
        int y = layout.footerY();
        for (UUID id : state.activePlayers) {
            boolean current = id.equals(state.getCurrentPlayerId());
            fill(context, x, y, x + slotW - 3, y + 16, current ? WOOD : 0xFF3B2819);
            String score = " " + state.scores.getOrDefault(id, 0) + (state.resetVotes.contains(id) ? " ✓" : "");
            int scoreW = textRenderer.getWidth(score);
            drawFitted(context, (current ? "▶ " : "") + state.playerNames.getOrDefault(id, "Игрок"), x + 3, y + 4, slotW - scoreW - 10, PARCHMENT);
            context.drawTextWithShadow(textRenderer, score, x + slotW - scoreW - 6, y + 4, BRASS);
            x += slotW;
        }
    }

    private void drawFooter(DrawContext context) {
        int resetW = layout.resetWidth();
        boolean hover = currentMouseX >= resetX && currentMouseX < resetX + resetW
                && currentMouseY >= resetY && currentMouseY < resetY + 20;
        fill(context, resetX, resetY, resetX + resetW, resetY + 20, BRASS);
        fill(context, resetX + 1, resetY + 1, resetX + resetW - 1, resetY + 19, hover ? WOOD_LIGHT : WOOD);
        context.drawCenteredTextWithShadow(textRenderer, "НОВАЯ ПАРТИЯ", resetX + resetW / 2, resetY + 6, PARCHMENT);
        int x = panelX + 10;
        int available = resetX - x - 8;
        if (dragging) {
            int power = (int) (Math.min(1, Math.hypot(currentMouseX - dragStartX, currentMouseY - dragStartY) / layout.pullDistance()) * 100);
            drawFitted(context, "Сила: " + power + "%", x, resetY + 1, available, PARCHMENT);
            int barW = Math.min(available, 160);
            fill(context, x, resetY + 13, x + barW, resetY + 17, WOOD);
            fill(context, x, resetY + 13, x + barW * power / 100, resetY + 17, BRASS);
        } else if (!state.appearanceLocked && !state.gameOver) {
            int w = styleButtonWidth();
            boolean enabled = canLocalChangeStyle();
            boolean over = currentMouseX >= x && currentMouseX < x + w
                    && currentMouseY >= resetY && currentMouseY < resetY + 20;
            fill(context, x, resetY, x + w, resetY + 20, enabled ? BRASS : DARK_WOOD);
            fill(context, x + 1, resetY + 1, x + w - 1, resetY + 19, enabled && over ? WOOD_LIGHT : WOOD);
            drawFitted(context, state.monochromeBalls ? "Шары: белые" : "Шары: цветные", x + 6, resetY + 6, w - 12, enabled ? PARCHMENT : MUTED);
        } else {
            drawFitted(context, "ЛКМ: удержать и отпустить", x, resetY + 6, available, MUTED);
        }
    }

    private int styleButtonWidth() {
        return Math.min(160, resetX - (panelX + 10) - 8);
    }

    private boolean canLocalChangeStyle() {
        return client != null && client.player != null && state.canChangeBallStyle(client.player.getUuid());
    }

    private void drawFitted(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        if (textRenderer.getWidth(text) > maxWidth) {
            text = textRenderer.trimToWidth(text, Math.max(0, maxWidth - textRenderer.getWidth("…"))) + "…";
        }
        context.drawTextWithShadow(textRenderer, text, x, y, color);
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !dragging && canLocalChangeStyle()
                && mouseX >= panelX + 10 && mouseX < panelX + 10 + styleButtonWidth()
                && mouseY >= resetY && mouseY < resetY + 20) {
            PoolBilliardsClient.sendBallStyle(tablePos, !state.monochromeBalls);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseX >= resetX && mouseX < resetX + layout.resetWidth()
                && mouseY >= resetY && mouseY < resetY + 20) {
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
                double power = Math.min(1.0, Math.hypot(mouseX - dragStartX, mouseY - dragStartY) / layout.pullDistance());
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

    private void drawLine(DrawContext context, double x1, double y1, double x2, double y2, int color) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        if (length <= 0) return;
        // A single thin quad replaces the staircase of whole GUI pixels. Keep
        // at least one physical screen pixel at every Minecraft GUI scale.
        float thickness = (float) Math.max(0.5, 1.0 / client.getWindow().getScaleFactor());
        context.getMatrices().push();
        context.getMatrices().translate(x1, y1, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(y2-y1, x2-x1)));
        context.getMatrices().translate(0, -thickness / 2, 0);
        context.getMatrices().scale((float) length, thickness, 1);
        context.fill(0, 0, 1, 1, color);
        context.getMatrices().pop();
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
